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

import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperCrawlerPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "mirrorace.com" }, urls = { "https?://(?:www\\.)?mirrorace.com/m/[A-Za-z0-9]+" })
public class MirroraceCom extends antiDDoSForDecrypt {
    public MirroraceCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML(">Links Unavailable<")) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        final String fpName = br.getRegex("<title>\\s*(?:Download)?\\s*([^<]*?)\\s*(?:-\\s*MirrorAce)?\\s*</title>").getMatch(0);
        // since mirrorace is single file response > many mirrors, results in package of compression parts all have there own package...
        // which then results in extraction tasks failing...
        final FilePackage fp = fpName != null && false ? FilePackage.getInstance() : null;
        if (fp != null) {
            fp.setName(Encoding.htmlDecode(fpName.trim()));
        }
        final String[] links = br.getRegex("\"(https?://mirrorace\\.com/m/[A-Za-z0-9]+/\\d+\\?t=[^<>\"]*?)\"").getColumn(0);
        if (links == null || links.length == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        for (final String singleLink : links) {
            if (this.isAbort()) {
                return decryptedLinks;
            }
            final Browser br = this.br.cloneBrowser();
            getPage(br, singleLink);
            {
                // sometimes a captcha event can happen here
                final Form captchaForm = br.getFormByRegex("class=\"g-recaptcha\"");
                if (captchaForm != null) {
                    final String recaptchaV2Response = new CaptchaHelperCrawlerPluginRecaptchaV2(this, br).getToken();
                    captchaForm.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                    submitForm(br, captchaForm);
                }
            }
            final String finallink = br.getRegex("<a class=\"uk-button\\s*(?:uk-button-large)?\\s*uk-button-primary\" href=\"(http[^<>\"]+)").getMatch(0);
            if (finallink == null) {
                return null;
            }
            final DownloadLink dl;
            if (finallink.contains("mirrorace.com")) {
                final Browser brc = br.cloneBrowser();
                brc.setFollowRedirects(false);
                brc.getPage(finallink);
                if (brc.getRedirectLocation() != null) {
                    dl = createDownloadlink(brc.getRedirectLocation());
                } else {
                    continue;
                }
            } else {
                dl = createDownloadlink(finallink);
            }
            decryptedLinks.add(dl);
            if (fp != null) {
                fp.add(dl);
            }
            distribute(dl);
        }
        return decryptedLinks;
    }
}
