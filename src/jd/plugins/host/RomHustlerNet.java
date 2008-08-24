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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jd.http.Browser;
import jd.http.Encoding;
import jd.http.HTTPConnection;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginForHost;
import jd.plugins.download.RAFDownload;

public class RomHustlerNet extends PluginForHost {

    private static final String HOST = "romhustler.net";

    static private final Pattern patternSupported = Pattern.compile("http://[\\w.]*?romhustler\\.net/download/.*?/\\d+", Pattern.CASE_INSENSITIVE);
    private String downloadUrl;

    public RomHustlerNet() {
        super();
    }

    @Override
    public boolean doBotCheck(File file) {
        return false;
    }

    @Override
    public String getAGBLink() {
        return "http://romhustler.net/disclaimer.php";
    }

    @Override
    public String getCoder() {
        return "JD-Team";
    }

    @Override
    public boolean getFileInformation(DownloadLink downloadLink) {
        try {
            Browser.clearCookies(HOST);
            br.getPage(downloadLink.getDownloadURL());
            downloadUrl = decodeurl(br.getRegex(Pattern.compile("link_enc=new Array\\((.*?)\\);", Pattern.CASE_INSENSITIVE)).getMatch(0));
            if (downloadUrl == null) return false;
            String name = Encoding.htmlDecode(downloadUrl.replaceAll("^.*/", ""));
            downloadLink.setName(name);
            return true;
        } catch (Exception e) {
        }
        return false;
    }

    @Override
    public String getHost() {
        return HOST;
    }

    public String getPluginName() {
        return HOST;
    }

    @Override
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    @Override
    public String getVersion() {
        String ret = new Regex("$Revision: 2398 $", "\\$Revision: ([\\d]*?) \\$").getMatch(0);
        return ret == null ? "0.0" : ret;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        LinkStatus linkStatus = downloadLink.getLinkStatus();
        if (!getFileInformation(downloadLink)) {
            linkStatus.addStatus(LinkStatus.ERROR_FILE_NOT_FOUND);
            return;
        }
        HTTPConnection urlConnection = br.openGetConnection(downloadUrl);
        logger.info(Plugin.getFileNameFormHeader(urlConnection));
        dl = new RAFDownload(this, downloadLink, urlConnection);
        dl.setResume(true);
        dl.setChunkNum(1);
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    private static String decodeurl(String page) {
        if (page == null) return null;
        StringBuffer sb = new StringBuffer();
        String pattern = "('.'),?";
        Matcher r = Pattern.compile(pattern, Pattern.DOTALL).matcher(page);
        while (r.find()) {
            if (r.group(1).length() > 0) {
                String content = r.group(1).replaceAll("'|,", "");
                r.appendReplacement(sb, content);
            }
        }
        r.appendTail(sb);
        return sb.toString();
    }
}