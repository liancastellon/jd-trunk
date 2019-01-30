//    jDownloader - Downloadmanager
//    Copyright (C) 2014  JD-Team support@jdownloader.org
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

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
import jd.plugins.PluginConfigPanelNG;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "xt7.pl" }, urls = { "REGEX_NOT_POSSIBLE_RANDOM-asdfasdfsadfsdgfd32423" })
public class Xt7Pl extends PluginForHost {
    private String                                         MAINPAGE           = "https://xt7.pl/";
    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap = new HashMap<Account, HashMap<String, Long>>();
    private static Object                                  LOCK               = new Object();

    public Xt7Pl(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(MAINPAGE + "login");
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    private void login(Account account, boolean force) throws PluginException, IOException {
        synchronized (LOCK) {
            try {
                br.postPage(MAINPAGE + "login", "login=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
                if (br.getCookie(MAINPAGE, "autologin") == null) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, getPhrase("PREMIUM_ERROR"), PluginException.VALUE_ID_PREMIUM_DISABLE);
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

    List<String> getSupportedHosts() {
        List<String> supportedHosts = new ArrayList<String>();
        String hosts;
        try {
            hosts = br.getPage(MAINPAGE + "jdhostingi.txt");
        } catch (IOException e) {
            return null;
        }
        if (hosts != null) {
            String hoster[] = new Regex(hosts, "([A-Zaa-z0-9]+\\.[A-Zaa-z0-9]+)").getColumn(0);
            for (String host : hoster) {
                if (hosts == null || host.length() == 0) {
                    continue;
                }
                supportedHosts.add(host.trim());
            }
            return supportedHosts;
        } else {
            return null;
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        String validUntil = null;
        final AccountInfo ai = new AccountInfo();
        br.setConnectTimeout(60 * 1000);
        br.setReadTimeout(60 * 1000);
        login(account, true);
        if (!br.getURL().contains("mojekonto")) {
            br.getPage("/mojekonto");
        }
        if (br.containsHTML("Brak ważnego dostępu Premium")) {
            ai.setExpired(true);
            // ai.setStatus("Account expired");
            ai.setStatus(getPhrase("EXPIRED"));
            ai.setProperty("premium", "FALSE");
            return ai;
        } else if (br.containsHTML(">Brak ważnego dostępu Premium<")) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, getPhrase("UNSUPPORTED_PREMIUM"), PluginException.VALUE_ID_PREMIUM_DISABLE);
        } else {
            validUntil = br.getRegex("<div class=\"textPremium\">Dostęp Premium ważny do <b>(.*?)</b><br />").getMatch(0);
            if (validUntil == null) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, getPhrase("PLUGIN_BROKEN"), PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
            validUntil = validUntil.replace(" / ", " ");
            ai.setProperty("premium", "TRUE");
        }
        long expireTime = TimeFormatter.getMilliSeconds(validUntil, "dd.MM.yyyy HH:mm", Locale.ENGLISH);
        ai.setValidUntil(expireTime);
        account.setValid(true);
        String otherHostersLimitLeft = // br.getRegex(" Pozostały limit na serwisy dodatkowe: <b>([^<>\"\\']+)</b></div>").getMatch(0);
                br.getRegex("Pozostały Limit Premium do wykorzystania: <b>([^<>\"\\']+)</b></div>").getMatch(0);
        if (otherHostersLimitLeft == null) {
            otherHostersLimitLeft = br.getRegex("Pozostały limit na serwisy dodatkowe: <b>([^<>\"\\']+)</b></div>").getMatch(0);
        }
        ai.setProperty("TRAFFIC_LEFT", otherHostersLimitLeft == null ? getPhrase("UNKNOWN") : SizeFormatter.getSize(otherHostersLimitLeft));
        String unlimited = br.getRegex("<br />(.*): <b>Bez limitu</b> \\|").getMatch(0);
        if (unlimited != null) {
            ai.setProperty("UNLIMITED", unlimited);
        }
        ai.setStatus("Premium" + " (" + getPhrase("TRAFFIC_LEFT") + ": " + (otherHostersLimitLeft == null ? getPhrase("UNKNOWN") : otherHostersLimitLeft) + (unlimited == null ? "" : ", " + unlimited + ": " + getPhrase("UNLIMITED")) + ")");
        if (otherHostersLimitLeft != null) {
            ai.setTrafficLeft(SizeFormatter.getSize(otherHostersLimitLeft));
        }
        List<String> supportedHostsList = getSupportedHosts();
        ai.setMultiHostSupport(this, supportedHostsList);
        return ai;
    }

    @Override
    public String getAGBLink() {
        return MAINPAGE + "regulamin";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    private void showMessage(DownloadLink link, String message) {
        link.getLinkStatus().setStatusText(message);
    }

    /** no override to keep plugin compatible to old stable */
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap != null) {
                Long lastUnavailable = unavailableMap.get(link.getHost());
                if (lastUnavailable != null && System.currentTimeMillis() < lastUnavailable) {
                    final long wait = lastUnavailable - System.currentTimeMillis();
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, getPhrase("HOSTER_UNAVAILABLE") + " " + this.getHost(), wait);
                } else if (lastUnavailable != null) {
                    unavailableMap.remove(link.getHost());
                    if (unavailableMap.size() == 0) {
                        hostUnavailableMap.remove(account);
                    }
                }
            }
        }
        final String downloadUrl = link.getPluginPatternMatcher();
        boolean resume = true;
        showMessage(link, "Phase 1/3: Login");
        login(account, false);
        br.setConnectTimeout(90 * 1000);
        br.setReadTimeout(90 * 1000);
        dl = null;
        // each time new download link is generated
        // (so even after user interrupted download) - transfer
        // is reduced, so:
        // first check if the property generatedLink was previously generated
        // if so, then try to use it, generated link store in link properties
        // for future usage (broken download etc)
        String generatedLink = checkDirectLink(link, "generatedLinkXt7");
        if (generatedLink == null) {
            /* generate new downloadlink */
            String url = Encoding.urlEncode(downloadUrl);
            String postData = "step=1" + "&content=" + url;
            showMessage(link, "Phase 2/3: Generating Link");
            br.postPage(MAINPAGE + "mojekonto/sciagaj", postData);
            if (br.containsHTML("Wymagane dodatkowe [0-9.]+ MB limitu")) {
                logger.severe("Xt7.pl(Error): " + br.getRegex("(Wymagane dodatkowe [0-9.]+ MB limitu)"));
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, getPhrase("DOWNLOAD_LIMIT"), 1 * 60 * 1000l);
            }
            postData = "step=2" + "&0=on";
            br.postPage(MAINPAGE + "mojekonto/sciagaj", postData);
            // New Regex, but not tested if it works for all files (not video)
            // String generatedLink =
            // br.getRegex("<div class=\"download\">(<a target=\"_blank\" href=\"mojekonto/ogladaj/[0-9A-Za-z]*?\">Oglądaj online</a> /
            // )*?<a href=\"([^\"<>]+)\" target=\"_blank\">Pobierz</a>").getMatch(1);
            // Old Regex
            generatedLink = br.getRegex("<div class=\"download\"><a href=\"([^\"<>]+)\" target=\"_blank\">Pobierz</a>").getMatch(0);
            if (generatedLink == null) {
                // New Regex (works with video files)
                generatedLink = br.getRegex("<div class=\"download\">(<a target=\"_blank\" href=\"mojekonto/ogladaj/[0-9A-Za-z]*?\">Oglądaj[ online]*?</a> / )<a href=\"([^\"<>]+)\" target=\"_blank\">Pobierz</a>").getMatch(1);
            }
            if (generatedLink == null) {
                logger.severe("Xt7.pl(Error): " + generatedLink);
                //
                // after x retries we disable this host and retry with normal plugin
                // but because traffic limit is decreased even if there's a problem
                // with download (seems like bug) - we limit retries to 2
                //
                if (link.getLinkStatus().getRetryCount() >= 2) {
                    try {
                        // disable hoster for 30min
                        tempUnavailableHoster(account, link, 30 * 60 * 1000l);
                    } catch (Exception e) {
                    }
                    /* reset retrycounter */
                    link.getLinkStatus().setRetryCount(0);
                    final String inactiveLink = br.getRegex("textarea id=\"listInactive\" class=\"small\" readonly>(.*?)[ \t\n\r]+</textarea>").getMatch(0);
                    if (downloadUrl.compareTo(inactiveLink) != 0) {
                        throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, getPhrase("LINK_INACTIVE"), 30 * 1000l);
                    }
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE);
                }
                String msg = "(" + link.getLinkStatus().getRetryCount() + 1 + "/" + 2 + ")";
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, getPhrase("RETRY") + msg, 20 * 1000l);
            }
            link.setProperty("generatedLinkXt7", generatedLink);
        }
        // wait, workaround
        sleep(1 * 1000l, link);
        int chunks = 0;
        // generated fileshark/lunaticfiles link allows only 1 chunk
        // because download doesn't support more chunks and
        // and resume (header response has no: "Content-Range" info)
        final String url = link.getPluginPatternMatcher();
        final String oneChunkHostersPattern = ".*(lunaticfiles\\.com|fileshark\\.pl).*";
        if (url.matches(oneChunkHostersPattern) || downloadUrl.matches(oneChunkHostersPattern)) {
            chunks = 1;
            resume = false;
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, generatedLink, resume, chunks);
        if (dl.getConnection().getContentType().equalsIgnoreCase("text/html")) // unknown
        // error
        {
            br.followConnection();
            if (br.containsHTML("<div id=\"message\">Ważność linka wygasła.</div>")) {
                // previously generated link expired,
                // clear the property and restart the download
                // and generate new link
                sleep(10 * 1000l, link, getPhrase("LINK_EXPIRED"));
                logger.info("Xt7.pl: previously generated link expired - removing it and restarting download process.");
                link.setProperty("generatedLinkXt7", null);
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            if (br.getBaseURL().contains("notransfer")) {
                /* No traffic left */
                account.getAccountInfo().setTrafficLeft(0);
                throw new PluginException(LinkStatus.ERROR_PREMIUM, getPhrase("NO_TRAFFIC"), PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
            if (br.getBaseURL().contains("serviceunavailable")) {
                tempUnavailableHoster(account, link, 60 * 60 * 1000l);
            }
            if (br.getBaseURL().contains("connecterror")) {
                tempUnavailableHoster(account, link, 60 * 60 * 1000l);
            }
            if (br.getBaseURL().contains("invaliduserpass")) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, getPhrase("PREMIUM_ERROR"), PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
            if ((br.getBaseURL().contains("notfound")) || (br.containsHTML("404 Not Found"))) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (br.containsHTML("Wymagane dodatkowe [0-9.]+ MB limitu")) {
                logger.severe("Xt7.pl(Error): " + br.getRegex("(Wymagane dodatkowe [0-9.]+ MB limitu)"));
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, getPhrase("DOWNLOAD_LIMIT"), 1 * 60 * 1000l);
            }
        }
        if (dl.getConnection().getResponseCode() == 404) {
            /* file offline */
            dl.getConnection().disconnect();
            tempUnavailableHoster(account, link, 20 * 60 * 1000l);
        }
        showMessage(link, "Phase 3/3: Begin download");
        dl.startDownload();
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    // try redirected link
                    boolean resetGeneratedLink = true;
                    String redirectConnection = br2.getRedirectLocation();
                    if (redirectConnection != null) {
                        if (redirectConnection.contains("xt7.pl")) {
                            con = br2.openGetConnection(redirectConnection);
                            if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                                resetGeneratedLink = true;
                            } else {
                                resetGeneratedLink = false;
                            }
                        } else { // turbobit link is already redirected link
                            resetGeneratedLink = false;
                        }
                    }
                    if (resetGeneratedLink) {
                        downloadLink.setProperty(property, Property.NULL);
                        dllink = null;
                    }
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

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        return AvailableStatus.UNCHECKABLE;
    }

    private void tempUnavailableHoster(Account account, DownloadLink downloadLink, long timeout) throws PluginException {
        if (downloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, getPhrase("UNKNOWN_ERROR"));
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
    public boolean canHandle(DownloadLink downloadLink, Account account) throws Exception {
        return true;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
        link.setProperty("generatedLinkXt7", null);
    }

    @Override
    public void extendAccountSettingsPanel(Account acc, PluginConfigPanelNG panel) {
        AccountInfo ai = acc.getAccountInfo();
        if (ai == null) {
            return;
        }
        if (!"FALSE".equals(ai.getProperty("premium"))) {
            long otherHostersLimit = Long.parseLong(ai.getProperty("TRAFFIC_LEFT").toString(), 10);
            String unlimited = (String) (ai.getProperty("UNLIMITED"));
            panel.addStringPair(_GUI.T.lit_traffic_left(), SizeFormatter.formatBytes(otherHostersLimit) + (unlimited == null ? "" : "\n" + unlimited + ": " + getPhrase("UNLIMITED")));
        }
    }

    private HashMap<String, String> phrasesEN = new HashMap<String, String>() {
                                                  {
                                                      put("PREMIUM_ERROR", "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.");
                                                      put("UNSUPPORTED_PREMIUM", "\r\nUnsupported account type!\r\nIf you think this message is incorrect or it makes sense to add support for this account type\r\ncontact us via our support forum.");
                                                      put("PLUGIN_BROKEN", "\r\nPlugin broken, please contact the JDownloader Support!");
                                                      put("TRAFFIC_LEFT", "Traffic left");
                                                      put("HOSTER_UNAVAILABLE", "Host is temporarily unavailable via");
                                                      put("DOWNLOAD_LIMIT", "Download limit exceeded!");
                                                      put("RETRY", "Retry in few secs");
                                                      put("LINK_INACTIVE", "Xt7 reports the link is as inactive!");
                                                      put("LINK_EXPIRED", "Previously generated Link expired!");
                                                      put("NO_TRAFFIC", "No traffic left");
                                                      put("UNKNOWN_ERROR", "Unable to handle this errorcode!");
                                                      put("ACCOUNT_TYPE", "Account type");
                                                      put("UNKNOWN", "Unknown");
                                                      put("UNLIMITED", "Unlimited");
                                                      put("FREE", "free");
                                                      put("EXPIRED", "Account expired/free");
                                                  }
                                              };
    private HashMap<String, String> phrasesPL = new HashMap<String, String>() {
                                                  {
                                                      put("PREMIUM_ERROR", "\r\nNieprawidłowy użytkownik/hasło!\r\nUpewnij się, że wprowadziłeś poprawnie użytkownika i hasło. Podpowiedzi:\r\n1. Jeśli w twoim haśle znajdują się znaki specjalne - usuń je/popraw i wprowadź ponownie hasło!\r\n2. Wprowadzając nazwę użytkownika i hasło - nie używaj operacji Kopiuj i Wklej.");
                                                      put("UNSUPPORTED_PREMIUM", "\r\nNieobsługiwany typ konta!\r\nJesli uważasz, że informacja ta jest niepoprawna i chcesz aby dodac obsługę tego typu konta\r\nskontaktuj się z nami poprzez forum wsparcia.");
                                                      put("PLUGIN_BROKEN", "\r\nProblem z wtyczką, skontaktuj się z zespołem wsparcia JDownloader!");
                                                      put("TRAFFIC_LEFT", "Pozostały transfer");
                                                      put("HOSTER_UNAVAILABLE", "Serwis jest niedostępny przez");
                                                      put("DOWNLOAD_LIMIT", "Przekroczono dostępny limit transferu!");
                                                      put("RETRY", "Ponawianie za kilka sekund");
                                                      put("LINK_INACTIVE", "Xt7 raportuje link jako nieaktywny!");
                                                      put("LINK_EXPIRED", "Poprzednio wygenerowany link wygasł!");
                                                      put("NO_TRAFFIC", "Brak dostępnego transferu");
                                                      put("UNKNOWN_ERROR", "Nieobsługiwany kod błędu!");
                                                      put("ACCOUNT_TYPE", "Typ konta");
                                                      put("UNKNOWN", "Nieznany");
                                                      put("UNLIMITED", "Bez limitu");
                                                      put("FREE", "darmowe");
                                                      put("EXPIRED", "Konto wygasło/darmowe");
                                                  }
                                              };

    /**
     * Returns a Polish/English translation of a phrase. We don't use the JDownloader translation framework since we need only Polish and
     * English.
     *
     * @param key
     * @return
     */
    private String getPhrase(String key) {
        String language = System.getProperty("user.language");
        if ("pl".equals(language) && phrasesPL.containsKey(key)) {
            return phrasesPL.get(key);
        } else if (phrasesEN.containsKey(key)) {
            return phrasesEN.get(key);
        }
        return "Translation not found!";
    }
}