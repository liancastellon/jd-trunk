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
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.Request;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.hoster.DummyScriptEnginePlugin;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "vsco.co" }, urls = { "https?://(?:[^/]+\\.vsco\\.co/grid/\\d+|(?:www\\.)?vsco\\.co/[a-zA-Z0-9]+/grid/\\d+)" }, flags = { 0 })
public class VscoCo extends PluginForDecrypt {

    public VscoCo(PluginWrapper wrapper) {
        super(wrapper);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        final String username = getUsername(parameter);
        br.getPage(parameter);
        final String cookie_vs = br.getCookie(this.getHost(), "vs");
        final String siteid = PluginJSonUtils.getJson(br, "id");
        long amount_total = 0;
        /* More than 500 possible */
        int max_count_per_page = 500;
        int page = 1;
        if (cookie_vs == null || siteid == null) {
            return null;
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(username);

        do {
            if (this.isAbort()) {
                logger.info("Decryption aborted by user");
                break;
            }
            final Browser ajax = br.cloneBrowser();
            ajax.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
            ajax.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            ajax.getPage("/ajxp/" + cookie_vs + "/2.0/medias?site_id=" + siteid + "&page=" + page + "&size=" + max_count_per_page);
            LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(ajax.toString());
            if (page == 1) {
                amount_total = DummyScriptEnginePlugin.toLong(entries.get("total"), 0);
                if (amount_total == 0) {
                    logger.info("User has zero content!");
                    decryptedLinks.add(this.createOfflinelink(parameter));
                    return decryptedLinks;
                }
            }
            final ArrayList<Object> ressources = (ArrayList) entries.get("media");
            for (final Object ressource : ressources) {
                entries = (LinkedHashMap<String, Object>) ressource;
                final String fid = (String) entries.get("_id");
                if (fid == null) {
                    return null;
                }
                final String medialink = (String) entries.get("permalink");
                String url_content = (String) entries.get("responsive_url");
                if (!(url_content.startsWith("http") || url_content.startsWith("//"))) {
                    url_content = Request.getLocation("//" + url_content, br.getRequest());
                }
                final String filename = username + "_" + fid + getFileNameExtensionFromString(url_content, ".jpg");
                final DownloadLink dl = this.createDownloadlink("directhttp://" + url_content);
                dl.setContentUrl(medialink);
                dl.setName(filename);
                dl.setAvailable(true);
                decryptedLinks.add(dl);
                fp.add(dl);
                distribute(dl);
            }
            if (ressources.size() < max_count_per_page) {
                /* Fail safe */
                break;
            }
            page++;
        } while (decryptedLinks.size() < amount_total);

        if (decryptedLinks.size() == 0 && !this.isAbort()) {
            return null;
        }
        return decryptedLinks;
    }

    private String getUsername(final String parameter) {
        String username = new Regex(parameter, "https?://([^/]+)\\.vsco\\.co/").getMatch(0);
        if (username == null) {
            username = new Regex(parameter, "vsco\\.co/([a-zA-Z0-9]+)/grid").getMatch(0);
        }
        return username;
    }

}
