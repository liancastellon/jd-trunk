//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
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

package jd.plugins.host;

import java.io.IOException;

import jd.PluginWrapper;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

public class MediafireCom extends PluginForHost {

    static private final String offlinelink = "tos_aup_violation";

    public MediafireCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.mediafire.com/terms_of_service.php";
    }

    @Override
    public boolean getFileInformation(DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        String url = downloadLink.getDownloadURL();
        br.getPage(url);
        if (br.getRegex(offlinelink).matches()) { throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND); }
        String filename = br.getRegex("<title>(.*?)<\\/title>").getMatch(0);
        String filesize = br.getRegex("<input type=\"hidden\" id=\"sharedtabsfileinfo1-fs\" value=\"(.*?)\">").getMatch(0);
        if (filename == null || filesize == null) { throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND); }
        downloadLink.setName(filename.trim());
        downloadLink.setDownloadSize(Regex.getSize(filesize));
        return true;
    }

    @Override
    public String getVersion() {
        return getVersion("$Revision$");
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        getFileInformation(downloadLink);

        String[][] para = br.getRegex("[a-z]{2}\\(\\'([a-z0-9]{7,14})\\'\\,\\'([0-f0-9]*?)\\'\\,\\'([a-z0-9]{2,14})\\'\\)\\;").getMatches();
        if (para.length == 0 || para[0].length == 0) { throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFEKT); }
        br.getPage("http://www.mediafire.com/dynamic/download.php?qk=" + para[0][0] + "&pk=" + para[0][1] + "&r=" + para[0][2]);
        String url = br.getRegex("http\\:\\/\\/\"(.*?)'\"").getMatch(0);
        url = "http://" + url.replaceAll("'(.*?)'", "$1");

        String[] vars = new Regex(url, "\\+ ?([a-z0-9]*?) ?\\+").getColumn(0);

        for (String var : vars) {
            String value = br.getRegex(var + " ?= ?\\'(.*?)\\'").getMatch(0);
            url = url.replaceAll("\\+ ?" + var + " ?\\+", value);

        }
        dl = br.openDownload(downloadLink, url, true, 0);
        dl.startDownload();
    }

    public int getMaxSimultanFreeDownloadNum() {
        return 20;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }
}