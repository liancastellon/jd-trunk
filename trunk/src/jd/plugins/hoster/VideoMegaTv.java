//jDownloader - Downloadmanager
//Copyright (C) 2012  JD-Team support@jdownloader.org
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

import jd.PluginWrapper;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "videomega.tv" }, urls = { "http://(www\\.)?videomega\\.tv/(iframe\\.php)?\\?ref=[A-Za-z0-9]+" }, flags = { 0 })
public class VideoMegaTv extends antiDDoSForHost {

    public VideoMegaTv(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://videomega.tv/terms.html";
    }

    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload("http://videomega.tv/?ref=" + new Regex(link.getDownloadURL(), "([A-Za-z0-9]+)$").getMatch(0));
    }

    @Override
    protected boolean useRUA() {
        return true;
    }

    private String fuid = null;

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        br = new Browser();
        this.setBrowserExclusive();
        br.setFollowRedirects(false);
        fuid = new Regex(link.getDownloadURL(), "([A-Za-z0-9]+)$").getMatch(0);
        final String page = "http://videomega.tv/?ref=" + fuid + "&width=595&height=340";
        getPage(page);
        String redirect = br.getRedirectLocation();
        if (redirect != null) {
            if (redirect.contains("google.com/")) {
                // without referer it will most likely redirect to google
                br.getHeaders().put("Referer", page);
                getPage(page);
                redirect = br.getRedirectLocation();
            }
            if (!redirect.contains("videomega.tv/")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        }
        if (br.containsHTML(">VIDEO NOT FOUND")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        link.setFinalFileName(fuid + ".mp4");
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        // cdn
        getPage("/cdn.php?ref=" + fuid + "&width=1000&height=450");
        if (br.containsHTML(">Sorry an error has occurred converting this video\\.<")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Hoster issue converting video.", 30 * 60 * 1000l);
        }
        String[] escaped = br.getRegex("document\\.write\\(unescape\\(\"([^<>\"]*?)\"").getColumn(0);
        if (escaped == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        for (String escape : escaped) {
            Browser br2 = br.cloneBrowser();
            escape = Encoding.htmlDecode(escape);
            String dllink = new Regex(escape, "file:\\s*\"(https?://[^<>\"]*?)\"").getMatch(0);
            if (dllink == null) {
                dllink = new Regex(escape, "\"(https?://([a-z0-9]+\\.){1,}videomega\\.tv/vid(?eo)?s/[a-z0-9]+/[a-z0-9]+/[a-z0-9]+\\.mp4)\"").getMatch(0);
            }
            if (dllink == null) {
                if (!escaped[escaped.length - 1].equals(escape)) {
                    // this tests if link is last in array
                    continue;
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            try {
                dl = jd.plugins.BrowserAdapter.openDownload(br2, downloadLink, dllink, true, 0);
            } catch (final Exception t) {
                if (!escaped[escaped.length - 1].equals(escape)) {
                    // this tests if link is last in array
                    continue;
                }
                throw t;
            }
            if (dl.getConnection().getContentType().contains("html")) {
                if (!escaped[escaped.length - 1].equals(escape)) {
                    // this tests if link is last in array
                    continue;
                }
                br2.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
            break;
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}