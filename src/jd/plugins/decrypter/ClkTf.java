//jDownloader - Downloadmanager
//Copyright (C) 2015  JD-Team support@jdownloader.org
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.SiteType.SiteTemplate;

/**
 *
 * I've created jac for this under the default names entry 'click.tf'.
 *
 * @author raztoki
 *
 */
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class ClkTf extends PluginForDecrypt {
    // add new domains here.
    private static final String[] domains = { "click.tf", "ssh.tf", "yep.pm", "adlink.wf", "kyc.pm", "lan.wf", "led.wf" };

    // all other domains mentioned within /services.html do not match expected.
    public ClkTf(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        br = new Browser();
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        // some links are delivered by redirects!!
        br.setFollowRedirects(false);
        br.getPage(parameter);
        if (br.getHttpConnection() == null || br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("Invalid Link\\.")) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        String finallink = br.getRedirectLocation();
        if (finallink != null) {
            decryptedLinks.add(createDownloadlink(finallink));
            return decryptedLinks;
        }
        // there could be captcha
        String fpName = new Regex(parameter, this.getSupportedLinks()).getMatch(0);
        if (fpName != null) {
            // remove /
            fpName = fpName.substring(1);
        }
        handleCaptcha(param);
        addLinks(decryptedLinks, parameter);
        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    private void addLinks(final ArrayList<DownloadLink> decryptedLinks, final String parameter) throws PluginException {
        // weird they show it in another form final action!
        final Form f = br.getForm(0);
        if (f == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final String link = f.getAction();
        if (link == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else if (!StringUtils.startsWithCaseInsensitive(link, "http://") && !StringUtils.startsWithCaseInsensitive(link, "https://")) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else {
            decryptedLinks.add(createDownloadlink(link));
        }
    }

    private void handleCaptcha(final CryptedLink param) throws Exception {
        final int retry = 4;
        Form captcha = br.getForm(0);
        for (int i = 1; i < retry; i++) {
            if (captcha == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final String captchaImage = captcha.getRegex("(/captcha\\.php\\?.*?)\"").getMatch(0);
            if (captchaImage != null) {
                final String c = getCaptchaCode(captchaImage, param);
                if (c == null) {
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                }
                if (captcha.containsHTML("verifycode")) {
                    captcha.put("verifycode", Encoding.urlEncode(c));
                } else {
                    captcha.put("ent_code", Encoding.urlEncode(c));
                }
            }
            br.submitForm(captcha);
            if (br.getRegex("(/captcha\\.php\\?.*?)\"").getMatch(0) != null) {
                if (i + 1 > retry) {
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                } else {
                    captcha = br.getForm(0);
                    continue;
                }
            }
            break;
        }
    }

    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return true;
    }

    public boolean hasAutoCaptcha() {
        return true;
    }

    private static AtomicReference<String> HOSTS           = new AtomicReference<String>(null);
    private static AtomicLong              HOSTS_REFERENCE = new AtomicLong(-1);

    @Override
    public void init() {
        // first run -1 && revision change == sync.
        if (this.getVersion() > HOSTS_REFERENCE.get()) {
            HOSTS.set(getHostsPattern());
            HOSTS_REFERENCE.set(this.getVersion());
        }
    }

    @Override
    public String[] siteSupportedNames() {
        return domains;
    }

    /**
     * Returns the annotations names array
     *
     * @return
     */
    public static String[] getAnnotationNames() {
        // never change! its linked to the JAC auto solving method.
        return new String[] { "click.tf" };
    }

    /**
     * returns the annotation pattern array
     *
     */
    public static String[] getAnnotationUrls() {
        // construct pattern
        final String host = getHostsPattern();
        return new String[] { host + "/[a-zA-Z0-9]{8,}(/.+)?" };
    }

    private static String getHostsPattern() {
        final StringBuilder pattern = new StringBuilder();
        for (final String name : domains) {
            pattern.append((pattern.length() > 0 ? "|" : "") + Pattern.quote(name));
        }
        final String hosts = "https?://(www\\.)?" + "(?:" + pattern.toString() + ")";
        return hosts;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.URLShortnerLLP_URLShortner;
    }
}