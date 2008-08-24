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

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import jd.http.Browser;
import jd.http.Encoding;
import jd.http.HTTPConnection;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginForHost;
import jd.plugins.download.RAFDownload;

public class ArchivTo extends PluginForHost {
    private static final String HOST = "archiv.to";

    static private final Pattern patternSupported = Pattern.compile("http://[\\w\\.]*?archiv\\.to/\\?Module\\=Details\\&HashID\\=.*", Pattern.CASE_INSENSITIVE);

    public ArchivTo() {
        super();
    }

    @Override
    public boolean doBotCheck(File file) {
        return false;
    }

    @Override
    public String getAGBLink() {
        return "http://archiv.to/?Module=Policy";
    }

    @Override
    public String getCoder() {
        return "JD-Team";
    }

    @Override
    public boolean getFileInformation(DownloadLink downloadLink) throws IOException {
        Browser.clearCookies(HOST);
        String page = br.getPage(downloadLink.getDownloadURL());

        downloadLink.setName(new Regex(page, Pattern.compile("<td width=\"23%\">Original-Dateiname</td>[\\s]*?<td width=\"77%\">: <a href=\"(.*?)\" style=\"Color: #5FB8E0\">(.*?)</a></td>", Pattern.CASE_INSENSITIVE)).getMatch(1));
        downloadLink.setDownloadSize(Long.parseLong(new Regex(page, Pattern.compile("<td width=\"23%\">Dateigr..e</td>[\\s]*?<td width=\"77%\">: (.*?) Bytes \\(~ (.*?)\\)</td>", Pattern.CASE_INSENSITIVE)).getMatch(0)));

        return true;
    }

    @Override
    public String getHost() {
        return HOST;
    }

    @Override
    public String getPluginName() {
        return HOST;
    }

    @Override
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    @Override
    public String getVersion() {
        String ret = new Regex("$Revision$", "\\$Revision: ([\\d]*?) \\$").getMatch(0);
        return ret == null ? "0.0" : ret;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws IOException {
        LinkStatus linkStatus = downloadLink.getLinkStatus();
        Browser.clearCookies(HOST);

        if (!getFileInformation(downloadLink)) {
            linkStatus.addStatus(LinkStatus.ERROR_FILE_NOT_FOUND);
            return;
        }

        HTTPConnection urlConnection = br.openGetConnection("http://archiv.to/" + Encoding.htmlDecode(new Regex(br.getPage(downloadLink.getDownloadURL()), Pattern.compile("<a href=\"\\./(.*?)\"", Pattern.CASE_INSENSITIVE)).getMatch(0)));
        dl = new RAFDownload(this, downloadLink, urlConnection);
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }
}
