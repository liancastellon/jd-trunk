//    jDownloader - Downloadmanager
//    Copyright (C) 2015  JD-Team support@jdownloader.org
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
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jd.PluginWrapper;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "filecloud.io", "ifile.it" }, urls = { "https?://(www\\.)?decryptedfilecloud\\.io/[a-z0-9]+", "fhrfzjnerhfDELETEMEdhzrnfdgvfcas4378zhb" }, flags = { 2, 0 })
public class IFileIt extends PluginForHost {

    private final String         useragent                = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:12.0) Gecko/20100101 Firefox/12.0";
    /* must be static so all plugins share same lock */
    private static Object        LOCK                     = new Object();
    private final int            MAXFREECHUNKS            = 1;
    private final int            MAXPREMIUMCHUNKS         = -2;
    private static final String  ONLY4REGISTERED          = "\"message\":\"signup\"";
    private static final String  ONLY4REGISTEREDUSERTEXT  = JDL.LF("plugins.hoster.ifileit.only4registered", "Wait or register to download the files");
    private static final String  NOCHUNKS                 = "NOCHUNKS";
    private static final String  NORESUME                 = "NORESUME";
    public static final String   MAINPAGE                 = "https://filecloud.io";
    private static AtomicInteger maxPrem                  = new AtomicInteger(1);
    private static AtomicBoolean UNDERMAINTENANCE         = new AtomicBoolean(false);
    private static final String  UNDERMAINTENANCEUSERTEXT = "The site is under maintenance!";

    private static final String  NICE_HOST                = "filecloud.io";
    private String               dllink                   = null;
    private boolean              webMethod                = false;

    public IFileIt(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(MAINPAGE + "/user-register.html");
        this.setStartIntervall(5 * 1000l);
    }

    public void correctDownloadLink(DownloadLink link) {
        link.setPluginPatternMatcher(link.getPluginPatternMatcher().replace("decryptedfilecloud", "filecloud"));
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        prepBrowser(br);
        boolean failed = true;
        try {
            final String fid = getFid(downloadLink);
            final Account aa = AccountController.getInstance().getValidAccount(this);
            String apikey = null;
            // First try API via apikey from account
            if (aa != null) {
                try {
                    apikey = getUrlEncodedAPIkey(aa, this, br);
                    br.postPage((br.getHttpConnection() == null ? MAINPAGE : "") + "/api-fetch_file_details.api", "akey=" + apikey + "&ukey=" + fid);
                    failed = false;
                } catch (final BrowserException e) {
                } catch (final ConnectException e) {
                }
            } else {
                failed = true;
            }
            // If API via account fails, try public check API
            if (failed) {
                this.getPluginConfig().setProperty("apikey", Property.NULL);
                this.getPluginConfig().setProperty("username", Property.NULL);
                this.getPluginConfig().setProperty("password", Property.NULL);
                br.postPage((br.getHttpConnection() == null ? MAINPAGE : "") + "/api-check_file.api", "ukey=" + fid);
                failed = false;
            }

        } catch (final BrowserException e) {
            failed = true;
        } catch (final ConnectException e) {
            failed = true;
        }
        this.getPluginConfig().save();
        // Check without API if everything fails
        if (br.containsHTML("\"message\":\"no such user\"") || failed) {
            logger.warning("API key is invalid, jumping in other handling...");
            final boolean isfollowingRedirect = br.isFollowingRedirects();
            // clear old browser
            br = prepBrowser(new Browser());
            // can be direct link!
            URLConnectionAdapter con = null;
            br.setFollowRedirects(true);
            try {
                con = br.openGetConnection(downloadLink.getDownloadURL());
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    if (con.getResponseCode() == 503) {
                        // they are using cloudflare these days!
                        // downloadLink.getLinkStatus().setStatusText(UNDERMAINTENANCEUSERTEXT);
                        // UNDERMAINTENANCE.set(true);
                        return AvailableStatus.UNCHECKABLE;
                    }
                    br.followConnection();
                } else {
                    downloadLink.setName(getFileNameFromHeader(con));
                    try {
                        // @since JD2
                        downloadLink.setVerifiedFileSize(con.getLongContentLength());
                    } catch (final Throwable t) {
                        downloadLink.setDownloadSize(con.getLongContentLength());
                    }
                    // lets also set dllink
                    dllink = br.getURL();
                    // set constants so we can save link, no point wasting this link!
                    return AvailableStatus.TRUE;
                }
            } finally {
                webMethod = true;
                br.setFollowRedirects(isfollowingRedirect);
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
            final String filesize = getAb1Downloadsize();
            if (filesize == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if ("0".equals(filesize)) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            downloadLink.setDownloadSize(SizeFormatter.getSize(filesize));
        } else {
            // Check with API
            final String status = getJson("status", br);
            if (status == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (!"ok".equals(getJson("status", br))) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            final String filename = getJson("name", br);
            final String filesize = getJson("size", br);
            if (filename == null || filesize == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            downloadLink.setFinalFileName(Encoding.htmlDecode(filename));
            downloadLink.setDownloadSize(Long.parseLong(filesize));
        }
        return AvailableStatus.TRUE;
    }

    public void doFree(final DownloadLink downloadLink, boolean viaAccount) throws Exception, PluginException {
        if (dllink == null) {
            // since linkcheck can be done via api-api or web-api or web-web. this is still needed here for when api linkchecking has
            // happened! We don't need to do it again when webmethod isn't directlink!
            if (!webMethod) {
                br.setFollowRedirects(true);
                URLConnectionAdapter con = null;
                try {
                    con = br.openGetConnection(downloadLink.getDownloadURL());
                    if (!con.getContentType().contains("html")) {
                        logger.info("Link is a direct link");
                        dllink = br.getURL();
                    } else {
                        logger.info("Link is no direct link");
                        br.followConnection();
                    }
                } finally {
                    try {
                        con.disconnect();
                    } catch (Throwable e) {
                    }
                }
            }
            if (dllink == null) {
                final String ab1 = br.getRegex("if\\( __ab1 == '([^']+)'").getMatch(0);
                if (ab1 == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.setFollowRedirects(true);
                // Br2 is our xml browser now!
                final Browser br2 = br.cloneBrowser();
                br2.setReadTimeout(40 * 1000);
                final String ukey = new Regex(downloadLink.getDownloadURL(), "filecloud\\.io/(.+)").getMatch(0);
                xmlrequest(br2, "/download-request.json", "ukey=" + ukey + "&__ab1=" + Encoding.urlEncode(ab1));
                if (br.containsHTML("message\":\"invalid request\"")) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error");
                }
                if (!viaAccount && br2.containsHTML(ONLY4REGISTERED)) {
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, ONLY4REGISTEREDUSERTEXT, 30 * 60 * 1000l);
                }
                if (br2.containsHTML("\"message\":\"gopremium\"")) {
                    throw new PluginException(LinkStatus.ERROR_FATAL, "Only downloadable for premium users");
                }
                if (br2.containsHTML("\"captcha\":1")) {
                    PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                    jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br2);
                    // Semi-automatic reCaptcha handling
                    final String k = br.getRegex("recaptcha_public.*?=.*?'([^']+)';").getMatch(0);
                    if (k == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    rc.setId(k);
                    rc.load();
                    for (int i = 0; i <= 5; i++) {
                        File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                        String c = getCaptchaCode("recaptcha", cf, downloadLink);
                        xmlrequest(br2, "/download-request.json", "ukey=" + ukey + "&__ab1=" + ab1 + "&ctype=recaptcha&recaptcha_response=" + Encoding.urlEncode_light(c) + "&recaptcha_challenge=" + rc.getChallenge());
                        if (br2.containsHTML("(\"retry\":1|\"captcha\":1)")) {
                            rc.reload();
                            continue;
                        }
                        break;
                    }
                }
                if (br2.containsHTML("(\"retry\":1|\"captcha\":1)")) {
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                }
                if (br2.containsHTML("\"message\":\"signup\"")) {
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, ONLY4REGISTEREDUSERTEXT, 30 * 60 * 1000l);
                }
                br.getPage("/download.html");
                dllink = br.getRegex("id=\"requestBtnHolder\">[\t\n\r ]+<a href=\"(http://[^<>\"]*?)\"").getMatch(0);
                if (dllink == null) {
                    dllink = br2.getRegex("\"(http://s\\d+\\.filecloud\\.io/[a-z0-9]+/\\d+/[^<>\"/]*?)\"").getMatch(0);
                }
                if (dllink == null) {
                    if (br.containsHTML("in order to download any publicly shared file, please enter its unique download URL")) {
                        int timesFailed = downloadLink.getIntegerProperty("timesfailedfilecloudit_dllinknull", 0);
                        downloadLink.getLinkStatus().setRetryCount(0);
                        if (timesFailed <= 2) {
                            timesFailed++;
                            downloadLink.setProperty("timesfailedfilecloudit_dllinknull", timesFailed);
                            throw new PluginException(LinkStatus.ERROR_RETRY, "Download could not be started");
                        } else {
                            downloadLink.setProperty("timesfailedfilecloudit_dllinknull", Property.NULL);
                            logger.info(NICE_HOST + ": dllink is null --> Plugin broken");
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                    }
                    logger.info("last try getting dllink failed, plugin must be defect!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
        }
        br.setFollowRedirects(false);
        int chunks = MAXFREECHUNKS;
        boolean resume = true;
        if (downloadLink.getBooleanProperty(IFileIt.NOCHUNKS, false) || resume == false) {
            chunks = 1;
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resume, chunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 503) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, 10 * 60 * 1000l);
            }
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!this.dl.startDownload()) {
            try {
                if (dl.externalDownloadStop()) {
                    return;
                }
            } catch (final Throwable e) {
            }
            if (downloadLink.getLinkStatus().getErrorMessage() != null && downloadLink.getLinkStatus().getErrorMessage().startsWith(JDL.L("download.error.message.rangeheaders", "Server does not support chunkload"))) {
                if (downloadLink.getBooleanProperty(IFileIt.NORESUME, false) == false) {
                    downloadLink.setChunksProgress(null);
                    downloadLink.setProperty(IFileIt.NORESUME, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            } else {
                /* unknown error, we disable multiple chunks */
                if (downloadLink.getBooleanProperty(IFileIt.NOCHUNKS, false) == false) {
                    downloadLink.setProperty(IFileIt.NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            }
        }
    }

    private String getAb1Downloadsize() {
        String ab1 = br.getRegex("var __ab1 = (\\d+);").getMatch(0);
        if (ab1 == null) {
            ab1 = br.getRegex("\\$\\('#fsize'\\)\\.empty\\(\\)\\.append\\( toMB\\( (\\d+) \\) \\);").getMatch(0);
        }
        return ab1;
    }

    @Override
    public String getAGBLink() {
        return MAINPAGE + "/misc-tos.html";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        /* Tested 12.01.15, trying more than one will result in empty page on download-try of finallink */
        return 1;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        /* Nochmals das File überprüfen */
        requestFileInformation(downloadLink);
        if (UNDERMAINTENANCE.get()) {
            throw new PluginException(LinkStatus.ERROR_FATAL, UNDERMAINTENANCEUSERTEXT);
        }
        if (dllink == null) {
            br.setRequestIntervalLimit(getHost(), 250);
            simulateBrowser();
        }
        doFree(downloadLink, false);
    }

    @SuppressWarnings("unchecked")
    private void login(final Account account) throws Exception {
        br.setFollowRedirects(true);
        prepBrowser(br);
        final String apikey = getUrlEncodedAPIkey(account, this, br);
        if (apikey == null) {
            logger.info("Couldn't find akey (APIKEY) -> Account must be invalid!");
            if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loginOldWay(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            this.br.setCookie(MAINPAGE, key, value);
                        }
                        return;
                    }
                }
                br.setFollowRedirects(true);
                prepBrowser(br);
                br.getPage(MAINPAGE + "/user-login.html");
                // We don't know if a captcha is needed so first we try without,
                // if we get an errormessage we know a captcha is needed
                boolean accountValid = false;
                boolean captchaNeeded = false;
                for (int i = 0; i <= 2; i++) {
                    final String rcID = br.getRegex("var.*?recaptcha_public.*?=.*?'([^']+)';").getMatch(0);
                    if (captchaNeeded) {
                        if (rcID == null) {
                            logger.warning("recaptcha ID not found, stoping!");
                            break;
                        }
                        final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                        final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
                        rc.setId(rcID);
                        rc.load();
                        final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                        final DownloadLink dummyLink = new DownloadLink(this, "Account", "filecloud.io", "http://filecloud.io", true);
                        final String c = getCaptchaCode("recaptcha", cf, dummyLink);
                        br.postPage("/user-login_p.html", "username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()) + "&recaptcha_challenge_field=" + rc.getChallenge() + "&recaptcha_response_field=" + c);
                    } else {
                        br.postPage("/user-login_p.html", "username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
                    }
                    if (br.getURL().contains("\\$\\('#alertFld'\\)\\.empty\\(\\)\\.append\\( 'incorrect reCaptcha entered, try again'") || br.getURL().contains("error=RECAPTCHA__INCORRECT")) {
                        captchaNeeded = true;
                        logger.info("Wrong captcha and/or username/password entered!");
                        continue;
                    }
                    if (!br.containsHTML("</i> you have successfully logged in")) {
                        continue;
                    }
                    accountValid = true;
                    break;
                }
                if (!accountValid) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = this.br.getCookies(MAINPAGE);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        /* reset maxPrem workaround on every fetchaccount info */
        maxPrem.set(1);
        try {
            // Use cookies, check and refresh if needed
            login(account);
            br.postPage((br.getHttpConnection() == null ? MAINPAGE : "") + "/api-fetch_account_details.api", "akey=" + getUrlEncodedAPIkey(account, this, br));
        } catch (final PluginException e) {
            account.setValid(false);
            throw e;
        }
        // Only free acc support till now
        if ("0".equals(getJson("is_premium", br))) {
            ai.setStatus("Registered (free) account");
            account.setProperty("free", true);
            try {
                account.setType(AccountType.FREE);
                maxPrem.set(1);
                account.setMaxSimultanDownloads(1);
                // free accounts can still have captcha.
                account.setConcurrentUsePossible(false);
            } catch (final Throwable e) {
            }
        } else {
            ai.setStatus("Premium Account");
            account.setProperty("free", false);
            ai.setValidUntil(Long.parseLong(getJson("premium_until", br)) * 1000);
            try {
                account.setType(AccountType.PREMIUM);
                maxPrem.set(5);
                account.setMaxSimultanDownloads(5);
                account.setConcurrentUsePossible(true);
            } catch (final Throwable e) {
            }
        }
        ai.setUnlimitedTraffic();
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        if (UNDERMAINTENANCE.get()) {
            throw new PluginException(LinkStatus.ERROR_FATAL, UNDERMAINTENANCEUSERTEXT);
        }
        login(account);
        if (!account.getBooleanProperty("free", false)) {
            final String apikey = getUrlEncodedAPIkey(account, this, br);
            final String fid = getFid(link);
            if (apikey == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            try {
                br.postPage((br.getHttpConnection() == null ? MAINPAGE : "") + "/api-fetch_download_url.api", "akey=" + apikey + "&ukey=" + fid);
            } catch (final BrowserException e) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 5 * 60 * 1000l);
            }
            if (br.containsHTML("\"message\":\"no such file\"")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            String finallink = getJson("download_url", br);
            if (finallink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            finallink = finallink.replace("\\", "");

            int maxchunks = MAXPREMIUMCHUNKS;
            if (link.getBooleanProperty(NOCHUNKS, false)) {
                maxchunks = 1;
            }

            dl = jd.plugins.BrowserAdapter.openDownload(br, link, finallink, true, maxchunks);
            if (dl.getConnection().getContentType().contains("html")) {
                if (dl.getConnection().getResponseCode() == 503) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Too many connections", 10 * 60 * 1000l);
                }
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (!this.dl.startDownload()) {
                try {
                    if (dl.externalDownloadStop()) {
                        return;
                    }
                } catch (final Throwable e) {
                }
                /* unknown error, we disable multiple chunks */
                if (link.getBooleanProperty(IFileIt.NOCHUNKS, false) == false) {
                    link.setProperty(IFileIt.NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            }
        } else {
            br.setFollowRedirects(true);
            loginOldWay(account, false);
            doFree(link, true);
        }
    }

    public static String getUrlEncodedAPIkey(final Account aa, final PluginForHost plu, final Browser br) throws IOException {
        String apikey = plu.getPluginConfig().getStringProperty("apikey", null);
        final String username = plu.getPluginConfig().getStringProperty("username", null);
        final String password = plu.getPluginConfig().getStringProperty("password", null);
        // Check if we already have an apikey and if it's the correct one
        if (apikey == null || username == null || password == null || !aa.getUser().equals(username) || !aa.getPass().equals(password)) {
            plu.getPluginConfig().setProperty("apikey", Property.NULL);
            plu.getPluginConfig().setProperty("username", Property.NULL);
            plu.getPluginConfig().setProperty("password", Property.NULL);
            br.postPage((br.getHttpConnection() == null ? MAINPAGE : "") + "/api-fetch_apikey.api", "username=" + Encoding.urlEncode(aa.getUser()) + "&password=" + Encoding.urlEncode(aa.getPass()));
            apikey = getJson("akey", br);
            if (apikey != null) {
                plu.getPluginConfig().setProperty("apikey", apikey);
                plu.getPluginConfig().setProperty("username", aa.getUser());
                plu.getPluginConfig().setProperty("password", aa.getPass());
            }
        }
        return Encoding.urlEncode(apikey);
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return maxPrem.get();
    }

    public Browser prepBrowser(Browser br) {
        if (br == null) {
            br = new Browser();
        }
        br.getHeaders().put("User-Agent", useragent);
        br.getHeaders().put("Accept-Language", "de-de,de;q=0.8,en-us;q=0.5,en;q=0.3");
        br.setCookie("http://filecloud.io/", "lang", "en");
        return br;
    }

    private String getFid(final DownloadLink downloadLink) {
        return new Regex(downloadLink.getDownloadURL(), "([a-z0-9]+)$").getMatch(0);
    }

    private void simulateBrowser() throws IOException {
        br.cloneBrowser().getPage("/ads/adframe.js");
    }

    private void xmlrequest(final Browser br, final String url, final String postData) throws IOException {
        br.getHeaders().put("User-Agent", useragent);
        br.getHeaders().put("Accept-Language", "de-de,de;q=0.8,en-us;q=0.5,en;q=0.3");
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        br.postPage(url, postData);
        br.getHeaders().remove("X-Requested-With");
    }

    public static String getJson(final String parameter, final Browser br) {
        String result = br.getRegex("\"" + parameter + "\":(\\d+)").getMatch(0);
        if (result == null) {
            result = br.getRegex("\"" + parameter + "\":\"([^<>\"]*?)\"").getMatch(0);
        }
        return result;
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
        if (Boolean.TRUE.equals(acc.getBooleanProperty("free"))) {
            /* free accounts also have captchas */
            return true;
        }
        return false;
    }
}