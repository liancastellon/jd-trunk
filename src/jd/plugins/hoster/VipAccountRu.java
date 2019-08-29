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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "vip-account.ru" }, urls = { "" })
public class VipAccountRu extends PluginForHost {
    /* Connection limits */
    private static final boolean                           ACCOUNT_PREMIUM_RESUME       = true;
    private static final int                               ACCOUNT_PREMIUM_MAXCHUNKS    = 0;
    private static final int                               ACCOUNT_PREMIUM_MAXDOWNLOADS = 20;
    private final String                                   default_UA                   = "JDownloader";
    private final String                                   html_loggedin                = "action=logout\"";
    private static AtomicReference<String>                 agent                        = new AtomicReference<String>(null);
    private static Object                                  LOCK                         = new Object();
    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap           = new HashMap<Account, HashMap<String, Long>>();
    private Account                                        currAcc                      = null;
    private DownloadLink                                   currDownloadLink             = null;

    public VipAccountRu(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://vip-account.ru/");
    }

    @Override
    public String getAGBLink() {
        return "http://vip-account.ru/";
    }

    private Browser newBrowser() {
        br = new Browser();
        br.setCookiesExclusive(true);
        br.getHeaders().put("User-Agent", default_UA);
        br.setFollowRedirects(true);
        return br;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws PluginException {
        return AvailableStatus.UNCHECKABLE;
    }

    private void setConstants(final Account acc, final DownloadLink dl) {
        this.currAcc = acc;
        this.currDownloadLink = dl;
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) throws Exception {
        if (account == null) {
            /* without account its not possible to download the link */
            return false;
        }
        return true;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        /* handle premium should never be called */
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        this.br = newBrowser();
        setConstants(account, link);
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap != null) {
                Long lastUnavailable = unavailableMap.get(link.getHost());
                if (lastUnavailable != null && System.currentTimeMillis() < lastUnavailable) {
                    final long wait = lastUnavailable - System.currentTimeMillis();
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Host is temporarily unavailable via " + this.getHost(), wait);
                } else if (lastUnavailable != null) {
                    unavailableMap.remove(link.getHost());
                    if (unavailableMap.size() == 0) {
                        hostUnavailableMap.remove(account);
                    }
                }
            }
        }
        login(account, false);
        String dllink = checkDirectLink(link, this.getHost() + "directlink");
        if (dllink == null) {
            this.getAPISafe("http://vip-account.ru/engine/ajax/check/core.php?url=" + Encoding.urlEncode(link.getDownloadURL()));
            dllink = this.br.getRegex("\"links\":\\[\"(http:[^<>\"]*?)\"\\]").getMatch(0);
            if (dllink == null) {
                /* Should never happen */
                handleErrorRetries("dllinknull", 5, 2 * 60 * 1000l);
            }
            dllink = dllink.replace("\\", "");
        }
        handleDL(account, link, dllink);
    }

    @SuppressWarnings("deprecation")
    private void handleDL(final Account account, final DownloadLink link, final String dllink) throws Exception {
        link.setProperty(this.getHost() + "directlink", dllink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, ACCOUNT_PREMIUM_RESUME, ACCOUNT_PREMIUM_MAXCHUNKS);
        final String contenttype = dl.getConnection().getContentType();
        if (contenttype.contains("html")) {
            br.followConnection();
            updatestatuscode();
            handleAPIErrors(this.br);
            handleErrorRetries("unknowndlerror", 5, 2 * 60 * 1000l);
        }
        this.dl.startDownload();
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = br2.openHeadConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return dllink;
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        setConstants(account, null);
        this.br = newBrowser();
        final AccountInfo ai = new AccountInfo();
        login(account, true);
        br.getPage("/user/" + Encoding.urlEncode(account.getUser()) + "/");
        final boolean ispremium = this.br.containsHTML(">Группа:</span> Vip </li>|Vip[\t\n\r ]*?</li>");
        long trafficleft = 0;
        if (ispremium) {
            br.getPage("/vip_check/");
            String trafficleft_str = this.br.getRegex("class=\"points\">(\\d+(?:\\.\\d+)?)<").getMatch(0);
            String trafficleft_str_unit = this.br.getRegex("class=\"points\">\\d+(?:\\.\\d+)?</span> ([A-Za-z]+)").getMatch(0);
            if (trafficleft_str_unit == null) {
                trafficleft_str_unit = "GB";
            }
            if (trafficleft_str != null) {
                trafficleft_str += trafficleft_str_unit;
                trafficleft = SizeFormatter.getSize(trafficleft_str);
            }
        }
        /*
         * Free users = Accept them but set zero traffic left.
         */
        if (!ispremium || trafficleft == 0) {
            account.setType(AccountType.FREE);
            ai.setStatus("Registered (free) account");
            ai.setTrafficLeft(0);
        } else {
            account.setType(AccountType.PREMIUM);
            account.setMaxSimultanDownloads(ACCOUNT_PREMIUM_MAXDOWNLOADS);
            ai.setStatus("Premium account");
            ai.setTrafficLeft(trafficleft);
        }
        account.setValid(true);
        this.getAPISafe("/");
        final ArrayList<String> supportedHosts = new ArrayList<String>();
        final String hosttexts[] = br.getRegex("<span style=\"font-weight: 700;\">([^<>\"]+)").getColumn(0);
        for (String hosttext : hosttexts) {
            hosttext = hosttext.toLowerCase();
            final String[] domains = hosttext.toLowerCase().split(",");
            for (String domain : domains) {
                domain = domain.trim();
                supportedHosts.add(domain);
            }
            ai.setMultiHostSupport(this, supportedHosts);
        }
        return ai;
    }

    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                /* Load cookies */
                br.setCookiesExclusive(true);
                this.br = newBrowser();
                final Cookies cookies = account.loadCookies("");
                boolean loggedin = false;
                if (cookies != null) {
                    this.br.setCookies(this.getHost(), cookies);
                    /* Even though login is forced first check if our cookies are still valid --> If not, force login! */
                    br.getPage("https://vip-account.ru/vip_check/");
                    loggedin = br.containsHTML(html_loggedin);
                }
                if (!loggedin) {
                    String postData = "login=submit&login_name=" + Encoding.urlEncode(currAcc.getUser()) + "&login_password=" + Encoding.urlEncode(currAcc.getPass());
                    this.postAPISafe("https://vip-account.ru/", postData);
                    final String userLanguage = System.getProperty("user.language");
                    if (!this.br.containsHTML(html_loggedin)) {
                        if ("de".equalsIgnoreCase(userLanguage)) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername/Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                }
                account.saveCookies(this.br.getCookies(this.getHost()), "");
            } catch (final PluginException e) {
                account.clearCookies("");
                throw e;
            }
        }
    }

    private void tempUnavailableHoster(final long timeout) throws PluginException {
        if (this.currDownloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unable to handle this errorcode!");
        }
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(this.currAcc);
            if (unavailableMap == null) {
                unavailableMap = new HashMap<String, Long>();
                hostUnavailableMap.put(this.currAcc, unavailableMap);
            }
            unavailableMap.put(this.currDownloadLink.getHost(), (System.currentTimeMillis() + timeout));
        }
        throw new PluginException(LinkStatus.ERROR_RETRY);
    }

    private void getAPISafe(final String accesslink) throws IOException, PluginException {
        br.getPage(accesslink);
        updatestatuscode();
        handleAPIErrors(this.br);
    }

    private void postAPISafe(final String accesslink, final String postdata) throws IOException, PluginException {
        br.postPage(accesslink, postdata);
        updatestatuscode();
        handleAPIErrors(this.br);
    }

    /** For future changes */
    private void updatestatuscode() {
    }

    /** For future changes */
    private void handleAPIErrors(final Browser br) throws PluginException {
        // String statusMessage = null;
        // try {
        // switch (statuscode) {
        // case 0:
        // /* Everything ok */
        // break;
        // case 1:
        // statusMessage = "";
        // tempUnavailableHoster(5 * 60 * 1000l);
        // break;
        // default:
        // handleErrorRetries("unknown_error_state", 50, 2 * 60 * 1000l);
        // }
        // } catch (final PluginException e) {
        // logger.info(NICE_HOST + ": Exception: statusCode: " + statuscode + " statusMessage: " + statusMessage);
        // throw e;
        // }
    }

    /**
     * Is intended to handle out of date errors which might occur seldom by re-tring a couple of times before we temporarily remove the host
     * from the host list.
     *
     * @param error
     *            : The name of the error
     * @param maxRetries
     *            : Max retries before out of date error is thrown
     */
    private void handleErrorRetries(final String error, final int maxRetries, final long waittime) throws PluginException {
        int timesFailed = this.currDownloadLink.getIntegerProperty(this.getHost() + "failedtimes_" + error, 0);
        this.currDownloadLink.getLinkStatus().setRetryCount(0);
        if (timesFailed <= maxRetries) {
            logger.info(this.getHost() + ": " + error + " -> Retrying");
            timesFailed++;
            this.currDownloadLink.setProperty(this.getHost() + "failedtimes_" + error, timesFailed);
            throw new PluginException(LinkStatus.ERROR_RETRY, error);
        } else {
            this.currDownloadLink.setProperty(this.getHost() + "failedtimes_" + error, Property.NULL);
            logger.info(this.getHost() + ": " + error + " -> Disabling current host");
            tempUnavailableHoster(waittime);
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}