//jDownloader - Downloadmanager
//Copyright (C) 2008  JD-Team support@jdownloader.org
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.Plugin;
import jd.plugins.components.SiteType.SiteTemplate;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class ImgShotDecrypt extends antiDDoSForDecrypt {
    /**
     * Returns the annotations names array
     */
    public static String[] getAnnotationNames() {
        return new String[] { "pixup.us", "damimage.com", "imgstudio.org", "imgshot.com", "imgspot.org", "imgserve.net", "dimtus.com", "imagedecode.com", "imageteam.org", "imgadult.com", "imgtornado.com", "acidimg.cc" };
    }

    /**
     * Returns the annotation pattern array
     */
    public static String[] getAnnotationUrls() {
        final String[] names = getAnnotationNames();
        final String[] ret = new String[names.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = "^https?://(www\\.)?" + Pattern.quote(names[i]) + "/img\\-[a-z0-9\\-_]+\\.(?:html|jpe?g(?:\\.html)?)$";
        }
        return ret;
    }

    public ImgShotDecrypt(final PluginWrapper wrapper) {
        super(wrapper);
    }

    // ImgScriptDecrypt_imgContinue Version: 1.0
    // Example url: http://imagehost.com/img-az09.html
    /* All of the above domains use the same script. Last checked version: 1.2 */
    @SuppressWarnings("deprecation")
    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        setBrowserExclusive();
        br.setFollowRedirects(true);
        prepBR(br);
        br.getPage(parameter);
        if (isOffline(this.br)) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        handleContinueStep(this.br);
        final String finallink = getFinallink(this.br, parameter);
        if (finallink == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        final DownloadLink link;
        decryptedLinks.add(link = createDownloadlink("directhttp://" + finallink));
        if ("acidimg.cc".equals(getHost())) {
            final String title = br.getRegex("<title>(?:ACiDiMG / )?(.*?)</title>").getMatch(0);
            if (StringUtils.isNotEmpty(title)) {
                final String extension = Plugin.getFileNameExtensionFromURL(finallink);
                link.setForcedFileName(title + extension);
            }
        }
        if ("imgadult.com".equals(getHost())) {
            final String title = br.getRegex("<title>(.*?) \\|").getMatch(0);
            if (StringUtils.isNotEmpty(title)) {
                link.setForcedFileName(title);
            }
        }
        return decryptedLinks;
    }

    public static boolean isOffline(final Browser br) {
        if (br.containsHTML(">Image Removed or Bad Link<|>This image has been removed") || br.getURL().contains("/noimage.php") || br.getHttpConnection().getResponseCode() == 404) {
            return true;
        }
        return false;
    }

    public static void handleContinueStep(final Browser br) throws IOException {
        /* general */
        if (br.containsHTML("imgContinue") || br.containsHTML("continue_to_image")) {
            br.postPage(br.getURL(), "imgContinue=Continue+to+image+...+");
        } else if (br.getHost().equals("imgadult.com")) {
            /* Extra handling needed for this one ... */
            br.postPage(br.getURL(), "cti=1&ref=-&rc=0&bt=0&bw=gecko");
            br.getPage(br.getURL());
        }
    }

    public static String getFinallink(final Browser br, final String sourcelink) {
        return br.getRegex("(\\'|\")(https?://([\\w\\-]+\\.)?" + Pattern.quote(Browser.getHost(sourcelink)) + "((?:/upload)?/big/|(?:/uploads)?/images/)[^<>\"]*?)\\1").getMatch(1);
    }

    public static String getFid(final String sourcelink) {
        return new Regex(sourcelink, "/img\\-([a-z0-9]+)").getMatch(0);
    }

    public static Browser prepBR(final Browser br) {
        br.getHeaders().put("User-Agent", jd.plugins.components.UserAgents.stringUserAgent());
        return br;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.ImageHosting_ImgShot;
    }
}