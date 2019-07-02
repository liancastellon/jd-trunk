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
import java.util.Set;

import org.jdownloader.controlling.PasswordUtils;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.HTMLParser;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

/**
 *
 * @version raz_Template-pastebin-201508200000
 * @author raztoki
 */
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "tupaste.info" }, urls = { "https?://(?:www\\.)?tupaste\\.info/(\\d+)/" })
public class TuPasteInfo extends PluginForDecrypt {
    public TuPasteInfo(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    // Tags: pastebin
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        br = new Browser();
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        final String uid = new Regex(parameter, this.getSupportedLinks()).getMatch(0);
        br.setFollowRedirects(true);
        br.getPage(parameter);
        /* Error handling */
        if (br.containsHTML("Page Not Found") || br.getHttpConnection() == null || br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        // can contain password protection
        final String passwordProtection = ">Password Required<";
        // can not bypass password
        if (br.containsHTML(passwordProtection)) {
            final int repeat = 2;
            for (int i = 0; i < repeat; i++) {
                final Form password = br.getFormByInputFieldKeyValue("password", "");
                if (password == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                // site is fucked they mess up the action url, so the password is never accepted even in browser!
                password.setAction(br.getURL());
                final String psw = Plugin.getUserInput(null, param);
                password.put("password", Encoding.urlEncode(psw));
                br.submitForm(password);
                if (br.containsHTML(passwordProtection)) {
                    if (i + 1 == repeat) {
                        throw new DecrypterException(DecrypterException.PASSWORD);
                    }
                    continue;
                }
                break;
            }
        }
        // another get request, this contains the body
        br.getPage("/" + uid + "/fullscreen.php?linenum=false&toolbar=false");
        final String plaintxt = br.getRegex("<pre id='thepaste'[^>]*>(.*?)</pre>\\s*").getMatch(0);
        if (plaintxt == null) {
            logger.info("Could not find 'plaintxt' : " + parameter);
            return decryptedLinks;
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
                if (!pws.isEmpty()) {
                    dl.setSourcePluginPasswordList(new ArrayList<String>(pws));
                }
                decryptedLinks.add(dl);
            }
        }
        return decryptedLinks;
    }
}