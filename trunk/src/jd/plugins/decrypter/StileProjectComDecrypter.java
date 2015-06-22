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

//EmbedDecrypter 0.1.3
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "stileproject.com" }, urls = { "http://(www\\.)?stileproject\\.com/video/\\d+" }, flags = { 0 })
public class StileProjectComDecrypter extends PluginForDecrypt {

    public StileProjectComDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        final DownloadLink mainlink = createDownloadlink(parameter.replace("stileproject.com/", "stileprojectdecrypted.com/"));
        String filename = br.getRegex("<title>([^<>\"]*?) \\- StileProject\\.com</title>").getMatch(0);
        /* Check if the video is selfhosted */
        final String externID = br.getRegex("stileproject\\.com/embed/(\\d+)").getMatch(0);
        if (externID == null && this.br.getHttpConnection().getResponseCode() == 200) {
            decryptedLinks = jd.plugins.decrypter.PornEmbedParser.findEmbedUrls(this.br, filename);
        } else {
            decryptedLinks.add(mainlink);
        }
        return decryptedLinks;
    }

}
