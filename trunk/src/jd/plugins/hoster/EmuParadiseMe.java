//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "emuparadise.me" }, urls = { "http://(www\\.)?emuparadise\\.me/[^<>/]+/[^<>/]+/\\d{4,}" }, flags = { 0 })
public class EmuParadiseMe extends PluginForHost {

    public EmuParadiseMe(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.emuparadise.me/contact.php";
    }

    private static Object        LOCK             = new Object();
    private static final String  COOKIE_HOST      = "http://emuparadise.me/";
    private static AtomicInteger maxFree          = new AtomicInteger(1);

    /* Books, movies and specials are "directlinks" (no captcha/waittime) */
    private static final String  HTML_TYPE_DIRECT = "Direct Download:</h2>";

    @SuppressWarnings({ "unchecked", "deprecation" })
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        link.setName(Encoding.htmlDecode(new Regex(link.getDownloadURL(), "emuparadise\\.me/[^<>/]+/([^<>/]+)/").getMatch(0)) + ".zip");
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        synchronized (LOCK) {
            /* Re-uses saved cookies to avoid captchas */
            final Object ret = this.getPluginConfig().getProperty("cookies", null);
            if (ret != null) {
                final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                    final String key = cookieEntry.getKey();
                    final String value = cookieEntry.getValue();
                    this.br.setCookie(COOKIE_HOST, key, value);
                }
            } else {
                /* Skips the captcha (tries to) */
                br.setCookie(COOKIE_HOST, "downloadcaptcha", "1");
            }
        }
        br.getPage(link.getDownloadURL());
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = null;
        String filesize = null;
        if (br.containsHTML(HTML_TYPE_DIRECT)) {
            filename = br.getRegex("\"http://[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+[^<>\"]*?/([^<>\"/]*?)\"").getMatch(0);
            filesize = br.getRegex("Size: (\\d+(?:\\.\\d+)?(?:K|M|G))<br>").getMatch(0);
            if (filesize != null) {
                filesize += "b";
            }
        } else {
            if (!br.containsHTML("id=\"Download\"")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            filename = br.getRegex("itemprop=\"name\">([^<>\"]*?)<br>").getMatch(0);
            filesize = br.getRegex("\\((\\d+(?:\\.\\d+)?(?:K|M|G))\\)").getMatch(0);
        }
        if (filename == null || filesize == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(encodeUnicode(Encoding.htmlDecode(filename.trim())) + ".zip");
        link.setDownloadSize(SizeFormatter.getSize(filesize + "b"));
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        String dllink = null;
        requestFileInformation(downloadLink);
        if (br.containsHTML(HTML_TYPE_DIRECT)) {
            dllink = br.getRegex("\"(http://[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+[^<>\"]*?/[^<>\"/]*?)\"").getMatch(0);
        } else {
            synchronized (LOCK) {
                br.getPage(br.getURL() + "-download");
                if (br.containsHTML("id=\"happy\\-hour\"") && !br.containsHTML("class=\"help tip\" style=\"display:none;\" id=\"happy\\-hour\">")) {
                    maxFree.set(2);
                } else {
                    maxFree.set(1);
                }
                dllink = checkDirectLink(downloadLink, "directlink");
                if (dllink == null) {
                    /* As long as the static cookie set captcha workaround works fine, */
                    if (br.containsHTML("solvemedia\\.com/papi/")) {
                        logger.info("Detected captcha method \"solvemedia\" for this host");
                        final PluginForDecrypt solveplug = JDUtilities.getPluginForDecrypt("linkcrypt.ws");
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
                        final String code = getCaptchaCode(cf, downloadLink);
                        final String chid = sm.getChallenge(code);
                        br.postPage(br.getURL(), "submit=+Verify+%26+Download&adcopy_response=" + Encoding.urlEncode(code) + "&adcopy_challenge=" + chid);
                        if (br.containsHTML("solvemedia\\.com/papi/")) {
                            throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                        }
                        /* Save cookies to avoid captchas in the future */
                        final HashMap<String, String> cookies = new HashMap<String, String>();
                        final Cookies add = this.br.getCookies(COOKIE_HOST);
                        for (final Cookie c : add.getCookies()) {
                            cookies.put(c.getKey(), c.getValue());
                        }
                        this.getPluginConfig().setProperty("cookies", cookies);
                    }
                    dllink = br.getRegex("\"[^<>\"]*?(/roms/get\\-download\\.php[^<>\"]*?)\"").getMatch(0);
                    if (dllink == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    dllink = "http://www.emuparadise.me" + Encoding.htmlDecode(dllink);
                }
            }
        }
        /* Without this the directlink won't be accepted! */
        br.getHeaders().put("Referer", "http://www.emuparadise.me/");
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 503) {
                /* Too many connections --> Happy hour is definitly not active --> Only allow 1 simultaneous download. */
                maxFree.set(1);
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Too many concurrent connections - wait before starting new downloads", 1 * 60 * 1000l);
            }
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty("directlink", dllink);
        downloadLink.setFinalFileName(Encoding.htmlDecode(getFileNameFromHeader(dl.getConnection())).trim());
        dl.startDownload();
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                /* Without this the directlink won't be accepted! */
                br2.getHeaders().put("Referer", "http://www.emuparadise.me/");
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    /* Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "⁄");
        output = output.replace("\\", "∖");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return maxFree.get();
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

}