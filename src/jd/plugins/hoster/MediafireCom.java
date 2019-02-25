//    jDownloader - Downloadmanager
//    Copyright (C) 2013  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.InputField;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountRequiredException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.components.UserAgents;
import jd.plugins.download.HashInfo;
import jd.utils.locale.JDL;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "mediafire.com" }, urls = { "https?://(www\\.|m\\.|download\\d+\\.)?mediafire\\.com/(download/[a-z0-9]+|(download\\.php\\?|\\?JDOWNLOADER(?!sharekey)|file/|file\\?|download/?).*?(?=http:|$|\r|\n))" })
public class MediafireCom extends PluginForHost {
    /** Settings stuff */
    private static final String FREE_FORCE_RECONNECT_ON_CAPTCHA = "FREE_FORCE_RECONNECT_ON_CAPTCHA";

    public static String stringUserAgent() {
        return UserAgents.stringUserAgent();
    }

    public static String portableUserAgent() {
        return UserAgents.portableUserAgent();
    }

    public static String hbbtvUserAgent() {
        return UserAgents.hbbtvUserAgent();
    }

    // ?9579576935451
    // Referer: http://www.mediafire.com/file/nw1lc2pyrtp043c/1972+Fritz+the+Cat+-+Fritz+Bugs+Out%7BSirReal.rar
    /* End of HbbTV agents */
    /** end of random agents **/
    private static final String PRIVATEFILE           = JDL.L("plugins.hoster.mediafirecom.errors.privatefile", "Private file: Only downloadable for registered users");
    private static final String PRIVATEFOLDERUSERTEXT = "This is a private folder. Re-Add this link while your account is active to make it work!";

    public static abstract class PasswordSolver {
        protected Browser       br;
        protected PluginForHost plg;
        protected DownloadLink  dlink;
        private final int       maxTries;
        private int             currentTry;

        public PasswordSolver(final PluginForHost plg, final Browser br, final DownloadLink downloadLink) {
            this.plg = plg;
            this.br = br;
            this.dlink = downloadLink;
            this.maxTries = 3;
            this.currentTry = 0;
        }

        abstract protected void handlePassword(String password) throws Exception;

        // do not add @Override here to keep 0.* compatibility
        public boolean hasAutoCaptcha() {
            return false;
        }

        abstract protected boolean isCorrect();

        public void run() throws Exception {
            while (this.currentTry++ < this.maxTries) {
                String password = null;
                if ((password = this.dlink.getStringProperty("pass", null)) != null) {
                } else {
                    password = Plugin.getUserInput(JDL.LF("PasswordSolver.askdialog", "Downloadpassword for %s/%s", this.plg.getHost(), this.dlink.getName()), this.dlink);
                }
                if (password == null) {
                    throw new PluginException(LinkStatus.ERROR_FATAL, JDL.L("plugins.errors.wrongpassword", "Password wrong"));
                }
                this.handlePassword(password);
                if (!this.isCorrect()) {
                    this.dlink.setProperty("pass", Property.NULL);
                    continue;
                } else {
                    this.dlink.setProperty("pass", password);
                    return;
                }
            }
            throw new PluginException(LinkStatus.ERROR_RETRY, JDL.L("plugins.errors.wrongpassword", "Password wrong"));
        }
    }

    private static AtomicReference<String> agent                      = new AtomicReference<String>(stringUserAgent());
    /**
     * The number of retries to be performed in order to determine if a file is availableor to try captcha/password.
     */
    private int                            max_number_of_free_retries = 3;
    private String                         dlURL;
    private Browser                        api                        = null;
    private String                         session_token              = null;

    @SuppressWarnings("deprecation")
    public MediafireCom(final PluginWrapper wrapper) {
        super(wrapper);
        this.setStartIntervall(5000);
        this.enablePremium("https://www.mediafire.com/upgrade/");
        setConfigElements();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void correctDownloadLink(final DownloadLink link) throws Exception {
        final String id = getFUID(link);
        if (id != null) {
            link.setProperty("LINKDUPEID", "mediafirecom_" + id);
        }
        link.setUrlDownload(link.getDownloadURL().replaceFirst("http://media", "http://www.media"));
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(br, account, true);
        } catch (final PluginException e) {
            account.setValid(false);
            throw e;
        }
        account.setValid(true);
        final String usedSpace = PluginJSonUtils.getJsonValue(api, "used_storage_size");
        ai.setUsedSpace(usedSpace != null ? Long.parseLong(usedSpace) : 0);
        if (account.getType() == AccountType.FREE) {
            ai.setStatus("Free Account");
            ai.setUnlimitedTraffic();
            account.setMaxSimultanDownloads(10);
            account.setConcurrentUsePossible(true);
        } else {
            // assume its in bytes.
            final String bandwidth = PluginJSonUtils.getJsonValue(api, "bandwidth");
            ai.setTrafficLeft(bandwidth != null ? Long.parseLong(bandwidth) : 0);
            ai.setStatus("Premium Account");
            account.setMaxSimultanDownloads(20);
            account.setConcurrentUsePossible(true);
        }
        return ai;
    }

    @Override
    public String getAGBLink() {
        return "http://www.mediafire.com/terms_of_service.php";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 10;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (downloadLink.getBooleanProperty("privatefolder")) {
            throw new PluginException(LinkStatus.ERROR_FATAL, PRIVATEFOLDERUSERTEXT);
        }
        doFree(downloadLink, null);
    }

    @SuppressWarnings("deprecation")
    public void doFree(final DownloadLink downloadLink, final Account account) throws Exception {
        br = new Browser();
        String url = null;
        int trycounter = 0;
        boolean captchaCorrect = false;
        if (account == null) {
            br.getHeaders().put("User-Agent", MediafireCom.agent.get());
        }
        do {
            if (url != null) {
                break;
            }
            this.requestFileInformation(downloadLink);
            if (downloadLink.getBooleanProperty("privatefile") && account == null) {
                throw new PluginException(LinkStatus.ERROR_FATAL, PRIVATEFILE);
            }
            // Check for direct link
            br.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            try {
                con = br.openGetConnection(downloadLink.getDownloadURL());
                if (con.isContentDisposition()) {
                    url = downloadLink.getDownloadURL();
                } else {
                    br.followConnection();
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
            handleNonAPIErrors(downloadLink, br);
            if (url == null) {
                // TODO: This errorhandling is missing for premium users!
                captchaCorrect = false;
                final Form captchaForm = br.getFormbyProperty("name", "form_captcha");
                if (captchaForm != null) {
                    if (captchaForm.containsHTML("solvemedia.com/papi/")) {
                        logger.info("Detected captcha method \"solvemedia\" for this host");
                        handleExtraReconnectSettingOnCaptcha(account);
                        final org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia sm = new org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia(br);
                        final File cf = sm.downloadCaptcha(getLocalCaptchaFile());
                        String code = getCaptchaCode(cf, downloadLink);
                        String chid = sm.getChallenge(code);
                        captchaForm.put("adcopy_challenge", chid);
                        captchaForm.put("adcopy_response", code.replace(" ", "+"));
                        br.submitForm(captchaForm);
                        if (br.getFormbyProperty("name", "form_captcha") != null) {
                            logger.info("solvemedia captcha wrong");
                            continue;
                        }
                    } else if (captchaForm.containsHTML("g-recaptcha-response")) {
                        handleExtraReconnectSettingOnCaptcha(account);
                        final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
                        captchaForm.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                        br.submitForm(captchaForm);
                        if (br.getFormbyProperty("name", "form_captcha") != null) {
                            logger.info("recaptchav2 captcha wrong");
                            continue;
                        }
                    } else if (captchaForm.containsHTML("(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)")) {
                        logger.info("Detected captcha method \"Re Captcha\" for this host");
                        handleExtraReconnectSettingOnCaptcha(account);
                        final Recaptcha rc = new Recaptcha(br, this);
                        String id = new Regex(captchaForm.getHtmlCode(), "challenge\\?k=(.+?)\"").getMatch(0);
                        if (id != null) {
                            logger.info("CaptchaID found, Form found " + (captchaForm != null));
                            rc.setId(id);
                            final InputField challenge = new InputField("recaptcha_challenge_field", null);
                            final InputField code = new InputField("recaptcha_response_field", null);
                            captchaForm.addInputField(challenge);
                            captchaForm.addInputField(code);
                            rc.setForm(captchaForm);
                            rc.load();
                            final File cf = rc.downloadCaptcha(this.getLocalCaptchaFile());
                            boolean defect = false;
                            try {
                                final String c = this.getCaptchaCode("recaptcha", cf, downloadLink);
                                rc.setCode(c);
                                final Form captchaForm2 = br.getFormbyProperty("name", "form_captcha");
                                id = br.getRegex("challenge\\?k=(.+?)\"").getMatch(0);
                                if (captchaForm2 != null && id == null) {
                                    logger.info("Form found but no ID");
                                    defect = true;
                                    logger.info("PluginError 672");
                                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                                }
                                if (id != null) {
                                    /* captcha wrong */
                                    logger.info("reCaptcha captcha wrong");
                                    continue;
                                }
                            } catch (final PluginException e) {
                                if (defect) {
                                    throw e;
                                }
                                /**
                                 * captcha input timeout run out.. try to reconnect
                                 */
                                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Try reconnect to avoid more captchas", 5 * 60 * 1000l);
                            }
                        }
                    } else if (captchaForm.containsHTML("for=\"customCaptchaCheckbox\"")) {
                        /* Mediafire custom checkbox "captcha" */
                        captchaForm.put("mf_captcha_response", "1");
                        br.submitForm(captchaForm);
                        if (br.getFormbyProperty("name", "form_captcha") != null) {
                            logger.info("custom captcha wrong");
                            continue;
                        }
                    } else {
                        br.submitForm(captchaForm);
                    }
                }
            }
            captchaCorrect = true;
            if (url == null) {
                logger.info("Handle possible PW");
                this.handlePW(downloadLink);
                url = br.getRegex("kNO\\s*=\\s*\"(https?://.*?)\"").getMatch(0);
                logger.info("Kno= " + url);
                if (url == null) {
                    /* pw protected files can directly redirect to download */
                    url = br.getRedirectLocation();
                }
                if (url == null) {
                    url = br.getRegex("(https?://download\\d+.mediafire\\.com/[^\"']+)").getMatch(0);
                }
            }
            trycounter++;
        } while (trycounter < max_number_of_free_retries && url == null);
        if (url == null) {
            if (!captchaCorrect) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        br.setFollowRedirects(true);
        br.setDebug(true);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, url, true, 0);
        if (!dl.getConnection().isContentDisposition()) {
            handleServerErrors();
            logger.info("Error (3)");
            // logger.info(dl.getConnection() + "");
            br.followConnection();
            handleNonAPIErrors(downloadLink, br);
            if (br.containsHTML("We apologize, but we are having difficulties processing your download request")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Please be patient while we try to repair your download request", 2 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.setFilenameFix(true);
        dl.startDownload();
    }

    private void handleServerErrors() throws PluginException {
        if (dl.getConnection().getResponseCode() == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403, ", 30 * 60 * 1000l);
        } else if (dl.getConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404, ", 30 * 60 * 1000l);
        }
    }

    private void handleExtraReconnectSettingOnCaptcha(final Account account) throws PluginException {
        if (this.getPluginConfig().getBooleanProperty(FREE_FORCE_RECONNECT_ON_CAPTCHA, false)) {
            if (account != null) {
                logger.info("Captcha reconnect setting active & free account used --> TEMPORARILY_UNAVAILABLE");
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Waiting some time to avoid captcha in free account mode", 30 * 60 * 1000l);
            } else {
                logger.info("Captcha reconnect setting active & NO account used --> IP_BLOCKED");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Reconnecting or waiting some time to avoid captcha in free mode", 30 * 60 * 1000l);
            }
        }
    }

    private String getFUID(DownloadLink link) {
        String fileID = new Regex(link.getDownloadURL(), "https?://.*?/(file|file\\.php|download|download\\.php)/?\\??([a-zA-Z0-9]+)").getMatch(1);
        if (fileID == null) {
            fileID = new Regex(link.getDownloadURL(), "\\?([a-zA-Z0-9]+)").getMatch(0);
            if (fileID == null) {
                fileID = new Regex(link.getDownloadURL(), "([a-z0-9]+)$").getMatch(0);
            }
        }
        return fileID;
    }

    @Override
    public void handlePremium(final DownloadLink downloadLink, final Account account) throws Exception {
        requestFileInformation(downloadLink);
        if (downloadLink.getBooleanProperty("privatefolder")) {
            throw new PluginException(LinkStatus.ERROR_FATAL, PRIVATEFOLDERUSERTEXT);
        }
        login(br, account, false);
        if (account.getType() == AccountType.FREE) {
            doFree(downloadLink, account);
        } else {
            apiCommand(account, "file/get_links.php", "link_type=direct_download&quick_key=" + getFUID(downloadLink));
            final String url = PluginJSonUtils.getJsonValue(api, "direct_download");
            if (url == null) {
                // you can error under success.....
                // {"response":{"action":"file\/get_links","links":[{"quickkey":"removed","error":"User lacks
                // permissions"}],"result":"Success","current_api_version":"1.5"}}
                if (StringUtils.equalsIgnoreCase(PluginJSonUtils.getJson(api, "error"), "User lacks permissions")) {
                    throw new AccountRequiredException("Incorrect account been used to download this file");
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.setFollowRedirects(true);
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, url, true, 0);
            if (!dl.getConnection().isContentDisposition()) {
                handleServerErrors();
                logger.info("Error (4)");
                logger.info(dl.getConnection() + "");
                br.followConnection();
                handleNonAPIErrors(downloadLink, br);
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.setFilenameFix(true);
            dl.startDownload();
        }
    }

    private void handlePW(final DownloadLink downloadLink) throws Exception {
        if (br.containsHTML("dh\\(''\\)")) {
            new PasswordSolver(this, br, downloadLink) {
                String curPw = null;

                @Override
                protected void handlePassword(final String password) throws Exception {
                    curPw = password;
                    final Form form = br.getFormbyProperty("name", "form_password");
                    if (form == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    form.put("downloadp", Encoding.urlEncode(curPw));
                    br.submitForm(form);
                }

                @Override
                protected boolean isCorrect() {
                    Form form = br.getFormbyProperty("name", "form_password");
                    if (form != null) {
                        return false;
                    } else {
                        return true;
                    }
                }
            }.run();
        }
    }

    @Override
    public void init() {
        Browser.setRequestIntervalLimitGlobal(this.getHost(), 250);
    }

    public void login(final Browser lbr, final Account account, boolean force) throws Exception {
        boolean red = lbr.isFollowingRedirects();
        try {
            // at this stage always trusting cookies
            final Cookies cookies = account.loadCookies("");
            if (!force && cookies != null) {
                lbr.setCookies(this.getHost(), cookies);
                return;
            }
            this.setBrowserExclusive();
            final String lang = System.getProperty("user.language");
            lbr.setFollowRedirects(true);
            if (!isValidMailAdress(account.getUser())) {
                if ("de".equalsIgnoreCase(lang)) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBitte trage deine E-Mail Adresse in das 'Name'-Feld ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlease enter your e-mail adress in the 'Name'-field.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            lbr.getPage("https://www.mediafire.com/");
            // some shit that happens in javascript.
            final Browser lbr2 = lbr.cloneBrowser();
            lbr2.getPage("/templates/login_signup/login_signup.php?dc=loginPath");
            Form form = lbr2.getFormbyProperty("id", "form_login1");
            if (form == null) {
                if ("de".equalsIgnoreCase(lang)) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            form.put("login_email", Encoding.urlEncode(account.getUser()));
            form.put("login_pass", Encoding.urlEncode(account.getPass()));
            // submit via the same browser
            lbr2.submitForm(form);
            final String cookie = lbr2.getCookie("http://www.mediafire.com", "user");
            if ("x".equals(cookie) || cookie == null) {
                if ("de".equalsIgnoreCase(lang)) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            // now with session token we can get info about the account from the web-api hybrid
            lbr.setFollowRedirects(false);
            lbr.getPage("/myaccount/");
            initApi(account);
            apiCommand(account, "user/get_info.php", null);
            // to determine free | premium this is done via above request
            final String accounType = PluginJSonUtils.getJsonValue(api, "premium");
            if (StringUtils.equalsIgnoreCase(accounType, "no")) {
                account.setType(AccountType.FREE);
            } else {
                account.setType(AccountType.PREMIUM);
            }
            account.saveCookies(lbr.getCookies(this.getHost()), "");
        } catch (final PluginException e) {
            throw e;
        } finally {
            lbr.setFollowRedirects(red);
        }
    }

    private void initApi(final Account account) throws Exception {
        if (api == null) {
            api = br.cloneBrowser();
        }
        if (session_token == null) {
            session_token = api.getRegex("parent\\.bqx\\(\"([a-f0-9]+)\"\\)").getMatch(0);
            if (session_token == null && br.getRequest() != null) {
                session_token = br.getRegex("LoadIframeLightbox\\('/templates/tos\\.php\\?token=([a-f0-9]+)").getMatch(0);
            }
            if (session_token == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            account.setProperty("session_token", session_token);
        }
        apiCommand(account, "device/get_status.php", null);
    }

    private void apiCommand(final Account account, final String command, final String args) throws Exception {
        if (session_token == null) {
            if (account != null) {
                session_token = account.getStringProperty("session_token", null);
                if (session_token == null) {
                    // relogin
                    login(br, account, true);
                    session_token = account.getStringProperty("session_token", null);
                }
            }
            if (session_token == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        api = br.cloneBrowser();
        api.setAllowedResponseCodes(403);
        api.getHeaders().put("Accept", "*/*");
        api.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        // website still uses 1.4, api is up to 1.5 at this stage -raztoki20160101
        String a = "https://www.mediafire.com/api/1.4/" + command + "?r=" + getRandomFourLetters() + (args != null ? "&" + args : "") + "&session_token=" + session_token + "&response_format=json";
        api.getPage(a);
        // is success ?
        handleApiError(account);
    }

    private void handleApiError(final Account account) throws PluginException {
        // FYI you can have errors even though it's success
        if (!StringUtils.equalsIgnoreCase(PluginJSonUtils.getJsonValue(api, "result"), "Success")) {
            if (StringUtils.equalsIgnoreCase(PluginJSonUtils.getJsonValue(api, "result"), "Error")) {
                // error handling
                final String error = PluginJSonUtils.getJsonValue(api, "error");
                switch (Integer.parseInt(error)) {
                case 105:
                    session_token = null;
                    if (account != null) {
                        account.setProperty("session_token", Property.NULL);
                        account.clearCookies("");
                    }
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                    // offline file, to file/get_info as a single file... we need to return so the proper
                case 110:
                    // invalid uid
                case 111:
                    return;
                default:
                    // unknown error!
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
        }
    }

    private boolean handleLinkcheckingApiError(final Account account) throws PluginException {
        if (!StringUtils.equalsIgnoreCase(PluginJSonUtils.getJsonValue(api, "result"), "Success")) {
            if (StringUtils.equalsIgnoreCase(PluginJSonUtils.getJsonValue(api, "result"), "Error")) {
                // error handling
                final String error = PluginJSonUtils.getJsonValue(api, "error");
                switch (Integer.parseInt(error)) {
                case 111:// invalid uid
                case 110:// Unknown or Invalid QuickKey
                    return true;
                default:
                    return false;
                }
            }
        }
        return false;
    }

    private String getRandomFourLetters() {
        final Random r = new Random();
        final String soup = "abcdefghijklmnopqrstuvwxyz";
        String v = "";
        for (int i = 0; i < 4; i++) {
            v = v + soup.charAt(r.nextInt(soup.length()));
        }
        return v;
    }

    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    @Override
    public boolean checkLinks(final DownloadLink[] urls) {
        final Account aa = AccountController.getInstance().getValidAccount(this);
        return checkLinks(urls, aa);
    }

    public boolean checkLinks(final DownloadLink[] urls, final Account account) {
        try {
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            final Map<String, DownloadLink> linkMap = new HashMap<String, DownloadLink>();
            while (true) {
                links.clear();
                linkMap.clear();
                while (true) {
                    // maximum number of quickkeys allowed is 500.
                    if (links.size() > 100 || index == urls.length) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                sb.append("quick_key=");
                boolean addDelimiter = false;
                for (final DownloadLink dl : links) {
                    if (addDelimiter) {
                        sb.append(",");
                    } else {
                        addDelimiter = true;
                    }
                    final String id = getFUID(dl);
                    linkMap.put(id, dl);
                    sb.append(id);
                }
                if (account != null) {
                    apiCommand(account, "file/get_info.php", sb.toString());
                } else {
                    api = br.cloneBrowser();
                    api.setAllowedResponseCodes(new int[] { 400, 403 });
                    api.getHeaders().put("Accept", "*/*");
                    api.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                    api.getPage("https://www.mediafire.com/api/1.4/file/get_info.php" + "?r=" + getRandomFourLetters() + "&" + sb.toString() + "&response_format=json");
                    handleApiError(account);
                }
                final Map<String, Object> apiResponse = JSonStorage.restoreFromString(api.toString(), TypeRef.HASHMAP);
                final List<Map<String, Object>> file_infos;
                Object infos = JavaScriptEngineFactory.walkJson(apiResponse, "response/file_infos");
                if (infos == null) {
                    infos = JavaScriptEngineFactory.walkJson(apiResponse, "response/file_info");
                }
                if (infos != null && infos instanceof List) {
                    file_infos = (List<Map<String, Object>>) infos;
                } else if (infos != null && infos instanceof Map) {
                    file_infos = new ArrayList<Map<String, Object>>();
                    file_infos.add((Map<String, Object>) infos);
                } else {
                    if (links.size() == 1) {
                        final DownloadLink dl = links.get(0);
                        // for invalid uid in arraylist.size == 1;
                        if (handleLinkcheckingApiError(account)) {
                            // we know that single result must be false!
                            dl.setAvailableStatus(AvailableStatus.FALSE);
                        } else {
                            // single result....
                            dl.setAvailableStatus(AvailableStatus.TRUE);
                        }
                        return true;
                    } else if (handleLinkcheckingApiError(account) && links.size() > 1) {
                        // all uids teh array are invalid.
                        file_infos = null;
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
                if (file_infos != null) {
                    for (final Map<String, Object> file_info : file_infos) {
                        final DownloadLink item = linkMap.remove(file_info.get("quickkey"));
                        if (item != null) {
                            item.setAvailableStatus(AvailableStatus.TRUE);
                            final String name = (String) file_info.get("filename");
                            final Long size = JavaScriptEngineFactory.toLong(file_info.get("size"), -1);
                            final String hash = (String) file_info.get("hash");
                            final String privacy = (String) file_info.get("privacy");
                            final String pass = (String) file_info.get("password_protected");
                            if (StringUtils.isNotEmpty(name)) {
                                item.setFinalFileName(name);
                            }
                            if (size != null && size >= 0) {
                                item.setVerifiedFileSize(size);
                            }
                            if (StringUtils.isNotEmpty(hash)) {
                                item.setHashInfo(HashInfo.parse(hash));
                            }
                            if (privacy != null) {
                                item.setProperty("privacy", privacy);
                            }
                            if (pass != null) {
                                item.setProperty("passwordRequired", PluginJSonUtils.parseBoolean(pass));
                            }
                        }
                    }
                }
                for (final DownloadLink offline : linkMap.values()) {
                    // if some uids are invalid with valid results, invalids just don't return.. we can then set them as offline!
                    offline.setAvailableStatus(AvailableStatus.FALSE);
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            getLogger().log(e);
            return false;
        }
        return true;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        correctDownloadLink(link);
        if (!checkLinks(new DownloadLink[] { link }) || !link.isAvailabilityStatusChecked()) {
            link.setAvailableStatus(AvailableStatus.UNCHECKABLE);
        } else if (!link.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return getAvailableStatus(link);
    }

    private void handleNonAPIErrors(final DownloadLink dl, Browser imported) throws PluginException, IOException {
        // imported browser affects br so lets make a new browser just for error checking.
        Browser eBr = new Browser();
        // catch, and prevent a null imported browser
        if (imported == null) {
            imported = br.cloneBrowser();
        }
        if (imported != null) {
            eBr = imported.cloneBrowser();
        } else {
            // prob not required...
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        // Some errors are only provided if isFollowingRedirects==true. As this isn't always the case throughout the plugin, lets grab the
        // redirect page so we can use .containsHTML
        if (!eBr.isFollowingRedirects()) {
            if (eBr.getRedirectLocation() != null) {
                eBr.getPage(eBr.getRedirectLocation());
            }
        }
        // error checking below!
        if (eBr.getURL().matches(".+/error\\.php\\?errno=3(20|23|78|80|86|88).*?")) {
            // 320 = file is removed by the originating user or MediaFire.
            // 323 = Dangerous File Blocked.
            // 378 = File Removed for Violation (of TOS)
            // 380 = claimed by a copyright holder through a valid DMCA request
            // 386 = File Blocked for Violation.
            // 388 = identified as copyrighted work
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (eBr.getURL().matches(".+/error\\.php\\?errno=394.*?")) {
            /*
             * The file you attempted to download is an archive that is encrypted or password protected. MediaFire does not support
             * unlimited downloads of encrypted or password protected archives and the limit for this file has been reached. MediaFire
             * understands the need for users to transfer encrypted and secured files, we offer this service starting at $1.50 per month. We
             * have informed the owner that sharing of this file has been limited and how they can resolve this issue.
             */
            throw new PluginException(LinkStatus.ERROR_FATAL, "Download not possible, retriction based on uploaders account");
        } else if (eBr.getURL().contains("mediafire.com/error.php?errno=382")) {
            dl.getLinkStatus().setStatusText("File Belongs to Suspended Account.");
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (eBr.getURL().contains("mediafire.com/error.php?errno=999") && eBr.containsHTML("<div id=\"privateTitle\">This file is currently set to private.</div>")) {
            // note: error 999 can redirect into other error messages! eg. http://www.mediafire.com/error.php?errno=999 ->
            // http://www.mediafire.com/error.php?errno=320, with the file id it will hold it's place
            // /error.php?errno=999&quickkey=FUID&origin=download.
            // 999 = ...
            // 999 = This file is currently set to private.
            // When a resource is set to private by its owner, only the owner can access it. If you would like to request access to the
            // file, please log in to your account.
            // Link; 8609354739341.log; 47049765; jdlog://8609354739341
            dl.getLinkStatus().setStatusText("File has been set to private, only owner can download.");
            throw new PluginException(LinkStatus.ERROR_FATAL);
        } else if (eBr.containsHTML("class=\"error\\-title\">Temporarily Unavailable</p>")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "This file is temporarily unavailable!", 30 * 60 * 1000l);
        } else if (eBr.containsHTML("class=\"error-title\">This download is currently unavailable<")) {
            // jdlog://7235652095341
            final String time = eBr.getRegex("we will retry your download again in (\\d+) seconds\\.<").getMatch(0);
            long t = ((time != null ? Long.parseLong(time) : 60) * 1000l) + 2;
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "This file is temporarily unavailable!", t);
        }
    }

    private boolean isValidMailAdress(final String value) {
        return value.matches(".+@.+");
    }

    @Override
    public String getDescription() {
        return "JDownloader's mediafire.com plugin helps downloading files from mediafire.com.";
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), FREE_FORCE_RECONNECT_ON_CAPTCHA, JDL.L("plugins.hoster.MediafireCom.FreeForceReconnectOnCaptcha", "Free mode: Reconnect if captcha input needed?\r\n<html><p style=\"color:#F62817\"><b>WARNING: This setting can prevent captchas but it can also lead to an infinite reconnect loop!</b></p></html>")).setDefaultValue(false));
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        }
        if (acc.getType() == AccountType.FREE) {
            /* free accounts also have captchas */
            return true;
        }
        return false;
    }

    private AvailableStatus getAvailableStatus(DownloadLink link) {
        try {
            final Field field = link.getClass().getDeclaredField("availableStatus");
            field.setAccessible(true);
            Object ret = field.get(link);
            if (ret != null && ret instanceof AvailableStatus) {
                return (AvailableStatus) ret;
            }
        } catch (final Throwable e) {
        }
        return AvailableStatus.UNCHECKED;
    }
}