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


package jd.plugins.decrypt;  import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginStep;
import jd.plugins.RequestInfo;
import jd.utils.JDUtilities;

/**
 * http://rs.xxx-blog.org/com-UmNkdzY1MjN/file.rar
 * http://rs.hoerbuch.in/com-UmY3YGNiRjN/PP-Grun.rar
 * 
 * 
 * @author JD-Team
 * 
 */
public class RsHoerbuchin extends PluginForDecrypt {
    static private final String host             = "rs.hoerbuch.in";

    private String              version          = "1.0.0.1";
    private static final Pattern patternPost = Pattern.compile("http://(www\\.|)hoerbuch.in/blog.php\\?id=[\\d]+");
    private static final Pattern patternLink = Pattern.compile("http://rs\\.hoerbuch\\.in/com-[a-zA-Z0-9]{11}/.*", Pattern.CASE_INSENSITIVE);
    static private final Pattern patternSupported = Pattern.compile(patternPost.pattern()+"|"+patternLink.pattern(), patternPost.flags()| patternLink.flags());
    
//    private static final Pattern patternPostName = Pattern.compile("<H1.*?><A HREF.*?>(.*?)</A></H1>");
    private static final Pattern patternLinksHTMLSource = Pattern.compile("Dauer.*?(.*)Passwort:</B>\\s*(.*?)\\s*<");
    



    public RsHoerbuchin() {
        super();
        steps.add(new PluginStep(PluginStep.STEP_DECRYPT, null));
        currentStep = steps.firstElement();
        default_password.add("www.hoerbuch.in");
    }

    @Override
    public String getCoder() {
        return "JD-Team";
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
    public String getHost() {
        return host;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getPluginID() {
        return "hoerbuch.in-1.0.0.";
    }

    @Override
    public boolean doBotCheck(File file) {
        return false;
    }

    @Override
    public PluginStep doStep(PluginStep step, String parameter) {
        String cryptedLink = (String) parameter;
        switch (step.getStep()) {
            case PluginStep.STEP_DECRYPT:
           
                Vector<DownloadLink> decryptedLinks = new Vector<DownloadLink>();
                try {
                    URL url = new URL(cryptedLink);
                    RequestInfo requestInfo = getRequest(url, null, null, false);
                    
                    if(cryptedLink.matches(patternLink.pattern())){
                        HashMap<String, String> fields = this.getInputHiddenFields(requestInfo.getHtmlCode(), "postit", "starten");
                        String newURL = "http://rapidshare.com" + JDUtilities.htmlDecode(fields.get("uri"));
                        decryptedLinks.add(this.createDownloadlink(newURL));                    	
                    }else if( cryptedLink.matches(patternPost.pattern())){
                    	Matcher matcher = patternLinksHTMLSource.matcher(requestInfo.getHtmlCode());
                    	if( matcher.find() ){
                    		String linksClaim = matcher.group(1);
                    		String [] links = getHttpLinks( linksClaim, null);
                    		
                    		for( String link:links){
                           		decryptedLinks.add( createDownloadlink(link) );
                    		}
                    	}else{
                    		logger.severe("unable to find links - adapt patternLinksHTMLSource (see next INFO log line for details)");
                    		logger.info(requestInfo.getHtmlCode());
                    	}
                    }
                }
                catch (MalformedURLException e) {
                   e.printStackTrace();
                }
                catch (IOException e) {
                   e.printStackTrace();
                }
                step.setParameter(decryptedLinks);
                break;

        }
        return null;

    }
}
