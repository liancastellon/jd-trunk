//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import jd.PluginWrapper;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.requests.GetRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.SpinnerValidator;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.plugins.config.TakeValueFromSubconfig;
import org.jdownloader.translate._JDT;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "1fichier.com" }, urls = { "https?://(?!www\\.)[a-z0-9]+\\.(?:dl4free\\.com|alterupload\\.com|cjoint\\.net|desfichiers\\.com|dfichiers\\.com|megadl\\.fr|mesfichiers\\.org|piecejointe\\.net|pjointe\\.com|tenvoi\\.com|1fichier\\.com)/?|https?://(?:www\\.)?(?:dl4free\\.com|alterupload\\.com|cjoint\\.net|desfichiers\\.com|dfichiers\\.com|megadl\\.fr|mesfichiers\\.org|piecejointe\\.net|pjointe\\.com|tenvoi\\.com|1fichier\\.com)/\\?[a-z0-9]+" })
public class OneFichierCom extends PluginForHost {
    private final String         HTML_PASSWORDPROTECTED       = "(This file is Password Protected|Ce fichier est protégé par mot de passe|access with a password)";
    private final String         PROPERTY_FREELINK            = "freeLink";
    private final String         PROPERTY_HOTLINK             = "hotlink";
    private final String         PROPERTY_PREMLINK            = "premLink";
    private final String         PREFER_RECONNECT             = "PREFER_RECONNECT";
    private final String         PREFER_SSL                   = "PREFER_SSL";
    private static final String  MAINPAGE                     = "https://1fichier.com/";
    private boolean              pwProtected                  = false;
    private DownloadLink         currDownloadLink             = null;
    /* Max total connections for premium = 30 (RE: admin, updated 07.03.2019) */
    private static final boolean resume_account_premium       = true;
    private static final int     maxchunks_account_premium    = -3;
    private static final int     maxdownloads_account_premium = 10;
    /* 2015-07-10: According to admin, resume is free mode is not possible anymore. On attempt this will lead to 404 server error! */
    private static final int     maxchunks_free               = 1;
    private static final boolean resume_free                  = true;
    private static final int     maxdownloads_free            = 1;
    /*
     * Settings for hotlinks - basically such links are created by premium users so free users can download them without limits (same limits
     * as premium users).
     */
    private static final boolean resume_free_hotlink          = true;
    private static final int     maxchunks_free_hotlink       = -4;

    public OneFichierCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www.1fichier.com/en/register.pl");
    }

    @Override
    public void init() {
        Browser.setRequestIntervalLimitGlobal(this.getHost(), 2000);
    }

    private String correctProtocol(final String input) {
        return input.replaceFirst("http://", "https://");
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        // link + protocol correction
        String url = correctProtocol(link.getDownloadURL());
        // Remove everything after the domain
        String linkID;
        if (link.getDownloadURL().matches("https?://[a-z0-9\\.]+(/|$)")) {
            final String[] idhostandName = new Regex(url, "(https?://)((?!www\\.).*?)\\.(.*?)(/|$)").getRow(0);
            if (idhostandName != null) {
                link.setUrlDownload(idhostandName[0] + idhostandName[2] + "/?" + idhostandName[1]);
                linkID = getHost() + "://" + idhostandName[1];
                link.setLinkID(linkID);
            }
        } else {
            linkID = getHost() + "://" + new Regex(url, "([a-z0-9]+)$").getMatch(0);
            link.setLinkID(linkID);
        }
    }

    private void setConstants(final Account acc, final DownloadLink dl) {
        this.currDownloadLink = dl;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean checkLinks(final DownloadLink[] urls) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        try {
            final Browser br = new Browser();
            br.setAllowedResponseCodes(503);
            br.getHeaders().put("User-Agent", "");
            br.getHeaders().put("Accept", "");
            br.getHeaders().put("Accept-Language", "");
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            while (true) {
                if (this.isAbort()) {
                    logger.info("User stopped downloads --> Stepping out of loop");
                    throw new PluginException(LinkStatus.ERROR_RETRY, "User aborted download");
                }
                links.clear();
                while (true) {
                    /* we test 100 links at once */
                    if (index == urls.length || links.size() > 100) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                for (final DownloadLink dl : links) {
                    sb.append("links[]=");
                    sb.append(Encoding.urlEncode(dl.getDownloadURL()));
                    sb.append("&");
                }
                // remove last &
                sb.deleteCharAt(sb.length() - 1);
                br.postPageRaw(correctProtocol("http://1fichier.com/check_links.pl"), sb.toString());
                checkConnection(br);
                for (final DownloadLink dllink : links) {
                    // final String addedLink = dllink.getDownloadURL();
                    final String addedlink_id = this.getFID(dllink);
                    if (addedlink_id == null) {
                        // invalid uid
                        dllink.setAvailable(false);
                    } else if (br.containsHTML(addedlink_id + "[^;]*;;;(NOT FOUND|BAD LINK)")) {
                        dllink.setAvailable(false);
                        dllink.setName(addedlink_id);
                    } else if (br.containsHTML(addedlink_id + "[^;]*;;;PRIVATE")) {
                        dllink.setProperty("privatelink", true);
                        dllink.setAvailable(true);
                        dllink.setName(addedlink_id);
                    } else {
                        final String[] linkInfo = br.getRegex(addedlink_id + "[^;]*;([^;]+);(\\d+)").getRow(0);
                        if (linkInfo.length != 2) {
                            logger.warning("Linkchecker for 1fichier.com is broken!");
                            return false;
                        }
                        dllink.setProperty("privatelink", false);
                        dllink.setAvailable(true);
                        /* Trust API information. */
                        dllink.setFinalFileName(Encoding.htmlDecode(linkInfo[0]));
                        dllink.setDownloadSize(SizeFormatter.getSize(linkInfo[1]));
                    }
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            return false;
        }
        return true;
    }

    /* Old linkcheck removed AFTER revision 29396 */
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        /* Offline links should also get nice filenames. */
        correctDownloadLink(link);
        checkLinks(new DownloadLink[] { link });
        prepareBrowser(br);
        if (!link.isAvailabilityStatusChecked()) {
            return AvailableStatus.UNCHECKED;
        }
        if (link.isAvailabilityStatusChecked() && !link.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public int getMaxSimultanDownload(DownloadLink link, Account account) {
        if (account == null && (link != null && link.getProperty(PROPERTY_HOTLINK, null) != null)) {
            return Integer.MAX_VALUE;
        }
        return super.getMaxSimultanDownload(link, account);
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        this.setConstants(null, downloadLink);
        requestFileInformation(downloadLink);
        if (checkShowFreeDialog(getHost())) {
            showFreeDialog(getHost());
        }
        doFree(null, downloadLink);
    }

    private String regex_dllink_middle = "align:middle\">\\s+<a href=(\"|')(https?://[a-zA-Z0-9_\\-]+\\.(1fichier|desfichiers)\\.com/[a-zA-Z0-9]+.*?)\\1";

    public void doFree(final Account account, final DownloadLink downloadLink) throws Exception, PluginException {
        checkDownloadable(account);
        // to prevent wasteful requests.
        int i = 0;
        /* The following code will cover saved hotlinks */
        String dllink = downloadLink.getStringProperty(PROPERTY_HOTLINK, null);
        if (dllink != null) {
            dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, dllink, resume_free_hotlink, maxchunks_free_hotlink);
            if (dl.getConnection().getContentType().contains("html")) {
                dl.getConnection().disconnect();
                // link has expired... but it could be for any reason! dont care!
                // clear saved final link
                downloadLink.setProperty(PROPERTY_HOTLINK, Property.NULL);
                br = new Browser();
                prepareBrowser(br);
            } else {
                /* resume download */
                downloadLink.setProperty(PROPERTY_HOTLINK, dllink);
                dl.startDownload();
                return;
            }
        }
        // retry/resume of cached free link!
        dllink = downloadLink.getStringProperty(PROPERTY_FREELINK, null);
        if (dllink != null) {
            dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, dllink, resume_free, maxchunks_free);
            if (dl.getConnection().getContentType().contains("html")) {
                dl.getConnection().disconnect();
                // link has expired... but it could be for any reason! dont care!
                // clear saved final link
                downloadLink.setProperty(PROPERTY_FREELINK, Property.NULL);
                br = new Browser();
                prepareBrowser(br);
            } else {
                /* resume download */
                downloadLink.setProperty(PROPERTY_FREELINK, dllink);
                dl.startDownload();
                return;
            }
        }
        // this covers virgin downloads which end up been hot link-able...
        dllink = getDownloadlinkNEW(downloadLink);
        dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, dllink, resume_free_hotlink, maxchunks_free_hotlink);
        if (!dl.getConnection().getContentType().contains("html")) {
            /* resume download */
            downloadLink.setProperty(PROPERTY_HOTLINK, dllink);
            dl.startDownload();
            return;
        }
        // not hotlinkable.. standard free link...
        // html yo!
        br.followConnection();
        checkConnection(br);
        dllink = null;
        br.setFollowRedirects(false);
        // use the English page, less support required
        boolean retried = false;
        while (true) {
            i++;
            // redirect log 2414663166931
            if (i > 1) {
                br.setFollowRedirects(true);
                // no need to do this link twice as it's been done above.
                br.getPage(this.getDownloadlinkNEW(downloadLink));
                br.setFollowRedirects(false);
            }
            errorHandling(downloadLink, account, br);
            if (pwProtected || br.containsHTML(HTML_PASSWORDPROTECTED)) {
                handlePassword();
                dllink = br.getRedirectLocation();
                if (dllink == null) {
                    // Link; 8182111113541.log; 464810; jdlog://8182111113541
                    dllink = br.getRegex(regex_dllink_middle).getMatch(1);
                    if (dllink == null) {
                        logger.warning("Failed to find final downloadlink after password handling success");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
                logger.info("Successfully went through the password handling");
                break;
            } else {
                // base > submit:Free Download > submit:Show the download link + t:35140198 == link
                final Browser br2 = br.cloneBrowser();
                // final Form a1 = br2.getForm(0);
                // if (a1 == null) {
                // throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                // }
                // a1.remove(null);
                br2.getHeaders().put("Content-Type", "application/x-www-form-urlencoded");
                sleep(2000, downloadLink);
                // br2.submitForm(a1);
                br2.postPageRaw(br.getURL(), "");
                errorHandling(downloadLink, account, br2);
                if (br2.containsHTML("not possible to unregistered users")) {
                    final Account aa = AccountController.getInstance().getValidAccount(this);
                    if (aa != null) {
                        try {
                            synchronized (aa) {
                                login(aa, true);
                                ensureSiteLogin(aa);
                            }
                        } catch (final PluginException e) {
                            logger.log(e);
                        }
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
                    }
                }
                dllink = br2.getRedirectLocation();
                if (dllink == null) {
                    dllink = br2.getRegex(regex_dllink_middle).getMatch(1);
                }
                if (dllink == null) {
                    final Form a2 = br2.getForm(0);
                    if (a2 == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    a2.remove("save");
                    final Browser br3 = br.cloneBrowser();
                    br3.getHeaders().put("Content-Type", "application/x-www-form-urlencoded");
                    sleep(2000, downloadLink);
                    br3.submitForm(a2);
                    errorHandling(downloadLink, account, br3);
                    if (dllink == null) {
                        dllink = br3.getRedirectLocation();
                    }
                    if (dllink == null) {
                        dllink = br3.getRegex("<a href=\"([^<>\"]*?)\"[^<>]*?>Click here to download").getMatch(0);
                    }
                    if (dllink == null) {
                        dllink = br3.getRegex("window\\.location\\s*=\\s*('|\")(https?://[a-zA-Z0-9_\\-]+\\.(1fichier|desfichiers)\\.com/[a-zA-Z0-9]+/.*?)\\1").getMatch(1);
                    }
                    if (dllink == null) {
                        String wait = br3.getRegex(" var count = (\\d+);").getMatch(0);
                        if (wait != null && retried == false) {
                            retried = true;
                            sleep(1000 * Long.parseLong(wait), downloadLink);
                            continue;
                        }
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
            }
            if (dllink != null) {
                break;
            }
        }
        br.setFollowRedirects(true);
        dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, dllink, resume_free, maxchunks_free);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            errorHandling(downloadLink, account, br);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty(PROPERTY_FREELINK, dllink);
        dl.startDownload();
    }

    private void errorHandling(final DownloadLink downloadLink, final Account account, final Browser ibr) throws Exception {
        long responsecode = 200;
        if (ibr.getHttpConnection() != null) {
            responsecode = ibr.getHttpConnection().getResponseCode();
        }
        if (ibr.containsHTML(">IP Locked|>Will be unlocked within 1h.")) {
            // jdlog://2958376935451/ https://board.jdownloader.org/showthread.php?t=67204&page=2
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "IP will be locked 1h", 60 * 60 * 1000l);
        } else if (responsecode == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 15 * 60 * 1000l);
        } else if (responsecode == 404) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
        } else if (ibr.containsHTML(">\\s*File not found !\\s*<br/>It has could be deleted by its owner\\.\\s*<")) {
            // api linkchecking can be out of sync (wrong)
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (ibr.containsHTML("Warning ! Without subscription, you can only download one file at|<span style=\"color:red\">Warning\\s*!\\s*</span>\\s*<br/>Without subscription, you can only download one file at a time\\.\\.\\.")) {
            // jdlog://3278035891641 jdlog://7543779150841
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Too many downloads - wait before starting new downloads", 3 * 60 * 1000l);
        } else if (ibr.containsHTML("<h1>Select files to send :</h1>")) {
            // for some reason they linkcheck correct, then show upload page. re: jdlog://3895673179241
            // https://svn.jdownloader.org/issues/65003
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Hoster issue?", 60 * 60 * 1000l);
        } else if (ibr.containsHTML(">Software error:<")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Software error'", 10 * 60 * 1000l);
        } else if (ibr.containsHTML(">Connexion à la base de données impossible<|>Can\\'t connect DB")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Internal database error", 5 * 60 * 1000l);
        } else if (ibr.containsHTML(">Votre adresse IP ouvre trop de connexions vers le serveur")) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Too many connections - wait before starting new downloads", 3 * 60 * 1000l);
        } else if (ibr.containsHTML("not possible to free unregistered users")) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        } else if (ibr.containsHTML("Your account will be unlock")) {
            if (account != null) {
                throw new AccountUnavailableException("Locked for security reasons", 60 * 60 * 1000l);
            } else {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "IP blocked for security reasons", 60 * 60 * 1000l);
            }
        }
        errorIpBlockedHandling(ibr);
    }

    private void errorIpBlockedHandling(final Browser br) throws PluginException {
        String waittime = br.getRegex("you must wait (at least|up to) (\\d+) minutes between each downloads").getMatch(1);
        if (waittime == null) {
            waittime = br.getRegex(">You must wait (\\d+) minutes").getMatch(0);
        }
        boolean isBlocked = waittime != null;
        isBlocked |= br.containsHTML("/>Téléchargements en cours");
        isBlocked |= br.containsHTML("En téléchargement standard, vous ne pouvez télécharger qu\\'un seul fichier");
        isBlocked |= br.containsHTML(">veuillez patienter avant de télécharger un autre fichier");
        isBlocked |= br.containsHTML(">You already downloading (some|a) file");
        isBlocked |= br.containsHTML(">You can download only one file at a time");
        isBlocked |= br.containsHTML(">Please wait a few seconds before downloading new ones");
        isBlocked |= br.containsHTML(">You must wait for another download");
        isBlocked |= br.containsHTML("Without premium status, you can download only one file at a time");
        isBlocked |= br.containsHTML("Without Premium, you can only download one file at a time");
        isBlocked |= br.containsHTML("Without Premium, you must wait between downloads");
        // <div style="text-align:center;margin:auto;color:red">Warning ! Without premium status, you must wait between each
        // downloads<br/>Your last download finished 05 minutes ago</div>
        isBlocked |= br.containsHTML("you must wait between each downloads");
        // <div style="text-align:center;margin:auto;color:red">Warning ! Without premium status, you must wait 15 minutes between each
        // downloads<br/>You must wait 15 minutes to download again or subscribe to a premium offer</div>
        isBlocked |= br.containsHTML("you must wait \\d+ minutes between each downloads<");
        if (isBlocked) {
            final boolean preferReconnect = PluginJsonConfig.get(OneFichierConfigInterface.class).isPreferReconnectEnabled();
            if (waittime != null && preferReconnect) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, Integer.parseInt(waittime) * 60 * 1001l);
            } else if (waittime != null && Integer.parseInt(waittime) >= 10) {
                /* High waittime --> Reconnect */
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, Integer.parseInt(waittime) * 60 * 1001l);
            } else if (preferReconnect) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, 5 * 60 * 1000l);
            } else if (waittime != null) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait between download, Reconnect is disabled in plugin settings", Integer.parseInt(waittime) * 60 * 1001l);
            } else {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait between download, Reconnect is disabled in plugin settings", 5 * 60 * 1001);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        setConstants(account, null);
        AccountInfo ai = new AccountInfo();
        if (account.getUser() == null || !account.getUser().matches(".+@.+")) {
            ai.setStatus(":\r\nYou need to use Email as username!");
            account.setValid(false);
            return ai;
        }
        br.setAllowedResponseCodes(503, 403);
        br = new Browser();
        login(account, true);
        /* And yet another workaround for broken API case ... */
        br.getPage("https://" + this.getHost() + "/en/console/index.pl");
        final boolean isPremium = br.containsHTML(">\\s*Premium\\s*(offer)\\s*Account\\s*<");
        final boolean isAccess = br.containsHTML(">\\s*Access\\s*(offer)\\s*Account\\s*<");
        // final boolean isFree = br.containsHTML(">\\s*Free\\s*(offer)\\s*Account\\s*<");
        if (isPremium || isAccess) {
            final GetRequest get = new GetRequest("https://" + this.getHost() + "/en/console/abo.pl");
            get.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br.setFollowRedirects(true);
            br.getPage(get);
            final String validUntil = br.getRegex("subscription is valid until\\s*<[^<]*>(\\d+-\\d+-\\d+)").getMatch(0);
            if (validUntil != null) {
                final long validUntilTimestamp = TimeFormatter.getMilliSeconds(validUntil, "yyyy'-'MM'-'dd", Locale.ENGLISH);
                if (validUntilTimestamp > 0) {
                    ai.setValidUntil(validUntilTimestamp + (24 * 60 * 60 * 1000l));
                }
            }
            // final String traffic=br.getRegex("Your account have ([^<>\"]*?) of CDN credits").getMatch(0);
            if (isPremium) {
                ai.setStatus("Premium Account");
            } else {
                ai.setStatus("Access Account");
            }
            ai.setUnlimitedTraffic();
            account.setType(AccountType.PREMIUM);
            account.setMaxSimultanDownloads(maxdownloads_account_premium);
            account.setConcurrentUsePossible(true);
        } else {
            account.setType(AccountType.FREE);
            account.setMaxSimultanDownloads(maxdownloads_free);
            account.setConcurrentUsePossible(false);
            account.setProperty("freeAPIdisabled", true);
            final GetRequest get = new GetRequest("https://" + this.getHost() + "/en/console/params.pl");
            get.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br.setFollowRedirects(true);
            br.getPage(get);
            final String credits = br.getRegex(">\\s*Your account have ([^<>\"]*?) of (?:Hotlinks|direct download) credits").getMatch(0);
            final boolean useOwnCredits = StringUtils.equalsIgnoreCase("checked", br.getRegex("<input\\s*type=\"checkbox\"\\s*checked=\"(.*?)\"\\s*name=\"own_credit\"").getMatch(0));
            if (credits != null && useOwnCredits) {
                ai.setStatus("Free Account (Credits available(hotlink enabled))");
                ai.setTrafficLeft(SizeFormatter.getSize(credits));
            } else {
                if (credits != null) {
                    ai.setStatus("Free Account (Credits available(hotlink disabled))");
                } else {
                    ai.setStatus("Free Account");
                }
                ai.setUnlimitedTraffic();
            }
        }
        return ai;
    }

    private void checkConnection(final Browser br) throws PluginException {
        if (br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 503 && br.containsHTML(">\\s*Our services are in maintenance\\. Please come back after")) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Hoster is in maintenance mode!", 20 * 60 * 1000l);
        }
    }

    private boolean checkSID(Browser br) {
        final String sid = br.getCookie(MAINPAGE, "SID");
        return !StringUtils.isEmpty(sid) && !StringUtils.equalsIgnoreCase(sid, "deleted");
    }

    private void login(final Account account, final boolean force) throws Exception {
        synchronized (account) {
            try {
                /* Load cookies */
                prepareBrowser(br);
                Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    br.setCookies(MAINPAGE, cookies);
                    if (!force) {
                        setBasicAuthHeader(br, account);
                        return;
                    } else {
                        br.getPage("https://1fichier.com/console/index.pl");
                        if (!checkSID(br)) {
                            cookies = null;
                            br.clearCookies(MAINPAGE);
                        }
                    }
                }
                if (cookies == null) {
                    logger.info("Using site login because API is either wrong or no free credits...");
                    br.postPage("https://1fichier.com/login.pl", "lt=on&valider=Send&mail=" + Encoding.urlEncode(account.getUser()) + "&pass=" + Encoding.urlEncode(account.getPass()));
                    if (!checkSID(br)) {
                        if (br.containsHTML("following many identification errors") && br.containsHTML("Your account will be unlock")) {
                            throw new AccountUnavailableException("Your account will be unlock within 1 hour", 60 * 60 * 1000l);
                        }
                        logger.info("Username/Password also invalid via site login!");
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.saveCookies(br.getCookies(getHost()), "");
                setBasicAuthHeader(br, account);
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    @Override
    public String getAGBLink() {
        return "http://www.1fichier.com/en/cgu.html";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return maxdownloads_free;
    }

    @Override
    protected long getStartIntervall(final DownloadLink downloadLink, final Account account) {
        if (account == null || !AccountType.PREMIUM.equals(account.getType()) || downloadLink == null) {
            return super.getStartIntervall(downloadLink, account);
        } else {
            final long knownDownloadSize = downloadLink.getKnownDownloadSize();
            if (knownDownloadSize > 0 && knownDownloadSize <= 50 * 1024 * 1024) {
                final int wait = PluginJsonConfig.get(OneFichierConfigInterface.class).getSmallFilesWaitInterval();
                // avoid IP block because of too many downloads in short time
                return Math.max(0, wait * 1000);
            } else {
                return super.getStartIntervall(downloadLink, account);
            }
        }
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        setConstants(account, link);
        requestFileInformation(link);
        checkDownloadable(account);
        br = new Browser();
        if (AccountType.FREE.equals(account.getType()) && account.getBooleanProperty("freeAPIdisabled")) {
            /**
             * Only used if the API fails and is wrong but that usually doesn't happen!
             */
            synchronized (account) {
                login(account, false);
                ensureSiteLogin(account);
            }
            doFree(account, link);
            return;
        }
        String dllink = link.getStringProperty("directlink");
        if (dllink != null) {
            try {
                logger.info("Connecting to cached dllink: " + dllink);
                dl = new jd.plugins.BrowserAdapter().openDownload(br, link, dllink, resume_account_premium, maxchunks_account_premium);
            } catch (final ConnectException c) {
                logger.info("Download failed because connection timed out, NOT a JD issue!");
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Connection timed out", 60 * 60 * 1000l);
            } catch (final Exception e) {
                logger.info("Download failed because: " + e.getMessage());
                throw e;
            }
            if (dl.getConnection().getContentType().contains("html") || dl.getConnection().getLongContentLength() == -1 || !dl.getConnection().isOK()) {
                dllink = null;
                try {
                    dl.getConnection().disconnect();
                } catch (final Throwable e) {
                }
                dl = null;
                br = new Browser();
            }
        }
        if (dllink == null) {
            // for some silly reason we have reverted from api to webmethod, so we need cookies!. 20150201
            br = new Browser();
            synchronized (account) {
                login(account, false);
                ensureSiteLogin(account);
            }
            br.setFollowRedirects(false);
            br.getPage(link.getDownloadURL());
            // error checking, offline links can happen here.
            errorHandling(link, account, br);
            dllink = br.getRedirectLocation();
            if (pwProtected || br.containsHTML(HTML_PASSWORDPROTECTED)) {
                handlePassword();
                /*
                 * The users' 'direct download' setting has no effect on the password handling so we should always get a redirect to the
                 * final downloadlink after having entered the correct download password (for premium users).
                 */
                dllink = br.getRedirectLocation();
                if (dllink == null) {
                    dllink = br.getRegex(regex_dllink_middle).getMatch(1);
                    if (dllink == null) {
                        logger.warning("After successful password handling: Final downloadlink 'dllink' is null");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
                if (dllink.contains("login.pl")) { // jdlog://4209376935451/
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "login.pl?exp=1", 3 * 60 * 1000l);
                }
            }
            try {
                errorIpBlockedHandling(br);
            } catch (PluginException e) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Too many simultan downloads", 45 * 1000l);
            }
            if (dllink == null) {
                /* The link is always SSL - based on user setting it will redirect to either https or http. */
                String postData = "did=0&";
                postData += getSSLFormValue();
                br.postPage(link.getDownloadURL(), postData);
                dllink = br.getRedirectLocation();
                if (dllink == null) {
                    if (br.containsHTML("\">Warning \\! Without premium status, you can download only")) {
                        logger.info("Seems like this is no premium account or it's vot valid anymore -> Disabling it");
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if (br.containsHTML("You can use your account only for downloading from 1 Internet access at a time") || br.containsHTML("You can use your Premium account for downloading from 1 Internet access at a time") || br.containsHTML("You can use your account for downloading from 1 Internet access at a time")) {
                        logger.warning("Your using account on multiple IP addresses at once");
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "Account been used on another Internet connection", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                    } else {
                        logger.warning("Final downloadlink 'dllink' is null");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
            }
        }
        if (PluginJsonConfig.get(OneFichierConfigInterface.class).isPreferSSLEnabled() && dllink.startsWith("http://")) {
            dllink = dllink.replace("http://", "https://");
        }
        for (int i = 0; i != 2; i++) {
            if (dl == null || i > 0) {
                try {
                    logger.info("Connecting to dllink: " + dllink);
                    dl = new jd.plugins.BrowserAdapter().openDownload(br, link, dllink, resume_account_premium, maxchunks_account_premium);
                } catch (final ConnectException c) {
                    logger.info("Download failed because connection timed out, NOT a JD issue!");
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Connection timed out", 60 * 60 * 1000l);
                } catch (final Exception e) {
                    logger.info("Download failed because: " + e.getMessage());
                    throw e;
                }
                if (dl.getConnection().getContentType().contains("html")) {
                    if ("http://www.1fichier.com/?c=DB".equalsIgnoreCase(br.getURL())) {
                        dl.getConnection().disconnect();
                        if (i + 1 == 2) {
                            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Internal database error", 5 * 60 * 1000l);
                        }
                        continue;
                    }
                    logger.warning("The final dllink seems not to be a file!");
                    br.followConnection();
                    errorHandling(link, account, br);
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
            link.setProperty(PROPERTY_PREMLINK, dllink);
            dl.startDownload();
            return;
        }
    }

    private void setBasicAuthHeader(final Browser br, final Account account) {
        br.getHeaders().put("Authorization", "Basic " + Encoding.Base64Encode(account.getUser() + ":" + account.getPass()));
    }

    private static AtomicReference<String> lastSessionPassword = new AtomicReference<String>(null);

    private Form getPasswordForm() throws Exception {
        final Form ret = br.getFormbyKey("pass");
        if (ret == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else {
            ret.remove("save");
            if (!PluginJsonConfig.get(OneFichierConfigInterface.class).isPreferSSLEnabled()) {
                ret.put("dl_no_ssl", "on");
            }
            return ret;
        }
    }

    private void handlePassword() throws Exception {
        synchronized (lastSessionPassword) {
            logger.info("This link seems to be password protected, continuing...");
            Form pwForm = getPasswordForm();
            // if property is set use it over lastSessionPassword!
            String passCode = currDownloadLink.getDownloadPassword();
            if (passCode != null) {
                pwForm.put("pass", Encoding.urlEncode(passCode));
                br.submitForm(pwForm);
                if (!br.containsHTML(HTML_PASSWORDPROTECTED)) {
                    lastSessionPassword.set(passCode);
                    currDownloadLink.setDownloadPassword(passCode);
                    return;
                } else {
                    pwForm = getPasswordForm();
                    // nullify stored password
                    currDownloadLink.setDownloadPassword(null);
                }
            }
            // next lastSessionPassword
            passCode = lastSessionPassword.get();
            if (passCode != null) {
                pwForm.put("pass", Encoding.urlEncode(passCode));
                br.submitForm(pwForm);
                if (!br.containsHTML(HTML_PASSWORDPROTECTED)) {
                    lastSessionPassword.set(passCode);
                    currDownloadLink.setDownloadPassword(passCode);
                    return;
                } else {
                    pwForm = getPasswordForm();
                    // do no nullify... as it could work for another link.
                }
            }
            // last user input
            passCode = Plugin.getUserInput("Password?", currDownloadLink);
            if (passCode != null) {
                pwForm.put("pass", Encoding.urlEncode(passCode));
                br.submitForm(pwForm);
                if (!br.containsHTML(HTML_PASSWORDPROTECTED)) {
                    lastSessionPassword.set(passCode);
                    currDownloadLink.setDownloadPassword(passCode);
                    return;
                }
            }
            // nothing to nullify, just throw exception
            throw new PluginException(LinkStatus.ERROR_RETRY, JDL.L("plugins.hoster.onefichiercom.wrongpassword", "Password wrong!"));
        }
    }

    /* Returns postPage key + data based on the users' SSL preference. */
    private String getSSLFormValue() {
        String formdata;
        if (PluginJsonConfig.get(OneFichierConfigInterface.class).isPreferSSLEnabled()) {
            logger.info("User prefers download with SSL");
            formdata = "dlssl=SSL+Download";
        } else {
            logger.info("User prefers download without SSL");
            formdata = "dl=Download";
        }
        return formdata;
    }

    /** Returns an accessible downloadlink in the NEW format. */
    private String getDownloadlinkNEW(final DownloadLink dl) {
        final String host_of_current_downloadlink = Browser.getHost(dl.getDownloadURL());
        return "https://" + host_of_current_downloadlink + "/?" + getFID(dl);
    }

    /**
     * Makes sure that we're allowed to download a link. This function will also find out of a link is password protected.
     *
     * @throws Exception
     */
    private void checkDownloadable(Account account) throws Exception {
        if (this.currDownloadLink.getBooleanProperty("privatelink", false)) {
            logger.info("Link is PRIVATE --> Checking whether it really is PRIVATE or just password protected");
            br.getPage(this.getDownloadlinkNEW(this.currDownloadLink));
            if (br.containsHTML(this.HTML_PASSWORDPROTECTED)) {
                logger.info("Link is password protected");
                this.pwProtected = true;
            } else if (br.containsHTML("Access to download")) {
                logger.info("Download is possible");
            } else if (br.containsHTML("Your account will be unlock")) {
                if (account != null) {
                    throw new AccountUnavailableException("Locked for security reasons", 60 * 60 * 1000l);
                } else {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "IP blocked for security reasons", 60 * 60 * 1000l);
                }
            } else {
                logger.info("Link is PRIVATE");
                throw new PluginException(LinkStatus.ERROR_FATAL, "This link is private. You're not authorized to download it!");
            }
        }
    }

    /** This function is there to make sure that we're really logged in (handling without API). */
    private boolean ensureSiteLogin(Account account) throws Exception {
        br.getPage("https://1fichier.com/console/index.pl");
        if (!checkSID(br) || !br.containsHTML("id=\"fileTree\"")) {
            logger.info("Site login seems not to be valid anymore - trying to refresh cookie");
            if (account != null) {
                this.login(account, true);
                ensureSiteLogin(null);
                logger.info("Successfully refreshed login cookie");
            } else {
                if (br.containsHTML("For security reasons") && br.containsHTML("is temporarily locked")) {
                    throw new AccountUnavailableException("Locked for security reasons", 60 * 60 * 1000l);
                } else {
                    logger.warning("Failed to refresh login cookie");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
        } else {
            logger.info("Success: our login cookie is fine - no need to do anything");
        }
        return true;
    }

    /** Returns the file/link-ID of any given downloadLink. */
    @SuppressWarnings("deprecation")
    private String getFID(final DownloadLink dl) {
        String test = new Regex(dl.getDownloadURL(), "/\\?([a-z0-9]+)$").getMatch(0);
        if (test == null) {
            test = new Regex(dl.getDownloadURL(), "://(?!www\\.)([a-z0-9]+)\\.").getMatch(0);
            if (test != null && test.matches("www")) {
                test = null;
            }
        }
        return test;
    }

    public static interface OneFichierConfigInterface extends PluginConfigInterface {
        public static class OneFichierConfigInterfaceTranslation {
            public String getPreferReconnectEnabled_label() {
                return _JDT.T.lit_prefer_reconnect();
            }

            public String getPreferSSLEnabled_label() {
                return _JDT.T.lit_prefer_ssl();
            }

            public String getSmallFilesWaitInterval_label() {
                return "Wait x seconds for small files (smaller than 50 mbyte) to prevent IP block";
            }
        }

        public static final OneFichierConfigInterfaceTranslation TRANSLATION = new OneFichierConfigInterfaceTranslation();

        @AboutConfig
        @DefaultBooleanValue(false)
        @TakeValueFromSubconfig("PREFER_RECONNECT")
        boolean isPreferReconnectEnabled();

        void setPreferReconnectEnabled(boolean b);

        @AboutConfig
        @DefaultBooleanValue(true)
        @TakeValueFromSubconfig("PREFER_SSL")
        boolean isPreferSSLEnabled();

        void setPreferSSLEnabled(boolean b);

        @AboutConfig
        @DefaultIntValue(10)
        @SpinnerValidator(min = 0, max = 60)
        int getSmallFilesWaitInterval();

        void setSmallFilesWaitInterval(int i);
    }

    private void prepareBrowser(final Browser br) {
        if (br == null) {
            return;
        }
        br.setConnectTimeout(3 * 60 * 1000);
        br.setReadTimeout(3 * 60 * 1000);
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/48.0.2564.103 Safari/537.36");
        br.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        br.getHeaders().put("Accept-Language", "en-us,en;q=0.5");
        br.getHeaders().put("Pragma", null);
        br.getHeaders().put("Cache-Control", null);
        br.setCustomCharset("utf-8");
        // we want ENGLISH!
        br.setCookie(this.getHost(), "LG", "en");
        br.setAllowedResponseCodes(503);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}