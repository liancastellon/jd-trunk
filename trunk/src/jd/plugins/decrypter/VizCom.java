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

import java.text.DecimalFormat;
import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.jdownloader.plugins.components.antiDDoSForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "viz.com" }, urls = { "https?://(?:www\\.)?viz\\.com/(read/)?[^/]+/[^/]+/(?:chapter/|issue/|product/|manga/product/|manga/product/digital/)\\d+" })
public class VizCom extends antiDDoSForDecrypt {
    public VizCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* Tags: MangaPictureCrawler */
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        getPage(parameter);
        final Regex urlinfo = new Regex(parameter, "([^/]+)/(?:[^/]+)/(\\d+)");
        final String url_name = urlinfo.getMatch(0);
        final String manga_id = urlinfo.getMatch(1);
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("Sorry, this website is not available in your country.")) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        /*
         * Fog: Length: x pages is always visible. but not always correct for pages available (e.g. previews) so we check the javascript for
         * the proper amount of pages, and then only use Length: x pages if var pages = 0
         */
        int accountPages = -1;
        if (AccountController.getInstance().getValidAccount(this) != null) {
            String pages = br.getRegex("<strong>Length</strong>\\s*(\\d+)\\s*pages\\s*</div>").getMatch(0);
            if (pages != null) {
                accountPages = Integer.parseInt(pages);
            }
            pages = br.getRegex("var pages\\s*=\\s*(\\d+);").getMatch(0);
            if (accountPages == -1 || (pages != null && Integer.parseInt(pages) > accountPages)) {
                accountPages = Integer.parseInt(pages);
            }
            if (accountPages == -1) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        int freePages = -1;
        {
            String pages = br.getRegex("var pages\\s*=\\s*(\\d+);").getMatch(0);
            if (pages != null) {
                freePages = Integer.parseInt(pages);
            }
            pages = br.getRegex("<strong>Length</strong>\\s*(\\d+)\\s*pages\\s*</div>").getMatch(0);
            if (freePages == -1 || (pages != null && Integer.parseInt(pages) == freePages)) {
                freePages = Integer.parseInt(pages);
            }
            if (freePages == -1) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        final int pages = Math.max(accountPages, freePages);
        final DecimalFormat page_formatter_page = new DecimalFormat("000");
        final String ext = ".jpg";
        int page_added_num = 0;
        int page_current = 0;
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(manga_id + "_" + url_name);
        do {
            if (this.isAbort()) {
                return decryptedLinks;
            }
            final int page_for_url_access = page_current;
            // accessPage(br, manga_id, Integer.toString(page_for_url_access));
            // final String[] urls = br.getRegex("url=\"(http[^<>\"]+)\"").getColumn(0);
            final String[] dummyarray = new String[] { Integer.toString(page_current), Integer.toString(page_current + 1) };
            page_added_num = dummyarray.length;
            for (final String dummy : dummyarray) {
                final String page_formatted = page_formatter_page.format(page_current);
                final String filename = manga_id + "_" + url_name + "_" + page_formatted + ext;
                final DownloadLink dl = this.createDownloadlink("http://vizdecrypted/" + manga_id + "_" + page_current + "_" + page_for_url_access);
                dl._setFilePackage(fp);
                dl.setFinalFileName(filename);
                dl.setContentUrl(parameter);
                dl.setLinkID(filename);
                dl.setAvailable(true);
                decryptedLinks.add(dl);
                // distribute(dl);
                page_current++;
                if (page_current > pages) {
                    /* Do not add too many pages - without this check, we might get one page too much! */
                    break;
                }
            }
        } while (page_added_num >= 2 && page_current <= pages);
        return decryptedLinks;
    }

    public static void accessPage(final Browser br, final String manga_id, final String page) throws Exception {
        final String page_url = "https://www." + br.getHost() + "/manga/get_manga_url?manga_id=" + manga_id + "&page=" + page + "&device_id=3&loadermax=1";
        br.getHeaders().put("Referer", "https://www.viz.com/assets/reader-" + System.currentTimeMillis() + ".swf");
        br.getPage(page_url);
    }
}
