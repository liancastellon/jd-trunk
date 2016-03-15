//jDownloader - Downloadmanager
//Copyright (C) 2016  JD-Team support@jdownloader.org
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jd.PluginWrapper;
import jd.config.Property;
import jd.gui.UserIO;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountError;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.utils.JDUtilities;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "dailyfiles.net" }, urls = { "https?://dailyfiles\\.net/([A-Za-z0-9]+)(/)?" }, flags = { 2 })
public class DailyfilesNet extends PluginForHost {
    private String  MAINPAGE = "http://dailyfiles.net/";
    private String  accountResponse;
    private Account currentAccount;

    public DailyfilesNet(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://dailyfiles.net/upgrade.html");
    }

    @Override
    public String getAGBLink() {
        return "http://dailyfiles.net/terms.html";
    }

    @Override
    public boolean checkLinks(final DownloadLink[] urls) {
        if (urls == null || urls.length == 0) {
            return false;
        }

        // correct link stuff goes here, stable is lame!
        for (DownloadLink link : urls) {
            if (link.getProperty("FILEID") == null) {
                String downloadUrl = link.getDownloadURL();
                String fileID;
                fileID = new Regex(downloadUrl, "https?://dailyfiles\\.net/([A-Za-z0-9]+)/?").getMatch(0);

                link.setProperty("FILEID", fileID);
            }
        }
        try {
            final Browser br = new Browser();
            br.setCookiesExclusive(true);

            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br.setFollowRedirects(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            while (true) {
                links.clear();
                while (true) {
                    /* we test 50 links at once */
                    if (index == urls.length || links.size() > 49) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                boolean first = true;
                for (final DownloadLink dl : links) {
                    if (!first) {
                        sb.append(",");
                    }
                    sb.append(dl.getProperty("FILEID"));
                    first = false;
                }
                // API CALL
                // http://dailyfiles.net/API/apidownload.php?request=filecheck&url=http://dailyfiles.net/eacc7758a35bd234/FileUploader.exe

                br.getPage(MAINPAGE + "API/apidownload.php?request=filecheck&url=" + MAINPAGE + sb);

                // output: output: filename, filesize
                String response = br.toString();
                int fileNumber = 0;
                for (final DownloadLink dllink : links) {
                    final String error = checkForErrors(response, "error");
                    // {"error":"File doesn't exists"}
                    if (error == null) {
                        String fileName = PluginJSonUtils.getJson(response, "fileName");
                        String fileSize = PluginJSonUtils.getJson(response, "fileSize");
                        fileName = Encoding.htmlDecode(fileName.trim());
                        fileName = unescape(fileName);
                        dllink.setFinalFileName(Encoding.htmlDecode(fileName.trim()));
                        dllink.setDownloadSize(SizeFormatter.getSize(fileSize));
                        dllink.setAvailable(true);
                    } else {
                        dllink.setAvailable(false);
                        logger.warning("Linkchecker returns: " + error + " for: " + getHost() + " and link: " + dllink.getDownloadURL());
                    }

                    fileNumber++;

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

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        checkLinks(new DownloadLink[] { downloadLink });
        if (!downloadLink.isAvailabilityStatusChecked()) {
            return AvailableStatus.UNCHECKED;
        }
        if (downloadLink.isAvailabilityStatusChecked() && !downloadLink.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return AvailableStatus.TRUE;

    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        doFree(downloadLink);
    }

    public void doFree(final DownloadLink downloadLink) throws Exception, PluginException {
        String downloadURL = downloadLink.getPluginPatternMatcher();
        setMainPage(downloadURL);
        br.setCookiesExclusive(true);
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.setAcceptLanguage("pl-PL,pl;q=0.9,en;q=0.8");

        br.setFollowRedirects(false);
        requestFileInformation(downloadLink);
        String getURL;
        Account currAccount = getCurrentAccount();
        if (currAccount == null) {
            getURL = MAINPAGE + "API/apidownload.php?request=getfile&url=" + downloadURL;
        } else {
            getURL = MAINPAGE + "API/apidownload.php?request=getfile&url=" + downloadURL + "&username=" + Encoding.urlEncode(currAccount.getUser()) + "&password=" + Encoding.urlEncode(currAccount.getPass());
            // Encoding.urlEncode()
        }

        br.getPage(getURL);
        String response = br.toString();

        String error = checkForErrors(response, "error");
        /*
         * output: wait (waiting time in seconds - unregistered 120, free user 60), downloadlink • File doesn't exists • Not authenticated -
         * if username is set, and authentication failed • You have reached your bandwidth limit • Could not save token - internal error •
         * Token doesn't exists - internal error • Could not generate download URL - internal error • File is too large for free users
         */

        if (error != null) {
            throw new PluginException(LinkStatus.ERROR_DOWNLOAD_FAILED, error);

        } else {
            long watiTime = Long.parseLong(PluginJSonUtils.getJson(response, "wait"));
            sleep(watiTime * 100l, downloadLink);

        }
        String fileLocation = PluginJSonUtils.getJson(response, "downloadlink");
        if (fileLocation == null) {
            logger.info("Hoster: DailytfilesNets reports: filelocation not found with link: " + downloadLink.getDownloadURL());
            throw new PluginException(LinkStatus.ERROR_DOWNLOAD_FAILED, getPhrase("DOWNLOADLINK_ERROR"));

        }

        String dllink = fileLocation.replace("\\", "");

        response = br.toString();
        if (response == null) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Host busy!", 1 * 60l * 1000l);
        }

        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Can't find final download link!", -1l);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            handleDownloadServerErrors();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private final void handleDownloadServerErrors() throws PluginException {
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
    }

    private void setLoginData(final Account account) throws Exception {
        br.getPage(MAINPAGE);
        br.setCookiesExclusive(true);
        final Object ret = account.getProperty("cookies", null);
        final HashMap<String, String> cookies = (HashMap<String, String>) ret;
        if (account.isValid()) {
            for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                final String key = cookieEntry.getKey();
                final String value = cookieEntry.getValue();
                this.br.setCookie(MAINPAGE, key, value);
            }
        }
    }

    @Override
    public void handlePremium(final DownloadLink downloadLink, final Account account) throws Exception {
        String downloadURL = downloadLink.getPluginPatternMatcher();
        setMainPage(downloadURL);
        String response = "";

        br.setFollowRedirects(false);
        requestFileInformation(downloadLink);
        String loginInfo = login(account, false);
        // (string) login - Login użytkownika
        // (string) password - Hasło
        // (string) id - Identyfikator pliku ( np: 7464459120 )
        String userType = PluginJSonUtils.getJson(loginInfo, "typ");
        if (!"premium".equals(userType)) {
            // setLoginData(account);
            setCurrentAccount(account);
            handleFree(downloadLink);
            return;
        }

        String getURL = MAINPAGE + "API/apidownload.php?request=getfile&url=" +
                // Encoding.urlEncode()
                downloadURL + "&username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass());
        br.getPage(getURL);
        response = br.toString();

        String error = checkForErrors(response, "error");
        /*
         * output: wait (waiting time in seconds - unregistered 120, free user 60), downloadlink • File doesn't exists • Not authenticated -
         * if username is set, and authentication failed • You have reached your bandwidth limit • Could not save token - internal error •
         * Token doesn't exists - internal error • Could not generate download URL - internal error • File is too large for free users
         */

        if (error != null) {
            throw new PluginException(LinkStatus.ERROR_DOWNLOAD_FAILED, error);
        }

        String fileLocation = PluginJSonUtils.getJson(response, "downloadlink");
        if (fileLocation == null) {
            logger.info("Hoster: DailytfilesNets reports: filelocation not found with link: " + downloadLink.getDownloadURL());
            throw new PluginException(LinkStatus.ERROR_DOWNLOAD_FAILED, getPhrase("DOWNLOADLINK_ERROR"));

        }
        // setLoginData(account);

        String dllink = fileLocation.replace("\\", "");

        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            logger.warning("The final dllink seems not to be a file!" + "Response: " + dl.getConnection().getResponseMessage() + ", code: " + dl.getConnection().getResponseCode() + "\n" + dl.getConnection().getContentType());
            handleDownloadServerErrors();
            logger.warning("br returns:" + br.toString());
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }

        dl.startDownload();
    }

    private static Object LOCK = new Object();

    @SuppressWarnings("unchecked")
    private String login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            br.setCookiesExclusive(true);
            final Object ret = account.getProperty("cookies", null);

            // API Call: /API/apidownload.php?request=usercheck&username=xxx&password=xxx
            br.getPage(MAINPAGE + "/API/apidownload.php?request=usercheck&username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
            String response = br.toString();

            String error = checkForErrors(response, "error");
            if (error != null) {
                logger.info("Hoster Dailyfiles.net reports: " + error);
                if ("Not authenticated".equals(error)) {
                    error = getPhrase("NOT_AUTHENTICATED");
                }
                throw new PluginException(LinkStatus.ERROR_PREMIUM, error);

            }
            br.postPageRaw(MAINPAGE + "/ajax/_account_login.ajax.php", "username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));

            account.setProperty("name", Encoding.urlEncode(account.getUser()));
            account.setProperty("pass", Encoding.urlEncode(account.getPass()));
            /** Save cookies */
            final HashMap<String, String> cookies = new HashMap<String, String>();
            final Cookies add = this.br.getCookies(MAINPAGE);
            for (final Cookie c : add.getCookies()) {
                cookies.put(c.getKey(), c.getValue());
            }
            account.setProperty("cookies", cookies);
            return response;
        }
    }

    private String checkForErrors(String source, String searchString) {
        if (source.contains("error")) {
            String errorMessage = PluginJSonUtils.getJson(source, searchString);
            return errorMessage;
        }
        return null;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        try {
            accountResponse = login(account, true);
        } catch (PluginException e) {

            String errorMessage = e.getErrorMessage();

            ai.setStatus("Login failed");
            UserIO.getInstance().requestMessageDialog(0, "Dailyfiles.net: " + getPhrase("LOGIN_ERROR"), getPhrase("LOGIN_ERROR") + ": " + errorMessage);
            account.setError(AccountError.INVALID, getPhrase("LOGIN_ERROR"));
            account.setProperty("cookies", Property.NULL);
            return ai;
        }
        // output
        // premium:
        // {
        // type: "premium",
        // expires: "2017-03-09 00:00:00",
        // traffic: "107374182400"
        // }
        // free:
        // {
        // type: "free",
        // expires: "0000-00-00 00:00:00"
        // }
        String userType = PluginJSonUtils.getJson(accountResponse, "typ");
        if ("premium".equals(userType)) {
            String userPremiumDateEnd = PluginJSonUtils.getJson(accountResponse, "expires");
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault());
            Date date;
            try {
                date = dateFormat.parse(userPremiumDateEnd);
                ai.setValidUntil(date.getTime());
            } catch (final Exception e) {
                logger.log(e);
            }

            long userTraffic = Long.parseLong(PluginJSonUtils.getJson(accountResponse, "traffic"));
            ai.setTrafficLeft(userTraffic);
            ai.setStatus(getPhrase("PREMIUM_USER"));
        } else {
            ai.setStatus(getPhrase("FREE_USER"));
        }
        account.setValid(true);
        return ai;
    }

    private static AtomicBoolean yt_loaded = new AtomicBoolean(false);

    private String unescape(final String s) {
        /* we have to make sure the youtube plugin is loaded */
        if (!yt_loaded.getAndSet(true)) {
            JDUtilities.getPluginForHost("youtube.com");
        }
        return jd.nutils.encoding.Encoding.unescapeYoutube(s);
    }

    /*
     * @Override public int getMaxSimultanPremiumDownloadNum() { }
     */
    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        // requested by the hoster admin
        return 1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    void setMainPage(String downloadUrl) {
        if (downloadUrl.contains("https://")) {
            MAINPAGE = "https://dailyfiles.net/";
        } else {
            MAINPAGE = "http://dailyfiles.net/";
        }
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

    private HashMap<String, String> phrasesEN = new HashMap<String, String>() {
                                                  {

                                                      put("LOGIN_ERROR", "Login Error");
                                                      put("PREMIUM_USER", "Premium User");
                                                      put("FREE_USER", "Registered (free) user");
                                                      put("NOT_AUTHENTICATED", "Not Authenticated");
                                                      put("DOWNLOADLINK_ERROR", "Downloadlink error");
                                                  }
                                              };

    private HashMap<String, String> phrasesPL = new HashMap<String, String>() {
                                                  {

                                                      put("LOGIN_ERROR", "Błąd logowania");
                                                      put("PREMIUM_USER", "Użytkownik Premium");
                                                      put("FREE_USER", "Zarejestrowany użytkownik darmowy");
                                                      put("NOT_AUTHENTICATED", "Nazwa użytkownika lub hasło jest niepoprawne");
                                                      put("DOWNLOADLINK_ERROR", "Serwer nie zwrócił linku pobierania");

                                                  }
                                              };

    /**
     * Returns a German/English translation of a phrase. We don't use the JDownloader translation framework since we need only German and
     * English.
     *
     * @param key
     * @return
     */
    private String getPhrase(String key) {
        if ("pl".equals(System.getProperty("user.language")) && phrasesPL.containsKey(key)) {
            return phrasesPL.get(key);
        } else if (phrasesEN.containsKey(key)) {
            return phrasesEN.get(key);
        }
        return "Translation not found!";
    }

    public Account getCurrentAccount() {
        return currentAccount;
    }

    public void setCurrentAccount(Account currentAccount) {
        this.currentAccount = currentAccount;
    }
}