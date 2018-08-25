//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
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
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.appwork.utils.StringUtils;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "highporn.net", "tanix.net", "japanhub.net", "thatav.net" }, urls = { "https?://(?:www\\.)?highporn\\.net/video/\\d+(?:/[a-z0-9\\-]+)?", "https?://(?:www\\.)?tanix\\.net/video/\\d+(?:/[a-z0-9\\-]+)?", "https?://(?:www\\.)?japanhub\\.net/video/\\d+(?:/[a-z0-9\\-]+)?", "https?://(?:www\\.)?thatav\\.net/video/\\d+(?:/[a-z0-9\\-]+)?" })
public class HighpornNet extends PluginForDecrypt {
    public HighpornNet(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString().replace("www.", "");
        br.setFollowRedirects(true);
        getPage(parameter);
        if (isOffline(br)) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        final String fpName = getTitle(br, parameter);
        final String videoLink = br.getRegex("data-src=\"(http[^<>\"]+)\"").getMatch(0); // If single link, no videoID
        String[] videoIDs = br.getRegex("data-src=\"(\\d+)\"").getColumn(0);
        if (videoIDs == null || videoIDs.length == 0) {
            if (videoLink == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            } else {
                videoIDs = new String[1];
                videoIDs[0] = (Long.toString(System.currentTimeMillis())); // dummy videoID
            }
        }
        final int padLength = getPadLength(videoIDs.length);
        int counter = 0;
        for (final String videoID : videoIDs) {
            counter++;
            final String orderid_formatted = String.format(Locale.US, "%0" + padLength + "d", counter);
            final String filename = fpName + "_" + orderid_formatted + ".mp4";
            final DownloadLink dl = createDownloadlink("highporndecrypted://" + videoID);
            dl.setName(filename);
            dl.setProperty("decryptername", filename);
            dl.setProperty("mainlink", parameter);
            dl.setContentUrl(parameter);
            PostRequest postRequest = new PostRequest("http://play.openhub.tv/playurl?random=" + (new Date().getTime() / 1000));
            postRequest.setContentType("application/x-www-form-urlencoded");
            postRequest.addVariable("v", videoID);
            postRequest.addVariable("source_play", "highporn");
            String file = br.getPage(postRequest);
            final URLConnectionAdapter con = br.cloneBrowser().openHeadConnection(file);
            try {
                if (con.getResponseCode() == 200 && con.getLongContentLength() > 0 && !StringUtils.contains(con.getContentType(), "html")) {
                    dl.setVerifiedFileSize(con.getCompleteContentLength());
                }
            } finally {
                con.disconnect();
            }
            dl.setAvailable(true);
            decryptedLinks.add(dl);
        }
        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    private void getPage(final String parameter) throws Exception {
        final PluginForHost plugin = JDUtilities.getPluginForHost("highporn.net");
        ((jd.plugins.hoster.HighpornNet) plugin).setBrowser(br);
        ((jd.plugins.hoster.HighpornNet) plugin).getPage(parameter);
    }

    public static boolean isOffline(final Browser br) {
        return br.getHttpConnection().getResponseCode() == 404 || br.getURL().contains("/error/video_missing");
    }

    public static String getTitle(final Browser br, final String url) {
        String title = br.getRegex("property=\"og:title\" content=\"([^<>\"]+)\"").getMatch(0);
        if (title == null) {
            title = new Regex(url, "video/(.+)").getMatch(0);
        }
        return title;
    }

    private final int getPadLength(final int size) {
        if (size < 10) {
            return 1;
        } else if (size < 100) {
            return 2;
        } else if (size < 1000) {
            return 3;
        } else if (size < 10000) {
            return 4;
        } else if (size < 100000) {
            return 5;
        } else if (size < 1000000) {
            return 6;
        } else if (size < 10000000) {
            return 7;
        } else {
            return 8;
        }
    }
}
