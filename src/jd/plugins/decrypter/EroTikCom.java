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
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "ero-tik.com" }, urls = { "http://(?:www\\.)?ero\\-tik\\.com/[^<>\"/]+\\.html" }, flags = { 0 })
public class EroTikCom extends PluginForDecrypt {

    public EroTikCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        if (parameter.matches("http://www\\.ero-tik\\.com/(contact_us|login)\\.html")) {
            logger.info("Unsupported/invalid link: " + parameter);
            return decryptedLinks;
        }
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            try {
                decryptedLinks.add(this.createOfflinelink(parameter));
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
            }
            return decryptedLinks;
        }
        if (br.containsHTML("video-watch")) { // Single links
            decryptSingleLink(decryptedLinks, parameter);
        } else { // Multi links
            final String fpName = "Ero-tik " + new Regex(parameter, "http://www\\.ero-tik\\.com/(.*)\\.html").getMatch(0);
            logger.info("fpName: " + fpName);
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            final String[] items = br.getRegex("<h3><a href=\"([^<>\"].+?)\" class=\"pm-title-link ?\"").getColumn(0);
            if ((items == null || items.length == 0) && decryptedLinks.isEmpty()) {
                logger.warning("Decrypter broken (items regex) for link: " + parameter);
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            for (final String item : items) {
                logger.info("item: " + item);
                br.getPage(item);
                decryptSingleLink(decryptedLinks, item);
                fp.addLinks(decryptedLinks);
            }
        }
        return decryptedLinks;
    }

    private void decryptSingleLink(final ArrayList<DownloadLink> decryptedLinks, final String parameter) throws Exception {
        String title = br.getRegex("<title>(.*?)</title>").getMatch(0);
        if (title == null) {
            logger.warning("Decrypter broken (title regex) for link: " + parameter);
            return;
        }
        logger.info("title: " + title);
        String filename = title;
        filename = filename.trim();
        // src="http://videomega.tv/validatehash.php?hashkey=050116111118107087049053074086086074053049087107118111116050"
        // src="http://videomega.tv/iframe.js"
        // src="http://www.ero-tik.com/embed.php?vid=188412d51"
        String externID = br.getRegex("\"(https?://videomega\\.tv/[^<>\"]*?)\"").getMatch(0);
        String embed = br.getRegex("src=\"(http://www\\.ero-tik\\.com/embed[^<>\"]*?)\"").getMatch(0);
        if (externID == null && embed == null) {
            logger.info("externID & embed not found");
            return;
        }
        if (externID == "http://videomega.tv/iframe.js") {
            br.getPage(externID);
            String ref = br.getRegex(">ref=\"([^<>\"]*?)\"").getMatch(0);
            externID = "http://videomega.tv/view.php?ref=" + ref;
        }
        if (embed != null) {
            br.getPage(embed);
            String ref = br.getRegex(">ref=\"([^<>\"]*?)\"").getMatch(0);
            if (ref != null) {
                externID = "http://videomega.tv/view.php?ref=" + ref;
            } else {
                externID = br.getRegex("\"(https?://videomega\\.tv/[^<>\"]*?)\"").getMatch(0);
            }
        }
        logger.info("externID: " + externID);
        DownloadLink dl = createDownloadlink(externID);
        dl.setContentUrl(externID);
        dl.setFinalFileName(filename + "." + ".mp4");
        decryptedLinks.add(dl);
        logger.info("decryptedLinks.add(dl) done");
        return;
    }

    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}