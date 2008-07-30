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
//    along with this program.  If not, see <http://www.gnu.org/licenses

package jd.plugins.decrypt;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Pattern;

import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.HTTP;
import jd.plugins.PluginForDecrypt;
import jd.plugins.RequestInfo;
import jd.utils.JDUtilities;

public class UploadJockeycom extends PluginForDecrypt {

    static private final String HOST = "uploadjockey.com";
    private String VERSION = "1.0.0";
    private String CODER = "JD-Team";

    static private final Pattern patternSupported = Pattern.compile("http://[\\w\\.]*?uploadjockey\\.com/download/[a-zA-Z0-9]+/(.*)", Pattern.CASE_INSENSITIVE);

    public UploadJockeycom() {
        super();
    }

    
    public String getCoder() {
        return CODER;
    }

    
    public String getHost() {
        return HOST;
    }

    
    
        
    

    
    public String getPluginName() {
        return HOST;
    }

    
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    
    public String getVersion() {
        return new Regex("$Revision$","\\$Revision: ([\\d]*?)\\$").getFirstMatch();
    }

    
    public ArrayList<DownloadLink> decryptIt(String parameter) {
        String cryptedLink = (String) parameter;
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        URL url;
        RequestInfo requestInfo;
        try {
            url = new URL(cryptedLink);
            requestInfo = HTTP.getRequest(url, null, url.toString(), false);
            String links[][] = new Regex(requestInfo.getHtmlCode(), Pattern.compile("<a href=\"http\\:\\/\\/www\\.uploadjockey\\.com\\/redirect\\.php\\?url=([a-zA-Z0-9=]+)\"", Pattern.CASE_INSENSITIVE)).getMatches();
            for (int i = 0; i < links.length; i++) {
                decryptedLinks.add(this.createDownloadlink(JDUtilities.Base64Decode(links[i][0])));
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
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
