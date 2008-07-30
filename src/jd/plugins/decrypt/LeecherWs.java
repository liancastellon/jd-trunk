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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Pattern;

import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.HTTP;
import jd.plugins.PluginForDecrypt;
import jd.plugins.RequestInfo;
import jd.utils.JDUtilities;

public class LeecherWs extends PluginForDecrypt {

    final static String host = "leecher.ws";

    private String version = "0.1.0";

    private Pattern patternSupported = Pattern.compile("http://[\\w\\.]*?leecher\\.ws/(folder/.+|out/.+/[0-9]+)", Pattern.CASE_INSENSITIVE);

    public LeecherWs() {
        super();
    }

    
    public String getCoder() {
        return "JD-Team";
    }

    
    public String getHost() {
        return host;
    }

  

    
    public String getPluginName() {
        return host;
    }

    
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    
    public String getVersion() {
        return new Regex("$Revision$","\\$Revision: ([\\d]*?)\\$").getFirstMatch();
    }

    
    public ArrayList<DownloadLink> decryptIt(String parameter) {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        RequestInfo reqinfo;
        String outLinks[][] = null;
        try {
            if (parameter.indexOf("out") != -1) {
                outLinks = new String[1][1];
                outLinks[0][0] = parameter.substring(parameter.lastIndexOf("leecher.ws/out/") + 15);
            } else {
                reqinfo = HTTP.getRequest(new URL(parameter));
                outLinks = new Regex(reqinfo.getHtmlCode(), Pattern.compile("href=\"http://www\\.leecher\\.ws/out/(.*?)\"", Pattern.CASE_INSENSITIVE)).getMatches();
            }
            progress.setRange(outLinks.length);
            for (int i = 0; i < outLinks.length; i++) {
                reqinfo = HTTP.getRequest(new URL("http://leecher.ws/out/" + outLinks[i][0]));
                String cryptedLink = new Regex(reqinfo.getHtmlCode(), Pattern.compile("<iframe src=\"(.?)\"", Pattern.CASE_INSENSITIVE)).getFirstMatch();
                decryptedLinks.add(this.createDownloadlink(JDUtilities.htmlDecode(cryptedLink)));
                progress.increase(1);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return decryptedLinks;
    }

    
    public boolean doBotCheck(File file) {
        return false;
    }
}