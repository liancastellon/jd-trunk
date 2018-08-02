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

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.jdownloader.downloader.hds.HDSDownloader;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
import jd.plugins.download.DownloadInterface;

/*
 * vrt.be network
 * new content handling --> data-video-src
 */
@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "deredactie.be", "sporza.be", "cobra.canvas.be" }, urls = { "https?://([a-z0-9]+\\.)?deredactiedecrypted\\.be/(permalink/\\d\\.\\d+(\\?video=\\d\\.\\d+)?|cm/vrtnieuws([^/]+)?/(mediatheek|videozone).+)", "https?://([a-z0-9]+\\.)?sporzadecrypted\\.be/.+", "https?://([a-z0-9]+\\.)?canvasdecrypted\\.be/.+" })
public class DeredactieBe extends PluginForHost {
    public DeredactieBe(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://deredactie.be/";
    }

    private Browser ajax     = null;
    private String  finalurl = null;

    private static enum protocol {
        HTTP,
        RTMP,
        HDS,
        HLS,
        DASH
    }

    private protocol ptcrl = null;

    @Override
    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("decrypted.be/", ".be/"));
        // not sure what this does! -raz
        link.setUrlDownload(link.getDownloadURL().replaceAll("/cm/vrtnieuws/mediatheek/[^/]+/[^/]+/[^/]+/([0-9\\.]+)(.+)?", "/permalink/$1"));
        link.setUrlDownload(link.getDownloadURL().replaceAll("/cm/vrtnieuws([^/]+)?/mediatheek(\\w+)?/([0-9\\.]+)(.+)?", "/permalink/$3"));
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink downloadLink) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(downloadLink.getDownloadURL());
        // Link offline
        if (br.containsHTML("(>Pagina \\- niet gevonden<|>De pagina die u zoekt kan niet gevonden worden)")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        HashMap<String, String> mediaValue = new HashMap<String, String>();
        for (String[] s : br.getRegex("data\\-video\\-([^=]+)=\"([^\"]+)\"").getMatches()) {
            mediaValue.put(s[0], s[1]);
        }
        // Nothing to download
        if (mediaValue == null || mediaValue.size() == 0) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        finalurl = mediaValue.get("src");
        final String filename = mediaValue.get("title");
        if (finalurl == null || filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (finalurl.contains("youtube")) {
            /* Therefore a decrypter would be needed! */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String ext = getFileNameExtensionFromString(finalurl, ".mp4");
        if (ext == null || ext.length() > 5 || ext.equals(".m3u8")) {
            ext = ".mp4";
        }
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename.trim()).replaceAll("\"", "").replace("/", "-") + ext);
        if (finalurl.contains("vod.stream.vrt.be") && finalurl.endsWith(".m3u8")) {
            /* 2018-08-02: Same here:https://github.com/rg3/youtube-dl/blob/master/youtube_dl/extractor/vrt.py */
            // <div class="video"
            // data-video-id="2138237_1155250086"
            // data-video-type="video"
            // data-video-src="http://vod.stream.vrt.be/deredactie/_definst_/2014/11/1210141103430708821_Polopoly_16x9_DV25_NGeo.mp4"
            // data-video-title="Reyers Laat - 3/11/14"
            // data-video-iphone-server="http://vod.stream.vrt.be/deredactie/_definst_"
            // data-video-iphone-path="mp4:2014/11/1210141103430708821_Polopoly_16x9_DV25_NGeo.mp4/playlist.m3u8"
            // data-video-rtmp-server=""
            // data-video-rtmp-path=""
            // data-video-sitestat-program="reyers_laat_-_31114_id_1-2138237"
            // data-video-sitestat-playlist=""
            // data-video-sitestat-site="deredactie-be"
            // data-video-sitestat-pubdate="1415048604184"
            // data-video-sitestat-cliptype="FULL_EPISODE"
            // data-video-sitestat-duration="2564"
            // data-video-autoplay="true"
            // data-video-whatsonid=""
            // data-video-geoblocking="false"
            // data-video-prerolls-enabled="false"
            // data-video-preroll-category="senieuws"
            // data-video-duration="2564000">
            try {
                // Request
                // URL:http://vod.stream.vrt.be/deredactie/_definst_/mp4:2014/11/1210141103430708821_Polopoly_16x9_DV25_NGeo.mp4/manifest.f4m
                ajaxGetPage(finalurl + "/manifest.f4m");
                final DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                final XPath xPath = XPathFactory.newInstance().newXPath();
                Document d = parser.parse(new ByteArrayInputStream(ajax.toString().getBytes("UTF-8")));
                NodeList mediaUrls = (NodeList) xPath.evaluate("/manifest/media", d, XPathConstants.NODESET);
                Node media;
                for (int j = 0; j < mediaUrls.getLength(); j++) {
                    media = mediaUrls.item(j);
                    // System.out.println(new String(Base64.decode(xPath.evaluate("/manifest/media[" + (j + 1) + "]/metadata", d).trim())));
                    String temp = getAttByNamedItem(media, "url");
                    if (temp != null) {
                        finalurl = finalurl + "/" + temp;
                        break;
                    }
                }
                ptcrl = protocol.HDS;
                return AvailableStatus.TRUE;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        if (finalurl.endsWith(".m3u8")) {
            ptcrl = protocol.HLS;
        } else if (finalurl.equalsIgnoreCase("http://do.not/use/this/url")) {
            /* 2018-08-02 */
            ptcrl = protocol.DASH;
        } else {
            final Browser br2 = br.cloneBrowser();
            URLConnectionAdapter con = null;
            try {
                con = br2.openGetConnection(finalurl);
                if (!con.getContentType().contains("html")) {
                    downloadLink.setDownloadSize(con.getLongContentLength());
                    ptcrl = protocol.HTTP;
                } else {
                    br2.followConnection();
                    finalurl = mediaValue.get("rtmp-server");
                    finalurl = finalurl != null && mediaValue.get("rtmp-path") != null ? finalurl + "@" + mediaValue.get("rtmp-path") : null;
                    if (finalurl == null) {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                    ptcrl = protocol.RTMP;
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

    private void ajaxGetPage(final String url) throws Exception {
        ajax = br.cloneBrowser();
        ajax.getHeaders().put("Accept", "*/*");
        ajax.getPage(url);
    }

    /**
     * lets try and prevent possible NPE from killing the progress.
     *
     * @author raztoki
     * @param n
     * @param item
     * @return
     */
    private String getAttByNamedItem(final Node n, final String item) {
        final String t = n.getAttributes().getNamedItem(item).getTextContent();
        return (t != null ? t.trim() : null);
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (ptcrl == protocol.DASH) {
            throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming type");
        }
        if (protocol.HDS.equals(ptcrl)) {
            dl = new HDSDownloader(downloadLink, br, finalurl);
            dl.startDownload();
            return;
        } else if (protocol.HLS.equals(ptcrl)) {
            this.br.getPage(finalurl);
            if (this.br.getHttpConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "This content is GEO-blocked in your country");
            } else if (this.br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            final HlsContainer hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(this.br));
            if (hlsbest == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final String url_hls = hlsbest.getDownloadurl();
            checkFFmpeg(downloadLink, "Download a HLS Stream");
            dl = new HLSDownloader(downloadLink, br, url_hls);
            dl.startDownload();
            return;
        } else if (protocol.RTMP.equals(ptcrl)) {
            dl = new RTMPDownload(this, downloadLink, finalurl);
            setupRTMPConnection(dl);
            ((RTMPDownload) dl).startDownload();
        } else {
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, finalurl, false, 1);
            if (dl.getConnection().getContentType().contains("html")) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        }
    }

    private void setupRTMPConnection(final DownloadInterface dl) {
        final jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();
        rtmp.setPlayPath(finalurl.split("@")[1]);
        rtmp.setUrl(finalurl.split("@")[0]);
        rtmp.setSwfVfy("http://www.deredactie.be/html/flash/common/player.5.10.swf");
        rtmp.setResume(true);
        rtmp.setRealTime();
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
        if (link != null) {
            link.removeProperty(HDSDownloader.RESUME_FRAGMENT);
        }
    }
}