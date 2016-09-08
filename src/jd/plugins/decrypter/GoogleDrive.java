//  jDownloader - Downloadmanager
//  Copyright (C) 2013  JD-Team support@jdownloader.org
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

import org.jdownloader.scripting.JavaScriptEngineFactory;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "docs.google.com" }, urls = { "https?://(?:www\\.)?docs\\.google\\.com/folder/d/[a-zA-Z0-9\\-_]+|https?://(?:www\\.)?(?:docs|drive)\\.google\\.com/folderview\\?[a-z0-9\\-_=\\&]+|https?://(?:www\\.)?drive\\.google\\.com/drive/folders/[a-z0-9\\-_=\\&]+" })
public class GoogleDrive extends PluginForDecrypt {

    /**
     * @author raztoki
     * */
    public GoogleDrive(PluginWrapper wrapper) {
        super(wrapper);
    }

    // DEV NOTES
    // https://docs.google.com/folder/d/0B4lNqBSBfg_dbEdISXAyNlBpLUk/edit?pli=1 :: folder view of dir and files, can't seem to view dir
    // unless edit present.
    // https://docs.google.com/folder/d/0B4lNqBSBfg_dOEVERmQzcU9LaWc/edit?pli=1&docId=0B4lNqBSBfg_deEpXNjJrZy1MSGM :: above sub dir of docs
    // they don't provide data constistantly.
    // - with /edit?pli=1 they provide via javascript section partly escaped
    // - with /list?rm=whitebox&hl=en_GB&forcehl=1&pref=2&pli=1"; - not used and commented out, supported except for scanLinks
    // language determined by the accept-language
    // user-agent required to use new ones otherwise blocks with javascript notice.

    private static final String FOLDER_NORMAL  = "https?://(?:www\\.)?docs\\.google\\.com/folder/d/[a-zA-Z0-9\\-_]+";
    private static final String FOLDER_OLD     = "https?://(?:www\\.)?docs\\.google\\.com/folderview\\?(pli=1\\&id=[A-Za-z0-9_]+(\\&tid=[A-Za-z0-9]+)?|id=[A-Za-z0-9_]+\\&usp=sharing)";
    private static final String FOLDER_CURRENT = "https?://(?:www\\.)?drive\\.google\\.com/drive/folders/[^/]+";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();

        String fid;
        if (parameter.matches(FOLDER_NORMAL) || parameter.matches(FOLDER_CURRENT)) {
            fid = new Regex(parameter, "([^/]+)$").getMatch(0);
        } else {
            fid = new Regex(parameter, "id=([^\\&=]+)").getMatch(0);
        }
        parameter = "https://drive.google.com/drive/folders/" + fid;

        final PluginForHost plugin = JDUtilities.getPluginForHost("docs.google.com");
        ((jd.plugins.hoster.GoogleDrive) plugin).prepBrowser(br);

        int retry = 0;
        do {
            try {
                if (parameter.matches(FOLDER_NORMAL)) {
                    br.getPage(parameter + "/edit?pli=1");
                } else {
                    br.getPage(parameter);
                }
            } catch (final Throwable e) {
                final URLConnectionAdapter con = br.getHttpConnection();
                if (con == null || con.getResponseCode() != 200 && con.getResponseCode() != 500) {
                    if (e instanceof Exception) {
                        throw (Exception) e;
                    } else {
                        throw new Exception(e);
                    }
                }
            }
            retry++;
        } while (br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 500 && retry <= 3);

        if (br.containsHTML("<p class=\"errorMessage\" style=\"padding-top: 50px\">Sorry, the file you have requested does not exist\\.</p>") || br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        if (fid == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }

        String fpName = br.getRegex("\"title\":\"([^\"]+)\",\"urlPrefix\"").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
        }

        /* 2016-08-26: TODO: Check if this works fine for big folders too */
        String json_src = this.br.getRegex("window\\[\\'_DRIVE_ivd\\'\\]\\s*?=\\s*?\\'\\[(.*?)\\';").getMatch(0);
        String[] results = null;
        if (json_src != null) {
            // json_src = JSonUtils.unescape(json_src);
            results = json_src.split("\\\\n,\\[\\\\x22");
        }
        // if (results == null || results.length == 0) {
        // br2.getPage(parameter + "/list?rm=whitebox&hl=en_GB&forcehl=1&pref=2&pli=1");
        // results = br2.getRegex("(<td class=\"list\\-entry\\-title\".+</a></td></tr>)").getColumn(0);
        // }
        if (results != null && results.length != 0) {
            /* Handle the json way. */
            /* TODO: Find out how the "pageToken" can be generated. */
            final String key = this.br.getRegex("\"([A-Za-z0-9]+)\",,1000,1,\"https?://client\\-channel\\.google\\.com/client\\-channel/client").getMatch(0);
            final String eof = this.br.getRegex("\\|eof\\|([^<>\"]*)\\\\x22").getMatch(0);
            int addedlinks;
            do {
                addedlinks = 0;
                if (decryptedLinks.size() >= 50) {
                    this.br.getPage("https://clients6.google.com/drive/v2internal/files?q=trashed%20%3D%20false%20and%20%27"
                            + fid
                            + "%27%20in%20parents&fields=kind%2CnextPageToken%2Citems(kind%2Ctitle%2CmimeType%2CcreatedDate%2CmodifiedDate%2CmodifiedByMeDate%2ClastViewedByMeDate%2CfileSize%2ClastModifyingUser(kind%2C%20displayName%2C%20picture%2C%20permissionId%2C%20emailAddress)%2ChasThumbnail%2CthumbnailVersion%2CiconLink%2Cid%2Cshared%2CsharedWithMeDate%2CuserPermission(role)%2CexplicitlyTrashed%2CquotaBytesUsed%2Cshareable%2Ccopyable%2Csubscribed%2CfolderColor%2ChasChildFolders%2CfileExtension%2CprimarySyncParentId%2CsharingUser(kind%2CdisplayName%2Cpicture%2CpermissionId%2CemailAddress)%2CflaggedForAbuse%2CfolderFeatures%2Cspaces%2CsourceAppId%2Ceditable%2Crecency%2CrecencyReason%2ChasAugmentedPermissions%2CprimaryDomainName%2CorganizationDisplayName%2Cparents(id)%2Clabels(starred%2Chidden%2Ctrashed%2Crestricted%2Cviewed)%2Cowners(permissionId%2CdisplayName%2Cpicture%2Ckind)%2Ccapabilities(canCopy%2CcanDownload%2CcanEdit%2CcanAddChildren%2CcanDelete%2CcanRemoveChildren%2CcanShare%2CcanTrash%2CcanRename%2CcanReadTeamDrive%2CcanMoveTeamDriveItem))&appDataFilter=NO_APP_DATA&spaces=DRIVE&pageToken=TODO%7Ceof%7C"
                            + eof + "&maxResults=50&openDrive=true&reason=102&syncType=0&errorRecovery=false&orderBy=folder%2Ctitle%20asc&key=" + key);
                    LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
                    final LinkedHashMap<String, Object> page_info = (LinkedHashMap<String, Object>) entries.get("");
                    final ArrayList<Object> items = (ArrayList<Object>) entries.get("items");
                    for (final Object item : items) {
                        addedlinks++;
                        entries = (LinkedHashMap<String, Object>) item;
                        final String kind = (String) entries.get("");
                        final String title = (String) entries.get("title");
                        final long fileSize = ((Long) entries.get("fileSize")).longValue();
                        final String id = (String) entries.get("id");
                        if (kind == null || title == null || id == null) {
                            /* This should never happen */
                            continue;
                        }
                        final DownloadLink dl;
                        if (kind.contains("#file")) {
                            /* Single file */
                            dl = createDownloadlink("https://drive.google.com/file/d/" + id);
                            dl.setName(title);
                            dl.setDownloadSize(fileSize);
                            dl.setAvailable(true);
                        } else {
                            /* Folder */
                            dl = createDownloadlink("https://drive.google.com/drive/folders/" + id);
                        }
                        decryptedLinks.add(dl);
                    }
                } else {
                    for (String result : results) {
                        final String id = new Regex(result, "(?:\\\\x22)?([A-Za-z0-9\\-_]{10,})\\\\x22").getMatch(0);
                        if (id == null) {
                            continue;
                        }
                        addedlinks++;
                        if (result.contains("vnd.google-apps.folder")) {
                            /* Folder */
                            decryptedLinks.add(createDownloadlink("https://drive.google.com/drive/folders/" + id));
                        } else {
                            /* Single file */
                            decryptedLinks.add(createDownloadlink("https://drive.google.com/file/d/" + id));
                        }
                    }
                }
            } while (key != null && eof != null && addedlinks >= 50);
            return decryptedLinks;
        }
        if (decryptedLinks.size() == 0) {
            /* Other handling removed 26.06.14 in Revision 24087 */
            final String content = br.getRegex("\\{folderModel: \\[(.*?\\])[\t\n\r ]+\\]").getMatch(0);
            /* Even if there are no FILES, we will get an array - empty! */
            final String all_items = br.getRegex("viewerItems: \\[(.*?)\\][\t\n\r ]+,\\};").getMatch(0);
            if (all_items == null || content == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            final String[] all_items_list = all_items.split("\\][\r\t\n ]+,\\[");
            final String[] filelinks = new Regex(content, "\"(https?://(?:docs|drive)\\.google\\.com/file/d/[^<>\"]*?)\"\\]").getColumn(0);
            if (filelinks != null && all_items_list != null && all_items_list.length == filelinks.length) {
                int counter = 0;
                for (final String item : all_items_list) {
                    String filename = new Regex(item, "\"([^<>\"]*?)\"").getMatch(0);
                    String final_link = filelinks[counter];
                    if (filename != null) {
                        filename = unescape(filename);
                        final_link = unescape(final_link);
                        final DownloadLink fina = createDownloadlink(final_link);
                        fina.setName(filename);
                        fina.setAvailable(true);
                        decryptedLinks.add(fina);
                    }
                    counter++;
                }
            }

            final String[] folderlinks = new Regex(content, "(" + FOLDER_CURRENT + ")").getColumn(0);
            if (folderlinks != null && folderlinks.length != 0) {
                for (String folderlink : folderlinks) {
                    folderlink = unescape(folderlink);
                    // return folder links back into the plugin again.
                    if (!folderlink.contains("id=" + fid + "&")) {
                        decryptedLinks.add(createDownloadlink(folderlink));
                    }
                }
            }

        }
        if (decryptedLinks.size() == 0) {
            logger.info("Found nothing to download: " + parameter);
            return decryptedLinks;
        }
        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    private void scanLinks(String result, ArrayList<DownloadLink> ret) {
        if (result != null) {
            String link = new Regex(result, "\"openURL\":\"(http.+(\\\\/|/)file(\\\\/|/)d(\\\\/|/)[^\"]+)").getMatch(0);
            String filename = new Regex(result, "\"name\":\"([^\"]+)").getMatch(0);
            if (filename == null) {
                filename = new Regex(result, ">(.*?)</a>").getMatch(0);
            }
            if (link != null && filename != null) {
                DownloadLink dl = createDownloadlink(link.replaceAll("\\\\/", "/"));
                dl.setName(filename);
                dl.setAvailable(true);
                ret.add(dl);
            }
        }
    }

    private static synchronized String unescape(final String s) {
        /* we have to make sure the youtube plugin is loaded */
        final PluginForHost plugin = JDUtilities.getPluginForHost("youtube.com");
        if (plugin == null) {
            throw new IllegalStateException("youtube plugin not found!");
        }

        return jd.nutils.encoding.Encoding.unescapeYoutube(s);
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}