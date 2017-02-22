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

import java.text.DecimalFormat;
import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

//Decrypts embedded videos from liveleak.com
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "liveleak.com" }, urls = { "https?://(www\\.)?liveleak\\.com/(view\\?i=[a-z0-9]+_\\d+|ll_embed\\?f=[a-z0-9]+)" })
public class LiveLeakComDecrypter extends PluginForDecrypt {

    public LiveLeakComDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setCookie("http://liveleak.com/", "liveleak_safe_mode", "0");
        br.getPage(parameter);
        // Handle embedded links
        if (parameter.matches("https?://(www\\.)?liveleak\\.com/ll_embed\\?f=[a-z0-9]+")) {
            if (br.containsHTML("File not found or deleted\\!") || this.br.getHttpConnection().getResponseCode() == 404) {
                final DownloadLink dl = this.createOfflinelink(parameter);
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
            final String originalID = br.getRegex("\\&item_token=([a-z0-9]+_\\d+)\\&embed=").getMatch(0);
            if (originalID == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            parameter = "/view?i=" + originalID;
            br.getPage(parameter);
        }
        if (isOffline(this.br)) {
            /* e.g. 'a possible violation of our terms of service' or 'a copyright violation' */
            final DownloadLink dl = this.createOfflinelink(parameter);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        String externID = br.getRegex("\"(https?://(www\\.)?prochan\\.com/embed\\?f=[^<>\"/]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"[^\"]+(youtube\\.com/embed/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("https://www." + externID));
            return decryptedLinks;
        }
        String filename = br.getRegex("class=\"section_title\" style=\"vertical\\-align:top; padding\\-right:10px\">([^<>\"]*?)<img").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<title>LiveLeak\\.com \\- ([^<>\"]*?)</title>").getMatch(0);
        }
        if (filename == null) {
            filename = new Regex(parameter, "([a-z0-9_]+)$").getMatch(0);
        }
        filename = Encoding.htmlDecode(filename.trim());
        // There can also be multiple videos on one page
        final String[] allEmbedcodes = br.getRegex("ll_embed\\?f=([A-Za-z0-9]+)").getColumn(0);
        if (allEmbedcodes != null && allEmbedcodes.length != 0) {
            int counter = 1;
            final DecimalFormat df = new DecimalFormat("000");
            for (final String embedID : allEmbedcodes) {
                final DownloadLink dl = createDownloadlink("http://liveleakvideodecrypted.com/" + embedID);
                if (allEmbedcodes.length > 1) {
                    dl.setName(filename + "_" + df.format(counter) + ".mp4");
                } else {
                    dl.setName(filename + ".mp4");
                }
                dl.setAvailable(true);
                decryptedLinks.add(dl);
                counter++;
            }
        }
        // ...or do we have (multiple) pixtures?
        final String[] allPics = br.getRegex("\"(https?://[a-z0-9\\-_]+\\.liveleak\\.com/[^<>\"]*?)\" target=\"_blank\"><img").getColumn(0);
        if (allPics != null && allPics.length != 0) {
            int counter = 1;
            final DecimalFormat df = new DecimalFormat("000");
            for (final String pictureLink : allPics) {
                final DownloadLink dl = createDownloadlink("directhttp://" + pictureLink);
                if (allPics.length > 1) {
                    dl.setName(filename + "_" + df.format(counter) + ".jpg");
                } else {
                    dl.setName(filename + ".jpg");
                }
                dl.setAvailable(true);
                decryptedLinks.add(dl);
                counter++;
            }
        }
        if (decryptedLinks == null || decryptedLinks.size() == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(filename);
        fp.addLinks(decryptedLinks);
        return decryptedLinks;
    }

    public static boolean isOffline(final Browser br) {
        return br.containsHTML(">This item has been deleted because of") || br.getHttpConnection().getResponseCode() == 404;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}