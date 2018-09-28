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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.controlling.linkcrawler.CrawledLink;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountRequiredException;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

import org.appwork.utils.StringUtils;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "docs.google.com" }, urls = { "https?://(?:www\\.)?drive\\.google\\.com/open\\?id=[a-zA-Z0-9\\-_]+|https?://(?:www\\.)?docs\\.google\\.com/folder/d/[a-zA-Z0-9\\-_]+|https?://(?:www\\.)?(?:docs|drive)\\.google\\.com/folderview\\?[a-z0-9\\-_=\\&]+|https?://(?:www\\.)?drive\\.google\\.com/drive/(?:[\\w\\-]+/)*folders/[a-zA-Z0-9\\-_=\\&]+" })
public class GoogleDrive extends PluginForDecrypt {
    /**
     * @author raztoki
     */
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
    private static final String FOLDER_CURRENT = "https?://(?:www\\.)?drive\\.google\\.com/drive/(?:[\\w\\-]+/)*folders/[^/]+";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        br = new Browser();
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString().replace("http:", "https:");
        final String fid;
        if (parameter.matches(FOLDER_NORMAL) || parameter.matches(FOLDER_CURRENT)) {
            fid = new Regex(parameter, "([^/]+)$").getMatch(0);
        } else {
            fid = new Regex(parameter, "id=([^\\&=]+)").getMatch(0);
        }
        final PluginForHost plg = JDUtilities.getPluginForHost("docs.google.com");
        final Account aa = AccountController.getInstance().getValidAccount(plg);
        boolean loggedin = false;
        if (aa != null) {
            loggedin = jd.plugins.hoster.GoogleDrive.login(br, aa);
        }
        logger.info("LoggedIn:" + loggedin);
        if (parameter.contains("open?id")) {
            br.getPage(parameter);
            String redirect = br.getRedirectLocation();
            /* Check whether we have a single file or a folder */
            if (redirect != null) {
                if (new Regex(redirect, "google\\.com/(?:document|file)/d/").matches()) {
                    logger.info("Found single file");
                    decryptedLinks.add(this.createDownloadlink(br.getRedirectLocation(), false));
                    return decryptedLinks;
                }
                int retry = 0;
                do {
                    br.getPage(redirect);
                } while ((redirect = br.getRedirectLocation()) != null && retry++ <= 3);
                if (new Regex(br.getURL(), "google\\.com/(?:document|file)/d/").matches() || "article".equals(((jd.plugins.hoster.GoogleDrive) plg).getType(br))) {
                    decryptedLinks.add(createDownloadlink(br.getURL()));
                    return decryptedLinks;
                }
            }
            logger.info("Found folder");
        }
        if (fid == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        parameter = "https://drive.google.com/drive/folders/" + fid;
        final PluginForHost plugin = JDUtilities.getPluginForHost("docs.google.com");
        ((jd.plugins.hoster.GoogleDrive) plugin).prepBrowser(br);
        final CrawledLink source = getCurrentLink().getSourceLink();
        final String subfolder;
        if (source != null && source.getDownloadLink() != null && canHandle(source.getURL())) {
            final DownloadLink downloadLink = source.getDownloadLink();
            subfolder = downloadLink.getStringProperty(DownloadLink.RELATIVE_DOWNLOAD_FOLDER_PATH, null);
        } else {
            subfolder = null;
        }
        int retry = 0;
        do {
            try {
                if (parameter.matches(FOLDER_NORMAL)) {
                    br.getPage(parameter + "/edit?pli=1");
                } else {
                    br.getPage(parameter);
                }
            } catch (final Throwable e) {
                logger.log(e);
                final URLConnectionAdapter con = br.getHttpConnection();
                if (con == null || con.getResponseCode() != 200 && con.getResponseCode() != 500) {
                    if (e instanceof Exception) {
                        throw (Exception) e;
                    } else {
                        throw new Exception(e);
                    }
                }
            }
        } while (br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 500 && retry++ <= 3);
        //
        if (br.containsHTML("<p class=\"errorMessage\" style=\"padding-top: 50px\">Sorry, the file you have requested does not exist\\.</p>") || br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        // login required!
        if (br.getURL().contains("//accounts.google.com/ServiceLogin?")) {
            // we are logged in but the account doesn't have permission
            if (loggedin) {
                throw new AccountRequiredException("This account doesn't have the required permission");
            } else {
                throw new AccountRequiredException("You need an account to access URL");
            }
        }
        String fpName = br.getRegex("\"title\":\"([^\"]+)\",\"urlPrefix\"").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("<title>([^<>\"]*?) - Google Drive</title>").getMatch(0);
            if (fpName == null) {
                fpName = br.getRegex("<title>([^<>\"]*?) – Google Drive</title>").getMatch(0);
                if (fpName == null) {
                    fpName = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
                }
            }
        }
        String[] results = null;
        // old type
        String json_src = br.getRegex("window\\['_DRIVE_ivd'\\]\\s*=\\s*'\\[(.*?)';").getMatch(0);
        if (json_src != null) {
            // json_src = JSonUtils.unescape(json_src);
            results = json_src.split("\\\\n,\\[\\\\x22");
        }
        // new type 20170709-raz
        if (json_src == null) {
            json_src = br.getRegex("window\\['_DRIVE_ivd'\\]\\s*=\\s*'(.*?)';").getMatch(0);
            // hex encoded
            if (json_src != null) {
                json_src = Encoding.unicodeDecode(json_src);
                results = json_src.split("\\][\r\n]+,\\[");
            }
        }
        if (results != null && results.length != 0) {
            /* Handle the json way. */
            /* TODO: Find out how the "pageToken" can be generated. */
            final String key;
            final String key_json = br.getRegex("\\]([^\\[\\]]+),\"https://client\\-channel\\.google\\.com/client\\-channel/client\"").getMatch(0);
            if (key_json != null) {
                /* 2017-05-10: New - but this is still a bad solution! */
                final String[] keys = new Regex(key_json, "\"([A-Za-z0-9\\-_]{10,})\"").getColumn(0);
                key = keys[0];
                logger.info("Keys:" + Arrays.toString(keys));
                logger.info("Key:" + key);
            } else {
                /* Old fallback */
                key = br.getRegex("\"([A-Za-z0-9\\-_]+)\",{1,}1000,1,\"https?://client\\-channel\\.google\\.com/client\\-channel/client").getMatch(0);
                logger.info("KeyOld:" + key);
            }
            if (StringUtils.isEmpty(key)) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            // final String eof = br.getRegex("\\|eof\\|([^<>\"]*)\\\\x22").getMatch(0);
            String nextPageToken = null;
            boolean firstRequest = true;
            int addedlinks;
            do {
                addedlinks = 0;
                if (decryptedLinks.size() >= 50 || firstRequest) {
                    final Browser brc = br.cloneBrowser();
                    brc.addAllowedResponseCodes(400);
                    try {
                        sleep(500, param);
                        if (firstRequest) {
                            /* 2017-05-10: Updated these requests / URLs! */
                            /* Required to get the first "nextPageToken". */
                            brc.getPage("https://clients6.google.com/drive/v2beta/files?openDrive=true&reason=102&syncType=0&errorRecovery=false&q=trashed%20%3D%20false%20and%20'"
                                    + fid
                                    + "'%20in%20parents&fields=kind%2CnextPageToken%2Citems(kind%2Ctitle%2CmimeType%2CcreatedDate%2CmodifiedDate%2CmodifiedByMeDate%2ClastViewedByMeDate%2CfileSize%2ClastModifyingUser(kind%2C%20displayName%2C%20picture%2C%20permissionId%2C%20emailAddress)%2ChasThumbnail%2CthumbnailVersion%2CiconLink%2Cid%2Cshared%2CsharedWithMeDate%2CuserPermission(role)%2CexplicitlyTrashed%2CquotaBytesUsed%2Cshareable%2Ccopyable%2CfileExtension%2CsharingUser(kind%2CdisplayName%2Cpicture%2CpermissionId%2CemailAddress)%2Cspaces%2Ceditable%2Cversion%2CteamDriveId%2ChasAugmentedPermissions%2CtrashingUser(kind%2CdisplayName%2Cpicture%2CpermissionId%2CemailAddress)%2CtrashedDate%2Cparents(id)%2Clabels(starred%2Chidden%2Ctrashed%2Crestricted%2Cviewed)%2Cowners(permissionId%2CdisplayName%2Cpicture%2Ckind)%2Ccapabilities(canCopy%2CcanDownload%2CcanEdit%2CcanAddChildren%2CcanDelete%2CcanRemoveChildren%2CcanShare%2CcanTrash%2CcanRename%2CcanReadTeamDrive%2CcanMoveTeamDriveItem))%2CincompleteSearch&appDataFilter=NO_APP_DATA&spaces=DRIVE&maxResults=50&orderBy=folder%2Ctitle%20asc&key="
                                    + key);
                            firstRequest = false;
                        } else {
                            brc.getPage("https://clients6.google.com/drive/v2beta/files?openDrive=true&reason=102&syncType=0&errorRecovery=false&q=trashed%20%3D%20false%20and%20'"
                                    + fid
                                    + "'%20in%20parents&fields=kind%2CnextPageToken%2Citems(kind%2Ctitle%2CmimeType%2CcreatedDate%2CmodifiedDate%2CmodifiedByMeDate%2ClastViewedByMeDate%2CfileSize%2ClastModifyingUser(kind%2C%20displayName%2C%20picture%2C%20permissionId%2C%20emailAddress)%2ChasThumbnail%2CthumbnailVersion%2CiconLink%2Cid%2Cshared%2CsharedWithMeDate%2CuserPermission(role)%2CexplicitlyTrashed%2CquotaBytesUsed%2Cshareable%2Ccopyable%2CfileExtension%2CsharingUser(kind%2CdisplayName%2Cpicture%2CpermissionId%2CemailAddress)%2Cspaces%2Ceditable%2Cversion%2CteamDriveId%2ChasAugmentedPermissions%2CtrashingUser(kind%2CdisplayName%2Cpicture%2CpermissionId%2CemailAddress)%2CtrashedDate%2Cparents(id)%2Clabels(starred%2Chidden%2Ctrashed%2Crestricted%2Cviewed)%2Cowners(permissionId%2CdisplayName%2Cpicture%2Ckind)%2Ccapabilities(canCopy%2CcanDownload%2CcanEdit%2CcanAddChildren%2CcanDelete%2CcanRemoveChildren%2CcanShare%2CcanTrash%2CcanRename%2CcanReadTeamDrive%2CcanMoveTeamDriveItem))%2CincompleteSearch&appDataFilter=NO_APP_DATA&spaces=DRIVE&maxResults=50&orderBy=folder%2Ctitle%20asc&key="
                                    + key + "&pageToken=" + nextPageToken);
                        }
                    } catch (final IOException e) {
                        logger.log(e);
                        break;
                    }
                    LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(brc.toString());
                    final ArrayList<Object> items = (ArrayList<Object>) entries.get("items");
                    if (items == null) {
                        logger.info("break1");
                        break;
                    }
                    nextPageToken = (String) entries.get("nextPageToken");
                    for (final Object item : items) {
                        addedlinks++;
                        entries = (LinkedHashMap<String, Object>) item;
                        // kind within entries, returns false positives 20170709-raz
                        final String kind = entries.get("mimeType") != null && ((String) entries.get("mimeType")).contains(".folder") ? "folder" : (String) entries.get("kind");
                        String title = (String) entries.get("title");
                        if (title != null) {
                            title = title.replace("의 사본", "");
                        }
                        final long fileSize = JavaScriptEngineFactory.toLong(entries.get("fileSize"), 0);
                        final String id = (String) entries.get("id");
                        if (kind == null || title == null || id == null) {
                            /* This should never happen */
                            continue;
                        }
                        final DownloadLink dl;
                        String folder_path = null;
                        if (kind.contains("#file")) {
                            /* Single file */
                            if (subfolder != null) {
                                folder_path = subfolder;
                            }
                            /* Single file */
                            dl = createDownloadlink("https://drive.google.com/file/d/" + id);
                            dl.setName(title);
                            dl.setDownloadSize(fileSize);
                            dl.setAvailable(true);
                        } else {
                            /* Folder */
                            if (subfolder != null) {
                                folder_path = subfolder + "/" + title;
                            } else {
                                folder_path = "/" + title;
                            }
                            dl = createDownloadlink("https://drive.google.com/drive/folders/" + id);
                        }
                        if (folder_path != null) {
                            dl.setProperty(DownloadLink.RELATIVE_DOWNLOAD_FOLDER_PATH, folder_path);
                        }
                        decryptedLinks.add(dl);
                    }
                    if (StringUtils.isEmpty(nextPageToken)) {
                        logger.info("break2");
                        /* Either we found everything or plugin failure ... */
                        break;
                    }
                    logger.info("added:" + addedlinks);
                }
            } while (key != null && addedlinks >= 50 && !isAbort());
            if (fpName != null) {
                FilePackage fp = FilePackage.getInstance();
                fp.setName(Encoding.htmlDecode(fpName.trim()));
                fp.addLinks(decryptedLinks);
            }
            return decryptedLinks;
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

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}