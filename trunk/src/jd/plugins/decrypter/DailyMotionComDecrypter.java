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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Random;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

//Decrypts embedded videos from dailymotion
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "dailymotion.com" }, urls = { "https?://(www\\.)?dailymotion\\.com/((embed/)?video/[a-z0-9\\-_]+|swf(/video)?/[a-zA-Z0-9]+)" }, flags = { 0 })
public class DailyMotionComDecrypter extends PluginForDecrypt {

    public DailyMotionComDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String                          VIDEOSOURCE    = null;
    /**
     * @ 1hd1080URL or stream_h264_hd1080_url
     * 
     * @ 2 hd720URL or stream_h264_hd_url
     * 
     * @ 3 hqURL
     * 
     * @ 4 sdURL or stream_h264_hq_url [848p]
     * 
     * @ 5 ldURL or stream_h264_ld_url [320p]
     * 
     * @ 6 video_url or rtmp
     * 
     * @ 7 hds
     * 
     * @String[] = {"Direct download url", "filename, if available before quality selection"}
     */
    private LinkedHashMap<String, String[]> FOUNDQUALITIES = new LinkedHashMap<String, String[]>();
    private String                          FILENAME       = null;
    private String                          PARAMETER      = null;

    private static final String             ALLOW_LQ       = "ALLOW_LQ";
    private static final String             ALLOW_SD       = "ALLOW_SD";
    private static final String             ALLOW_HQ       = "ALLOW_HQ";
    private static final String             ALLOW_720      = "ALLOW_720";
    private static final String             ALLOW_1080     = "ALLOW_1080";
    private static final String             ALLOW_OTHERS   = "ALLOW_OTHERS";
    private static final String             ALLOW_HDS      = "ALLOW_HDS";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        PARAMETER = param.toString().replace("www.", "").replace("embed/video/", "video/").replaceAll("\\.com/swf(/video)?/", ".com/video/").replace("https://", "http://");
        br.setFollowRedirects(true);

        /** Login if account available */
        final PluginForHost dailymotionHosterplugin = JDUtilities.getPluginForHost("dailymotion.com");
        Account aa = AccountController.getInstance().getValidAccount(dailymotionHosterplugin);
        boolean accInUse = false;
        if (aa != null) {
            try {
                ((jd.plugins.hoster.DailyMotionCom) dailymotionHosterplugin).login(aa, this.br);
                accInUse = true;
            } catch (final PluginException e) {
                logger.info("Account seems to be invalid, returnung empty linklist!");
            }
        }
        /** Login end... */

        br.setCookie("http://www.dailymotion.com", "family_filter", "off");
        br.setCookie("http://www.dailymotion.com", "ff", "off");
        br.setCookie("http://www.dailymotion.com", "lang", "en_US");
        try {
            br.getPage(PARAMETER);
        } catch (final Exception e) {
            final DownloadLink dl = createDownloadlink(PARAMETER.replace("dailymotion.com/", "dailymotiondecrypted.com/"));
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        // 404
        if (br.containsHTML("(<title>Dailymotion \\– 404 Not Found</title>|url\\(/images/404_background\\.jpg)")) {
            final DownloadLink dl = createDownloadlink(PARAMETER.replace("dailymotion.com/", "dailymotiondecrypted.com/"));
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        // 403
        if (br.containsHTML("class=\"forbidden\">Access forbidden</h3>|>You don\\'t have permission to access the requested URL")) {
            final DownloadLink dl = createDownloadlink(PARAMETER.replace("dailymotion.com/", "dailymotiondecrypted.com/"));
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        // We can't download livestreams
        if (br.containsHTML("DMSTREAMMODE=live")) {
            final DownloadLink dl = createDownloadlink(PARAMETER.replace("dailymotion.com/", "dailymotiondecrypted.com/"));
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        /** Decrypt start */
        /** Decrypt external links START */
        String externID = br.getRegex("player\\.hulu\\.com/express/(\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.hulu.com/watch/" + externID));
            return decryptedLinks;
        }
        externID = br.getRegex("name=\"movie\" value=\"(http://(www\\.)?embed\\.5min\\.com/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        /** Decrypt external links END */
        /** Find videolinks START */
        VIDEOSOURCE = br.getRegex("\"sequence\":\"([^<>\"]*?)\"").getMatch(0);
        if (VIDEOSOURCE == null) VIDEOSOURCE = br.getRegex("%2Fsequence%2F(.*?)</object>").getMatch(0);
        if (VIDEOSOURCE == null) VIDEOSOURCE = br.getRegex("name=\"flashvars\" value=\"(.*?)\"/></object>").getMatch(0);
        FILENAME = br.getRegex("<meta itemprop=\"name\" content=\"([^<>\"]*?)\"").getMatch(0);
        if (FILENAME == null) {
            FILENAME = br.getRegex("<meta property=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
        }
        if (VIDEOSOURCE == null || FILENAME == null) {
            logger.warning("Dailymotion.com decrypter failed..." + PARAMETER);
            return null;
        }
        VIDEOSOURCE = Encoding.htmlDecode(VIDEOSOURCE).replace("\\", "");
        FILENAME = Encoding.htmlDecode(FILENAME.trim()).replace(":", " - ").replaceAll("/|<|>", "");
        if (new Regex(VIDEOSOURCE, "(Dein Land nicht abrufbar|this content is not available for your country|This video has not been made available in your country by the owner)").matches()) {
            final DownloadLink dl = createDownloadlink(PARAMETER.replace("dailymotion.com/", "dailymotiondecrypted.com/"));
            dl.setFinalFileName(FILENAME + ".mp4");
            dl.setProperty("countryblock", true);
            dl.setAvailable(true);
            decryptedLinks.add(dl);
            return decryptedLinks;
        } else if (new Regex(VIDEOSOURCE, "(his content as suitable for mature audiences only|You must be logged in, over 18 years old, and set your family filter OFF, in order to watch it)").matches() && !accInUse) {
            final DownloadLink dl = createDownloadlink(PARAMETER.replace("dailymotion.com/", "dailymotiondecrypted.com/"));
            dl.setFinalFileName(FILENAME + ".mp4");
            dl.setProperty("registeredonly", true);
            dl.setAvailable(true);
            decryptedLinks.add(dl);
            return decryptedLinks;
        } else if (new Regex(VIDEOSOURCE, "\"message\":\"Publication of this video is in progress").matches()) {
            final DownloadLink dl = createDownloadlink(PARAMETER.replace("dailymotion.com/", "dailymotiondecrypted.com/"));
            dl.setFinalFileName(FILENAME + ".mp4");
            dl.setProperty("offline", true);
            dl.setAvailable(false);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(FILENAME);

        /** Decrypt subtitles if available */
        String[] subtitles = new Regex(VIDEOSOURCE, "\"(http://static\\d+\\.dmcdn\\.net/static/video/\\d+/\\d+/\\d+:subtitle_[a-z]{1,4}\\.srt\\?\\d+)\"").getColumn(0);
        if (subtitles != null && subtitles.length != 0) {
            final FilePackage fpSub = FilePackage.getInstance();
            fpSub.setName(FILENAME + "_Subtitles");
            for (final String subtitle : subtitles) {
                final DownloadLink dl = createDownloadlink("directhttp://" + subtitle);
                final String language = new Regex(subtitle, ".*?\\d+:subtitle_(.{1,4}).srt.*?").getMatch(0);
                if (language != null)
                    dl.setFinalFileName(correctFilename(FILENAME + "_subtitle_" + language + ".srt"));
                else
                    dl.setFinalFileName(correctFilename(FILENAME + "_subtitle_" + Integer.toString(new Random().nextInt(1000)) + ".srt"));
                fpSub.add(dl);
                decryptedLinks.add(dl);
            }
        }

        final String[][] qualities = { { "hd1080URL", "1" }, { "hd720URL", "2" }, { "hqURL", "3" }, { "sdURL", "4" }, { "ldURL", "5" }, { "video_url", "6" } };
        for (final String quality[] : qualities) {
            final String currentQualityUrl = getQuality(quality[0]);
            if (currentQualityUrl != null) {
                final String[] dlinfo = new String[2];
                dlinfo[0] = currentQualityUrl;
                dlinfo[1] = null;
                FOUNDQUALITIES.put(quality[1], dlinfo);
            }
        }
        // List empty or only 1 link found -> Check for (more) links
        if (FOUNDQUALITIES.isEmpty() || FOUNDQUALITIES.size() == 1) {
            final String manifestURL = new Regex(VIDEOSOURCE, "\"autoURL\":\"(http://[^<>\"]*?)\"").getMatch(0);
            if (manifestURL != null) {
                /** HDS */
                final String[] dlinfo = new String[2];
                dlinfo[0] = manifestURL;
                dlinfo[1] = "hds";
                FOUNDQUALITIES.put("7", dlinfo);
            }

            // Try to avoid HDS
            br.getPage("http://www.dailymotion.com/embed/video/" + new Regex(PARAMETER, "([A-Za-z0-9\\-_]+)$").getMatch(0));
            VIDEOSOURCE = br.getRegex("var info = \\{(.*?)\\},").getMatch(0);
            if (VIDEOSOURCE != null) {
                VIDEOSOURCE = Encoding.htmlDecode(VIDEOSOURCE).replace("\\", "");
                final String[][] embedQualities = { { "stream_h264_ld_url", "5" }, { "stream_h264_hq_url", "4" }, { "stream_h264_hd_url", "2" }, { "stream_h264_hd1080_url", "1" } };
                for (final String quality[] : embedQualities) {
                    final String currentQualityUrl = getQuality(quality[0]);
                    if (currentQualityUrl != null) {
                        final String[] dlinfo = new String[2];
                        dlinfo[0] = currentQualityUrl;
                        dlinfo[1] = null;
                        FOUNDQUALITIES.put(quality[1], dlinfo);
                    }
                }
            }
            // if (FOUNDQUALITIES.isEmpty()) {
            // String[] values =
            // br.getRegex("new SWFObject\\(\"(http://player\\.grabnetworks\\.com/swf/GrabOSMFPlayer\\.swf)\\?id=\\d+\\&content=v([0-9a-f]+)\"").getRow(0);
            // if (values == null || values.length != 2) {
            // /** RTMP */
            // final DownloadLink dl = createDownloadlink("http://dailymotiondecrypted.com/video/" + System.currentTimeMillis() + new
            // Random(10000));
            // dl.setProperty("isrtmp", true);
            // dl.setProperty("mainlink", PARAMETER);
            // dl.setFinalFileName(FILENAME + "_RTMP.mp4");
            // fp.add(dl);
            // decryptedLinks.add(dl);
            // return decryptedLinks;
            // }
            // }
            if (FOUNDQUALITIES.isEmpty() && decryptedLinks.size() == 0) {
                logger.warning("Found no quality for link: " + PARAMETER);
                return null;
            }
        }
        /** Find videolinks END */
        /** Pick qualities, selected by the user START */
        final ArrayList<String> selectedQualities = new ArrayList<String>();
        final SubConfiguration cfg = SubConfiguration.getConfig("dailymotion.com");
        if (cfg.getBooleanProperty("ALLOW_BEST", false)) {
            ArrayList<String> list = new ArrayList<String>(FOUNDQUALITIES.keySet());
            final String highestAvailableQualityValue = list.get(list.size() - 1);
            selectedQualities.add(highestAvailableQualityValue);
        } else {
            boolean qld = cfg.getBooleanProperty(ALLOW_LQ, false);
            boolean qsd = cfg.getBooleanProperty(ALLOW_SD, false);
            boolean qhq = cfg.getBooleanProperty(ALLOW_HQ, false);
            boolean q720 = cfg.getBooleanProperty(ALLOW_720, false);
            boolean q1080 = cfg.getBooleanProperty(ALLOW_1080, false);
            boolean others = cfg.getBooleanProperty(ALLOW_OTHERS, false);
            boolean hds = cfg.getBooleanProperty(ALLOW_HDS, false);
            /** User selected nothing -> Decrypt everything */
            if (qld == false && qsd == false && qhq == false && q720 == false && q1080 == false && others == false && hds == false) {
                qld = true;
                qsd = true;
                qhq = true;
                q720 = true;
                q1080 = true;
                others = true;
                hds = true;
            }
            if (qld) selectedQualities.add("5");
            if (qsd) selectedQualities.add("4");
            if (qhq) selectedQualities.add("3");
            if (q720) selectedQualities.add("2");
            if (q1080) selectedQualities.add("1");
            if (others) selectedQualities.add("6");
            if (hds) selectedQualities.add("7");
        }
        for (final String selectedQualityValue : selectedQualities) {
            final DownloadLink dl = getVideoDownloadlink(selectedQualityValue);
            if (dl != null) {
                fp.add(dl);
                decryptedLinks.add(dl);
            }
        }
        /** Pick qualities, selected by the user END */
        if (decryptedLinks.size() == 0) {
            logger.info("None of the selected qualities were found, decrypting done...");
            return decryptedLinks;
        }
        return decryptedLinks;
    }

    private DownloadLink getVideoDownloadlink(final String qualityValue) {
        String directlinkinfo[] = FOUNDQUALITIES.get(qualityValue);
        if (directlinkinfo != null) {
            final String directlink = Encoding.htmlDecode(directlinkinfo[0]);
            final DownloadLink dl = createDownloadlink("http://dailymotiondecrypted.com/video/" + System.currentTimeMillis() + new Random().nextInt(10000));
            String qualityName = directlinkinfo[1];
            if (qualityName == null) qualityName = new Regex(directlink, "cdn/([^<>\"]*?)/video").getMatch(0);
            dl.setProperty("directlink", directlink);
            dl.setProperty("qualityvalue", qualityValue);
            dl.setProperty("qualityname", qualityName);
            dl.setProperty("mainlink", PARAMETER);
            dl.setFinalFileName(correctFilename(FILENAME + "_" + qualityName + ".mp4"));
            return dl;
        } else {
            return null;
        }
    }

    private String correctFilename(String filename) {
        // Cut filenames if they're too long
        if (filename.length() > 240) {
            final String ext = filename.substring(filename.lastIndexOf("."));
            int extLength = ext.length();
            filename = filename.substring(0, 240 - extLength);
            filename += ext;
        }
        return filename;
    }

    private String getQuality(final String quality) {
        return new Regex(VIDEOSOURCE, "\"" + quality + "\":\"(http[^<>\"\\']+)\"").getMatch(0);
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}