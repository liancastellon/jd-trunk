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

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "glumbouploads.com" }, urls = { "http://(www\\.)?(glumbouploads|uploads\\.glumbo)\\.com/users/[a-z0-9_]+/\\d+" }, flags = { 0 })
public class GlumboUploadsComFolder extends PluginForDecrypt {

    public GlumboUploadsComFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String HOST  = "glumbouploads.com";
    private static final String HOSTS = "(glumbouploads|uploads\\.glumbo)\\.com";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        parameter = parameter.replace("glumbouploads.com/", "uploads.glumbo.com/");
        br.setFollowRedirects(true);
        br.setCookie("http://" + HOST, "lang", "english");
        br.getPage(parameter);
        if (br.containsHTML("No such user exist")) return decryptedLinks;
        String[] links = br.getRegex("\"(http://(www\\.)?" + HOST + "/[a-z0-9]{12})").getColumn(0);
        if (links == null || links.length == 0) return null;
        for (String dl : links)
            decryptedLinks.add(createDownloadlink(dl));
        return decryptedLinks;
    }

}
