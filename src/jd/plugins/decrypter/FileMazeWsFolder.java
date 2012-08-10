//jDownloader - Downloadmanager
//Copyright (C) 2011  JD-Team support@jdownloader.org
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

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "filemaze.ws" }, urls = { "http://(www\\.)?filemaze\\.ws/users/[a-z0-9]+/\\d+/[^<>\"/]+" }, flags = { 0 })
public class FileMazeWsFolder extends PluginForDecrypt {

    public FileMazeWsFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String HOST = "filemaze.ws";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(true);
        br.setCookie("http://" + HOST, "lang", "english");
        br.getPage(parameter);
        if (br.containsHTML("No such user exist")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        final String[] links = br.getRegex("\"(http://(www\\.)?" + HOST + "/[a-z0-9]{12})").getColumn(0);
        final String[] folders = br.getRegex("<TD><a href=\"(http://(www\\.)?filemaze\\.ws/users/[a-z0-9]+/\\d+/[^<>\"/]*?)\"").getColumn(0);
        if ((links == null || links.length == 0) && folders == null || folders.length == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        if (links != null && links.length != 0) {
            for (final String dl : links)
                decryptedLinks.add(createDownloadlink(dl));
        }
        if (folders != null && folders.length != 0) {
            for (final String dl : links)
                decryptedLinks.add(createDownloadlink(dl));
        }
        return decryptedLinks;
    }

}
