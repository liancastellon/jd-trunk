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
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "catcut.net" }, urls = { "https?://(?:www\\.)?catcut\\.net/[A-Za-z0-9]+" })
public class CatcutNet extends PluginForDecrypt {
    public CatcutNet(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String finallink = br.getRegex("<span  id=\"noCaptchaBlock\"[^<>]+>\\s*?<a href=\"(http[^<>\"]+)\"").getMatch(0);
        if (finallink == null) {
            // now within base64 element
            String go_url = br.getRegex("var go_url\\s*=\\s*decodeURIComponent\\('(.*?)'\\)").getMatch(0);
            if (go_url != null) {
                // under the a value
                go_url = Encoding.urlDecode(go_url, true);
                final String a = new Regex(go_url, "a=([a-zA-Z0-9_/\\+\\=\\-%]+)&?").getMatch(0);
                if (a != null) {
                    finallink = Encoding.Base64Decode(a);
                }
            }
            if (finallink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        decryptedLinks.add(createDownloadlink(finallink));
        return decryptedLinks;
    }
}
