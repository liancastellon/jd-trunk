package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.Map;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "liveme.com" }, urls = { "https?://(?:www\\.)?liveme\\.com/(?:media/play/\\?videoid=\\d+|media/liveshort/dist/\\?videoid=\\d+&.*?|live\\.html\\?videoid=\\d+.*?|.*?/\\d+/index.html)" })
public class LiveMeCom extends PluginForDecrypt {
    public LiveMeCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        final String videoid = getVideoID(parameter);
        br.setFollowRedirects(true);
        final String vali = vali(4) + "l" + vali(4) + "m" + vali(5);
        br.postPage("https://live.ksmobile.net/live/queryinfo", "userid=1&videoid=" + videoid + "&area=&h5=1&vali=" + vali);
        final Map<String, Object> response = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
        final Map<String, Object> data = (Map<String, Object>) response.get("data");
        final Map<String, Object> video_info = (Map<String, Object>) data.get("video_info");
        final Map<String, Object> user_info = (Map<String, Object>) data.get("user_info");
        final String title = (String) video_info.get("title");
        final String desc = (String) user_info.get("desc");
        final String url = (String) video_info.get("videosource");
        final Object videosize = video_info.get("videosize");
        final DownloadLink link;
        if (StringUtils.endsWithCaseInsensitive(url, "m3u8")) {
            link = createDownloadlink("m3u8" + url.substring(4));
        } else if (StringUtils.isNotEmpty(url)) {
            link = createDownloadlink(url);
        } else {
            ret.add(createOfflinelink(parameter));
            return ret;
        }
        if (videosize != null) {
            link.setDownloadSize(JavaScriptEngineFactory.toLong(videosize, -1));
        }
        if (StringUtils.isAllNotEmpty(title, desc)) {
            link.setFinalFileName(desc + "_" + title + ".mp4");
        } else if (StringUtils.isNotEmpty(title)) {
            link.setFinalFileName(title + ".mp4");
        } else if (StringUtils.isNotEmpty(desc)) {
            link.setFinalFileName(desc + ".mp4");
        }
        link.setContentUrl(param.getCryptedUrl());
        ret.add(link);
        return ret;
    }

    private String vali(int t) {
        // vali = t(4) + "l" + t(4) + "m" + t(5);
        // function t(t) {
        // t = t || 32;
        // for (var n = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678", e = n.length, A = "", r = 0; r < t; r++)
        // A += n.charAt(Math.floor(Math.random() * e));
        // return A
        // }
        final String n = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
        String A = "";
        for (int r = 0; r < t; r++) {
            A += n.charAt((int) Math.floor(Math.random() * n.length()));
        }
        return A;
    }

    private String getVideoID(String parameter) {
        String result = new Regex(parameter, "[&?]videoid=(\\d+)").getMatch(0);
        if (result == null) {
            result = new Regex(parameter, "/(\\d+)/index\\.html").getMatch(0);
        }
        return result;
    }
}
