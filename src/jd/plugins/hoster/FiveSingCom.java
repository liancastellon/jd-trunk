//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
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
import java.util.Map;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "5sing.kugou.com" }, urls = { "http://(www\\.)?5sing\\.kugou\\.com/(f|y)c/\\d+\\.html" })
public class FiveSingCom extends PluginForHost {
    public FiveSingCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://5sing.com/";
    }

    private static final String CRIPPLEDLINK = "http://(www\\.)?5sing\\.kugou\\.com/(f|y)c/\\d+\\.html";

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (br.getURL().contains("FileNotFind") || br.getURL().contains("5sing.com/404.htm") || br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String extension = br.getRegex("(<em>)?格式：(</em>)?([^<>\"]*?)(<|&)").getMatch(2);
        if (extension == null && br.containsHTML("<em>演唱：</em>")) {
            extension = "mp3";
        }
        // final String filename = br.getRegex("var SongName[^<>\"\t\n\r]*= \"([^<>\"]*?)\"").getMatch(0);
        final String filename = br.getRegex("song_title\" title=\"([^<>\"]*?)\"").getMatch(0);
        final String fileid = br.getRegex("var SongID[^<>\"\t\n\r]*= ([^<>\"]*?);").getMatch(0);
        final String stype = br.getRegex("var SongType[^<>\"\t\n\r]*= \"([^<>\"]*?)\";").getMatch(0);
        String filesize = br.getRegex("(<em>)?大小：(</em>)?([^<>\"]*?)(<|\")").getMatch(2);
        if (filename == null || extension == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setFinalFileName(stype + "-" + Encoding.htmlDecode(filename.trim()) + "-" + fileid + "." + Encoding.htmlDecode(extension.trim()));
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize + "b"));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        String src = br.getRegex("\"ticket\": \"([^<>\"]*?)\"").getMatch(0);
        if (src == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        src = Encoding.Base64Decode(src).replace("\\", "");
        String dllink = new Regex(src, "\"file\":\"(https?:[^<>\"]*?)\"").getMatch(0);
        if (dllink == null) {
            Map<String, Object> map = JSonStorage.restoreFromString(src, TypeRef.HASHMAP);
            final String songID = map.containsKey("songID") ? String.valueOf(map.get("songID")) : null;
            final String songType = map.containsKey("songType") ? String.valueOf(map.get("songType")) : null;
            if (StringUtils.isEmpty(songType) || StringUtils.isEmpty(songID)) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final Browser brc = br.cloneBrowser();
            brc.getPage("http://service.5sing.kugou.com/song/getsongurl?jsoncallback=jQuery" + System.currentTimeMillis() + "_" + System.currentTimeMillis() + "&songid=" + songID + "&songtype=" + songType + "&from=web&version=6.6.72&_=1539798427612");
            map = JSonStorage.restoreFromString(new Regex(brc.toString(), "(\\{.+\\})").getMatch(0), TypeRef.HASHMAP);
            map = (Map<String, Object>) map.get("data");
            dllink = (String) map.get("lqurl");
            if (dllink == null) {
                dllink = (String) map.get("hqurl");
            }
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }
}