//    jDownloader - Downloadmanager
//    Copyright (C) 2013  JD-Team support@jdownloader.org
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
import java.util.Random;

import jd.PluginWrapper;
import jd.config.Property;
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
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

import org.appwork.utils.formatter.SizeFormatter;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "4shared.com" }, urls = { "https?://(www\\.)?4shared(\\-china)?\\.com/(dir|folder|minifolder)/[A-Za-z0-9\\-_]+/[A-Za-z0-9\\-_]+" }, flags = { 0 })
public class FrShrdFldr extends PluginForDecrypt {

    public FrShrdFldr(final PluginWrapper wrapper) {
        super(wrapper);
    }

    private String                  sid            = null;
    private String                  host           = null;
    private String                  uid            = null;
    private String                  parameter      = null;
    private String                  pass           = null;
    private Browser                 br2            = new Browser();
    private ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();

    /**
     * TODO: Implement API: http://www.4shared.com/developer/ 19.12.12: Their support never responded so we don't know how to use the API...
     * */
    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        host = new Regex(param.toString(), "(https?://[^/]+)").getMatch(0);
        uid = new Regex(param.toString(), "\\.com/(dir|folder|minifolder)/(.+)").getMatch(1);
        parameter = new Regex(param.toString(), "(https?://(www\\.)?4shared(\\-china)?\\.com/)").getMatch(0);
        parameter = parameter + "folder/" + uid;
        br.setFollowRedirects(true);
        try {
            br.setCookie(this.getHost(), "4langcookie", "en");
        } catch (final Throwable e) {
        }

        // check the folder/ page for password stuff and validity of url
        br.getPage(parameter);

        if (br.containsHTML("The file link that you requested is not valid") || br.containsHTML("This folder was deleted") || br.containsHTML("This folder is no longer available because of a claim")) {
            logger.info("Link offline: " + parameter);
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setFinalFileName(new Regex(parameter, "shared(\\-china)?\\.com/(dir|folder|minifolder)/(.+)").getMatch(2));
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        if (br.containsHTML(">You need owner\\'s permission to access this folder")) {
            logger.info("Link offline (no permissions): " + parameter);
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setFinalFileName(new Regex(parameter, "shared(\\-china)?\\.com/(dir|folder|minifolder)/(.+)").getMatch(2));
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        if (br.containsHTML("class=\"emptyFolderPlaceholder\"")) {
            logger.info("Link offline (empty): " + parameter);
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setFinalFileName(new Regex(parameter, "shared(\\-china)?\\.com/(dir|folder|minifolder)/(.+)").getMatch(2));
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }

        if (br.containsHTML("enter a password to access")) {
            final Form form = br.getFormbyProperty("name", "theForm");
            if (form == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }

            pass = this.getPluginConfig().getStringProperty("lastusedpassword");

            for (int retry = 5; retry > 0; retry--) {
                if (pass == null) {
                    pass = Plugin.getUserInput(null, param);
                    if (pass == null || pass.equals("")) {
                        logger.info("User abored/entered blank password");
                        return decryptedLinks;
                    }
                }
                form.put("userPass2", pass);
                br.submitForm(form);
                if (!br.containsHTML("enter a password to access")) {
                    this.getPluginConfig().setProperty("lastusedpassword", pass);
                    this.getPluginConfig().save();
                    break;
                } else {
                    this.getPluginConfig().setProperty("lastusedpassword", Property.NULL);
                    pass = null;
                    if (retry == 1) {
                        logger.severe("Wrong Password!");
                        throw new DecrypterException(DecrypterException.PASSWORD);
                    }
                }
            }
        }

        String fpName = br.getRegex("<title>([^<>\"]*?)\\- 4shared</title>").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("<title>4shared folder \\- (.*?)[\r\n\t ]+</title>").getMatch(0);
        }
        if (fpName == null) {
            fpName = br.getRegex("<h1 id=\"folderNameText\">(.*?)[\r\n\t ]+<h1>").getMatch(0);
        }
        if (fpName == null) {
            fpName = "4Shared - Folder";
        }

        sid = br.getRegex("sId:'([a-zA-Z0-9]+)',").getMatch(0);
        if (sid == null) {
            sid = br.getRegex("<input type=\"hidden\" name=\"sId\" value=\"([a-zA-Z0-9]+)\"").getMatch(0);
        }
        if (sid == null) {
            if (sid != null) {
                parsePage("0");
                parseNextPage();
            } else {
                final ArrayList<String> pages = new ArrayList<String>();
                pages.add(parameter);
                final String folderText = br.getRegex("<div id=\"folderToolbar\" class=\"simplePagerAndUpload\">(.*?)</div>").getMatch(0);
                if (folderText != null) {
                    final String[] pageLinks = new Regex(folderText, "<a href=\"(/folder/[^<>\"]*?)\" >\\d+</a>").getColumn(0);
                    if (pageLinks != null && pageLinks.length > 0) {
                        for (String aPage : pageLinks) {
                            aPage = "http://www.4shared.com" + aPage;
                            if (!pages.contains(aPage)) {
                                pages.add(aPage);
                            }
                        }
                    }
                }

                int pagecounter = 1;
                for (final String page : pages) {
                    if (pagecounter > 1) {
                        logger.info("Decrypting page " + pagecounter + " of " + pages.size());
                        br.getPage(page);
                    }
                    final String[] linkInfo = br.getRegex("<tr align=\"center\">(.*?)</tr>").getColumn(0);
                    if (linkInfo == null || linkInfo.length == 0) {
                        logger.warning("Decrypter broken for link: " + parameter);
                        return null;
                    }
                    for (final String singleInfo : linkInfo) {
                        final String dlink = new Regex(singleInfo, "\"(http://(www\\.)?4shared(\\-china)?\\.com/[^<>\"]*?)\"").getMatch(0);
                        final String filename = new Regex(singleInfo, "data\\-element=\"72\">([^<>\"]*?)<").getMatch(0);
                        final String filesize = new Regex(singleInfo, "<div class=\"itemSizeInfo\">([^<>\"]*?)</div>").getMatch(0);
                        if (dlink == null || filename == null || filesize == null) {
                            logger.warning("Decrypter broken for link: " + parameter);
                            return null;
                        }
                        final DownloadLink fina = createDownloadlink(dlink);
                        fina.setName(Encoding.htmlDecode(filename.trim()));
                        fina.setProperty("decrypterfilename", filename);
                        fina.setDownloadSize(SizeFormatter.getSize(Encoding.htmlDecode(filesize.trim()).replace(",", "")));
                        /* Do NOT set available true because status is not known! */
                        // fina.setAvailable(true);
                        decryptedLinks.add(fina);
                    }
                    pagecounter++;
                }
            }
        }

        if (decryptedLinks.size() == 0) {
            logger.warning("Possible empty folder, or plugin out of date for link: " + parameter);
        }

        FilePackage fp = FilePackage.getInstance();
        fp.setName(Encoding.htmlDecode(fpName.trim()));
        fp.addLinks(decryptedLinks);

        return decryptedLinks;
    }

    private void parsePage(final String offset) throws Exception {
        br2 = br.cloneBrowser();
        br2.getHeaders().put("Accept", "text/html, */*; q=0.01");
        br2.getHeaders().put("X-Requested-With", "XMLHttpRequest");

        String ran = Long.toString(new Random().nextLong()).substring(1, 11);
        br2.getPage(host + "/pageDownload1/folderContent.jsp?ajax=true&sId=" + sid + "&firstFileToShow=" + offset + "&rnd=" + ran);
        String[] filter = br2.getRegex("class=\"fnameCont\">(.*?)</td>").getColumn(0);
        if (filter == null || filter.length == 0) {
            filter = br2.getRegex("class=\"simpleTumbItem\">.*?</div>[\r\n\t ]+(<a.*?)</div>[\r\n\t ]+</div>").getColumn(0);
        }

        if (filter == null || filter.length == 0) {
            logger.warning("Couldn't filter 'folderContent'");
            if (decryptedLinks.size() > 0) {
                logger.info("Possible empty page or last page");
            } else {
                logger.warning("Possible error");
            }
            return;
        }
        if (filter != null && filter.length > 0) {
            for (final String entry : filter) {
                // sync folders share same uid but have ?sID=UID at the end, but this is done by JS from the main /folder/uid page...
                String subDir = new Regex(entry, "\"(https?://(www\\.)?4shared(\\-china)?\\.com/(dir|folder)/[^\"' ]+/[^\"' ]+(\\?sID=[a-zA-z0-9]{16})?)\"").getMatch(0);
                // prevent the UID from showing up in another url format structure
                if (subDir != null) {
                    if (subDir.contains("?sID=") || !new Regex(subDir, "\\.com/(folder|dir)/([^/]+)").getMatch(1).equals(uid)) {
                        decryptedLinks.add(createDownloadlink(subDir));
                    }
                } else {
                    final String dllink = new Regex(entry, "\"(http[^\"]+4shared(\\-china)?\\.com/(?!folder/|dir/)[^\"]+\\.html)").getMatch(0);
                    if (dllink == null) {
                        // logger.warning("Couldn't find dllink!");
                        continue;
                    }
                    final DownloadLink dlink = createDownloadlink(dllink);
                    if (pass != null && pass.length() != 0) {
                        dlink.setProperty("pass", pass);
                    }
                    String fileName = new Regex(entry, "title=\"(.*?)\"").getMatch(0);
                    if (fileName != null) {
                        dlink.setName(Encoding.htmlDecode(fileName));
                    }
                    dlink.setAvailable(true);
                    decryptedLinks.add(dlink);
                }
            }
        }
    }

    private boolean parseNextPage() throws Exception {
        String offset = br2.getRegex("id=\"pagerItemNext\" onclick=\"FolderActions.goToPage\\((\\d+)\\)").getMatch(0);
        if (offset != null) {
            parsePage(offset);
            parseNextPage();
            return true;
        }
        return false;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}