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

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.K2SApi.JSonUtils;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "revision3.com" }, urls = { "http://(www\\.)?revision3\\.com/[a-z0-9]+/[a-z0-9\\-_]+" }, flags = { 0 })
public class RevisionThreeCom extends PluginForDecrypt {

    public RevisionThreeCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String INVALIDLINKS  = "http://(www\\.)?revision3\\.com/(blog|api|content|category|search|shows|login|forum|episodes|host|network|hub|sitemap)/.*?";
    private static final String INVALIDLINKS2 = "http://(www\\.)?revision3\\.com/[a-z0-9]+/(feed|about|subscribe|episodes).*?";

    private static final String TYPE_NORMAL   = "http://(www\\.)?revision3\\.com/[a-z0-9]+/[a-z0-9\\-_]+";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        br.setFollowRedirects(true);
        String parameter = param.toString();
        if (parameter.matches(INVALIDLINKS) || parameter.matches(INVALIDLINKS2)) {
            logger.info("Invalid link: " + parameter);
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        br.getPage(parameter);
        if (br.containsHTML("ey there\\! You look a little lo|404: Page Not Found<") || br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        } else if (!br.getURL().matches(TYPE_NORMAL)) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        String fpName = br.getRegex("<meta property=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
        if (fpName != null) {
            fpName = fpName.replace("...", "");
        }
        if (br.containsHTML(">This episode hasn\\'t been published yet")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        if (!br.getURL().contains("revision3.com/")) {
            final DownloadLink fina = createDownloadlink(br.getURL());
            decryptedLinks.add(fina);
            return decryptedLinks;
        }
        String externID = br.getRegex("\"(//(www\\.)?youtube\\.com/embed/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http:" + externID));
            return decryptedLinks;
        }
        String videoID = br.getRegex("\\'video_id\\', (\\d+)\\);").getMatch(0);
        if (videoID == null) {
            // JSON
            final String json = br.getRegex("(\"dataForPage\"\\s*:\\s*\\{.*?)</script>").getMatch(0);
            videoID = JSonUtils.getJson(json, "id");
            if (videoID == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
        }
        br.getPage("http://revision3.com/api/getPlaylist.json?api_key=ba9c741bce1b9d8e3defcc22193f3651b8867e62&codecs=h264,vp8,theora&video_id=" + videoID + "&jsonp=parseResponse&_=" + System.currentTimeMillis());
        final String[] allLinks = br.getRegex("\"url\":\"(http:[^<>\"]*?)\"").getColumn(0);
        if (allLinks == null || allLinks.length == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        for (String finallink : allLinks) {
            finallink = finallink.replace("\\", "");
            final DownloadLink fina = createDownloadlink("directhttp://" + finallink);
            if (fpName != null) {
                String ext = finallink.substring(finallink.lastIndexOf("."));
                if (ext != null && ext.length() < 5) {
                    fina.setFinalFileName(fpName + ext);
                }
            }
            decryptedLinks.add(fina);
        }
        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(fpName.trim());
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}