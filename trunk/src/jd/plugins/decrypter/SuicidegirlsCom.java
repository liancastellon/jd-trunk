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
import java.util.Random;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "suicidegirls.com" }, urls = { "https?://(?:www\\.)?suicidegirls\\.com/girls/[A-Za-z0-9\\-_]+/(?:album/\\d+/[A-Za-z0-9\\-_]+/)?" }, flags = { 0 })
public class SuicidegirlsCom extends PluginForDecrypt {

    public SuicidegirlsCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String TYPE_ALBUM = "https?://(?:www\\.)?suicidegirls\\.com/girls/[A-Za-z0-9\\-_]+/album/\\d+/[A-Za-z0-9\\-_]+/";
    private static final String TYPE_USER  = "https?://(?:www\\.)?suicidegirls\\.com/girls/[A-Za-z0-9\\-_]+/";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<String> dupecheck = new ArrayList<String>();
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        String fpName = null;
        final boolean loggedin = jd.plugins.hoster.SuicidegirlsCom.login(this.br);
        jd.plugins.hoster.SuicidegirlsCom.prepBR(this.br);
        this.br.setFollowRedirects(true);
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        if (this.br.containsHTML("class=\"album-join-message\"")) {
            logger.info("User added member-only url but has no (valid) account");
            return decryptedLinks;
        }
        final String username = new Regex(parameter, "girls/([A-Za-z0-9\\-_]+)/").getMatch(0);
        if (parameter.matches(TYPE_ALBUM)) {
            fpName = br.getRegex("<h2 class=\"title\">([^<>\"]*?)</h2>").getMatch(0);
            if (fpName == null) {
                /* Fallback to url-packagename */
                fpName = new Regex(parameter, "([A-Za-z0-9\\-_]+)/$").getMatch(0);
            }
            fpName = username + " - " + fpName;

            final String[] links = br.getRegex(" <li class=\"photo\\-container\" id=\"thumb\\-\\d+\" data\\-index=\"\\d+\">[\t\n\r ]*<a href=\"(http[^<>\"]*?)\"").getColumn(0);
            if ((links == null || links.length == 0) && !loggedin) {
                /* Account is needed most times */
                logger.info("Account needed to crawl link: " + parameter);
                return decryptedLinks;
            }
            if (links == null || links.length == 0) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            for (final String directlink : links) {
                final DownloadLink dl = this.createDownloadlink("http://suicidegirlsdecrypted/" + System.currentTimeMillis() + new Random().nextInt(1000000000));
                dl.setProperty("directlink", directlink);
                dl.setLinkID(directlink);
                dl.setAvailable(true);
                dl.setContentUrl(directlink);
                decryptedLinks.add(dl);
            }
        } else {
            /* TYPE_USER */
            fpName = username;

            this.br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            int addedlinks_total = 0;
            int addedlinks = 0;
            do {
                if (this.isAbort()) {
                    logger.info("Decryption aborted by user");
                    return decryptedLinks;
                }
                addedlinks = 0;
                if (addedlinks_total > 0) {
                    this.br.getPage("/girls/" + username + "/photos/?partial=true&offset=" + addedlinks_total);
                }
                final String[] links = br.getRegex("\"(/girls/[^/]+/album/\\d+/[A-Za-z0-9\\-_]+/)[^<>\"]*?\"").getColumn(0);
                if ((links == null || links.length == 0) && !loggedin) {
                    /* Account is needed most times */
                    logger.info("Account needed to crawl link: " + parameter);
                    return decryptedLinks;
                }
                if (links == null || links.length == 0) {
                    logger.warning("Decrypter broken for link: " + parameter);
                    return null;
                }
                for (final String singleLink : links) {
                    final String final_album_url = "https://www." + this.getHost() + singleLink;
                    if (!dupecheck.contains(final_album_url)) {
                        dupecheck.add(final_album_url);
                        decryptedLinks.add(createDownloadlink(final_album_url));
                        addedlinks++;
                        addedlinks_total++;
                    }
                }
            } while (this.br.containsHTML("id=\"load\\-more\"") && addedlinks > 0);
        }

        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }

        return decryptedLinks;
    }
}
