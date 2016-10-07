//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.File;
import java.util.List;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;
import org.jdownloader.plugins.components.antiDDoSForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "brazzers.com" }, urls = { "http://brazzersdecrypted\\.com/scenes/view/id/\\d+/|https?://ma\\.brazzers\\.com/download/\\d+/\\d+/mp4_\\d+_\\d+/|https?://brazzersdecrypted\\.photos\\.bz\\.contentdef\\.com/\\d+/pics/img/\\d+\\.jpg\\?nvb=\\d+\\&nva=\\d+\\&hash=[a-f0-9]+" })
public class BrazzersCom extends antiDDoSForHost {

    public BrazzersCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://enter.brazzers.com/signup/signup.php");
    }

    @Override
    public String getAGBLink() {
        return "http://brazzerssupport.com/terms-of-service/";
    }

    /* Connection stuff */
    private static final boolean FREE_RESUME                  = true;
    private static final int     FREE_MAXCHUNKS               = 0;
    private static final int     FREE_MAXDOWNLOADS            = 20;
    private final boolean        ACCOUNT_PREMIUM_RESUME       = true;
    private final int            ACCOUNT_PREMIUM_MAXCHUNKS    = 0;
    private final int            ACCOUNT_PREMIUM_MAXDOWNLOADS = 20;

    private boolean              not_yet_released             = false;

    private final String         type_normal_moch             = "http://brazzersdecrypted\\.com/scenes/view/id/\\d+/";
    private final String         type_premium_video           = "https?://ma\\.brazzers\\.com/download/.+";
    private final String         type_premium_pic             = "https?://(?:brazzersdecrypted\\.)?photos\\.bz\\.contentdef\\.com/\\d+/pics/img/\\d+\\.jpg\\?nvb=\\d+\\&nva=\\d+\\&hash=[a-f0-9]+";

    public static final String   html_loggedin                = "id=\"my\\-account\"";

    private String               dllink                       = null;
    private boolean              server_issues                = false;

    public static Browser prepBR(final Browser br) {
        br.setFollowRedirects(true);
        /* Skips redirect to stupid advertising page after login. */
        br.setCookie("ma.brazzers.com", "skipPostLogin", "1");
        return br;
    }

    public void correctDownloadLink(final DownloadLink link) {
        if (link.getDownloadURL().matches(type_normal_moch)) {
            /* Make MOCH download possible --> We have to correct the downloadurl again! */
            final String fid = getFidMOCH(link);
            link.setUrlDownload(jd.plugins.decrypter.BrazzersCom.getVideoUrlFree(fid));
        } else if (link.getDownloadURL().matches(type_premium_pic)) {
            link.setUrlDownload(link.getDownloadURL().replaceAll("https?://brazzersdecrypted\\.photos\\.bz", "http://photos.bz"));
        }
    }

    /**
     * So far this plugin has no account support which means the plugin itself cannot download anything but the download via MOCH will work
     * fine.
     *
     * @throws Exception
     */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final Account aa = AccountController.getInstance().getValidAccount(this);
        Account moch_account = null;
        final List<Account> moch_accounts = AccountController.getInstance().getMultiHostAccounts(this.getHost());
        for (final Account moch_account_temp : moch_accounts) {
            if (moch_account_temp.isValid() && moch_account_temp.isEnabled()) {
                moch_account = moch_account_temp;
                break;
            }
        }

        final String fid;
        if (link.getDownloadURL().matches(type_premium_video) || link.getDownloadURL().matches(type_premium_pic)) {
            fid = link.getStringProperty("fid", null);
            this.login(this.br, aa, false);
            dllink = link.getDownloadURL();
            URLConnectionAdapter con = null;
            try {
                con = br.openHeadConnection(dllink);
                if (!con.getContentType().contains("html")) {
                    link.setDownloadSize(con.getLongContentLength());
                } else {
                    if (link.getDownloadURL().matches(type_premium_pic)) {
                        /* Refresh directurl */
                        final String number_formatted = link.getStringProperty("picnumber_formatted", null);
                        if (fid == null || number_formatted == null) {
                            /* User added url without decrypter --> Impossible to refresh this directurl! */
                            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                        }
                        this.br.getPage(jd.plugins.decrypter.BrazzersCom.getPicUrl(fid));
                        if (jd.plugins.decrypter.BrazzersCom.isOffline(this.br)) {
                            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                        }
                        final String pic_format_string = jd.plugins.decrypter.BrazzersCom.getPicFormatString(this.br);
                        if (pic_format_string == null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        dllink = String.format(pic_format_string, number_formatted);
                        /* ... new URL should work! */
                        con = br.openHeadConnection(dllink);
                        if (!con.getContentType().contains("html")) {
                            /* Set new url */
                            link.setUrlDownload(dllink);
                            /* If user copies url he should always get a valid one too :) */
                            link.setContentUrl(dllink);
                            link.setDownloadSize(con.getLongContentLength());
                        } else {
                            server_issues = true;
                        }
                    } else {
                        server_issues = true;
                    }
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        } else {
            fid = getFidMOCH(link);
            getPage(jd.plugins.decrypter.BrazzersCom.getVideoUrlFree(fid));
            /* Offline will usually return 404 */
            if (br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            final String url_name = new Regex(link.getDownloadURL(), "/id/\\d+/([^/]+)").getMatch(0);
            String filename = br.getRegex("<h1[^>]*?itemprop=\"name\">([^<>\"]+)<span").getMatch(0);

            /* This way we have a better dupe-detection! */
            link.setLinkID(fid);

            /* Two fallbacks in case we do not get any filename via html code */
            if (inValidate(filename)) {
                filename = url_name;
            }
            if (inValidate(filename)) {
                /* Finally - fallback to fid because we found nothing better. */
                filename = fid;
            } else {
                /* Add fileid in front of the filename to make it look nicer - will usually be removed in the final filename. */
                filename = fid + "_" + filename;
            }
            // final boolean moch_download_possible = AccountController.getInstance().hasMultiHostAccounts(this.getHost());
            long filesize_final = 0;
            long filesize_max = -1;
            long filesize_temp = -1;
            final String[] filesizes = br.getRegex("\\[(\\d{1,5}(?:\\.\\d{1,2})? (?:GB|GiB|MB))\\]").getColumn(0);
            for (final String filesize_temp_str : filesizes) {
                filesize_temp = SizeFormatter.getSize(filesize_temp_str);
                if (filesize_temp > filesize_max) {
                    filesize_max = filesize_temp;
                }
            }
            if (filename == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            filename = Encoding.htmlDecode(filename).trim();
            filename = encodeUnicode(filename);
            /* Do NOT set final filename yet!! */
            link.setName(filename + ".mp4");
            if (filesize_max > -1) {
                if (aa != null) {
                    /* Original brazzers account available --> Set highest filesize found --> Best Quality possible */
                    filesize_final = filesize_max;
                } else if (moch_account != null && moch_account.getHoster().contains("debriditalia")) {
                    /* Multihoster debriditalia usually returns a medium quality - about 1 / 4 the size of the best possible! */
                    filesize_final = (long) (filesize_max * 0.25);
                } else if (moch_account != null && moch_account.getHoster().contains("premiumize")) {
                    /* Multihoster premiumize usually returns 720p quality (or less, if not possible). */
                    if (this.br.containsHTML("HD MP4 1080P") && filesizes.length == 5) {
                        final String filesize_720p_temp_str = filesizes[1];
                        filesize_final = SizeFormatter.getSize(filesize_720p_temp_str);
                    } else {
                        /* 1080p not available. This else is also used as a fallback! */
                        filesize_final = filesize_max;
                    }
                } else {
                    filesize_final = filesize_max;
                }
                link.setProperty("not_yet_released", false);
                not_yet_released = false;
                link.setDownloadSize(filesize_final);
            } else {
                /* No filesize available --> Content is (probably) not (yet) released/downloadable */
                link.getLinkStatus().setStatusText("Content has not yet been released");
                link.setProperty("not_yet_released", true);
                not_yet_released = true;
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, FREE_RESUME, FREE_MAXCHUNKS, "free_directlink");
    }

    private void doFree(final DownloadLink downloadLink, final boolean resumable, final int maxchunks, final String directlinkproperty) throws Exception, PluginException {
        handleGeneralErrors();
        /* Premiumonly - TODO: Maybe download trailer in this case. */
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    private void handleGeneralErrors() throws PluginException {
        if (not_yet_released) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Content has not (yet) been released", 1 * 60 * 60 * 1000l);
        }
    }

    private String getFidMOCH(final DownloadLink dl) {
        return new Regex(dl.getDownloadURL(), "^.+/(\\d+)/?").getMatch(0);
    }

    private boolean isMOCHUrlOnly(final DownloadLink dl) {
        final String url = dl.getPluginPatternMatcher();
        return "".equals(url) || (url != null && (url.matches(type_normal_moch) || url.matches(jd.plugins.decrypter.BrazzersCom.type_video_free)));
    }

    @Override
    public boolean canHandle(final DownloadLink downloadLink, final Account account) {
        /*
         * Usually an account is needed for this host but in case content has not yet been released the plugin should jump into download
         * mode to display this errormessage to the user!
         */
        return account != null || contentHasNotYetBeenReleased(downloadLink);
    }

    private boolean contentHasNotYetBeenReleased(final DownloadLink dl) {
        return dl.getBooleanProperty("not_yet_released", false);
    }

    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        final boolean is_this_plugin = downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
        if (is_this_plugin) {
            /* The original brazzers plugin is always allowed to download. */
            return true;
        } else {
            /* Multihosts should not be tried if we know that content is not yet downloadable! */
            return !contentHasNotYetBeenReleased(downloadLink) && isMOCHUrlOnly(downloadLink);
        }
    }

    private static Object LOCK = new Object();

    public void login(Browser br, final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                prepBR(br);
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    /* Try to avoid login captcha at all cost! */
                    br.setCookies(account.getHoster(), cookies);
                    br.getPage("http://ma." + account.getHoster() + "/home/");
                    if (br.containsHTML(html_loggedin)) {
                        account.saveCookies(br.getCookies(account.getHoster()), "");
                        return;
                    }
                    br = prepBR(new Browser());
                }
                br.getPage("http://ma.brazzers.com/access/login/");
                final DownloadLink dlinkbefore = this.getDownloadLink();
                String postData = "username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass());
                if (br.containsHTML("google\\.com/recaptcha")) {
                    if (dlinkbefore == null) {
                        this.setDownloadLink(new DownloadLink(this, "Account", account.getHoster(), "http://" + account.getHoster(), true));
                    }
                    final Recaptcha rc = new Recaptcha(br, this);
                    rc.findID();
                    rc.load();
                    final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                    final String c = getCaptchaCode("recaptcha", cf, this.getDownloadLink());
                    postData += "&recaptcha_challenge_field=" + Encoding.urlEncode(rc.getChallenge());
                    postData += "&recaptcha_response_field=" + Encoding.urlEncode(c);
                }
                br.postPage("/access/submit/", postData);
                final Form continueform = br.getFormbyKey("response");
                if (continueform != null) {
                    /* Redirect from probiller.com to main website --> Login complete */
                    br.submitForm(continueform);
                }
                if (br.getCookie(account.getHoster(), "login_usr") == null || !br.containsHTML(html_loggedin)) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.saveCookies(br.getCookies(account.getHoster()), "");
                if (dlinkbefore != null) {
                    this.setDownloadLink(dlinkbefore);
                }
            } catch (final PluginException e) {
                account.clearCookies("");
                throw e;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(this.br, account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        ai.setUnlimitedTraffic();
        /*
         * 2016-09-28: No way to verify premium status and/or expire date - I guess if an account works, it always has a subscription
         * (premium status) ...
         */
        account.setType(AccountType.PREMIUM);
        account.setConcurrentUsePossible(true);
        ai.setStatus("Premium account");
        account.setValid(true);
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        if (account.getType() == AccountType.FREE) {
            /* This should never happen! */
            doFree(link, FREE_RESUME, FREE_MAXCHUNKS, "account_free_directlink");
        } else {
            handleGeneralErrors();
            if (isMOCHUrlOnly(link)) {
                /* Only downloadable via multihoster - if a user owns a premiumaccount for this service he will usually never add such URLs! */
                logger.info("This url is only downloadable via MOCH account");
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            }
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            boolean resume = ACCOUNT_PREMIUM_RESUME;
            int maxchunks = ACCOUNT_PREMIUM_MAXCHUNKS;
            if (link.getDownloadURL().matches(type_premium_pic)) {
                /* Not needed for pictures / avoid connection issues. */
                resume = false;
                maxchunks = 1;
            }
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, resume, maxchunks);
            if (dl.getConnection().getContentType().contains("html")) {
                if (dl.getConnection().getResponseCode() == 403) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
                } else if (dl.getConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
                }
                logger.warning("The final dllink seems not to be a file!");
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            link.setProperty("premium_directlink", dllink);
            dl.startDownload();
        }
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return ACCOUNT_PREMIUM_MAXDOWNLOADS;
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     */
    protected boolean inValidate(final String s) {
        if (s == null || s.matches("\\s+") || s.equals("")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return FREE_MAXDOWNLOADS;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.PornPortal;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}