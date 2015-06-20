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

//EmbedDecrypter 0.1
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "perfectgirls.net" }, urls = { "http://(www\\.)?(perfectgirls\\.net/\\d+/|(ipad|m)\\.perfectgirls\\.net/gal/\\d+/).{1}" }, flags = { 0 })
public class PerfectGirlsNet extends PluginForDecrypt {

    public PerfectGirlsNet(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString().replaceAll("(ipad|m)\\.perfectgirls\\.net/gal/", "perfectgirls.net/");
        br.setFollowRedirects(true);
        br.getPage(parameter);
        if (br.containsHTML("No htmlCode read")) {
            final DownloadLink offline = createDownloadlink("http://perfectgirlsdecrypted.net/" + System.currentTimeMillis() + "/x");
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        String filename = br.getRegex("<title>([^<>\"]*?) ::: PERFECT GIRLS</title>").getMatch(0);
        decryptedLinks = jd.plugins.decrypter.PornEmbedParser.findEmbedUrls(this.br, filename);
        if (decryptedLinks != null && decryptedLinks.size() > 0) {
            return decryptedLinks;
        }
        decryptedLinks = new ArrayList<DownloadLink>();
        final DownloadLink main = createDownloadlink(parameter.replace("perfectgirls.net/", "perfectgirlsdecrypted.net/"));
        if (br.containsHTML("src=\"http://(www\\.)?dachix\\.com/flashplayer/flvplayer\\.swf\"|\"http://(www\\.)?deviantclip\\.com/flashplayer/flvplayer\\.swf\"|thumbs/misc/not_available\\.gif")) {
            main.setAvailable(false);
            main.setProperty("offline", true);
        } else {
            main.setAvailable(true);
            main.setName(filename + ".mp4");
        }
        decryptedLinks.add(main);
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}