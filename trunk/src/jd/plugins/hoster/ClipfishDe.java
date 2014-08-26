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

import jd.PluginWrapper;
import jd.http.URLConnectionAdapter;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.download.DownloadInterface;

import org.appwork.utils.StringUtils;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "clipfish.de" }, urls = { "clipfish://.+" }, flags = { 0 })
public class ClipfishDe extends PluginForHost {

    public ClipfishDe(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replaceAll("clipfish://", ""));
    }

    @Override
    public String getAGBLink() {
        return "http://www.clipfish.de/agb/";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 20;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        final LinkStatus linkStatus = downloadLink.getLinkStatus();
        final String dllink = downloadLink.getDownloadURL();
        String type = downloadLink.getStringProperty("MEDIA_TYPE");
        if (type != null && !"video".equalsIgnoreCase(type)) {
            // this will throw statserv errors.
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unsupported Media Type: " + type);
        }
        if (dllink.startsWith("http")) {
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, downloadLink.getDownloadURL());
            if (dl.getConnection().getLongContentLength() == 0) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        } else if (dllink.startsWith("rtmp")) {
            if (downloadLink.getStringProperty("FLASHPLAYER", null) == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl = new RTMPDownload(this, downloadLink, dllink);
            setupRTMPConnection(dllink, dl, downloadLink.getStringProperty("FLASHPLAYER"));
            ((RTMPDownload) dl).startDownload();
        } else {
            logger.severe("Plugin out of date for link: " + downloadLink.getDownloadURL());
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        if (downloadLink.getDownloadURL().startsWith("http")) {
            final URLConnectionAdapter con = br.openHeadConnection(downloadLink.getDownloadURL());
            try {
                if (con.getResponseCode() == 200 && StringUtils.containsIgnoreCase(con.getContentType(), "video") && con.getCompleteContentLength() > 0) {
                    downloadLink.setVerifiedFileSize(con.getCompleteContentLength());
                } else {
                    return AvailableStatus.FALSE;
                }
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    private void setupRTMPConnection(String stream, DownloadInterface dl, String fp) {
        jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();
        if (stream.contains("mp4:") && stream.contains("auth=")) {
            String pp = "mp4:" + stream.split("mp4:")[1];
            rtmp.setPlayPath(pp);
            rtmp.setUrl(stream.split("mp4:")[0]);
            rtmp.setApp("ondemand?ovpfv=2.0&" + new Regex(pp, "(auth=.*?)$").getMatch(0));
            rtmp.setSwfUrl(fp);
            rtmp.setResume(true);
        }
    }

}