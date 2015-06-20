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

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "lbtube.com" }, urls = { "http://(www\\.)?lbtube\\.com/videos/\\d+/[a-z0-9\\-]+\\.html" }, flags = { 0 })
public class LbTubeCom extends PluginForDecrypt {

    public LbTubeCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* Using playerConfig script */
    /* Tags: playerConfig.php */
    // EmbedDecrypter 0.1.9

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        br.setFollowRedirects(false);
        if (br.getURL().equals("http://www.lbtube.com/404.php")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        String filename = br.getRegex("class=\"title_vid\">([^<>\"]*?)</div>").getMatch(0);
        decryptedLinks = jd.plugins.decrypter.PornEmbedParser.findEmbedUrls(this.br, filename);
        if (decryptedLinks == null || decryptedLinks.size() == 0) {
            return null;
        }
        return decryptedLinks;
    }

}
