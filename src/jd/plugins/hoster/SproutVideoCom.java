package jd.plugins.hoster;

import java.util.LinkedHashMap;

import org.appwork.utils.StringUtils;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.parser.html.InputField;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

/**
 *
 * note: backend cloudfront
 *
 * @author raztoki
 *
 */
@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "sproutvideo.com" }, urls = { "https?://(?:www\\.)?(?:videos\\.sproutvideo\\.com/embed/[a-f0-9]{18}/[a-f0-9]{16}|\\w+\\.vids\\.io/videos/[a-f0-9]{18})" })
public class SproutVideoCom extends PluginForHost {
    public SproutVideoCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return null;
    }

    private String dllink;

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        br = new Browser();
        dllink = null;
        br.setFollowRedirects(true);
        br.getPage(link.getPluginPatternMatcher());
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML(">404 Not Found")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        // if (!br.getURL().contains("/embed/")) {
        if (containsPassword()) {
            /* Handle password on downloadstart */
            return AvailableStatus.TRUE;
        }
        parseDownloadInfo(link);
        return AvailableStatus.TRUE;
    }

    private boolean containsPassword() {
        final boolean result = br.getHttpConnection().getResponseCode() == 403 && br.containsHTML("<title>Password Protected");
        return result;
    }

    private void parseDownloadInfo(final DownloadLink link) throws Exception {
        String http_downloadurl = br.getRegex("class=\\'hd\\-download\\'[^<>]+href=\"(http[^<>\"]+)\"").getMatch(0);
        String json = br.getRegex("var dat\\s*=\\s*'([a-zA-Z0-9-=_/+]+)'").getMatch(0);
        if (json == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        json = Encoding.Base64Decode(json);
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json);
        final Boolean hls = (Boolean) entries.get("hls");
        final String title = (String) entries.get("title");
        if (StringUtils.isEmpty(title)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!StringUtils.isEmpty(http_downloadurl)) {
            http_downloadurl = http_downloadurl.replace("&amp;", "&");
            dllink = http_downloadurl;
        } else {
            String m3u;
            if (!Boolean.TRUE.equals(hls)) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final String userHash = (String) entries.get("s3_user_hash");
            final String videoHash = (String) entries.get("s3_video_hash");
            // / cant use cdSig, its wrong
            entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "signatures/m");
            final String cfPolicy = (String) entries.get("CloudFront-Policy");
            final String cfSignature = (String) entries.get("CloudFront-Signature");
            final String cfKeyPairId = (String) entries.get("CloudFront-Key-Pair-Id");
            m3u = "https://hls2.videos.sproutvideo.com/" + userHash + "/" + videoHash + "/video/index.m3u8?Policy=" + cfPolicy + "&Signature=" + cfSignature + "&Key-Pair-Id=" + cfKeyPairId;
            dllink = m3u;
        }
        link.setName(title + ".mp4");
        link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        if (containsPassword()) {
            Form f = null;
            String password = link.getDownloadPassword();
            f = br.getFormByInputFieldPropertyKeyValue("id", "password");
            f = br.getFormbyProperty("name", "host");
            f = br.getFormByInputFieldPropertyKeyValue("name", "host");
            final Form[] allForms = br.getForms();
            for (final Form aForm : allForms) {
                final InputField inputFieldOP = aForm.getInputFieldByName("password");
                if (inputFieldOP != null) {
                    f = aForm;
                    break;
                }
            }
            /* No stored pw available? Ask user. */
            if (password == null) {
                password = Plugin.getUserInput("Password?", link);
            }
            logger.info("f: " + f);
            logger.info("password: " + Encoding.urlEncode(password));
            f.put("password", Encoding.urlEncode(password));
            br.submitForm(f);
            if (containsPassword()) {
                // since we dont have a password linkstatus
                link.setDownloadPassword(null);
                throw new PluginException(LinkStatus.ERROR_FATAL, "Incorrect Password");
            }
            link.setDownloadPassword(password);
            // }
            // embed required, you can get it from omebed json/xml or iframe or 'meta content twitter'
            // prefer iframe as it can have ?type=hd parameter
            if (!link.getDownloadURL().contains("embed")) {
                String embed = br.getRegex("<iframe src=('|\"|)(.*?)\\1 ").getMatch(1);
                if (StringUtils.isEmpty(embed)) {
                    embed = br.getRegex("<meta content=('|\"|)(.*?)\\1 name=\"twitter:player\"").getMatch(1);
                }
                if (embed == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.getPage(embed);
            }
            /* Do this now as we've skipped this before because a password was required. */
            parseDownloadInfo(link);
        }
        if (dllink.contains(".m3u8")) {
            /* Download stream */
            final HlsContainer hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(br, dllink));
            String dllink = hlsbest.getDownloadurl();
            String betterFilename = link.getName();
            betterFilename = betterFilename.replace(".mp4", "") + "-" + hlsbest.getHeight() + "p.mp4";
            link.setName(betterFilename);
            checkFFmpeg(link, "Download a HLS Stream");
            dl = new HLSDownloader(link, br, dllink, this.dllink.substring(this.dllink.indexOf("?")));
            dl.startDownload();
        } else {
            /* Official download */
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
            if (dl.getConnection().getContentType().contains("html") || dl.getConnection().getContentType().contains("json")) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            this.dl.startDownload();
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
