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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "porntube.com" }, urls = { "https?://(www\\.)?(porntube\\.com/videos/[a-z0-9\\-]+_\\d+|embed\\.porntube\\.com/\\d+|porntube\\.com/embed/\\d+)" })
public class PornTubeCom extends PluginForHost {
    /* DEV NOTES */
    /* Porn_plugin */
    /* tags: fux.com, porntube.com, 4tube.com, pornerbros.com */
    private String DLLINK = null;

    public PornTubeCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.porntube.com/info#terms";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    private static final String TYPE_EMBED = "http://(www\\.)?(embed\\.porntube\\.com/\\d+|porntube\\.com/embed/\\d+)";

    public void correctDownloadLink(final DownloadLink link) {
        if (link.getDownloadURL().matches(TYPE_EMBED)) {
            link.setUrlDownload("http://www.porntube.com/videos/" + System.currentTimeMillis() + "_" + new Regex(link.getDownloadURL(), "(\\d+)$").getMatch(0));
        }
    }

    @SuppressWarnings({ "deprecation", "unchecked" })
    @Override
    public AvailableStatus requestFileInformation(DownloadLink downloadLink) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(downloadLink.getDownloadURL());
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("(page-not-found\\.jpg\"|<title>Error 404 \\- Page not Found \\| PornTube\\.com</title>|alt=\"Page not Found\"|>\\s*This video is no longer available\\s*<|Porn Videos Found<)") || br.getURL().contains("error=")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        /* 2019-01-14: Same for: porntube.com, pornerbros.com */
        String initialState = br.getRegex("window.INITIALSTATE = '([^']+)'").getMatch(0);
        String filename = null;
        String mediaID = null;
        String availablequalities = null;
        if (initialState == null) {
            filename = br.getRegex("itemprop=\"name\" content=\"([^<>\"]*?)\"").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("<title>([^<>]*?)</title>").getMatch(0);
            }
            mediaID = getMediaid(br);
            availablequalities = br.getRegex("\\((\\d+)\\s*,\\s*\\d+\\s*,\\s*\\[([0-9,]+)\\]\\);").getMatch(1);
            logger.info("availablequalities: " + availablequalities);
            if (availablequalities != null) {
                availablequalities = availablequalities.replace(",", "+");
            } else {
                availablequalities = "1080+720+480+360+240";
            }
        } else {
            String json = Encoding.htmlDecode(Encoding.Base64Decode(initialState));
            Map<String, Object> entries = (Map<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json);
            filename = (String) JavaScriptEngineFactory.walkJson(entries, "page/video/title");
            mediaID = String.valueOf(JavaScriptEngineFactory.walkJson(entries, "page/video/mediaId"));
            List<Map<String, Object>> encodings = (List<Map<String, Object>>) JavaScriptEngineFactory.walkJson(entries, "page/video/encodings");
            List<Integer> enc = new ArrayList<Integer>();
            for (Map<String, Object> encoding : encodings) {
                enc.add((Integer) encoding.get("height"));
            }
            Collections.sort(enc, Collections.reverseOrder());
            StringBuilder sb = new StringBuilder();
            for (int h : enc) {
                sb.append(String.valueOf(h));
                sb.append("+");
            }
            sb.delete(sb.length() - 1, sb.length());
            availablequalities = sb.toString();
        }
        if (mediaID == null || filename == null) {
            logger.info("mediaID: " + mediaID + ", filename: " + filename);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        br.getHeaders().put("Origin", "http://www.porntube.com");
        final boolean newWay = true;
        if (newWay) {
            /* 2017-05-31 */
            br.postPage("https://tkn.kodicdn.com/" + mediaID + "/desktop/" + availablequalities, "");
        } else {
            br.postPage("https://tkn.fux.com/" + mediaID + "/desktop/" + availablequalities, "");
        }
        // seems to be listed in order highest quality to lowest. 20130513
        getDllink(downloadLink);
        String ext = "mp4";
        if (DLLINK.contains(".flv")) {
            ext = "flv";
        }
        filename = filename.endsWith(".") ? filename + ext : filename + "." + ext;
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename.trim()));
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    public static String getMediaid(final Browser br) throws IOException {
        return getMediaid(br, br.toString());
    }

    public static String getMediaid(final Browser br, final String source) throws IOException {
        final Regex info = new Regex(source, "\\.ready\\(function\\(\\) \\{embedPlayer\\((\\d+), \\d+, \\[(.*?)\\],");
        String mediaID = info.getMatch(0);
        if (mediaID == null) {
            mediaID = new Regex(source, "\\$\\.ajax\\(url, opts\\);[\t\n\r ]+\\}[\t\n\r ]+\\}\\)\\((\\d+),").getMatch(0);
        }
        if (mediaID == null) {
            mediaID = new Regex(source, "id=\"download\\d+p\" data\\-id=\"(\\d+)\"").getMatch(0);
        }
        if (mediaID == null) {
            // just like 4tube/porntube/fux....<script id="playerembed" src...
            final String embed = new Regex(source, "/js/player/(?:embed|web)/\\d+(?:\\.js)?").getMatch(-1);
            if (embed != null) {
                br.getPage(embed);
                mediaID = br.getRegex("\\((\\d+)\\s*,\\s*\\d+\\s*,\\s*\\[([0-9,]+)\\]\\);").getMatch(0); // $.ajax(url,opts);}})(
            }
        }
        return mediaID;
    }

    private void getDllink(final DownloadLink link) throws Exception {
        String finallink = null;
        final String[] qualities = new String[] { "1080", "720", "480", "360", "240" };
        for (final String quality : qualities) {
            if (br.containsHTML("\"" + quality + "\"")) {
                finallink = br.getRegex("\"" + quality + "\":\\{\"status\":\"success\",\"token\":\"(http[^<>\"]*?)\"").getMatch(0);
                if (finallink != null && checkDirectLink(link, finallink) != null) {
                    break;
                }
            }
        }
        if (finallink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        DLLINK = finallink;
    }

    private String checkDirectLink(final DownloadLink link, String directlink) throws Exception {
        URLConnectionAdapter con = null;
        final Browser br2 = br.cloneBrowser();
        br2.setFollowRedirects(true);
        try {
            con = br2.openHeadConnection(directlink);
            if (!con.getContentType().contains("html")) {
                link.setDownloadSize(con.getLongContentLength());
            } else {
                directlink = null;
            }
        } catch (final Exception e) {
        } finally {
            try {
                con.disconnect();
            } catch (final Exception e) {
            }
        }
        return directlink;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.UnknownPornScript6;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}