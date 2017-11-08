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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.HTMLParser;
import jd.parser.html.InputField;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;
import jd.plugins.components.UserAgents;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.keycaptcha.KeyCaptcha;
import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "uploadrocket.net" }, urls = { "https?://(www\\.)?uploadrocket\\.net/(vidembed\\-)?[a-z0-9]{12}" })
public class UploadRocketNet extends PluginForHost {
    private String                         correctedBR                  = "";
    private String                         passCode                     = null;
    private static final String            PASSWORDTEXT                 = "<br><b>Passwor(d|t):</b> <input";
    // primary website url, take note of redirects
    private static final String            COOKIE_HOST                  = "http://uploadrocket.net";
    private static final String            NICE_HOST                    = COOKIE_HOST.replaceAll("(https://|http://)", "");
    private static final String            NICE_HOSTproperty            = COOKIE_HOST.replaceAll("(https://|http://|\\.|\\-)", "");
    // domain names used within download links.
    private static final String            DOMAINS                      = "(uploadrocket\\.net)";
    private static final String            MAINTENANCE                  = ">This server is in maintenance mode";
    private static final String            MAINTENANCEUSERTEXT          = JDL.L("hoster.xfilesharingprobasic.errors.undermaintenance", "This server is under Maintenance");
    private static final String            ALLWAIT_SHORT                = JDL.L("hoster.xfilesharingprobasic.errors.waitingfordownloads", "Waiting till new downloads can be started");
    private static final String            PREMIUMONLY1                 = JDL.L("hoster.xfilesharingprobasic.errors.premiumonly1", "Max downloadable filesize for free users:");
    private static final String            PREMIUMONLY2                 = JDL.L("hoster.xfilesharingprobasic.errors.premiumonly2", "Only downloadable via premium or registered");
    private static final boolean           VIDEOHOSTER                  = false;
    private static final boolean           SUPPORTSHTTPS                = true;
    private static final boolean           SUPPORTS_ALT_AVAILABLECHECK  = true;
    /* Enable/Disable random User-Agent - only needed if a website blocks the standard JDownloader User-Agent */
    private final boolean                  ENABLE_RANDOM_UA             = true;
    // Connection stuff
    private static final boolean           FREE_RESUME                  = true;
    private static final int               FREE_MAXCHUNKS               = -2;
    private static final int               FREE_MAXDOWNLOADS            = 20;
    private static final boolean           ACCOUNT_FREE_RESUME          = true;
    private static final int               ACCOUNT_FREE_MAXCHUNKS       = -2;
    private static final int               ACCOUNT_FREE_MAXDOWNLOADS    = 20;
    private static final boolean           ACCOUNT_PREMIUM_RESUME       = true;
    private static final int               ACCOUNT_PREMIUM_MAXCHUNKS    = 0;
    private static final int               ACCOUNT_PREMIUM_MAXDOWNLOADS = 20;
    // note: CAN NOT be negative or zero! (ie. -1 or 0) Otherwise math sections fail. .:. use [1-20]
    private static AtomicInteger           totalMaxSimultanFreeDownload = new AtomicInteger(FREE_MAXDOWNLOADS);
    // don't touch the following!
    private static AtomicInteger           maxFree                      = new AtomicInteger(1);
    private static Object                  LOCK                         = new Object();
    private String                         fuid                         = null;
    private static AtomicReference<String> agent                        = new AtomicReference<String>(null);

    // DEV NOTES
    // XfileSharingProBasic Version 2.6.4.4
    // mods: implemented requestFileInfo method of XFS 2.6.6.6
    // limit-info: premium untested
    // protocol: no https
    // captchatype: 4dignum
    // other:
    // note: sister site kingfiles.net, 2016-10-19: kingfiles.net == RIP, 2016-11-28: kingfiles.net back online [XFS]
    @Override
    public void correctDownloadLink(final DownloadLink link) {
        // link cleanup, but respect users protocol choosing.
        if (!SUPPORTSHTTPS) {
            link.setUrlDownload(link.getDownloadURL().replaceFirst("https://", "http://"));
        }
        final String fid = new Regex(link.getDownloadURL(), "([a-z0-9]{12})$").getMatch(0);
        link.setUrlDownload(COOKIE_HOST + "/" + fid);
    }

    @Override
    public String getAGBLink() {
        return COOKIE_HOST + "/tos.html";
    }

    public UploadRocketNet(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(COOKIE_HOST + "/premium.html");
    }

    // do not add @Override here to keep 0.* compatibility
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

    public void prepBrowser(final Browser br) {
        // define custom browser headers and language settings.
        br.getHeaders().put("Accept-Language", "en-gb, en;q=0.9");
        br.setCookie(COOKIE_HOST, "lang", "english");
        if (ENABLE_RANDOM_UA) {
            if (agent.get() == null) {
                agent.set(UserAgents.stringUserAgent());
            }
            br.getHeaders().put("User-Agent", agent.get());
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        final String[] fileInfo = new String[3];
        final Browser altbr = br.cloneBrowser();
        br.setFollowRedirects(true);
        correctDownloadLink(link);
        prepBrowser(br);
        setFUID(link);
        getPage(link.getDownloadURL());
        if (new Regex(correctedBR, MAINTENANCE).matches()) {
            fileInfo[0] = this.getFnameViaAbuseLink(altbr, link);
            if (fileInfo[0] != null) {
                link.setName(Encoding.htmlDecode(fileInfo[0]).trim());
                return AvailableStatus.TRUE;
            }
            link.getLinkStatus().setStatusText(MAINTENANCEUSERTEXT);
            return AvailableStatus.UNCHECKABLE;
        }
        if (br.getURL().contains("/?op=login&redirect=")) {
            logger.info("PREMIUMONLY handling: Trying alternative linkcheck");
            link.getLinkStatus().setStatusText(PREMIUMONLY2);
            try {
                fileInfo[0] = this.getFnameViaAbuseLink(altbr, link);
                if (br.containsHTML(">No such file<")) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                altbr.getPage("http://" + NICE_HOST + "/?op=report_file&id=" + fuid);
                if (altbr.containsHTML(">No such file<")) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                fileInfo[0] = altbr.getRegex("<b>Filename:</b></td><td>([^<>\"]*?)</td>").getMatch(0);
                if (SUPPORTS_ALT_AVAILABLECHECK) {
                    altbr.postPage(COOKIE_HOST + "/?op=checkfiles", "op=checkfiles&process=Check+URLs&list=" + Encoding.urlEncode(link.getDownloadURL()));
                    fileInfo[1] = altbr.getRegex(">" + link.getDownloadURL() + "</td><td style=\"color:green;\">Found</td><td>([^<>\"]*?)</td>").getMatch(0);
                }
                /* 2nd offline check */
                if ((SUPPORTS_ALT_AVAILABLECHECK && altbr.containsHTML("(>" + link.getDownloadURL() + "</td><td style=\"color:red;\">Not found\\!</td>|" + this.fuid + " not found\\!</font>)")) && fileInfo[0] == null) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                } else if (fileInfo[0] != null || fileInfo[1] != null) {
                    /* We know the link is online, set all information we got */
                    link.setAvailable(true);
                    if (fileInfo[0] != null) {
                        link.setName(Encoding.htmlDecode(fileInfo[0].trim()));
                    } else {
                        link.setName(fuid);
                    }
                    if (fileInfo[1] != null) {
                        link.setDownloadSize(SizeFormatter.getSize(fileInfo[1]));
                    }
                    return AvailableStatus.TRUE;
                }
            } catch (final Throwable e) {
                logger.info("Unknown error occured in alternative linkcheck:");
                e.printStackTrace();
            }
            logger.warning("Alternative linkcheck failed!");
            return AvailableStatus.UNCHECKABLE;
        }
        scanInfo(fileInfo);
        if (fileInfo[0] == null || fileInfo[0].equals("")) {
            if (correctedBR.contains("You have reached the download(\\-| )limit")) {
                logger.warning("Waittime detected, please reconnect to make the linkchecker work!");
                return AvailableStatus.UNCHECKABLE;
            }
            if (new Regex(correctedBR, "(No such file|>File Not Found<|>The file was removed by|Reason for deletion\\.?:\n|File Not Found|>The file expired|>The file was removed by administrator<)").matches()) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            /* Last chance, check for offline */
            fileInfo[0] = getFnameViaAbuseLink(altbr, link);
            logger.warning("filename equals null, throwing \"plugin defect\"");
            if (inValidate(fileInfo[0])) {
                // throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        if (fileInfo[2] != null && !fileInfo[2].equals("")) {
            link.setMD5Hash(fileInfo[2].trim());
        }
        fileInfo[0] = fileInfo[0].replaceAll("(</b>|<b>|\\.html)", "");
        link.setName(fileInfo[0].trim());
        if (fileInfo[1] == null && SUPPORTS_ALT_AVAILABLECHECK) {
            /* Do alt availablecheck here but don't check availibility because we already know that the file must be online! */
            logger.info("Filesize not available, trying altAvailablecheck");
            try {
                altbr.postPage(COOKIE_HOST + "/?op=checkfiles", "op=checkfiles&process=Check+URLs&list=" + Encoding.urlEncode(link.getDownloadURL()));
                fileInfo[1] = altbr.getRegex(">" + link.getDownloadURL() + "</td><td style=\"color:green;\">Found</td><td>([^<>\"]*?)</td>").getMatch(0);
            } catch (final Throwable e) {
            }
        }
        if (fileInfo[1] != null && !fileInfo[1].equals("")) {
            link.setDownloadSize(SizeFormatter.getSize(fileInfo[1]));
        }
        return AvailableStatus.TRUE;
    }

    private String[] scanInfo(final String[] fileInfo) {
        // standard traits from base page
        if (inValidate(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, "You have requested.*?https?://(www\\.)?" + DOMAINS + "/" + fuid + "/(.*?)</font>").getMatch(2);
            if (inValidate(fileInfo[0])) {
                fileInfo[0] = new Regex(correctedBR, "fname\"( type=\"hidden\")? value=\"(.*?)\"").getMatch(1);
                if (inValidate(fileInfo[0])) {
                    fileInfo[0] = new Regex(correctedBR, "<h2>Download File(.*?)</h2>").getMatch(0);
                    // traits from download1 page below.
                    if (inValidate(fileInfo[0])) {
                        fileInfo[0] = new Regex(correctedBR, "Filename:? ?(<[^>]+> ?)+?([^<>\"\\']+)").getMatch(1);
                        // next two are details from sharing box
                        if (inValidate(fileInfo[0])) {
                            fileInfo[0] = new Regex(correctedBR, "copy\\(this\\);.+>(.+) \\- [\\d\\.]+ (KB|MB|GB)</a></textarea>[\r\n\t ]+</div>").getMatch(0);
                            if (inValidate(fileInfo[0])) {
                                fileInfo[0] = new Regex(correctedBR, "copy\\(this\\);.+\\](.+) \\- [\\d\\.]+ (KB|MB|GB)\\[/URL\\]").getMatch(0);
                                if (inValidate(fileInfo[0])) {
                                    // Link of the box without filesize
                                    fileInfo[0] = new Regex(correctedBR, "onFocus=\"copy\\(this\\);\">http://(www\\.)?" + DOMAINS + "/" + fuid + "/([^<>\"]*?)</textarea").getMatch(2);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (inValidate(fileInfo[1])) {
            fileInfo[1] = new Regex(correctedBR, "\\(([0-9]+ bytes)\\)").getMatch(0);
            if (inValidate(fileInfo[1])) {
                fileInfo[1] = new Regex(correctedBR, "</font>[ ]+\\(([^<>\"\\'/]+)\\)(.*?)</font>").getMatch(0);
                // if (inValidate(fileInfo[1])) {
                // fileInfo[1] = new Regex(correctedBR, "(\\d+(\\.\\d+)? ?(KB|MB|GB))").getMatch(0);
                // }
            }
        }
        if (fileInfo[2] == null) {
            fileInfo[2] = new Regex(correctedBR, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
        }
        return fileInfo;
    }

    private String getFnameViaAbuseLink(final Browser br, final DownloadLink dl) throws IOException, PluginException {
        br.getPage("http://" + NICE_HOST + "/?op=report_file&id=" + fuid);
        br.getRequest().setHtmlCode(correctBR(br));
        if (br.containsHTML(">No such file<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return br.getRegex("<b>Filename\\s*:?\\s*</b></td><td>([^<>\"]*?)</td>").getMatch(0);
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, FREE_RESUME, FREE_MAXCHUNKS, "freelink");
    }

    @SuppressWarnings("unused")
    public void doFree(final DownloadLink downloadLink, final boolean resumable, final int maxchunks, final String directlinkproperty) throws Exception, PluginException {
        br.setFollowRedirects(false);
        passCode = downloadLink.getStringProperty("pass");
        // First, bring up saved final links
        String dllink = checkDirectLink(downloadLink, directlinkproperty);
        // Second, check for streaming links on the first page
        if (dllink == null) {
            dllink = getDllink();
            speedMod();
        }
        // Third, do they provide video hosting?
        if (dllink == null && VIDEOHOSTER) {
            try {
                logger.info("Trying to get link via vidembed");
                final Browser brv = br.cloneBrowser();
                brv.getPage("/vidembed-" + fuid);
                dllink = brv.getRedirectLocation();
                if (dllink == null) {
                    logger.info("Failed to get link via vidembed");
                }
            } catch (final Throwable e) {
                logger.info("Failed to get link via vidembed");
            }
        }
        // Fourth, continue like normal.
        if (dllink == null) {
            checkErrors(downloadLink, false);
            Form download1 = getFormDownload1();
            if (download1 != null) {
                download1.remove("method_premium");
                download1.remove("method_ispremium");
                // stable is lame, issue finding input data fields correctly. eg. closes at ' quotation mark - remove when jd2 goes
                // stable!
                if (downloadLink.getName().contains("'")) {
                    String fname = new Regex(br, "<input type=\"hidden\" name=\"fname\" value=\"([^\"]+)\">").getMatch(0);
                    if (fname != null) {
                        download1.put("fname", Encoding.urlEncode(fname));
                    } else {
                        logger.warning("Could not find 'fname'");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
                // end of backward compatibility
                sendForm(download1);
            }
            checkErrors(downloadLink, false);
            dllink = getDllink();
        }
        if (dllink == null) {
            Form dlForm = getFormDownload2();
            if (dlForm == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            // how many forms deep do you want to try.
            int repeat = 2;
            for (int i = 0; i <= repeat; i++) {
                dlForm.remove(null);
                final long timeBefore = System.currentTimeMillis();
                boolean password = false;
                boolean skipWaittime = false;
                if (new Regex(correctedBR, PASSWORDTEXT).matches()) {
                    password = true;
                    logger.info("The downloadlink seems to be password protected.");
                }
                // md5 can be on the subsequent pages
                if (downloadLink.getMD5Hash() == null) {
                    String md5hash = new Regex(correctedBR, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
                    if (md5hash != null) {
                        downloadLink.setMD5Hash(md5hash.trim());
                    }
                }
                /* Captcha START */
                if (correctedBR.contains(";background:#ccc;text-align")) {
                    logger.info("Detected captcha method \"plaintext captchas\" for this host");
                    /** Captcha method by ManiacMansion */
                    final String[][] letters = new Regex(br, "<span style=\\'position:absolute;padding\\-left:(\\d+)px;padding\\-top:\\d+px;\\'>(&#\\d+;)</span>").getMatches();
                    if (letters == null || letters.length == 0) {
                        logger.warning("plaintext captchahandling broken!");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    final SortedMap<Integer, String> capMap = new TreeMap<Integer, String>();
                    for (String[] letter : letters) {
                        capMap.put(Integer.parseInt(letter[0]), Encoding.htmlDecode(letter[1]));
                    }
                    final StringBuilder code = new StringBuilder();
                    for (String value : capMap.values()) {
                        code.append(value);
                    }
                    dlForm.put("code", code.toString());
                    logger.info("Put captchacode " + code.toString() + " obtained by captcha metod \"plaintext captchas\" in the form.");
                } else if (correctedBR.contains("/captchas/")) {
                    logger.info("Detected captcha method \"Standard captcha\" for this host");
                    final String[] sitelinks = HTMLParser.getHttpLinks(br.toString(), null);
                    String captchaurl = null;
                    if (sitelinks == null || sitelinks.length == 0) {
                        logger.warning("Standard captcha captchahandling broken!");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    for (String link : sitelinks) {
                        if (link.contains("/captchas/")) {
                            captchaurl = link;
                            break;
                        }
                    }
                    if (captchaurl == null) {
                        logger.warning("Standard captcha captchahandling broken!");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    String code = getCaptchaCode("xfilesharingprobasic", captchaurl, downloadLink);
                    dlForm.put("code", code);
                    logger.info("Put captchacode " + code + " obtained by captcha metod \"Standard captcha\" in the form.");
                } else if (new Regex(correctedBR, "(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)").matches()) {
                    logger.info("Detected captcha method \"Re Captcha\" for this host");
                    final Recaptcha rc = new Recaptcha(br, this);
                    rc.findID();
                    rc.load();
                    final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                    final String c = getCaptchaCode("recaptcha", cf, downloadLink);
                    dlForm.put("recaptcha_challenge_field", rc.getChallenge());
                    dlForm.put("recaptcha_response_field", Encoding.urlEncode(c));
                    logger.info("Put captchacode " + c + " obtained by captcha metod \"Re Captcha\" in the form and submitted it.");
                    /** wait time is often skippable for reCaptcha handling */
                    skipWaittime = true;
                } else if (br.containsHTML("solvemedia\\.com/papi/")) {
                    logger.info("Detected captcha method \"solvemedia\" for this host");
                    final org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia sm = new org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia(br);
                    File cf = null;
                    try {
                        cf = sm.downloadCaptcha(getLocalCaptchaFile());
                    } catch (final Exception e) {
                        if (org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia.FAIL_CAUSE_CKEY_MISSING.equals(e.getMessage())) {
                            throw new PluginException(LinkStatus.ERROR_FATAL, "Host side solvemedia.com captcha error - please contact the " + this.getHost() + " support");
                        }
                        throw e;
                    }
                    final String code = getCaptchaCode("solvemedia", cf, downloadLink);
                    final String chid = sm.getChallenge(code);
                    dlForm.put("adcopy_challenge", chid);
                    dlForm.put("adcopy_response", "manual_challenge");
                } else if (br.containsHTML("id=\"capcode\" name= \"capcode\"")) {
                    logger.info("Detected captcha method \"keycaptca\"");
                    String result = handleCaptchaChallenge(getDownloadLink(), new KeyCaptcha(this, br, getDownloadLink()).createChallenge(this));
                    if (result == null) {
                        throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                    }
                    if ("CANCEL".equals(result)) {
                        throw new PluginException(LinkStatus.ERROR_FATAL);
                    }
                    dlForm.put("capcode", result);
                    /** wait time is often skippable for reCaptcha handling */
                    skipWaittime = false;
                }
                /* Captcha END */
                if (password) {
                    passCode = handlePassword(dlForm, downloadLink);
                }
                if (!skipWaittime) {
                    waitTime(timeBefore, downloadLink);
                }
                sendForm(dlForm);
                logger.info("Submitted DLForm");
                checkErrors(downloadLink, true);
                dllink = getDllink();
                if (dllink == null && (!br.containsHTML("<Form name=\"F1\" method=\"POST\" action=\"\"") || i == repeat)) {
                    logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } else if (dllink == null && br.containsHTML("<Form name=\"F1\" method=\"POST\" action=\"\"")) {
                    dlForm = getFormDownload2();
                    invalidateLastChallengeResponse();
                    continue;
                } else {
                    validateLastChallengeResponse();
                    break;
                }
            }
        }
        speedMod();
        logger.info("Final downloadlink = " + dllink + " starting the download...");
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resumable, maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 503) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Connection limit reached, please contact our support!", 5 * 60 * 1000l);
            }
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            correctBR();
            checkServerErrors();
            int timesFailed = downloadLink.getIntegerProperty(NICE_HOSTproperty + "failedtimes_dllinknofile", 0);
            downloadLink.getLinkStatus().setRetryCount(0);
            if (timesFailed <= 2) {
                logger.info(NICE_HOST + ": Final link is no file -> Retrying");
                timesFailed++;
                downloadLink.setProperty(NICE_HOSTproperty + "failedtimes_dllinknofile", timesFailed);
                throw new PluginException(LinkStatus.ERROR_RETRY, "Final download link not found");
            } else {
                downloadLink.setProperty(NICE_HOSTproperty + "failedtimes_dllinknofile", Property.NULL);
                logger.info(NICE_HOST + ": Final link is no file -> Plugin is broken");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        downloadLink.setProperty(directlinkproperty, dllink);
        fixFilename(downloadLink);
        try {
            // add a download slot
            controlFree(+1);
            // start the dl
            dl.startDownload();
        } finally {
            // remove download slot
            controlFree(-1);
        }
    }

    private void speedMod() {
        final String fullspeed_cookie = new Regex(correctedBR, "if\\(document\\.getElementById\\(\\'[^<>\"\\']+\\'\\)\\)\\{[^=]*?document\\.cookie = \"sessionID=([^<>\"\\';]+);").getMatch(0);
        if (fullspeed_cookie != null) {
            /*
             * Adblocked-sessionid-cookie = max ~150 KB/s, good sessionid-cookie = 1 MB/s. Sometimes we can even skip the
             * pre-download-waittime via this!
             */
            logger.info("Speedmod cookie available!");
            this.br.setCookie(this.br.getHost(), "sessionID", fullspeed_cookie);
        } else {
            logger.info("NO speedmod cookie available");
        }
    }

    private Form getFormDownload1() {
        Form download1 = getFormByKey("op", "download1");
        if (download1 == null) {
            download1 = getFormByKey("method_free", "Free+Download");
        }
        if (download1 == null) {
            Form[] dlForms = Form.getForms(correctedBR);
            ArrayList<Form> result = new ArrayList<Form>();
            if (dlForms != null) {
                for (Form f : dlForms) {
                    if (f.hasInputFieldByName("method_premium") || f.hasInputFieldByName("method_free") || f.hasInputFieldByName("method_ispremium") || f.hasInputFieldByName("method_isfree") || f.hasInputFieldByName("fname") || f.hasInputFieldByName("id")) {
                        result.add(f);
                    }
                }
            }
            if (!result.isEmpty()) {
                download1 = result.get(0);
            }
        }
        return download1;
    }

    private Form getFormDownload2() {
        Form download2 = getFormByKey("op", "download2");
        if (download2 == null) {
            Form[] dlForms = Form.getForms(correctedBR);
            ArrayList<Form> result = new ArrayList<Form>();
            if (dlForms != null) {
                for (Form f : dlForms) {
                    if (f.hasInputFieldByName("method_premium") || f.hasInputFieldByName("method_free") || f.hasInputFieldByName("method_ispremium") || f.hasInputFieldByName("method_isfree") || f.hasInputFieldByName("fname") || f.hasInputFieldByName("id")) {
                        result.add(f);
                    }
                }
            }
            if (!result.isEmpty()) {
                download2 = result.get(0);
            }
        }
        return download2;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return maxFree.get();
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

    /** Remove HTML code which could break the plugin */
    private String correctBR() throws NumberFormatException, PluginException {
        final String corrected = correctBR(this.br);
        this.correctedBR = corrected;
        return corrected;
    }

    private String correctBR(final Browser br) {
        String correctedBR = br.toString();
        ArrayList<String> regexStuff = new ArrayList<String>();
        {
            String results[] = new Regex(correctedBR, "(<!--\\s*XFSCUSTOM.COM: gunggo pops Start.*?)<\\s*/BODY\\s*>").getColumn(0);
            for (String result : results) {
                correctedBR = correctedBR.replace(result, "");
            }
        }
        // remove custom rules first!!! As html can change because of generic cleanup rules.
        correctedBR = correctedBR.replaceAll("<\\s*(h\\d+)[^>]*>.*?<\\s*/\\1\\s*>", "");
        // generic cleanup
        regexStuff.add("<!(--.*?--)>");
        regexStuff.add("(<td[^>]+style=(\"|')[\\w:;\\s#-]*color\\s*:\\s*transparent\\s*;[^>]*>.*?</td>)");
        regexStuff.add("(<\\s*(\\w+)\\s+[^>]*style\\s*=\\s*(\"|')(?:(?:[\\w:;\\s#-]*(visibility\\s*:\\s*hidden;|display\\s*:\\s*none;|font-size\\s*:\\s*0;)[\\w:;\\s#-]*)|font-size\\s*:\\s*0|visibility\\s*:\\s*hidden|display\\s*:\\s*none)\\3[^>]*/\\s*>)");
        regexStuff.add("(<\\s*(\\w+)\\s+[^>]*style\\s*=\\s*(\"|')(?:(?:[\\w:;\\s#-]*(visibility\\s*:\\s*hidden;|display\\s*:\\s*none;|font-size\\s*:\\s*0;)[\\w:;\\s#-]*)|font-size\\s*:\\s*0|visibility\\s*:\\s*hidden|display\\s*:\\s*none)\\3[^>]*>.*?<\\s*/\\2[^>]*>)");
        for (String aRegex : regexStuff) {
            String results[] = new Regex(correctedBR, aRegex).getColumn(0);
            if (results != null) {
                for (String result : results) {
                    correctedBR = correctedBR.replace(result, "");
                }
            }
        }
        return correctedBR;
    }

    public String getDllink() {
        String dllink = br.getRedirectLocation();
        if (dllink == null) {
            dllink = new Regex(correctedBR, "var download_url = '(http://[^<>\"]*?)';").getMatch(0);
        }
        if (dllink == null) {
            dllink = new Regex(correctedBR, "(\"|')(https?://(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|([\\w\\-]+\\.)?" + DOMAINS + ")(:\\d{1,4})?/(files|d|cgi\\-bin/dl\\.cgi)/(\\d+/)?[a-z0-9]+/[^<>\"/]*?)\\1").getMatch(1);
            if (dllink == null) {
                final String cryptedScripts[] = new Regex(correctedBR, "p\\}\\((.*?)\\.split\\('\\|'\\)").getColumn(0);
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
        return dllink;
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
            finallink = new Regex(decoded, "name=\"src\"value=\"(.*?)\"").getMatch(0);
            if (finallink == null) {
                finallink = new Regex(decoded, "type=\"video/divx\"src=\"(.*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = new Regex(decoded, "\\.addVariable\\(\\'file\\',\\'(http://.*?)\\'\\)").getMatch(0);
                }
            }
        }
        return finallink;
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
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

    private void getPage(final String page) throws Exception {
        br.getPage(page);
        correctBR();
    }

    @SuppressWarnings("unused")
    private void postPage(final String page, final String postdata) throws Exception {
        br.postPage(page, postdata);
        correctBR();
    }

    private void sendForm(final Form form) throws Exception {
        br.submitForm(form);
        correctBR();
    }

    private void waitTime(long timeBefore, final DownloadLink downloadLink) throws PluginException {
        int passedTime = (int) ((System.currentTimeMillis() - timeBefore) / 1000) - 1;
        /** Ticket Time */
        final String ttt = new Regex(correctedBR, "id=\"countdown_str\">[^<>\"]+<span id=\"[^<>\"]+\"( class=\"[^<>\"]+\")?>([\n ]+)?(\\d+)([\n ]+)?</span>").getMatch(2);
        if (ttt != null) {
            int tt = Integer.parseInt(ttt);
            tt -= passedTime;
            logger.info("Waittime detected, waiting " + ttt + " - " + passedTime + " seconds from now on...");
            if (tt > 0) {
                sleep(tt * 1000l, downloadLink);
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
        Form[] workaround = Form.getForms(correctedBR);
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
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     */
    private boolean inValidate(final String s) {
        if (s == null || s != null && (s.matches("[\r\n\t ]+") || s.equals(""))) {
            return true;
        } else {
            return false;
        }
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
            // when partial match of filename exists. eg cut off by quotation mark miss match, or orgNameExt has been abbreviated by hoster.
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
            // so we know handlePassword triggered without any form
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
                // handle password has failed in the past, additional try catching / resetting values
                logger.warning("Wrong password, the entered password \"" + passCode + "\" is wrong, retrying...");
                passCode = null;
                theLink.setProperty("pass", Property.NULL);
                throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
            }
            if (correctedBR.contains("Wrong captcha")) {
                logger.warning("Wrong captcha!");
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            if (correctedBR.contains("\">Skipped countdown<")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Fatal countdown error (countdown skipped)");
            }
        }
        /** Wait time reconnect handling */
        if (new Regex(correctedBR, "(You have reached the download(\\-| )limit|You have to wait)").matches()) {
            // adjust this regex to catch the wait time string for COOKIE_HOST
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
                /** Not enough wait time to reconnect->Wait and try again */
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
                try {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
                } catch (final Throwable e) {
                    if (e instanceof PluginException) {
                        throw (PluginException) e;
                    }
                }
                throw new PluginException(LinkStatus.ERROR_FATAL, PREMIUMONLY2);
            }
        }
        if (br.getURL().contains("/?op=login&redirect=")) {
            logger.info("Only downloadable via premium");
            throw new PluginException(LinkStatus.ERROR_FATAL, PREMIUMONLY2);
        }
        if (new Regex(correctedBR, MAINTENANCE).matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, MAINTENANCEUSERTEXT, 2 * 60 * 60 * 1000l);
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
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error (404)", 30 * 60 * 1000l);
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(account, true);
        } catch (final PluginException e) {
            account.setValid(false);
            throw e;
        }
        final String space[] = new Regex(correctedBR, ">Used space:</td>.*?<td.*?b>([0-9\\.]+) ?(KB|MB|GB|TB)?</b>").getRow(0);
        if ((space != null && space.length != 0) && (space[0] != null && space[1] != null)) {
            // free users it's provided by default
            ai.setUsedSpace(space[0] + " " + space[1]);
        } else if ((space != null && space.length != 0) && space[0] != null) {
            // premium users the Mb value isn't provided for some reason...
            ai.setUsedSpace(space[0] + "Mb");
        }
        account.setValid(true);
        final String availabletraffic = new Regex(correctedBR, "Traffic available.*?:</TD><TD><b>([^<>\"\\']+)</b>").getMatch(0);
        if (availabletraffic != null && !availabletraffic.contains("nlimited") && !availabletraffic.equalsIgnoreCase(" Mb")) {
            availabletraffic.trim();
            // need to set 0 traffic left, as getSize returns positive result, even when negative value supplied.
            if (!availabletraffic.startsWith("-")) {
                ai.setTrafficLeft(SizeFormatter.getSize(availabletraffic));
            } else {
                ai.setTrafficLeft(0);
            }
        } else {
            ai.setUnlimitedTraffic();
        }
        // If the account is expired we'll accept it as a free account
        final String expire = new Regex(correctedBR, "(\\d{1,2} (January|February|March|April|May|June|July|August|September|October|November|December) \\d{4})").getMatch(0);
        long expiretime = 0;
        if (expire != null) {
            expiretime = TimeFormatter.getMilliSeconds(expire, "dd MMMM yyyy", Locale.ENGLISH);
        }
        if (account.getBooleanProperty("nopremium") && (expiretime - System.currentTimeMillis()) <= 0) {
            // free accounts can still have captcha.
            account.setMaxSimultanDownloads(ACCOUNT_FREE_MAXDOWNLOADS);
            account.setConcurrentUsePossible(false);
            ai.setStatus("Free Account");
        } else {
            ai.setValidUntil(expiretime);
            account.setMaxSimultanDownloads(ACCOUNT_PREMIUM_MAXDOWNLOADS);
            account.setConcurrentUsePossible(true);
            ai.setStatus("Premium Account");
        }
        return ai;
    }

    @SuppressWarnings("unchecked")
    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                /** Load cookies */
                br.setCookiesExclusive(true);
                prepBrowser(br);
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
                getPage(COOKIE_HOST + "/login.html");
                final String lang = System.getProperty("user.language");
                final Form loginform = br.getFormbyProperty("name", "FL");
                if (loginform == null) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                loginform.put("login", Encoding.urlEncode(account.getUser()));
                loginform.put("password", Encoding.urlEncode(account.getPass()));
                sendForm(loginform);
                if (br.getCookie(COOKIE_HOST, "login") == null || br.getCookie(COOKIE_HOST, "xfss") == null) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                if (!br.getURL().contains("/?op=my_account")) {
                    getPage("/?op=my_account");
                }
                if (!new Regex(correctedBR, "(Premium(\\-| )Account expire|>Renew premium<)").matches()) {
                    account.setProperty("nopremium", true);
                } else {
                    account.setProperty("nopremium", false);
                }
                /** Save cookies */
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = this.br.getCookies(COOKIE_HOST);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

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
                    Form dlform = br.getFormbyProperty("name", "F1");
                    if (dlform != null && new Regex(correctedBR, PASSWORDTEXT).matches()) {
                        passCode = handlePassword(dlform, downloadLink);
                    }
                    checkErrors(downloadLink, true);
                    if (dlform == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    sendForm(dlform);
                    checkErrors(downloadLink, true);
                    dllink = getDllink();
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
                int timesFailed = downloadLink.getIntegerProperty(NICE_HOSTproperty + "failedtimes_dllinknofile", 0);
                downloadLink.getLinkStatus().setRetryCount(0);
                if (timesFailed <= 2) {
                    logger.info(NICE_HOST + ": Final link is no file -> Retrying");
                    timesFailed++;
                    downloadLink.setProperty(NICE_HOSTproperty + "failedtimes_dllinknofile", timesFailed);
                    throw new PluginException(LinkStatus.ERROR_RETRY, "Final download link not found");
                } else {
                    downloadLink.setProperty(NICE_HOSTproperty + "failedtimes_dllinknofile", Property.NULL);
                    logger.info(NICE_HOST + ": Final link is no file -> Plugin is broken");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
            fixFilename(downloadLink);
            downloadLink.setProperty("premlink", dllink);
            dl.startDownload();
        }
    }

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