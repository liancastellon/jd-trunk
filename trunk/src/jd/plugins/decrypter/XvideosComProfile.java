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

import org.jdownloader.controlling.filter.CompiledFiletypeFilter;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "xvideos.com" }, urls = { "https?://(?:www\\.)?xvideos\\.com/profiles/[A-Za-z0-9\\-_]+" })
public class XvideosComProfile extends PluginForDecrypt {
    public XvideosComProfile(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final ArrayList<String> dupeList = new ArrayList<String>();
        final String parameter = param.toString();
        br.addAllowedResponseCodes(400);
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 403 || br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        final String username = new Regex(parameter, "/profiles/(.+)").getMatch(0);
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(Encoding.htmlDecode(username.trim()));
        fp.addLinks(decryptedLinks);
        short pageNum = 0;
        int decryptedLinksNum;
        boolean pornStar = false;
        boolean retry = false;
        do {
            retry = false;
            if (this.isAbort()) {
                return decryptedLinks;
            }
            logger.info(String.format("Decrypting page %d", pageNum));
            decryptedLinksNum = 0;
            if (pornStar) {
                br.getPage("/profiles/" + username + "/videos/pornstar/" + pageNum);
            } else {
                br.getPage("/profiles/" + username + "/videos/best/" + pageNum);
                // users don't always have profile... as guardo finds links from google... false positive.
                if (br.getHttpConnection().getResponseCode() == 403 || br.getHttpConnection().getResponseCode() == 400) {
                    return decryptedLinks;
                } else if (br.toString().matches("<h4 class=\"text-center\">[^<]+  hat keine hochgeladene Videos</h4>\\s*")) {
                    logger.info("This user does not have any videos");
                    return decryptedLinks;
                }
            }
            final String[] links = br.getRegex("(/prof-video-click/[^/]+/[^/]+/\\d+((?:/THUMBNUM)?/[^/\"\\']+)?)").getColumn(0);
            if (!br.containsHTML("profile-listing-uploads") && !br.containsHTML("profile-videos-sort") && (links == null || links.length == 0)) {
                if (pornStar) {
                    logger.info("This user does not have any videos");
                    return decryptedLinks;
                } else {
                    pornStar = true;
                    retry = true;
                    continue;
                }
            }
            if (links == null || links.length == 0) {
                break;
            }
            decryptedLinksNum = links.length;
            for (String singleLink : links) {
                if (this.isAbort()) {
                    return decryptedLinks;
                }
                final String linkid = new Regex(singleLink, "prof-video-click/[^/]+/[^/]+/(\\d+)").getMatch(0);
                /* Only add new URLs */
                if (!dupeList.contains(linkid)) {
                    singleLink = "https://www." + this.getHost() + singleLink;
                    final String url_name = new Regex(singleLink, "/\\d+/(?:THUMBNUM/)?(.+)").getMatch(0);
                    final String name_temp;
                    final DownloadLink dl = createDownloadlink(singleLink);
                    /* Usually we will crawl a lot of URLs at this stage --> Set onlinestatus right away! */
                    dl.setAvailable(true);
                    fp.add(dl);
                    dl.setLinkID(linkid);
                    if (url_name != null) {
                        String clean = url_name.replaceAll("(watch_)?(free_)?(live_)?camgirls_at_(www(_|\\.))?teenhdcams(_|\\.)com$", "");
                        clean = clean.replaceAll("(watch_)?free_at_(www(_|\\.))?teenhdcams(_|\\.)com$", "");
                        clean = clean.replaceAll("(watch_)?full_video_at_(www(_|\\.))?teenhdcams(_|\\.)com$", "");
                        clean = clean.replaceAll("\\.*_*$", "");
                        name_temp = linkid + "_" + clean;
                    } else {
                        name_temp = linkid;
                    }
                    dl.setName(name_temp);
                    dl.setMimeHint(CompiledFiletypeFilter.VideoExtensions.FLV);
                    decryptedLinks.add(dl);
                    distribute(dl);
                    decryptedLinksNum++;
                    dupeList.add(linkid);
                }
            }
            pageNum++;
        } while (decryptedLinksNum >= 36 || retry);
        if (decryptedLinks.size() == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        return decryptedLinks;
    }
}
