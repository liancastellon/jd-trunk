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
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "iwara.tv" }, urls = { "https?://(?:[A-Za-z0-9]+\\.)?(?:trollvids\\.com|iwara\\.tv)/(?:videos|node)/[A-Za-z0-9]+" })
public class IwaraTv extends PluginForDecrypt {
    public IwaraTv(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        final PluginForHost hostPlugin = JDUtilities.getPluginForHost("iwara.tv");
        final Account aa = AccountController.getInstance().getValidAccount(hostPlugin);
        if (aa != null) {
            try {
                ((jd.plugins.hoster.IwaraTv) hostPlugin).login(br, aa, false);
            } catch (final Throwable e) {
            }
        }
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("class=\"cb_error\"|\"Processing video|>Sort by:")) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        String filename = br.getRegex("<h1 class=\"title\">([^<>\"]+)</h1>").getMatch(0);
        if (filename == null) {
            filename = new Regex(br.getURL(), "/videos/(.+)").getMatch(0);
        }
        filename = Encoding.htmlDecode(filename).trim();
        String externID = br.getRegex("\"(https?://docs\\.google\\.com/file/d/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(?:https?:)?//(?:www\\.)?youtube(?:\\-nocookie)?\\.com/embed/([^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            externID = "https://www.youtube.com/watch?v=" + externID;
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        final String source_html = br.getRegex("<div class=\"watch_left\">(.*?)<div class=\"rating_container\">").getMatch(0);
        if (source_html != null) {
            externID = new Regex(source_html, "\"(https?[^<>\"]*?)\"").getMatch(0);
            if (externID != null) {
                decryptedLinks.add(createDownloadlink(externID));
                return decryptedLinks;
            }
        }
        final DownloadLink dl = createDownloadlink(br.getURL().replace("iwara.tv/", "iwaradecrypted.tv/"));
        decryptedLinks.add(dl);
        return decryptedLinks;
    }
}
