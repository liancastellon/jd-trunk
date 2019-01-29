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

import org.appwork.utils.StringUtils;

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

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "alotporn.com" }, urls = { "https?://(?:www\\.)?alotporn\\.com/(?:\\d+/[A-Za-z0-9\\-_]+/|(?:embed\\.php\\?id=|embed/)\\d+)|https?://m\\.alotporn\\.com/\\d+/[a-z0-9\\-]+/" })
public class AlotpornCom extends PluginForHost {
    public AlotpornCom(PluginWrapper wrapper) {
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
        return "http://www.alotporn.com/terms.php";
    }

    public void correctDownloadLink(final DownloadLink link) {
        final String fid;
        final String name_url;
        if (link.getDownloadURL().matches(".+(/embed/|embed\\.php\\?id=).+")) {
            fid = new Regex(link.getDownloadURL(), "(?:/embed/|embed\\.php\\?id=)(\\d+)").getMatch(0);
            /* Use dummy */
            name_url = Long.toString(System.currentTimeMillis());
        } else {
            final Regex linkinfo = new Regex(link.getDownloadURL(), "https?://[^/]+/(\\d+)/(.+)/$");
            fid = new Regex(link.getDownloadURL(), "https?://[^/]+/(\\d+)").getMatch(0);
            name_url = linkinfo.getMatch(1);
        }
        link.setLinkID(fid);
        link.setUrlDownload(String.format("http://www.%s/%s/%s/", this.getHost(), fid, name_url));
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        dllink = null;
        server_issues = false;
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final String url_filename;
        br.getPage(link.getDownloadURL());
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        url_filename = new Regex(br.getURL(), "([a-z0-9\\-]+)/?$").getMatch(0);
        String filename = br.getRegex("<div class=\"headline\">[\t\n\r ]*?<h1>([^<>\"]*?)</h1>").getMatch(0);
        if (StringUtils.isEmpty(filename)) {
            filename = jd.plugins.hoster.KernelVideoSharingCom.regexStandardTitleWithHost(br, this.getHost());
        }
        if (filename == null) {
            filename = url_filename;
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        dllink = getDllink();
        final String ext;
        if (!StringUtils.isEmpty(dllink)) {
            ext = getFileNameExtensionFromString(dllink, default_extension);
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
        /* 2019-01-29: URL can only be used once so we need to generate a new one after the availablecheck. */
        this.br = new Browser();
        this.br.setFollowRedirects(true);
        br.getPage(downloadLink.getPluginPatternMatcher());
        dllink = this.getDllink();
        if (StringUtils.isEmpty(dllink)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, dllink, free_resume, free_maxchunks);
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

    private String getDllink() {
        String dllink = null;
        String videoUrl = br.getRegex("video_alt_url\\s*?:\\s*?\\'(.+?)\\'").getMatch(0);
        if (videoUrl == null) {
            videoUrl = br.getRegex("video_url\\s*?:\\s*?\\'(.+?)\\'").getMatch(0);
        }
        if (videoUrl == null) {
            videoUrl = br.getRegex("<source src=(?:'|\")(http[^<>\"]*?)/?(?:'|\")").getMatch(0);
        }
        if (videoUrl == null) {
            return null;
        }
        if (videoUrl.startsWith("function")) {
            dllink = jd.plugins.hoster.KernelVideoSharingCom.getDllinkCrypted(br, videoUrl);
        } else {
            dllink = videoUrl;
        }
        String rnd = br.getRegex("rnd\\s*?:\\s*?\\'(.+?)\\'").getMatch(0);
        if (rnd != null) {
            dllink = dllink + "&rnd=" + rnd;
        }
        return dllink;
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
