//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.SubConfiguration;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "load.to" }, urls = { "https?://(www\\.)?load\\.to/[A-Za-z0-9]+/" })
public class LoadTo extends PluginForHost {
    public LoadTo(PluginWrapper wrapper) {
        super(wrapper);
        this.setConfigElements();
    }

    @Override
    public String getAGBLink() {
        return "http://www.load.to/terms.php";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return maxFree.get();
    }

    @Override
    public int getTimegapBetweenConnections() {
        return 2000;
    }

    /* Settings stuff */
    private static final String            ENABLE_UNLIMITED_MAXDLS      = "ENABLE_UNLIMITED_MAXDLS";
    // note: CAN NOT be negative or zero! (ie. -1 or 0) Otherwise math sections
    // fail. .:. use [1-20]
    private static AtomicInteger           totalMaxSimultanFreeDownload = new AtomicInteger(getMaxdls());
    // don't touch the following!
    private static AtomicInteger           maxFree                      = new AtomicInteger(1);
    private final String                   INVALIDLINKS                 = "http://(www\\.)?load\\.to/(news|imprint|faq)/";
    /* Connection stuff */
    private static final boolean           FREE_RESUME                  = false;
    private static final int               FREE_MAXCHUNKS               = 1;
    private static final int               FREE_MAXDOWNLOADS            = 1;
    private static AtomicReference<String> agent                        = new AtomicReference<String>(null);

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        if (link.getDownloadURL().matches(INVALIDLINKS)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        workAroundTimeOut(br);
        prepareBrowser();
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (br.containsHTML(">Can't find file")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("id=\"filename\">([^<\"]*?)</").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<title>?:Load\\.to -)?(\\s*Download of)?\\s*([^<>\"]*?)(?: // Load\\.to)?</title>").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("Download file\\s*:\\s*<br/>\\s*<h1>(.*?)</h1>").getMatch(0);
            }
        }
        final String filesize = br.getRegex("Size:\\s*(\\d+(\\.\\d+)?\\s*(KB|MB|GB))").getMatch(0);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(Encoding.htmlDecode(filename.trim()));
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        /* Nochmals das File überprüfen */
        requestFileInformation(downloadLink);
        /* Link holen */
        String linkurl = getLinkurl();
        br.setFollowRedirects(true);
        boolean captchaFailed = true;
        if (br.containsHTML("(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)")) {
            for (int i = 1; i <= 5; i++) {
                /* Captcha */
                final Recaptcha rc = new Recaptcha(br, this);
                rc.findID();
                rc.load();
                linkurl = getLinkurl();
                final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                final String c = getCaptchaCode("recaptcha", cf, downloadLink);
                final String postData = "recaptcha_challenge_field=" + rc.getChallenge() + "&recaptcha_response_field=" + Encoding.urlEncode(c) + "&returnUrl=" + Encoding.urlEncode(br.getURL());
                dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, linkurl, postData, FREE_RESUME, FREE_MAXCHUNKS);
                if (dl.getConnection().getContentType().contains("html")) {
                    br.followConnection();
                    if (br.containsHTML("(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)") || br.getURL().contains("load.to/?e=3")) {
                        /* Try to avoid block (loop) on captcha reload */
                        br.clearCookies("http://load.to/");
                        br.getHeaders().put("User-Agent", jd.plugins.hoster.MediafireCom.stringUserAgent());
                        br.getPage(downloadLink.getDownloadURL());
                        continue;
                    }
                }
                captchaFailed = false;
                break;
            }
            if (captchaFailed) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
        } else if (br.containsHTML("solvemedia\\.com/papi/")) {
            for (int i = 1; i <= 3; i++) {
                /* Captcha */
                final org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia sm = new org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia(br);
                File cf = null;
                try {
                    cf = sm.downloadCaptcha(getLocalCaptchaFile());
                } catch (final Exception e) {
                    if (org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia.FAIL_CAUSE_CKEY_MISSING.equals(e.getMessage())) {
                        throw new PluginException(LinkStatus.ERROR_FATAL, "Host side solvemedia.com captcha error - please contact the " + this.getHost() + " support");
                    }
                    throw e;
                }
                final String code = getCaptchaCode("solvemedia", cf, downloadLink);
                final String chid = sm.getChallenge(code);
                if (chid == null) {
                    logger.info("Invalid captcha answer");
                    continue;
                }
                final String postData = "adcopy_response=" + code + "&adcopy_challenge=" + Encoding.urlEncode(chid) + "&returnUrl=" + Encoding.urlEncode(br.getURL());
                dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, linkurl, postData, FREE_RESUME, FREE_MAXCHUNKS);
                if (dl.getConnection().getContentType().contains("html")) {
                    br.followConnection();
                    if (br.containsHTML("solvemedia\\.com/papi/") || br.getURL().contains("load.to/?e=3")) {
                        /* Try to avoid block (loop) on captcha reload */
                        br.clearCookies("http://load.to/");
                        br.getHeaders().put("User-Agent", jd.plugins.hoster.MediafireCom.stringUserAgent());
                        br.getPage(downloadLink.getDownloadURL());
                        logger.info("Invalid captcha answer");
                        continue;
                    }
                }
                captchaFailed = false;
                break;
            }
            if (captchaFailed) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
        } else {
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, linkurl, "", FREE_RESUME, FREE_MAXCHUNKS);
        }
        this.sleep(2 * 1000, downloadLink);
        if (dl.getConnection().getResponseCode() == 416) {
            logger.info("Resume failed --> Retrying from zero");
            downloadLink.setChunksProgress(null);
            throw new PluginException(LinkStatus.ERROR_RETRY);
        }
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML("file not exist")) {
                logger.info("File maybe offline");
            }
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, 30 * 60 * 1000l);
        }
        if (!dl.getConnection().isContentDisposition()) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        try {
            // add a download slot
            controlFree(+1);
            // start the dl
            dl.startDownload();
        } finally {
            // remove download slot
            controlFree(-1);
        }
    }

    private String getLinkurl() throws PluginException {
        String linkurl = Encoding.htmlDecode(new Regex(br, Pattern.compile("\"(https?://s\\d+\\.load\\.to/\\?t=\\d+)\"", Pattern.CASE_INSENSITIVE)).getMatch(0));
        if (linkurl == null) {
            linkurl = Encoding.htmlDecode(new Regex(br, Pattern.compile("<form method=\"post\" action=\"(https?://.*?)\"", Pattern.CASE_INSENSITIVE)).getMatch(0));
        }
        if (linkurl == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        return linkurl;
    }

    private void prepareBrowser() throws IOException {
        if (agent.get() == null) {
            /* we first have to load the plugin, before we can reference it */
            JDUtilities.getPluginForHost("mediafire.com");
            agent.set(jd.plugins.hoster.MediafireCom.stringUserAgent());
        }
        br.getHeaders().put("User-Agent", agent.get());
    }

    /**
     * Prevents more than one free download from starting at a given time. One step prior to dl.startDownload(), it adds a slot to maxFree
     * which allows the next singleton download to start, or at least try.
     *
     * This is needed because xfileshare(website) only throws errors after a final dllink starts transferring or at a given step within pre
     * download sequence. But this template(XfileSharingProBasic) allows multiple slots(when available) to commence the download sequence,
     * this.setstartintival does not resolve this issue. Which results in x(20) captcha events all at once and only allows one download to
     * start. This prevents wasting peoples time and effort on captcha solving and|or wasting captcha trading credits. Users will experience
     * minimal harm to downloading as slots are freed up soon as current download begins.
     *
     * @param controlFree
     *            (+1|-1)
     */
    public synchronized void controlFree(final int num) {
        logger.info("maxFree was = " + maxFree.get());
        maxFree.set(Math.min(Math.max(1, maxFree.addAndGet(num)), totalMaxSimultanFreeDownload.get()));
        logger.info("maxFree now = " + maxFree.get());
    }

    @Override
    public void init() {
        Browser.setRequestIntervalLimitGlobal(getHost(), 500);
    }

    @SuppressWarnings("deprecation")
    private static int getMaxdls() {
        if (SubConfiguration.getConfig("load.to").getBooleanProperty(ENABLE_UNLIMITED_MAXDLS, false)) {
            return 20;
        } else {
            return FREE_MAXDOWNLOADS;
        }
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ENABLE_UNLIMITED_MAXDLS, JDL.L("plugins.hoster.loadto.enable_unlimited_maxdls", "Enable unlimited (=20) max simultaneous downloads?\r\n<html><p style=\"color:#F62817\"><b>Warning:</b> This can cause server errors- or captcha loops!</p></html>")).setDefaultValue(false));
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
        link.setProperty("error", 0);
    }

    @Override
    public void resetPluginGlobals() {
    }

    /* TODO: remove me after 0.9xx public */
    private void workAroundTimeOut(final Browser br) {
        try {
            if (br != null) {
                br.setConnectTimeout(120 * 1000);
                br.setReadTimeout(120 * 1000);
            }
        } catch (Throwable e) {
        }
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        return true;
    }
}