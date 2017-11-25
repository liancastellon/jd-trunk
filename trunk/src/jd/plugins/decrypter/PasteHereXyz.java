//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.HTMLParser;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.Plugin;
import jd.plugins.PluginForDecrypt;

import org.jdownloader.controlling.PasswordUtils;

/**
 *
 * @version raz_Template-pastebin-201508200000
 * @author raztoki
 */
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "pastehere.xyz" }, urls = { "https?://(?:www\\.)?pastehere\\.xyz/\\d+/?" })
public class PasteHereXyz extends PluginForDecrypt {
    public PasteHereXyz(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        final String id = new Regex(parameter, "/(\\d+)").getMatch(0);
        /* Error handling */
        if (br.containsHTML("<strong>Alert!</strong>\\s*Paste not found") || br.getHttpConnection() == null || br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        if (br.containsHTML("Password protected")) {
            while (!isAbort()) {
                Form form = br.getFormbyAction("/" + id);
                if (form == null) {
                    form = br.getFormbyAction("/" + id + "/");
                }
                if (form == null) {
                    throw new DecrypterException("Decrypter broken for link: " + parameter);
                }
                final List<String> passwords = getPreSetPasswords();
                final String passCode;
                if (passwords.size() > 0) {
                    passCode = passwords.remove(0);
                } else {
                    passCode = Plugin.getUserInput(null, param);
                }
                form.put("mypass", Encoding.urlEncode(passCode));
                br.submitForm(form);
                if (br.containsHTML("Password is Wrong")) {
                    br.getPage(parameter);
                }
                if (!br.containsHTML("Password protected")) {
                    break;
                }
            }
        }
        String plaintxt = br.getRegex("<div[^>]+id=\"p_data\"[^>]*>(.*?)\\s*</div>\\s*</div>").getMatch(0);
        if (plaintxt == null) {
            logger.info("Could not find 'plaintxt' : " + parameter + ", using full browser instead.");
            plaintxt = br.toString();
            // return decryptedLinks;
        }
        final Set<String> pws = PasswordUtils.getPasswords(plaintxt);
        final String[] links = HTMLParser.getHttpLinks(plaintxt, "");
        if (links == null || links.length == 0) {
            logger.info("Found no links[] from 'plaintxt' : " + parameter);
            return decryptedLinks;
        }
        /* avoid recursion */
        for (final String link : links) {
            if (!this.canHandle(link)) {
                final DownloadLink dl = createDownloadlink(link);
                if (pws != null && pws.size() > 0) {
                    dl.setSourcePluginPasswordList(new ArrayList<String>(pws));
                }
                decryptedLinks.add(dl);
            }
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}