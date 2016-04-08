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
import java.util.LinkedHashMap;

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

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "vidyard.com" }, urls = { "http://(?:www\\.)?[A-Za-z0-9]+\\.vidyard\\.com/watch/[A-Za-z0-9\\-_]+" }, flags = { 0 })
public class VidyardCom extends PluginForHost {

    public VidyardCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    // Tags:
    // protocol: no https
    // other:

    /* Extension which will be used if no correct extension is found */
    private static final String  default_Extension = ".mp4";
    /* Connection stuff */
    private static final boolean free_resume       = true;
    private static final int     free_maxchunks    = 0;
    private static final int     free_maxdownloads = -1;

    private String               dllink            = null;
    private boolean              server_issues     = false;

    @Override
    public String getAGBLink() {
        return "https://www.vidyard.com/terms/";
    }

    @SuppressWarnings({ "deprecation", "unchecked", "rawtypes" })
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        dllink = null;
        server_issues = false;
        final String fid = new Regex(link.getDownloadURL(), "/([^/]+)$").getMatch(0);
        link.setLinkID(fid);
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage("http://demos.vidyard.com/watch/" + fid);
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("property=\"og:title\" content=\"([^<>\"]+)\"").getMatch(0);
        if (filename == null) {
            filename = fid;
        }
        String fallback_sd_url = this.br.getRegex("property=\"og:video\" content=\"(https[^<>\"]+)\"").getMatch(0);

        try {
            this.br.getPage("http://play.vidyard.com/" + fid);
            final String json = this.br.getRegex("var vidyard_chapter_data = (\\[.*?\\]);").getMatch(0);
            ArrayList<Object> ressourcelist = (ArrayList) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(json);
            LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) ressourcelist.get(0);
            if (fallback_sd_url == null) {
                fallback_sd_url = (String) entries.get("sd_url");
            }
            if (fallback_sd_url == null) {
                fallback_sd_url = (String) entries.get("sd_unsecure_url");
            }
            ressourcelist = (ArrayList) entries.get("video_files");
            String profile = null;
            String url_temp = null;
            boolean stop = false;
            final String[] qualities = { "full_hd", "hd", "480", "sd" };
            for (final String quality : qualities) {
                for (final Object qualityo : ressourcelist) {
                    entries = (LinkedHashMap<String, Object>) qualityo;
                    profile = (String) entries.get("profile");
                    url_temp = (String) entries.get("url");
                    if (url_temp == null) {
                        url_temp = (String) entries.get("unsecure_url");
                    }
                    if (profile == null || url_temp == null) {
                        continue;
                    }
                    if (profile.equals(quality)) {
                        stop = true;
                        dllink = url_temp;
                        break;
                    }
                }
                if (stop) {
                    break;
                }
            }
        } catch (final Throwable e) {
        }

        if (dllink == null) {
            /* Last chance */
            dllink = fallback_sd_url;
        }
        if (filename == null || dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = Encoding.htmlDecode(dllink);
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        String ext = dllink.substring(dllink.lastIndexOf("."));
        /* Make sure that we get a correct extension */
        if (ext == null || !ext.matches("\\.[A-Za-z0-9]{3,5}")) {
            ext = default_Extension;
        }
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        link.setFinalFileName(filename);
        final Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openHeadConnection(dllink);
            if (!con.getContentType().contains("html")) {
                link.setDownloadSize(con.getLongContentLength());
                link.setProperty("directlink", dllink);
            } else {
                server_issues = true;
            }
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (server_issues) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 10 * 60 * 1000l);
        } else if (dllink == null) {
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

    /** Avoid chars which are not allowed in filenames under certain OS' */
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
