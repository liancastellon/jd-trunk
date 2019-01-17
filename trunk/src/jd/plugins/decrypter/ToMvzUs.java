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

import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperCrawlerPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

/**
 *
 * @author raztoki
 *
 */
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "twomovies.us" }, urls = { "https?://(?:www\\d*\\.)?(?:twomovies\\.(?:us|net|tv)|two-movies\\.name)/(?:watch_movie/[a-zA-z0-9_]+|watch_episode/[a-zA-Z0-9_]+/\\d+/\\d+|full_movie/\\d+/\\d+/\\d+/(?:episode/\\d+/\\d+/|movie/))" })
public class ToMvzUs extends antiDDoSForDecrypt {
    @Override
    public String[] siteSupportedNames() {
        return new String[] { "twomovies.us", "twomovies.net", "twomovies.tv", "two-movies.name" };
    }

    public ToMvzUs(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public int getMaxConcurrentProcessingInstances() {
        /* Prevent "bot warning" messages. */
        return 1;
    }

    String       domain = "www1.two-movies.name/";
    final String host   = "https?://(?:www\\d*\\.)?(?:twomovies\\.(?:us|net|tv)|two-movies\\.name)/";
    final String fm     = host + "full_movie/\\d+/\\d+/\\d+/(?:episode/\\d+/\\d+/|movie/)";
    final String wt     = host + "(?:watch_episode/[a-zA-Z0-9_]+/\\d+/\\d+|watch_movie/[a-zA-z0-9_]+)";

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        // lets force https, and correct host so that we don't have issues with cookies
        final String parameter = param.toString().replace("http://", "https://").replaceFirst("twomovies\\.(?:us|net|tv)/", "two-movies.name/");
        br.setFollowRedirects(true);
        // cookie needed for seeing links!
        final String cookie_host = Browser.getHost(parameter);
        br.setCookie(cookie_host, "links_tos", "1");
        br.setCookie(cookie_host, "js_enabled", "true");
        br.setCookie(cookie_host, "subscribe", "true");
        getPage(parameter);
        if (br.getHttpConnection() == null || !br.getHttpConnection().isOK()) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        } else if (parameter.matches(fm) && br.getURL().matches(host + "watch_[^/]+/.+")) {
            // redirect happened back to the main subgroup, this happens when the parameter doesn't end with /
            return decryptedLinks;
        } else if (br._getURL().getPath().equals("/abuse")) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        if (br.containsHTML("Enter captcha:")) {
            logger.info("Unhandled captcha at decryptIt");
            return decryptedLinks;
        }
        String cybdomain = br.getRegex("cybdomain = \"([^\"]+)\"").getMatch(0);
        if (cybdomain != null) {
            domain = cybdomain; // Get the correct domain to prevent redirect
        }
        // tv ep each mirror, movie each mirror.
        if (parameter.matches(wt)) {
            decryptWatch(decryptedLinks);
            final String fpName = new Regex(parameter, "(?:watch_episode|watch_movie)/(.+)/?").getMatch(0);
            if (fpName != null) {
                final FilePackage fp = FilePackage.getInstance();
                fp.setName(fpName.replace("_", " "));
                fp.addLinks(decryptedLinks);
            }
            return decryptedLinks;
        }
        if (parameter.matches(fm)) {
            decryptIframe(decryptedLinks);
            return decryptedLinks;
        }
        return decryptedLinks;
    }

    private void decryptIframe(ArrayList<DownloadLink> decryptedLinks) throws Exception {
        // they are always held in iframe src. page seems to only have one.
        confirm_continue();
        DownloadLink dl = null;
        if (br.containsHTML("We temporary marked this link .*? as possibly dangerous")) {
            final String finallink = br.getRegex("/user/go_away/\\?go=(https?://[^<>\"]+)\"").getMatch(0);
            if (finallink == null) {
                throw new DecrypterException("Handling for 'dangerous' urls failed");
            }
            dl = createDownloadlink(finallink);
            decryptedLinks.add(createDownloadlink(finallink));
            distribute(dl);
        } else {
            String externID = br.getRegex("go=(http[^<>\"]+)\"").getMatch(0);
            if (externID != null) {
                dl = createDownloadlink(externID);
                decryptedLinks.add(createDownloadlink(externID));
                distribute(dl);
            }
            final String[] iframes = br.getRegex("<iframe .*?</iframe>").getColumn(-1);
            if (iframes != null && iframes.length > 0) {
                for (final String iframe : iframes) {
                    String src = new Regex(iframe, "src=(\"|')(.*?)\\1").getMatch(1);
                    if (src != null) {
                        if (!src.startsWith("http:")) {
                            src = "http:" + src;
                        }
                        dl = createDownloadlink(src);
                        decryptedLinks.add(dl);
                        distribute(dl);
                        logger.info("Queueing: http:" + src);
                    }
                }
            } else {
                logger.warning("Failed to find iFrames - possible plugin failure: " + br.getURL());
            }
        }
    }

    private void decryptWatch(ArrayList<DownloadLink> decryptedLinks) throws Exception {
        // int counter = 0;
        // String captchaurl = null;
        // do {
        // captchaurl = this.br.getRegex("TODO(blabla)").getMatch(0);
        // if (captchaurl == null) {
        // break;
        // }
        // final String c = this.getCaptchaCode(captchaurl, param);
        // this.br.postPage(this.br.getURL(), "TODO");
        // counter++;
        // } while (counter <= 3);
        // if (captchaurl != null && counter >= 3) {
        // throw new DecrypterException(DecrypterException.CAPTCHA);
        // }
        final String full_movie = br.getRegex("full_movie/\\d+/\\d+/\\d+/(?:episode/\\d+/\\d+/|movie/)").getMatch(-1);
        if (full_movie != null) {
            decryptedLinks.add(createDownloadlink("https://" + domain + full_movie));
            logger.info("full_movie: " + "https://" + domain + full_movie);
            getPage("https://" + domain + full_movie); // To get another links
            confirm_continue();
        }
        /* Crawl all mirrors - wait after each item to avoid captchas */
        final String[] fms = br.getRegex("full_movie/\\d+/\\d+/\\d+/(?:episode/\\d+/\\d+/|movie/)").getColumn(-1);
        if (fms != null && fms.length > 0) {
            int counter = 1;
            logger.info("fms.length: " + fms.length);
            for (final String fm : fms) {
                logger.info("Decrypting item " + counter + " of " + fms.length);
                getPage("/" + fm);
                decryptIframe(decryptedLinks);
                this.sleep(5000, param);
                counter++;
            }
        } else {
            if (br.containsHTML("Enter captcha:")) {
                logger.info("Unhandled captcha at decryptWatch");
                return;
            }
            System.out.println("Possible error: break point me");
        }
    }

    public void confirm_continue() throws Exception {
        String toshash = br.getRegex("document\\.getElementById\\(\"toshash2\"\\)\\.value = \"([^<>\"]+)\"").getMatch(0);
        if (toshash == null) {
            /* 2017-03-16 */
            toshash = br.getRegex("var\\s*?hash\\s*?=\\s*?\\'([a-f0-9]+)\\';").getMatch(0);
        }
        if (toshash == null) {
            /* 2019-01-17 */
            toshash = br.getRegex("var\\s*?hash2\\s*?=\\s*?\\'([a-f0-9]+)\\';").getMatch(0);
        }
        Form tosform = br.getFormbyKey("confirm_continue");
        if (tosform == null) {
            tosform = br.getFormbyProperty("id", "continueToLinkForm");
        }
        if (tosform != null) {
            if (toshash != null) {
                tosform.put("hash", toshash);
            }
            br.submitForm(tosform);
            if (br.containsHTML(">Before you start watching")) {
                throw new DecrypterException("submitForm(tosform) failed ");
            }
        }
        if (br.containsHTML("Enter captcha:")) {
            logger.info("Handling captcha");
            /* 2019-01-17: reCaptcha key hardcoded */
            final String recaptchaV2Response = new CaptchaHelperCrawlerPluginRecaptchaV2(this, br, "6LdbViUTAAAAABBE0NJRfpZ05KN6oE4Ojawle0h3").getToken();
            br.postPage(br.getURL(), "your_age=18&unblock_me=Unblock+me+now%21&g-recaptcha-response=" + Encoding.urlEncode(recaptchaV2Response));
        }
        return;
    }

    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return true;
    }

    public boolean hasAutoCaptcha() {
        return false;
    }
}