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
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "nsanedown.com" }, urls = { "https?://(?:www\\.)?nsanedown\\.com/\\?request=\\d+" })
public class NsanedownCom extends PluginForDecrypt {
    public NsanedownCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString().replace("http:", "https:");
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        final String[] links = br.getRegex("<a[^>]+data\\-ipb=\"nomediaparse\"[^>]+href=\"(http[^<>\"]+)\"[^>]+>").getColumn(0);
        if (links == null || links.length == 0) {
            if (!this.br.containsHTML("Download</a>")) {
                /* Empty page --> Offline */
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        for (final String singleLink : links) {
            final DownloadLink dl = createDownloadlink(singleLink);
            if (singleLink.contains("proxy.nsanedown.com")) {
                dl.setProperty("mainlink", parameter);
            }
            decryptedLinks.add(dl);
        }
        return decryptedLinks;
    }
}
