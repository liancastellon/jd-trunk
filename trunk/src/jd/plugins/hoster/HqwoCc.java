//jDownloader - Downloadmanager
//Copyright (C) 2017  JD-Team support@jdownloader.org
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

import org.appwork.utils.StringUtils;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "hqwo.cc" }, urls = { "https?://(?:www\\.)?hqwo\\.cc/player/([a-f0-9]{32})" })
public class HqwoCc extends PluginForHost {
    public HqwoCc(PluginWrapper wrapper) {
        super(wrapper);
    }
    /* DEV NOTES */
    // Tags: Porn plugin
    // protocol: no https
    // other:

    /* Extension which will be used if no correct extension is found */
    private static final String  default_extension = ".mp4";
    /* Connection stuff */
    private static final boolean free_resume       = true;
    private static final int     free_maxchunks    = 0;
    private static final int     free_maxdownloads = -1;
    private String               dllink            = null;
    private boolean              server_issues     = false;

    @Override
    public String getAGBLink() {
        return "https://hqwo.cc/";
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        return new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(0);
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
        dllink = null;
        server_issues = false;
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getPluginPatternMatcher());
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String url_filename = getLinkID(link);
        /* Website does not have good filenames - first check if maybe decrypter set final filename! */
        String filename = link.getFinalFileName();
        if (StringUtils.isEmpty(filename)) {
            /* Fallback */
            filename = url_filename;
        }
        final String continue_url = br.getRegex("\\'(https?://[^/]+/runplayer/[^<>\"\\']+)\\'").getMatch(0);
        if (continue_url != null) {
            br.getPage(continue_url);
        }
        /* RegExes sometimes used for streaming */
        final String jssource = br.getRegex("play\\((.*?)\\);").getMatch(0);
        if (jssource != null) {
            try {
                HashMap<String, Object> entries = null;
                Object quality_temp_o = null;
                long quality_temp = 0;
                String quality_temp_str = null;
                long quality_best = 0;
                String dllink_temp = null;
                final ArrayList<Object> ressourcelist = (ArrayList) JavaScriptEngineFactory.jsonToJavaObject(jssource);
                for (final Object videoo : ressourcelist) {
                    entries = (HashMap<String, Object>) videoo;
                    dllink_temp = (String) entries.get("file");
                    quality_temp_o = entries.get("label");
                    if (quality_temp_o != null && quality_temp_o instanceof Long) {
                        quality_temp = JavaScriptEngineFactory.toLong(quality_temp_o, 0);
                    } else if (quality_temp_o != null && quality_temp_o instanceof String) {
                        quality_temp_str = (String) quality_temp_o;
                        if (quality_temp_str.matches("\\d+p.*?")) {
                            /* E.g. '360p' */
                            quality_temp = Long.parseLong(new Regex(quality_temp_str, "(\\d+)p").getMatch(0));
                        } else {
                            /* Bad / Unsupported format */
                            continue;
                        }
                    }
                    if (StringUtils.isEmpty(dllink_temp) || quality_temp == 0) {
                        continue;
                    } else if (dllink_temp.contains(".m3u8")) {
                        /* Skip hls */
                        continue;
                    }
                    if (quality_temp > quality_best) {
                        quality_best = quality_temp;
                        dllink = dllink_temp;
                    }
                }
                if (!StringUtils.isEmpty(dllink)) {
                    logger.info("BEST handling for multiple video source succeeded");
                }
            } catch (final Throwable e) {
                logger.info("BEST handling for multiple video source failed");
            }
        }
        // if (StringUtils.isEmpty(dllink)) {
        // dllink = br.getRegex("\\'(?:file|video)\\'[\t\n\r ]*?:[\t\n\r ]*?\\'(http[^<>\"]*?)\\'").getMatch(0);
        // }
        // if (StringUtils.isEmpty(dllink)) {
        // dllink = br.getRegex("(?:file|url):[\t\n\r ]*?(?:\"|\\')(http[^<>\"]*?)(?:\"|\\')").getMatch(0);
        // }
        // if (StringUtils.isEmpty(dllink)) {
        // dllink = br.getRegex("<source src=\"(https?://[^<>\"]*?)\"[^>]*?type=(?:\"|\\')video/(?:mp4|flv)(?:\"|\\')").getMatch(0);
        // }
        // if (StringUtils.isEmpty(dllink)) {
        // dllink = br.getRegex("property=\"og:video\" content=\"(http[^<>\"]*?)\"").getMatch(0);
        // }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        String ext;
        if (!StringUtils.isEmpty(dllink)) {
            ext = getFileNameExtensionFromString(dllink, default_extension);
            if (ext != null && !ext.matches("\\.(?:flv|mp4)")) {
                ext = default_extension;
            }
        } else {
            ext = default_extension;
        }
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        if (!StringUtils.isEmpty(dllink)) {
            dllink = Encoding.htmlDecode(dllink);
            link.setFinalFileName(filename);
            URLConnectionAdapter con = null;
            try {
                con = br.openHeadConnection(dllink);
                if (!con.getContentType().contains("html")) {
                    link.setDownloadSize(con.getLongContentLength());
                } else {
                    server_issues = true;
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        } else {
            /* We cannot be sure whether we have the correct extension or not! */
            link.setName(filename);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (server_issues) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 10 * 60 * 1000l);
        } else if (StringUtils.isEmpty(dllink)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, free_resume, free_maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            }
            br.followConnection();
            try {
                dl.getConnection().disconnect();
            } catch (final Throwable e) {
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_maxdownloads;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
