//    jDownloader - Downloadmanager
//    Copyright (C) 2011  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.components.SiteType.SiteTemplate;

//Similar to SafeUrlMe (safeurl.me)
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {}, flags = {})
public class SflnkgNt extends abstractSafeLinking {

    /**
     * Returns the annotations names array
     *
     * @return
     */
    public static String[] getAnnotationNames() {
        return new String[] { "rawfile.co", "3download.safelinking.net", "deni1743-safelink.tk", "cryptit.so", "hosmy.com", "i7q.com", "jessica.suyalynx.com", "linkler.us", "link.yify.info", "links.sceper.ws", "linkshield.org", "mtsafelinking.org", "nexushd2urlprotector.tytung.com", "ninjasecure.cf", "nsad.xcarlos.safelinking.net", "oxyl.me", "r4dm.com", "protect.mmportal.info", "rgf.me", "safe.linkninja.net", "safe.dlinks.info", "safe.linksjunk.com", "safelinking.net", "safelinking.com", "safelinknsn.net", "savelinks.net", "safelinking.mobi", "sf.anime4u.ir", "sflnk.tk", "slinx.tk", "trollbridge.org", "sl.unspeakable.org", "vault.vhsclub.com", "url-shortener.info", "yls.re", "sflk.in" };
        // add new domains at the beginning of the array, not the END
    }

    /**
     * returns the annotation pattern array
     *
     * @return
     */
    public static String[] getAnnotationUrls() {
        String[] a = new String[getAnnotationNames().length];
        int i = 0;
        for (final String domain : getAnnotationNames()) {
            if (i == a.length) {
                // short link domain! www will redirect to standard page. no https (but we will correct via correctLinks call to
                // supportsHTTPS())
                a[i] = "https?://sflk\\.in/[a-zA-Z0-9]{10}";
            } else {
                // https is only supported on there safelinking.net domain, once again auto correct within correctLink call to supportsHTTPS
                a[i] = "https?://(?:www\\.)?" + Pattern.quote(domain) + "/(?:(?:p|d(?:/com)?)/[a-zA-Z0-9]{10}|(?:d/)?" + regexBase58() + ")";
            }
            i++;
        }
        return a;
    }

    /**
     * Returns the annotations flags array
     *
     * @return
     */
    public static int[] getAnnotationFlags() {
        final int gl = getAnnotationNames().length;
        int[] a = new int[gl];
        for (int i = 0; i < gl; i++) {
            a[i] = 0;
        }
        return a;
    }

    public SflnkgNt(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = super.decryptIt(param, progress);
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return true;
    }

    @Override
    protected boolean supportsHTTPS() {
        if ("safelinking.net".equalsIgnoreCase(Browser.getHost(parameter))) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean enforcesHTTPS() {
        return false;
    }

    @Override
    protected boolean useRUA() {
        return true;
    }

    @Override
    protected String regexLinkD() {
        return "https?://[^/]*" + regexSupportedDomains() + "/d(?:/com)?/[a-z0-9]+";
    }

    @Override
    protected String getShortHost() {
        return "sflk.in";
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.SafeLinking_SafeLinking;
    }

}