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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.pluginUtils.Recaptcha;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "iload.to" }, urls = { "http://[\\w\\.]*?links\\.iload\\.to/links/\\?lid=.+" }, flags = { 0 })
public class LdTTemp extends PluginForDecrypt {

    public LdTTemp(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.getPage(parameter);
        for (int i = 0; i <= 3; i++) {
            Recaptcha rc = new Recaptcha(br);
            rc.parse();
            rc.load();
            File cf = rc.downloadCaptcha(getLocalCaptchaFile());
            String c = getCaptchaCode(cf, param);
            rc.setCode(c);
            if (br.containsHTML("(api.recaptcha.net|Das war leider Falsch)")) continue;
            break;
        }
        if (br.containsHTML("(api.recaptcha.net|Das war leider Falsch)")) throw new PluginException(LinkStatus.ERROR_CAPTCHA);
        // container handling
//        String containerregex = br.getRegex("\"(\\?lid=.*?)\"").getMatch(0);
//        if (containerregex != null) {
//            containerregex = containerregex.replace("amp;", "");
//            decryptedLinks = loadcontainer(br, containerregex);
//            if (decryptedLinks != null && decryptedLinks.size() > 0) return decryptedLinks;
//        }
        String[] links = br.getRegex("<a href=\"(http.*?)\" target=").getColumn(0);
        if (links.length == 0) return null;
        for (String finallink : links)
            decryptedLinks.add(createDownloadlink(finallink));

        return decryptedLinks;
    }

    // by jiaz
    private ArrayList<DownloadLink> loadcontainer(Browser br, String format) throws IOException, PluginException {
        Browser brc = br.cloneBrowser();
        String dlclinks = "http://links.iload.to/links/" + format;
        String test = Encoding.htmlDecode(dlclinks);
        File file = null;
        URLConnectionAdapter con = brc.openGetConnection(dlclinks);
        if (con.getResponseCode() == 200) {
            file = JDUtilities.getResourceFile("tmp/ldttemp/" + test.replaceAll("(http://links.iload.to/links/|/|\\?|\\&|%)", "") + ".dlc");
            if (file == null) return null;
            file.deleteOnExit();
            brc.downloadConnection(file, con);
        } else {
            con.disconnect();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }

        if (file != null && file.exists() && file.length() > 100) {
            ArrayList<DownloadLink> decryptedLinks = JDUtilities.getController().getContainerLinks(file);
            if (decryptedLinks.size() > 0) return decryptedLinks;
        } else {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        return null;
    }

}
