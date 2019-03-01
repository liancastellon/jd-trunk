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
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Pattern;

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
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;
import jd.utils.JDUtilities;

import org.appwork.utils.formatter.SizeFormatter;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "chomikuj.pl" }, urls = { "https?://((www\\.)?chomikuj\\.pl//?[^<>\"]+|chomikujpagedecrypt\\.pl/result/.+)" })
public class ChoMikujPl extends PluginForDecrypt {
    public ChoMikujPl(PluginWrapper wrapper) {
        super(wrapper);
    }

    private final String       PASSWORDTEXT             = "Ten folder jest (<b>)?zabezpieczony oddzielnym hasłem";
    private String             FOLDERPASSWORD           = null;
    private ArrayList<Integer> REGEXSORT                = new ArrayList<Integer>();
    private String             ERROR                    = "Decrypter broken for link: ";
    private String             REQUESTVERIFICATIONTOKEN = null;
    private final String       PAGEDECRYPTLINK          = "https?://chomikujpagedecrypt\\.pl/.+";
    private final String       ENDINGS                  = "\\.(3gp|7zip|7z|abr|ac3|aiff|aifc|aif|ai|au|avi|bin|bat|bz2|cbr|cbz|ccf|chm|cso|cue|cvd|dta|deb|divx|djvu|dlc|dmg|doc|docx|dot|eps|epub|exe|ff|flv|flac|f4v|gsd|gif|gz|iwd|idx|iso|ipa|ipsw|java|jar|jpg|jpeg|load|m2ts|mws|mv|m4v|m4a|mkv|mp2|mp3|mp4|mobi|mov|movie|mpeg|mpe|mpg|mpq|msi|msu|msp|nfo|npk|oga|ogg|ogv|otrkey|par2|pkg|png|pdf|pptx|ppt|pps|ppz|pot|psd|qt|rmvb|rm|rar|ram|ra|rev|rnd|[r-z]\\d{2}|r\\d+|rpm|run|rsdf|reg|rtf|shnf|sh(?!tml)|ssa|smi|sub|srt|snd|sfv|swf|tar\\.gz|tar\\.bz2|tar\\.xz|tar|tgz|tiff|tif|ts|txt|url|viv|vivo|vob|webm|wav|wmv|wma|wpl|xla|xls|xpi|zeno|zip)";
    private final String       UNSUPPORTED              = "https?://(www\\.)?chomikuj\\.pl//?(action/[^<>\"]+|(Media|Kontakt|PolitykaPrywatnosci|Empty|Abuse|Sugestia|LostPassword|Zmiany|Regulamin|Platforma)\\.aspx|favicon\\.ico|konkurs_literacki/info)";
    private static Object      LOCK                     = new Object();

    public int getMaxConcurrentProcessingInstances() {
        return 4;
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = null;
        br.setLoadLimit(3123000);
        if (param.toString().matches(PAGEDECRYPTLINK)) {
            String base = new Regex(param.toString(), "\\.pl/result/(.+)").getMatch(0);
            parameter = Encoding.Base64Decode(base);
        } else {
            parameter = param.toString().replace("chomikuj.pl//", "chomikuj.pl/");
            // Check for page 1 of multi page folder
            if (!parameter.contains(",")) {
                getPage(parameter);
                if (br.containsHTML("fileListPage")) {
                    FilePackage fp = FilePackage.getInstance();
                    Integer pageNum = 1;
                    while (true) {
                        if (!br.containsHTML("class=\"\" rel=\"" + (pageNum + 1) + "\" ")) {
                            break;
                        }
                        pageNum = pageNum + 1;
                        final DownloadLink dl = createDownloadlink("https://chomikujpagedecrypt.pl/result/" + Encoding.Base64Encode(parameter + "," + pageNum));
                        dl.setProperty("reallink", parameter);
                        fp.add(dl);
                        try {
                            distribute(dl);
                        } catch (final Throwable e) {
                            /* does not exist in 09581 */
                        }
                        decryptedLinks.add(dl);
                    }
                    logger.info("Number of page: " + pageNum);
                }
            }
        }
        if (parameter.matches(UNSUPPORTED)) {
            logger.info("Unsupported/invalid link: " + parameter);
            return decryptedLinks;
        }
        String linkending = null;
        if (parameter.contains(",")) {
            linkending = parameter.substring(parameter.lastIndexOf(","));
        }
        if (linkending == null) {
            linkending = parameter.substring(parameter.lastIndexOf("/") + 1);
        }
        /* Correct added link */
        parameter = parameter.replace("www.", "");
        br.setFollowRedirects(false);
        br.setLoadLimit(4194304);
        /* checking if the single link is folder with EXTENSTION in the name */
        boolean folderCheck = false;
        /* Handle single links */
        if (linkending != null) {
            String tempExt = null;
            if (linkending.contains(".")) {
                tempExt = linkending.substring(linkending.lastIndexOf("."));
                /* Be sure to get a correct ending - exclude any other html-encoded stuff. This might reset it to null but that's fine. */
                tempExt = new Regex(tempExt, "^(\\.[A-Za-z0-9]+)").getMatch(0);
            }
            final boolean isLinkendingWithoutID = (!linkending.contains(",") && tempExt != null && new Regex(tempExt, Pattern.compile(ENDINGS, Pattern.CASE_INSENSITIVE & Pattern.CANON_EQ)).matches());
            if (new Regex(linkending, Pattern.compile("\\d+\\.[A-Za-z0-9]{1,5}", Pattern.CASE_INSENSITIVE)).matches() || isLinkendingWithoutID) {
                /**
                 * If the ID is missing but it's a single link we have to access the link to get it's read link and it's download ID.
                 */
                if (isLinkendingWithoutID) {
                    getPage(parameter);
                    final String orgLink = br.getRegex("property=\"og:url\" content=\"(https?://(www\\.)?chomikuj\\.pl/[^<>\"]*?)\"").getMatch(0);
                    if (orgLink != null && orgLink.contains(",")) {
                        linkending = orgLink.substring(orgLink.lastIndexOf(","));
                        if (!linkending.matches(",\\d+\\.[A-Za-z0-9]{1,5}")) {
                            logger.warning("SingleLink handling failed for link: " + parameter);
                            return null;
                        }
                        parameter = orgLink;
                    } else {
                        // Hmm nothing to download --> Offline
                        // first check if it is folder - i.e foldername with
                        // ENDINGS ("8 Cold fusion 2011 pl brrip x264")
                        String folderIdCheck = br.getRegex("type=\"hidden\" name=\"FolderId\" value=\"(\\d+)\"").getMatch(0);
                        if (folderIdCheck == null) {
                            folderIdCheck = br.getRegex("name=\"folderId\" type=\"hidden\" value=\"(\\d+)\"").getMatch(0);
                        }
                        // if it is not folder then report offline file
                        if (folderIdCheck == null) {
                            final DownloadLink dloffline = createDownloadlink(parameter.replace("chomikuj.pl/", "chomikujdecrypted.pl/") + "," + System.currentTimeMillis() + new Random().nextInt(100000));
                            dloffline.setAvailable(false);
                            dloffline.setProperty("offline", true);
                            decryptedLinks.add(dloffline);
                            return decryptedLinks;
                        } else {
                            folderCheck = true;
                        }
                    }
                }
                // if the single link was not a folder then handle this file
                if (!folderCheck) {
                    final DownloadLink dl = createDownloadlink(parameter.replace("chomikuj.pl/", "chomikujdecrypted.pl/") + "," + System.currentTimeMillis() + new Random().nextInt(100000));
                    final Regex info = new Regex(parameter, "/([^<>\"/]*?),(\\d+)(\\.[A-Za-z0-9]+).*?$");
                    String filename = Encoding.htmlDecode(info.getMatch(0)) + info.getMatch(2);
                    if (filename.equals("nullnull")) {
                        filename = parameter.substring(parameter.lastIndexOf("/") + 1);
                    }
                    if (filename != null) {
                        filename = filename.replaceAll("(\\*([a-f0-9]{2}))", "%$2");
                        if (filename.contains("%")) {
                            filename = URLDecoder.decode(filename, "UTF-8");
                        }
                    }
                    String fileid = info.getMatch(1);
                    if (fileid == null) {
                        getPage(parameter);
                        fileid = br.getRegex("id=\"fileDetails_(\\d+)\"").getMatch(0);
                    }
                    if (fileid == null) {
                        /* No ID --> We can't download anything --> Must be offline */
                        dl.setProperty("offline", true);
                        dl.setAvailable(false);
                    } else {
                        dl.setProperty("fileid", fileid);
                    }
                    dl.setName(filename);
                    dl.setContentUrl(parameter);
                    dl.setProperty("mainlink", parameter);
                    dl.setLinkID(fileid);
                    distribute(dl);
                    decryptedLinks.add(dl);
                    return decryptedLinks;
                }
            } else {
                // Or it's just a specified page of a folder, we remove that to
                // prevent problems!
                logger.info("Usually linkreplace would be here...");
                // parameter = parameter.replace(linkending, "");
            }
        }
        getPage(parameter);
        // Check for redirect and apply new link
        String redirect = br.getRedirectLocation();
        if (redirect != null) {
            parameter = redirect;
            getPage(parameter);
        }
        final String numberof_files = br.getRegex("class=\"bold\">(\\d+)</span> plik\\&#243;w<br />").getMatch(0);
        if (br.containsHTML("Nie znaleziono \\- błąd 404") || br.getHttpConnection().getResponseCode() == 404 || !br.containsHTML("class=\"greenActionButton\"|name=\"FolderId\"") || ("0".equals(numberof_files) && !br.containsHTML("foldersList"))) {
            // Offline
            final DownloadLink dloffline = createDownloadlink(parameter.replace("chomikuj.pl/", "chomikujdecrypted.pl/") + "," + System.currentTimeMillis() + new Random().nextInt(100000));
            dloffline.setAvailable(false);
            dloffline.setProperty("offline", true);
            dloffline.setName(new Regex(parameter, "chomikuj\\.pl/(.+)").getMatch(0));
            decryptedLinks.add(dloffline);
            return decryptedLinks;
        }
        /* Handle single links 2 */
        String ext = parameter.substring(parameter.lastIndexOf("."));
        if (ext != null) {
            ext = new Regex(ext, "(" + ENDINGS + ").+$").getMatch(0);
        }
        if (ext != null && ext.matches(ENDINGS) && !folderCheck) {
            getPage(parameter);
            redirect = br.getRedirectLocation();
            if (redirect != null) {
                // Maybe direct link is no direct link anymore?!
                ext = redirect.substring(redirect.lastIndexOf("."));
                if (ext == null || ext.length() > 5 || !ext.matches(ENDINGS)) {
                    logger.info("Link offline: " + parameter);
                    return decryptedLinks;
                }
                getPage(redirect);
            }
            // Check if link can be decrypted
            final String cantDecrypt = getError();
            if (cantDecrypt != null) {
                logger.info(String.format(cantDecrypt, parameter));
                return decryptedLinks;
            }
            final String filename = br.getRegex("Download: <b>([^<>\"]*?)</b>").getMatch(0);
            final String filesize = br.getRegex("<p class=\"fileSize\">([^<>\"]*?)</p>").getMatch(0);
            final String fid = br.getRegex("id=\"fileDetails_(\\d+)\"").getMatch(0);
            if (filename == null || filesize == null || fid == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            final DownloadLink dl = createDownloadlink(parameter.replace("chomikuj.pl/", "chomikujdecrypted.pl/") + "," + System.currentTimeMillis() + new Random().nextInt(100000));
            dl.setProperty("fileid", fid);
            dl.setName(correctFilename(Encoding.htmlDecode(filename)));
            dl.setDownloadSize(SizeFormatter.getSize(Encoding.htmlDecode(filesize.trim().replace(",", "."))));
            dl.setAvailable(true);
            dl.setProperty("requestverificationtoken", REQUESTVERIFICATIONTOKEN);
            dl.setContentUrl(parameter);
            dl.setLinkID(fid);
            distribute(dl);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        // Check if link can be decrypted
        final String cantDecrypt = getError();
        if (cantDecrypt != null) {
            logger.info(String.format(cantDecrypt, parameter));
            // Offline
            final DownloadLink dloffline = createDownloadlink(parameter.replace("chomikuj.pl/", "chomikujdecrypted.pl/") + "," + System.currentTimeMillis() + new Random().nextInt(100000));
            dloffline.setAvailable(false);
            dloffline.setProperty("offline", true);
            dloffline.setName(cantDecrypt + "_" + new Regex(parameter, "chomikuj\\.pl/(.+)").getMatch(0));
            decryptedLinks.add(dloffline);
            return decryptedLinks;
        }
        // If we have a new link we have to use it or we'll have big problems
        // later when POSTing things to the server
        if (br.getRedirectLocation() != null) {
            parameter = br.getRedirectLocation();
            getPage(br.getRedirectLocation());
        }
        /* Get needed values */
        String fpName = br.getRegex(">\\s*([^<]*?)\\s*</a>\\s*</h1>\\s*<div[^<]*id\\s*=\\s*\"folderOptionsTitle\"\\s*>").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("<meta name=\"keywords\" content=\"(.+?)\" />").getMatch(0);
            if (fpName == null) {
                br.getRegex("<title>(.*?) \\- .*? \\- Chomikuj\\.pl.*?</title>").getMatch(0);
                if (fpName == null) {
                    fpName = br.getRegex("class=\"T_selected\">(.*?)</span>").getMatch(0);
                    if (fpName == null) {
                        fpName = br.getRegex("<span id=\"ctl00_CT_FW_SelectedFolderLabel\" style=\"font\\-weight:bold;\">(.*?)</span>").getMatch(0);
                    }
                }
            }
        }
        String chomikID = br.getRegex("name=\"(?:chomikId|ChomikName)\" type=\"hidden\" value=\"(.+?)\"").getMatch(0);
        if (chomikID == null) {
            chomikID = br.getRegex("id=\"__(?:accno|accname)\" name=\"__(?:accno|accname)\" type=\"hidden\" value=\"(.+?)\"").getMatch(0);
            if (chomikID == null) {
                chomikID = br.getRegex("name=\"friendId\" type=\"hidden\" value=\"(.+?)\"").getMatch(0);
                if (chomikID == null) {
                    chomikID = br.getRegex("\\&amp;(?:chomikId|ChomikName)=(.+?)\"").getMatch(0);
                }
            }
        }
        String folderID = br.getRegex("type=\"hidden\" name=\"FolderId\" value=\"(\\d+)\"").getMatch(0);
        if (folderID == null) {
            folderID = br.getRegex("name=\"FolderId\" type=\"hidden\" value=\"(\\d+)\"").getMatch(0);
        }
        REQUESTVERIFICATIONTOKEN = br.getRegex("<input name=\"__RequestVerificationToken\" type=\"hidden\" value=\"([^<>\"\\']+)\"").getMatch(0);
        if (REQUESTVERIFICATIONTOKEN == null) {
            logger.warning(ERROR + parameter);
            return null;
        }
        if (folderID == null || fpName == null) {
            logger.warning(ERROR + parameter);
            return null;
        }
        fpName = fpName.trim();
        // All Main-POSTdata
        String postdata = "ChomikName=" + chomikID + "&folderId=" + folderID + "&__RequestVerificationToken=" + Encoding.urlEncode(REQUESTVERIFICATIONTOKEN);
        final FilePackage fp = FilePackage.getInstance();
        // Make only one package
        fp.setProperty("ALLOW_MERGE", true);
        // set user-friendly package name and download directory
        final PluginForHost chomikujpl = JDUtilities.getPluginForHost("chomikuj.pl");
        final boolean decryptFolders = chomikujpl.getPluginConfig().getBooleanProperty(jd.plugins.hoster.ChoMikujPl.DECRYPTFOLDERS, false);
        if (decryptFolders) {
            String serverPath = parameter.replace("http://chomikuj.pl/", "").replace("https://chomikuj.pl/", "");
            serverPath = serverPath.replace("*", "%");
            serverPath = URLDecoder.decode(serverPath, "UTF-8");
            final Regex serverPathRe = new Regex(serverPath, "^(.+)(,\\d+)$");
            if (serverPathRe.matches()) {
                serverPath = serverPathRe.getMatch(0);
            }
            File downloadDirectory = new File(fp.getDownloadDirectory(), serverPath != null ? serverPath : "");
            String downloadDirectoryStr = downloadDirectory.getPath();
            fp.setDownloadDirectory(downloadDirectoryStr);
            if (fpName != null) {
                fp.setName(fpName);
            } else {
                String packageName = serverPath.replace("/", ",");
                fp.setName(packageName);
            }
        } else if (fpName != null) {
            fp.setName(fpName);
        }
        decryptedLinks = decryptAll(parameter, postdata, param, fp, chomikID);
        return decryptedLinks;
    }

    private String getError() {
        String error = null;
        if (br.containsHTML("label for=\"Password\">Hasło</label><input id=\"Password\"")) {
            error = "Password protected links can't be decrypted: %s";
        } else if (br.containsHTML("Konto czasowo zablokowane")) {
            error = "Can't decrypt link, the account of the owner is banned: %s";
        } else if (br.containsHTML("Chomik o takiej nazwie nie istnieje<|Nie znaleziono - błąd 404")) {
            error = "This link is offline (received error 404): %s";
        } else if (br.containsHTML("Chomik Jareczczek zablokowany")) {
            error = "Link blocked";
        } else if (br.containsHTML("<title>Przyjazny dysk internetowy \\- Chomikuj\\.pl</title>")) {
            error = "Link leads to mainpage";
        }
        return error;
    }

    @SuppressWarnings("deprecation")
    private ArrayList<DownloadLink> decryptAll(final String parameter, final String postdata, final CryptedLink param, final FilePackage fp, final String chomikID) throws Exception {
        br.setFollowRedirects(true);
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String savePost = postdata;
        String saveLink = null;
        final PluginForHost chomikujpl = JDUtilities.getPluginForHost("chomikuj.pl");
        final boolean decryptFolders = chomikujpl.getPluginConfig().getBooleanProperty(jd.plugins.hoster.ChoMikujPl.DECRYPTFOLDERS, false);
        String[][] allFolders = null;
        // Password handling
        if (br.containsHTML(PASSWORDTEXT)) {
            // prevent more than one password from processing and displaying at
            // any point in time!
            synchronized (LOCK) {
                prepareBrowser(parameter, br);
                final Form pass = br.getFormbyProperty("id", "LoginToFolder");
                if (pass == null) {
                    logger.warning(ERROR + " :: Can't find Password Form!");
                    return null;
                }
                if (chomikID == null) {
                    logger.warning("ChomikID not found on source page. The link will not work. Please fix decryptId() method.");
                    return null;
                }
                for (int i = 0; i <= 3; i++) {
                    FOLDERPASSWORD = param.getDecrypterPassword();
                    if (FOLDERPASSWORD == null) {
                        FOLDERPASSWORD = this.getPluginConfig().getStringProperty("password");
                    }
                    if (FOLDERPASSWORD == null) {
                        FOLDERPASSWORD = getUserInput(null, param);
                    }
                    // you should exit if they enter blank password!
                    if (FOLDERPASSWORD == null || FOLDERPASSWORD.length() == 0) {
                        return decryptedLinks;
                    }
                    pass.put("Password", FOLDERPASSWORD);
                    submitForm(pass);
                    if (br.containsHTML("\\{\"IsSuccess\":true")) {
                        param.setDecrypterPassword(FOLDERPASSWORD);
                        this.getPluginConfig().setProperty("password", FOLDERPASSWORD);
                        break;
                    } else {
                        // Maybe password was saved before but has changed in the meantime!
                        this.getPluginConfig().setProperty("password", Property.NULL);
                        param.setDecrypterPassword(null);
                        continue;
                    }
                }
                if (!br.containsHTML("\\{\"IsSuccess\":true")) {
                    logger.warning("Wrong password!");
                    throw new DecrypterException(DecrypterException.PASSWORD);
                }
                saveLink = parameter;
            }
        }
        // logger.info("Looking how many pages we got here for link " + parameter + " ...");
        // Herausfinden wie viele Seiten der Link hat
        int pageCount = 1;
        // More than one page? Every page goes back into the decrypter as a
        // single link!
        if (pageCount > 1 && !param.toString().matches(PAGEDECRYPTLINK)) {
            // Moved up
        } else {
            /* Decrypt all pages, start with 1 (not 0 as it was before) */
            pageCount = 1;
            final String pn = new Regex(param.toString(), "(,\\d{1,3})$").getMatch(0);
            if (pn != null) {
                pageCount = Integer.parseInt(new Regex(param.toString(), ",(\\d+)$").getMatch(0));
            }
            logger.info("Decrypting page " + pageCount + " of link: " + parameter);
            final Browser tempBr = br.cloneBrowser();
            prepareBrowser(parameter, tempBr);
            /** Only request further pages is folder isn't password protected */
            if (FOLDERPASSWORD != null) {
                getPage(tempBr, parameter);
            } else {
                accessPage(postdata, tempBr, pageCount);
            }
            final String __RequestVerificationToken_Lw__ = br.getCookie(parameter, "__RequestVerificationToken_Lw__");
            String[] v2list = tempBr.getRegex("<li class=\"fileItemContainer\"(.*?)href=\"javascript:;\"").getColumn(0);
            if (v2list == null || v2list.length == 0) {
                v2list = tempBr.getRegex("class=\"fileinfo tab\"(.*?)href=\"javascript:;\"").getColumn(0);
            }
            // if (v2list == null || v2list.length == 0) {
            // v2list = tempBr.getRegex("<div class=\"filerow fileItemContainer\">(.*?)visibility: hidden").getColumn(0);
            // }
            final String folderTable = tempBr.getRegex("<div id=\"foldersList\">[\t\n\r ]+<table>(.*?)</table>[\t\n\r ]+</div>").getMatch(0);
            if (folderTable != null) {
                allFolders = new Regex(folderTable, "<a href=\"(/[^<>\"]*?)\" rel=\"\\d+\" title=\"([^<>\"]*?)\"").getMatches();
            }
            if ((v2list == null || v2list.length == 0) && (allFolders == null || allFolders.length == 0)) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            for (final String entry : v2list) {
                final DownloadLink dl = createDownloadlink(parameter.replace("chomikuj.pl/", "chomikujdecrypted.pl/") + "," + System.currentTimeMillis() + new Random().nextInt(100000));
                String ext = null;
                String url_filename = null;
                final String fid = new Regex(entry, "rel=\"(\\d+)\"").getMatch(0);
                String content_url = new Regex(entry, "<li><a href=\"(/[^<>\"]*?)\"").getMatch(0);
                if (content_url != null) {
                    content_url = "https://" + this.getHost() + content_url;
                    url_filename = new Regex(content_url, "/([^<>\"/]+)$").getMatch(0);
                }
                String filesize = new Regex(entry, "<li><span>(\\d+(,\\d+)? [A-Za-z]{1,5})</span>").getMatch(0);
                if (filesize == null) {
                    filesize = new Regex(entry, "<li>[\t\n\r ]*?(\\d+(,\\d{1,2})? [A-Za-z]{1,5})[\t\n\r ]*?</li>").getMatch(0);
                }
                final Regex finfo = new Regex(entry, "<span class=\"bold\">(.*?)</span>(\\.[^<>\"/]*?)</a>");
                String filename = new Regex(entry, "style=\"[^<>\"]*?\" title=\"([^<>\"]*?)\"").getMatch(0);
                if (filename == null) {
                    filename = finfo.getMatch(0);
                }
                /* This is usually only for filenames without ext */
                if (filename == null) {
                    filename = new Regex(entry, "data\\-title=\"([^<>\"]*?)\"").getMatch(0);
                }
                ext = finfo.getMatch(1);
                if (content_url == null || url_filename == null || filesize == null || fid == null) {
                    logger.warning("Decrypter broken for link: " + parameter);
                    return null;
                }
                /* Use filename from content_url if necessary */
                if (filename == null && content_url != null) {
                    filename = url_filename;
                }
                if (filename != null) {
                    filename = filename.replaceAll("(\\*([a-f0-9]{2}))", "%$2");
                    if (filename.contains("%")) {
                        filename = URLDecoder.decode(filename, "UTF-8");
                    }
                }
                filename = correctFilename(Encoding.htmlDecode(filename).trim());
                filename = filename.replace("<span class=\"e\"> </span>", "");
                filename = filename.replace("," + fid, "");
                if (ext == null && url_filename.lastIndexOf(".") >= 0) {
                    /* Probably extension is already in filename --> Find & correct it */
                    final String tempExt = url_filename.substring(url_filename.lastIndexOf("."));
                    if (tempExt != null) {
                        ext = new Regex(tempExt, "(" + ENDINGS + ").*?$").getMatch(0);
                        if (ext == null) {
                            /*
                             * Last try to find the correct extension - if we fail to find it here the host plugin should find it anyways!
                             */
                            ext = new Regex(tempExt, "(\\.[A-Za-z0-9]+)").getMatch(0);
                        }
                        /* We found the good extension? Okay then let's remove the previously found bad extension! */
                        if (ext != null) {
                            filename = filename.replace(tempExt, "");
                        }
                    }
                }
                if (ext != null) {
                    ext = Encoding.htmlDecode(ext.trim());
                    if (!filename.endsWith(ext)) {
                        filename += ext;
                    }
                }
                dl.setProperty("fileid", fid);
                if (__RequestVerificationToken_Lw__ != null) {
                    dl.setProperty("__RequestVerificationToken_Lw__", __RequestVerificationToken_Lw__);
                }
                dl.setProperty("plain_filename", filename);
                dl.setName(filename);
                dl.setDownloadSize(SizeFormatter.getSize(filesize));
                dl.setAvailable(true);
                if (saveLink != null && savePost != null && FOLDERPASSWORD != null) {
                    dl.setProperty("savedlink", saveLink);
                    dl.setProperty("savedpost", savePost);
                    // password for the folder, must be sent as a cookie when requesting file
                    dl.setProperty("chomikID", chomikID);
                    dl.setProperty("password", FOLDERPASSWORD);
                }
                dl.setProperty("requestverificationtoken", REQUESTVERIFICATIONTOKEN);
                fp.add(dl);
                dl.setContentUrl(content_url);
                dl.setLinkID(fid);
                distribute(dl);
                decryptedLinks.add(dl);
            }
        }
        if (decryptFolders && allFolders != null && allFolders.length != 0) {
            String linkPart = new Regex(parameter, "chomikuj\\.pl(/.+)").getMatch(0);
            // work around Firefox copy/paste URL magic that automatically converts brackets
            // ( and ) to %28 and %29. Chomikuj.pl in page source has links with unencoded
            // brackets, so we need to fix this or links will not match and won't be added.
            linkPart = linkPart.replaceAll("%28", "(").replaceAll("%29", ")");
            for (String[] folder : allFolders) {
                String folderLink = folder[0];
                folderLink = "https://chomikuj.pl" + folderLink;
                if (folderLink.contains(linkPart) && !folderLink.equals(parameter)) {
                    final DownloadLink dl = createDownloadlink(folderLink);
                    if (FOLDERPASSWORD != null) {
                        dl.setProperty("password", FOLDERPASSWORD);
                    }
                    fp.add(dl);
                    try {
                        distribute(dl);
                    } catch (final Throwable e) {
                        /* does not exist in 09581 */
                    }
                    decryptedLinks.add(dl);
                }
            }
        }
        return decryptedLinks;
    }

    private void accessPage(String postData, Browser pageBR, int pageNum) throws Exception {
        postPage(pageBR, "https://chomikuj.pl/action/Files/FilesList", postData + "&pageNr=" + pageNum);
    }

    private void prepareBrowser(String parameter, Browser bro) {
        // Not needed but has been implemented so lets use it
        bro.getHeaders().put("Referer", parameter);
        bro.getHeaders().put("Accept", "*/*");
        bro.getHeaders().put("Accept-Language", "de-de,de;q=0.8,en-us;q=0.5,en;q=0.3");
        bro.getHeaders().put("Accept-Encoding", "gzip,deflate");
        bro.getHeaders().put("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        bro.getHeaders().put("Cache-Control", "no-cache");
        bro.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        bro.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        bro.getHeaders().put("Pragma", "no-cache");
    }

    private String correctFilename(final String filename) {
        return filename.replace("<span class=\"e\"> </span>", "");
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

    private PluginForHost plugin = null;

    private void getPage(final String parameter) throws Exception {
        getPage(br, parameter);
    }

    private void getPage(final Browser br, final String parameter) throws Exception {
        loadPlugin();
        ((jd.plugins.hoster.ChoMikujPl) plugin).setBrowser(br);
        ((jd.plugins.hoster.ChoMikujPl) plugin).getPage(parameter);
    }

    private void postPage(final String url, final String arg) throws Exception {
        postPage(br, url, arg);
    }

    private void postPage(final Browser br, final String url, final String arg) throws Exception {
        loadPlugin();
        ((jd.plugins.hoster.ChoMikujPl) plugin).setBrowser(br);
        ((jd.plugins.hoster.ChoMikujPl) plugin).postPage(url, arg);
    }

    private void submitForm(final Form form) throws Exception {
        submitForm(br, form);
    }

    private void submitForm(final Browser br, final Form form) throws Exception {
        loadPlugin();
        ((jd.plugins.hoster.ChoMikujPl) plugin).setBrowser(br);
        ((jd.plugins.hoster.ChoMikujPl) plugin).submitForm(form);
    }

    public void loadPlugin() {
        if (plugin == null) {
            plugin = JDUtilities.getPluginForHost("chomikuj.pl");
            if (plugin == null) {
                throw new IllegalStateException(getHost() + " hoster plugin not found!");
            }
        }
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.ChomikujPlScript;
    }
}