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

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "freefuckvidz.com" }, urls = { "http://(www\\.)?freefuckvidz\\.com/free\\-porn/\\d+" })
public class FreeFuckVidzCom extends PluginForHost {
    public FreeFuckVidzCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String        dllink                         = null;
    private boolean       free_limit_reached             = false;
    /* 2018-12-04: Disabled as this will cause 'limit reached' 401 errors! */
    private final boolean availablestatus_check_filesize = false;

    @Override
    public String getAGBLink() {
        return "http://www.freefuckvidz.com/dmca.html";
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        dllink = null;
        free_limit_reached = false;
        this.setBrowserExclusive();
        br.setCookiesExclusive(true);
        br.setFollowRedirects(true);
        this.br.setAllowedResponseCodes(410);
        br.getPage(downloadLink.getDownloadURL());
        if (br.getHttpConnection().getResponseCode() == 404 || this.br.getHttpConnection().getResponseCode() == 410 || br.containsHTML(">Removed from Free Fuck") || !br.getURL().startsWith("http://www.freefuckvidz")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<div class=\"content player clearfix\"><h1>([^<>\"]*?)</h1>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex(">Embed</a></span><h1>([^<>\"]*?)</h1>").getMatch(0);
        }
        if (filename == null) {
            filename = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
        }
        final String[] qualities = { "720p", "480p", "360p", "240p", "med", "low", "trailer" };
        for (final String quality : qualities) {
            getLink(quality);
            if (dllink != null) {
                break;
            }
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = filename.trim();
        final String ext = ".mp4";
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename) + ext);
        if (dllink != null && availablestatus_check_filesize) {
            dllink = Encoding.htmlDecode(dllink);
            Browser br2 = br.cloneBrowser();
            // In case the link redirects to the finallink
            br2.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            try {
                con = br2.openGetConnection(dllink);
                if (con.getResponseCode() == 401) {
                    free_limit_reached = true;
                    return AvailableStatus.TRUE;
                }
                if (!con.getContentType().contains("html")) {
                    downloadLink.setDownloadSize(con.getLongContentLength());
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    private void getLink(String quality) {
        dllink = br.getRegex("\"" + quality + "\",url:\"(http[^<>\"]*?)\"").getMatch(0);
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (free_limit_reached) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED);
        } else if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 401) {
                free_limit_reached = true;
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED);
            }
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
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
