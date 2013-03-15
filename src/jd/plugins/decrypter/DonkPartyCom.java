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
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "donkparty.com" }, urls = { "http://(www\\.)?donkparty\\.com/\\d+/.{1}" }, flags = { 0 })
public class DonkPartyCom extends PluginForDecrypt {

    public DonkPartyCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        br.setFollowRedirects(false);
        String parameter = param.toString() + "/";
        br.getPage(parameter);
        String tempID = br.getRedirectLocation();
        if (tempID != null) {
            DownloadLink dl = createDownloadlink(tempID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        if (br.containsHTML("Media not found\\!<")) {
            logger.info("Decrypter broken for link: " + parameter);
            return decryptedLinks;
        }
        String filename = br.getRegex("<span style=\"font\\-weight: bold; font\\-size: 18px;\">(.*?)</span><br").getMatch(0);
        if (filename == null) filename = br.getRegex("<title>(.*?) \\- Donk Party</title>").getMatch(0);
        if (filename == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        filename = filename.trim();
        tempID = br.getRegex("settings=(http://secret\\.shooshtime\\.com/playerConfig\\.php?.*?)\"").getMatch(0);
        if (tempID != null) {
            br.getPage(tempID);
            String finallink = br.getRegex("defaultVideo:(http://.*?);").getMatch(0);
            if (finallink == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            DownloadLink dl = createDownloadlink("directhttp://" + finallink);
            dl.setFinalFileName(filename + ".flv");
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        tempID = br.getRegex("xhamster\\.com/xembed\\.php\\?video=(\\d+)\"").getMatch(0);
        if (tempID != null) {
            decryptedLinks.add(createDownloadlink("http://xhamster.com/movies/" + tempID + "/" + System.currentTimeMillis() + ".html"));
            return decryptedLinks;
        }
        tempID = br.getRegex("(\\'|\")(http://(www\\.)?myxvids\\.com/embed_code/\\d+/\\d+/myxvids_embed\\.js)(\\'|\")").getMatch(1);
        if (tempID != null) {
            br.getPage(tempID);
            tempID = br.getRegex("var urlAddress = \"(http://[^<>\"]*?)\";").getMatch(0);
            if (tempID != null) {
                decryptedLinks.add(createDownloadlink(tempID));
                return decryptedLinks;
            }
            if (tempID == null) {
                String id = br.getRegex("src=\"(http[^ \"']+myxvids\\.com/embed/\\d+)").getMatch(0);
                if (id != null) {
                    br.getPage(id);
                    String finallink = br.getRegex("video_url: '(http[^\"']+)").getMatch(0);
                    if (finallink != null) {
                        // browser & flash will fill in other args (in the format of other plugins), but it doesn't seem necessary as download works.
                        // finallink + ?time=20130315100520&ahv=272678ee4d246a2c3ce8866ddd9af032&cv=5bde6b6fb56417ef49ba402c6e181a56&ref=http://www.myxvids.com/embed/ + id
                        DownloadLink dl = createDownloadlink("directhttp://" + finallink);
                        dl.setFinalFileName(filename + ".mp4");
                        decryptedLinks.add(dl);
                        return decryptedLinks;
                    }
                }
            }
        }
        if (br.containsHTML("megaporn.com/")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        if (tempID == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        return decryptedLinks;
    }

}
