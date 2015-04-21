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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.logging2.LogSource;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "premiumax.net" }, urls = { "REGEX_NOT_POSSIBLE_RANDOM-asdfasdfsadfsdgfd32423" }, flags = { 2 })
public class PremiumaxNet extends antiDDoSForHost {

    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap = new HashMap<Account, HashMap<String, Long>>();
    private static final String                            NOCHUNKS           = "NOCHUNKS";
    private static final String                            MAINPAGE           = "http://premiumax.net";
    private static final String                            NICE_HOST          = "premiumax.net";
    private static final String                            NICE_HOSTproperty  = "premiumaxnet";

    public PremiumaxNet(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.premiumax.net/premium.html");
    }

    @Override
    public String getAGBLink() {
        return "http://www.premiumax.net/more/terms-and-conditions.html";
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        final AccountInfo ac = new AccountInfo();
        br.setConnectTimeout(60 * 1000);
        br.setReadTimeout(60 * 1000);
        ac.setProperty("multiHostSupport", Property.NULL);
        // check if account is valid
        if (!login(account, true)) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername/Passwort oder Login Captcha falsch eingegeben!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or wrong login captcha input!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        getPage("http://www.premiumax.net/profile/");
        boolean is_freeaccount = false;
        final String expire = br.getRegex("<span>Premium until: </span><strong>([^<>\"]*?)</strong>").getMatch(0);
        if (expire != null) {
            ac.setValidUntil(TimeFormatter.getMilliSeconds(expire, "dd.MM.yyyy hh:mm", Locale.ENGLISH));
            ac.setStatus("Premium account");
            account.setMaxSimultanDownloads(-1);
            account.setConcurrentUsePossible(true);
        } else {
            ac.setStatus("Registered (free) user");
            is_freeaccount = true;
            account.setMaxSimultanDownloads(20);
            account.setConcurrentUsePossible(true);
        }
        ac.setUnlimitedTraffic();
        // now let's get a list of all supported hosts:
        getPage("http://www.premiumax.net/hosts.html");
        final String[] possible_domains = { "to", "de", "com", "net", "co.nz", "in", "co", "me", "biz", "ch", "pl", "us" };
        final ArrayList<String> supportedHosts = new ArrayList<String>();
        final String[] hostDomainsInfo = br.getRegex("(<img src=\"/assets/images/hosts/.*?)</tr>").getColumn(0);
        for (String hinfo : hostDomainsInfo) {
            String crippledhost = new Regex(hinfo, "images/hosts/([a-z0-9\\-\\.]+)\\.png\"").getMatch(0);
            if (crippledhost == null) {
                logger.warning("WTF");
            }
            crippledhost = crippledhost.trim();
            crippledhost = crippledhost.toLowerCase();
            final String[] imgs = new Regex(hinfo, "src=\"(tmpl/images/[^<>\"]*?)\"").getColumn(0);
            /* Apply supported hosts depending on account type */
            if (imgs != null && imgs.length >= 4 && imgs[3].equals("tmpl/images/ico_yes.png") && (!is_freeaccount && imgs[2].equals("tmpl/images/ico_yes.png") || is_freeaccount && imgs[1].equals("tmpl/images/ico_yes.png"))) {
                /* Go insane */
                for (final String possibledomain : possible_domains) {
                    final String full_possible_host = crippledhost + "." + possibledomain;
                    supportedHosts.add(full_possible_host);
                }
            }
        }
        ac.setMultiHostSupport(this, supportedHosts);
        return ac;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    /** no override to keep plugin compatible to old stable */
    public void handleMultiHost(final DownloadLink link, final Account acc) throws Exception {
        login(acc, true);
        String dllink = checkDirectLink(link, "premiumaxnetdirectlink");
        if (dllink == null) {
            br.getHeaders().put("Accept", "*/*");
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            postPage("http://www.premiumax.net/direct_link.html?rand=0." + System.currentTimeMillis(), "captcha=&key=indexKEY&urllist=" + Encoding.urlEncode(link.getDownloadURL()));
            if (br.containsHTML("temporary problem")) {
                logger.info("Current hoster is temporarily not available via premiumax.net -> Disabling it");
                tempUnavailableHoster(acc, link, 60 * 60 * 1000l);
            } else if (br.containsHTML("You do not have the rights to download from")) {
                logger.info("Current hoster is not available via this premiumax.net account -> Disabling it");
                tempUnavailableHoster(acc, link, 60 * 60 * 1000l);
            } else if (br.containsHTML("We do not support your link")) {
                logger.info("Current hoster is not supported by premiumax.net -> Disabling it");
                tempUnavailableHoster(acc, link, 3 * 60 * 60 * 1000l);
            } else if (br.containsHTML("You only can download")) {
                /* We're too fast - usually this should not happen */
                throw new PluginException(LinkStatus.ERROR_RETRY, "Too many connections active, try again in some seconds...");
            } else if (br.containsHTML("> Our server can\\'t connect to")) {
                logger.info(NICE_HOST + ": cantconnect");
                int timesFailed = link.getIntegerProperty(NICE_HOSTproperty + "timesfailed_cantconnect", 0);
                link.getLinkStatus().setRetryCount(0);
                if (timesFailed <= 10) {
                    timesFailed++;
                    link.setProperty(NICE_HOSTproperty + "timesfailed_cantconnect", timesFailed);
                    logger.info(NICE_HOST + ": cantconnect -> Retrying");
                    throw new PluginException(LinkStatus.ERROR_RETRY, "cantconnect");
                } else {
                    link.setProperty(NICE_HOSTproperty + "timesfailed_cantconnect", Property.NULL);
                    logger.info(NICE_HOST + ": cantconnect - disabling current host!");
                    tempUnavailableHoster(acc, link, 30 * 60 * 1000l);
                }
            }

            dllink = br.getRegex("\"(http://(www\\.)?premiumax\\.net/dl/[a-z0-9]+/?)\"").getMatch(0);
            if (dllink == null) {
                logger.info(NICE_HOST + ": dllinknullerror");
                int timesFailed = link.getIntegerProperty(NICE_HOSTproperty + "timesfailed_dllinknullerror", 0);
                link.getLinkStatus().setRetryCount(0);
                if (timesFailed <= 2) {
                    timesFailed++;
                    link.setProperty(NICE_HOSTproperty + "timesfailed_dllinknullerror", timesFailed);
                    logger.info(NICE_HOST + ": dllinknullerror -> Retrying");
                    throw new PluginException(LinkStatus.ERROR_RETRY, "dllinknullerror");
                } else {
                    link.setProperty(NICE_HOSTproperty + "timesfailed_dllinknullerror", Property.NULL);
                    logger.info(NICE_HOST + ": dllinknullerror - disabling current host!");
                    tempUnavailableHoster(acc, link, 60 * 60 * 1000l);
                }
            }
        }

        int maxChunks = 0;
        if (link.getBooleanProperty(PremiumaxNet.NOCHUNKS, false)) {
            maxChunks = 1;
        }

        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, maxChunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 404) {
                handleErrors(acc, link, "404servererror", 10);
            }
            br.followConnection();
            logger.info("Unhandled download error on premiumax.net: " + br.toString());
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        /* Workaround for possible double filename bug: http://board.jdownloader.org/showthread.php?t=59540 */
        String finalname = link.getFinalFileName();
        if (finalname == null) {
            finalname = link.getName();
        }
        if (finalname != null) {
            link.setFinalFileName(finalname);
        }
        link.setProperty("premiumaxnetdirectlink", dllink);
        try {
            if (!this.dl.startDownload()) {
                try {
                    if (dl.externalDownloadStop()) {
                        return;
                    }
                } catch (final Throwable e) {
                }
                /* unknown error, we disable multiple chunks */
                if (link.getBooleanProperty(PremiumaxNet.NOCHUNKS, false) == false) {
                    link.setProperty(PremiumaxNet.NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            }
        } catch (final PluginException e) {
            // New V2 errorhandling
            /* unknown error, we disable multiple chunks */
            if (e.getLinkStatus() != LinkStatus.ERROR_RETRY && link.getBooleanProperty(PremiumaxNet.NOCHUNKS, false) == false) {
                link.setProperty(PremiumaxNet.NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            throw e;
        }
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                con = br2.openGetConnection(dllink);
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

    /**
     * Is intended to handle errors which might occur seldom by re-tring a couple of times before we temporarily remove the host from the
     * host list.
     *
     * @param dl
     *            : The DownloadLink
     * @param error
     *            : The name of the error
     * @param maxRetries
     *            : Max retries before out of date error is thrown
     */
    private void handleErrors(final Account acc, final DownloadLink dl, final String error, final int maxRetries) throws PluginException {
        int timesFailed = dl.getIntegerProperty(NICE_HOSTproperty + "failedtimes_" + error, 0);
        dl.getLinkStatus().setRetryCount(0);
        if (timesFailed <= maxRetries) {
            logger.info(NICE_HOST + ": " + error + " -> Retrying");
            timesFailed++;
            dl.setProperty(NICE_HOSTproperty + "failedtimes_" + error, timesFailed);
            throw new PluginException(LinkStatus.ERROR_RETRY, error);
        } else {
            dl.setProperty(NICE_HOSTproperty + "failedtimes_" + error, Property.NULL);
            logger.info(NICE_HOST + ": " + error + " -> Disabling current host");
            tempUnavailableHoster(acc, dl, 1 * 60 * 60 * 1000l);
        }
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        return AvailableStatus.UNCHECKABLE;
    }

    private static Object LOCK = new Object();

    @SuppressWarnings("unchecked")
    private boolean login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                br.setFollowRedirects(true);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof HashMap<?, ?>) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            br.setCookie(MAINPAGE, key, value);
                        }
                        /* Avoids unnerving login captchas */
                        if (force) {
                            getPage("http://www.premiumax.net/");
                            if (br.containsHTML(">Sign out</a>")) {
                                return true;
                            } else {
                                br.clearCookies(MAINPAGE);
                                logger.info("Seems like the cookies are no longer valid -> Doing a full refresh");
                                if (logger instanceof LogSource) {
                                    ((LogSource) logger).flush();
                                }
                            }
                        } else {
                            return true;
                        }
                    }
                }
                getPage("http://www.premiumax.net/");
                final String stayin = br.getRegex("type=\"hidden\" name=\"stayloggedin\" value=\"([^<>\"]*?)\"").getMatch(0);
                if (stayin == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                final DownloadLink dummyLink = new DownloadLink(this, "Account", "premiumax.net", "http://premiumax.net", true);
                final String code = getCaptchaCode("http://www.premiumax.net/veriword.php", dummyLink);
                postPage("http://www.premiumax.net/", "serviceButtonValue=login&service=login&stayloggedin=" + stayin + "&username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()) + "&formcode=" + code);
                if (br.getCookie(MAINPAGE, "WebLoginPE") == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = br.getCookies(MAINPAGE);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
                return true;
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                return false;
            }
        }
    }

    private void tempUnavailableHoster(Account account, DownloadLink downloadLink, long timeout) throws PluginException {
        if (downloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unable to handle this errorcode!");
        }
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap == null) {
                unavailableMap = new HashMap<String, Long>();
                hostUnavailableMap.put(account, unavailableMap);
            }
            /* wait to retry this host */
            unavailableMap.put(downloadLink.getHost(), (System.currentTimeMillis() + timeout));
        }
        throw new PluginException(LinkStatus.ERROR_RETRY);
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) {
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap != null) {
                Long lastUnavailable = unavailableMap.get(downloadLink.getHost());
                if (lastUnavailable != null && System.currentTimeMillis() < lastUnavailable) {
                    return false;
                } else if (lastUnavailable != null) {
                    unavailableMap.remove(downloadLink.getHost());
                    if (unavailableMap.size() == 0) {
                        hostUnavailableMap.remove(account);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
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
        if (Boolean.TRUE.equals(acc.getBooleanProperty("nopremium"))) {
            /* free accounts also have captchas */
            return true;
        }
        if (acc.getStringProperty("session_type") != null && !"premium".equalsIgnoreCase(acc.getStringProperty("session_type"))) {
            return true;
        }
        return false;
    }
}