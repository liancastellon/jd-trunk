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

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "protect.iplus4u.com" }, urls = { "http://(www\\.)?protect\\.iplus4u\\.com/[A-Za-z0-9\\-_\\.]+\\.html" })
public class ProtectIplus4uCom extends PluginForDecrypt {
    public ProtectIplus4uCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DecrypterScript_linkid=_linkcheck.php Version 0.1 */
    private static final String  RECAPTCHATEXT  = "api\\.recaptcha\\.net";
    private static final String  RECAPTCHATEXT2 = "google\\.com/recaptcha/api/challenge";
    private static final boolean SKIP_CAPTCHA   = true;

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        final String host = new Regex(parameter, "https?://(?:www\\.)?([^/]+)").getMatch(0);
        String linkid = new Regex(parameter, host + "/(?:check\\.)([a-z0-9]+)\\.html$").getMatch(0);
        if (linkid == null) {
            linkid = new Regex(parameter, host + "/([a-z0-9]+)(?:-.*?)?\\.html$").getMatch(0);
        }
        br.setFollowRedirects(true);
        br.setCustomCharset("utf-8");
        br.getPage(parameter);
        if (!SKIP_CAPTCHA) {
            boolean failed = true;
            for (int i = 0; i <= 5; i++) {
                if (!br.containsHTML(RECAPTCHATEXT) && !br.containsHTML(RECAPTCHATEXT2)) {
                    logger.warning("Decrypter broken for link: " + parameter);
                    return null;
                }
                final Recaptcha rc = new Recaptcha(br, this);
                rc.parse();
                rc.load();
                final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                final String c = getCaptchaCode("recaptcha", cf, param);
                final String postData = "recaptcha_challenge_field=" + rc.getChallenge() + "&recaptcha_response_field=" + Encoding.urlEncode(c) + "&x=" + Integer.toString(new Random().nextInt(100)) + "&y=" + Integer.toString(new Random().nextInt(100)) + "&captcha=recaptcha&mon_captcha=" + linkid;
                br.postPage("/showlinks.php", postData);
                if (br.containsHTML(">Captcha erroné vous allez être rediriger")) {
                    /* Prefer reCaptcha */
                    br.getPage(parameter + "&c=1");
                    continue;
                }
                failed = false;
                break;
            }
            if (failed) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
        }
        if (br.getURL().contains("check.")) {
            br.postPage("/linkid.php", "linkid=" + linkid + "&x=" + Integer.toString(new Random().nextInt(100)) + "&y=" + Integer.toString(new Random().nextInt(100)));
        }
        final String fpName = br.getRegex("font\\-size:90%;font\\-family:Arial,Helvetica,sans\\-serif;'>([^<>\"]*?)</td>").getMatch(0);
        String[] links = br.getRegex("style='font-size:70%;font-family:Arial,Helvetica,sans-serif;'>[\t\n\r ]+<a href=(https?://[^<>\"]*?) target=_blank").getColumn(0);
        if (links == null || links.length == 0) {
            if (br.containsHTML("href= target=_blank></a><br></br><a")) {
                logger.info("Link offline: " + parameter);
                return decryptedLinks;
            }
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        for (String singleLink : links) {
            if (!singleLink.contains(host)) {
                decryptedLinks.add(createDownloadlink(singleLink));
            }
        }
        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}