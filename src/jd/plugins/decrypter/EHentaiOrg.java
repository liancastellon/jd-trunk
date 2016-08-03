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
import java.util.Random;

import org.jdownloader.controlling.filter.CompiledFiletypeFilter;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "e-hentai.org" }, urls = { "https?://(?:www\\.)?(?:g\\.e-hentai\\.org|exhentai\\.org)/g/(\\d+)/[a-z0-9]+" }, flags = { 0 })
public class EHentaiOrg extends PluginForDecrypt {

    public EHentaiOrg(PluginWrapper wrapper) {
        super(wrapper);
    }

    @SuppressWarnings("deprecation")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final PluginForHost hostplugin = JDUtilities.getPluginForHost("e-hentai.org");
        final Account aa = AccountController.getInstance().getValidAccount(hostplugin);
        if (aa != null) {
            ((jd.plugins.hoster.EHentaiOrg) hostplugin).login(this.br, aa, false);
        }
        // links are transferable between the login enforced url and public, but may not be available on public
        final String parameter = aa == null ? param.toString().replace("exhentai.org/", "g.e-hentai.org/") : param.toString().replace("g.e-hentai.org/", "exhentai.org/");

        final String uid = new Regex(parameter, this.getSupportedLinks()).getMatch(0);
        if (uid == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "fuid can not be found");
        }
        this.br.setFollowRedirects(true);
        br.setCookie(Browser.getHost(parameter), "nw", "1");
        br.getPage(parameter);
        if (br.containsHTML("Key missing, or incorrect key provided") || br.containsHTML("class=\"d\"") || br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String fpName = ((jd.plugins.hoster.EHentaiOrg) hostplugin).getTitle(br);
        if (fpName == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "fpName can not be found");
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);

        int pagemax = 0;
        final String[] pages = br.getRegex("/?p=(\\d+)\" onclick=").getColumn(0);
        if (pages != null && pages.length != 0) {
            for (final String aPage : pages) {
                final int pageint = Integer.parseInt(aPage);
                if (pageint > pagemax) {
                    pagemax = pageint;
                }
            }
        }
        final DecimalFormat df = new DecimalFormat("0000");
        int counter = 1;
        for (int page = 0; page <= pagemax; page++) {
            if (this.isAbort()) {
                logger.info("Decryption aborted by user: " + parameter);
                return decryptedLinks;
            }
            final Browser br2 = br.cloneBrowser();
            if (page > 0) {
                sleep(new Random().nextInt(5000), param);
                br2.getPage(parameter + "/?p=" + page);
            }
            final String[] links = br2.getRegex("\"(https?://(?:g\\.e-hentai|exhentai)\\.org/s/[a-z0-9]+/" + uid + "-\\d+)\"").getColumn(0);
            if (links == null || links.length == 0 || fpName == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            for (final String singleLink : links) {
                final DownloadLink dl = createDownloadlink(singleLink);
                final String namepart = fpName + "_" + uid + "-" + df.format(counter);
                dl.setProperty("namepart", namepart);
                dl.setName(namepart);
                dl.setMimeHint(CompiledFiletypeFilter.ImageExtensions.BMP);
                dl.setAvailable(true);
                fp.add(dl);
                distribute(dl);
                decryptedLinks.add(dl);
                counter++;
            }
        }
        return decryptedLinks;
    }

    /* NOTE: no override to keep compatible to old stable */
    public int getMaxConcurrentProcessingInstances() {
        /* Too many processes = server hates us */
        return 1;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}