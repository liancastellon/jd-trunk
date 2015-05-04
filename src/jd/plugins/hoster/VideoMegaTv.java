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

import java.util.HashSet;
import java.util.Random;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "videomega.tv" }, urls = { "http://(www\\.)?videomega\\.tv/(?:(?:(?:iframe|cdn|view)\\.php)?\\?ref=|validatehash\\.php\\?hashkey=)[A-Za-z0-9]+" }, flags = { 0 })
public class VideoMegaTv extends antiDDoSForHost {

    public VideoMegaTv(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://videomega.tv/terms.html";
    }

    private static final String TYPE_HASH   = "http://(www\\.)?videomega\\.tv/(?:view\\.php\\?ref=|validatehash\\.php\\?hashkey=)[A-Za-z0-9]+";
    private static final String TYPE_NORMAL = "http://(www\\.)?videomega\\.tv/(?:(?:iframe|cdn)\\.php)?\\?ref=[A-Za-z0-9]+";

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        if (link.getDownloadURL().matches(TYPE_NORMAL)) {
            link.setUrlDownload("http://videomega.tv/?ref=" + new Regex(link.getDownloadURL(), "([A-Za-z0-9]+)$").getMatch(0));
        } else {
            link.setUrlDownload("http://videomega.tv/?ref=" + new Regex(link.getDownloadURL(), "([A-Za-z0-9]+)$").getMatch(0));
        }
    }

    @Override
    protected boolean useRUA() {
        return true;
    }

    private String               fuid              = null;
    /* Connection stuff */
    private static final boolean FREE_RESUME       = true;
    private static final int     FREE_MAXCHUNKS    = 0;
    private static final int     FREE_MAXDOWNLOADS = 20;

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        br = new Browser();
        fuid = new Regex(link.getDownloadURL(), "([A-Za-z0-9]+)$").getMatch(0);
        this.setBrowserExclusive();
        br.setFollowRedirects(false);
        if (link.getDownloadURL().matches(TYPE_HASH)) {
            br.setCookie("http://videomega.tv/", "_gat", "1");
            br.setCookie("http://videomega.tv/", "vid_mainpu", "true");
            br.setCookie("http://videomega.tv/", "vid_subpu", "1");
            br.setCookie("http://videomega.tv/", "hashopen", "1");
            br.setCookie("http://videomega.tv/", "noadvtday", "0");
            // br.getPage("http://videomega.tv/validatehash.php?hashkey=" + fuid);
            br.getPage("http://videomega.tv/iframe.php?ref=" + fuid + "&width=863&height=430");
            if (br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        } else {
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
        }
        link.setFinalFileName(fuid + ".mp4");
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        String dllink = null;
        requestFileInformation(downloadLink);
        // cdn
        getPage("/cdn.php?ref=" + fuid + "&width=1000&height=450");
        if (br.containsHTML(">Sorry an error has occurred converting this video\\.<")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Hoster issue converting video.", 30 * 60 * 1000l);
        }
        String[] escaped = br.getRegex("document\\.write\\(unescape\\(\"([^<>\"]*?)\"").getColumn(0);
        if (escaped != null && escaped.length > 0) {
            /* Old way */
            for (String escape : escaped) {
                Browser br2 = br.cloneBrowser();
                escape = Encoding.htmlDecode(escape);
                dllink = new Regex(escape, "file:\\s*\"(https?://[^<>\"]*?)\"").getMatch(0);
                if (dllink == null) {
                    dllink = new Regex(escape, "\"(https?://([a-z0-9]+\\.){1,}videomega\\.tv/vid(?:eo)?s/[a-z0-9]+/[a-z0-9]+/[a-z0-9]+\\.mp4)\"").getMatch(0);
                }
                if (dllink == null) {
                    if (!escaped[escaped.length - 1].equals(escape)) {
                        // this tests if link is last in array
                        continue;
                    }
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                try {
                    dl = jd.plugins.BrowserAdapter.openDownload(br2, downloadLink, dllink, FREE_RESUME, FREE_MAXCHUNKS);
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
                    handleServerErrors();
                    br2.followConnection();
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                dl.startDownload();
                break;
            }
        } else {
            /* New way */
            dllink = br.getRegex("<source src=\"(http://[^<>\"]*?)\"").getMatch(0);
            final String id = br.getRegex("id: \"(\\d+)\"").getMatch(0);
            if (dllink == null || id == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final Browser adbr = br.cloneBrowser();
            adbr.getPage("http://videomega.tv/vjs/plugin/css/videomega.css");
            final String[] adlinks = br.getRegex("\"(/[A-Z0-9]+/ad\\.php[^<>\"]*?)\"").getColumn(0);
            final HashSet<String> dupe = new HashSet<String>();
            if (adlinks != null) {
                for (final String adlink : adlinks) {
                    if (dupe.add(adlink)) {
                        adbr.getPage(adlink);
                        // final String ad_id = new Regex(adlink, "/([A-Za-z0-9]+)/").getMatch(0);
                        // final String nextone = adbr.getRegex("([A-Za-z0-9]+)= true;").getMatch(0);
                        // if (ad_id != null && nextone != null) {
                        // br.getPage("http://videomega.tv/" + ad_id + "/ad.php?id=" + nextone);
                        // }
                    }
                }
            }
            // adbr.setCookie("http://videomega.tv/", "__gmgvm", "ppm0v0eai4");
            adbr.getHeaders().put("Accept", "*/*");
            adbr.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            String ref = br.getRegex("referal: \"(http[^<>\"]*?)\"").getMatch(0);
            if (ref == null) {
                ref = downloadLink.getDownloadURL();
            }
            adbr.postPage("/upd_views.php", "id=" + id + "&referal=" + Encoding.urlEncode(ref));

            // br.getHeaders().put("Accept", "*/*");
            this.br = new Browser();
            br.getHeaders().put("Accept", "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5");
            br.getHeaders().put("Accept-Encoding", "identity;q=1, *;q=0");
            // this is needed
            String ran = "";
            while (ran.length() < 12) {
                ran = new Random().nextLong() + "";
            }
            ran = ran.substring(1, 11);
            br.setCookie(this.getHost(), "_ga", "GA1.2." + ran + "." + System.currentTimeMillis());
            // br.setCookie(this.getHost(), "__cfduid", "");
            this.br.setFollowRedirects(false);
            this.br.getHeaders().put("Referer", ref);

            dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, dllink, FREE_RESUME, FREE_MAXCHUNKS);
            if (dl.getConnection().getContentType().contains("html")) {
                handleServerErrors();
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        }
    }

    private void handleServerErrors() throws PluginException {
        if (dl.getConnection().getResponseCode() == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
        } else if (dl.getConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (dl.getConnection().getLongContentLength() < 10000l) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: File is too small", 60 * 60 * 1000l);
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return FREE_MAXDOWNLOADS;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}