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

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "amigura.co.uk" }, urls = { "http://[\\w\\.]*?amigura\\.co\\.uk/(s\\d+|send_file)\\.php\\?d=(\\d+(-|/)[A-Z0-9]+(-|/)\\d+(-|/)|\\d+/).+" }, flags = { 0 })
public class MgrCk extends PluginForDecrypt {

    public MgrCk(PluginWrapper wrapper) {
        super(wrapper);
    }

    // @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        setBrowserExclusive();
        br.setFollowRedirects(false);
        br.getPage(parameter);
        String link;
        if (br.getRedirectLocation() != null)
            link = br.getRedirectLocation();
        else {
            String error = br.getRegex("class=\"sm\">(.*?)<").getMatch(0);
            if (error.length() != 0) {
                logger.warning(error + " (" + parameter + ")");
                return new ArrayList<DownloadLink>();
            }
            link = br.getRegex("1;url=(.*?)\"").getMatch(0);
            if (link == null) link = br.getRegex("action=\"(.*?)\"").getMatch(0);
            if (link == null) return null;
        }

        decryptedLinks.add(createDownloadlink(link));
        return decryptedLinks;
    }

    // @Override

}
