package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.ProgressController;
import jd.http.Request;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.utils.JDUtilities;

import org.appwork.utils.StringUtils;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "ted.com" }, urls = { "https?://(?:www\\.)?ted\\.com/(talks/(?:lang/[a-zA-Z\\-]+/)?[\\w_]+|[\\w_]+\\?language=\\w+|playlists/\\d+/[^/]+)" })
public class TedCom extends PluginForDecrypt {
    public TedCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String     TYPE_PLAYLIST                      = "https?://(?:www\\.)?ted\\.com/playlists/\\d+/[^/]+";
    private static final String     TYPE_VIDEO                         = "https?://(?:www\\.)?ted\\.com/talks/(?:(?:lang/[a-zA-Z\\-]+/)?\\w+|[\\w_]+\\?language=\\w+)";
    private static final String     CHECKFAST_VIDEOS                   = "CHECKFAST_VIDEOS";
    private static final String     CHECKFAST_MP3                      = "CHECKFAST_MP3";
    private static final String     GRAB_MP3                           = "GRAB_MP3";
    private static final String     GRAB_ALL_AVAILABLE_SUBTITLES       = "GRAB_ALL_AVAILABLE_SUBTITLES";
    private static final String     GRAB_SUBTITLE_ALBANIAN             = "GRAB_SUBTITLE_ALBANIAN";
    private static final String     GRAB_SUBTITLE_ARABIC               = "GRAB_SUBTITLE_ARABIC";
    private static final String     GRAB_SUBTITLE_ARMENIAN             = "GRAB_SUBTITLE_ARMENIAN";
    private static final String     GRAB_SUBTITLE_AZERBAIJANI          = "GRAB_SUBTITLE_AZERBAIJANI";
    private static final String     GRAB_SUBTITLE_BENGALI              = "GRAB_SUBTITLE_BENGALI";
    private static final String     GRAB_SUBTITLE_BULGARIAN            = "GRAB_SUBTITLE_BULGARIAN";
    private static final String     GRAB_SUBTITLE_CHINESE_SIMPLIFIED   = "GRAB_SUBTITLE_CHINESE_SIMPLIFIED";
    private static final String     GRAB_SUBTITLE_CHINESE_TRADITIONAL  = "GRAB_SUBTITLE_CHINESE_TRADITIONAL";
    private static final String     GRAB_SUBTITLE_CROATIAN             = "GRAB_SUBTITLE_CROATIAN";
    private static final String     GRAB_SUBTITLE_CZECH                = "GRAB_SUBTITLE_CZECH";
    private static final String     GRAB_SUBTITLE_DANISH               = "GRAB_SUBTITLE_DANISH";
    private static final String     GRAB_SUBTITLE_DUTCH                = "GRAB_SUBTITLE_DUTCH";
    private static final String     GRAB_SUBTITLE_ENGLISH              = "GRAB_SUBTITLE_ENGLISH";
    private static final String     GRAB_SUBTITLE_ESTONIAN             = "GRAB_SUBTITLE_ESTONIAN";
    private static final String     GRAB_SUBTITLE_FINNISH              = "GRAB_SUBTITLE_FINNISH";
    private static final String     GRAB_SUBTITLE_FRENCH               = "GRAB_SUBTITLE_FRENCH";
    private static final String     GRAB_SUBTITLE_GEORGIAN             = "GRAB_SUBTITLE_GEORGIAN";
    private static final String     GRAB_SUBTITLE_GERMAN               = "GRAB_SUBTITLE_GERMAN";
    private static final String     GRAB_SUBTITLE_GREEK                = "GRAB_SUBTITLE_GREEK";
    private static final String     GRAB_SUBTITLE_HEBREW               = "GRAB_SUBTITLE_HEBREW";
    private static final String     GRAB_SUBTITLE_HUNGARIAN            = "GRAB_SUBTITLE_HUNGARIAN";
    private static final String     GRAB_SUBTITLE_INDONESIAN           = "GRAB_SUBTITLE_INDONESIAN";
    private static final String     GRAB_SUBTITLE_ITALIAN              = "GRAB_SUBTITLE_ITALIAN";
    private static final String     GRAB_SUBTITLE_JAPANESE             = "GRAB_SUBTITLE_JAPANESE";
    private static final String     GRAB_SUBTITLE_KOREAN               = "GRAB_SUBTITLE_KOREAN";
    private static final String     GRAB_SUBTITLE_KURDISH              = "GRAB_SUBTITLE_KURDISH";
    private static final String     GRAB_SUBTITLE_LITHUANIAN           = "GRAB_SUBTITLE_LITHUANIAN";
    private static final String     GRAB_SUBTITLE_MACEDONIAN           = "GRAB_SUBTITLE_MACEDONIAN";
    private static final String     GRAB_SUBTITLE_MALAY                = "GRAB_SUBTITLE_MALAY";
    private static final String     GRAB_SUBTITLE_NORWEGIAN_BOKMAL     = "GRAB_SUBTITLE_NORWEGIAN_BOKMAL";
    private static final String     GRAB_SUBTITLE_PERSIAN              = "GRAB_SUBTITLE_PERSIAN";
    private static final String     GRAB_SUBTITLE_POLISH               = "GRAB_SUBTITLE_POLISH";
    private static final String     GRAB_SUBTITLE_PORTUGUESE           = "GRAB_SUBTITLE_PORTUGUESE";
    private static final String     GRAB_SUBTITLE_PORTUGUESE_BRAZILIAN = "GRAB_SUBTITLE_PORTUGUESE_BRAZILIAN";
    private static final String     GRAB_SUBTITLE_ROMANIAN             = "GRAB_SUBTITLE_ROMANIAN";
    private static final String     GRAB_SUBTITLE_RUSSIAN              = "GRAB_SUBTITLE_RUSSIAN";
    private static final String     GRAB_SUBTITLE_SERBIAN              = "GRAB_SUBTITLE_SERBIAN";
    private static final String     GRAB_SUBTITLE_SLOVAK               = "GRAB_SUBTITLE_SLOVAK";
    private static final String     GRAB_SUBTITLE_SLOVENIAN            = "GRAB_SUBTITLE_SLOVENIAN";
    private static final String     GRAB_SUBTITLE_SPANISH              = "GRAB_SUBTITLE_SPANISH";
    private static final String     GRAB_SUBTITLE_SWEDISH              = "GRAB_SUBTITLE_SWEDISH";
    private static final String     GRAB_SUBTITLE_THAI                 = "GRAB_SUBTITLE_THAI";
    private static final String     GRAB_SUBTITLE_TURKISH              = "GRAB_SUBTITLE_TURKISH";
    private static final String     GRAB_SUBTITLE_UKRAINIAN            = "GRAB_SUBTITLE_UKRAINIAN";
    private static final String     GRAB_SUBTITLE_VIETNAMESE           = "GRAB_SUBTITLE_VIETNAMESE";
    private ArrayList<DownloadLink> decryptedLinks                     = new ArrayList<DownloadLink>();
    private String                  parameter                          = null;
    private SubConfiguration        cfg                                = null;

    /** Old 'apikey' value = "TEDDOWNLOAD" */
    /** Ild download-way: "http://www.ted.com/download/links/slug/" + plainfilename (slug) + "/type/talks/ext/mp4" */
    /***
     * HLS info:
     *
     * Get HLS information: https://hls.ted.com/talks/2323.json
     *
     *
     * HLS manifest: https://hls.ted.com/talks/2323.m3u8
     *
     * 2323 = tedID
     *
     */
    @SuppressWarnings({ "deprecation" })
    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        parameter = param.toString();
        /* Load host plugin */
        JDUtilities.getPluginForHost("ted.com");
        cfg = SubConfiguration.getConfig("ted.com");
        br.setFollowRedirects(true);
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        decryptAll();
        return decryptedLinks;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void decryptAll() throws Exception {
        final LinkedHashMap<String, String[]> formats = jd.plugins.hoster.TedCom.formats;
        final LinkedHashMap<String, DownloadLink> foundVideoLinks = new LinkedHashMap();
        final String json;
        LinkedHashMap<String, Object> entries;
        if (parameter.matches(TYPE_PLAYLIST)) {
            /*
             * We could crawl from here straight away but this way we won't be able to find all qualities thus we prefer to decrypt one by
             * one via their original URLs.
             */
            String[] links = br.getRegex("<a\\s+href=\"([^<>\"]+referrer=playlist[^<>\"]+)\"").getColumn(0);
            logger.info("links: " + links.length);
            for (final String link : links) {
                logger.info("link: " + link);
                decryptedLinks.add(createDownloadlink("https://www.ted.com" + link));
            }
            if (links.length > 0) {
                return;
            }
            json = this.br.getRegex("<script>q\\(\"permalink\\.init\",(\\{.*?)</script>").getMatch(0);
            entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json);
            entries = (LinkedHashMap<String, Object>) entries.get("__INITIAL_DATA__");
            final ArrayList<Object> videos = (ArrayList) entries.get("talks");
            for (final Object videoo : videos) {
                entries = (LinkedHashMap<String, Object>) videoo;
                final String url_single_video = (String) entries.get("canonical");
                if (url_single_video == null) {
                    throw new DecrypterException("Decrypter broken");
                }
                decryptedLinks.add(createDownloadlink(url_single_video));
            }
        } else {
            /** Look for external links */
            String externalLink = br.getRegex("class=\"external\" href=\"(https?://(www\\.)?youtube\\.com/[^<>\"]*?)\"").getMatch(0);
            if (externalLink == null) {
                externalLink = br.getRegex("<iframe src=\"(https?://(www\\.)?youtube\\.com/embed/[A-Za-z0-9\\-_]+)").getMatch(0);
            }
            if (externalLink != null) {
                decryptedLinks.add(createDownloadlink(externalLink));
                return;
            }
            json = this.br.getRegex("<script>q\\(\"talkPage\\.init\",\\s*?(\\{.*?)\\)</script>").getMatch(0);
            if (json == null) {
                this.decryptedLinks.add(this.createOfflinelink(parameter));
                return;
            }
            entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json);
            entries = (LinkedHashMap<String, Object>) entries.get("__INITIAL_DATA__");
            // This is needed later for the subtitle decrypter
            final String subtitleText = br.getRegex("<select name=\"languageCode\" id=\"languageCode\"><option value=\"\">Show transcript</option>(.*?)</select>").getMatch(0);
            /** Decrypt video */
            final Object externalMedia = JavaScriptEngineFactory.walkJson(entries, "media/external");
            if (externalMedia != null) {
                logger.info("Found external media");
                entries = (LinkedHashMap<String, Object>) externalMedia;
                final String mediaCode = (String) entries.get("code");
                final String service = (String) entries.get("service");
                if (!StringUtils.isEmpty(service) && !StringUtils.isEmpty(mediaCode) && service.equalsIgnoreCase("youtube")) {
                    decryptedLinks.add(createDownloadlink(String.format("https://www.youtube.com/watch?v=%s", mediaCode)));
                }
                final String uri = (String) entries.get("uri");
                if (!StringUtils.isEmpty(uri)) {
                    /* Sometimes, a mirror is available e.g. YouTube (above code) and vimeo (here via URL). */
                    decryptedLinks.add(createDownloadlink(uri));
                }
                return;
            }
            LinkedHashMap<String, Object> http_stream_url_list = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "media/internal");
            if (http_stream_url_list == null) {
                http_stream_url_list = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "talks/{0}/player_talks/{0}/resources");
            }
            entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "talks/{0}");
            String title = (String) entries.get("title");
            if (title == null) {
                throw new DecrypterException("Decrypter broken");
            }
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(title);
            final String tedID = Long.toString(JavaScriptEngineFactory.toLong(entries.get("id"), -1));
            String url_mp3 = null;
            long filesize_mp3 = 0;
            /*
             * 2017-09-26: 'resources' object is still available sometimes, containing http/hls/rtmp[?] URLs but much less qualities than
             * 'internal'.
             */
            // final ArrayList<Object> rtmp_resource_data_list = (ArrayList) JavaScriptEngineFactory.walkJson(entries, "resources/rtmp");
            final Iterator<Entry<String, Object>> iteratorAvailableQualities = http_stream_url_list.entrySet().iterator();
            while (iteratorAvailableQualities.hasNext()) {
                final Entry<String, Object> currentObject = iteratorAvailableQualities.next();
                if ("hls".equals(currentObject.getKey())) {
                    // TODO: check after HLS support
                    continue;
                }
                final String qualityKey = currentObject.getKey();
                final LinkedHashMap<String, Object> tmp;
                if (currentObject.getValue() instanceof List) {
                    tmp = (LinkedHashMap<String, Object>) ((List<Object>) currentObject.getValue()).get(0);
                } else {
                    tmp = (LinkedHashMap<String, Object>) currentObject.getValue();
                }
                final long filesize = JavaScriptEngineFactory.toLong(tmp.get("filesize_bytes"), 0);
                String url_http = (String) tmp.get("uri");
                if (url_http == null) {
                    url_http = (String) tmp.get("file");
                    if (url_http == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
                if (qualityKey.equalsIgnoreCase("audio-podcast")) {
                    /* Audio download user selection is handled below. */
                    filesize_mp3 = filesize;
                    url_mp3 = url_http;
                    break;
                } else if (!formats.containsKey(qualityKey)) {
                    /* Skip unknown qualities */
                    // continue;
                }
                final DownloadLink dl = createDownloadlink("decrypted://decryptedtedcom.com/" + System.currentTimeMillis() + new Random().nextInt(100000));
                dl.setProperty("directlink", url_http);
                dl.setProperty("type", "video");
                dl.setProperty("selectedvideoquality", qualityKey);
                if (cfg.getBooleanProperty(CHECKFAST_VIDEOS, false)) {
                    dl.setAvailable(true);
                }
                final String[] vidinfo = formats.get(qualityKey);
                /* Get format-String for filename */
                String formatString = "";
                if (vidinfo != null) {
                    // TODO: check after HLS support
                    final String videoCodec = vidinfo[0];
                    final String videoBitrate = vidinfo[1];
                    final String videoResolution = vidinfo[2];
                    final String audioCodec = vidinfo[3];
                    final String audioBitrate = vidinfo[4];
                    if (videoCodec != null) {
                        formatString += videoCodec + "_";
                    }
                    if (videoResolution != null) {
                        formatString += videoResolution + "_";
                    }
                    if (videoBitrate != null) {
                        formatString += videoBitrate + "_";
                    }
                    if (audioCodec != null) {
                        formatString += audioCodec + "_";
                    }
                    if (audioBitrate != null) {
                        formatString += audioBitrate;
                    }
                    if (formatString.endsWith("_")) {
                        formatString = formatString.substring(0, formatString.lastIndexOf("_"));
                    }
                }
                final String finalName = title + "_" + formatString + ".mp4";
                dl.setFinalFileName(finalName);
                dl.setProperty("finalfilename", finalName);
                dl.setLinkID(finalName);
                dl._setFilePackage(fp);
                dl.setContentUrl(parameter);
                if (filesize > 0) {
                    dl.setDownloadSize(filesize);
                    dl.setAvailable(true);
                }
                foundVideoLinks.put(qualityKey, dl);
            }
            title = encodeUnicode(title);
            /* Add user selected video qualities */
            final Iterator<Entry<String, String[]>> it = formats.entrySet().iterator();
            while (it.hasNext()) {
                final Entry<String, String[]> videntry = it.next();
                final String internalname = videntry.getKey();
                final DownloadLink dl = foundVideoLinks.get(internalname);
                if (dl != null && cfg.getBooleanProperty(internalname, true)) {
                    decryptedLinks.add(dl);
                }
            }
            // TODO: check after HLS support
            decryptedLinks.addAll(foundVideoLinks.values());
            if (url_mp3 != null && cfg.getBooleanProperty(GRAB_MP3, false)) {
                final DownloadLink dl = createDownloadlink("decrypted://decryptedtedcom.com/" + System.currentTimeMillis() + new Random().nextInt(100000));
                final String finalName = title + "_mp3.mp3";
                dl.setFinalFileName(finalName);
                dl.setProperty("finalfilename", finalName);
                if (filesize_mp3 > 0) {
                    dl.setDownloadSize(filesize_mp3);
                    dl.setAvailable(true);
                } else if (cfg.getBooleanProperty(CHECKFAST_MP3, false)) {
                    dl.setAvailable(true);
                }
                dl.setProperty("directlink", url_mp3);
                dl.setProperty("type", "mp3");
                fp.add(dl);
                dl.setContentUrl(parameter);
                decryptedLinks.add(dl);
            }
            /** Decrypt subtitles */
            if (subtitleText != null && tedID != null || entries.containsKey("languages")) {
                final String[][] allSubtitleValues = { { "sq", "Albanian" }, { "ar", "Arabic" }, { "hy", "Armenian" }, { "az", "Azerbaijani" }, { "bn", "Bengali" }, { "bg", "Bulgarian" }, { "zh-cn", "Chinese, Simplified" }, { "zh-tw", "Chinese, Traditional" }, { "hr", "Croatian" }, { "cs", "Czech" }, { "da", "Danish" }, { "nl", "Dutch" }, { "en", "English" }, { "et", "Estonian" }, { "fi", "Finnish" }, { "fr", "French" }, { "ka", "Georgian" }, { "de", "German" }, { "el", "Greek" }, { "he", "Hebrew" }, { "hu", "Hungarian" }, { "id", "Indonesian" }, { "it", "Italian" }, { "ja", "Japanese" }, { "ko", "Korean" }, { "ku", "Kurdish" }, { "lt", "Lithuanian" }, { "mk", "Macedonian" }, { "ms", "Malay" }, { "nb", "Norwegian Bokmal" }, { "fa", "Persian" }, { "pl", "Polish" }, { "pt", "Portuguese" }, { "pt-br", "Portuguese, Brazilian" }, { "ro", "Romanian" }, { "ru", "Russian" },
                        { "sr", "Serbian" }, { "sk", "Slovak" }, { "sl", "Slovenian" }, { "es", "Spanish" }, { "sv", "Swedish" }, { "th", "Thai" }, { "tr", "Turkish" }, { "uk", "Ukrainian" }, { "vi", "Vietnamese" } };
                final ArrayList<String[]> selectedSubtitles = new ArrayList<String[]>();
                final LinkedHashMap<String, String> foundSubtitles = new LinkedHashMap();
                if (subtitleText != null) {
                    final String[] availableSubtitles = new Regex(subtitleText, "value=\"([a-z\\-]{2,5})\">").getColumn(0);
                    for (final String currentSubtitle : availableSubtitles) {
                        foundSubtitles.put(currentSubtitle, Request.getLocation("/talks/subtitles/id/" + tedID + "/lang/" + currentSubtitle + "/format/srt", br.getRequest()));
                    }
                } else {
                    // back ported for JSON
                    final ArrayList<Object> subtitles = (ArrayList) entries.get("languages");
                    if (subtitles != null) {
                        for (final Object result : subtitles) {
                            final LinkedHashMap<String, String> yay = (LinkedHashMap<String, String>) result;
                            // assume its the lower case one from the code below.
                            final String langCode = yay.get("languageCode");
                            foundSubtitles.put(langCode, Request.getLocation("/talks/subtitles/id/" + tedID + "/lang/" + langCode + "/format/srt", br.getRequest()));
                        }
                    }
                }
                if (cfg.getBooleanProperty(GRAB_ALL_AVAILABLE_SUBTITLES, false)) {
                    for (final String[] subtitleValue : allSubtitleValues) {
                        selectedSubtitles.add(subtitleValue);
                    }
                } else {
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_ALBANIAN, false)) {
                        selectedSubtitles.add(new String[] { "sq", "Albanian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_ARABIC, false)) {
                        selectedSubtitles.add(new String[] { "ar", "Arabic" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_ARMENIAN, false)) {
                        selectedSubtitles.add(new String[] { "hy", "Armenian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_AZERBAIJANI, false)) {
                        selectedSubtitles.add(new String[] { "az", "Azerbaijani" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_BENGALI, false)) {
                        selectedSubtitles.add(new String[] { "bn", "Bengali" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_BULGARIAN, false)) {
                        selectedSubtitles.add(new String[] { "bg", "Bulgarian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_CHINESE_SIMPLIFIED, false)) {
                        selectedSubtitles.add(new String[] { "zh-cn", "Chinese, Simplified" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_CHINESE_TRADITIONAL, false)) {
                        selectedSubtitles.add(new String[] { "zh-tw", "Chinese, Traditional" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_CROATIAN, false)) {
                        selectedSubtitles.add(new String[] { "hr", "Croatian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_CZECH, false)) {
                        selectedSubtitles.add(new String[] { "cs", "Czech" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_DANISH, false)) {
                        selectedSubtitles.add(new String[] { "da", "Danish" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_DUTCH, false)) {
                        selectedSubtitles.add(new String[] { "nl", "Dutch" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_ENGLISH, false)) {
                        selectedSubtitles.add(new String[] { "en", "English" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_ESTONIAN, false)) {
                        selectedSubtitles.add(new String[] { "et", "Estonian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_FINNISH, false)) {
                        selectedSubtitles.add(new String[] { "fi", "Finnish" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_FRENCH, false)) {
                        selectedSubtitles.add(new String[] { "fr", "French" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_GEORGIAN, false)) {
                        selectedSubtitles.add(new String[] { "ka", "Georgian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_GERMAN, false)) {
                        selectedSubtitles.add(new String[] { "de", "German" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_GREEK, false)) {
                        selectedSubtitles.add(new String[] { "el", "Greek" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_HEBREW, false)) {
                        selectedSubtitles.add(new String[] { "he", "Hebrew" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_HUNGARIAN, false)) {
                        selectedSubtitles.add(new String[] { "hu", "Hungarian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_INDONESIAN, false)) {
                        selectedSubtitles.add(new String[] { "id", "Indonesian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_ITALIAN, false)) {
                        selectedSubtitles.add(new String[] { "it", "Italian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_JAPANESE, false)) {
                        selectedSubtitles.add(new String[] { "ja", "Japanese" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_KOREAN, false)) {
                        selectedSubtitles.add(new String[] { "ko", "Korean" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_KURDISH, false)) {
                        selectedSubtitles.add(new String[] { "ku", "Kurdish" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_LITHUANIAN, false)) {
                        selectedSubtitles.add(new String[] { "lt", "Lithuanian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_MACEDONIAN, false)) {
                        selectedSubtitles.add(new String[] { "mk", "Macedonian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_MALAY, false)) {
                        selectedSubtitles.add(new String[] { "ms", "Malay" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_NORWEGIAN_BOKMAL, false)) {
                        selectedSubtitles.add(new String[] { "nb", "Norwegian Bokmal" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_PERSIAN, false)) {
                        selectedSubtitles.add(new String[] { "fa", "Persian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_POLISH, false)) {
                        selectedSubtitles.add(new String[] { "pl", "Polish" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_PORTUGUESE, false)) {
                        selectedSubtitles.add(new String[] { "pt", "Portuguese" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_PORTUGUESE_BRAZILIAN, false)) {
                        selectedSubtitles.add(new String[] { "pt-br", "Portuguese, Brazilian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_ROMANIAN, false)) {
                        selectedSubtitles.add(new String[] { "ro", "Romanian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_RUSSIAN, false)) {
                        selectedSubtitles.add(new String[] { "ru", "Russian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_SERBIAN, false)) {
                        selectedSubtitles.add(new String[] { "sr", "Serbian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_SLOVAK, false)) {
                        selectedSubtitles.add(new String[] { "sk", "Slovak" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_SLOVENIAN, false)) {
                        selectedSubtitles.add(new String[] { "sl", "Slovenian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_SPANISH, false)) {
                        selectedSubtitles.add(new String[] { "es", "Spanish" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_SWEDISH, false)) {
                        selectedSubtitles.add(new String[] { "sv", "Swedish" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_THAI, false)) {
                        selectedSubtitles.add(new String[] { "th", "Thai" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_TURKISH, false)) {
                        selectedSubtitles.add(new String[] { "tr", "Turkish" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_UKRAINIAN, false)) {
                        selectedSubtitles.add(new String[] { "uk", "Ukrainian" });
                    }
                    if (cfg.getBooleanProperty(GRAB_SUBTITLE_VIETNAMESE, false)) {
                        selectedSubtitles.add(new String[] { "vi", "Vietnamese" });
                    }
                }
                // Find available qualities and add them to the decrypted links
                for (final String[] selectedSubtitle : selectedSubtitles) {
                    final String foundSubtitleDirectLink = foundSubtitles.get(selectedSubtitle[0]);
                    if (foundSubtitleDirectLink != null) {
                        final String subtitleName = selectedSubtitle[1];
                        final DownloadLink dl = createDownloadlink("decrypted://decryptedtedcom.com/" + System.currentTimeMillis() + new Random().nextInt(100000));
                        final String finalName = title + "_subtitle_" + subtitleName + ".srt";
                        dl.setFinalFileName(finalName);
                        dl.setProperty("finalfilename", finalName);
                        dl.setProperty("directlink", foundSubtitleDirectLink);
                        dl.setProperty("type", "subtitle");
                        dl.setAvailable(true);
                        fp.add(dl);
                        dl.setContentUrl(parameter);
                        decryptedLinks.add(dl);
                    }
                }
            }
        }
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}