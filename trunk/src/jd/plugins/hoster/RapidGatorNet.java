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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

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
import jd.parser.html.HTMLParser;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.os.CrossSystem;
import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;
import org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "rapidgator.net" }, urls = { "http://(www\\.)?(rapidgator\\.net|rg\\.to)/file/([a-z0-9]{32}(/[^/<>]+\\.html)?|\\d+(/[^/<>]+\\.html)?)" }, flags = { 2 })
public class RapidGatorNet extends PluginForHost {

    public RapidGatorNet(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://rapidgator.net/article/premium");
        this.setConfigElements();
    }

    private static final String            MAINPAGE                        = "http://rapidgator.net/";
    private static Object                  LOCK                            = new Object();
    private static AtomicReference<String> agent                           = new AtomicReference<String>();
    private static final String            PREMIUMONLYTEXT                 = "This file can be downloaded by premium only</div>";
    private static final String            PREMIUMONLYUSERTEXT             = JDL.L("plugins.hoster.rapidgatornet.only4premium", "Only downloadable for premium users!");
    private final String                   EXPERIMENTALHANDLING            = "EXPERIMENTALHANDLING";
    private final String                   DISABLE_API_PREMIUM             = "DISABLE_API_PREMIUM";

    private final String                   apiURL                          = "https://rapidgator.net/api/";

    private final String[]                 IPCHECK                         = new String[] { "http://ipcheck0.jdownloader.org", "http://ipcheck1.jdownloader.org", "http://ipcheck2.jdownloader.org", "http://ipcheck3.jdownloader.org" };
    private static AtomicBoolean           hasAttemptedDownloadstart       = new AtomicBoolean(false);
    private static AtomicLong              timeBefore                      = new AtomicLong(0);
    private static final String            PROPERTY_LASTDOWNLOAD_TIMESTAMP = "rapidgatornet_lastdownload_timestamp";
    private final String                   LASTIP                          = "LASTIP";
    private static AtomicReference<String> lastIP                          = new AtomicReference<String>();
    private final Pattern                  IPREGEX                         = Pattern.compile("(([1-2])?([0-9])?([0-9])\\.([1-2])?([0-9])?([0-9])\\.([1-2])?([0-9])?([0-9])\\.([1-2])?([0-9])?([0-9]))", Pattern.CASE_INSENSITIVE);

    private static final long              FREE_RECONNECTWAIT_GENERAL      = 2 * 60 * 60 * 1000L;
    private static final long              FREE_RECONNECTWAIT_DAILYLIMIT   = 3 * 60 * 60 * 1000L;
    private static final long              FREE_RECONNECTWAIT_OTHERS       = 30 * 60 * 1000L;

    private static final long              FREE_CAPTCHA_EXPIRE_TIME        = 105 * 1000L;

    @Override
    public String getAGBLink() {
        return "http://rapidgator.net/article/terms";
    }

    @Override
    public String rewriteHost(String host) {
        if (host == null || "rapidgator.net".equals(host) || "rg.to".equals(host)) {
            return "rapidgator.net";
        }
        return super.rewriteHost(host);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void correctDownloadLink(final DownloadLink link) throws Exception {
        if (link.getDownloadURL().contains("rg.to/")) {
            String url = link.getDownloadURL();
            url = url.replaceFirst("rg.to/", "rapidgator.net/");
            link.setUrlDownload(url);
        }
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    @Override
    public boolean hasCaptcha(final DownloadLink link, final jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        }
        if (!Account.AccountType.PREMIUM.equals(acc.getType())) {
            /* free accounts also have captchas */
            return true;
        }
        return false;
    }

    @Override
    public boolean hasAutoCaptcha() {
        return false;
    }

    public static Browser prepareBrowser(final Browser prepBr) {
        if (prepBr == null) {
            return prepBr;
        }
        if (agent.get() == null) {
            /* we first have to load the plugin, before we can reference it */
            agent.set(jd.plugins.hoster.MediafireCom.stringUserAgent());
        }
        prepBr.setRequestIntervalLimit("http://rapidgator.net/", 319 * (int) Math.round(Math.random() * 3 + Math.random() * 3));
        prepBr.getHeaders().put("User-Agent", RapidGatorNet.agent.get());
        prepBr.getHeaders().put("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.3");
        prepBr.getHeaders().put("Accept-Language", "en-US,en;q=0.8");
        prepBr.getHeaders().put("Cache-Control", null);
        prepBr.getHeaders().put("Pragma", null);
        prepBr.setCookie("http://rapidgator.net/", "lang", "en");
        prepBr.setCustomCharset("UTF-8");
        prepBr.setReadTimeout(1 * 60 * 1000);
        prepBr.setConnectTimeout(1 * 60 * 1000);
        return prepBr;
    }

    private String handleJavaScriptRedirect() {
        /* check for js redirect */
        final int c = this.br.getRegex("\n").count();
        final boolean isJsRedirect = this.br.getRegex("<html><head><meta http-equiv=\"Content-Type\" content=\"[\\w\\-/;=]{20,50}\"></head>").matches();
        final String[] jsRedirectScripts = this.br.getRegex("<script language=\"JavaScript\">(.*?)</script>").getColumn(0);
        if (jsRedirectScripts != null && jsRedirectScripts.length == 1) {
            if (c == 0 && isJsRedirect) {
                /* final jsredirectcheck */
                String jsRedirectScript = jsRedirectScripts[0];
                final int scriptLen = jsRedirectScript.length();
                final int jsFactor = Math.round((float) scriptLen / (float) this.br.toString().length() * 100);
                /* min 75% of html contains js */
                if (jsFactor > 75) {
                    final String returnValue = new Regex(jsRedirectScript, ";(\\w+)=\'\';$").getMatch(0);
                    jsRedirectScript = jsRedirectScript.substring(0, jsRedirectScript.lastIndexOf("window.location.href"));
                    if (scriptLen > jsRedirectScript.length() && returnValue != null) {
                        return this.executeJavaScriptRedirect(returnValue, jsRedirectScript);
                    }
                }
            }
        }
        return null;
    }

    private String executeJavaScriptRedirect(final String retVal, final String script) {
        Object result = new Object();
        final ScriptEngineManager manager = jd.plugins.hoster.DummyScriptEnginePlugin.getScriptEngineManager(this);
        final ScriptEngine engine = manager.getEngineByName("javascript");
        try {
            engine.eval(script);
            result = engine.get(retVal);
        } catch (final Throwable e) {
            return null;
        }
        return result != null ? result.toString() : null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.correctDownloadLink(link);
        this.setBrowserExclusive();
        RapidGatorNet.prepareBrowser(this.br);
        this.br.setFollowRedirects(true);
        this.br.getPage(link.getDownloadURL());

        /* jsRedirect */
        final String reDirHash = this.handleJavaScriptRedirect();
        if (reDirHash != null) {
            this.logger.info("JSRedirect in requestFileInformation");
            this.br.getPage(link.getDownloadURL() + "?" + reDirHash);
        }

        if (this.br.containsHTML("400 Bad Request") && link.getDownloadURL().contains("%")) {
            link.setUrlDownload(link.getDownloadURL().replace("%", ""));
            this.br.getPage(link.getDownloadURL());
        }
        if (this.br.containsHTML("File not found")) {
            final String filenameFromURL = new Regex(link.getDownloadURL(), ".+/(.+)\\.html").getMatch(0);
            if (filenameFromURL != null) {
                link.setName(filenameFromURL);
            }
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String freedlsizelimit = this.br.getRegex("'You can download files up to ([\\d\\.]+ ?(MB|GB)) in free mode<").getMatch(0);
        if (freedlsizelimit != null) {
            link.getLinkStatus().setStatusText(JDL.L("plugins.hoster.rapidgatornet.only4premium", "This file is restricted to Premium users only"));
        }
        final String md5 = this.br.getRegex(">MD5: ([A-Fa-f0-9]{32})</label>").getMatch(0);
        String filename = this.br.getRegex("Downloading:[\t\n\r ]+</strong>([^<>\"]+)</p>").getMatch(0);
        if (filename == null) {
            filename = this.br.getRegex("<title>Download file ([^<>\"]+)</title>").getMatch(0);
        }
        final String filesize = this.br.getRegex("File size:[\t\n\r ]+<strong>([^<>\"]+)</strong>").getMatch(0);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (filename.startsWith(".") && /* effectively unix based filesystems */!CrossSystem.isWindows()) {
            /* Temp workaround for hidden files */
            filename = filename.substring(1);
        }
        link.setName(Encoding.htmlDecode(filename.trim()));
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        this.br.setFollowRedirects(false);
        // Only show message if user has no active premium account
        if (this.br.containsHTML(RapidGatorNet.PREMIUMONLYTEXT) && AccountController.getInstance().getValidAccount(this) == null) {
            link.getLinkStatus().setStatusText(RapidGatorNet.PREMIUMONLYUSERTEXT);
        }
        if (md5 != null) {
            link.setMD5Hash(md5);
        }
        return AvailableStatus.TRUE;
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    @Override
    protected void showFreeDialog(final String domain) {
        if (System.getProperty("org.jdownloader.revision") != null) { /* JD2 ONLY! */
            super.showFreeDialog(domain);
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            final String lng = System.getProperty("user.language");
                            String message = null;
                            String title = null;
                            final String tab = "                        ";
                            if ("de".equalsIgnoreCase(lng)) {
                                title = domain + " Free Download";
                                message = "Du lädst im kostenlosen Modus von " + domain + ".\r\n";
                                message += "Wie bei allen anderen Hostern holt JDownloader auch hier das Beste für dich heraus!\r\n\r\n";
                                message += tab + "  Falls du allerdings mehrere Dateien\r\n" + "          - und das möglichst mit Fullspeed und ohne Unterbrechungen - \r\n" + "             laden willst, solltest du dir den Premium Modus anschauen.\r\n\r\nUnserer Erfahrung nach lohnt sich das - Aber entscheide am besten selbst. Jetzt ausprobieren?  ";
                            } else {
                                title = domain + " Free Download";
                                message = "You are using the " + domain + " Free Mode.\r\n";
                                message += "JDownloader always tries to get the best out of each hoster's free mode!\r\n\r\n";
                                message += tab + "   However, if you want to download multiple files\r\n" + tab + "- possibly at fullspeed and without any wait times - \r\n" + tab + "you really should have a look at the Premium Mode.\r\n\r\nIn our experience, Premium is well worth the money. Decide for yourself, though. Let's give it a try?   ";
                            }
                            if (CrossSystem.isOpenBrowserSupported()) {
                                final int result = JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null);
                                if (JOptionPane.OK_OPTION == result) {
                                    CrossSystem.openURL(new URL("http://update3.jdownloader.org/jdserv/BuyPremiumInterface/redirect?" + domain + "&freedialog"));
                                }
                            }
                        } catch (final Throwable e) {
                        }
                    }
                });
            } catch (final Throwable e) {
            }
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        this.requestFileInformation(downloadLink);
        this.doFree(downloadLink);
    }

    @SuppressWarnings("deprecation")
    private void doFree(final DownloadLink downloadLink) throws Exception {
        // experimental code - raz
        // so called 15mins between your last download, ends up with your IP blocked for the day..
        // Trail and error until we find the sweet spot.
        if (checkShowFreeDialog(getHost())) {
            showFreeDialog(getHost());
        }
        final boolean useExperimentalHandling = this.getPluginConfig().getBooleanProperty(this.EXPERIMENTALHANDLING, false);
        final String currentIP = this.getIP();
        if (useExperimentalHandling) {
            this.logger.info("New Download: currentIP = " + currentIP);
            if (this.ipChanged(currentIP, downloadLink) == false) {
                long lastdownload_timestamp = timeBefore.get();
                if (lastdownload_timestamp == 0) {
                    lastdownload_timestamp = getPluginSavedLastDownloadTimestamp();
                }
                final long passedTimeSinceLastDl = System.currentTimeMillis() - lastdownload_timestamp;
                this.logger.info("Wait time between downloads to prevent your IP from been blocked for 1 Day!");
                if (passedTimeSinceLastDl < FREE_RECONNECTWAIT_GENERAL) {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Wait time between download session", FREE_RECONNECTWAIT_GENERAL - passedTimeSinceLastDl);
                }
            }
        }
        if (this.br.containsHTML(RapidGatorNet.PREMIUMONLYTEXT)) {
            try {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            } catch (final Throwable e) {
                if (e instanceof PluginException) {
                    throw (PluginException) e;
                }
            }
            throw new PluginException(LinkStatus.ERROR_FATAL, "This file can only be downloaded by premium users");
        }
        try {
            // end of experiment
            if (this.br.containsHTML("You have reached your daily downloads limit\\. Please try")) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "You have reached your daily downloads limit", FREE_RECONNECTWAIT_DAILYLIMIT);
            } else if (br.containsHTML(">[\\r\n ]+You have reached your hourly downloads limit\\.")) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "You have reached your hourly downloads limit", FREE_RECONNECTWAIT_GENERAL);
            }
            if (this.br.containsHTML("(You can`t download not more than 1 file at a time in free mode\\.<|>Wish to remove the restrictions\\?)")) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "You can't download more than one file within a certain time period in free mode", FREE_RECONNECTWAIT_OTHERS);
            }
            final String freedlsizelimit = this.br.getRegex("'You can download files up to ([\\d\\.]+ ?(MB|GB)) in free mode<").getMatch(0);
            if (freedlsizelimit != null) {
                try {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
                } catch (final Throwable e) {
                    if (e instanceof PluginException) {
                        throw (PluginException) e;
                    }
                }
                throw new PluginException(LinkStatus.ERROR_FATAL, JDL.L("plugins.hoster.rapidgatornet.only4premium", "No free download link for this file"));
            }
            final String reconnectWait = this.br.getRegex("Delay between downloads must be not less than (\\d+) min\\.<br>Don`t want to wait\\? <a style=\"").getMatch(0);
            if (reconnectWait != null) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, (Integer.parseInt(reconnectWait) + 1) * 60 * 1000l);
            }
            final String fid = this.br.getRegex("var fid = (\\d+);").getMatch(0);
            if (fid == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            // far as I can tell it's not needed.
            final String[] sitelinks = HTMLParser.getHttpLinks(this.br.toString(), null);
            for (final String link : sitelinks) {
                if (link.matches("(.+\\.(js|css))")) {
                    final Browser br2 = this.br.cloneBrowser();
                    this.simulateBrowser(br2, link);
                }
            }
            int wait = 30;
            final String waittime = this.br.getRegex("var secs = (\\d+);").getMatch(0);
            if (waittime != null) {
                wait = Integer.parseInt(waittime);
            }
            Browser br2 = this.br.cloneBrowser();
            RapidGatorNet.prepareBrowser(br2);
            br2.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br2.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
            br2.getPage("http://rapidgator.net/download/AjaxStartTimer?fid=" + fid);
            final String sid = br2.getRegex("sid\":\"([a-zA-Z0-9]{32})").getMatch(0);
            String state = br2.getRegex("state\":\"([^\"]+)").getMatch(0);
            if (!"started".equalsIgnoreCase(state)) {
                if (br2.toString().equals("No htmlCode read")) {
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Unknown server error", 2 * 60 * 1000l);
                }
                this.logger.info(br2.toString());
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (sid == null) {
                this.logger.info(br2.toString());
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            this.sleep((wait + 5) * 1001l, downloadLink);
            /* needed so we have correct referrer ;) (back to original br) */
            br2 = this.br.cloneBrowser();
            RapidGatorNet.prepareBrowser(br2);
            br2.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br2.getPage("http://rapidgator.net/download/AjaxGetDownloadLink?sid=" + sid);
            state = br2.getRegex("state\":\"(.*?)\"").getMatch(0);
            if (!"done".equalsIgnoreCase(state)) {
                if (br2.containsHTML("wait specified time")) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "ServerIssue", 5 * 60 * 1000l);
                }
                this.logger.info(br2.toString());
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final URLConnectionAdapter con1 = this.br.openGetConnection("http://rapidgator.net/download/captcha");
            if (con1.getResponseCode() == 302) {
                try {
                    con1.disconnect();
                } catch (final Throwable e) {
                }
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "ServerIssue", 5 * 60 * 1000l);
            } else if (con1.getResponseCode() == 403) {
                try {
                    con1.disconnect();
                } catch (final Throwable e) {
                }
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 5 * 60 * 1000l);
            } else if (con1.getResponseCode() == 500) {
                try {
                    con1.disconnect();
                } catch (final Throwable e) {
                }
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Downloading is not possible at the moment", FREE_RECONNECTWAIT_OTHERS);
            }
            // wasn't needed for raz, but psp said something about a redirect)
            this.br.followConnection();
            final long timeBeforeCaptchaInput = System.currentTimeMillis();
            if (this.br.containsHTML("(api\\.recaptcha\\.net/|google\\.com/recaptcha/api/)")) {
                final Recaptcha rc = new Recaptcha(br, this);
                for (int i = 0; i <= 5; i++) {
                    rc.parse();
                    rc.load();
                    final File cf = rc.downloadCaptcha(this.getLocalCaptchaFile());
                    final String c = this.getCaptchaCode("recaptcha", cf, downloadLink);
                    checkForExpiredCaptcha(timeBeforeCaptchaInput);
                    rc.getForm().put("DownloadCaptchaForm%5Bcaptcha%5D", "");
                    rc.setCode(c);
                    if (this.br.containsHTML("(>Please fix the following input errors|>The verification code is incorrect|api\\.recaptcha\\.net/|google\\.com/recaptcha/api/)")) {
                        continue;
                    }
                    break;
                }
            } else {
                if (this.br.containsHTML("//api\\.solvemedia\\.com/papi|//api\\.adscapchta\\.com/")) {
                    final Form captcha = this.br.getFormbyProperty("id", "captchaform");
                    if (captcha == null) {
                        this.logger.info(this.br.toString());
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }

                    captcha.put("DownloadCaptchaForm[captcha]", "");
                    String code = null, challenge = null;
                    final Browser capt = this.br.cloneBrowser();

                    if (this.br.containsHTML("//api\\.solvemedia\\.com/papi")) {

                        final org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia sm = new SolveMedia(br);
                        final File cf = sm.downloadCaptcha(this.getLocalCaptchaFile());
                        code = this.getCaptchaCode(cf, downloadLink);
                        checkForExpiredCaptcha(timeBeforeCaptchaInput);
                        final String chid = sm.getChallenge(code);

                        // if (chid == null) throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                        captcha.put("adcopy_challenge", chid);
                        captcha.put("adcopy_response", Encoding.urlEncode(code));

                    } else if (this.br.containsHTML("//api\\.adscapchta\\.com/")) {
                        final String captchaAdress = captcha.getRegex("<iframe src=\'(http://api\\.adscaptcha\\.com/NoScript\\.aspx\\?CaptchaId=\\d+&PublicKey=[^\'<>]+)").getMatch(0);
                        final String captchaType = new Regex(captchaAdress, "CaptchaId=(\\d+)&").getMatch(0);
                        if (captchaAdress == null || captchaType == null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }

                        if (!"3017".equals(captchaType)) {
                            this.logger.warning("ADSCaptcha: Captcha type not supported!");
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        capt.getPage(captchaAdress);
                        challenge = capt.getRegex("<img src=\"(http://api\\.adscaptcha\\.com//Challenge\\.aspx\\?cid=[^\"]+)").getMatch(0);
                        code = capt.getRegex("class=\"code\">([0-9a-f\\-]+)<").getMatch(0);

                        if (challenge == null || code == null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        challenge = this.getCaptchaCode(challenge, downloadLink);
                        checkForExpiredCaptcha(timeBeforeCaptchaInput);
                        captcha.put("adscaptcha_response_field", challenge);
                        captcha.put("adscaptcha_challenge_field", Encoding.urlEncode(code));
                    }
                    this.br.submitForm(captcha);
                }
            }
            final String redirect = br.getRedirectLocation();
            // Set-Cookie: failed_on_captcha=1; path=/ response if the captcha expired.
            if ("1".equals(this.br.getCookie("http://rapidgator.net", "failed_on_captcha")) || this.br.containsHTML("(>Please fix the following input errors|>The verification code is incorrect|api\\.recaptcha\\.net/|google\\.com/recaptcha/api/|//api\\.solvemedia\\.com/papi|//api\\.adscaptcha\\.com)") || (redirect != null && redirect.matches("https?://rapidgator\\.net/file/[a-z0-9]+"))) {
                try {
                    this.invalidateLastChallengeResponse();
                } catch (final Throwable e) {
                }
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            } else {
                try {
                    this.validateLastChallengeResponse();
                } catch (final Throwable e) {
                }
            }

            String dllink = this.br.getRegex("'(https?://[A-Za-z0-9\\-_]+\\.rapidgator\\.net//\\?r=download/index&session_id=[A-Za-z0-9]+)'").getMatch(0);
            if (dllink == null) {
                dllink = this.br.getRegex("'(https?://[A-Za-z0-9\\-_]+\\.rapidgator\\.net//\\?r=download/index&session_id=[A-Za-z0-9]+)'").getMatch(0);
            }
            // Old regex
            if (dllink == null) {
                dllink = this.br.getRegex("location\\.href = '(http://.*?)'").getMatch(0);
            }
            if (dllink == null) {
                this.logger.info(this.br.toString());
                if (this.br.getRegex("location\\.href = '/\\?r=download/index&session_id=[A-Za-z0-9]+'").matches()) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                handleErrorsBasic();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, dllink, true, 1);
            if (this.dl.getConnection().getContentType().contains("html")) {
                final URLConnectionAdapter con = dl.getConnection();
                if (con.getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404 (session expired?)", 30 * 60 * 1000l);
                } else if (con.getResponseCode() == 416) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 416", 10 * 60 * 1000l);
                }
                this.br.followConnection();
                if (this.br.containsHTML("<div class=\"error\">[\r\n ]+Error\\. Link expired. You have reached your daily limit of downloads\\.")) {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Link expired, or You've reached your daily limit ", FREE_RECONNECTWAIT_DAILYLIMIT);
                } else if (this.br.containsHTML("<div class=\"error\">[\r\n ]+File is already downloading</div>")) {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Download session in progress", FREE_RECONNECTWAIT_OTHERS);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
            RapidGatorNet.hasAttemptedDownloadstart.set(true);
            this.dl.startDownload();
        } finally {
            try {
                if (RapidGatorNet.hasAttemptedDownloadstart.get()) {
                    RapidGatorNet.timeBefore.set(System.currentTimeMillis());
                    this.getPluginConfig().setProperty(PROPERTY_LASTDOWNLOAD_TIMESTAMP, System.currentTimeMillis());
                }
                this.setIP(currentIP, downloadLink);
            } catch (final Throwable e) {
            }
        }

    }

    /**
     * If users need more than X seconds to enter the captcha and we actually send the captcha input after this time has passed, rapidgator
     * will 'ban' the IP of the user for at least 60 minutes. This function is there to avoid this case. Instead of sending the captcha it
     * throws a retry exception, avoiding the 60+ minutes IP 'ban'.
     */
    private void checkForExpiredCaptcha(final long timeBefore) throws PluginException {
        final long passedTime = System.currentTimeMillis() - timeBefore;
        if (passedTime >= (FREE_CAPTCHA_EXPIRE_TIME - 1000)) {
            /*
             * Do NOT throw a captcha Exception here as it is not the users' fault that we cannot download - he simply took too much time to
             * enter the captcha!
             */
            throw new PluginException(LinkStatus.ERROR_RETRY, "Captcha session expired");
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    private static AtomicInteger maxPrem = new AtomicInteger(1);

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        account.setProperty("PROPERTY_TEMP_DISABLED_TIMEOUT", Property.NULL);
        final AccountInfo ai = new AccountInfo();
        synchronized (RapidGatorNet.LOCK) {
            if (this.getPluginConfig().getBooleanProperty(this.DISABLE_API_PREMIUM, false)) {
                return this.fetchAccountInfo_web(account, ai);
            } else {
                return this.fetchAccountInfo_api(account, ai);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public AccountInfo fetchAccountInfo_api(final Account account, final AccountInfo ai) throws Exception {
        synchronized (RapidGatorNet.LOCK) {
            try {
                RapidGatorNet.maxPrem.set(1);
                final String sid = this.login_api(account);
                if (sid != null) {
                    account.setValid(true);
                    /* premium account */
                    final String expire_date = this.getJSonValueByKey("expire_date");
                    final String traffic_left = this.getJSonValueByKey("traffic_left");
                    final String reset_in = this.getJSonValueByKey("reset_in");
                    if (expire_date != null && traffic_left != null) {
                        /*
                         * expire date and traffic left are available, so it is a premium account, add one day extra to prevent it from
                         * expiring too early
                         */
                        ai.setValidUntil(Long.parseLong(expire_date) * 1000 + (24 * 60 * 60 * 1000l));
                        final long left = Long.parseLong(traffic_left);
                        ai.setTrafficLeft(left);
                        final long TB = 1024 * 1024 * 1024 * 1024l;
                        if (left > 3 * TB) {
                            ai.setTrafficMax(12 * TB);
                        } else if (left <= 3 * TB && left > TB) {
                            ai.setTrafficMax(3 * TB);
                        } else {
                            ai.setTrafficMax(TB);
                        }
                        if (!ai.isExpired()) {
                            account.setType(AccountType.PREMIUM);
                            /* account still valid */
                            try {
                                RapidGatorNet.maxPrem.set(-1);
                                account.setMaxSimultanDownloads(-1);
                                account.setConcurrentUsePossible(true);
                            } catch (final Throwable e) {
                                // not available in old Stable 0.9.581
                            }
                            if (account.getAccountInfo() == null) {
                                account.setAccountInfo(ai);
                            }
                            if (reset_in != null) {
                                // this is pointless, when traffic == 0 == core automatically sets ai.settraffic("No Traffic Left")
                                // ai.setStatus("Traffic exceeded " + reset_in);
                                // account.setAccountInfo(ai);

                                // is reset_in == seconds, * 1000 back into ms.
                                final Long resetInTimestamp = Long.parseLong(reset_in) * 1000;
                                account.setProperty("PROPERTY_TEMP_DISABLED_TIMEOUT", resetInTimestamp);
                                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                            } else {
                                ai.setStatus("Premium Account");
                            }
                            return ai;
                        }
                    }
                    ai.setStatus("Free Account");
                    account.setType(AccountType.FREE);
                    try {
                        RapidGatorNet.maxPrem.set(1);
                        account.setMaxSimultanDownloads(1);
                        account.setConcurrentUsePossible(false);
                    } catch (final Throwable e) {
                    }
                    return ai;
                }
                account.setType(null);
                account.setProperty("session_id", Property.NULL);
                account.setValid(false);
                return ai;
            } catch (final PluginException e) {
                if (e.getLinkStatus() != 256) {
                    account.setType(null);
                    account.setProperty("session_id", Property.NULL);
                    account.setValid(false);
                }
                throw e;
            }
        }
    }

    @SuppressWarnings("deprecation")
    public AccountInfo fetchAccountInfo_web(final Account account, final AccountInfo ai) throws Exception {
        RapidGatorNet.maxPrem.set(1);
        try {
            this.login_web(account, true);
        } catch (final PluginException e) {
            account.setValid(false);
            throw e;
        }
        final boolean isPremium = Account.AccountType.PREMIUM.equals(account.getType());
        if (!isPremium) {
            ai.setStatus("Registered (free) User");
            try {
                RapidGatorNet.maxPrem.set(1);
                // free accounts still have captcha.
                account.setMaxSimultanDownloads(1);
                account.setConcurrentUsePossible(false);
            } catch (final Throwable e) {
                // not available in old Stable 0.9.581
            }
            ai.setUnlimitedTraffic();
        } else {
            this.br.getPage("http://rapidgator.net/profile/index");
            String availableTraffic = this.br.getRegex(">Bandwith available</td>\\s+<td>\\s+([^<>\"]*?) of").getMatch(0);
            final String availableTrafficMax = this.br.getRegex(">Bandwith available</td>\\s+<td>\\s+[^<>\"]*? of (\\d+(\\.\\d+)? (?:MB|GB|TB))").getMatch(0);
            logger.info("availableTraffic = " + availableTraffic);
            if (availableTraffic != null) {
                Long avtr = SizeFormatter.getSize(availableTraffic.trim());
                if (avtr == 0) {
                    availableTraffic = "1024 GB"; // SizeFormatter can't handle TB (Temporary workaround)
                }
                ai.setTrafficLeft(SizeFormatter.getSize(availableTraffic.trim()));
                if (availableTrafficMax != null) {
                    ai.setTrafficMax(SizeFormatter.getSize(availableTrafficMax));
                }
            } else {
                /* Probably not true but our errorhandling for empty traffic should work well */
                ai.setUnlimitedTraffic();
            }
            String expireDate = this.br.getRegex("Premium services will end on ([^<>\"]*?)\\.<br").getMatch(0);
            if (expireDate == null) {
                expireDate = this.br.getRegex("login-open1.*Premium till (\\d{4}-\\d{2}-\\d{2})").getMatch(0);
            }
            if (expireDate == null) {
                /**
                 * eg subscriptions
                 */
                this.br.getPage("http://rapidgator.net/Payment/Payment");
                // expireDate = this.br.getRegex("style=\"width:60px;\">\\d+</td><td>([^<>\"]*?)</td>").getMatch(0);
                expireDate = this.br.getRegex("style=\"width.*?style=\"width.*?style=\"width.*?>([^<>\"]*?)<").getMatch(0);
            }
            if (expireDate == null) {
                this.logger.warning("Could not find expire date!");
                account.setValid(false);
                return ai;
            } else {
                ai.setValidUntil(TimeFormatter.getMilliSeconds(expireDate, "yyyy-MM-dd", Locale.ENGLISH) + 24 * 60 * 60 * 1000l);
            }
            ai.setStatus("Premium User");
            try {
                RapidGatorNet.maxPrem.set(-1);
                account.setMaxSimultanDownloads(-1);
                account.setConcurrentUsePossible(true);
            } catch (final Throwable e) {
                // not available in old Stable 0.9.581
            }
        }
        account.setValid(true);
        return ai;
    }

    private Cookies login_web(final Account account, final boolean force) throws Exception {
        synchronized (RapidGatorNet.LOCK) {
            try {
                // Load cookies
                this.br.setCookiesExclusive(true);
                RapidGatorNet.prepareBrowser(this.br);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null && account.isValid()) {
                    /*
                     * Make sure that we're logged in. Doing this for every downloadlink might sound like a waste of server capacity but
                     * really it doesn't hurt anybody.
                     */
                    this.br.setCookies(this.getHost(), cookies);
                    /* Even if login is forced, use cookies and check if they are still valid to avoid the captcha below */
                    this.br.setFollowRedirects(true);
                    this.br.getPage("http://rapidgator.net/");
                    if (this.br.containsHTML("<a href=\"/auth/logout\"")) {
                        setAccountTypeWebsite(account, this.br);
                        return cookies;
                    }
                }
                this.br.setFollowRedirects(true);
                /* Maybe cookies were used but are expired --> Clear them */
                br.clearCookies("https://rapidgator.net/");
                for (int i = 1; i <= 3; i++) {
                    logger.info("Site login attempt " + i + " of 3");
                    this.br.getPage("https://rapidgator.net/auth/login");
                    String loginPostData = "LoginForm%5Bemail%5D=" + Encoding.urlEncode(account.getUser()) + "&LoginForm%5Bpassword%5D=" + Encoding.urlEncode(account.getPass());
                    final Form loginForm = this.br.getFormbyProperty("id", "login");
                    final String captcha_url = br.getRegex("\"(/auth/captcha/v/[a-z0-9]+)\"").getMatch(0);
                    String code = null;
                    if (captcha_url != null) {
                        final DownloadLink dummyLink = new DownloadLink(this, "Account", "rapidgator.net", "http://rapidgator.net", true);
                        code = getCaptchaCode("https://rapidgator.net" + captcha_url, dummyLink);
                        loginPostData += "&LoginForm%5BverifyCode%5D=" + Encoding.urlEncode(code);
                    }
                    if (loginForm != null) {
                        String user = loginForm.getBestVariable("email");
                        String pass = loginForm.getBestVariable("password");
                        if (user == null) {
                            user = "LoginForm%5Bemail%5D";
                        }
                        if (pass == null) {
                            pass = "LoginForm%5Bpassword%5D";
                        }
                        loginForm.put(user, Encoding.urlEncode(account.getUser()));
                        loginForm.put(pass, Encoding.urlEncode(account.getPass()));
                        if (captcha_url != null) {
                            loginForm.put("LoginForm%5BverifyCode%5D", Encoding.urlEncode(code));
                        }
                        this.br.submitForm(loginForm);
                        loginPostData = loginForm.getPropertyString();
                    } else {
                        this.br.postPage("https://rapidgator.net/auth/login", loginPostData);
                    }
                    /* jsRedirect */
                    final String reDirHash = this.handleJavaScriptRedirect();
                    if (reDirHash != null) {
                        this.logger.info("JSRedirect in login");
                        // prob should be https also!!
                        this.br.postPage("https://rapidgator.net/auth/login", loginPostData + "&" + reDirHash);
                    }
                    if (this.br.getCookie(RapidGatorNet.MAINPAGE, "user__") == null) {
                        continue;
                    }
                    break;
                }

                if (this.br.getCookie(RapidGatorNet.MAINPAGE, "user__") == null) {
                    this.logger.info("disabled because of" + this.br.toString());
                    final String lang = System.getProperty("user.language");
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                setAccountTypeWebsite(account, this.br);
                account.saveCookies(this.br.getCookies(this.getHost()), "");
                return cookies;
            } catch (final PluginException e) {
                account.setType(null);
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    private void setAccountTypeWebsite(final Account account, final Browser br) {
        if (br.containsHTML("Account:\\&nbsp;<a href=\"/article/premium\">Free</a>")) {
            account.setType(AccountType.FREE);
        } else {
            account.setType(AccountType.PREMIUM);
        }
    }

    private String login_api(final Account account) throws Exception {
        URLConnectionAdapter con = null;
        synchronized (RapidGatorNet.LOCK) {
            try {
                this.prepareBrowser_api(this.br);
                con = this.br.openGetConnection(this.apiURL + "user/login?username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
                this.handleErrors_api(null, null, account, con);
                if (con.getResponseCode() == 200) {
                    this.br.followConnection();
                    final String session_id = this.getJSonValueByKey("session_id");
                    if (session_id != null) {
                        boolean isPremium = false;
                        final String expire_date = this.getJSonValueByKey("expire_date");
                        final String traffic_left = this.getJSonValueByKey("traffic_left");
                        if (expire_date != null && traffic_left != null) {
                            /*
                             * expire date and traffic left are available, so its a premium account, add one day extra to prevent it from
                             * expiring too early
                             */
                            final AccountInfo ai = new AccountInfo();
                            ai.setValidUntil(Long.parseLong(expire_date) * 1000 + (24 * 60 * 60 * 1000l));
                            isPremium = !ai.isExpired();
                        }
                        if (isPremium) {
                            account.setType(Account.AccountType.PREMIUM);
                        } else {
                            account.setType(Account.AccountType.FREE);
                        }
                        account.setProperty("session_id", session_id);
                    }
                    return session_id;
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable ignore) {
                }
            }
        }
    }

    private String getJSonValueByKey(final String key) {
        String result = this.br.getRegex("\"" + key + "\":\"([^\"]+)\"").getMatch(0);
        if (result == null) {
            result = this.br.getRegex("\"" + key + "\":(\\d+)").getMatch(0);
        }
        return result;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        this.correctDownloadLink(link);
        if (this.getPluginConfig().getBooleanProperty(this.DISABLE_API_PREMIUM, false)) {
            this.requestFileInformation(link);
            this.handlePremium_web(link, account);
        } else {
            this.handlePremium_api(link, account);
        }
    }

    public static String readErrorStream(final URLConnectionAdapter con) throws UnsupportedEncodingException, IOException {
        BufferedReader f = null;
        try {
            try {
                con.setAllowedResponseCodes(new int[] { con.getResponseCode() });
            } catch (final Throwable not09581) {
            }
            final InputStream es = con.getErrorStream();
            if (es == null) {
                throw new IOException("No errorstream!");
            }
            f = new BufferedReader(new InputStreamReader(es, "UTF8"));
            String line;
            final StringBuilder ret = new StringBuilder();
            final String sep = System.getProperty("line.separator");
            while ((line = f.readLine()) != null) {
                if (ret.length() > 0) {
                    ret.append(sep);
                }
                ret.append(line);
            }
            return ret.toString();
        } finally {
            try {
                f.close();
            } catch (final Throwable e) {
            }

        }
    }

    private void handleErrors_api(final String session_id, final DownloadLink link, final Account account, final URLConnectionAdapter con) throws PluginException, UnsupportedEncodingException, IOException {
        if (link != null && con.getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (link != null && con.getResponseCode() == 416) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 416", 5 * 60 * 1000l);
        }
        if (link != null && con.getResponseCode() == 500) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 500", 60 * 60 * 1000l);
        }
        if (con.getResponseCode() != 200) {
            synchronized (RapidGatorNet.LOCK) {
                final String lang = System.getProperty("user.language");
                final String errorMessage = RapidGatorNet.readErrorStream(con);
                this.logger.info("ErrorMessage: " + errorMessage);
                if (link != null && errorMessage.contains("Exceeded traffic")) {
                    final AccountInfo ac = new AccountInfo();
                    ac.setTrafficLeft(0);
                    account.setAccountInfo(ac);
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                }
                boolean sessionReset = false;
                if (session_id != null && session_id.equals(account.getStringProperty("session_id", null))) {
                    sessionReset = true;
                }
                if (errorMessage.contains("Please wait")) {
                    if (link == null) {
                        /* we are inside fetchAccountInfo */
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "Server says: 'Please wait ...'", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                    } else {
                        /* we are inside handlePremium */
                        throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Server says: 'Please wait ...'", 10 * 60 * 1000l);
                    }
                } else if (errorMessage.contains("User is not PREMIUM") || errorMessage.contains("This file can be downloaded by premium only") || errorMessage.contains("You can download files up to")) {
                    if (sessionReset) {
                        account.setProperty("session_id", Property.NULL);
                    }
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                } else if (errorMessage.contains("Login or password is wrong") || errorMessage.contains("Error: Error e-mail or password")) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                } else if (errorMessage.contains("Password cannot be blank")) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nDas Passwortfeld darf nicht leer sein!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nThe password field cannot be blank!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                } else if (errorMessage.contains("User is FROZEN")) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nAccount ist gesperrt!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nAccount is banned!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                } else if (StringUtils.containsIgnoreCase(errorMessage, "Error: ACCOUNT LOCKED FOR VIOLATION OF OUR TERMS. PLEASE CONTACT SUPPORT.")) {
                    // most likely account sharing as result of shared account dbs.
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nAccount Locked! Violation of Terms of Service!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else if (errorMessage.contains("Parameter login or password is missing")) {
                    /*
                     * Unusual case but this may also happen frequently if users use strange chars as usernme/password so simply treat this
                     * as "login/password wrong"!
                     */
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                } else if (errorMessage.contains("Session not exist")) {
                    if (sessionReset) {
                        account.setProperty("session_id", Property.NULL);
                    }
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                } else if (errorMessage.contains("\"Error: Error e\\-mail or password")) {
                    /* Usually comes with response_status 401 --> Not exactly sure what it means but probably some kind of account issue. */
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                } else if (errorMessage.contains("Error: You requested login to your account from unusual Ip address")) {
                    /* User needs to confirm his current IP. */
                    String statusMessage;
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        statusMessage = "\r\nBitte bestätige deine aktuelle IP Adresse über den Bestätigungslink per E-Mail um den Account wieder nutzen zu können.";
                    } else {
                        statusMessage = "\r\nPlease confirm your current IP adress via the activation link you got per mail to continue using this account.";
                    }
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, statusMessage, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                }
                if (con.getResponseCode() == 503 || errorMessage.contains("Service Temporarily Unavailable")) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Service Temporarily Unavailable", 5 * 60 * 1000l);
                }
                if (link != null) {
                    // disable api?
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
    }

    private void prepareBrowser_api(final Browser br) {
        RapidGatorNet.prepareBrowser(br);
        try {
            /* not available in old stable */
            if (br != null) {
                br.setAllowedResponseCodes(new int[] { 401, 402, 501, 423 });
            }
        } catch (final Throwable not09581) {
        }
    }

    @SuppressWarnings("deprecation")
    public void handlePremium_api(final DownloadLink link, final Account account) throws Exception {
        this.prepareBrowser_api(this.br);
        String session_id = null;
        boolean isPremium = false;
        synchronized (RapidGatorNet.LOCK) {
            session_id = account.getStringProperty("session_id", null);
            if (session_id == null) {
                session_id = this.login_api(account);
            }
            isPremium = Account.AccountType.PREMIUM.equals(account.getType());
        }
        if (isPremium == false) {
            this.handleFree(link);
            return;
        }
        if (session_id == null) {
            // disable api?
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        URLConnectionAdapter con = null;
        String fileName = link.getFinalFileName();
        if (fileName == null) {
            /* no final filename yet, do linkcheck */
            try {
                con = this.br.openGetConnection(this.apiURL + "file/info?sid=" + session_id + "&url=" + Encoding.urlEncode(link.getDownloadURL()));
                this.handleErrors_api(session_id, link, account, con);
                if (con.getResponseCode() == 200) {
                    this.br.followConnection();
                    fileName = this.getJSonValueByKey("filename");
                    final String fileSize = this.getJSonValueByKey("size");
                    final String fileHash = this.getJSonValueByKey("hash");
                    if (fileName != null) {
                        link.setFinalFileName(fileName);
                    }
                    if (fileSize != null) {
                        final long size = Long.parseLong(fileSize);
                        try {
                            link.setVerifiedFileSize(size);
                        } catch (final Throwable not09581) {
                            link.setDownloadSize(size);
                        }
                    }
                    if (fileHash != null) {
                        link.setMD5Hash(fileHash);
                    }
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable ignore) {
                }
            }
        }

        String url = null;
        try {
            con = this.br.openGetConnection(this.apiURL + "file/download?sid=" + session_id + "&url=" + Encoding.urlEncode(link.getDownloadURL()));
            this.handleErrors_api(session_id, link, account, con);
            if (con.getResponseCode() == 200) {
                this.br.followConnection();
                url = this.getJSonValueByKey("url");
                if (url != null) {
                    url = url.replace("\\", "");
                    url = url.replace("//?", "/?");
                }
            }
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable ignore) {
            }
        }
        if (url == null) {
            // disable api?
            // {"response":{"url":false},"response_status":200,"response_details":null}
            /*
             * This can happen if links go offline in the moment when the user is trying to download them - I (psp) was not able to
             * reproduce this so this is just a bad workaround! Correct server response would be:
             *
             * {"response":null,"response_status":404,"response_details":"Error: File not found"}
             *
             * TODO: Maybe move this info handleErrors_api
             */
            if (br.containsHTML("\"response_details\":null")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            throw new PluginException(LinkStatus.ERROR_RETRY);
        }
        this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, link, url, true, 0);
        if (this.dl.getConnection().getContentType().contains("html")) {
            this.logger.warning("The final dllink seems not to be a file!");
            this.handleErrors_api(session_id, link, account, this.dl.getConnection());
            // so we can see errors maybe proxy errors etc.
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        this.dl.startDownload();
    }

    @SuppressWarnings("deprecation")
    public void handlePremium_web(final DownloadLink link, final Account account) throws Exception {
        this.logger.info("Performing cached login sequence!!");
        Cookies cookies = this.login_web(account, false);
        final int repeat = 2;
        for (int i = 0; i <= repeat; i++) {
            this.br.setFollowRedirects(false);
            this.br.getPage(link.getDownloadURL());
            if (this.br.getCookie(RapidGatorNet.MAINPAGE, "user__") == null && i + 1 != repeat) {
                // lets login fully again, as hoster as removed premium cookie for some unknown reason...
                this.logger.info("Performing full login sequence!!");
                this.br = new Browser();
                cookies = this.login_web(account, true);
                continue;
            } else if (this.br.getCookie(RapidGatorNet.MAINPAGE, "user__") == null && i + 1 == repeat) {
                // failure
                this.logger.warning("handlePremium Failed! Please report to JDownloader Development Team.");
                synchronized (RapidGatorNet.LOCK) {
                    if (cookies == null) {
                        account.setProperty("cookies", Property.NULL);
                    }
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            } else {
                break;
            }
        }
        final boolean isPremium = Account.AccountType.PREMIUM.equals(account.getType());
        if (!isPremium) {
            this.doFree(link);
        } else {
            String dllink = this.br.getRedirectLocation();
            if (dllink == null) {
                /* jsRedirect */
                final String reDirHash = this.handleJavaScriptRedirect();
                if (reDirHash != null) {
                    this.logger.info("JSRedirect in premium");
                    this.br.getPage(link.getDownloadURL() + "?" + reDirHash);
                }
                dllink = this.br.getRegex("var premium_download_link = '(http://[^<>\"']+)';").getMatch(0);
                if (dllink == null) {
                    dllink = this.br.getRegex("'(http://pr_srv\\.rapidgator\\.net//\\?r=download/index&session_id=[A-Za-z0-9]+)'").getMatch(0);
                    if (dllink == null) {
                        dllink = this.br.getRegex("'(http://pr\\d+\\.rapidgator\\.net//\\?r=download/index&session_id=[A-Za-z0-9]+)'").getMatch(0);
                        if (dllink == null) {
                            if (this.br.containsHTML("You have reached quota|You have reached daily quota of downloaded information for premium accounts")) {
                                this.logger.info("You've reached daily download quota for " + account.getUser() + " account");
                                final AccountInfo ac = new AccountInfo();
                                ac.setTrafficLeft(0);
                                account.setAccountInfo(ac);
                                throw new PluginException(LinkStatus.ERROR_RETRY);
                            }
                            if (this.br.getCookie(RapidGatorNet.MAINPAGE, "user__") == null) {
                                this.logger.info("Account seems to be invalid!");
                                // throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                                account.setProperty("cookies", Property.NULL);
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }
                            this.logger.warning("Could not find 'dllink'. Please report to JDownloader Development Team");
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                    }
                }
            }
            this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, link, Encoding.htmlDecode(dllink), true, 0);
            if (this.dl.getConnection().getContentType().contains("html")) {
                this.logger.warning("The final dllink seems not to be a file!");
                this.handleErrors_api(null, link, account, this.dl.getConnection());
                // so we can see errors maybe proxy errors etc.
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            this.dl.startDownload();
        }
    }

    private void simulateBrowser(final Browser rb, final String url) {
        if (rb == null || url == null) {
            return;
        }
        URLConnectionAdapter con = null;
        try {
            con = rb.openGetConnection(url);
        } catch (final Throwable e) {
        } finally {
            try {
                con.disconnect();
            } catch (final Exception e) {
            }
        }
    }

    private void handleErrorsBasic() throws PluginException {
        if (br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404 (session expired?)", 30 * 60 * 1000l);
        }
    }

    private String getIP() throws PluginException {
        final Browser ip = new Browser();
        String currentIP = null;
        final ArrayList<String> checkIP = new ArrayList<String>(Arrays.asList(this.IPCHECK));
        Collections.shuffle(checkIP);
        for (final String ipServer : checkIP) {
            if (currentIP == null) {
                try {
                    ip.getPage(ipServer);
                    currentIP = ip.getRegex(this.IPREGEX).getMatch(0);
                    if (currentIP != null) {
                        break;
                    }
                } catch (final Throwable e) {
                }
            }
        }
        if (currentIP == null) {
            this.logger.warning("firewall/antivirus/malware/peerblock software is most likely is restricting accesss to JDownloader IP checking services");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        return currentIP;
    }

    private boolean ipChanged(final String IP, final DownloadLink link) throws PluginException {
        String currentIP = null;
        if (IP != null && new Regex(IP, this.IPREGEX).matches()) {
            currentIP = IP;
        } else {
            currentIP = this.getIP();
        }
        if (currentIP == null) {
            return false;
        }
        String lastIP = link.getStringProperty(this.LASTIP, null);
        if (lastIP == null) {
            lastIP = RapidGatorNet.lastIP.get();
        }
        return !currentIP.equals(lastIP);
    }

    private boolean setIP(final String IP, final DownloadLink link) throws PluginException {
        synchronized (this.IPCHECK) {
            if (IP != null && !new Regex(IP, this.IPREGEX).matches()) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (this.ipChanged(IP, link) == false) {
                // Static IP or failure to reconnect! We don't change lastIP
                this.logger.warning("Your IP hasn't changed since last download");
                return false;
            } else {
                final String lastIP = IP;
                link.setProperty(this.LASTIP, lastIP);
                RapidGatorNet.lastIP.set(lastIP);
                this.logger.info("LastIP = " + lastIP);
                return true;
            }
        }
    }

    private long getPluginSavedLastDownloadTimestamp() {
        return getLongProperty(getPluginConfig(), PROPERTY_LASTDOWNLOAD_TIMESTAMP, 0);
    }

    private static long getLongProperty(final Property link, final String key, final long def) {
        try {
            return link.getLongProperty(key, def);
        } catch (final Throwable e) {
            try {
                Object r = link.getProperty(key, def);
                if (r instanceof String) {
                    r = Long.parseLong((String) r);
                } else if (r instanceof Integer) {
                    r = ((Integer) r).longValue();
                }
                final Long ret = (Long) r;
                return ret;
            } catch (final Throwable e2) {
                return def;
            }
        }
    }

    private void setConfigElements() {
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), this.EXPERIMENTALHANDLING, JDL.L("plugins.hoster.rapidgatornet.useExperimentalWaittimeHandling", "Activate experimental waittime handling to prevent 24-hours IP ban from rapidgator?")).setDefaultValue(false));
        // Some users always get server error 500 via API
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), this.DISABLE_API_PREMIUM, JDL.L("plugins.hoster.rapidgatornet.disableAPIPremium", "Disable API for premium downloads (use web download)?")).setDefaultValue(false));
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return RapidGatorNet.maxPrem.get();
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    private static AtomicBoolean stableSucks = new AtomicBoolean(false);

    public static void showSSLWarning(final String domain) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    try {
                        final String lng = System.getProperty("user.language");
                        String message = null;
                        String title = null;
                        final boolean xSystem = CrossSystem.isOpenBrowserSupported();
                        if ("de".equalsIgnoreCase(lng)) {
                            title = domain + " :: Java 7+ && HTTPS Post Requests.";
                            message = "Wegen einem Bug in in Java 7+ in dieser JDownloader version koennen wir keine HTTPS Post Requests ausfuehren.\r\n";
                            message += "Wir haben eine Notloesung ergaenzt durch die man weiterhin diese JDownloader Version nutzen kann.\r\n";
                            message += "Bitte bedenke, dass HTTPS Post Requests als HTTP gesendet werden. Nutzung auf eigene Gefahr!\r\n";
                            message += "Falls du keine unverschluesselten Daten versenden willst, update bitte auf JDownloader 2!\r\n";
                            if (xSystem) {
                                message += "JDownloader 2 Installationsanleitung und Downloadlink: Klicke -OK- (per Browser oeffnen)\r\n ";
                            } else {
                                message += "JDownloader 2 Installationsanleitung und Downloadlink:\r\n" + new URL("http://board.jdownloader.org/showthread.php?t=37365") + "\r\n";
                            }
                        } else if ("es".equalsIgnoreCase(lng)) {
                            title = domain + " :: Java 7+ && HTTPS Solicitudes Post.";
                            message = "Debido a un bug en Java 7+, al utilizar esta versión de JDownloader, no se puede enviar correctamente las solicitudes Post en HTTPS\r\n";
                            message += "Por ello, hemos añadido una solución alternativa para que pueda seguir utilizando esta versión de JDownloader...\r\n";
                            message += "Tenga en cuenta que las peticiones Post de HTTPS se envían como HTTP. Utilice esto a su propia discreción.\r\n";
                            message += "Si usted no desea enviar información o datos desencriptados, por favor utilice JDownloader 2!\r\n";
                            if (xSystem) {
                                message += " Las instrucciones para descargar e instalar Jdownloader 2 se muestran a continuación: Hacer Click en -Aceptar- (El navegador de internet se abrirá)\r\n ";
                            } else {
                                message += " Las instrucciones para descargar e instalar Jdownloader 2 se muestran a continuación, enlace :\r\n" + new URL("http://board.jdownloader.org/showthread.php?t=37365") + "\r\n";
                            }
                        } else {
                            title = domain + " :: Java 7+ && HTTPS Post Requests.";
                            message = "Due to a bug in Java 7+ when using this version of JDownloader, we can not successfully send HTTPS Post Requests.\r\n";
                            message += "We have added a work around so you can continue to use this version of JDownloader...\r\n";
                            message += "Please be aware that HTTPS Post Requests are sent as HTTP. Use at your own discretion.\r\n";
                            message += "If you do not want to send unecrypted data, please upgrade to JDownloader 2!\r\n";
                            if (xSystem) {
                                message += "Jdownloader 2 install instructions and download link: Click -OK- (open in browser)\r\n ";
                            } else {
                                message += "JDownloader 2 install instructions and download link:\r\n" + new URL("http://board.jdownloader.org/showthread.php?t=37365") + "\r\n";
                            }
                        }
                        final int result = JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.CLOSED_OPTION, JOptionPane.CLOSED_OPTION);
                        if (xSystem && JOptionPane.OK_OPTION == result) {
                            CrossSystem.openURL(new URL("http://board.jdownloader.org/showthread.php?t=37365"));
                        }
                        RapidGatorNet.stableSucks.set(true);
                    } catch (final Throwable e) {
                    }
                }
            });
        } catch (final Throwable e) {
        }
    }

}