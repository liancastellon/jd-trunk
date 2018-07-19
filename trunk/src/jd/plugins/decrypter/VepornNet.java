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
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

import org.jdownloader.plugins.components.antiDDoSForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "veporns.com" }, urls = { "https?://(?:www\\.)?ve(?:-)?porns?\\.(net|com)/video/[A-Za-z0-9\\-_]+" })
public class VepornNet extends antiDDoSForDecrypt {
    public VepornNet(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        getPage(parameter);
        if (br.containsHTML(">Site is too crowded<")) {
            for (int i = 1; i <= 3; i++) {
                sleep(i * 3 * 1001l, param);
                getPage(parameter);
                if (!br.containsHTML(">Site is too crowded<")) {
                    break;
                }
            }
        }
        if (br.getHttpConnection().getResponseCode() == 404 || !br.getURL().contains("video/") || br.containsHTML("URL=https?://www.ve(?:-)?porns?.(net|com)'")) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String fpName = br.getRegex("(rhgd)").getMatch(0);
        final String[] links = br.getRegex("comment\\((\\d+)\\)").getColumn(0);
        if (links == null || links.length == 0) {
            throw new DecrypterException("Decrypter broken for link: " + parameter);
        }
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        int counter = 1;
        for (final String singleLink : links) {
            if (this.isAbort()) {
                return decryptedLinks;
            }
            final Browser br = this.br.cloneBrowser();
            getPage(br, "/ajax.php?page=video_play&thumb&theme=&video=&id=" + singleLink + "&server=" + counter);
            if (br.containsHTML(">Site is too crowded<")) {
                for (int i = 1; i <= 3; i++) {
                    sleep(i * 3 * 1001l, param);
                    getPage(br, "/ajax.php?page=video_play&thumb&theme=&video=&id=" + singleLink + "&server=" + counter);
                    if (!br.containsHTML(">Site is too crowded<")) {
                        break;
                    }
                }
            }
            String finallink = br.getRegex("iframe src='(https?[^<>']+)'").getMatch(0);
            if (finallink == null) {
                finallink = br.getRegex("iframe src=\"(https?[^<>\"]+)\"").getMatch(0);
                if (finallink == null) {
                    continue;
                }
            }
            decryptedLinks.add(createDownloadlink(finallink));
            counter++;
        }
        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }
}
