//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
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

package jd.plugins.host;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.regex.Pattern;

import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Configuration;
import jd.http.Browser;
import jd.http.Encoding;
import jd.http.HTTPConnection;
import jd.http.Request;
import jd.parser.HTMLParser;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.HTTP;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginForHost;
import jd.plugins.RequestInfo;
import jd.plugins.download.RAFDownload;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;

public class Megauploadcom extends PluginForHost {

    static private final String COOKIE = "l=de; v=1; ve_view=1";

    static private final String ERROR_FILENOTFOUND = "Die Datei konnte leider nicht gefunden werden";

    static private final String ERROR_TEMP_NOT_AVAILABLE = "Zugriff auf die Datei ist vor";

    static private final String HOST = "megaupload.com";

    static private final Pattern PAT_SUPPORTED = Pattern.compile("http://[\\w\\.]*?(megaupload|megarotic|sexuploader)\\.com/.*?\\?d\\=.{8}", Pattern.CASE_INSENSITIVE);

    private static final String PATTERN_PASSWORD_WRONG = "Wrong password! Please try again";

    static private final int PENDING_WAITTIME = 45000;

    static private final String SIMPLEPATTERN_CAPTCHA_POST_URL = "<form method=\"POST\" action=\"(.*?)\" target";

    static private final String SIMPLEPATTERN_CAPTCHA_URl = " <img src=\"/capgen\\.php?(.*?)\">";

    static private final String SIMPLEPATTERN_GEN_DOWNLOADLINK = "var (.*?) = String\\.fromCharCode\\(Math\\.abs\\((.*?)\\)\\);(.*?)var (.*?) = '(.*?)' \\+ String\\.fromCharCode\\(Math\\.sqrt\\((.*?)\\)\\);";

    static private final String SIMPLEPATTERN_GEN_DOWNLOADLINK_LINK = "Math\\.sqrt\\((.*?)\\)\\);(.*?)document\\.getElementById\\(\"(.*?)\"\\)\\.innerHTML = '<a href=\"(.*?)' (.*?) '(.*?)\"(.*?)onclick=\"loadingdownload\\(\\)";

    private String captchaPost;

    private String captchaURL;

    private HashMap<String, String> fields;

    private boolean tempUnavailable = false;

    public Megauploadcom() {
        setConfigElements();
        this.enablePremium();
    }

    public boolean doBotCheck(File file) {
        return false;
    }

    public AccountInfo getAccountInformation(Account account) throws Exception {
        AccountInfo ai = new AccountInfo(this, account);
        Browser br = new Browser();
        br.setCookiesExclusive(true);br.clearCookies(HOST);
        br.setAcceptLanguage("en, en-gb;q=0.8");

        br.postPage("http://megaupload.com/en/", "login=" + account.getUser() + "&password=" + account.getPass());

        br.getPage("http://www.megaupload.com/xml/premiumstats.php?confirmcode=" + br.getCookie("http://megaupload.com", "user") + "&language=en&uniq=" + System.currentTimeMillis());
        String days = br.getRegex("daysremaining=\"(\\d*?)\"").getMatch(0);
        ai.setValidUntil(System.currentTimeMillis() + (Long.parseLong(days) * 24 * 50 * 50 * 1000));
        if (days == null || days.equals("0")) ai.setExpired(true);
        // /xml/rewardpoints.php?confirmcode=ed4f6c040c12111d9aae6fa0cc046861&
        // language=en&uniq=1218486921448
        br.getPage("http://www.megaupload.com/xml/rewardpoints.php?confirmcode=" + br.getCookie("http://megaupload.com", "user") + "&language=en&uniq=" + System.currentTimeMillis());
        String points = br.getRegex("availablepoints=\"(\\d*?)\"").getMatch(0);
        ai.setPremiumPoints(Integer.parseInt(points));

        return ai;
    }

    public void handlePremium(DownloadLink parameter, Account account) throws Exception {
        LinkStatus linkStatus = parameter.getLinkStatus();
        DownloadLink downloadLink = (DownloadLink) parameter;
        String link = downloadLink.getDownloadURL().replaceAll("/de", "");

        String countryID = getPluginConfig().getStringProperty("COUNTRY_ID", "-");
        logger.info("PREMOIM");
        String url = "http://www.megaupload.com/de/";
        if (!countryID.equals("-")) {
            logger.info("Use Country trick");

            link = link.replace(".com/", ".com/" + countryID + "/");
            url = url.replaceAll("/de/", "/" + countryID + "/");

        }

        downloadLink.getLinkStatus().setStatusText("Login");
        Browser br = new Browser();
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.0; de; rv:1.8.1.14) Gecko/20080404 Firefox/2.0.0.14;MEGAUPLOAD 1.0");
        br.getHeaders().put("X-MUTB", link);
        br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded");
        br.postPage(url, "login=" + account.getUser() + "&password=" + account.getPass());
        if (br.getCookie(url, "user") == null || br.getCookie(url, "user").length() == 0) {
            linkStatus.addStatus(LinkStatus.ERROR_PREMIUM);
        }

        String id = Request.parseQuery(link).get("d");
        br.getHeaders().clear();
        br.getHeaders().put("TE", "trailers");
        br.getHeaders().put("Connection", "TE");
        br.setFollowRedirects(false);
        br.getPage("http://" + new URL(link).getHost() + "/mgr_dl.php?d=" + id + "&u=" + br.getCookie(url, "user"));

        HTTPConnection urlConnection;
        downloadLink.getLinkStatus().setStatusText("Premium");
        urlConnection = br.openGetConnection(br.getRedirectLocation());
        dl = new RAFDownload(this, downloadLink, urlConnection);
   
        dl.setChunkNum(JDUtilities.getSubConfig("DOWNLOAD").getIntegerProperty(Configuration.PARAM_DOWNLOAD_MAX_CHUNKS, 2));
        dl.setResume(true);
        dl.startDownload();

    }

    public String getAGBLink() {

        return "http://www.megaupload.com/terms/";
    }

    public String getCoder() {
        return "JD-Team";
    }

    public boolean getFileInformation(DownloadLink downloadLink) {
        try {
            return checkLinks(new DownloadLink[] { downloadLink })[0];
        } catch (Exception e) {
        }
        return true;
    }

    public String getFileInformationString(DownloadLink downloadLink) {
        return (tempUnavailable ? "<Temp. unavailable> " : "") + downloadLink.getName() + " (" + JDUtilities.formatBytesToMB(downloadLink.getDownloadSize()) + ")";
    }

    public String getHost() {
        return HOST;
    }

    public String getPluginName() {
        return HOST;
    }

    public Pattern getSupportedLinks() {
        return PAT_SUPPORTED;
    }

    public String getVersion() {
        String ret = new Regex("$Revision$", "\\$Revision: ([\\d]*?) \\$").getMatch(0);
        return ret == null ? "0.0" : ret;
    }

    public void handleFree(DownloadLink parameter) throws Exception {
        LinkStatus linkStatus = parameter.getLinkStatus();

        DownloadLink downloadLink = (DownloadLink) parameter;
        String link = downloadLink.getDownloadURL().replaceAll("/de", "");

        String countryID = getPluginConfig().getStringProperty("COUNTRY_ID", "-");
        if (!countryID.equals("-")) {
            logger.info("Use Country trick");

            try {
                link = "http://" + new URL(link).getHost() + "/" + countryID + "/?d=" + link.substring(link.indexOf("?d=") + 3);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

        }

        RequestInfo requestInfo = HTTP.getRequest(new URL(link), COOKIE, null, true);
        if (requestInfo.containsHTML(ERROR_TEMP_NOT_AVAILABLE)) {
            linkStatus.addStatus(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE);
            linkStatus.setValue(20 * 60 * 1000l);
            tempUnavailable = true;            
            return;
        }
        if (requestInfo.containsHTML(ERROR_FILENOTFOUND)) {
            linkStatus.addStatus(LinkStatus.ERROR_FILE_NOT_FOUND);
            return;
        }

        captchaURL = "http://" + new URL(link).getHost() + "/capgen.php" + new Regex(requestInfo.getHtmlCode(), SIMPLEPATTERN_CAPTCHA_URl).getMatch(0);
        fields = HTMLParser.getInputHiddenFields(requestInfo.getHtmlCode(), "checkverificationform", "passwordhtml");
        captchaPost = new Regex(requestInfo.getHtmlCode(), SIMPLEPATTERN_CAPTCHA_POST_URL).getMatch(0);

        if (captchaURL.endsWith("null") || captchaPost == null) {
            linkStatus.addStatus(LinkStatus.ERROR_RETRY);
        }

        File file = this.getLocalCaptchaFile(this);
        logger.info("Captcha " + captchaURL);
        requestInfo = HTTP.getRequestWithoutHtmlCode(new URL(captchaURL), COOKIE, requestInfo.getLocation(), true);
        if (!requestInfo.isOK() || !Browser.download(file, requestInfo.getConnection()) || !file.exists()) {
            logger.severe("Captcha Download fehlgeschlagen: " + captchaURL);
            linkStatus.addStatus(LinkStatus.ERROR_CAPTCHA);
            return;
        }
        String code = this.getCaptchaCode(file, downloadLink);

        requestInfo = HTTP.postRequest(new URL(captchaPost), COOKIE, null, null, Plugin.joinMap(fields, "=", "&") + "&imagestring=" + code, true);
        if (new Regex(requestInfo.getHtmlCode(), SIMPLEPATTERN_CAPTCHA_URl).getMatch(0) != null) {

            linkStatus.addStatus(LinkStatus.ERROR_CAPTCHA);
            return;
        }

        String pwdata = HTMLParser.getFormInputHidden(requestInfo.getHtmlCode(), "passwordbox", "passwordcountdown");
        if (pwdata != null && pwdata.indexOf("passkey") > 0) {
            logger.info("Password protected");
            String pass = JDUtilities.getController().getUiInterface().showUserInputDialog("Password:");
            if (pass == null) {

                linkStatus.addStatus(LinkStatus.ERROR_FATAL);
                linkStatus.setErrorMessage(JDLocale.L("plugins.errors.wrongpassword", "Password wrong"));

                return;
            }
            if (countryID.equals("-")) {

                requestInfo = HTTP.postRequest(new URL("http://" + new URL(link).getHost() + "/de/"), COOKIE, null, null, pwdata + "&pass=" + pass, true);
            } else {
                requestInfo = HTTP.postRequest(new URL("http://" + new URL(link).getHost() + "/" + countryID + "/"), COOKIE, null, null, pwdata + "&pass=" + pass, true);

            }
            if (requestInfo.containsHTML(PATTERN_PASSWORD_WRONG)) {
                linkStatus.addStatus(LinkStatus.ERROR_FATAL);
                linkStatus.setErrorMessage(JDLocale.L("plugins.errors.wrongpassword", "Password wrong"));

                return;
            }

        }
        sleep(PENDING_WAITTIME, downloadLink);

        String[] tmp = new Regex(requestInfo.getHtmlCode(), SIMPLEPATTERN_GEN_DOWNLOADLINK).getRow(0);
        Character l = (char) Math.abs(Integer.parseInt(tmp[1].trim()));
        String i = tmp[4] + (char) Math.sqrt(Integer.parseInt(tmp[5].trim()));
        tmp = new Regex(requestInfo.getHtmlCode(), SIMPLEPATTERN_GEN_DOWNLOADLINK_LINK).getRow(0);
        String url = Encoding.htmlDecode(tmp[3] + i + l + tmp[5]);

        requestInfo = HTTP.getRequestWithoutHtmlCode(new URL(url), COOKIE, null, true);
        if (!requestInfo.isOK()) {
            logger.warning("Download Limit!");
            linkStatus.addStatus(LinkStatus.ERROR_IP_BLOCKED);
            String wait = requestInfo.getConnection().getHeaderField("Retry-After");
            logger.finer("Warten: " + wait + " minuten");
            if (wait != null) {
                linkStatus.setValue(Integer.parseInt(wait.trim()) * 60 * 1000);
            } else {
                linkStatus.setValue(120 * 60 * 1000);
            }
            return;

        }

        dl = new RAFDownload(this, downloadLink, requestInfo.getConnection());

        dl.startDownload();

    }

    /**
     * Bietet der hoster eine Möglichkeit mehrere Links gleichzeitig zu prüfen,
     * kann das über diese Funktion gemacht werden. Bei RS.com ist das derzeit
     * deaktiviert, weil der Linkchecker nicht mehr über SSL erreichbar ist.
     */
    public boolean[] checkLinks(DownloadLink[] urls) {
        try {
            if (urls == null) { return null; }
            boolean[] ret = new boolean[urls.length];
            HashMap<String, String> post = new HashMap<String, String>();
            for (int j = 0; j < urls.length; j++) {
                if (!canHandle(urls[j].getDownloadURL())) { return null; }
                post.put("id" + j, new Regex(urls[j].getDownloadURL(), ".*?\\?d\\=(.{8})").getMatch(0));
            }
            Browser b = new Browser();
            b.setCookie("http://www.megaupload.com/mgr_linkcheck.php", "l", "de");
            b.setCookie("http://www.megaupload.com/mgr_linkcheck.php", "toolbar", "1");
            String pag = b.postPage("http://www.megaupload.com/mgr_linkcheck.php", post).replaceFirst("0=www.megaupload.com&1=www.megarotic.com&", "").replaceFirst("id[\\d]+=", "").trim();
            String[] pg = pag.split("&id[\\d]+=");
            for (int j = 0; j < pg.length; j++) {
                try {
                    String[] infos = pg[j].split("&[sdn]=");
                    if (infos.length < 4 || !infos[0].equals("0")) {
                        ret[j] = false;
                    } else {
                        ret[j] = true;
                        urls[j].setDownloadSize(Integer.parseInt(infos[1]));
                        urls[j].setName(Encoding.htmlDecode(infos[3].trim()));
                    }
                } catch (Exception e) {
                    ret[j] = false;
                }

            }
            return ret;

        } catch (MalformedURLException e) {

            e.printStackTrace();
            return null;
        } catch (Exception e) {

            e.printStackTrace();
            return null;
        }

    }

    public void reset() {
        captchaPost = null;
        captchaURL = null;
        fields = null;
    }

    public void resetPluginGlobals() {

    }

    private void setConfigElements() {
        config.addEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX, getPluginConfig(), "COUNTRY_ID", new String[] { "-", "en", "de", "fr", "es", "pt", "nl", "it", "cn", "ct", "jp", "kr", "ru", "fi", "se", "dk", "tr", "sa", "vn", "pl" }, "LänderID").setDefaultValue("-"));
    }
}
