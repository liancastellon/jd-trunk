package jd.plugins.host;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.regex.Pattern;

import jd.parser.HTMLParser;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.HTTP;
import jd.plugins.HTTPConnection;
import jd.plugins.LinkStatus;
import jd.plugins.PluginForHost;
import jd.plugins.RequestInfo;
import jd.plugins.download.RAFDownload;
import jd.utils.JDUtilities;

public class UploadServiceinfo extends PluginForHost {

    private static final String HOST = "uploadservice.info";
   
    private String url;
    private String postdata;
    static private final Pattern patternSupported = Pattern.compile("http://[\\w\\.]*?uploadservice\\.info/file/[a-zA-Z0-9]+\\.html", Pattern.CASE_INSENSITIVE);
    private RequestInfo requestInfo;

    //
    
    public boolean doBotCheck(File file) {
        return false;
    }

    
    public String getCoder() {
        return "JD-Team";
    }

    
    public String getPluginName() {
        return HOST;
    }

    
    public String getHost() {
        return HOST;
    }

    
    public String getVersion() {
        return new Regex("$Revision$","\\$Revision: ([\\d]*?)\\$").getFirstMatch();
    }


 

    
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    public UploadServiceinfo() {
        super();
        // steps.add(new PluginStep(PluginStep.STEP_PAGE, null));
        // //steps.add(new PluginStep(PluginStep.STEP_PENDING, null));
        // steps.add(new PluginStep(PluginStep.STEP_DOWNLOAD, null));
    }

    public void handle(DownloadLink downloadLink) throws Exception {
        LinkStatus linkStatus = downloadLink.getLinkStatus();

        // switch (step.getStep()) {
        // case PluginStep.STEP_PAGE:
        /* Nochmals das File überprüfen */
        if (!getFileInformation(downloadLink)) {
            linkStatus.addStatus(LinkStatus.ERROR_FILE_NOT_FOUND);
            // step.setStatus(PluginStep.STATUS_ERROR);
            return;
        }
        /* Link holen */
        url = requestInfo.getForms()[0].action;
        HashMap<String, String> submitvalues = HTMLParser.getInputHiddenFields(requestInfo.getHtmlCode());
        postdata = "key=" + JDUtilities.urlEncode(submitvalues.get("key"));
        postdata = postdata + "&mysubmit=Download";

        // case PluginStep.STEP_PENDING:
        /* Zwangswarten, 10seks, kann man auch weglassen */
        this.sleep(10000, downloadLink);

        // case PluginStep.STEP_DOWNLOAD:
        /* Datei herunterladen */
        requestInfo = HTTP.postRequestWithoutHtmlCode(new URL(url), requestInfo.getCookie(), downloadLink.getDownloadURL(), postdata, false);
        HTTPConnection urlConnection = requestInfo.getConnection();
        String filename = getFileNameFormHeader(urlConnection);
        if (urlConnection.getContentLength() == 0) {
            linkStatus.addStatus(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE);
            // step.setStatus(PluginStep.STATUS_ERROR);
            return;
        }
        downloadLink.setDownloadMax(urlConnection.getContentLength());
        downloadLink.setName(filename);
        long length = downloadLink.getDownloadMax();
        dl = new RAFDownload(this, downloadLink, urlConnection);
        dl.setChunkNum(1);
        dl.setResume(false);
        dl.setFilesize(length);
        dl.startDownload();
    }

    
    public boolean getFileInformation(DownloadLink downloadLink) {
        LinkStatus linkStatus = downloadLink.getLinkStatus();
        try {
            String url = downloadLink.getDownloadURL();
            requestInfo = HTTP.getRequest(new URL(url));
            if (!requestInfo.containsHTML("<strong>Die ausgew&auml;hlte Datei existiert nicht!</strong>")) {
                downloadLink.setName(JDUtilities.htmlDecode(new Regex(requestInfo.getHtmlCode(), Pattern.compile("<input type=\"text\" value=\"(.*?)\" /></td>", Pattern.CASE_INSENSITIVE)).getFirstMatch()));
                String filesize = null;
                if ((filesize = new Regex(requestInfo.getHtmlCode(), "<td style=\"font-weight: bold;\">(\\d+) MB</td>").getFirstMatch()) != null) {
                    downloadLink.setDownloadMax(new Integer(filesize) * 1024 * 1024);
                }
                return true;
            }
        } catch (MalformedURLException e) {

            e.printStackTrace();
        } catch (IOException e) {

            e.printStackTrace();
        }
        downloadLink.setAvailable(false);
        return false;
    }

    
    public int getMaxSimultanDownloadNum() {
        return Integer.MAX_VALUE;
    }

    
    public void reset() {
    }

    
    public void resetPluginGlobals() {
    }

    
    public String getAGBLink() {
        return "http://www.uploadservice.info/rules.html";
    }
}
