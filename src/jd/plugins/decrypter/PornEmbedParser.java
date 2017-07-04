package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.Random;

import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Request;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.Plugin;
import jd.plugins.components.DecrypterArrayList;
import jd.utils.JDUtilities;

public abstract class PornEmbedParser extends antiDDoSForDecrypt {
    public PornEmbedParser(PluginWrapper wrapper) {
        super(wrapper);
    }

    /**
     * PornEmbedParser 0.3.2
     *
     *
     * porn_plugin
     *
     *
     * This method is designed to find embedded porn urls in html code.
     *
     * @param pluginBrowser
     *            : Browser containing the sourceurl with the embed urls/codes *
     *
     *
     * @param title
     *            : Title to be used in case a directhttp url is found. If the title is not given, directhttp urls will never be decrypted.
     * @throws Exception
     *
     *
     */
    public final ArrayList<DownloadLink> findEmbedUrls(String title) throws Exception {
        final Browser br = this.br.cloneBrowser();
        final DecrypterArrayList<DownloadLink> decryptedLinks = new DecrypterArrayList<DownloadLink>() {
            //
            @Override
            public boolean add(final String link) {
                return add(link, br);
            }

            @Override
            public boolean add(final String link, final Browser br) {
                final String url = Request.getLocation(link, br.getRequest());
                return add(createDownloadlink(url));
            }
        };
        // use plugin regex where possible... this means less maintaince required.
        Plugin plugin = null;
        /* Cleanup/Improve title */
        if (title != null) {
            title = Encoding.htmlDecode(title).trim();
            title = encodeUnicode(title);
        }
        // xvideos.com 1
        String externID = br.getRegex("xvideos\\.com/embedframe/(\\d+)\"").getMatch(0);
        // xvideos.com 2
        if (externID == null) {
            externID = br.getRegex("\"(?:https?:)?//(?:www\\.)?flashservice\\.xvideos\\.com/embedframe/(\\d+)\"").getMatch(0);
        }
        // xvideos.com 3
        if (externID == null) {
            externID = br.getRegex("\"(?:https?:)?//(?:www\\.)?xvideos\\.com/video(\\d+)/[^<>\"]+\"").getMatch(0);
        }
        // xvideos.com 4
        if (externID == null) {
            externID = br.getRegex("name=\"flashvars\" value=\"id_video=(\\d+)\" /><embed src=\"(?:https?:)?//static\\.xvideos\\.com").getMatch(0);
        }
        if (externID != null) {
            decryptedLinks.add("//www.xvideos.com/video" + externID + "/");
            return decryptedLinks;
        }
        externID = br.getRegex("madthumbs\\.com%2Fvideos%2Fembed_config%3Fid%3D(\\d+)").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("http%3A%2F%2F(?:www\\.)?madthumbs\\.com%2Fvideos%2Fembed_config%3Fvid%3D[^<>\"]+videos%2F[^<>\"]+%2F(\\d+)%26splash%3Dhttp").getMatch(0);
        }
        if (externID != null) {
            final DownloadLink dl = createDownloadlink("//www.madthumbs.com/videos/amateur/" + new Random().nextInt(100000) + "/" + externID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("(\"|')((?:https?:)?//openload\\.co/embed/[A-Za-z0-9_\\-]+(/[^<>\"/]*?)?)\\1").getMatch(1);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("(\"|'')((?:https?:)?//(www\\.)?tube8\\.com/embed/[^<>\"/]*?/[^<>\"/]*?/\\d+/?)\\1").getMatch(1);
        if (externID != null) {
            decryptedLinks.add(externID.replace("tube8.com/embed/", "tube8.com/"));
            return decryptedLinks;
        }
        externID = br.getRegex("redtube\\.com/player/\"><param name=\"FlashVars\" value=\"id=(\\d+)&").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("embed\\.redtube\\.com/player/\\?id=(\\d+)&").getMatch(0);
        }
        if (externID == null) {
            externID = br.getRegex("(?:https?:)?//(?:www\\.)?embed\\.redtube\\.com/\\?id=(\\d+)").getMatch(0);
        }
        if (externID != null) {
            decryptedLinks.add("//www.redtube.com/" + externID);
            return decryptedLinks;
        }
        // drtuber.com embed v3
        externID = br.getRegex("((?:https?:)?//(www\\.)?drtuber\\.com/player/config_embed3\\.php\\?vkey=[a-z0-9]+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // drtuber.com embed v4
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?drtuber\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?xhamster\\.(?:com|xxx)/x?embed\\.php\\?video=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // slutload.com 1
        externID = br.getRegex("emb\\.slutload\\.com/([A-Za-z0-9]+)\"").getMatch(0);
        // slutload.com 2
        if (externID == null) {
            externID = br.getRegex("\"(?:https?:)?//(?:www\\.)?slutload\\.com/embed_player/([A-Za-z0-9]+)/?\"").getMatch(0);
        }
        if (externID != null) {
            decryptedLinks.add("//slutload.com/watch/" + externID);
            return decryptedLinks;
        }
        externID = br.getRegex("pornerbros\\.com/content/(\\d+)\\.xml").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("//www.pornerbros.com/" + externID + "/" + System.currentTimeMillis() + ".html");
            return decryptedLinks;
        }
        externID = br.getRegex("hardsextube\\.com/embed/(\\d+)/").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("//www.hardsextube.com/video/" + externID + "/");
            return decryptedLinks;
        }
        externID = br.getRegex("embed\\.pornrabbit\\.com/player\\.swf\\?movie_id=(\\d+)\"").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("pornrabbit\\.com/embed/(\\d+)").getMatch(0);
        }
        if (externID != null) {
            decryptedLinks.add("http://pornrabbitdecrypted.com/video/" + externID + "/");
            return decryptedLinks;
        }
        /* tnaflix.com handling #1 */
        externID = br.getRegex("player\\.tnaflix\\.com/video/(\\d+)\"").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink("//www.tnaflix.com/teen-porn/" + System.currentTimeMillis() + "/video" + externID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        /* tnaflix.com handling #2 */
        externID = br.getRegex("tnaflix\\.com/embedding_player/player_[^<>\"]+\\.swf\".*?value=\"config=(embedding_feed\\.php\\?viewkey=[a-z0-9]+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("https://www.tnaflix.com/embedding_player/" + externID);
            return decryptedLinks;
        }
        /* tnaflix.com, other */
        plugin = JDUtilities.getPluginForHost("tnaflix.com");
        externID = br.getRegex(plugin.getSupportedLinks()).getMatch(-1);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("metacafe\\.com/fplayer/(\\d+)/").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("//www.metacafe.com/watch/" + externID + "/" + System.currentTimeMillis());
            return decryptedLinks;
        }
        // pornhub handling number #1
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?pornhub\\.com/embed/[a-z0-9]+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // pornhub handling number #2
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?pornhub\\.com/view_video\\.php\\?viewkey=[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // pornhub handling number #3
        externID = br.getRegex("name=\"FlashVars\" value=\"options=((?:https?:)?//(?:www\\.)?pornhub\\.com/embed_player(_v\\d+)?\\.php\\?id=\\d+)\"").getMatch(0);
        if (externID != null) {
            getPage(br, externID);
            if (br.containsHTML("<link_url>N/A</link_url>") || br.containsHTML("No htmlCode read") || br.containsHTML(">404 Not Found<")) {
                decryptedLinks.add(createOfflinelink("//www.pornhub.com/view_video.php?viewkey=" + new Random().nextInt(10000000), externID));
                return decryptedLinks;
            }
            externID = br.getRegex("<link_url>((?:https?:)?//[^<>\"]*?)</link_url>").getMatch(0);
            if (externID == null) {
                return decryptedLinks;
            }
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // myxvids.com 1
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?myxvids\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // myxvids.com 2
        externID = br.getRegex("('|\")((?:https?:)?//(www\\.)?myxvids\\.com/embed_code/\\d+/\\d+/myxvids_embed\\.js)\\1").getMatch(1);
        if (externID != null) {
            getPage(externID);
            final String finallink = br.getRegex("\"((?:https?:)?//(www\\.)?myxvids\\.com/embed/\\d+)\"").getMatch(0);
            if (finallink == null) {
                return decryptedLinks;
            }
            decryptedLinks.add(finallink);
            return decryptedLinks;
        }
        // empflix.com 1
        externID = br.getRegex("player\\.empflix\\.com/video/(\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("//www.empflix.com/videos/" + System.currentTimeMillis() + "-" + externID + ".html");
            return decryptedLinks;
        }
        // empflix.com 2
        externID = br.getRegex("empflix\\.com/embedding_player/player[^<>\"/]*?\\.swf\".*?value=\"config=embedding_feed\\.php\\?viewkey=([^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("//www.empflix.com/embedding_player/embedding_feed.php?viewkey=" + externID);
            return decryptedLinks;
        }
        externID = br.getRegex("stileproject\\.com/embed/(\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("//stileproject.com/video/" + externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?deviantclip\\.com/watch/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("webdata\\.vidz\\.com/demo/swf/FlashPlayerV2\\.swf\".*?flashvars=\"id_scene=(\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("//www.vidz.com/video/" + System.currentTimeMillis() + "/vidz_porn_videos/?s=" + externID);
            return decryptedLinks;
        }
        // youporn.com handling 1
        externID = br.getRegex("youporn\\.com/embed/(\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("//www.youporn.com/watch/" + externID + "/" + System.currentTimeMillis());
            return decryptedLinks;
        }
        externID = br.getRegex("pornyeah\\.com/playerConfig\\.php\\?[a-z0-9]+\\.[a-z0-9\\.]+\\|(\\d+)").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink("//www.pornyeah.com/videos/" + Integer.toString(new Random().nextInt(1000000)) + "-" + externID + ".html");
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("((?:https?:)?//(www\\.)?mofosex\\.com/(embed_player\\.php\\?id=|embed\\?videoid=)\\d+)").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink(externID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?nuvid\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?youjizz\\.com/videos/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?vporn\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?bangyoulater\\.com/embed\\.php\\?id=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?pornhost\\.com/(embed/)?\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?spankwire\\.com/EmbedPlayer\\.aspx/?\\?ArticleId=[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?submityourflicks\\.com/embedded/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("((?:https?:)?//(www\\.)?theamateurzone\\.info/media/player/config_embed\\.php\\?vkey=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?embeds\\.sunporno\\.com/embed/[^<>\"]+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?fux\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("moviefap\\.com/embedding_player/player.*?value=\"config=(embedding_feed\\.php\\?viewkey=[a-z0-9]+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("//www.moviefap.com/embedding_player/" + externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?embed\\.porntube\\.com/\\d+)\"").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("\"((?:https?:)?//(www\\.)?porntube\\.com/embed/\\d+)\"").getMatch(0);
        }
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?xxxhdd\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink(externID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?extremetube\\.com/embed/[^<>\"/]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//embeds\\.ah-me\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?proporn\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?spankbang\\.com/[A-Za-z0-9]+/embed/)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?desihoes\\.com/embed/\\d+/?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?sexbot\\.com/embed/\\d+/?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?xtube\\.com/watch\\.php\\?v=[a-z0-9_-]+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // not to be confused with xtube.com
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?xxxtube\\.com/embed/\\d+/?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"(?:https?:)?//(www\\.)?freeviewmovies\\.com/embed/(\\d+)/\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("http://www.freeviewmoviesdecrypted/video/" + externID);
            return decryptedLinks;
        }
        /* keezmovies.com #1 */
        externID = br.getRegex("((?:https?:)?//(?:www\\.)?keezmovies\\.com/embed_player\\.php\\?v?id=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        /* keezmovies.com #2 */
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?keezmovies\\.com/embed/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("((?:https?:)?//(?:www\\.)?moviesand\\.com/embedded/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?boysfood\\.com/embed/\\d+/?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//video\\.fc2\\.com/a/flv2\\.swf\\?i=\\w+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?sextube\\.com/media/\\d+/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // pornstar
        plugin = JDUtilities.getPluginForHost("pornstarnetwork.com");
        externID = br.getRegex(plugin.getSupportedLinks()).getMatch(-1);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?playvid\\.com/embed/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?pornhd\\.com/video/embed/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // isharemybitch.com #1
        externID = br.getRegex("(\"|')((?:https?:)?//(?:www\\.)?isharemybitch\\.com/flvPlayer\\.swf\\?settings=[^<>\"]*?)\"").getMatch(0);
        // isharemybitch.com #2
        if (externID == null) {
            externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?share-image\\.com/gallery/[^<>\"]*?)\"").getMatch(0);
        }
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("src=\"(?:https?:)?//videos\\.allelitepass\\.com/txc/([^<>\"/]*?)\\.swf\"").getMatch(0);
        if (externID != null) {
            /* Add as offline -this site is down! */
            decryptedLinks.add(createOfflinelink("//videos.allelitepass.com/txc/player.php?video=" + Encoding.htmlDecode(externID)));
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?isharemybitch\\.com/flvPlayer\\.swf\\?settings=[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?eporner\\.com/hd-porn/\\d+/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?alotporn\\.com/(?:embed\\.php\\?id=|embed/)\\d+[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?youtube\\.com/embed/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("('|\")((?:https?:)?//(?:www\\.)?4tube.com/(?:videos|embed)/\\d+)\\1").getMatch(1);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("('|\")((?:https?:)?//(?:www\\.)?pornsharing.com/.+)\\1").getMatch(1);
        if (externID != null) {
            if (externID.contains("playlist")) {
                // get the id and reformat?
                final String uid = new Regex(externID, "id=(\\d+)").getMatch(0);
                // any random crap is needed before _vUID to make it a 'valid' link.
                externID = new Regex(externID, "(?:https?:)?//").getMatch(-1) + "pornsharing.com/" + (!inValidate(title) ? title.replaceAll("\\s", "-") : "abc") + "_v" + uid;
            }
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("(?:\"|')((?:https?:)?//(?:www\\.)?tubecup\\.com/embed/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?txxx\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("\"((?:https?:)?//(?:www\\.)?(?:bemywife|mydaddy)\\.cc/video/[a-z0-9]+)").getMatch(0);
        if (externID != null) {
            /* 2017-03-30: Added mydaddy.cc */
            final DownloadLink dl = createDownloadlink(externID);
            if (title != null) {
                dl.setProperty("decryptertitle", title);
            }
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("(?:'|\")((?:https?:)?//(?:www\\.)?hclips\\.com/embed/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("(?:'|\")((?:https?:)?//(?:www\\.)?pornomovies\\.com/embed/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("(?:'|\")((?:https?:)?//(?:www\\.)?gotporn\\.com/video/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("(?:'|\")((?:https?:)?//(?:www\\.)?camwhores\\.tv/embed/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(jd.plugins.decrypter.CamwhoresTv.createDownloadUrlForHostPlugin(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("(?:'|\")((?:https?:)?//(?:www\\.)?camvideos\\.org/embed/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        externID = br.getRegex("(?:'|\")((?:https?:)?//(?:www\\.)?borfos\\.com/embed/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        // filename needed for all IDs below
        if (title == null) {
            return decryptedLinks;
        }
        /* TODO: Remove as much Browser-accesses as possible, handle all embedded urls in the corresponding host plugins! */
        externID = br.getRegex("shufuni\\.com/Flash/.*?flashvars=\"VideoCode=(.*?)\"").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink("//www.shufuni.com/handlers/FLVStreamingv2.ashx?videoCode=" + externID);
            dl.setFinalFileName(Encoding.htmlDecode(title.trim()));
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("src=\"(?:https?:)?//videos\\.allelitepass\\.com/txc/([^<>\"/]*?)\\.swf\"").getMatch(0);
        if (externID != null) {
            getPage(br, "http://videos.allelitepass.com/txc/player.php?video=" + Encoding.htmlDecode(externID));
            externID = br.getRegex("<file>(http://[^<>\"]*?)</file>").getMatch(0);
            if (externID != null) {
                final DownloadLink dl = createDownloadlink("directhttp://" + externID);
                dl.setFinalFileName(title + ".flv");
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
        }
        // youporn.com handling 2
        externID = br.getRegex("flashvars=\"file=(http%3A%2F%2Fdownload\\.youporn\\.com[^<>\"]*?)&").getMatch(0);
        if (externID != null) {
            br.setCookie("http://youporn.com/", "age_verified", "1");
            br.setCookie("http://youporn.com/", "is_pc", "1");
            br.setCookie("http://youporn.com/", "language", "en");
            getPage(br, Encoding.htmlDecode(externID));
            if (br.getRequest().getHttpConnection().getResponseCode() == 404) {
                return decryptedLinks;
            }
            if (br.containsHTML("download\\.youporn\\.com/agecheck")) {
                return decryptedLinks;
            }
            externID = br.getRegex("\"((?:https?:)?//(www\\.)?download\\.youporn.com/download/\\d+/\\?xml=1)\"").getMatch(0);
            if (externID == null) {
                return decryptedLinks;
            }
            getPage(br, externID);
            final String finallink = br.getRegex("<location>((?:https?:)?//.*?)</location>").getMatch(0);
            if (finallink == null) {
                return decryptedLinks;
            }
            final DownloadLink dl = createDownloadlink("directhttp://" + Request.getLocation(Encoding.htmlDecode(finallink), br.getRequest()));
            String type = br.getRegex("<meta rel=\"type\">(.*?)</meta>").getMatch(0);
            if (type == null) {
                type = "flv";
            }
            dl.setFinalFileName(title + "." + type);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("((?:https?:)?//(www\\.)?5ilthy\\.com/playerConfig\\.php\\?[a-z0-9]+\\.(flv|mp4))").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink(externID);
            dl.setProperty("5ilthydirectfilename", title);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        /* RegExes for permanently offline websites go here */
        /* 2016-03-29: gasxxx.com --> xvid6.com */
        externID = br.getRegex("((?:https?:)?//(?:www\\.)?gasxxx\\.com/media/player/config_embed\\.php\\?vkey=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        /* 2016-03-25: xrabbit.com --> xpage.com */
        externID = br.getRegex("\"((?:https?:)?//(www\\.)?xrabbit\\.com/video/embed/[A-Za-z0-9=]+/?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(externID);
            return decryptedLinks;
        }
        /* 2016-03-25: foxytube.com == offline */
        externID = br.getRegex("(foxytube\\.com/embedded/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add("http://www." + externID);
            return decryptedLinks;
        }
        /* 2017-01-27 fantasti.cc */
        externID = br.getRegex("('|\")((?:https?:)?//(?:www\\.)?fantasti\\.cc/embed/\\d+/?)\\1").getMatch(1);
        if (externID != null) {
            decryptedLinks.add(Request.getLocation(externID, br.getRequest()));
            return decryptedLinks;
        }
        /* 2017-01-27 porn.com */
        externID = br.getRegex("('|\")((?:https?:)?//(?:www\\.)?porn\\.com/videos/embed/\\d+?)\\1").getMatch(1);
        if (externID != null) {
            decryptedLinks.add(Request.getLocation(externID, br.getRequest()));
            return decryptedLinks;
        }
        return decryptedLinks;
    }
}
