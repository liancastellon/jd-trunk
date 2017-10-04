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

import org.appwork.utils.StringUtils;

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

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "nur.kz" }, urls = { "https?://videos\\.nur\\.kz/(?:serial/\\d+\\-[a-z0-9\\-]+/[a-z0-9\\-]+|view=[a-z0-9]+)" })
public class NurKz extends PluginForHost {
    public NurKz(PluginWrapper wrapper) {
        super(wrapper);
    }
    /* DEV NOTES */
    // Tags:
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
        return "http://videos.nur.kz/";
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        dllink = null;
        server_issues = false;
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final String url_filename;
        final String videoid;
        if (link.getDownloadURL().matches(".+view=.+")) {
            /* videoid already given :) */
            videoid = new Regex(link.getDownloadURL(), "([a-z0-9]+)$").getMatch(0);
            url_filename = videoid;
        } else {
            /* find videoid ... */
            url_filename = new Regex(link.getDownloadURL(), "serial/\\d+\\-(.+)").getMatch(0);
            br.getPage(link.getDownloadURL());
            if (br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            videoid = br.getRegex("data\\-video_id=\"([a-z0-9]+)\"").getMatch(0);
            if (videoid == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        link.setLinkID(videoid);
        br.getPage(String.format("http://player.nur.kz/service/player/%s", videoid));
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("Статус видео-файла не установлен")) {
            /* 2nd offline check */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String seasonnumber = this.br.getRegex("var season\\s*?=\\s*?\\'(\\d+)\\';").getMatch(0);
        final String episodenumber = this.br.getRegex("var episode\\s*?=\\s*?\\'(\\d+)\\';").getMatch(0);
        String filename = br.getRegex("var q = \\'([^<>\"\\']+)\\';").getMatch(0);
        if (StringUtils.isEmpty(filename)) {
            filename = url_filename;
        }
        if (seasonnumber != null && episodenumber != null) {
            filename += String.format("_S%02dE%02d", Integer.parseInt(seasonnumber), Integer.parseInt(episodenumber));
        }
        dllink = br.getRegex("<source src=\"(https?://[^<>\"]*?)\" type=(?:\"|\\')video/(?:mp4|flv)(?:\"|\\')").getMatch(0);
        if (StringUtils.isEmpty(dllink)) {
            dllink = br.getRegex("var backup\\s*?=\\s*?\\'(http[^<>\"]+)\\'").getMatch(0);
        }
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
                    link.setProperty("directlink", dllink);
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
