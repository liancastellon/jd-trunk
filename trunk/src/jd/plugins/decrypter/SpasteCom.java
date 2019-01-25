//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.HTMLParser;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.appwork.utils.StringUtils;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperCrawlerPluginRecaptchaV2;
import org.jdownloader.controlling.PasswordUtils;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

/**
 * NOTE: <br />
 * - UID case sensitive.<br />
 * - primary captcha is their own. I looked at this couple days earlier, and swear I got solvemedia. So I have placed that code as failover.
 *
 * @version raz_Template-pastebin-201508200000
 * @author raztoki
 */
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "spaste.com" }, urls = { "https?://(?:www\\.)?spaste\\.com/(?:(?:site/checkPasteUrl|p/?)\\?c=[a-zA-Z0-9]{10}|s/[a-zA-Z0-9]{6}|r/[a-zA-Z0-9]{6}\\?link=.+)" })
public class SpasteCom extends antiDDoSForDecrypt {
    public SpasteCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        br = new Browser();
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        final String browserReferrer = getBrowserReferrer();
        if (browserReferrer != null) {
            br.setCurrentURL(browserReferrer);
        }
        getPage(parameter);
        /* Error handling */
        if (br.getHttpConnection() == null || br.getHttpConnection().getResponseCode() == 404 || br._getURL().getPath().equals("/site/index") || br.containsHTML("Page Not Found|<h4>\\s*Oops\\s*!\\s*</h4>|>\\s*The requested paste has been deleted by")) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        int zz = -1;
        while (true) {
            zz++;
            final Form form = br.getFormbyProperty("id", "spasteCaptcha");
            if (form == null) {
                // need a way to break.
                if (zz == 0) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } else {
                    break;
                }
            }
            if (zz > 4) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            // they can have captcha, ive seen solvemedia and there own
            String captchaScript = null;
            {
                final String[] mm = form.getRegex("<script[^>]*>.*?</script>").getColumn(-1);
                if (mm != null) {
                    for (final String m : mm) {
                        if (m.contains("var myCaptcha")) {
                            captchaScript = m;
                            break;
                        }
                    }
                }
            }
            if (form.containsHTML("g-recaptcha")) {
                final String recaptchaV2Response = new CaptchaHelperCrawlerPluginRecaptchaV2(this, br).getToken();
                // form only has one input...its static
                form.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                br.submitForm(form);
            } else if (form.containsHTML("api\\.solvemedia\\.com/papi")) {
                final org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia sm = new org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia(br);
                File cf = null;
                try {
                    cf = sm.downloadCaptcha(getLocalCaptchaFile());
                } catch (final Exception e) {
                    if (org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia.FAIL_CAUSE_CKEY_MISSING.equals(e.getMessage())) {
                        throw new PluginException(LinkStatus.ERROR_FATAL, "Host side solvemedia.com captcha error - please contact the " + this.getHost() + " support");
                    }
                    throw e;
                }
                final String code = getCaptchaCode("solvemedia", cf, param);
                final String chid = sm.getChallenge(code);
                form.put("adcopy_response", "manual_challenge");
                form.put("adcopy_challenge", Encoding.urlEncode(chid));
                form.put("pasteUrlForm%5Bsubmit%5D", "submit");
                submitForm(form);
            } else if (captchaScript != null) {
                // hello!
                final String hash = getJS(captchaScript, "myCaptchaHash");
                final String[] getQuestion = getJSArray(captchaScript, "myCaptchaAns");
                final String[] getImgArray = getJSArray(captchaScript, "myCaptchaImages");
                if (hash == null || getQuestion == null || getImgArray == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                // stupid
                String result = "";
                c: for (final String q : getQuestion) {
                    int count = -1;
                    for (final String i : getImgArray) {
                        ++count;
                        if (StringUtils.containsIgnoreCase(i, q)) {
                            result += count;
                            continue c;
                        }
                        // some times they do partial word within filename and more in hint, reverse lookup should solve this
                        final String filename = extractFileNameFromURL(i);
                        final String f = new Regex(filename, "\\d*([a-zA-Z]+)\\d*\\.jpg").getMatch(0);
                        if (StringUtils.containsIgnoreCase(q, f)) {
                            result += count;
                            continue c;
                        }
                        if ("building".equalsIgnoreCase(q) && "14291865221429186522index.jpg".equals(filename)) {
                            result += count;
                            continue c;
                        }
                    }
                }
                if (result.length() != getQuestion.length) {
                    // refresh
                    getPage(parameter);
                    continue;
                }
                form.put("sPasteCaptcha", Encoding.urlEncode(hash));
                form.put("userEnterHashHere", result);
                form.put("pasteUrlForm%5Bsubmit%5D", "submit");
                submitForm(form);
            } else {
                break;
            }
        }
        final String plaintxt;
        // /s links have a different format
        if (parameter.contains("spaste.com/s/") || parameter.contains("spaste.com/r/")) {
            // we need some info
            final String id = br.getRegex("\\$\\.post\\(\"/site/getRedirectLink\",\\{id:'(\\d+)'\\}").getMatch(0);
            if (id == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            // ajax request
            br.getHeaders().put("Accept", "*/*");
            br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            br.getHeaders().put("Accept", "*/*");
            sleep(5000, param);
            postPage("/site/getRedirectLink", "id=" + Encoding.urlEncode(id));
            plaintxt = br.toString();
        } else {
            // look for content! within '/p/c=? + uid', by the way you cant just jump to it.
            plaintxt = br.getRegex("class=\"required input-block-level pasteContent\"(.*?)(?:</div>\\s*){3}").getMatch(0);
        }
        if (plaintxt == null) {
            // this isn't always an error! there might not be any links!
            logger.info("Could not find 'plaintxt' : " + parameter);
            return decryptedLinks;
        }
        final Set<String> pws = PasswordUtils.getPasswords(plaintxt);
        final String[] links = HTMLParser.getHttpLinks(plaintxt, "");
        if (links == null || links.length == 0) {
            logger.info("Found no links[] from 'plaintxt' : " + parameter);
            return decryptedLinks;
        }
        /* avoid recursion */
        for (final String link : links) {
            if (!this.canHandle(link)) {
                final DownloadLink dl = createDownloadlink(link);
                if (pws != null && pws.size() > 0) {
                    dl.setSourcePluginPasswordList(new ArrayList<String>(pws));
                }
                decryptedLinks.add(dl);
            }
        }
        return decryptedLinks;
    }

    private final String getJS(final String input, final String key) {
        final String result = new Regex(input, "var\\s+" + Pattern.quote(key) + "\\s*=\\s*('|\"|)(.*?)\\1;").getMatch(1);
        return result;
    }

    private final String[] getJSArray(final String input, final String key) {
        String[] result = null;
        final String array = new Regex(input, "var\\s+" + Pattern.quote(key) + "\\s*=\\s*\\[(.*?)\\];").getMatch(0);
        if (array != null) {
            result = new Regex(array, "('|\")(.*?)\\1,?").getColumn(1);
        }
        return result;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}