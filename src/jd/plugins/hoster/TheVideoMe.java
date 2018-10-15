//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForHost;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.InputField;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.components.SiteType.SiteTemplate;
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "vev.io" }, urls = { "https?://(?:www\\.)?(thevideo\\.me|thevideo\\.cc|vev\\.io)/((?:vid)?embed\\-|embed/)?[a-z0-9]{12}" })
public class TheVideoMe extends antiDDoSForHost {
    private String               correctedBR                  = "";
    private String               passCode                     = null;
    private static final String  PASSWORDTEXT                 = "<br><b>Passwor(d|t):</b> <input";
    /* primary website url, take note of redirects */
    private static final String  COOKIE_HOST                  = "https://vev.io";
    private static final String  NICE_HOST                    = COOKIE_HOST.replaceAll("(https://|http://)", "");
    private static final String  NICE_HOSTproperty            = COOKIE_HOST.replaceAll("(https://|http://|\\.|\\-)", "");
    /* domain names used within download links */
    private static final String  DOMAINS                      = "(thevideo\\.me|thevideo\\.cc|vev\\.io)";
    private static final String  MAINTENANCE                  = ">\\s*?This server is in maintenance mode";
    private static final String  MAINTENANCEUSERTEXT          = JDL.L("hoster.xfilesharingprobasic.errors.undermaintenance", "This server is under maintenance");
    private static final String  ALLWAIT_SHORT                = JDL.L("hoster.xfilesharingprobasic.errors.waitingfordownloads", "Waiting till new downloads can be started");
    private static final String  PREMIUMONLY1                 = JDL.L("hoster.xfilesharingprobasic.errors.premiumonly1", "Max downloadable filesize for free users:");
    private static final String  PREMIUMONLY2                 = JDL.L("hoster.xfilesharingprobasic.errors.premiumonly2", "Only downloadable via premium or registered");
    private static final boolean VIDEOHOSTER                  = false;
    private static final boolean VIDEOHOSTER_2                = true;
    private static final boolean VIDEOHOSTER_3                = false;
    /* 'Pairing' */
    private static final boolean VIDEOHOSTER_4                = true;
    private static final boolean SUPPORTSHTTPS                = true;
    private final boolean        ENABLE_HTML_FILESIZE_CHECK   = false;
    private static final boolean ENABLE_API_AVAILABLECHECK    = true;
    /* Connection stuff */
    private static final boolean FREE_RESUME                  = true;
    private static final int     FREE_MAXCHUNKS               = -2;
    private static final int     FREE_MAXDOWNLOADS            = 2;
    private static final boolean ACCOUNT_FREE_RESUME          = true;
    private static final int     ACCOUNT_FREE_MAXCHUNKS       = -2;
    private static final int     ACCOUNT_FREE_MAXDOWNLOADS    = 2;
    private static final boolean ACCOUNT_PREMIUM_RESUME       = true;
    private static final int     ACCOUNT_PREMIUM_MAXCHUNKS    = -2;
    private static final int     ACCOUNT_PREMIUM_MAXDOWNLOADS = 20;
    /* note: CAN NOT be negative or zero! (ie. -1 or 0) Otherwise math sections fail. .:. use [1-20] */
    private static AtomicInteger totalMaxSimultanFreeDownload = new AtomicInteger(FREE_MAXDOWNLOADS);
    /* don't touch the following! */
    private static AtomicInteger maxFree                      = new AtomicInteger(1);
    private static AtomicInteger maxPrem                      = new AtomicInteger(1);
    private static Object        LOCK                         = new Object();
    private String               fuid                         = null;

    /* DEV NOTES */
    // XfileSharingProBasic Version 2.6.6.2
    // mods: heavily modified, DO NOT UPGRADE!
    // limit-info:
    // protocol: no https
    // captchatype: null
    // other: VIDEOHOSTER_2-handling only works for "official" videos, returns "in conversion stage" error for all others - does not
    // matter, we try anyways
    // They fight against DL-managers - other possibility to get dllink easier: https://thevideo.me/pair and
    // https://thevideo.me/pair?file_code=<fuid>&check
    @Override
    public void correctDownloadLink(final DownloadLink link) {
        /* link cleanup, but respect users protocol choosing */
        if (!SUPPORTSHTTPS) {
            link.setUrlDownload(link.getDownloadURL().replaceFirst("https://", "http://"));
        }
        if (!StringUtils.containsIgnoreCase("vev.io/", link.getDownloadURL())) {
            try {
                Browser br = new Browser();
                br.setFollowRedirects(true);
                br.getPage(link.getDownloadURL());
                if (canHandle(br.getURL()) && StringUtils.containsIgnoreCase("vev.io", br._getURL().getHost())) {
                    link.setUrlDownload(br.getURL());
                }
            } catch (final Throwable e) {
                logger.log(e);
            }
        }
    }

    @Override
    public String rewriteHost(String host) {
        if ("thevideo.me".equals(host)) {
            return "vev.io";
        }
        return super.rewriteHost(host);
    }

    @Override
    public String getAGBLink() {
        return COOKIE_HOST + "/tos.html";
    }

    public TheVideoMe(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(COOKIE_HOST + "/premium.html");
        setConfigElements();
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        br.setFollowRedirects(true);
        setFUID(link);
        final String[] fileInfo = new String[3];
        if (ENABLE_API_AVAILABLECHECK) {
            /* 2018-10-15: New */
            getPage("https://" + this.getHost() + "/api/serve/video/" + this.fuid);
            final String errorcode = PluginJSonUtils.getJson(br, "code");
            if (errorcode != null) {
                /* E.. {"code":400,"message":"invalid video code","errors":[]} */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            fileInfo[0] = PluginJSonUtils.getJson(br, "title");
            if (StringUtils.isEmpty(fileInfo[0])) {
                /* Fallback */
                fileInfo[0] = this.fuid;
            }
        } else {
            getPage(link.getDownloadURL());
            if (new Regex(correctedBR, "(No such file|>\\s*File Not Found\\s*<|>The file was removed by|Reason for deletion:\n|>Video encoding error|>Video not found)").matches()) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (new Regex(correctedBR, MAINTENANCE).matches()) {
                link.getLinkStatus().setStatusText(MAINTENANCEUSERTEXT);
                return AvailableStatus.UNCHECKABLE;
            }
            if (br.getURL().contains("/?op=login&redirect=")) {
                link.getLinkStatus().setStatusText(PREMIUMONLY2);
                return AvailableStatus.UNCHECKABLE;
            }
            scanInfo(fileInfo);
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            if (correctedBR.contains("You have reached the download(\\-| )limit")) {
                logger.warning("Waittime detected, please reconnect to make the linkchecker work!");
                return AvailableStatus.UNCHECKABLE;
            }
            logger.warning("filename equals null, throwing \"plugin defect\"");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (fileInfo[2] != null && !fileInfo[2].equals("")) {
            link.setMD5Hash(fileInfo[2].trim());
        }
        fileInfo[0] = fileInfo[0].replaceAll("(</b>|<b>|\\.html)", "");
        fileInfo[0] = removeDoubleExtensions(fileInfo[0], "mp4");
        if (!fileInfo[0].endsWith(".mp4")) {
            fileInfo[0] += ".mp4";
        }
        link.setName(fileInfo[0].trim());
        if (fileInfo[1] != null && !fileInfo[1].equals("")) {
            link.setDownloadSize(SizeFormatter.getSize(fileInfo[1]));
        }
        return AvailableStatus.TRUE;
    }

    private String[] scanInfo(final String[] fileInfo) {
        /* standard traits from base page */
        if (fileInfo[0] == null) {
            fileInfo[0] = new Regex(correctedBR, "You have requested.*?https?://(www\\.)?" + DOMAINS + "/" + fuid + "/(.*?)</font>").getMatch(2);
            if (fileInfo[0] == null) {
                fileInfo[0] = new Regex(correctedBR, "fname\"( type=\"hidden\")? value=\"(.*?)\"").getMatch(1);
                if (fileInfo[0] == null) {
                    fileInfo[0] = new Regex(correctedBR, "<h2>Download File(.*?)</h2>").getMatch(0);
                    /* traits from download1 page below */
                    if (fileInfo[0] == null) {
                        fileInfo[0] = new Regex(correctedBR, "Filename:? ?(<[^>]+> ?)+?([^<>\"\\']+)").getMatch(1);
                        // next two are details from sharing box
                        if (fileInfo[0] == null) {
                            fileInfo[0] = new Regex(correctedBR, "copy\\(this\\);.+>(.+) \\- [\\d\\.]+ (KB|MB|GB)</a></textarea>[\r\n\t ]+</div>").getMatch(0);
                            if (fileInfo[0] == null) {
                                fileInfo[0] = new Regex(correctedBR, "copy\\(this\\);.+\\](.+) \\- [\\d\\.]+ (KB|MB|GB)\\[/URL\\]").getMatch(0);
                                if (fileInfo[0] == null) {
                                    /* Link of the box without filesize */
                                    fileInfo[0] = new Regex(correctedBR, "onFocus=\"copy\\(this\\);\">http://(www\\.)?" + DOMAINS + "/" + fuid + "/([^<>\"]*?)</textarea").getMatch(2);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (fileInfo[0] == null) {
            fileInfo[0] = br.getRegex("video\"\\s*:\\s*\\{\\s*\"title\"\\s*:\\s*\"(.*?)\"").getMatch(0);
        }
        if (fileInfo[0] == null) {
            fileInfo[0] = new Regex(correctedBR, "<title>(?:Watch)?\\s*(.*?)(\\s*-\\s*Vevio)?</title>").getMatch(0);
        }
        if (ENABLE_HTML_FILESIZE_CHECK) {
            if (fileInfo[1] == null) {
                fileInfo[1] = new Regex(correctedBR, "\\(([0-9]+ bytes)\\)").getMatch(0);
                if (fileInfo[1] == null) {
                    fileInfo[1] = new Regex(correctedBR, "</font>[ ]+\\(([^<>\"\\'/]+)\\)(.*?)</font>").getMatch(0);
                    if (fileInfo[1] == null) {
                        fileInfo[1] = new Regex(correctedBR, "(\\d+(\\.\\d+)? ?(KB|MB|GB))").getMatch(0);
                    }
                }
            }
        }
        if (fileInfo[2] == null) {
            fileInfo[2] = new Regex(correctedBR, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
        }
        return fileInfo;
    }

    private String removeDoubleExtensions(String filename, final String defaultExtension) {
        if (filename == null) {
            return filename;
        }
        String ext_temp = null;
        int index = 0;
        while (filename.contains(".")) {
            /* First let's remove all video extensions */
            index = filename.lastIndexOf(".");
            ext_temp = filename.substring(index);
            if (ext_temp != null && ext_temp.matches("\\.(mp4|flv|mkv|avi)")) {
                filename = filename.substring(0, index);
                continue;
            }
            break;
        }
        /* Add wished default video extension */
        if (!filename.endsWith("." + defaultExtension)) {
            filename += "." + defaultExtension;
        }
        return filename;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, FREE_RESUME, FREE_MAXCHUNKS, "freelink");
    }

    @SuppressWarnings({ "unused", "deprecation" })
    public void doFree(final DownloadLink downloadLink, final boolean resumable, final int maxchunks, final String directlinkproperty) throws Exception, PluginException {
        if (ENABLE_API_AVAILABLECHECK) {
            /* Important! Access our main content URL first! */
            getPage(downloadLink.getDownloadURL());
        }
        /* Prevent redirects when we access the main url again later below. */
        final String url_from_availablecheck = this.br.getURL();
        br.setFollowRedirects(false);
        passCode = downloadLink.getStringProperty("pass");
        /* First, bring up saved final links */
        boolean is_saved_directlink = false;
        boolean is_correct_finallink = false;
        /* Required to get 'auth_code' via website-handling (without 'pairing'-mode). */
        String special_js_bullshit_code = getSpecialJsBullshit();
        String auth_code = null;
        String dllink = checkDirectLink(downloadLink, directlinkproperty);
        if (dllink != null) {
            is_saved_directlink = true;
        }
        /* Second, check for streaming/direct links on the first page */
        if (dllink == null) {
            dllink = getDllink();
        }
        Browser brv = br.cloneBrowser();
        /* Third, do they provide video hosting? */
        if (dllink == null && VIDEOHOSTER) {
            try {
                logger.info("Trying to get link via vidembed");
                getPage(brv, "/vidembed-" + fuid);
                dllink = brv.getRedirectLocation();
                if (dllink == null) {
                    logger.info("Failed to get link via embed because: " + br.toString());
                } else {
                    logger.info("Successfully found link via vidembed");
                }
            } catch (final Throwable e) {
                logger.info("Failed to get link via vidembed");
            }
        }
        if (dllink == null && VIDEOHOSTER_3) {
            /* TODO 2016-09-16: Fix this! */
            try {
                // getPage(brv, "/download/" + fuid);
                /* 2016-08-31: New - but the old one still works: /download/getversions/<FUID> */
                getPage(brv, "/cgi-bin/index_dl.cgi?op=get_vid_versions&file_code=" + fuid);
                final String[] vidinfo = brv.getRegex("download_video\\((.*?)\\)").getColumn(0);
                long filesize_max = 0;
                long filesize_temp = 0;
                for (final String vidinfo_single : vidinfo) {
                    final Regex videoinfo = new Regex(vidinfo_single, "\\'([a-z0-9]+)\\',\\'([^<>\"\\']*?)\\',\\'([^<>\"\\']*?)\\'");
                    final String vid = videoinfo.getMatch(0);
                    String q = videoinfo.getMatch(1);
                    final String dlid = videoinfo.getMatch(2);
                    if (vid == null || q == null || dlid == null) {
                        continue;
                    }
                    /* Force highest quality */
                    q = "h";
                    getPage(brv, "http://thevideo.me/download/" + vid + "/" + q + "/" + dlid);
                    dllink = this.getDllink(brv.toString());
                    if (dllink == null) {
                        dllink = brv.getRegex("\"(https?://(?!stats)[^<>\"]+\\.thevideo\\.[^/]+/[^<>\"]*?)\"").getMatch(0);
                    }
                    if (dllink == null) {
                        final Form origdl = brv.getFormByInputFieldKeyValue("op", "download_orig");
                        if (origdl != null) {
                            origdl.setAction("/download/" + this.fuid + "/" + q + "/" + dlid);
                            origdl.remove("dl");
                            brv.submitForm(origdl);
                            dllink = this.getDllink(brv.toString());
                        }
                    }
                    if (dllink != null) {
                        /* Do not modify dllink because of special_js_bullshit later! */
                        is_correct_finallink = true;
                        logger.info("VIDEOHOSTER_3 handling: success!");
                        // http://thevideo.me/dljsv/xme2krekhp78
                        special_js_bullshit_code = brv.getRegex("/dljsv/([^<>\"\\'/]+)\"").getMatch(0);
                        if (special_js_bullshit_code != null) {
                            brv.getPage("/dljsv/" + this.fuid);
                            final String special_id = brv.getRegex("each\\|([A-Za-z0-9]+)").getMatch(0);
                            if (special_id != null) {
                                dllink += "?download=true&vt=" + special_id;
                            }
                        }
                        break;
                    } else {
                        // logger.warning("VIDEOHOSTER_3 handling failed --> Trying again");
                        logger.warning("VIDEOHOSTER_3 handling failed");
                        break;
                    }
                    // this.sleep(3000, downloadLink);
                }
            } catch (final Throwable e) {
                logger.warning("VIDEOHOSTER_3 handling failed");
            }
        }
        if (VIDEOHOSTER_4 && StringUtils.isEmpty(dllink) && StringUtils.isEmpty(auth_code) && StringUtils.isEmpty(special_js_bullshit_code)) {
            synchronized (LOCK) {
                /* 2018-10-15: Thx to: github.com/Kodi-vStream/venom-xbmc-addons/issues/2144 */
                /*
                 * 2017-07-28: Try pairing as a fallback if we cannot work around it <br /> This is commonly used in KODI.
                 */
                int count = 0;
                boolean authenticated = false;
                do {
                    logger.info("Attempting Pairing: " + count);
                    /* Remove cookies & headers */
                    brv = br.cloneBrowser();
                    brv.setFollowRedirects(true);
                    brv.getHeaders().put("Accept", "application/json");
                    brv.getPage(String.format("/api/pair?file_code=%s&check", this.fuid));
                    /* Bad: {"sessions":[]}, Good: {"sessions":[{"ip":"12.12.12.12","expire":8528}]} */
                    try {
                        final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(brv.toString());
                        final ArrayList<Object> ressourcelist = (ArrayList<Object>) entries.get("sessions");
                        if (!ressourcelist.isEmpty()) {
                            authenticated = true;
                        }
                    } catch (final Throwable e) {
                    }
                    if (!authenticated) {
                        logger.info("Pairing: No authenticated - requires captcha");
                        brv.getPage("/pair");
                        final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, brv, "6Ld4TlsUAAAAAAeU5tInYtZNMEOTANb6LKxP94it").getToken();
                        brv.getPage("/pair?activate=1&g-recaptcha-response=" + Encoding.urlEncode(recaptchaV2Response));
                        /*
                         * Possible 'response' (String) errormessages here (WITH ""): <br /> "Invalid Captcha!" <br />
                         * "Captcha is required!"
                         */
                        if (Boolean.parseBoolean(PluginJSonUtils.getJson(brv, "status"))) {
                            logger.info("Pairing: Seems to be successful");
                        } else {
                            logger.info("Pairing: Seems to have failed");
                        }
                    } else {
                        final String authentification_expire_seconds = PluginJSonUtils.getJson(brv, "expire");
                        if (!StringUtils.isEmpty(authentification_expire_seconds) && authentification_expire_seconds.matches("\\d+")) {
                            logger.info("Pairing: Authenticated for: " + TimeFormatter.formatSeconds(Long.parseLong(authentification_expire_seconds), 0));
                        } else {
                            logger.info("Pairing: Authenticated");
                        }
                        break;
                    }
                    count++;
                } while (!authenticated && count <= 1);
                if (authenticated) {
                    brv.getHeaders().put("Accept", "application/json");
                    brv.getHeaders().put("Content-Type", "application/json;charset=UTF-8");
                    brv.postPage("/api/serve/video/" + this.fuid, "");
                    logger.info("Pairing successful");
                    try {
                        final String dllink_temp = this.getDllink(brv.toString());
                        if (!StringUtils.isEmpty(auth_code)) {
                            logger.info("Pairing: Found auth_code");
                        } else {
                            /* 2018-10-15: auth_code is not required anymore */
                            logger.info("Pairing: Failed to find auth_code");
                        }
                        if (!StringUtils.isEmpty(dllink_temp)) {
                            logger.info("Pairing: Found downloadlink --> Using it");
                            dllink = dllink_temp;
                        } else {
                            logger.warning("Pairing: Failed to find downloadlink");
                        }
                    } catch (final Throwable e) {
                        logger.warning("Pairing: json handling failed");
                    }
                } else {
                    logger.info("Pairing failed");
                }
            }
        }
        if (VIDEOHOSTER_2 && StringUtils.isEmpty(dllink)) {
            try {
                logger.info("Trying to get link via embed");
                final String embed_access = "/embed/" + fuid;
                getPage(embed_access);
                if (br.containsHTML("while we validate your request")) {
                    /*
                     * 2018-10-15: Captcha required, reCaptchaKey currently hardcoded (ATTENTION: This is a different key than used for the
                     * 'pairing' handling!!)
                     */
                    final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br, "6LczkHAUAAAAAO6frTIweoNAgvLg_RWmoe8JZJkU").getToken();
                    brv.getHeaders().put("content-type", "application/json;charset=UTF-8");
                    brv.getHeaders().put("x-adblock", "0");
                    brv.postPageRaw("/api/serve/video/" + this.fuid, "{\"g-recaptcha-response\":\"" + recaptchaV2Response + "\"}");
                } else {
                    /* Without captcha - untested */
                    brv.postPage("/api/serve/video/" + this.fuid, "");
                }
                /* 2018-10-15: special_js_bullshit_code is not required anymore */
                // special_js_bullshit_code = getSpecialJsBullshit();
                dllink = getDllink(brv.toString());
                if (dllink == null) {
                    logger.info("Failed to get link via embed because: " + br.toString());
                } else {
                    logger.info("Successfully found link via embed");
                    /* Do not modify url later! */
                    is_correct_finallink = true;
                }
            } catch (final Throwable e) {
                logger.info("Failed to get link via embed");
            }
            if (dllink == null) {
                /* If failed, go back to the beginning */
                getPage(url_from_availablecheck);
            }
        }
        if (!is_correct_finallink && auth_code == null && !StringUtils.isEmpty(dllink) && !StringUtils.isEmpty(special_js_bullshit_code) && !is_saved_directlink) {
            /* Some code to prevent their measures of blocking us (2016-08-19: They rickrolled us :D) */
            getPage(brv, "/vsign/player/" + special_js_bullshit_code);
            final String jscrap = doThis(brv);
            auth_code = new Regex(jscrap, "vt=(.*?)\";").getMatch(0);
        }
        if (auth_code != null) {
            logger.info("auth_code is present --> Adding it");
            dllink += "?direct=false&ua=1&vt=" + auth_code;
        } else {
            logger.info("auth_code is null --> Possible failure");
        }
        logger.info("Final downloadlink = " + dllink + " starting the download...");
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resumable, maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 503) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Connection limit reached, please contact our support!", 5 * 60 * 1000l);
            }
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            correctBR();
            checkServerErrors();
            handlePluginBroken(downloadLink, "dllinknofile", 3);
        } else if (isFakeDllink(dl.getConnection())) {
            /* Admin trolls/blocks us by returning rick-roll video ... */
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Server error please contact the JDownloader Support", 5 * 60 * 1000l);
        }
        downloadLink.setProperty(directlinkproperty, dllink);
        fixFilename(downloadLink);
        try {
            /* add a download slot */
            controlFree(+1);
            /* start the dl */
            dl.startDownload();
        } finally {
            /* remove download slot */
            controlFree(-1);
        }
    }

    private String doThis(Browser brv) {
        try {
            final String x = brv.toString().replace("eval(", "").replaceFirst("\\)$", "");
            final ScriptEngineManager manager = org.jdownloader.scripting.JavaScriptEngineFactory.getScriptEngineManager(null);
            final ScriptEngine engine = manager.getEngineByName("javascript");
            String result = null;
            try {
                engine.eval("var res = " + x);
                result = (String) engine.get("res");
            } catch (final Exception e) {
                e.printStackTrace();
            }
            return result;
        } catch (final Throwable t) {
            return null;
        }
    }

    private String getSpecialJsBullshit() {
        return new Regex(correctedBR, "app\\.config\\.adblock_domain \\+ location\\.pathname \\+ location\\.search[^<>]*?</script>\\s*?<script>\\s*?var [^=]+=\\'([^<>\"\\']+)\\';").getMatch(0);
    }

    private boolean isFakeDllink(final URLConnectionAdapter con) {
        return con.getLongContentLength() == 7548543;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return maxFree.get();
    }

    /* do not add @Override here to keep 0.* compatibility */
    public boolean hasAutoCaptcha() {
        return true;
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("nopremium"))) {
            /* free accounts also have captchas */
            return true;
        }
        return false;
    }

    @Override
    protected boolean useRUA() {
        return true;
    }

    @Override
    protected Browser prepBrowser(final Browser prepBr, final String host) {
        if (!(browserPrepped.containsKey(prepBr) && browserPrepped.get(prepBr) == Boolean.TRUE)) {
            super.prepBrowser(prepBr, host);
            prepBr.setCookie(COOKIE_HOST, "lang", "english");
        }
        return prepBr;
    }

    /**
     * Prevents more than one free download from starting at a given time. One step prior to dl.startDownload(), it adds a slot to maxFree
     * which allows the next singleton download to start, or at least try.
     *
     * This is needed because xfileshare(website) only throws errors after a final dllink starts transferring or at a given step within pre
     * download sequence. But this template(XfileSharingProBasic) allows multiple slots(when available) to commence the download sequence,
     * this.setstartintival does not resolve this issue. Which results in x(20) captcha events all at once and only allows one download to
     * start. This prevents wasting peoples time and effort on captcha solving and|or wasting captcha trading credits. Users will experience
     * minimal harm to downloading as slots are freed up soon as current download begins.
     *
     * @param controlFree
     *            (+1|-1)
     */
    public synchronized void controlFree(final int num) {
        logger.info("maxFree was = " + maxFree.get());
        maxFree.set(Math.min(Math.max(1, maxFree.addAndGet(num)), totalMaxSimultanFreeDownload.get()));
        logger.info("maxFree now = " + maxFree.get());
    }

    /* Removes HTML code which could break the plugin */
    public void correctBR() throws NumberFormatException, PluginException {
        correctedBR = br.toString();
        ArrayList<String> regexStuff = new ArrayList<String>();
        // remove custom rules first!!! As html can change because of generic cleanup rules.
        /* generic cleanup */
        regexStuff.add("<\\!(\\-\\-.*?\\-\\-)>");
        regexStuff.add("(display: ?none;\">.*?</div>)");
        regexStuff.add("(visibility:hidden>.*?<)");
        for (String aRegex : regexStuff) {
            String results[] = new Regex(correctedBR, aRegex).getColumn(0);
            if (results != null) {
                for (String result : results) {
                    correctedBR = correctedBR.replace(result, "");
                }
            }
        }
    }

    public String getDllink() {
        return getDllink(correctedBR);
    }

    private String js = null;

    public String getDllink(final String source) {
        final HashMap<String, String> qualities = new HashMap<String, String>();
        String dllink = br.getRedirectLocation();
        if (dllink == null || !isDllink(dllink)) {
            // json within javascript var. note: within br not correctedbr
            js = new Regex(br, "var jwConfig_vars = (\\{.*?\\});").getMatch(0);
            if (js != null) {
                try {
                    final String[] sources = PluginJSonUtils.getJsonResultsFromArray(PluginJSonUtils.getJsonArray(js, "sources"));
                    if (sources != null) {
                        for (final String sourcee : sources) {
                            final String label = PluginJSonUtils.getJson(sourcee, "label");
                            final String file = PluginJSonUtils.getJson(sourcee, "file");
                            qualities.put(label, file);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (source.startsWith("{\"qualities")) {
            try {
                LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(source);
                entries = (LinkedHashMap<String, Object>) entries.get("qualities");
                final Iterator<Entry<String, Object>> iterator = entries.entrySet().iterator();
                while (iterator.hasNext()) {
                    final Entry<String, Object> entry = iterator.next();
                    final String label = entry.getKey();
                    final String url = (String) entry.getValue();
                    if (label != null && url != null) {
                        qualities.put(label, url);
                    }
                }
            } catch (final Throwable e) {
            }
        }
        if (!qualities.isEmpty()) {
            /* Multiple qualities available --> Return what the user prefers. */
            final String configuredQuality = getConfiguredVideoHeight();
            final boolean downloadBEST = !configuredQuality.matches("\\d+");
            // get best
            if (!downloadBEST) {
                dllink = qualities.get(configuredQuality + "p");
            }
            if (dllink == null) {
                /* User wants best quality or his selected quality was not available. */
                dllink = qualities.get("1080p");
                if (dllink == null) {
                    dllink = qualities.get("720p");
                    if (dllink == null) {
                        dllink = qualities.get("480p");
                        if (dllink == null) {
                            dllink = qualities.get("360p");
                            if (dllink == null) {
                                dllink = qualities.get("240p");
                            }
                        }
                    }
                }
            }
            if (dllink != null) {
                return dllink;
            }
        }
        if (dllink == null) {
            dllink = br.getRegex("\"(https?://d\\d*.\\." + DOMAINS + ".*?)\"").getMatch(0);
        }
        if (dllink == null) {
            dllink = new Regex(source, "(\"|\\')(https?://(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|([\\w\\-\\.]+\\.)?" + DOMAINS + ")(:\\d{1,4})?/(files|d|cgi\\-bin/dl\\.cgi)/(\\d+/)?[a-z0-9]+/[^<>\"/]*?)(\"|\\')").getMatch(1);
            if (dllink == null) {
                final String cryptedScripts[] = new Regex(source, "p\\}\\((.*?)\\.split\\('\\|'\\)").getColumn(0);
                if (cryptedScripts != null && cryptedScripts.length != 0) {
                    for (String crypted : cryptedScripts) {
                        dllink = decodeDownloadLink(crypted);
                        if (dllink != null) {
                            break;
                        }
                    }
                }
            }
        }
        if (dllink == null) {
            dllink = new Regex(source, "(https?://[a-z0-9]+\\." + DOMAINS + ":\\d+/[^<>\"\\']+)").getMatch(0);
        }
        if (dllink == null) {
            /* Sometimes used for streaming */
            dllink = new Regex(source, "file:[\t\n\r ]*?\"(http[^<>\"]*?\\.(?:mp4|flv))\"").getMatch(0);
        }
        return dllink;
    }

    /* 2017-02-15: Quick n dirty function to prevent dllink = br.getRedirectLocation() --> Wrong dllink! */
    private boolean isDllink(final String url) {
        final boolean isDllink = url != null && !url.matches(".+/[a-z0-9]+\\-[a-z0-9]{12}.*?");
        return isDllink;
    }

    private String decodeDownloadLink(final String s) {
        String decoded = null;
        try {
            Regex params = new Regex(s, "\\'(.*?[^\\\\])\\',(\\d+),(\\d+),\\'(.*?)\\'");
            String p = params.getMatch(0).replaceAll("\\\\", "");
            int a = Integer.parseInt(params.getMatch(1));
            int c = Integer.parseInt(params.getMatch(2));
            String[] k = params.getMatch(3).split("\\|");
            while (c != 0) {
                c--;
                if (k[c].length() != 0) {
                    p = p.replaceAll("\\b" + Integer.toString(c, a) + "\\b", k[c]);
                }
            }
            decoded = p;
        } catch (Exception e) {
        }
        String finallink = null;
        if (decoded != null) {
            /* Open regex is possible because in the unpacked JS there are usually only 1 links */
            finallink = new Regex(decoded, "(\"|\\')(https?://[^<>\"\\']*?(\\.(avi|flv|mkv|mp4)|/v))(\"|\\')").getMatch(1);
        }
        return finallink;
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("text") || con.getLongContentLength() == -1 || isFakeDllink(con)) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    @Override
    protected void getPage(final String page) throws Exception {
        super.getPage(page);
        correctBR();
    }

    @Override
    protected void postPage(final String page, final String postdata) throws Exception {
        super.postPage(page, postdata);
        correctBR();
    }

    @Override
    protected void submitForm(final Form form) throws Exception {
        super.submitForm(form);
        correctBR();
    }

    private void waitTime(long timeBefore, final DownloadLink downloadLink) throws PluginException {
        int passedTime = (int) ((System.currentTimeMillis() - timeBefore) / 1000) - 1;
        /** Ticket Time */
        final String ttt = new Regex(correctedBR, "id=\"countdown_str\">[^<>\"]+<span id=\"[^<>\"]+\"( class=\"[^<>\"]+\")?>([\n ]+)?(\\d+)([\n ]+)?</span>").getMatch(2);
        if (ttt != null) {
            int wait = Integer.parseInt(ttt);
            wait -= passedTime;
            logger.info("[Seconds] Waittime on the page: " + ttt);
            logger.info("[Seconds] Passed time: " + passedTime);
            logger.info("[Seconds] Total time to wait: " + wait);
            if (wait > 0) {
                sleep(wait * 1000l, downloadLink);
            }
        }
    }

    // TODO: remove this when v2 becomes stable. use br.getFormbyKey(String key, String value)
    /**
     * Returns the first form that has a 'key' that equals 'value'.
     *
     * @param key
     * @param value
     * @return
     */
    private Form getFormByKey(final String key, final String value) {
        Form[] workaround = br.getForms();
        if (workaround != null) {
            for (Form f : workaround) {
                for (InputField field : f.getInputFields()) {
                    if (key != null && key.equals(field.getKey())) {
                        if (value == null && field.getValue() == null) {
                            return f;
                        }
                        if (value != null && value.equals(field.getValue())) {
                            return f;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * This fixes filenames from all xfs modules: file hoster, audio/video streaming (including transcoded video), or blocked link checking
     * which is based on fuid.
     *
     * @version 0.2
     * @author raztoki
     */
    private void fixFilename(final DownloadLink downloadLink) {
        String orgName = null;
        String orgExt = null;
        String servName = null;
        String servExt = null;
        String orgNameExt = downloadLink.getFinalFileName();
        if (orgNameExt == null) {
            orgNameExt = downloadLink.getName();
        }
        if (!inValidate(orgNameExt) && orgNameExt.contains(".")) {
            orgExt = orgNameExt.substring(orgNameExt.lastIndexOf("."));
        }
        if (!inValidate(orgExt)) {
            orgName = new Regex(orgNameExt, "(.+)" + orgExt).getMatch(0);
        } else {
            orgName = orgNameExt;
        }
        // if (orgName.endsWith("...")) orgName = orgName.replaceFirst("\\.\\.\\.$", "");
        String servNameExt = Encoding.htmlDecode(getFileNameFromHeader(dl.getConnection()));
        if (!inValidate(servNameExt) && servNameExt.contains(".")) {
            servExt = servNameExt.substring(servNameExt.lastIndexOf("."));
            servName = new Regex(servNameExt, "(.+)" + servExt).getMatch(0);
        } else {
            servName = servNameExt;
        }
        String FFN = null;
        if (orgName.equalsIgnoreCase(fuid.toLowerCase())) {
            FFN = servNameExt;
        } else if (inValidate(orgExt) && !inValidate(servExt) && (servName.toLowerCase().contains(orgName.toLowerCase()) && !servName.equalsIgnoreCase(orgName))) {
            /*
             * when partial match of filename exists. eg cut off by quotation mark miss match, or orgNameExt has been abbreviated by hoster
             */
            FFN = servNameExt;
        } else if (!inValidate(orgExt) && !inValidate(servExt) && !orgExt.equalsIgnoreCase(servExt)) {
            FFN = orgName + servExt;
        } else {
            FFN = orgNameExt;
        }
        downloadLink.setFinalFileName(FFN);
    }

    private void setFUID(final DownloadLink dl) {
        fuid = new Regex(dl.getDownloadURL(), "([a-z0-9]{12})$").getMatch(0);
    }

    private String handlePassword(final Form pwform, final DownloadLink thelink) throws PluginException {
        if (passCode == null) {
            passCode = Plugin.getUserInput("Password?", thelink);
        }
        if (passCode == null || passCode.equals("")) {
            logger.info("User has entered blank password, exiting handlePassword");
            passCode = null;
            thelink.setProperty("pass", Property.NULL);
            return null;
        }
        if (pwform == null) {
            /* so we know handlePassword triggered without any form */
            logger.info("Password Form == null");
        } else {
            logger.info("Put password \"" + passCode + "\" entered by user in the DLForm.");
            pwform.put("password", Encoding.urlEncode(passCode));
        }
        thelink.setProperty("pass", passCode);
        return passCode;
    }

    public void checkErrors(final DownloadLink theLink, final boolean checkAll) throws NumberFormatException, PluginException {
        if (checkAll) {
            if (new Regex(correctedBR, PASSWORDTEXT).matches() && correctedBR.contains("Wrong password")) {
                /* handle password has failed in the past, additional try catching / resetting values */
                logger.warning("Wrong password, the entered password \"" + passCode + "\" is wrong, retrying...");
                passCode = null;
                theLink.setProperty("pass", Property.NULL);
                throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
            }
            if (correctedBR.contains("Wrong captcha")) {
                logger.warning("Wrong captcha or wrong password!");
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            if (correctedBR.contains("\">Skipped countdown<")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Fatal countdown error (countdown skipped)");
            }
        }
        /** Wait time reconnect handling */
        if (new Regex(correctedBR, "(You have reached the download(\\-| )limit|You have to wait)").matches()) {
            /* adjust this regex to catch the wait time string for COOKIE_HOST */
            String WAIT = new Regex(correctedBR, "((You have reached the download(\\-| )limit|You have to wait)[^<>]+)").getMatch(0);
            String tmphrs = new Regex(WAIT, "\\s+(\\d+)\\s+hours?").getMatch(0);
            if (tmphrs == null) {
                tmphrs = new Regex(correctedBR, "You have to wait.*?\\s+(\\d+)\\s+hours?").getMatch(0);
            }
            String tmpmin = new Regex(WAIT, "\\s+(\\d+)\\s+minutes?").getMatch(0);
            if (tmpmin == null) {
                tmpmin = new Regex(correctedBR, "You have to wait.*?\\s+(\\d+)\\s+minutes?").getMatch(0);
            }
            String tmpsec = new Regex(WAIT, "\\s+(\\d+)\\s+seconds?").getMatch(0);
            String tmpdays = new Regex(WAIT, "\\s+(\\d+)\\s+days?").getMatch(0);
            if (tmphrs == null && tmpmin == null && tmpsec == null && tmpdays == null) {
                logger.info("Waittime regexes seem to be broken");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, 60 * 60 * 1000l);
            } else {
                int minutes = 0, seconds = 0, hours = 0, days = 0;
                if (tmphrs != null) {
                    hours = Integer.parseInt(tmphrs);
                }
                if (tmpmin != null) {
                    minutes = Integer.parseInt(tmpmin);
                }
                if (tmpsec != null) {
                    seconds = Integer.parseInt(tmpsec);
                }
                if (tmpdays != null) {
                    days = Integer.parseInt(tmpdays);
                }
                int waittime = ((days * 24 * 3600) + (3600 * hours) + (60 * minutes) + seconds + 1) * 1000;
                logger.info("Detected waittime #2, waiting " + waittime + "milliseconds");
                /* Not enough wait time to reconnect -> Wait short and retry */
                if (waittime < 180000) {
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, JDL.L("plugins.hoster.xfilesharingprobasic.allwait", ALLWAIT_SHORT), waittime);
                }
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, waittime);
            }
        }
        if (correctedBR.contains("You're using all download slots for IP")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, 10 * 60 * 1001l);
        }
        if (correctedBR.contains("Error happened when generating Download Link")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error!", 10 * 60 * 1000l);
        }
        /** Error handling for only-premium links */
        if (new Regex(correctedBR, "( can download files up to |Upgrade your account to download bigger files|>Upgrade your account to download larger files|>The file you requested reached max downloads limit for Free Users|Please Buy Premium To download this file<|This file reached max downloads limit)").matches()) {
            String filesizelimit = new Regex(correctedBR, "You can download files up to(.*?)only").getMatch(0);
            if (filesizelimit != null) {
                filesizelimit = filesizelimit.trim();
                logger.info("As free user you can download files up to " + filesizelimit + " only");
                try {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
                } catch (final Throwable e) {
                    if (e instanceof PluginException) {
                        throw (PluginException) e;
                    }
                }
                throw new PluginException(LinkStatus.ERROR_FATAL, PREMIUMONLY1 + " " + filesizelimit);
            } else {
                logger.info("Only downloadable via premium");
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            }
        } else if (br.getURL().contains("/?op=login&redirect=")) {
            logger.info("Only downloadable via premium");
            try {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            } catch (final Throwable e) {
                if (e instanceof PluginException) {
                    throw (PluginException) e;
                }
            }
            throw new PluginException(LinkStatus.ERROR_FATAL, PREMIUMONLY2);
        }
        if (new Regex(correctedBR, MAINTENANCE).matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, MAINTENANCEUSERTEXT, 2 * 60 * 60 * 1000l);
        } else if (new Regex(correctedBR, "Conversion Status:").matches()) {
            /* 2017-02-22 */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "This video is still in conversion state, download & stream not possible yet", 30 * 60 * 1000l);
        }
    }

    public void checkServerErrors() throws NumberFormatException, PluginException {
        if (new Regex(correctedBR, Pattern.compile("No file", Pattern.CASE_INSENSITIVE)).matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: 'no file'", 2 * 60 * 60 * 1000l);
        }
        if (new Regex(correctedBR, Pattern.compile("Wrong IP", Pattern.CASE_INSENSITIVE)).matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: 'Wrong IP'", 2 * 60 * 60 * 1000l);
        }
        if (new Regex(correctedBR, "(File Not Found|<h1>404 Not Found</h1>)").matches()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, "Server error (404)", 30 * 60 * 1000l);
        }
    }

    /**
     * Is intended to handle out of date errors which might occur seldom by re-tring a couple of times before throwing the out of date
     * error.
     *
     * @param dl
     *            : The DownloadLink
     * @param error
     *            : The name of the error
     * @param maxRetries
     *            : Max retries before out of date error is thrown
     */
    private void handlePluginBroken(final DownloadLink dl, final String error, final int maxRetries) throws PluginException {
        int timesFailed = dl.getIntegerProperty(NICE_HOSTproperty + "failedtimes_" + error, 0);
        dl.getLinkStatus().setRetryCount(0);
        if (timesFailed <= maxRetries) {
            logger.info(NICE_HOST + ": " + error + " -> Retrying");
            timesFailed++;
            dl.setProperty(NICE_HOSTproperty + "failedtimes_" + error, timesFailed);
            throw new PluginException(LinkStatus.ERROR_RETRY, "Unknown error occured: " + error);
        } else {
            dl.setProperty(NICE_HOSTproperty + "failedtimes_" + error, Property.NULL);
            logger.info(NICE_HOST + ": " + error + " -> Plugin is broken");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        /* reset maxPrem workaround on every fetchaccount info */
        try {
            login(account, true);
        } catch (final PluginException e) {
            account.setValid(false);
            throw e;
        }
        final String space[] = new Regex(correctedBR, ">Used space:</td>.*?<td.*?b>([0-9\\.]+) ?(KB|MB|GB|TB)?</b>").getRow(0);
        if ((space != null && space.length != 0) && (space[0] != null && space[1] != null)) {
            /* free users it's provided by default */
            ai.setUsedSpace(space[0] + " " + space[1]);
        } else if ((space != null && space.length != 0) && space[0] != null) {
            /* premium users the Mb value isn't provided for some reason... */
            ai.setUsedSpace(space[0] + "Mb");
        }
        account.setValid(true);
        final String availabletraffic = new Regex(correctedBR, "Traffic available.*?:</TD><TD><b>([^<>\"\\']+)</b>").getMatch(0);
        if (availabletraffic != null && !availabletraffic.contains("nlimited") && !availabletraffic.equalsIgnoreCase(" Mb")) {
            availabletraffic.trim();
            /* need to set 0 traffic left, as getSize returns positive result, even when negative value supplied. */
            if (!availabletraffic.startsWith("-")) {
                ai.setTrafficLeft(SizeFormatter.getSize(availabletraffic));
            } else {
                ai.setTrafficLeft(0);
            }
        } else {
            ai.setUnlimitedTraffic();
        }
        /* If the premium account is expired we'll simply accept it as a free account. */
        final String expire = new Regex(correctedBR, "On (\\w+ \\d{1,2}, \\d{4}), your premium membership").getMatch(0);
        long expire_milliseconds = 0;
        if (expire != null) {
            expire_milliseconds = TimeFormatter.getMilliSeconds(expire, "MMM dd, yyyy", Locale.ENGLISH);
        }
        if ((expire_milliseconds - System.currentTimeMillis()) <= 0) {
            maxPrem.set(ACCOUNT_FREE_MAXDOWNLOADS);
            account.setProperty("nopremium", true);
            try {
                account.setType(AccountType.FREE);
                account.setMaxSimultanDownloads(maxPrem.get());
                account.setConcurrentUsePossible(false);
            } catch (final Throwable e) {
                /* not available in old Stable 0.9.581 */
            }
            ai.setStatus("Registered (free) account");
        } else {
            ai.setValidUntil(expire_milliseconds);
            maxPrem.set(ACCOUNT_PREMIUM_MAXDOWNLOADS);
            account.setProperty("nopremium", false);
            try {
                account.setType(AccountType.PREMIUM);
                account.setMaxSimultanDownloads(maxPrem.get());
                account.setConcurrentUsePossible(true);
            } catch (final Throwable e) {
                /* not available in old Stable 0.9.581 */
            }
            ai.setStatus("Premium account");
        }
        return ai;
    }

    @SuppressWarnings("unchecked")
    private void login(final Account account, final boolean force) throws Exception {
        br = new Browser();
        synchronized (LOCK) {
            try {
                /* Load cookies */
                br.setCookiesExclusive(true);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            this.br.setCookie(COOKIE_HOST, key, value);
                        }
                        return;
                    }
                }
                br.setFollowRedirects(true);
                /** TODO: Fix me / reCaptchaV2 login */
                getPage(COOKIE_HOST + "/auth/login");
                final Form loginform = br.getFormbyProperty("name", "FL");
                if (loginform == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłąd wtyczki, skontaktuj się z Supportem JDownloadera!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                loginform.put("login", Encoding.urlEncode(account.getUser()));
                loginform.put("password", Encoding.urlEncode(account.getPass()));
                submitForm(loginform);
                if (br.getCookie(COOKIE_HOST, "login") == null || br.getCookie(COOKIE_HOST, "xfsts") == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername, Passwort oder login Captcha!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłędny użytkownik/hasło lub kod Captcha wymagany do zalogowania!\r\nUpewnij się, że prawidłowo wprowadziłes hasło i nazwę użytkownika. Dodatkowo:\r\n1. Jeśli twoje hasło zawiera znaki specjalne, zmień je (usuń) i spróbuj ponownie!\r\n2. Wprowadź hasło i nazwę użytkownika ręcznie bez użycia opcji Kopiuj i Wklej.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or login captcha!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                if (!br.getURL().contains("/?op=my_account")) {
                    getPage("/?op=my_account");
                }
                if (!new Regex(correctedBR, "Premium(?:-| )Account expire|>Renew premium<|<strong>Premium</strong>|").matches()) {
                    account.setProperty("nopremium", true);
                } else {
                    account.setProperty("nopremium", false);
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", fetchCookies(this.getHost()));
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink downloadLink, final Account account) throws Exception {
        passCode = downloadLink.getStringProperty("pass");
        requestFileInformation(downloadLink);
        login(account, false);
        if (account.getBooleanProperty("nopremium")) {
            requestFileInformation(downloadLink);
            doFree(downloadLink, ACCOUNT_FREE_RESUME, ACCOUNT_FREE_MAXCHUNKS, "freelink2");
        } else {
            String dllink = checkDirectLink(downloadLink, "premlink");
            if (dllink == null) {
                br.setFollowRedirects(false);
                getPage(downloadLink.getDownloadURL());
                dllink = getDllink();
                if (dllink == null) {
                    String link = br.getRegex("\"(https?://thevideo\\.me/download/.*?)\"").getMatch(0);
                    if (link != null) {
                        getPage(br, link);
                        link = br.getRegex("url: \"(/cgi-bin/.*?)\"").getMatch(0);
                        if (link != null) {
                            getPage(br, link);
                            link = br.getRegex("video\\(\\'(.*?)\\'\\)").getMatch(0);
                            if (link != null) {
                                link = "http://thevideo.me/download/" + link.replace("','", "/");
                                getPage(br, link);
                                dllink = br.getRegex("\"(https?://d\\d*.\\.thevideo\\.me.*?)\"").getMatch(0);
                            }
                        }
                    }
                }
                if (dllink == null) {
                    Form dlform = br.getFormbyProperty("name", "F1");
                    if (dlform != null && new Regex(correctedBR, PASSWORDTEXT).matches()) {
                        passCode = handlePassword(dlform, downloadLink);
                    }
                    checkErrors(downloadLink, true);
                    if (dlform == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    submitForm(dlform);
                    checkErrors(downloadLink, true);
                    dllink = getDllink();
                    if (dllink == null) {
                        sleep(2000, downloadLink);
                        dlform = br.getFormbyProperty("name", "F1");
                        if (dlform == null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        submitForm(dlform);
                        checkErrors(downloadLink, true);
                        dllink = getDllink();
                    }
                }
            }
            if (dllink == null) {
                logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            logger.info("Final downloadlink = " + dllink + " starting the download...");
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, ACCOUNT_PREMIUM_RESUME, ACCOUNT_PREMIUM_MAXCHUNKS);
            if (dl.getConnection().getContentType().contains("html")) {
                if (dl.getConnection().getResponseCode() == 503) {
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Connection limit reached, please contact our support!", 5 * 60 * 1000l);
                }
                logger.warning("The final dllink seems not to be a file!");
                br.followConnection();
                correctBR();
                checkServerErrors();
                handlePluginBroken(downloadLink, "dllinknofile", 3);
            }
            fixFilename(downloadLink);
            downloadLink.setProperty("premlink", dllink);
            dl.startDownload();
        }
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /* workaround for free/premium issue on stable 09581 */
        return maxPrem.get();
    }

    private String getConfiguredVideoHeight() {
        final int selection = this.getPluginConfig().getIntegerProperty(SELECTED_VIDEO_FORMAT, 0);
        final String selectedResolution = FORMATS[selection];
        if (selectedResolution.matches("\\d+p")) {
            final String height = new Regex(selectedResolution, "(\\d+)p").getMatch(0);
            return height;
        } else {
            /* BEST selection */
            return selectedResolution;
        }
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX_INDEX, getPluginConfig(), SELECTED_VIDEO_FORMAT, FORMATS, "Select preferred videoresolution:").setDefaultValue(0));
    }

    /* The list of qualities displayed to the user */
    private final String[] FORMATS               = new String[] { "BEST", "1080p", "720p", "480p", "360p", "240p" };
    private final String   SELECTED_VIDEO_FORMAT = "SELECTED_VIDEO_FORMAT";

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.SibSoft_XFileShare;
    }
}