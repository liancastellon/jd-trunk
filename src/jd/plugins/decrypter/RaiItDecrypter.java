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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.decrypter.GenericM3u8Decrypter.HlsContainer;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "rai.tv" }, urls = { "https?://[A-Za-z0-9\\.]*?rai\\.tv/dl/replaytv/replaytv\\.html\\?day=\\d{4}\\-\\d{2}\\-\\d{2}(?:\\&ch=\\d+)?|https?://[A-Za-z0-9\\.]*?rai\\.(?:tv|it)/dl/[^<>\"]+/ContentItem\\-[a-f0-9\\-]+\\.html" }, flags = { 0 })
public class RaiItDecrypter extends PluginForDecrypt {

    public RaiItDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String     TYPE_DAY         = "https?://[A-Za-z0-9\\.]*?rai\\.tv/dl/replaytv/replaytv\\.html\\?day=\\d{4}\\-\\d{2}\\-\\d{2}(?:\\&ch=\\d+)?";
    private static final String     TYPE_CONTENTITEM = ".+/dl/[^<>\"]+/ContentItem\\-[a-f0-9\\-]+\\.html$";

    private ArrayList<DownloadLink> decryptedLinks   = new ArrayList<DownloadLink>();
    private String                  parameter        = null;

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        this.br.setFollowRedirects(true);
        parameter = param.toString();

        if (parameter.matches(TYPE_DAY)) {
            decryptWholeDay();
        } else {
            decryptSingleVideo();
        }

        return decryptedLinks;
    }

    private void decryptWholeDay() throws Exception {
        final String date = new Regex(parameter, "(\\d{4}\\-\\d{2}\\-\\d{2})").getMatch(0);
        String chnumber_str = new Regex(parameter, "ch=(\\d+)").getMatch(0);
        if (chnumber_str == null) {
            /* Small fallback */
            chnumber_str = "1";
        }
        final String date_underscore = date.replace("-", "_");
        LinkedHashMap<String, Object> tempmap = null;
        LinkedHashMap<String, Object> entries = null;
        ArrayList<Object> ressourcelist = null;
        /* Find the name of our channel needed for the following requests */
        this.br.getPage("http://www.rai.tv/dl/RaiTV/iphone/android/smartphone/advertising_config.html");
        String channel_name = null;
        try {
            entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
            ressourcelist = (ArrayList<Object>) entries.get("Channels");
            for (final Object channelo : ressourcelist) {
                entries = (LinkedHashMap<String, Object>) channelo;
                final String channelnumber = (String) entries.get("id");
                if (channelnumber.equals(chnumber_str)) {
                    channel_name = (String) entries.get("tag");
                    break;
                }
            }
        } catch (final Throwable e) {
        }
        if (channel_name == null || channel_name.equals("")) {
            channel_name = "RaiUno";
        }

        this.br.getPage("/dl/portale/html/palinsesti/replaytv/static/" + channel_name + "_" + date_underscore + ".html?_=" + System.currentTimeMillis());
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return;
        }
        entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
        entries = (LinkedHashMap<String, Object>) entries.get(chnumber_str);
        entries = (LinkedHashMap<String, Object>) entries.get(date);

        final Iterator<Entry<String, Object>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            if (this.isAbort()) {
                logger.info("Decryption aborted by user");
                return;
            }
            final Entry<String, Object> entry = it.next();
            tempmap = (LinkedHashMap<String, Object>) entry.getValue();
            final String title = (String) tempmap.get("t");
            final String relinker = (String) tempmap.get("r");
            final String description = (String) tempmap.get("d");
            if (title == null || title.equals("") || relinker == null || relinker.equals("")) {
                continue;
            }
            final String cont = jd.plugins.hoster.RaiTv.getContFromRelinkerUrl(relinker);
            jd.plugins.hoster.RaiTv.accessCont(this.br, cont);
            final String dllink = jd.plugins.hoster.RaiTv.getDllink(this.br);
            if (!jd.plugins.hoster.RaiTv.dllinkIsDownloadable(dllink)) {
                continue;
            }
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(date + " - " + title);
            if (dllink.contains(".m3u8")) {
                this.br.getPage(dllink);
                final ArrayList<HlsContainer> allqualities = jd.plugins.decrypter.GenericM3u8Decrypter.getHlsQualities(this.br);
                for (final HlsContainer singleHlsQuality : allqualities) {
                    final DownloadLink dl = this.createDownloadlink(singleHlsQuality.downloadurl);
                    final String filename = title + " - " + description + "_" + singleHlsQuality.getStandardFilename();
                    dl.setFinalFileName(filename);
                    dl._setFilePackage(fp);
                    if (description != null) {
                        dl.setComment(description);
                    }
                    decryptedLinks.add(dl);
                }
            } else {
                final DownloadLink dl = this.createDownloadlink("directhttp://" + dllink);
                dl.setFinalFileName(title + " - " + description + ".mp4");
                dl._setFilePackage(fp);
                if (description != null) {
                    dl.setComment(description);
                }
                decryptedLinks.add(dl);
            }
        }
    }

    private void decryptSingleVideo() throws DecrypterException, Exception {
        boolean possibleNotDownloadableMSSilverlight = false;
        String dllink = null;
        this.br.getPage(this.parameter);
        if (this.br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(this.parameter));
            return;
        }
        /* Do NOT use value of "videoURL_MP4" here! */
        /* E.g. http://www.rai.tv/dl/RaiTV/programmi/media/ContentItem-70996227-7fec-4be9-bc49-ba0a8104305a.html */
        dllink = this.br.getRegex("var[\t\n\r ]*?videoURL[\t\n\r ]*?=[\t\n\r ]*?\"(http://[^<>\"]+)\"").getMatch(0);
        String content_id_from_url = null;
        if (this.parameter.matches(TYPE_CONTENTITEM)) {
            content_id_from_url = new Regex(this.parameter, "(\\-[a-f0-9\\-]+)\\.html$").getMatch(0);
        }
        final String contentset_id = this.br.getRegex("var[\t\n\r ]*?urlTop[\t\n\r ]*?=[\t\n\r ]*?\"[^<>\"]+/ContentSet([A-Za-z0-9\\-]+)\\.html").getMatch(0);
        final String content_id_from_html = this.br.getRegex("id=\"ContentItem(\\-[a-f0-9\\-]+)\"").getMatch(0);
        if (br.getHttpConnection().getResponseCode() == 404 || (contentset_id == null && content_id_from_html == null && dllink == null)) {
            /* Probably not a video/offline */
            decryptedLinks.add(this.createOfflinelink(this.parameter));
            return;
        }
        String filename = null;
        String extension = ".mp4";
        String date = null;
        String date_formatted = null;
        String description = null;
        if (dllink != null) {
            /* Streamurls directly in html */
            filename = this.br.getRegex("id=\"idMedia\">([^<>]+)<").getMatch(0);
            date = this.br.getRegex("id=\"myGenDate\">(\\d{2}\\-\\d{2}\\-\\d{4} \\d{2}:\\d{2})<").getMatch(0);
            possibleNotDownloadableMSSilverlight = this.br.containsHTML("id=\"silverlightControlHost\"");
        } else {
            LinkedHashMap<String, Object> entries = null;
            if (content_id_from_html != null) {
                /* Easiest way to find videoinfo */
                this.br.getPage("http://www.rai.tv/dl/RaiTV/programmi/media/ContentItem" + content_id_from_html + ".html?json");
                entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
            }
            if (entries == null) {
                final ArrayList<Object> ressourcelist;
                final String list_json_from_html = this.br.getRegex("\"list\"[\t\n\r ]*?:[\t\n\r ]*?(\\[.*?\\}[\t\n\r ]*?\\])").getMatch(0);
                if (list_json_from_html != null) {
                    ressourcelist = (ArrayList<Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(list_json_from_html);
                } else {
                    br.getPage("http://www.rai.tv/dl/RaiTV/ondemand/ContentSet" + contentset_id + ".html?json");
                    if (br.getHttpConnection().getResponseCode() == 404) {
                        decryptedLinks.add(this.createOfflinelink(this.parameter));
                        return;
                    }
                    entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
                    ressourcelist = (ArrayList<Object>) entries.get("list");
                }

                if (content_id_from_url == null) {
                    /* Hm probably not a video */
                    decryptedLinks.add(this.createOfflinelink(this.parameter));
                    return;
                }
                String content_id_temp = null;
                boolean foundVideoInfo = false;
                for (final Object videoo : ressourcelist) {
                    entries = (LinkedHashMap<String, Object>) videoo;
                    content_id_temp = (String) entries.get("itemId");
                    if (content_id_temp != null && content_id_temp.contains(content_id_from_url)) {
                        foundVideoInfo = true;
                        break;
                    }
                }
                if (!foundVideoInfo) {
                    /* Probably offline ... */
                    decryptedLinks.add(this.createOfflinelink(this.parameter));
                    return;
                }
            }
            date = (String) entries.get("date");
            filename = (String) entries.get("name");
            description = (String) entries.get("desc");
            final String type = (String) entries.get("type");
            if (type.equalsIgnoreCase("RaiTv Media Video Item")) {
            } else {
                /* TODO */
                logger.warning("Unsupported media type!");
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
            extension = "mp4";
            dllink = (String) entries.get("h264");
            if (dllink == null || dllink.equals("")) {
                dllink = (String) entries.get("m3u8");
                extension = "mp4";
            }
            if (dllink == null || dllink.equals("")) {
                dllink = (String) entries.get("wmv");
                extension = "wmv";
            }
            if (dllink == null || dllink.equals("")) {
                dllink = (String) entries.get("mediaUri");
                extension = "mp4";
            }
        }
        if (filename == null) {
            filename = content_id_from_url;
        }
        date_formatted = jd.plugins.hoster.RaiTv.formatDate(date);
        filename = date_formatted + "_raitv_" + filename + "." + extension;
        filename = encodeUnicode(filename);

        /* Not needed (yet/anymore) */
        // if (possibleNotDownloadableMSSilverlight) {
        // throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming protocol Microsoft Silverlight");
        // }
        if (filename.endsWith(".wmv")) {
            /* E.g. http://www.tg1.rai.it/dl/tg1/2010/rubriche/ContentItem-9b79c397-b248-4c03-a297-68b4b666e0a5.html */
            logger.info("Download http .wmv video");
        } else {
            final String cont = jd.plugins.hoster.RaiTv.getContFromRelinkerUrl(dllink);
            if (cont == null) {
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
            /* Drop previous Headers & Cookies */
            this.br = jd.plugins.hoster.RaiTv.prepVideoBrowser(new Browser());
            jd.plugins.hoster.RaiTv.accessCont(this.br, cont);
            dllink = jd.plugins.hoster.RaiTv.getDllink(this.br);
            if (dllink == null) {
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
            if (!jd.plugins.hoster.RaiTv.dllinkIsDownloadable(dllink)) {
                logger.info("Unsupported streaming protocol");
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
        }
        if (dllink.contains(".m3u8")) {
            /* hls */
            /* Access hls master */
            this.br.getPage(dllink);
            final HlsContainer hlsbest = jd.plugins.decrypter.GenericM3u8Decrypter.findBestVideoByBandwidth(jd.plugins.decrypter.GenericM3u8Decrypter.getHlsQualities(this.br));
            if (hlsbest == null) {
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
            dllink = hlsbest.downloadurl;
        } else {
            /* http */
        }

        final DownloadLink dl = this.createDownloadlink("directhttp://" + dllink);
        if (description != null && dl.getComment() == null) {
            dl.setComment(description);
        }
        dl.setFinalFileName(filename);

        this.decryptedLinks.add(dl);
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
