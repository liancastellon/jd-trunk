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
import java.util.LinkedHashSet;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.jdownloader.plugins.components.antiDDoSForDecrypt;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.Request;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

/**
 *
 * note: primewire.ag using cloudflare. -raztoki20150225
 *
 */
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "1channel.ch" }, urls = { "https?://(?:www\\.)?(?:vodly\\.to|primewire\\.(ag|is|life|site|fun)|primewire\\.unblocked\\.cc)/(?:watch\\-\\d+([A-Za-z0-9\\-_]+)?|tv\\-\\d+[A-Za-z0-9\\-_]+/season\\-\\d+\\-episode\\-\\d+)|http://(?:www\\.)?letmewatchthis\\.lv/movies/view/watch\\-\\d+[A-Za-z0-9\\-]+|https?://(?:www\\.)?primewire\\.(ag|is|life|site|fun)/go.php.*" })
public class OneChannelCh extends antiDDoSForDecrypt {
    public OneChannelCh(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final LinkedHashSet<String> dupe = new LinkedHashSet<String>();
        String parameter = param.toString().replace("vodly.to/", "primewire.is/").replace("primewire.ag/", "primewire.is/");
        br.setFollowRedirects(true);
        getPage(parameter);
        String page = br.toString();
        String slug = new Regex(parameter, "/([^/]+)$").getMatch(0);
        String itemID = new Regex(slug.toString(), "([0-9]+)").getMatch(0).toString();
        if (br.containsHTML("\\(TV Show\\) \\-  on 1Channel \\| LetMeWatchThis</title>")) {
            final String[] episodes = br.getRegex("class=\"tv_episode_item\"> <a href=\"(/tv[^<>\"]*?)\"").getColumn(0);
            if (episodes == null || episodes.length == 0) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            for (String singleLink : episodes) {
                decryptedLinks.add(createDownloadlink("http://www.1channel.ch" + singleLink));
            }
        } else {
            if (br.getURL().equals("http://www.primewire.is/") || br.getURL().contains("/index.php")) {
                logger.info("Link offline: " + parameter);
                return decryptedLinks;
            } else if (br.containsHTML(">No episodes listed<")) {
                logger.info("Link offline (no downloadlinks available): " + parameter);
                return decryptedLinks;
            } else if (br.containsHTML("class=\"tv_container\"")) {
                logger.info("Linktype (series overview) is not supported: " + parameter);
                return decryptedLinks;
            }
            String fpName = br.getRegex("<title>Watch ([^<>\"]*?) online.*?\\| [^<>\"]*?</title>").getMatch(0);
            if (fpName == null) {
                fpName = br.getRegex("<meta property=\"og:title\" content=\"([^<>\"]*?)\">").getMatch(0);
            }
            if (parameter.contains("season-") && fpName != null) {
                final Regex seasonAndEpisode = br.getRegex("<a href=\"/tv\\-[^<>\"/]*?/[^<>\"/]*?\">([^<>\"]*?)</a>[\t\n\r ]+</strong>[\t\n\r ]+> <strong>([^<>\"]*?)</strong>");
                if (seasonAndEpisode.getMatches().length != 0) {
                    fpName = Encoding.htmlDecode(fpName.trim());
                    fpName = fpName + " - " + Encoding.htmlDecode(seasonAndEpisode.getMatch(0)) + " - " + Encoding.htmlDecode(seasonAndEpisode.getMatch(1));
                }
            }
            if (parameter.contains("go.php") && br.toString().contains("p,a,c,k,e,d")) {
                final String js = br.getRegex("eval\\((function\\(p,a,c,k,e,d\\)[^\r\n]+\\))\\)").getMatch(0);
                final ScriptEngineManager manager = JavaScriptEngineFactory.getScriptEngineManager(null);
                final ScriptEngine engine = manager.getEngineByName("javascript");
                String result = null;
                try {
                    engine.eval("var res = " + js + ";");
                    result = (String) engine.get("res");
                    String finallink = new Regex(result, "go\\('(.*?)'\\)").getMatch(0);
                    fpName = new Regex(parameter, "title=([^<>]*?)(&|$)").getMatch(0);
                    fpName = (fpName == null) ? br.getRegex("<title>Watching ([^<>]*?)</title>").getMatch(0) : fpName.replaceAll("(-|_|\\&20)", " ");
                    decryptedLinks.add(createDownloadlink(finallink));
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            } else if (br.containsHTML("<div class=\"loader\">") || br.containsHTML("Please Wait! Loading The Links.")) {
                Browser br2 = br.cloneBrowser();
                String page2 = br2.getPage("/ajax-78583.php?slug=" + slug + "&cp=7TYP4N");
                String[] links = br2.getRegex("href=\"([^\"]*go\\.php[^\"]*)\"").getColumn(0);
                for (String singleLink : links) {
                    singleLink = Encoding.htmlDecode(singleLink);
                    if (!dupe.add(singleLink)) {
                        continue;
                    }
                    decryptedLinks.add(createDownloadlink(singleLink));
                }
                page2 = page2;
            } else {
                String[] links = br.getRegex("(/\\w+\\.php[^\"]*[&?](?:url|link)=[^\"]*?|/(?:external|goto|gohere|go)\\.php[^<>\"]*?)\"").getColumn(0);
                if (links == null || links.length == 0) {
                    if (br.containsHTML("\\'HD Sponsor\\'")) {
                        logger.info("Found no downloadlink in link: " + parameter);
                        return decryptedLinks;
                    }
                    logger.warning("Decrypter broken for link: " + parameter);
                    return null;
                }
                br.setFollowRedirects(false);
                for (String singleLink : links) {
                    singleLink = Encoding.htmlDecode(singleLink);
                    if (!dupe.add(singleLink)) {
                        continue;
                    }
                    String finallink;
                    final String b64link = new Regex(singleLink, "[&?](?:url|link)=([^<>\"&]+)").getMatch(0);
                    if (singleLink.contains("go.php")) {
                        final Browser br2 = br.cloneBrowser();
                        getPage(br2, singleLink);
                        finallink = br2.getRedirectLocation() == null ? br2.getURL() : br2.getRedirectLocation();
                    } else if (b64link != null) {
                        finallink = Encoding.Base64Decode(b64link);
                        finallink = Request.getLocation(finallink, br.getRequest());
                    } else {
                        final Browser br2 = br.cloneBrowser();
                        getPage(br2, singleLink);
                        finallink = br2.getRedirectLocation();
                        if (finallink == null) {
                            finallink = br2.getRegex("<frame src=\"(http[^<>\"]*?)\"").getMatch(0);
                        }
                    }
                    if (finallink == null) {
                        logger.warning("Decrypter broken for link: " + parameter);
                        return null;
                    }
                    if (dupe.add(finallink)) {
                        decryptedLinks.add(createDownloadlink(finallink));
                    }
                }
            }
            if (fpName != null) {
                FilePackage fp = FilePackage.getInstance();
                fp.setName(Encoding.htmlDecode(fpName.trim()));
                fp.addLinks(decryptedLinks);
            }
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}