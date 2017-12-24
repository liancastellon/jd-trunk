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
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.SiteType.SiteTemplate;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "danbooru.donmai.us" }, urls = { "https?://(?:www\\.)?danbooru\\.donmai\\.us/posts\\?tags=[^<>\"\\&=\\?/]+" })
public class DanbooruDonmaiUs extends PluginForDecrypt {
    public DanbooruDonmaiUs(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        final String fpName = new Regex(parameter, "tags=(.+)$").getMatch(0);
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(Encoding.htmlDecode(fpName.trim()));
        final String url_part = parameter;
        int page_counter = 1;
        int offset = 0;
        final int min_entries_per_page = 15;
        int entries_per_page_current = 0;
        do {
            if (this.isAbort()) {
                logger.info("Decryption aborted by user");
                return decryptedLinks;
            }
            if (page_counter > 1) {
                this.br.getPage(url_part + "&page=" + page_counter);
            }
            logger.info("Decrypting: " + this.br.getURL());
            final String[] linkids = br.getRegex("id=\"post_(\\d+)\"").getColumn(0);
            if (linkids == null || linkids.length == 0) {
                logger.warning("Decrypter might be broken for link: " + parameter);
                break;
            }
            entries_per_page_current = linkids.length;
            for (final String linkid : linkids) {
                final String link = "http://" + this.getHost() + "/posts/" + linkid;
                final DownloadLink dl = createDownloadlink(link);
                dl.setLinkID(linkid);
                dl.setAvailable(true);
                dl.setName(linkid + ".jpg");
                dl._setFilePackage(fp);
                decryptedLinks.add(dl);
                distribute(dl);
                offset++;
            }
            page_counter++;
        } while (entries_per_page_current >= min_entries_per_page);
        return decryptedLinks;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.Danbooru;
    }
}
