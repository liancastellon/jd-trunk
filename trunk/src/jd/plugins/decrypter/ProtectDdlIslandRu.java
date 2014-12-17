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

import java.awt.Point;
import java.io.File;
import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.gui.UserIO;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "protect.ddl-island.ru" }, urls = { "http://(www\\.)?protect\\.ddl\\-island\\.ru/[A-Za-z0-9]+" }, flags = { 0 })
public class ProtectDdlIslandRu extends PluginForDecrypt {

    public ProtectDdlIslandRu(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String INVALIDLINKS = "http://(www\\.)?protect\\.ddl\\-island\\.ru/(img|other)";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        if (parameter.matches(INVALIDLINKS)) {
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setFinalFileName(new Regex(parameter, "https?://[^<>\"/]+/(.+)").getMatch(0));
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404 || br.getHttpConnection().getContentType().matches("(application/javascript|text/css)")) {
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setFinalFileName(new Regex(parameter, "https?://[^<>\"/]+/(.+)").getMatch(0));
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        final String captchapass = Encoding.urlEncode(generatePass());
        br.postPage("http://protect.ddl-island.ru/php/Qaptcha.jquery.php", "action=qaptcha&qaptcha_key=" + captchapass);
        br.postPage(parameter, captchapass + "=&submit=Submit+form");
        if (br.containsHTML("<b>Nom :</b></td><td></td></tr>")) {
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setFinalFileName(new Regex(parameter, "https?://[^<>\"/]+/(.+)").getMatch(0));
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        if (!br.containsHTML("img\\.php\\?get_captcha=true")) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        boolean success = false;
        for (int i = 1; i <= 3; i++) {
            final File captchaFile = this.getLocalCaptchaFile();
            Browser.download(captchaFile, br.cloneBrowser().openGetConnection("http://protect.ddl-island.ru/img.php?get_captcha=true"));
            final Point p = UserIO.getInstance().requestClickPositionDialog(captchaFile, "protect.ddl-island.ru | " + String.valueOf(i + 1) + "/3", null);
            if (p == null) {
                throw new DecrypterException(DecrypterException.CAPTCHA);
            }
            br.postPage(br.getURL(), "position%5B%5D.x=" + String.valueOf(p.x) + "&position%5B%5D.y=" + String.valueOf(p.y));
            if (br.containsHTML("img\\.php\\?get_captcha=true")) {
                continue;
            }
            success = true;
            break;
        }
        if (!success) {
            throw new DecrypterException(DecrypterException.CAPTCHA);
        }
        final String finallink = br.getRegex(">Lien :</b></td><td><a href=\"(http[^<>\"]*?)\"").getMatch(0);
        if (finallink == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        decryptedLinks.add(createDownloadlink(finallink));

        return decryptedLinks;
    }

    private String generatePass() {
        int nb = 32;
        final String chars = "azertyupqsdfghjkmwxcvbn23456789AZERTYUPQSDFGHJKMWXCVBN_-#@";
        String pass = "";
        for (int i = 0; i < nb; i++) {
            long wpos = Math.round(Math.random() * (chars.length() - 1));
            int lool = (int) wpos;
            pass += chars.substring(lool, lool + 1);
        }
        return pass;
    }

}
