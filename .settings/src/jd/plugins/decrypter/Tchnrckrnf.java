//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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

@DecrypterPlugin(revision = "$Revision: 16544 $", interfaceVersion = 2, names = { "technorocker.info" }, urls = { "http://[\\w\\.]*?technorocker\\.info/download/[0-9]+/.*" }, flags = { 0 })
public class Tchnrckrnf extends PluginForDecrypt {

    private static ArrayList<String> pwList = new ArrayList<String>();
    static {
        pwList.add("technorocker");
    }

    public Tchnrckrnf(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();

        br.getPage(parameter);
        String link = br.getRegex("window\\.location\\.href = \\'(.*?)\\';").getMatch(0);
        if (link == null) return null;
        DownloadLink dl_link = createDownloadlink(link);
        dl_link.setSourcePluginPasswordList(pwList);
        decryptedLinks.add(dl_link);

        return decryptedLinks;
    }

}
