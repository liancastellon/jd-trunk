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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.components.SiteType.SiteTemplate;

import org.appwork.utils.IO;
import org.appwork.utils.StringUtils;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.antiDDoSForHost;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.plugins.components.kvs.Script;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { /** Please add new entries to the END of this array! */
"kernel-video-sharing.com", "hotmovs.com", "cartoontube.xxx", "theclassicporn.com", "alphaporno.com", "updatetube.com", "thenewporn.com", "pinkrod.com", "hotshame.com", "tubewolf.com", "voyeurhit.com", "yourlust.com", "pornicom.com", "pervclips.com", "wankoz.com", "tubecup.com", "myxvids.com", "hellporno.com", "h2porn.com", "gayfall.com", "finevids.xxx", "freepornvs.com", "mylust.com", "pornoid.com", "pornwhite.com", "sheshaft.com", "tryboobs.com", "tubepornclassic.com", "vikiporn.com", "fetishshrine.com", "katestube.com", "sleazyneasy.com", "yeswegays.com", "wetplace.com", "xbabe.com", "hdzog.com", "sex3.com", "bravoteens.com", "yoxhub.com", "xxxymovies.com", "bravotube.net", "upornia.com", "xcafe.com", "txxx.com", "pornpillow.com", "anon-v.com", "hclips.com", "faptube.com", "vjav.com", "shameless.com", "evilhub.com", "japan-whores.com", "needgayporn.com", "pornyeah.com",
        "javwhores.com", "analdin.com", "onlygayvideo.com", "bravoporn.com" }, urls = { /**
 * Please add new entries to the END of this array!
 */
"https?://(?:www\\.)?kvs\\-demo\\.com/videos/[a-z0-9\\-]+/?", "https?://(?:www\\.)?hotmovs\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?cartoontube\\.xxx/video\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?theclassicporn\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?alphaporno\\.com/videos/[a-z0-9\\-]+/?", "https?://(?:www\\.)?updatetube\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?thenewporn\\.com/videos/\\d+/[a-z0-9\\-]+", "https?://(?:www\\.)?pinkrod\\.com/videos/\\d+/[a-z0-9\\-]+", "https?://(?:www\\.)?hotshame\\.com/videos/\\d+/[a-z0-9\\-]+", "https?://(?:www\\.)?tubewolf\\.com/movies/[a-z0-9\\-]+", "https?://(?:www\\.)?voyeurhit\\.com/videos/[a-z0-9\\-]+", "https?://(?:www\\.)?yourlust\\.com/videos/[a-z0-9\\-]+\\.html", "https?://(?:www\\.)?pornicom\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?pervclips\\.com/tube/videos/[^<>\"/]+/?",
        "https?://(?:www\\.|m\\.)?wankoz\\.com/videos/\\d+/[a-z0-9\\-_]+/?", "https?://(?:www\\.)?tubecup\\.com/(?:videos/\\d+/[a-z0-9\\-_]+/|embed/\\d+)", "https?://(?:www\\.)?myxvids\\.com/(videos/\\d+/[a-z0-9\\-_]+/|embed/\\d+)", "https?://(?:www\\.)?hellporno\\.com/videos/[a-z0-9\\-]+/?", "https?://(?:www\\.)?h2porn\\.com/videos/[a-z0-9\\-]+/?", "https?://(?:www\\.)?gayfall\\.com/videos/[a-z0-9\\-]+/?", "https?://(?:www\\.)?finevids\\.xxx/videos/\\d+/[a-z0-9\\-]+", "https?://(?:www\\.)?freepornvs\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?mylust\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?pornoid\\.com/videos/\\d+/[a-z0-9\\-]+", "https?://(?:www\\.)?pornwhite\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?sheshaft\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?tryboobs\\.com/videos/\\d+/[a-z0-9\\-]+/?",
        "https?://(?:\\w+\\.)?tubepornclassic\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?vikiporn\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?fetishshrine\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?katestube\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?sleazyneasy\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?yeswegays\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?wetplace\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(www\\.)?xbabe\\.com/videos/[a-z0-9\\-]+/?", "https?://(?:www\\.)?hdzog\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(www\\.)?sex3\\.com/\\d+/?", "https?://(?:www\\.)?bravoteens\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?yoxhub\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?xxxymovies\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?bravotube\\.net/videos/[a-z0-9\\-]+",
        "https?://(?:www\\.)?upornia\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://xcafe\\.com/\\d+/?", "https?://(?:www\\.)?txxx\\.com/videos/\\d+/[a-z0-9\\-]+/|(https?://(?:www\\.)?txxx\\.com/embed/\\d+)", "https?://(?:www\\.)?pornpillow\\.com/\\d+/[^/]+\\.html", "https?://(?:www\\.)?anon\\-v\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?hclips\\.com/videos/[a-z0-9\\-]+/?", "https?://(?:www\\.)?faptube\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://([a-z]{2,3}\\.)?vjav\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?shameless\\.com/videos/[a-z0-9\\-]+/?", "https?://(?:www\\.)?evilhub\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?japan\\-whores\\.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?needgayporn.com/videos/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?pornyeah\\.com/videos/[a-z0-9\\-]+\\-\\d+\\.html",
        "https?://(?:www\\.)?javwhores\\.com/video/\\d+/[a-z0-9\\-]+/?", "https?://(?:www\\.)?analdin\\.com/videos/\\d+/[a-z0-9\\-]+/", "https?://(?:www\\.)?onlygayvideo\\.com/videos/\\d+/[a-z0-9\\-]+/", "https?://(?:www\\.)?bravoporn\\.com/videos/\\d+/(?:[a-z0-9\\-]+/)?" })
public class KernelVideoSharingCom extends antiDDoSForHost {
    public KernelVideoSharingCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    /* Porn_plugin */
    // Version 1.0
    // Tags:
    // protocol: no https
    // other: URL to a live demo: http://www.kvs-demo.com/
    // other #2: Special websites that have their own plugins (examples): pornktube.com, alotporn.com, xfig.net, clipcake.com
    // other #3: Plugins with "high security" removed 2015-07-02: BravoTubeNet, BravoTeensCom
    // other #3: h2porn.com: Added without security stuff 2015-11-03 REV 29387
    // TODO: Check if it is possible to get nice filenames for embed-urls as well
    /**
     * specifications that have to be met for hosts to be added here:
     *
     * -404 error response on file not found
     *
     * -Possible filename inside URL
     *
     * -No serverside downloadlimits
     *
     * -No account support
     *
     * -Final downloadlink that fits the RegExes
     *
     * -Website should NOT link to external sources (needs decrypter)
     *
     */
    /* Connection stuff */
    private static final boolean free_resume       = true;
    private static final int     free_maxchunks    = 0;
    private static final int     free_maxdownloads = -1;
    /* E.g. normal kernel-video-sharing.com video urls */
    private static final String  type_normal       = "^https?://.+/(videos/)?(?:\\d+/)?[a-z0-9\\-]+(/?|\\.html)$";
    private static final String  type_mobile       = "^https?://m\\.([^/]+/(videos/)?\\d+/[a-z0-9\\-]+/$)";
    /* E.g. sex3.com, */
    public static final String   type_only_numbers = "^https?://[^/]+/(?:video/)?\\d+/$";
    /* E.g. myxvids.com */
    public static final String   type_embedded     = "^https?://(?:www\\.)?[^/]+/embed/\\d+/?$";
    private String               dllink            = null;
    private boolean              isDownload        = false;
    private boolean              server_issues     = false;

    @Override
    public String getAGBLink() {
        return "http://www.kvs-demo.com/terms.php";
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        if (link.getDownloadURL().matches(type_mobile)) {
            /* Correct mobile urls --> Normal URLs */
            final Regex info = new Regex(link.getDownloadURL(), "^(https?://)m\\.([^/]+/(videos/)?\\d+/[a-z0-9\\-]+/$)");
            link.setUrlDownload(String.format("%swww.%s", info.getMatch(0), info.getMatch(1)));
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        dllink = null;
        server_issues = false;
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        downloadLink.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
        getPage(downloadLink.getDownloadURL());
        final String filename_url = regexURLFilenameAuto(this.br, downloadLink);
        if (br.containsHTML("KernelTeamVideoSharingSystem\\.js|KernelTeamImageRotator_")) {
            /* <script src="/js/KernelTeamImageRotator_3.8.1.jsx?v=3"></script> */
            /* <script type="text/javascript" src="http://www.hclips.com/js/KernelTeamVideoSharingSystem.js?v=3.8.1"></script> */
        }
        String filename = regexFilenameAuto(br, downloadLink);
        if (inValidate(filename_url)) {
            /* This should never happen */
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (br.getHttpConnection().getResponseCode() == 404 || br.getURL().contains("/404.php")) {
            /* Definitly offline - set url filename to avoid bad names! */
            downloadLink.setName(filename_url);
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        downloadLink.setName(filename);
        dllink = getDllink(br, this);
        final String ext;
        if (dllink != null && !dllink.contains(".m3u8")) {
            ext = getFileNameExtensionFromString(dllink, ".mp4");
        } else {
            /* Fallback */
            ext = ".mp4";
        }
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        downloadLink.setFinalFileName(filename);
        // this prevents another check when download is about to happen! -raztoki
        if (isDownload) {
            return AvailableStatus.TRUE;
        }
        if (dllink != null && !dllink.contains(".m3u8")) {
            URLConnectionAdapter con = null;
            try {
                // if you don't do this then referrer is fked for the download! -raztoki
                final Browser br = this.br.cloneBrowser();
                // In case the link redirects to the finallink -
                br.setFollowRedirects(true);
                try {
                    con = br.openHeadConnection(dllink);
                    final String workaroundURL = getHttpServerErrorWorkaroundURL(br.getHttpConnection());
                    if (workaroundURL != null) {
                        con = br.openHeadConnection(workaroundURL);
                    }
                } catch (final BrowserException e) {
                    server_issues = true;
                    return AvailableStatus.TRUE;
                }
                final long filesize = con.getLongContentLength();
                if (!con.getContentType().contains("html") && filesize > 100000) {
                    downloadLink.setDownloadSize(filesize);
                    final String redirect_url = br.getHttpConnection().getRequest().getUrl();
                    if (redirect_url != null) {
                        dllink = redirect_url;
                        logger.info("dllink: " + dllink);
                    }
                } else {
                    server_issues = true;
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        isDownload = true;
        requestFileInformation(downloadLink);
        if (inValidate(dllink, this)) {
            /* 2016-12-02: At this stage we should have a working hls to http workaround so we should never get hls urls. */
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else if (br.getHttpConnection().getResponseCode() == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
        } else if (br.getHttpConnection().getResponseCode() == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
        } else if (server_issues) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 10 * 60 * 1000l);
        }
        if (this.dllink.contains(".m3u8")) {
            /* hls download */
            /* Access hls master. */
            getPage(this.dllink);
            if (br.getHttpConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            }
            final HlsContainer hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(br));
            if (hlsbest == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            checkFFmpeg(downloadLink, "Download a HLS Stream");
            dl = new HLSDownloader(downloadLink, br, hlsbest.getDownloadurl());
            dl.startDownload();
        } else {
            /* http download */
            dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, dllink, free_resume, free_maxchunks);
            final String workaroundURL = getHttpServerErrorWorkaroundURL(dl.getConnection());
            if (workaroundURL != null) {
                dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, workaroundURL, free_resume, free_maxchunks);
            }
            if (dl.getConnection().getContentType().contains("html")) {
                if (dl.getConnection().getResponseCode() == 403) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
                } else if (dl.getConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
                } else if (dl.getConnection().getResponseCode() == 503) {
                    /* Should only happen in rare cases */
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Server error 503 connection limit reached", 5 * 60 * 1000l);
                }
                br.followConnection();
                try {
                    dl.getConnection().disconnect();
                } catch (final Throwable e) {
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        }
    }

    public static String getHttpServerErrorWorkaroundURL(final URLConnectionAdapter con) {
        String workaroundURL = null;
        if (con.getResponseCode() == 403 || con.getResponseCode() == 404) {
            /*
             * Small workaround for buggy servers that redirect and fail if the Referer is wrong then. Examples: hdzog.com (404), txxx.com
             * (403)
             */
            workaroundURL = con.getRequest().getUrl();
        }
        return workaroundURL;
    }

    public static String getDllink(final Browser br, final Plugin plugin) throws PluginException {
        /*
         * Newer KVS versions also support html5 --> RegEx for that as this is a reliable source for our final downloadurl.They can contain
         * the old "video_url" as well but it will lead to 404 --> Prefer this way.
         * 
         * E.g. wankoz.com, pervclips.com, pornicom.com
         */
        String dllink = null;
        final String json_playlist_source = br.getRegex("sources\\s*?:\\s*?(\\[.*?\\])").getMatch(0);
        String httpurl_temp = null;
        if (json_playlist_source != null) {
            /* 2017-03-16: E.g. txxx.com */
            /* TODO: Eventually improve this */
            // see if there are non hls streams first. since regex does this based on first in entry of source == =[ raztoki20170507
            dllink = new Regex(json_playlist_source, "'file'\\s*?:\\s*?'((?!.*\\.m3u8)http[^<>\"']*?(mp4|flv)[^<>\"']*?)'").getMatch(0);
            if (inValidate(dllink, plugin)) {
                dllink = new Regex(json_playlist_source, "'file'\\s*?:\\s*?'(http[^<>\"']*?(mp4|flv|m3u8)[^<>\"']*?)'").getMatch(0);
            }
        }
        if (inValidate(dllink, plugin)) {
            dllink = br.getRegex("flashvars\\['video_html5_url'\\]='(http[^<>\"]*?)'").getMatch(0);
        }
        if (inValidate(dllink, plugin)) {
            /* E.g. yourlust.com */
            dllink = br.getRegex("flashvars\\.video_html5_url\\s*=\\s*\"(http[^<>\"]*?)\"").getMatch(0);
        }
        if (inValidate(dllink, plugin)) {
            // xxxymovies.com
            dllink = br.getRegex("video_url\\s*:\\s*'((?:http|/)[^<>\"']*?)'").getMatch(0);
        }
        if (inValidate(dllink, plugin)) {
            // HD javwhores
            dllink = br.getRegex("video_alt_url\\s*:\\s*\\'((?:http|/)[^<>\"]*?)\\'").getMatch(0);
        }
        if (inValidate(dllink, plugin)) {
            /* RegEx for "older" KVS versions, example for website with relative URL: sex3.com */
            dllink = br.getRegex("video_url\\s*:\\s*\\'((?:http|/)[^<>\"]*?)\\'").getMatch(0);
        }
        if (inValidate(dllink, plugin)) {
            // function/0/http camwheres, pornyeah
            dllink = br.getRegex("(function/0/https?://[A-Za-z0-9\\.\\-]+/get_file/[^<>\"]*?)(?:\\&amp|'|\")").getMatch(0);
            if (dllink != null) {
                dllink = getDllinkCrypted(br, dllink);
            } else {
                if (br.containsHTML("function/0/http")) {
                    dllink = br.getRegex("(function/0/http[^']+)'").getMatch(0);
                    System.out.println("dllink: " + dllink);
                }
            }
        }
        if (inValidate(dllink, plugin)) {
            /* Last change: 2017-08-03, regarding txxx.com */
            // dllink = br.getRegex("(https?://[A-Za-z0-9\\.\\-]+/get_file/[^<>\"]*?)(?:\\&amp|'|\")").getMatch(0);
            dllink = br.getRegex("(https?://[A-Za-z0-9\\.\\-]+/get_file/[^<>\"]*?)(?:'|\")").getMatch(0); // 2018-06-20
            if (StringUtils.endsWithCaseInsensitive(dllink, "jpg/")) {
                dllink = null;
            }
        }
        if (inValidate(dllink, plugin)) {
            dllink = br.getRegex("(?:file|video)\\s*?:\\s*?(?:\"|')(http[^<>\"\\']*?\\.(?:m3u8|mp4|flv)[^<>\"]*?)(?:\"|')").getMatch(0);
        }
        if (inValidate(dllink, plugin)) {
            dllink = br.getRegex("(?:file|url):[\t\n\r ]*?(\"|')(http[^<>\"\\']*?\\.(?:m3u8|mp4|flv)[^<>\"]*?)\\1").getMatch(1);
        }
        if (inValidate(dllink, plugin)) {
            dllink = br.getRegex("<source src=\"(https?://[^<>\"]*?)\" type=(\"|')video/(?:mp4|flv)\\2").getMatch(0);
        }
        if (inValidate(dllink, plugin)) { // tryboobs.com
            dllink = br.getRegex("<video src=\"(https?://[^<>\"]*?)\" controls").getMatch(0);
        }
        if (inValidate(dllink, plugin)) {
            dllink = br.getRegex("property=\"og:video\" content=\"(http[^<>\"]*?)\"").getMatch(0);
        }
        if (inValidate(dllink, plugin)) {
            /* 2016-11-01 - bravotube.net */
            dllink = br.getRegex("<source src=\"([^<>\"]*?)\"").getMatch(0);
        }
        if (inValidate(dllink, plugin)) {
            /* 2018-03-12 - wankoz.com */
            dllink = br.getRegex("source[^']+src:\\s*'([^']*?)'").getMatch(0);
        }
        if (dllink != null && dllink.contains(".m3u8")) {
            /* 2016-12-02 - txxx.com, tubecup.com, hdzog.com */
            /* Prefer httpp over hls */
            try {
                /* First try to find highest quality */
                final String fallback_player_json = br.getRegex("\\.on\\(\\'setupError\\',function\\(\\)\\{[^>]*?jwsettings\\.playlist\\[0\\]\\.sources=(\\[.*?\\])").getMatch(0);
                final String[][] qualities = new Regex(fallback_player_json, "\\'label\\'\\s*?:\\s*?\\'(\\d+)p\\',\\s*?\\'file\\'\\s*?:\\s*?\\'(http[^<>\\']+)\\'").getMatches();
                int quality_max = 0;
                for (final String[] qualityInfo : qualities) {
                    final String quality_temp_str = qualityInfo[0];
                    final String quality_url_temp = qualityInfo[1];
                    final int quality_temp = Integer.parseInt(quality_temp_str);
                    if (quality_temp > quality_max) {
                        quality_max = quality_temp;
                        httpurl_temp = quality_url_temp;
                    }
                }
            } catch (final Throwable e) {
            }
            /* Last chance */
            if (httpurl_temp == null) {
                httpurl_temp = br.getRegex("\\.on\\(\\'setupError\\',function\\(\\)\\{[^>]*?\\'file\\'\\s*?:\\s*?\\'(http[^<>\"\\']*?\\.mp4[^<>\"\\']*?)\\'").getMatch(0);
            }
            if (httpurl_temp != null) {
                /* Prefer http over hls */
                dllink = httpurl_temp;
            }
        }
        if (inValidate(dllink, plugin)) {
            String video_url = br.getRegex("var\\s+video_url\\s*=\\s*(\"|')(.*?)(\"|')\\s*;").getMatch(1);
            if (video_url == null) {
                video_url = br.getRegex("var\\s+video_url=Dpww3Dw64\\(\"([^\"]+)").getMatch(0);
            }
            // hdzog.com, hclips.com
            String video_url_append = br.getRegex("video_url\\s*\\+=\\s*(\"|')(.*?)(\"|')\\s*;").getMatch(1);
            if (video_url != null && video_url_append != null) {
                video_url += video_url_append;
            }
            if (video_url != null) {
                String[] videoUrlSplit = video_url.split("\\|\\|");
                video_url = videoUrlSplit[0];
                if (!video_url.startsWith("http")) {
                    final ScriptEngineManager manager = JavaScriptEngineFactory.getScriptEngineManager(null);
                    final ScriptEngine engine = manager.getEngineByName("javascript");
                    final Invocable inv = (Invocable) engine;
                    try {
                        engine.eval(IO.readURLToString(Script.class.getResource("script.js")));
                        final Object result = inv.invokeFunction("result", video_url);
                        if (result != null) {
                            dllink = result.toString();
                        }
                    } catch (final Throwable e) {
                        plugin.getLogger().log(e);
                    }
                } else {
                    dllink = video_url;
                }
                if (videoUrlSplit.length == 2) {
                    dllink = dllink.replaceFirst("/get_file/\\d+/[0-9a-z]{32}/", videoUrlSplit[1]);
                } else if (videoUrlSplit.length == 4) {
                    dllink = dllink.replaceFirst("/get_file/\\d+/[0-9a-z]{32}/", videoUrlSplit[1]);
                    dllink = dllink + "&lip=" + videoUrlSplit[2];
                    dllink = dllink + "&lt=" + videoUrlSplit[3];
                }
                return dllink;
            }
        }
        if (inValidate(dllink, plugin)) {
            if (!br.containsHTML("license_code:") && !br.containsHTML("kt_player_[0-9\\.]+\\.swfx?")) {
                /* No licence key present in html and/or no player --> No video --> Offline */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        // dllink = Encoding.htmlDecode(dllink); - Why?
        dllink = Encoding.urlDecode(dllink, true);
        if (dllink.contains("&amp;")) {
            dllink = Encoding.htmlDecode(dllink);
        }
        return dllink;
    }

    public static String getDllinkCrypted(final Browser br) {
        String videoUrl = br.getRegex("video_url\\s*?:\\s*?\\'(.+?)\\'").getMatch(0);
        if (videoUrl == null) {
            return null;
        }
        return getDllinkCrypted(br, videoUrl);
    }

    public static String getDllinkCrypted(final Browser br, final String videoUrl) {
        String dllink = null;
        // final String scriptUrl = br.getRegex("src=\"([^\"]+kt_player\\.js.*?)\"").getMatch(0);
        final String licenseCode = br.getRegex("license_code\\s*?:\\s*?\\'(.+?)\\'").getMatch(0);
        if (videoUrl.startsWith("function")) {
            if (videoUrl != null && licenseCode != null) {
                // final Browser cbr = br.cloneBrowser();
                // cbr.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
                // cbr.getPage(scriptUrl);
                // final String hashRange = cbr.getRegex("(\\d+)px").getMatch(0);
                String hashRange = "16";
                dllink = decryptHash(videoUrl, licenseCode, hashRange);
            }
        } else {
            dllink = videoUrl;
        }
        return dllink;
    }

    private static String decryptHash(final String videoUrl, final String licenseCode, final String hashRange) {
        String result = null;
        List<String> videoUrlPart = new ArrayList<String>();
        Collections.addAll(videoUrlPart, videoUrl.split("/"));
        // hash
        String hash = videoUrlPart.get(7).substring(0, 2 * Integer.parseInt(hashRange));
        String nonConvertHash = videoUrlPart.get(7).substring(2 * Integer.parseInt(hashRange));
        String seed = calcSeed(licenseCode, hashRange);
        String[] seedArray = new String[seed.length()];
        for (int i = 0; i < seed.length(); i++) {
            seedArray[i] = seed.substring(i, i + 1);
        }
        if (seed != null && hash != null) {
            for (int k = hash.length() - 1; k >= 0; k--) {
                String[] hashArray = new String[hash.length()];
                for (int i = 0; i < hash.length(); i++) {
                    hashArray[i] = hash.substring(i, i + 1);
                }
                int l = k;
                for (int m = k; m < seedArray.length; m++) {
                    l += Integer.parseInt(seedArray[m]);
                }
                for (; l >= hashArray.length;) {
                    l -= hashArray.length;
                }
                StringBuffer n = new StringBuffer();
                for (int o = 0; o < hashArray.length; o++) {
                    n.append(o == k ? hashArray[l] : o == l ? hashArray[k] : hashArray[o]);
                }
                hash = n.toString();
            }
            videoUrlPart.set(7, hash + nonConvertHash);
            for (String string : videoUrlPart.subList(2, videoUrlPart.size())) {
                if (result == null) {
                    result = string;
                } else {
                    result = result + "/" + string;
                }
            }
        }
        return result;
    }

    private static String calcSeed(final String licenseCode, final String hashRange) {
        StringBuffer fb = new StringBuffer();
        String[] licenseCodeArray = new String[licenseCode.length()];
        for (int i = 0; i < licenseCode.length(); i++) {
            licenseCodeArray[i] = licenseCode.substring(i, i + 1);
        }
        for (String c : licenseCodeArray) {
            if (c.equals("$")) {
                continue;
            }
            int v = Integer.parseInt(c);
            fb.append(v != 0 ? c : "1");
        }
        String f = fb.toString();
        int j = f.length() / 2;
        int k = Integer.parseInt(f.substring(0, j + 1));
        int l = Integer.parseInt(f.substring(j));
        int g = l - k;
        g = Math.abs(g);
        int fi = g;
        g = k - l;
        g = Math.abs(g);
        fi += g;
        fi *= 2;
        String s = String.valueOf(fi);
        String[] fArray = new String[s.length()];
        for (int i = 0; i < s.length(); i++) {
            fArray[i] = s.substring(i, i + 1);
        }
        int i = Integer.parseInt(hashRange) / 2 + 2;
        StringBuffer m = new StringBuffer();
        for (int g2 = 0; g2 < j + 1; g2++) {
            for (int h = 1; h <= 4; h++) {
                int n = Integer.parseInt(licenseCodeArray[g2 + h]) + Integer.parseInt(fArray[g2]);
                if (n >= i) {
                    n -= i;
                }
                m.append(String.valueOf(n));
            }
        }
        return m.toString();
    }

    public static String getURL_source(final Browser br, final DownloadLink dl) {
        if (br == null || dl == null) {
            return null;
        }
        final String url_source;
        final String current_url = br.getURL();
        if (current_url != null && current_url.length() > dl.getDownloadURL().length() && current_url.matches(type_normal)) {
            url_source = current_url;
        } else {
            url_source = dl.getDownloadURL();
        }
        return url_source;
    }

    /**
     * Finds title inside a KVS URL. <br />
     * Automatically decides whether to use the current Browsers' URL or the original source URL added by the user.
     */
    public static String regexURLFilenameAuto(final Browser br, final DownloadLink dl) {
        if (br == null || dl == null) {
            return null;
        }
        final String url_source = getURL_source(br, dl);
        return regexURLFilename(url_source);
    }

    /**
     * Finds title inside a given KVS URL. <br />
     */
    public static String regexURLFilename(final String url_source) {
        if (url_source == null) {
            return null;
        }
        String filename_url = regexURLFilenameSiteSpecific(url_source);
        if (StringUtils.isEmpty(filename_url)) {
            /* Find filename inside our URL. */
            if (url_source.matches(type_only_numbers)) {
                filename_url = new Regex(url_source, "(\\d+)/?").getMatch(0);
            } else if (url_source.matches(type_embedded)) {
                filename_url = new Regex(url_source, "(\\d+)/?").getMatch(0);
            } else {
                filename_url = new Regex(url_source, "(?:videos|movies)/(?:\\d+/)?([a-z0-9\\-]+)(?:/?|\\.html)$").getMatch(0);
                if (filename_url == null) {
                    /* Last chance fallback: auto-url-filename */
                    String url_part = new Regex(url_source, "https?://[^/]+/(.+)").getMatch(0);
                    url_part = url_part.replace(".html", "");
                    url_part = url_part.replace(".htm", "");
                    url_part = url_part.replace("/", "_");
                    filename_url = url_part;
                }
            }
        }
        if (!StringUtils.isEmpty(filename_url)) {
            /* Make the url-filenames look a bit better by using spaces instead of '-'. */
            filename_url = filename_url.replace("-", " ");
        }
        return filename_url;
    }

    /**
     * Uses site-specific RegExes to find the URLFilename (if one is available for current website). <br />
     * 2017-07-27: So far there are no site-specific RegExes for URLFilenames.
     */
    public static String regexURLFilenameSiteSpecific(final String url) {
        if (url == null) {
            return null;
        }
        final String host = Browser.getHost(url);
        if (host == null) {
            return null;
        }
        final String filename = null;
        return filename;
    }

    /**
     * Tries everything possible to find a nice filename for KVS websites. <br />
     * Returns url_filename as fallback.
     */
    public static String regexFilenameAuto(final Browser br, final DownloadLink dl) {
        String filename;
        final String url_source = getURL_source(br, dl);
        /* Find 'real' filename and the one inside our URL. */
        if (url_source.matches(type_only_numbers)) {
            filename = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
        } else if (url_source.matches(type_embedded)) {
            filename = br.getRegex("<title>([^<>\"]*?) / Embed Player</title>").getMatch(0);
            if (StringUtils.isEmpty(filename)) {
                /* Filename from decrypter */
                filename = dl.getStringProperty("filename", null);
            }
            if (StringUtils.isEmpty(filename)) {
                filename = new Regex(dl.getDownloadURL(), "(\\d+)/?$").getMatch(0);
            }
        } else {
            filename = regexFilenameSiteSpecific(br);
            if (StringUtils.isEmpty(filename)) {
                filename = regexFilenameGeneral(br);
            }
            if (StringUtils.isEmpty(filename)) {
                filename = regexStandardTitleWithHost(br, br.getHost());
            }
        }
        if (StringUtils.isEmpty(filename)) {
            filename = regexURLFilenameAuto(br, dl);
        } else {
            /* Remove html crap and spaces at the beginning and end. */
            filename = Encoding.htmlDecode(filename);
            filename = filename.trim();
        }
        return filename;
    }

    public static String regexFilenameGeneral(final Browser br) {
        /* Works e.g. for hdzog.com */
        String filename = br.getRegex("var video_title\\s*?=\\s*?\"([^<>]*?)\";").getMatch(0);
        if (StringUtils.isEmpty(filename)) { // tryboobs.com
            filename = br.getRegex("data-title=\"(.*?)\"").getMatch(0);
        }
        if (StringUtils.isEmpty(filename)) {
            /* Newer KVS e.g. tubecup.com */
            filename = br.getRegex("title[\t\n\r ]*?:[\t\n\r ]*?\"([^<>\"]*?)\"").getMatch(0);
        }
        if (StringUtils.isEmpty(filename)) {
            filename = br.getRegex("<h\\d+ class=\"album_title\">([^<>]*?)<").getMatch(0);
        }
        if (StringUtils.isEmpty(filename)) {
            filename = br.getRegex("<meta property=\"og:title\" content=\"(?:Free Porn Videos \\| )?([^<>\"]*?)\"").getMatch(0);
        }
        if (StringUtils.isEmpty(filename)) {
            filename = br.getRegex("\"media-title\"\\s*>\\s*(.*?)\\s*<").getMatch(0);
        }
        if (StringUtils.isEmpty(filename)) {
            /* Fails e.g. for alphaporno.com */
            filename = br.getRegex("<h\\d+ class=\"title\">([^<>\"]*?)<").getMatch(0);
        }
        if (StringUtils.isEmpty(filename)) {
            /* Working e.g. for wankoz.com */
            filename = br.getRegex("<h\\d+ class=\"block_header\" id=\"desc_button\">([^<>\"]*?)</h\\d+>").getMatch(0);
        }
        if (StringUtils.isEmpty(filename)) {
            /* Working e.g. for pervclips.com, pornicom.com */
            filename = br.getRegex("class=\"heading video-heading\">[\t\n\r ]+<(h\\d+)>([^<>\"]*?)</h\\1>").getMatch(1);
        }
        if (StringUtils.isEmpty(filename)) {
            /* Working e.g. for voyeurhit.com */
            filename = br.getRegex("<div class=\"info\\-player\">[\t\n\r ]+<h\\d+>([^<>\"]*?)</h\\d+>").getMatch(0);
        }
        if (StringUtils.isEmpty(filename)) {
            /* Working e.g. for japan-whores.com */
            filename = br.getRegex("(?:<h1 itemprop=\")?name\">([^<>]+?)</h1>").getMatch(0);
        }
        return filename;
    }

    /**
     * Uses site-specific RegExes to find the filename (if one is available for current website). <br />
     * Use this first to find the filename for KVS websites!
     */
    public static String regexFilenameSiteSpecific(final Browser br) {
        String filename;
        if (br.getHost().equalsIgnoreCase("yourlust.com")) {
            /* 2016-12-21 */
            filename = br.getRegex("<h\\d+ class=\"[^<>]+>([^<>]*?)<").getMatch(0);
        } else if (br.getHost().equalsIgnoreCase("theclassicporn.com")) {
            /* 2016-12-18 */
            filename = br.getRegex("class=\"link\\-blue link\\-no\\-border\">([^<>\"]*?)<").getMatch(0);
        } else if (br.getHost().equalsIgnoreCase("alphaporno.com")) {
            /* 2017-08-03 */
            filename = br.getRegex("<h1 class=\"title\" itemprop=\"name\">([^<>\"]+)</h1>").getMatch(0);
            if (StringUtils.isEmpty(filename)) {
                filename = br.getRegex("<title>([^<>]+)</title>").getMatch(0);
            }
        } else {
            filename = null;
        }
        return filename;
    }

    /** Many websites in general use this format - title plus their own hostname as ending. */
    public static String regexStandardTitleWithHost(final Browser br, final String host) {
        return br.getRegex(Pattern.compile("<title>([^<>\"]*?) \\- " + host + "</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)).getMatch(0);
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     */
    public static boolean inValidate(final String s, final Plugin plugin) {
        if (s == null || s.matches("\\s+") || s.equals("")) {
            return true;
        } else if (("upornia.com".equals(plugin.getHost()) || "vjav.com".equals(plugin.getHost())) && s.contains("/player/timeline")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_maxdownloads;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.KernelVideoSharing;
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
}
