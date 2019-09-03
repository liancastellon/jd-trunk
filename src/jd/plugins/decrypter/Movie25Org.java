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

import org.appwork.utils.Regex;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

@DecrypterPlugin(revision = "$Revision: 41211 $", interfaceVersion = 3, names = { "movies25.org" }, urls = { "https?://(www\\.)?movies25\\.org/((movie|watch)/)?.+\\.html?.*" })
public class Movie25Org extends antiDDoSForDecrypt {
    public Movie25Org(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(true);
        getPage(parameter);
        String fpName = br.getRegex("<meta name=\"description\" content=\"Watch\\s+([^\"]+)\\s+[Oo]nline\\s+[\\-|]").getMatch(0);
        String[] links = null;
        links = br.getRegex("<iframe width=\"100%\"[^>]*src=\"([^\"]+)\"[^>]*>").getColumn(0);
        if (links == null || links.length == 0) {
            links = br.getRegex("class=\"chapter\" href=\'([^\']+)\'").getColumn(0);
        }
        if (links == null || links.length == 0) {
            String[][] resultBlock = br.getRegex("<ul\\s*class=\"[^\"]*list-episode-item\"\\s*>[^$]+</ul>").getMatches();
            if (resultBlock != null && resultBlock.length > 0) {
                links = new Regex(resultBlock[0][0], "<a class=\"img\" href=\"([^\"]+)\">").getColumn(0);
            }
        }
        if (links != null && links.length > 0) {
            for (String link : links) {
                link = Encoding.htmlDecode(link);
                if (link.startsWith("/")) {
                    link = br.getURL(link).toString();
                }
                decryptedLinks.add(createDownloadlink(link));
            }
            if (fpName != null) {
                final FilePackage fp = FilePackage.getInstance();
                fp.setName(Encoding.htmlDecode(fpName.trim()));
                fp.setProperty("ALLOW_MERGE", true);
                fp.addLinks(decryptedLinks);
            }
        }
        return decryptedLinks;
    }
}