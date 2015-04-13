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

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "twitter.com" }, urls = { "https?://(www\\.)?twitter\\.com/[A-Za-z0-9_\\-]+/(media|status/\\d+/photo/\\d+)" }, flags = { 0 })
public class TwitterCom extends PluginForDecrypt {

    public TwitterCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String TYPE_USER_ALL  = "https?://(www\\.)?twitter\\.com/[A-Za-z0-9_\\-]+/media";
    private static final String TYPE_USER_POST = "https?://(www\\.)?twitter\\.com/status/\\d+/photo/\\d+";

    @SuppressWarnings("deprecation")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString().replace("http://", "https://");
        final String urlfilename = getUrlFname(parameter);
        br.setFollowRedirects(true);
        /* Some profiles can only be accessed if they accepted others as followers --> Log in if the user has added his twitter account */
        if (getUserLogin(false)) {
            logger.info("Account available and we're logged in");
        } else {
            logger.info("No account available or login failed");
        }
        br.getPage(parameter);
        if (br.getRequest().getHttpConnection().getResponseCode() == 404) {
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setFinalFileName(urlfilename);
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        } else if (br.containsHTML("class=\"ProtectedTimeline\"")) {
            logger.info("This tweet timeline is protected");
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setFinalFileName("This tweet timeline is protected_" + urlfilename);
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        if (parameter.matches(TYPE_USER_ALL)) {
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            final String user = new Regex(parameter, "twitter\\.com/([A-Za-z0-9_\\-]+)/").getMatch(0);
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(user);
            int reloadNumber = 1;
            String maxid = br.getRegex("data\\-max\\-id=\"(\\d+)\"").getMatch(0);
            do {
                try {
                    if (this.isAbort()) {
                        logger.info("Decryption aborted at reload " + reloadNumber);
                        return decryptedLinks;
                    }
                } catch (final Throwable e) {
                    // Not available in 0.9.581
                }
                logger.info("Decrypting reloadnumber " + reloadNumber + ", found " + decryptedLinks.size() + " links till now");
                if (reloadNumber > 1) {
                    maxid = br.getRegex("\"max_id\":\"(\\d+)").getMatch(0);
                }
                if (maxid == null) {
                    logger.info("Either there is nothing to decrypt or the decrypter is broken: " + parameter);
                    return decryptedLinks;
                }
                int addedlinks_all = 0;
                final String[] embedurl_regexes = new String[] { "\"(https?://(www\\.)?(youtu\\.be/|youtube\\.com/embed/)[A-Za-z0-9\\-_]+)\"", "data\\-expanded\\-url=\"(https?://vine\\.co/v/[A-Za-z0-9]+)\"" };
                for (final String regex : embedurl_regexes) {
                    final String[] embed_links = br.getRegex(regex).getColumn(0);
                    if (embed_links != null && embed_links.length != 0) {
                        for (final String single_embed_ink : embed_links) {
                            final DownloadLink dl = createDownloadlink(single_embed_ink);
                            fp.add(dl);
                            try {
                                distribute(dl);
                            } catch (final Throwable e) {
                                // Not available in 0.9.581
                            }
                            decryptedLinks.add(dl);
                            addedlinks_all++;
                        }
                    }
                }

                final String[] directlink_regexes = new String[] { "data\\-url=\\&quot;(https?://[a-z0-9]+\\.twimg\\.com/[^<>\"]*?\\.(jpg|png|gif):large)\\&", "data\\-url=\"(https?://[a-z0-9]+\\.twimg\\.com/[^<>\"]*?)\"" };
                for (final String regex : directlink_regexes) {
                    final String[] piclinks = br.getRegex(regex).getColumn(0);
                    if (piclinks != null && piclinks.length != 0) {
                        for (String singleLink : piclinks) {
                            final String remove = new Regex(singleLink, "(:[a-z0-9]+)").getMatch(0);
                            if (remove != null) {
                                singleLink = singleLink.replace(remove, "");
                            }
                            final DownloadLink dl = createDownloadlink(Encoding.htmlDecode(singleLink.trim()));
                            fp.add(dl);
                            dl.setAvailable(true);
                            try {
                                distribute(dl);
                            } catch (final Throwable e) {
                                // Not available in 0.9.581
                            }
                            decryptedLinks.add(dl);
                            addedlinks_all++;
                        }
                    }
                }
                if (addedlinks_all == 0) {
                    break;
                }
                br.getPage("https://twitter.com/i/profiles/show/" + user + "/media_timeline?include_available_features=1&include_entities=1&max_id=" + maxid);
                br.getRequest().setHtmlCode(br.toString().replace("\\", ""));
                reloadNumber++;
            } while (br.containsHTML("\"has_more_items\":true"));
            fp.addLinks(decryptedLinks);
        } else {
            final String status_id = new Regex(parameter, "/status/(\\d+)/").getMatch(0);
            final String[] alllinks = br.getRegex("property=\"og:image\" content=\"(https?://[^<>\"]+/media/[A-Za-z0-9\\-_]+\\.(?:jpg|png|gif):large)\"").getColumn(0);
            for (final String alink : alllinks) {
                final Regex fin_al = new Regex(alink, "https?://[^<>\"]+/media/([A-Za-z0-9\\-_]+)\\.(jpg|png|gif):large");
                final String servername = fin_al.getMatch(0);
                final String ending = fin_al.getMatch(1);
                final String final_filename = status_id + "_" + servername + "." + ending;
                final DownloadLink dl = createDownloadlink(Encoding.htmlDecode(alink.trim()));
                dl.setAvailable(true);
                dl.setProperty("decryptedfilename", final_filename);
                dl.setName(final_filename);
                decryptedLinks.add(dl);
            }
        }
        if (decryptedLinks.size() == 0) {
            return null;
        }
        return decryptedLinks;
    }

    private String getUrlFname(final String parameter) {
        String urlfilename;
        if (parameter.matches(TYPE_USER_ALL)) {
            urlfilename = new Regex(parameter, "twitter\\.com/([A-Za-z0-9_\\-]+)/media").getMatch(0);
        } else {
            urlfilename = new Regex(parameter, "twitter\\.com/status/\\d+/photo/(\\d+)").getMatch(0);
        }
        return urlfilename;
    }

    /** Log in the account of the hostplugin */
    @SuppressWarnings({ "deprecation", "static-access" })
    private boolean getUserLogin(final boolean force) throws Exception {
        final PluginForHost hostPlugin = JDUtilities.getPluginForHost("twitter.com");
        final Account aa = AccountController.getInstance().getValidAccount(hostPlugin);
        if (aa == null) {
            return false;
        }
        try {
            ((jd.plugins.hoster.TwitterCom) hostPlugin).login(this.br, aa, force);
        } catch (final PluginException e) {
            aa.setValid(false);
            return false;
        }
        return true;
    }

}
