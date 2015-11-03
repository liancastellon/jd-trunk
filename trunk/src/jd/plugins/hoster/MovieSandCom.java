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
import java.util.regex.Pattern;

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

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "moviesand.com" }, urls = { "http://(www\\.)?moviesand\\.com/(videos/\\d+/[a-z0-9\\-_]+\\.html|embedded/\\d+)" }, flags = { 0 })
public class MovieSandCom extends PluginForHost {

    public MovieSandCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    /* Porn_plugin */

    private String              DLLINK      = null;
    private static final String TYPE_NORMAL = "http://(www\\.)?moviesand\\.com/videos/\\d+/[a-z0-9\\-_]+\\.html";
    private static final String TYPE_EMBED  = "http://(www\\.)?moviesand\\.com/embedded/\\d+";

    @Override
    public String getAGBLink() {
        return "http://www.moviesand.com/terms.php";
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        final String fid;
        final String addedlink = link.getDownloadURL();
        if (new Regex(addedlink, Pattern.compile(TYPE_EMBED, Pattern.CASE_INSENSITIVE)).matches()) {
            fid = new Regex(addedlink, "(\\d+)$").getMatch(0);
            link.setUrlDownload("http://www.moviesand.com/videos/" + fid + "/xyz.html");
        } else {
            fid = new Regex(addedlink, "moviesand\\.com/videos/(\\d+)/").getMatch(0);
        }
        link.setLinkID(fid);
    }

    /**
     * Other way to get direct downloadlinks: http://www.moviesand.com/emconfig/ +videoID
     */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(downloadLink.getDownloadURL());
        if (br.getURL().equals("http://www.moviesand.com/404.php") || br.containsHTML("Page Not Found<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<title>MoviesAnd \\- ([^<>\"]*?) \\- Better than YouPorn and RedTube</title>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("itemprop=\"title\" style=\"color: #46A5F1;\">([^<>\"]*?)</span>").getMatch(0);
        }
        DLLINK = br.getRegex("\\'(?:file|video)\\'[\t\n\r ]*?:[\t\n\r ]*?\\'(http[^<>\"]*?)\\'").getMatch(0);
        if (DLLINK == null) {
            DLLINK = br.getRegex("(?:file|url):[\t\n\r ]*?(?:\"|\\')(http[^<>\"]*?)(?:\"|\\')").getMatch(0);
        }
        if (DLLINK == null) {
            DLLINK = br.getRegex("<source src=\"(https?://[^<>\"]*?)\" type=(?:\"|\\')video/(?:mp4|flv)(?:\"|\\')").getMatch(0);
        }
        if (filename == null || DLLINK == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        DLLINK = Encoding.htmlDecode(DLLINK);
        filename = filename.trim();
        String ext = DLLINK.substring(DLLINK.lastIndexOf("."));
        if (ext == null || ext.length() > 5) {
            ext = ".flv";
        }
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename) + ext);
        Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openGetConnection(DLLINK);
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
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
