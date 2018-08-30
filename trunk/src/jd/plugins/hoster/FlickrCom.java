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
package jd.plugins.hoster;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

import org.appwork.utils.Files;
import org.appwork.utils.StringUtils;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter.ExtensionsFilterInterface;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "flickr.com" }, urls = { "https?://(www\\.)?flickrdecrypted\\.com/photos/[^<>\"/]+/\\d+(/in/album-\\d+)?" })
public class FlickrCom extends PluginForHost {
    public FlickrCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://edit.yahoo.com/registration?.src=flickrsignup");
        setConfigElements();
    }

    @Override
    public String getAGBLink() {
        return "http://flickr.com";
    }

    /* Settings */
    private static final String CUSTOM_DATE                      = "CUSTOM_DATE";
    private static final String CUSTOM_FILENAME                  = "CUSTOM_FILENAME";
    private static final String CUSTOM_FILENAME_EMPTY_TAG_STRING = "CUSTOM_FILENAME_EMPTY_TAG_STRING";
    private static final String MAINPAGE                         = "http://flickr.com";
    private static Object       LOCK                             = new Object();
    private String              dllink                           = null;
    private String              user                             = null;
    private String              id                               = null;

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(DownloadLink link) {
        try {
            link.setUrlDownload("https://www.flickr.com/" + new Regex(getPhotoURL(link), "\\.com/(.+)").getMatch(0));
        } catch (PluginException e) {
            if (logger != null) {
                logger.log(e);
                return;
            }
        }
        if (link.getContainerUrl() == null) {
            link.setContainerUrl(link.getContentUrl());
        }
        link.setContentUrl(link.getPluginPatternMatcher());
    }

    /* Max 2000 requests per hour */
    @Override
    public void init() {
        try {
            Browser.setRequestIntervalLimitGlobal(this.getHost(), 3000, 20, 1900);
        } catch (final Throwable t) {
            Browser.setRequestIntervalLimitGlobal(this.getHost(), 1800);
        }
    }

    private String getPhotoURL(DownloadLink link) throws PluginException {
        final String ret = new Regex(link.getPluginPatternMatcher(), "(https?://(www\\.)?(flickrdecrypted|flickr)\\.com/photos/[^<>\"/]+/\\d+)").getMatch(0);
        if (ret == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else {
            return ret;
        }
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    /**
     * Keep in mind that there is this nice oauth API which might be useful in the future: https://www.flickr.com/services/oembed?url=
     *
     * Other calls of the normal API which might be useful in the future: https://www.flickr.com/services/api/flickr.photos.getInfo.html
     * https://www.flickr.com/services/api/flickr.photos.getSizes.html TODO API: Get correct csrf values so we can make requests as a
     * logged-in user
     */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        if (downloadLink.getBooleanProperty("offline", false)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        correctDownloadLink(downloadLink);
        final String photoURL = getPhotoURL(downloadLink);
        id = new Regex(photoURL, "(\\d+)$").getMatch(0);
        user = new Regex(photoURL, "flickr\\.com/photos/([^<>\"/]+)/").getMatch(0);
        /* Needed for custom filenames! */
        if (downloadLink.getStringProperty("username", null) == null) {
            downloadLink.setProperty("username", user);
        }
        br.clearCookies(MAINPAGE);
        final Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa != null) {
            login(aa, false, br);
        } else {
            logger.info("No account available, continuing without account...");
        }
        br.setFollowRedirects(true);
        br.getPage(photoURL);
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("div class=\"Four04Case\">") || br.containsHTML(">This member is no longer active on Flickr") || br.containsHTML("class=\"Problem\"")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (br.getURL().contains("login.yahoo.com/config")) {
            downloadLink.getLinkStatus().setStatusText("Only downloadable via account");
            return AvailableStatus.UNCHECKABLE;
        }
        String filename = getFilename(downloadLink);
        if (filename == null) {
            downloadLink.getLinkStatus().setStatusText("Only downloadable for registered users [Add a flickt account to download such links!]");
            logger.warning("Filename not found, plugin must be broken...");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (br.containsHTML("class=\"videoplayer main\\-photo\"")) {
            /* Last build with old handling: 26451 */
            /*
             * TODO: Add correct API csrf cookie handling so we can use this while being logged in to download videos and do not have to
             * remove the cookies here - that's just a workaround!
             */
            br.clearCookies(MAINPAGE);
            br.getPage(photoURL + "/in/photostream");
            final String secret = br.getRegex("\"secret\":\"([^<>\"]*)\"").getMatch(0);
            final Browser apibr = br.cloneBrowser();
            // we need to load it before calling!!
            JDUtilities.getPluginForDecrypt("flickr.com");
            final String api_key = jd.plugins.decrypter.FlickrCom.getPublicAPIKey(apibr);
            if (api_key == null || secret == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            apibr.getPage("https://api.flickr.com/services/rest?photo_id=" + id + "&secret=" + secret + "&method=flickr.video.getStreamInfo&csrf=&api_key=" + api_key + "&format=json&hermes=1&hermesClient=1&reqId=&nojsoncallback=1");
            dllink = apibr.getRegex("\"type\":\"orig\",\\s*?\"_content\":\"(https[^<>\"]*?)\"").getMatch(0);
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dllink = dllink.replace("\\", "");
            String videoExt;
            if (dllink.contains("mp4")) {
                videoExt = ".mp4";
            } else {
                videoExt = ".flv";
            }
            filename += videoExt;
            /* Needed for custom filenames! */
            downloadLink.setProperty("ext", videoExt);
        } else {
            br.getPage(photoURL + "/in/photostream");
            dllink = getFinalLink();
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            String ext = dllink.substring(dllink.lastIndexOf("."));
            if (ext == null || ext.length() > 5) {
                ext = defaultPhotoExt;
            }
            if (!filename.endsWith(ext)) {
                filename = filename + ext;
            }
            /* Needed for custom filenames! */
            downloadLink.setProperty("photo_id", id);
        }
        /* Needed for custom filenames! */
        final String uploadedDate = PluginJSonUtils.getJsonValue(br, "datePosted");
        if (uploadedDate != null) {
            downloadLink.setProperty("dateadded", Long.parseLong(uploadedDate) * 1000);
        }
        /* Save it for the getFormattedFilename function. */
        downloadLink.setProperty("decryptedfilename", filename);
        /* TODO: Remove this backwards compatibility in March 2015 */
        downloadLink.setProperty("custom_filenames_allowed", true);
        filename = getFormattedFilename(downloadLink);
        downloadLink.setFinalFileName(filename);
        this.br.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br.openHeadConnection(dllink);
            // con = br.openGetConnection(dllink);
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (br.getURL().contains("login.yahoo.com/config")) {
            try {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            } catch (final Throwable e) {
                if (e instanceof PluginException) {
                    throw (PluginException) e;
                }
            }
            throw new PluginException(LinkStatus.ERROR_FATAL, "This file can only be downloaded by premium users");
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
        if (dl.getConnection().getURL().toString().contains("/photo_unavailable.gif")) {
            /* Same as check below */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE);
        }
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (dl.startDownload()) {
            /*
             * 2016-08-19: Detect "TZemporarily unavailable" message inside downloaded picture via md5 hash of the file:
             * https://board.jdownloader.org/showthread.php?t=70487"
             */
            boolean isTempUnavailable = false;
            try {
                isTempUnavailable = "e60b98765d26e34bfbb797c1a5f378f2".equalsIgnoreCase(JDHash.getMD5(new File(downloadLink.getFileOutput())));
            } catch (final Throwable e) {
            }
            if (isTempUnavailable) {
                /* Reset progress */
                downloadLink.setDownloadCurrent(0);
                /* Size unknown */
                downloadLink.setDownloadSize(0);
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE);
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, false, br);
        account.setType(AccountType.FREE);
        ai.setUnlimitedTraffic();
        return ai;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void handlePremium(final DownloadLink downloadLink, final Account account) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    public void login(final Account account, final boolean force, final Browser br) throws Exception {
        synchronized (LOCK) {
            try {
                br.setFollowRedirects(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    br.setCookies(getHost(), cookies);
                    if (isValid(br)) {
                        account.saveCookies(br.getCookies(getHost()), "");
                        return;
                    } else {
                        br.clearCookies(null);
                    }
                }
                br.getPage("https://www.flickr.com/signin/");
                Form login = br.getFormByRegex("login-username-form");
                if (login == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                login.put("username", Encoding.urlEncode(account.getUser()));
                br.submitForm(login);
                if (br.containsHTML("messages\\.ERROR_INVALID_USERNAME")) {
                    final String message = br.getRegex("messages\\.ERROR_INVALID_USERNAME\">\\s*(.*?)\\s*<").getMatch(0);
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, message, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                login = br.getFormByRegex("name\\s*=\\s*\"displayName\"");
                if (login == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                login.put("password", Encoding.urlEncode(account.getPass()));
                login.remove("skip");
                br.submitForm(login);
                if (br.containsHTML("messages\\.ERROR_INVALID_PASSWORD")) {
                    final String message = br.getRegex("messages\\.ERROR_INVALID_PASSWORD\">\\s*(.*?)\\s*<").getMatch(0);
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, message, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                account.saveCookies(br.getCookies(getHost()), "");
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    private boolean isValid(final Browser br) throws IOException {
        br.getPage("https://www.flickr.com/");
        if (br.containsHTML("gnSignin")) {
            return false;
        } else {
            return true;
        }
    }

    @SuppressWarnings("unused")
    private String createGuid() {
        String a = "";
        final String b = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._";
        int c = 0;
        while (c < 22) {
            final int index = (int) Math.floor(Math.random() * b.length());
            a = a + b.substring(index, index + 1);
            c++;
        }
        return a;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private String getFinalLink() throws Exception {
        String finallink = null;
        final String[] sizes = { "o", "k", "h", "l", "c", "z", "m", "n", "s", "t", "q", "sq" };
        String picSource;
        // picSource = br.getRegex("modelExport: (\\{\"photo\\-models\".*?),[\t\n\r ]+auth: auth,").getMatch(0);
        picSource = br.getRegex("main\":(\\{\"photo-models\".*?),[\t\n\r ]+auth: auth,").getMatch(0);
        if (picSource != null) {
            /* json handling */
            final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(picSource);
            final ArrayList<Object> photo_models = (ArrayList) entries.get("photo-models");
            final LinkedHashMap<String, Object> photo_data = (LinkedHashMap<String, Object>) photo_models.get(0);
            final LinkedHashMap<String, Object> photo_sizes = (LinkedHashMap<String, Object>) photo_data.get("sizes");
            for (final String size : sizes) {
                final LinkedHashMap<String, Object> single_size_map = (LinkedHashMap<String, Object>) photo_sizes.get(size);
                if (single_size_map != null) {
                    finallink = (String) single_size_map.get("url");
                    if (finallink != null) {
                        if (!finallink.startsWith("http")) {
                            finallink = "https:" + finallink;
                        }
                        break;
                    }
                }
            }
        } else {
            /* Site handling */
            /*
             * Fast way to get finallink via site as we always try to access the "o" (original) quality. Page might be redirected!
             */
            br.getPage("https://www.flickr.com/photos/" + user + "/" + id + "/sizes/o");
            if (br.getURL().contains("sizes/o")) { // Not redirected
                finallink = br.getRegex("<a href=\"([^<>\"]+)\">\\s*(Dieses Foto im Originalformat|Download the Original)").getMatch(0);
            }
            if (finallink == null) { // Redirected if download original is not allowed
                /*
                 * If it is redirected, get the highest available quality
                 */
                picSource = br.getRegex("<ol class=\"sizes-list\">(.*?)<div id=\"allsizes-photo\">").getMatch(0);
                for (final String size : sizes) {
                    String allsizeslink = new Regex(picSource, "\"(/photos/[A-Za-z0-9\\-_]+/\\d+/sizes/" + size + "/)\"").getMatch(0);
                    if (allsizeslink != null) {
                        br.getPage("https://www.flickr.com" + allsizeslink);
                        finallink = br.getRegex("id=\"allsizes-photo\">[^~]*?<img src=\"(http[^<>\"]*?)\"").getMatch(0);
                    }
                    if (finallink != null) {
                        break;
                    }
                }
            }
        }
        return finallink;
    }

    @SuppressWarnings("deprecation")
    private String getFilename(final DownloadLink dl) throws PluginException {
        final String photoURL = getPhotoURL(dl);
        final String photo_id = new Regex(photoURL, "(\\d+)$").getMatch(0);
        String filename = dl.getStringProperty("decryptedfilename", null);
        if (filename == null) {
            filename = br.getRegex("<meta name=\"title\" content=\"(.*?)\"").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("class=\"photo\\-title\">(.*?)</h1").getMatch(0);
            }
            if (filename == null) {
                filename = br.getRegex("<title>(.*?) \\| Flickr \\- Photo Sharing\\!</title>").getMatch(0);
            }
            if (filename == null) {
                filename = br.getRegex("<meta name=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
            }
            if (filename == null) {
                filename = br.getRegex("<title>([^<>]+)\\| Flickr</title").getMatch(0);
            }
            if (filename == null) {
                /* Ultimate fallback */
                filename = photo_id;
            }
            if (filename != null) {
                filename = Encoding.htmlDecode(filename).trim();
                /* trim */
                while (filename != null) {
                    if (filename.endsWith(".")) {
                        filename = filename.substring(0, filename.length() - 1);
                    } else if (filename.endsWith(" ")) {
                        filename = filename.substring(0, filename.length() - 1);
                    } else {
                        break;
                    }
                }
                final ExtensionsFilterInterface ext = CompiledFiletypeFilter.getExtensionsFilterInterface(Files.getExtension(filename));
                if (ext == null) {
                    filename = photo_id + "_" + filename;
                } else {
                    if (!StringUtils.equalsIgnoreCase(dl.getStringProperty("ext", defaultPhotoExt), "." + ext.name())) {
                        dl.setProperty("ext", "." + ext.name().toLowerCase(Locale.ENGLISH));
                    }
                    filename = Files.getFileNameWithoutExtension(filename);
                }
            }
            /* Required for custom filenames! */
            if (dl.getStringProperty("title", null) == null) {
                dl.setProperty("title", filename);
            }
        }
        return filename;
    }

    @SuppressWarnings("deprecation")
    public static String getFormattedFilename(final DownloadLink downloadLink) throws ParseException {
        String formattedFilename = null;
        final SubConfiguration cfg = SubConfiguration.getConfig("flickr.com");
        final String customStringForEmptyTags = getCustomStringForEmptyTags();
        final String owner = cfg.getStringProperty("owner", customStringForEmptyTags);
        final String site_title = downloadLink.getStringProperty("title", customStringForEmptyTags);
        final String ext = downloadLink.getStringProperty("ext", defaultPhotoExt);
        final String username = downloadLink.getStringProperty("username", customStringForEmptyTags);
        final String photo_id = downloadLink.getStringProperty("photo_id", customStringForEmptyTags);
        final long date = downloadLink.getLongProperty("dateadded", 0l);
        String formattedDate = null;
        final String userDefinedDateFormat = cfg.getStringProperty(CUSTOM_DATE, defaultCustomDate);
        Date theDate = new Date(date);
        if (userDefinedDateFormat != null) {
            try {
                final SimpleDateFormat formatter = new SimpleDateFormat(userDefinedDateFormat);
                formattedDate = formatter.format(theDate);
            } catch (Exception e) {
                /* prevent user error killing the custom filename function. */
                formattedDate = defaultCustomStringForEmptyTags;
            }
        }
        formattedFilename = cfg.getStringProperty(CUSTOM_FILENAME, defaultCustomFilename);
        if (formattedFilename == null || formattedFilename.equals("")) {
            formattedFilename = defaultCustomFilename;
        }
        formattedFilename = formattedFilename.toLowerCase();
        /* Make sure that the user entered a VALID custom filename - if not, use the default name */
        if (!formattedFilename.contains("*extension*") || (!formattedFilename.contains("*photo_id*") && !formattedFilename.contains("*date*") && !formattedFilename.contains("*username*") && !formattedFilename.contains("*owner*"))) {
            formattedFilename = defaultCustomFilename;
        }
        formattedFilename = formattedFilename.replace("*photo_id*", photo_id);
        formattedFilename = formattedFilename.replace("*date*", formattedDate);
        formattedFilename = formattedFilename.replace("*extension*", ext);
        formattedFilename = formattedFilename.replace("*username*", username);
        formattedFilename = formattedFilename.replace("*owner*", owner);
        formattedFilename = formattedFilename.replace("*title*", site_title);
        /* Cut filenames if they're too long */
        if (formattedFilename.length() > 180) {
            int extLength = ext.length();
            formattedFilename = formattedFilename.substring(0, 180 - extLength);
            formattedFilename += ext;
        }
        return formattedFilename;
    }

    public static String getCustomStringForEmptyTags() {
        final SubConfiguration cfg = SubConfiguration.getConfig("flickr.com");
        String emptytag = cfg.getStringProperty(CUSTOM_FILENAME_EMPTY_TAG_STRING, defaultCustomStringForEmptyTags);
        if (emptytag.equals("")) {
            emptytag = defaultCustomStringForEmptyTags;
        }
        return emptytag;
    }

    private static final String defaultCustomDate               = "MM-dd-yyyy";
    private static final String defaultCustomFilename           = "*username*_*photo_id*_*title**extension*";
    public final static String  defaultCustomStringForEmptyTags = "-";
    public final static String  defaultPhotoExt                 = ".jpg";

    @Override
    public String getDescription() {
        return "JDownloader's flickr.com Plugin helps downloading media from flickr. Here you can define custom filenames.";
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_DATE, JDL.L("plugins.hoster.flickrcom.customdate", "Define how the date should look like:")).setDefaultValue(defaultCustomDate));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        /* Filename settings */
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME, JDL.L("plugins.hoster.flickrcom.customfilename", "Custom filename:")).setDefaultValue(defaultCustomFilename));
        final StringBuilder sbtags = new StringBuilder();
        sbtags.append("Explanation of the available tags:\r\n");
        sbtags.append("*photo_id* = ID of the photo\r\n");
        sbtags.append("*owner* = Name of the owner of the photo\r\n");
        sbtags.append("*username* = Username taken out of the url\r\n");
        sbtags.append("*date* = ate when the photo was uploaded - custom date format will be used here\r\n");
        sbtags.append("*title* = Title of the photo\r\n");
        sbtags.append("*extension* = Extension of the photo - usually '.jpg'");
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, sbtags.toString()));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME_EMPTY_TAG_STRING, JDL.L("plugins.hoster.flirkrcom.customEmptyTagsString", "Char which will be used for empty tags (e.g. missing data):")).setDefaultValue(defaultCustomStringForEmptyTags));
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("free"))) {
            /* free accounts also have captchas */
            return true;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("nopremium"))) {
            /* free accounts also have captchas */
            return true;
        }
        if (acc.getStringProperty("session_type") != null && !"premium".equalsIgnoreCase(acc.getStringProperty("session_type"))) {
            return true;
        }
        return false;
    }
}