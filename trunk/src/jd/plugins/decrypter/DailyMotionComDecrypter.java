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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.utils.JDUtilities;

//Decrypts embedded videos from dailymotion
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "dailymotion.com" }, urls = { "https?://(?:www\\.)?dailymotion\\.com/.+" })
public class DailyMotionComDecrypter extends PluginForDecrypt {
    public DailyMotionComDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String                          videoSource       = null;
    /**
     * @ 1hd1080URL or stream_h264_hd1080_url [1920x1080]
     *
     * @ 2 hd720URL or stream_h264_hd_url [1280x720]
     *
     * @ 3 hqURL or stream_h264_hq_url [848x480]
     *
     * @ 4 sdURL or stream_h264_url [512x384]
     *
     * @ 5 ldURL or video_url or stream_h264_ld_url [320x240]
     *
     * @ 6 video_url or rtmp
     *
     * @ 7 hds
     *
     * @String[] = {"Direct download url", "filename, if available before quality selection"}
     */
    private LinkedHashMap<String, String[]> foundQualities    = new LinkedHashMap<String, String[]>();
    private String                          filename          = null;
    private String                          parameter         = null;
    private static final String             ALLOW_BEST        = "ALLOW_BEST";
    private static final String             ALLOW_OTHERS      = "ALLOW_OTHERS";
    public static final String              ALLOW_AUDIO       = "ALLOW_AUDIO";
    private static final String             TYPE_PLAYLIST     = "https?://(?:www\\.)?dailymotion\\.com/playlist/[A-Za-z0-9\\-_]+(?:/\\d+)?.*?";
    private static final String             TYPE_USER         = "https?://(?:www\\.)?dailymotion\\.com/(user/[A-Za-z0-9_\\-]+/\\d+|[^/]+/videos)";
    private static final String             TYPE_USER_SEARCH  = "https?://(?:www\\.)?dailymotion\\.com/.*?/user/[^/]+/search/[^/]+/\\d+";
    private static final String             TYPE_VIDEO        = "https?://(?:www\\.)?dailymotion\\.com/((?:embed/)?video/[^/]+|swf(?:/video)?/[^/]+)";
    /** API limits for: https://developer.dailymotion.com/api#graph-api */
    private static final short              api_limit_items   = 100;
    private static final short              api_limit_pages   = 100;
    public final static boolean             defaultAllowAudio = true;
    private ArrayList<DownloadLink>         decryptedLinks    = new ArrayList<DownloadLink>();
    private boolean                         acc_in_use        = false;
    private static Object                   ctrlLock          = new Object();

    /**
     * JD2 CODE: DO NOIT USE OVERRIDE FÒR COMPATIBILITY REASONS!!!!!
     */
    public boolean isProxyRotationEnabledForLinkCrawler() {
        return false;
    }

    @SuppressWarnings("deprecation")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        parameter = param.toString().replace("embed/video/", "video/").replaceAll("\\.com/swf(/video)?/", ".com/video/").replace("http://", "https://");
        br.setFollowRedirects(true);
        jd.plugins.hoster.DailyMotionCom.prepBrowser(this.br);
        synchronized (ctrlLock) {
            /* Login if account available */
            final PluginForHost dailymotionHosterplugin = JDUtilities.getPluginForHost("dailymotion.com");
            Account aa = AccountController.getInstance().getValidAccount(dailymotionHosterplugin);
            if (aa != null) {
                try {
                    ((jd.plugins.hoster.DailyMotionCom) dailymotionHosterplugin).login(aa, this.br);
                    acc_in_use = true;
                } catch (final PluginException e) {
                    logger.info("Account seems to be invalid -> Continuing without account!");
                }
            }
            /* Login end... */
            br.getPage(parameter);
            /* 404 */
            if (br.containsHTML("(<title>Dailymotion \\– 404 Not Found</title>|url\\(/images/404_background\\.jpg)") || this.br.getHttpConnection().getResponseCode() == 404) {
                final DownloadLink dl = this.createOfflinelink(parameter);
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
            /* 403 */
            if (br.containsHTML("class=\"forbidden\">Access forbidden</h3>|>You don\\'t have permission to access the requested URL") || this.br.getHttpConnection().getResponseCode() == 403) {
                final DownloadLink dl = this.createOfflinelink(parameter);
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
            /* 410 */
            if (br.getHttpConnection().getResponseCode() == 410) {
                final DownloadLink dl = this.createOfflinelink(parameter);
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
            /* video == 'video_item', user == 'user_home' */
            final String route_name = PluginJSonUtils.getJson(this.br, "route_name");
            if (parameter.matches(TYPE_PLAYLIST)) {
                decryptPlaylist();
            } else if (parameter.matches(TYPE_USER) || "user_home".equalsIgnoreCase(route_name)) {
                decryptUser();
            } else if (parameter.matches(TYPE_VIDEO)) {
                decryptSingleVideo(decryptedLinks);
            } else if (parameter.matches(TYPE_USER_SEARCH)) {
                decryptUserSearch();
            } else {
                logger.info("Unsupported linktype: " + parameter);
                return decryptedLinks;
            }
        }
        if (decryptedLinks == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        return decryptedLinks;
    }

    /**
     * Crawls all videos of a user. In some cases it is not possible to crawl all videos due to website- AND API limitations (both have the
     * same limits).
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void decryptUser() throws Exception {
        /*
         * 2019-01-18: The API used in decryptPlaylist can also be used to crawl all videos of a user but as long as this one is working,
         * we'll stick to that.
         */
        logger.info("Decrypting user: " + parameter);
        String username = new Regex(parameter, "dailymotion\\.com/user/([A-Za-z0-9\\-_]+)").getMatch(0);
        if (username == null) {
            username = new Regex(parameter, "dailymotion\\.com/([^/]+)").getMatch(0);
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(username);
        boolean has_more = false;
        int page = 0;
        do {
            page++;
            if (this.isAbort()) {
                logger.info("Decrypt process aborted by user on page " + page);
                return;
            }
            final String json = this.br.cloneBrowser().getPage("https://api.dailymotion.com/user/" + username + "/videos?limit=" + api_limit_items + "&page=" + page);
            LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json);
            has_more = ((Boolean) entries.get("has_more")).booleanValue();
            final ArrayList<Object> list = (ArrayList) entries.get("list");
            for (final Object video_o : list) {
                entries = (LinkedHashMap<String, Object>) video_o;
                final String videoid = (String) entries.get("id");
                if (videoid == null) {
                    logger.warning("Decrypter failed: " + parameter);
                    decryptedLinks = null;
                    return;
                }
                final DownloadLink dl = this.createDownloadlink(createVideolink(videoid));
                dl._setFilePackage(fp);
                this.decryptedLinks.add(dl);
                distribute(dl);
            }
        } while (has_more && page <= api_limit_pages);
        if (decryptedLinks == null) {
            logger.warning("Decrypter failed: " + parameter);
            decryptedLinks = null;
            return;
        }
    }

    private String createVideolink(final String videoID) {
        return String.format("https://www.dailymotion.com/video/%s", videoID);
    }

    private void decryptPlaylist() throws Exception {
        logger.info("Decrypting playlist: " + parameter);
        final String playlist_id = new Regex(this.parameter, "/playlist/([^/]+)").getMatch(0);
        final String player_config_json = br.getRegex("var __PLAYER_CONFIG__ = (\\{.*?\\});</script>").getMatch(0);
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(player_config_json);
        entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "context/api");
        final String client_id = (String) entries.get("client_id");
        final String auth_url = (String) entries.get("auth_url");
        final String client_secret = (String) entries.get("client_secret");
        if (StringUtils.isEmpty(auth_url) || StringUtils.isEmpty(client_id) || StringUtils.isEmpty(client_secret)) {
            logger.warning("Failed to find auth_url, client_id or client_secret");
            throw new DecrypterException();
        }
        // final String client_scope = (String) entries.get("client_scope");
        // final String product_scope = (String) entries.get("product_scope");
        final String postdata = "grant_type=client_credentials&client_secret=" + client_secret + "&client_id=" + client_id;
        br.postPage(auth_url, postdata);
        // final String expires_in = PluginJSonUtils.getJson(br, "expires_in");
        br.getHeaders().put("content-type", "application/json, application/json");
        final String access_token = PluginJSonUtils.getJson(br, "access_token");
        if (StringUtils.isEmpty(access_token)) {
            logger.warning("Failed to find access_token");
            throw new DecrypterException();
        }
        br.getHeaders().put("authorization", "Bearer " + access_token);
        br.getHeaders().put("x-dm-appinfo-id", "com.dailymotion.neon");
        br.getHeaders().put("x-dm-appinfo-type", "website");
        br.getHeaders().put("x-dm-appinfo-version", "v2019-01-10T13:08:47.423Z");
        br.getHeaders().put("x-dm-neon-ssr", "0");
        br.getHeaders().put("x-dm-preferred-country", "de");
        br.getHeaders().put("accept", "*/*, */*");
        br.getHeaders().put("accept-language", "de-DE");
        br.getHeaders().put("origin", "https://www.dailymotion.com");
        br.getHeaders().put("accept-encoding", "gzip, deflate, br");
        final ArrayList<String> dupelist = new ArrayList<String>();
        final boolean parseDesiredPageOnly;
        String desiredPage = new Regex(parameter, "playlist/[A-Za-z0-9]+_[A-Za-z0-9\\-_]+/(\\d+)").getMatch(0);
        if (desiredPage == null) {
            logger.info("Crawling all pages");
            desiredPage = "1";
            parseDesiredPageOnly = false;
        } else {
            logger.info("Only crawling desired page: " + desiredPage);
            parseDesiredPageOnly = true;
        }
        int page = Integer.parseInt(desiredPage);
        int numberofVideos = 0;
        boolean hasMore = false;
        String username = null;
        String playlistTitle = null;
        do {
            /* Check for abort by user */
            if (this.isAbort()) {
                break;
            }
            final PostRequest playlistPagination = br.createJSonPostRequest("https://graphql.api.dailymotion.com/", "{\"operationName\":\"DESKTOP_COLLECTION_VIDEO_QUERY\",\"variables\":{\"xid\":\"" + playlist_id + "\",\"pageCV\":" + page
                    + ",\"allowExplicit\":false},\"query\":\"fragment COLLECTION_BASE_FRAGMENT on Collection {\\n  id\\n  xid\\n  updatedAt\\n  __typename\\n}\\n\\nfragment COLLECTION_IMAGES_FRAGMENT on Collection {\\n  thumbURLx60: thumbnailURL(size: \\\"x60\\\")\\n  thumbURLx120: thumbnailURL(size: \\\"x120\\\")\\n  thumbURLx180: thumbnailURL(size: \\\"x180\\\")\\n  thumbURLx240: thumbnailURL(size: \\\"x240\\\")\\n  thumbURLx360: thumbnailURL(size: \\\"x360\\\")\\n  thumbURLx480: thumbnailURL(size: \\\"x480\\\")\\n  thumbURLx720: thumbnailURL(size: \\\"x720\\\")\\n  __typename\\n}\\n\\nfragment CHANNEL_BASE_FRAGMENT on Channel {\\n  id\\n  xid\\n  name\\n  displayName\\n  isArtist\\n  logoURL(size: \\\"x60\\\")\\n  isFollowed\\n  accountType\\n  __typename\\n}\\n\\nfragment CHANNEL_IMAGES_FRAGMENT on Channel {\\n  coverURLx375: coverURL(size: \\\"x375\\\")\\n  __typename\\n}\\n\\nfragment CHANNEL_UPDATED_FRAGMENT on Channel {\\n  isFollowed\\n  stats {\\n    views {\\n      total\\n      __typename\\n    }\\n    followers {\\n      total\\n      __typename\\n    }\\n    videos {\\n      total\\n      __typename\\n    }\\n    __typename\\n  }\\n  __typename\\n}\\n\\nfragment CHANNEL_NORMAL_FRAGMENT on Channel {\\n  ...CHANNEL_BASE_FRAGMENT\\n  ...CHANNEL_IMAGES_FRAGMENT\\n  ...CHANNEL_UPDATED_FRAGMENT\\n  __typename\\n}\\n\\nfragment ALTERNATIVE_VIDEO_BASE_FRAGMENT on Video {\\n  id\\n  xid\\n  title\\n  description\\n  thumbnail: thumbnailURL(size: \\\"x240\\\")\\n  thumbURLx60: thumbnailURL(size: \\\"x60\\\")\\n  thumbURLx120: thumbnailURL(size: \\\"x120\\\")\\n  thumbURLx240: thumbnailURL(size: \\\"x240\\\")\\n  thumbURLx360: thumbnailURL(size: \\\"x360\\\")\\n  thumbURLx480: thumbnailURL(size: \\\"x480\\\")\\n  thumbURLx720: thumbnailURL(size: \\\"x720\\\")\\n  thumbURLx1080: thumbnailURL(size: \\\"x1080\\\")\\n  bestAvailableQuality\\n  viewCount\\n  duration\\n  createdAt\\n  isInWatchLater\\n  isLiked\\n  isWatched\\n  isExplicit\\n  canDisplayAds\\n  stats {\\n    views {\\n      total\\n      __typename\\n    }\\n    __typename\\n  }\\n  __typename\\n}\\n\\nfragment COLLECTION_UPDATED_FRAGMENT on Collection {\\n  name\\n  description\\n  stats {\\n    videos {\\n      total\\n      __typename\\n    }\\n    __typename\\n  }\\n  videos(first: 15, page: $pageCV, allowExplicit: $allowExplicit) {\\n    pageInfo {\\n      hasNextPage\\n      nextPage\\n      __typename\\n    }\\n    edges {\\n      node {\\n        __typename\\n        ...ALTERNATIVE_VIDEO_BASE_FRAGMENT\\n        channel {\\n          ...CHANNEL_BASE_FRAGMENT\\n          __typename\\n        }\\n      }\\n      __typename\\n    }\\n    __typename\\n  }\\n  __typename\\n}\\n\\nfragment COLLECTION_FRAGMENT on Collection {\\n  ...COLLECTION_BASE_FRAGMENT\\n  ...COLLECTION_UPDATED_FRAGMENT\\n  ...COLLECTION_IMAGES_FRAGMENT\\n  channel {\\n    ...CHANNEL_NORMAL_FRAGMENT\\n    __typename\\n  }\\n  __typename\\n}\\n\\nquery DESKTOP_COLLECTION_VIDEO_QUERY($xid: String!, $pageCV: Int!, $allowExplicit: Boolean) {\\n  collection(xid: $xid) {\\n    ...COLLECTION_FRAGMENT\\n    __typename\\n  }\\n}\\n\"}");
            br.openRequestConnection(playlistPagination);
            br.loadConnection(null);
            entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
            entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "data/collection");
            if (page == 1) {
                numberofVideos = (int) JavaScriptEngineFactory.toLong(JavaScriptEngineFactory.walkJson(entries, "stats/videos/total"), 0);
                username = (String) JavaScriptEngineFactory.walkJson(entries, "channel/name");
                playlistTitle = (String) entries.get("name");
                if (numberofVideos == 0) {
                    /* User has 0 videos */
                    logger.info("Playlist contains 0 items");
                    final DownloadLink dl = this.createOfflinelink(parameter);
                    /* TODO */
                    // dl.setFinalFileName(username);
                    decryptedLinks.add(dl);
                    return;
                }
            }
            hasMore = ((Boolean) JavaScriptEngineFactory.walkJson(entries, "videos/pageInfo/hasNextPage")).booleanValue();
            final ArrayList<Object> ressourcelist = (ArrayList<Object>) JavaScriptEngineFactory.walkJson(entries, "videos/edges");
            if (ressourcelist == null || ressourcelist.size() == 0) {
                logger.info("Stopping: Found nothing on page: " + page);
                break;
            }
            for (final Object videoO : ressourcelist) {
                entries = (LinkedHashMap<String, Object>) videoO;
                entries = (LinkedHashMap<String, Object>) entries.get("node");
                final String videoid = (String) entries.get("xid");
                if (dupelist.contains(videoid)) {
                    logger.info("Found dupe, stopping");
                    break;
                }
                final DownloadLink fina = createDownloadlink(createVideolink(videoid));
                distribute(fina);
                decryptedLinks.add(fina);
                dupelist.add(videoid);
            }
            logger.info("Decrypted page " + page);
            logger.info("Found " + ressourcelist.size() + " links on current page");
            logger.info("Found " + decryptedLinks.size() + " of total " + numberofVideos + " links already...");
            page++;
        } while (hasMore && !parseDesiredPageOnly);
        if (decryptedLinks == null || decryptedLinks.size() == 0) {
            logger.warning("Decrypter failed: " + parameter);
            decryptedLinks = null;
            return;
        }
    }

    private void decryptUserSearch() throws Exception {
        int pagesNum = 1;
        final String[] page_strs = this.br.getRegex("class=\"foreground2 inverted-link-on-hvr\"> ?(\\d+)</a>").getColumn(0);
        if (page_strs != null) {
            for (final String page_str : page_strs) {
                final int page_int = Integer.parseInt(page_str);
                if (page_int > pagesNum) {
                    pagesNum = page_int;
                }
            }
        }
        final String main_search_url = new Regex(parameter, "(.+/)\\d+$").getMatch(0);
        final String username = new Regex(parameter, "/user/([^/]+)/").getMatch(0);
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(username);
        String desiredPage = new Regex(parameter, "(\\d+)$").getMatch(0);
        if (desiredPage == null) {
            desiredPage = "1";
        }
        boolean parsePageOnly = false;
        if (Integer.parseInt(desiredPage) != 1) {
            parsePageOnly = true;
        }
        int currentPage = Integer.parseInt(desiredPage);
        do {
            if (this.isAbort()) {
                logger.info("Decrypt process aborted by user on page " + currentPage + " of " + pagesNum);
                return;
            }
            logger.info("Decrypting page " + currentPage + " / " + pagesNum);
            br.getPage(main_search_url + currentPage);
            final String[] videos = br.getRegex("<a href=\"(/video/[^<>\"]*?)\" class=\"link\"").getColumn(0);
            if (videos == null || videos.length == 0) {
                logger.info("Found no videos on page " + currentPage + " -> Stopping decryption");
                break;
            }
            for (final String videolink : videos) {
                final DownloadLink fina = createDownloadlink(br.getURL(videolink).toString());
                fp.add(fina);
                distribute(fina);
                decryptedLinks.add(fina);
            }
            logger.info("Decrypted page " + currentPage + " of " + pagesNum);
            logger.info("Found " + videos.length + " links on current page");
            currentPage++;
        } while (currentPage <= pagesNum && !parsePageOnly);
        if (this.decryptedLinks.size() == 0) {
            logger.info("Found nothing - user probably entered invalid search term(s)");
        }
    }

    private String videoId     = null;
    private String channelName = null;
    private long   date        = 0;

    /**
     * 2019-01-18: psp: Issues with http URLs - seems like http urls are not valid anymore/at the moment. Via browser they work sometimes
     * but really slow/often run into timeouts --> I auto-reset settings, disabled http downloads by default and preferred HLS!
     */
    @SuppressWarnings("deprecation")
    protected void decryptSingleVideo(ArrayList<DownloadLink> decryptedLinks) throws Exception {
        final SubConfiguration cfg = SubConfiguration.getConfig("dailymotion.com");
        boolean grab_subtitle = cfg.getBooleanProperty(jd.plugins.hoster.DailyMotionCom.ALLOW_SUBTITLE, jd.plugins.hoster.DailyMotionCom.default_ALLOW_SUBTITLE);
        logger.info("Decrypting single video: " + parameter);
        // We can't download live streams
        if (br.containsHTML("DMSTREAMMODE=live")) {
            final DownloadLink dl = createDownloadlink(parameter.replace("dailymotion.com/", "dailymotiondecrypted.com/"));
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return;
        }
        /** Decrypt start */
        /** Decrypt external links START */
        String externID = br.getRegex("player\\.hulu\\.com/express/(\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.hulu.com/watch/" + externID));
            return;
        }
        externID = br.getRegex("name=\"movie\" value=\"(https?://(www\\.)?embed\\.5min\\.com/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return;
        }
        externID = br.getRegex("\"(https?://videoplayer\\.vevo\\.com/embed/embedded\\?videoId=[A-Za-z0-9]+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return;
        }
        /** Decrypt external links END */
        /** Find videolinks START */
        videoId = new Regex(parameter, "dailymotion\\.com/video/([a-z0-9]+)").getMatch(0);
        channelName = br.getRegex("\"owner\":\"([^<>\"]*?)\"").getMatch(0);
        String strdate = br.getRegex("property=\"video:release_date\" content=\"([^<>\"]*?)\"").getMatch(0);
        filename = br.getRegex("<meta itemprop=\"name\" content=\"([^<>\"]*?)\"").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<meta property=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
        }
        videoSource = getVideosource(this, this.br, videoId);
        // channel might not be present above, but is within videoSource
        if (videoSource != null) {
            final LinkedHashMap<String, Object> json = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(videoSource);
            if (channelName == null) {
                channelName = (String) JavaScriptEngineFactory.walkJson(json, "metadata/owner/username");
                if (channelName == null) {
                    channelName = (String) JavaScriptEngineFactory.walkJson(json, "owner/username");
                }
            }
            if (strdate == null) {
                final Number created_time = (Number) JavaScriptEngineFactory.walkJson(json, "created_time");
                if (created_time != null) {
                    strdate = new SimpleDateFormat("yyyy-MM-ddHH:mm:ssz", Locale.ENGLISH).format(new Date(created_time.intValue() * 1000l));
                }
            }
            if (filename == null) {
                filename = (String) JavaScriptEngineFactory.walkJson(json, "title");
            }
        }
        if (videoSource == null || filename == null || videoId == null || channelName == null || strdate == null) {
            logger.warning("Decrypter failed: " + parameter);
            final DownloadLink dl = this.createOfflinelink(parameter);
            dl.setFinalFileName(new Regex(parameter, "dailymotion\\.com/(.+)").getMatch(0));
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return;
        }
        /* Fix date */
        strdate = strdate.replace("T", "").replace("+", "GMT");
        date = TimeFormatter.getMilliSeconds(strdate, "yyyy-MM-ddHH:mm:ssz", Locale.ENGLISH);
        filename = Encoding.htmlDecode(filename.trim()).replace(":", " - ").replaceAll("/|<|>", "");
        if (new Regex(videoSource, "(Dein Land nicht abrufbar|this content is not available for your country|This video has not been made available in your country by the owner|\"Video not available due to geo\\-restriction)").matches()) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            dl.setFinalFileName("Geo restricted video - " + filename + ".mp4");
            dl.setProperty("countryblock", true);
            decryptedLinks.add(dl);
            return;
        } else if (new Regex(videoSource, "\"title\":\"Video geo\\-restricted by the owner").matches()) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            dl.setFinalFileName("Geo-Restricted by owner - " + filename + ".mp4");
            decryptedLinks.add(dl);
        } else if (new Regex(videoSource, "(his content as suitable for mature audiences only|You must be logged in, over 18 years old, and set your family filter OFF, in order to watch it)").matches() && !acc_in_use) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            dl.setFinalFileName(filename + ".mp4");
            dl.setProperty("registeredonly", true);
            decryptedLinks.add(dl);
            return;
        } else if (new Regex(videoSource, "\"message\":\"Publication of this video is in progress").matches()) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            dl.setFinalFileName("Publication of this video is in progress - " + filename + ".mp4");
            decryptedLinks.add(dl);
            return;
        } else if (new Regex(videoSource, "\"encodingMessage\":\"Encoding in progress\\.\\.\\.\"").matches()) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            dl.setFinalFileName("Encoding in progress - " + filename + ".mp4");
            decryptedLinks.add(dl);
            return;
        } else if (new Regex(videoSource, "\"title\":\"Channel offline\\.\"").matches()) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            dl.setFinalFileName("Channel offline - " + filename + ".mp4");
            decryptedLinks.add(dl);
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(filename);
        /** Decrypt subtitles if available and user wants to have it */
        String subsource = new Regex(videoSource, "\"recorded\",(.*?\\}\\})").getMatch(0);
        if (subsource != null && grab_subtitle) {
            subsource = subsource.replace("\\/", "/");
            final String[] subtitles = new Regex(subsource, "\"(https?://static\\d+(-ssl)?\\.dmcdn\\.net/static/video/\\d+/\\d+/\\d+:subtitle_[a-z]{1,4}\\.srt(?:\\?\\d+)?)\"").getColumn(0);
            if (subtitles != null && subtitles.length != 0) {
                final FilePackage fpSub = FilePackage.getInstance();
                fpSub.setName(filename + "_Subtitles");
                for (final String subtitle : subtitles) {
                    final DownloadLink dl = createDownloadlink(br.getURL("//dailymotiondecrypted.com/video/" + videoId).toString());
                    dl.setContentUrl(parameter);
                    final String language = new Regex(subtitle, ".*?\\d+:subtitle_(.{1,4}).srt.*?").getMatch(0);
                    String qualityname = "subtitle";
                    if (language != null) {
                        qualityname += "_" + language;
                    }
                    dl.setProperty("directlink", subtitle);
                    dl.setProperty("type_subtitle", true);
                    dl.setProperty("qualityname", qualityname);
                    dl.setProperty("mainlink", parameter);
                    dl.setProperty("plain_videoname", filename);
                    dl.setProperty("plain_ext", ".srt");
                    dl.setProperty("plain_videoid", videoId);
                    dl.setProperty("plain_channel", channelName);
                    dl.setProperty("plain_date", Long.toString(date));
                    dl.setLinkID("dailymotioncom" + videoId + "_" + qualityname);
                    final String formattedFilename = jd.plugins.hoster.DailyMotionCom.getFormattedFilename(dl);
                    dl.setName(formattedFilename);
                    fpSub.add(dl);
                    decryptedLinks.add(dl);
                }
            }
        }
        foundQualities = findVideoQualities(this, this.br, parameter, videoSource);
        if (foundQualities.isEmpty() && decryptedLinks.size() == 0) {
            logger.warning("Found no quality for link: " + parameter);
            decryptedLinks = null;
            return;
        }
        /** Find videolinks END */
        /** Pick qualities, selected by the user START */
        final ArrayList<String> selectedQualities = new ArrayList<String>();
        final boolean best = cfg.getBooleanProperty(ALLOW_BEST, false);
        boolean mp4 = cfg.getBooleanProperty(jd.plugins.hoster.DailyMotionCom.ALLOW_MP4, jd.plugins.hoster.DailyMotionCom.default_ALLOW_MP4);
        boolean hls = cfg.getBooleanProperty(jd.plugins.hoster.DailyMotionCom.ALLOW_HLS, jd.plugins.hoster.DailyMotionCom.default_ALLOW_HLS);
        if (!mp4 && !hls) {
            hls = true;
            mp4 = true;
        }
        boolean noneSelected = true;
        for (final String quality : new String[] { "7", "6", "5", "4", "3", "2", "1", "0" }) {
            if (cfg.getBooleanProperty("ALLOW_" + quality, true)) {
                noneSelected = false;
                break;
            }
        }
        for (final String quality : new String[] { "7", "6", "5", "4", "3", "2", "1", "0" }) {
            if (selectedQualities.size() > 0 && best) {
                break;
            }
            for (String foundQuality : foundQualities.keySet()) {
                if (foundQuality.startsWith(quality) && (best || noneSelected || cfg.getBooleanProperty("ALLOW_" + quality, true))) {
                    if (!mp4 && foundQuality.endsWith("_MP4")) {
                        continue;
                    } else if (!hls && foundQuality.endsWith("_HLS")) {
                        continue;
                    }
                    selectedQualities.add(foundQuality);
                }
            }
        }
        for (final String selectedQuality : selectedQualities) {
            final DownloadLink dl = setVideoDownloadlink(this.br, foundQualities, selectedQuality);
            if (dl == null) {
                continue;
            }
            dl.setContentUrl(parameter);
            fp.add(dl);
            decryptedLinks.add(dl); // Needed only for the "if" below.
        }
        /** Pick qualities, selected by the user END */
        if (decryptedLinks.size() == 0) {
            logger.info("None of the selected qualities were found, decrypting done...");
            return;
        }
    }

    @SuppressWarnings("unchecked")
    public static LinkedHashMap<String, String[]> findVideoQualities(final Plugin plugin, final Browser br, final String parameter, String videosource) throws Exception {
        final LinkedHashMap<String, String[]> QUALITIES = new LinkedHashMap<String, String[]>();
        final String[][] qualities = { { "hd1080URL", "5" }, { "hd720URL", "4" }, { "hqURL", "3" }, { "sdURL", "2" }, { "ldURL", "1" }, { "video_url", "6" } };
        for (final String quality[] : qualities) {
            final String qualityName = quality[0];
            final String qualityNumber = quality[1];
            final String currentQualityUrl = PluginJSonUtils.getJsonValue(videosource, qualityName);
            if (currentQualityUrl != null) {
                final String[] dlinfo = new String[4];
                dlinfo[0] = currentQualityUrl;
                dlinfo[1] = null;
                dlinfo[2] = qualityName;
                dlinfo[3] = qualityNumber;
                QUALITIES.put(qualityNumber, dlinfo);
            }
        }
        if (QUALITIES.isEmpty() && (videosource.startsWith("{\"context\"") || videosource.contains("\"qualities\""))) {
            /* "New" player July 2015 */
            try {
                final LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(videosource);
                LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(map, "metadata/qualities");
                if (entries == null) {
                    entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(map, "qualities");
                }
                /* TODO: Maybe add HLS support in case it gives us more/other formats/qualities */
                final String[][] qualities_2 = { { "2160@60", "7" }, { "2160", "7" }, { "1440@60", "6" }, { "1440", "6" }, { "1080@60", "5" }, { "1080", "5" }, { "720@60", "4" }, { "720", "4" }, { "480", "3" }, { "380", "2" }, { "240", "1" }, { "144", "0" } };
                for (final String quality[] : qualities_2) {
                    final String qualityName = quality[0];
                    final String qualityNumber = quality[1];
                    final Object jsono = entries.get(qualityName);
                    if (jsono != null) {
                        for (int i = 0; i < ((List) jsono).size(); i++) {
                            final String currentQualityType = (String) JavaScriptEngineFactory.walkJson(jsono, "{" + i + "}/type");
                            final String currentQualityUrl = (String) JavaScriptEngineFactory.walkJson(jsono, "{" + i + "}/url");
                            if (currentQualityUrl != null) {
                                final String[] dlinfo = new String[4];
                                dlinfo[0] = currentQualityUrl;
                                dlinfo[1] = null;
                                dlinfo[2] = qualityName;
                                dlinfo[3] = qualityNumber;
                                if (StringUtils.equalsIgnoreCase("application/x-mpegURL", currentQualityType)) {
                                    QUALITIES.put(qualityNumber + "_HLS", dlinfo);
                                } else if (StringUtils.equalsIgnoreCase("video/mp4", currentQualityType)) {
                                    QUALITIES.put(qualityNumber + "_MP4", dlinfo);
                                } else {
                                    QUALITIES.put(qualityNumber, dlinfo);
                                }
                            }
                        }
                    }
                }
            } catch (final Throwable e) {
                plugin.getLogger().log(e);
            }
        }
        // List empty or only 1 link found -> Check for (more) links
        if (QUALITIES.isEmpty() || QUALITIES.size() == 1) {
            final String manifestURL = PluginJSonUtils.getJsonValue(videosource, "autoURL");
            if (manifestURL != null) {
                /** HDS */
                final String[] dlinfo = new String[4];
                dlinfo[0] = manifestURL;
                dlinfo[1] = "hds";
                dlinfo[2] = "autoURL";
                dlinfo[3] = "8";
                QUALITIES.put("8", dlinfo);
            }
            // Try to avoid HDS
            br.getPage("https://www.dailymotion.com/embed/video/" + new Regex(parameter, "([A-Za-z0-9\\-_]+)$").getMatch(0));
            // 19.09.2014
            videosource = br.getRegex("(\"stream_.*)\"swf_url\":").getMatch(0);
            if (videosource == null) {
                // old version. did not work for me today (19.09.2014)
                videosource = br.getRegex("var info = \\{(.*?)\\},").getMatch(0);
            }
            if (videosource != null) {
                videosource = Encoding.htmlDecode(videosource).replace("\\", "");
                final String[][] embedQualities = { { "stream_h264_ld_url", "5" }, { "stream_h264_url", "4" }, { "stream_h264_hq_url", "3" }, { "stream_h264_hd_url", "2" }, { "stream_h264_hd1080_url", "1" } };
                for (final String quality[] : embedQualities) {
                    final String qualityName = quality[0];
                    final String qualityNumber = quality[1];
                    final String currentQualityUrl = PluginJSonUtils.getJsonValue(videosource, qualityName);
                    if (currentQualityUrl != null) {
                        final String[] dlinfo = new String[4];
                        dlinfo[0] = currentQualityUrl;
                        dlinfo[1] = null;
                        dlinfo[2] = qualityName;
                        dlinfo[3] = qualityNumber;
                        QUALITIES.put(qualityNumber, dlinfo);
                    }
                }
            }
        }
        return QUALITIES;
    }

    /* Sync the following functions in hoster- and decrypterplugin */
    public static String getVideosource(Plugin plugin, final Browser br, final String videoID) throws Exception {
        if (videoID != null) {
            final Browser brc = br.cloneBrowser();
            brc.setFollowRedirects(true);
            brc.getPage("https://www.dailymotion.com/player/metadata/video/" + videoID + "?integration=inline&GK_PV5_NEON=1");
            if (brc.getHttpConnection().isOK() && StringUtils.containsIgnoreCase(brc.getHttpConnection().getContentType(), "json")) {
                return brc.toString();
            } else {
                brc.setRequest(null);
                brc.getPage("https://www.dailymotion.com/embed/video/" + videoID);
                final String config = brc.getRegex("var\\s*config\\s*=\\s*(\\{.*?};)\\s*window").getMatch(0);
                return config;
            }
        } else {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
    }

    private DownloadLink setVideoDownloadlink(final Browser br, final LinkedHashMap<String, String[]> foundqualities, final String qualityValue) throws ParseException {
        String directlinkinfo[] = foundqualities.get(qualityValue);
        if (directlinkinfo != null) {
            final String directlink = Encoding.htmlDecode(directlinkinfo[0]);
            final DownloadLink dl = createDownloadlink("https://dailymotiondecrypted.com/video/" + videoId);
            String qualityName = directlinkinfo[1]; // qualityName is dlinfo[2]
            if (qualityName == null) {
                /* For hls urls */
                if (directlink.matches(".+/manifest/.+\\.m3u8.+include=\\d+")) {
                    qualityName = new Regex(directlink, "include=(\\d+)").getMatch(0);
                    if (qualityName.equals("240")) {
                        qualityName = "320x240";
                    } else if (qualityName.equals("380")) {
                        qualityName = "640X380";
                    } else if (qualityName.equals("480")) {
                        qualityName = "640X480";
                    } else if (qualityName.equals("720")) {
                        qualityName = "1280x720";
                    } else {
                        /* TODO / leave that untouched */
                    }
                } else {
                    /* For http urls mostly */
                    // for example H264-320x240
                    qualityName = new Regex(directlink, "cdn/([^<>\"]*?)/video").getMatch(0);
                    /* 2016-10-18: Added "manifest" handling for hls urls. */
                    if (qualityName == null) {
                        // statically set it... better than nothing.
                        if ("1".equalsIgnoreCase(qualityValue)) {
                            qualityName = "H264-1920x1080";
                        } else if ("2".equalsIgnoreCase(qualityValue)) {
                            qualityName = "H264-1280x720";
                        } else if ("3".equalsIgnoreCase(qualityValue)) {
                            qualityName = "H264-848x480";
                        } else if ("4".equalsIgnoreCase(qualityValue)) {
                            qualityName = "H264-512x384";
                        } else if ("5".equalsIgnoreCase(qualityValue)) {
                            qualityName = "H264-320x240";
                        }
                    }
                }
            }
            final String originalQualityName = directlinkinfo[2];
            final String qualityNumber = directlinkinfo[3];
            dl.setProperty("directlink", directlink);
            dl.setProperty("qualityvalue", qualityValue);
            dl.setProperty("qualityname", qualityName);
            dl.setProperty("originalqualityname", originalQualityName);
            dl.setProperty("qualitynumber", qualityNumber);
            dl.setProperty("mainlink", parameter);
            dl.setProperty("plain_videoname", filename);
            dl.setProperty("plain_ext", ".mp4");
            dl.setProperty("plain_videoid", videoId);
            dl.setProperty("plain_channel", channelName);
            dl.setProperty("plain_date", Long.toString(date));
            dl.setLinkID("dailymotioncom" + videoId + "_" + qualityName);
            final String formattedFilename = jd.plugins.hoster.DailyMotionCom.getFormattedFilename(dl);
            dl.setName(formattedFilename);
            dl.setContentUrl(parameter);
            logger.info("Creating: " + directlinkinfo[2] + "/" + qualityName + " link");
            logger.info(directlink);
            decryptedLinks.add(dl); // This is it, not the other one.
            return dl;
        } else {
            return null;
        }
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}