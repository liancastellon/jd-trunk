//    jDownloader - Downloadmanager
//    Copyright (C) 2012  JD-Team support@jdownloader.org
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
import java.util.HashSet;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "box.com" }, urls = { "https?://(?:\\w+\\.)*box\\.(?:net|com)/s(?:hared)?/(?:[a-z0-9]{32}|[a-z0-9]{20})(?:/folder/\\d+)?" })
public class BoxCom extends antiDDoSForDecrypt {
    private static final String TYPE_APP    = "https?://(?:\\w+\\.)*box\\.(?:net|com)/s(?:hared)?/(?:[a-z0-9]{32}|[a-z0-9]{20})(?:/folder/\\d+)?";
    private String              cryptedlink = null;

    public BoxCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink parameter, final ProgressController progress) throws Exception {
        br = new Browser();
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        cryptedlink = parameter.toString().replace("box.net/", "box.com/");
        logger.finer("Decrypting: " + cryptedlink);
        br.setFollowRedirects(true);
        // our default is german, this returns german!!
        br.getHeaders().put("Accept-Language", "en-gb, en;q=0.8");
        if (cryptedlink.matches(".+/folder/\\d+")) {
            final String rootFolder = new Regex(cryptedlink, "(.+)/folder/\\d+").getMatch(0);
            br.getPage(rootFolder);
        }
        br.getPage(cryptedlink);
        if (br._getURL().getPath().equals("/freeshare")) {
            decryptedLinks.add(createOfflinelink(cryptedlink));
            return decryptedLinks;
        }
        if (jd.plugins.hoster.BoxCom.isOffline(br)) {
            decryptedLinks.add(createOfflinelink(cryptedlink));
            return decryptedLinks;
        }
        if (br.getURL().matches(TYPE_APP)) {
            decryptedLinks.addAll(decryptApp(cryptedlink));
            // single link share url!
            if (decryptedLinks.isEmpty()) {
                // test links for password/empty folder/login required https://svn.jdownloader.org/issues/83897
                if (br.containsHTML("<strong>There are no items in this folder.</strong>")) {
                    // could be a empty folder.
                    decryptedLinks.add(createOfflinelink(cryptedlink));
                    return decryptedLinks;
                } else if (br.containsHTML(">\\s*Enter Password</h1>")) {
                    // password protected
                    decryptedLinks.add(createOfflinelink(cryptedlink));
                    return decryptedLinks;
                }
                // single link should still have fuid
                final String fuid = br.getRegex("typedID\"\\s*:\\s*\"f_(\\d+)\"").getMatch(0);
                final String filename = br.getRegex("\"name\"\\s*:\\s*\"([^<>\"]*?)\"").getMatch(0);
                final String itemSize = br.getRegex("\"itemSize\"\\s*:\\s*(\\d+)").getMatch(0);
                if (fuid == null) {
                    if (br.containsHTML("/login\\?redirect_url=" + Pattern.quote(br._getURL().getPath()))) {
                        // login required
                        decryptedLinks.add(createOfflinelink(cryptedlink, filename, "Login Required, unsupported feature"));
                        return decryptedLinks;
                    }
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                final String url = br.getURL() + "/file/" + fuid;
                final DownloadLink dl = createDownloadlink(url);
                // otherwise will enter decrypter again..
                dl.setAvailable(true);
                dl.setName(Encoding.htmlOnlyDecode(filename));
                if (itemSize != null) {
                    dl.setDownloadSize(SizeFormatter.getSize(itemSize));
                }
                dl.setLinkID("box.com://file/" + fuid);
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
        }
        // define all types within own methods......
        return decryptedLinks;
    }

    private ArrayList<DownloadLink> decryptApp(final String cryptedlink) throws Exception {
        // 20170711
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final HashSet<String> dupe = new HashSet<String>();
        final String fpName = br.getRegex("\"currentFolderName\":\"([^\"]*?)\"").getMatch(0);
        final FilePackage fp = fpName != null ? FilePackage.getInstance() : null;
        if (fp != null) {
            fp.setName(Encoding.unicodeDecode(fpName));
        }
        do {
            // final String[] results = br.getRegex("<li class=\"tbl-list-item.*?</div>\\s*</li>").getColumn(-1);
            final String[] results = br.getRegex("(\\{\"typedID\".*?\\]\\})").getColumn(0);
            if (results != null && results.length > 0) {
                logger.info("Links found: " + results.length);
                for (final String result : results) {
                    final String type = new Regex(result, "\"type\":\"([^\"]*?)\"").getMatch(0);
                    if ("file".equals(type)) {
                        final String size = new Regex(result, "\"itemSize\":(\\d+),").getMatch(0);
                        final String filename = new Regex(result, "\"name\":\"([^\"]*?)\"").getMatch(0);
                        final String fuid = new Regex(result, "\"typedID\":\"f_(\\d+)\"").getMatch(0);
                        final String link = new Regex(cryptedlink, "(https?://[^/]*?box.com/s/[a-z0-9]+)").getMatch(0) + "/file/" + fuid;
                        // logger.info("cryptedlink: " + cryptedlink);
                        // logger.info("link: " + link);
                        if (!dupe.add(link)) {
                            continue;
                        }
                        final DownloadLink dl = createDownloadlink(link);
                        dl.setLinkID("box.com://file/" + fuid);
                        dl.setName(Encoding.unicodeDecode(filename));
                        dl.setVerifiedFileSize(Long.parseLong(size));
                        dl.setAvailable(true);
                        decryptedLinks.add(dl);
                        if (fp != null) {
                            fp.add(dl);
                        }
                        distribute(dl);
                    } else {
                        // directory
                        final String duid = new Regex(result, "\"typedID\":\"d_(\\d+)\"").getMatch(0);
                        final String link = cryptedlink + "/folder/" + duid;
                        if (!dupe.add(link)) {
                            continue;
                        }
                        final DownloadLink dl = createDownloadlink(link);
                        dl.setLinkID("box.com://folder/" + duid);
                        decryptedLinks.add(dl);
                        if (fp != null) {
                            fp.add(dl);
                        }
                        distribute(dl);
                    }
                }
            }
        } while (hasNextPage());
        return decryptedLinks;
    }

    private boolean hasNextPage() throws Exception {
        final String pageCountString = br.getRegex("\"pageCount\":(\\d+),").getMatch(0);
        final String pageNumerString = br.getRegex("\"pageNumber\":(\\d+),").getMatch(0);
        if (pageCountString != null && pageNumerString != null) {
            final int pageCount = Integer.parseInt(pageCountString);
            final int pageNumber = Integer.parseInt(pageNumerString);
            if (pageCount > pageNumber) {
                final int nextPage = pageNumber + 1;
                br.getPage(cryptedlink + "?page=" + nextPage);
                return true;
            }
            final String r = "<a href=\"([^\"]+pageNumber=\\d+)\"[^>]+aria-label=\"Next Page\"[^>]+";
            final String result = br.getRegex(r).getMatch(-1);
            final boolean nextPage = result != null ? !new Regex(result, "btn page-forward is-disabled").matches() : false;
            if (nextPage) {
                final String url = new Regex(result, r).getMatch(0);
                if (url != null) {
                    br.getPage(Encoding.htmlOnlyDecode(url));
                    return true;
                }
            }
        }
        return false;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}