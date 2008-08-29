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

package jd.plugins.decrypt;

import java.util.ArrayList;
import java.util.regex.Pattern;

import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

public class RnB4Uin extends PluginForDecrypt {
    static private final String host = "RnB4U.in";
    private static final Pattern pattern_Kategorie = Pattern.compile("http://[\\w\\.]*?rnb4u\\.in/download\\.php\\?action=kategorie&kat_id=\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern pattern_File = Pattern.compile("http://[\\w\\.]*?rnb4u\\.in/download\\.php\\?action=popup&kat_id=\\d+&fileid=\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternSupported = Pattern.compile(pattern_Kategorie.pattern() + "|" + pattern_File.pattern(), Pattern.CASE_INSENSITIVE);

    public RnB4Uin() {
        super();
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();

        String page = br.getPage("http://rnb4u.in/download.php?action=load&fileid=" + parameter.substring(parameter.lastIndexOf("=") + 1) + "&sp=1");
        decryptedLinks.add(createDownloadlink(new Regex(page, Pattern.compile("URL=(.*?)\"", Pattern.CASE_INSENSITIVE)).getMatch(0)));

        return decryptedLinks;
    }

    @Override
    public String getCoder() {
        return "JD-Team";
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getPluginName() {
        return host;
    }

    @Override
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    @Override
    public String getVersion() {
        String ret = new Regex("$Revision: 2541 $", "\\$Revision: ([\\d]*?) \\$").getMatch(0);
        return ret == null ? "0.0" : ret;
    }
}
