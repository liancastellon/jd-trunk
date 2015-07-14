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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.BrightcoveClipData;
import jd.plugins.hoster.DummyScriptEnginePlugin;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "brightcove.com" }, urls = { "https?://c\\.brightcove\\.com/services/viewer/htmlFederated\\?.+" }, flags = { 0 })
public class BrightcoveDecrypter extends PluginForDecrypt {

    public BrightcoveDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    /**
     * General plugin to decrypt html versions of videos hosted via https://www.brightcove.com/de/ SLASH AKAMAI.
     *
     * TODO: Maybe add thumbnail support, maybe add HLS/HDS support
     * */
    @SuppressWarnings("unchecked")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            try {
                decryptedLinks.add(this.createOfflinelink(parameter));
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
            }
            return decryptedLinks;
        }
        // final String brightcove_URL = "http://c.brightcove.com/services/viewer/htmlFederated?&width=340&height=192&flashID=" + flashID +
        // "&includeAPI=true&templateLoadHandler=templateLoaded&templateReadyHandler=playerReady&bgcolor=%23FFFFFF&htmlFallback=true&playerID="
        // + playerID + "&publisherID=" + publisherID + "&playerKey=" + Encoding.urlEncode(playerKey) +
        // "&isVid=true&isUI=true&dynamicStreaming=true&optimizedContentLoad=true&wmode=transparent&%40videoPlayer=" + videoID +
        // "&allowScriptAccess=always";
        // this.br.getPage(brightcove_URL);

        final String json = getSourceJson(this.br);
        if (json == null) {
            return null;
        }
        final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) DummyScriptEnginePlugin.jsonToJavaObject(json);
        final String publisherName = getPublisherName(entries);
        final String title = getTitle(entries);
        final long creationDate = getCreationDate(entries);
        final String date_formatted = formatDate(creationDate);

        final ArrayList<BrightcoveClipData> media = findAllQualities(entries);
        for (final BrightcoveClipData clip : media) {
            final String final_filename = date_formatted + "_" + publisherName + "_" + title + "_" + clip.width + "x" + clip.height + "_" + clip.videoCodec + clip.ext;
            final DownloadLink dl = this.createDownloadlink("directhttp://" + clip.downloadurl);
            dl.setDownloadSize(clip.size);
            dl.setAvailable(true);
            dl.setFinalFileName(final_filename);
            dl.setContentUrl(clip.downloadurl);
            decryptedLinks.add(dl);
        }

        String fpName = date_formatted + "_" + publisherName + "_" + title;

        final FilePackage fp = FilePackage.getInstance();
        fp.setName(Encoding.htmlDecode(fpName.trim()));
        fp.addLinks(decryptedLinks);

        return decryptedLinks;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static ArrayList<BrightcoveClipData> findAllQualities(final LinkedHashMap<String, Object> map) {
        final ArrayList<BrightcoveClipData> media = new ArrayList<BrightcoveClipData>();
        final String publisherName = encodeUnicode(getPublisherName(map));
        final String title = encodeUnicode(getTitle(map));
        final long creationDate = getCreationDate(map);
        LinkedHashMap<String, Object> entries = null;
        final ArrayList<Object> resource_data_list = (ArrayList) DummyScriptEnginePlugin.walkJson(map, "data/programmedContent/videoPlayer/mediaDTO/renditions");
        for (final Object o : resource_data_list) {
            entries = (LinkedHashMap<String, Object>) o;
            /* audioOnly == true = untested case */
            // final boolean audioOnly = ((Boolean) entries.get("audioOnly")).booleanValue();
            // if (audioOnly) {
            // continue;
            // }
            final String videoCodec = (String) entries.get("videoCodec");
            final String downloadurl = (String) entries.get("defaultURL");
            final long filesize = DummyScriptEnginePlugin.toLong(entries.get("size"), -1);
            final long encodingRate = DummyScriptEnginePlugin.toLong(entries.get("encodingRate"), -1);
            final long frameWidth = DummyScriptEnginePlugin.toLong(entries.get("frameWidth"), -1);
            final long frameHeight = DummyScriptEnginePlugin.toLong(entries.get("frameHeight"), -1);
            final long mediaDeliveryType = DummyScriptEnginePlugin.toLong(entries.get("mediaDeliveryType"), -1);

            if (videoCodec == null || downloadurl == null || filesize == -1 || encodingRate == -1 || frameWidth == -1 || frameHeight == -1 || mediaDeliveryType == -1) {
                return null;
            }

            final BrightcoveClipData clip = new BrightcoveClipData();
            clip.creationDate = creationDate;
            clip.publisherName = publisherName;
            clip.displayName = title;
            clip.size = filesize;
            clip.encodingRate = encodingRate;
            clip.width = frameWidth;
            clip.height = frameHeight;
            clip.videoCodec = videoCodec;
            clip.downloadurl = downloadurl;
            clip.mediaDeliveryType = mediaDeliveryType;
            media.add(clip);
        }
        return media;
    }

    /** Finds the highest video quality based on the max filesize. */
    @SuppressWarnings("unchecked")
    public static BrightcoveClipData findBestVideoHttpByFilesize(final Browser br) {
        final String json = getSourceJson(br);
        if (json == null) {
            return null;
        }
        LinkedHashMap<String, Object> map = null;
        try {
            map = (LinkedHashMap<String, Object>) DummyScriptEnginePlugin.jsonToJavaObject(json);
        } catch (final Throwable e) {
            return null;
        }
        return findBestVideoByFilesize(map);
    }

    /** Finds the highest video quality based on the max filesize. */
    public static BrightcoveClipData findBestVideoByFilesize(final LinkedHashMap<String, Object> map) {
        final ArrayList<BrightcoveClipData> media = findAllQualities(map);
        return findBestVideoByFilesize(media);
    }

    /** Finds the highest video quality based on the max filesize. */
    public static BrightcoveClipData findBestVideoByFilesize(final ArrayList<BrightcoveClipData> media) {
        if (media == null) {
            return null;
        }
        BrightcoveClipData best = null;
        long filesize_highest = 0;
        for (final BrightcoveClipData clip : media) {
            final long filesize_temp = clip.size;
            if (filesize_temp > filesize_highest) {
                filesize_highest = filesize_temp;
                best = clip;
            }
        }
        return best;
    }

    public static String getSourceJson(final Browser br) {
        return br.getRegex("var experienceJSON = (\\{.*?);\r").getMatch(0);
    }

    public static String getPublisherName(final LinkedHashMap<String, Object> map) {
        return (String) DummyScriptEnginePlugin.walkJson(map, "data/programmedContent/videoPlayer/mediaDTO/publisherName");
    }

    public static String getTitle(final LinkedHashMap<String, Object> map) {
        return (String) DummyScriptEnginePlugin.walkJson(map, "data/programmedContent/videoPlayer/mediaDTO/displayName");
    }

    public static long getCreationDate(final LinkedHashMap<String, Object> map) {
        return DummyScriptEnginePlugin.toLong(DummyScriptEnginePlugin.walkJson(map, "data/programmedContent/videoPlayer/mediaDTO/creationDate"), -1);
    }

    private String formatDate(final long date) {
        String formattedDate = null;
        final String targetFormat = "yyyy-MM-dd";
        Date theDate = new Date(date);
        try {
            final SimpleDateFormat formatter = new SimpleDateFormat(targetFormat);
            formattedDate = formatter.format(theDate);
        } catch (Exception e) {
            /* prevent input error killing plugin */
            formattedDate = Long.toString(date);
        }
        return formattedDate;
    }

    /** Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "⁄");
        output = output.replace("\\", "∖");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
    }

}
