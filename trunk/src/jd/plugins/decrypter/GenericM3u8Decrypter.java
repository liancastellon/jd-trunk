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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.controlling.linkcrawler.CrawledLink;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.http.requests.HeadRequest;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.HexFormatter;
import org.jdownloader.plugins.controller.crawler.LazyCrawlerPlugin.FEATURE;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "m3u8" }, urls = { "https?://.+\\.m3u8($|\\?[^\\s<>\"']*|#.*)" })
public class GenericM3u8Decrypter extends PluginForDecrypt {
    @Override
    public Boolean siteTesterDisabled() {
        return Boolean.TRUE;
    }

    public GenericM3u8Decrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.GENERIC };
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        CrawledLink source = getCurrentLink();
        String referer = null;
        String cookiesString = null;
        while (source != null) {
            if (source.getDownloadLink() != null && StringUtils.equals(source.getURL(), param.getCryptedUrl())) {
                final DownloadLink downloadLink = source.getDownloadLink();
                cookiesString = downloadLink.getStringProperty("cookies", null);
                if (cookiesString != null) {
                    final String host = Browser.getHost(source.getURL());
                    br.setCookies(host, Cookies.parseCookies(cookiesString, host, null));
                }
            }
            if (!StringUtils.equals(source.getURL(), param.getCryptedUrl())) {
                if (source.getCryptedLink() != null) {
                    referer = source.getURL();
                    br.getPage(source.getURL());
                }
                break;
            } else {
                source = source.getSourceLink();
            }
        }
        String forced_referer = new Regex(param.getCryptedUrl(), "((\\&|\\?|#)forced_referer=.+)").getMatch(0);
        if (forced_referer != null) {
            forced_referer = new Regex(forced_referer, "forced_referer=([A-Za-z0-9=]+)").getMatch(0);
            if (forced_referer != null) {
                String ref = null;
                if (forced_referer.matches("^[a-fA-F0-9]+$") && forced_referer.length() % 2 == 0) {
                    final byte[] bytes = HexFormatter.hexToByteArray(forced_referer);
                    ref = bytes != null ? new String(bytes) : null;
                }
                if (ref == null) {
                    ref = Encoding.Base64Decode(forced_referer);
                }
                if (ref != null) {
                    try {
                        br.getPage(ref);
                        referer = ref;
                    } catch (final IOException e) {
                        logger.log(e);
                    }
                }
            }
        }
        br.getPage(param.getCryptedUrl());
        br.followRedirect();
        if (br.getHttpConnection() == null || br.getHttpConnection().getResponseCode() == 403 || br.getHttpConnection().getResponseCode() == 404) {
            // invalid link
            return ret;
        }
        return parseM3U8(this, param.getCryptedUrl(), br, referer, cookiesString, null, null);
    }

    public static ArrayList<DownloadLink> parseM3U8(final PluginForDecrypt plugin, final String m3u8URL, final Browser br, final String referer, final String cookiesString, final String finalName, final String preSetName) throws IOException {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        if (br.containsHTML("#EXT-X-STREAM-INF")) {
            final ArrayList<String> infos = new ArrayList<String>();
            for (final String line : Regex.getLines(br.toString())) {
                if (StringUtils.startsWithCaseInsensitive(line, "concat") || StringUtils.contains(line, "file:")) {
                    continue;
                } else if (!line.startsWith("#")) {
                    final URL url = br.getURL(line);
                    final DownloadLink link = new DownloadLink(null, null, plugin.getHost(), url.toString(), true);
                    if (finalName != null) {
                        link.setFinalFileName(finalName);
                    }
                    link.setProperty("preSetName", preSetName);
                    link.setProperty("Referer", referer);
                    link.setProperty("cookies", cookiesString);
                    addToResults(plugin, ret, br, url, link);
                    infos.clear();
                } else {
                    infos.add(line);
                }
            }
        } else {
            final DownloadLink link = new DownloadLink(null, null, plugin.getHost(), "m3u8" + m3u8URL.substring(4), true);
            if (finalName != null) {
                link.setFinalFileName(finalName);
            }
            link.setProperty("preSetName", preSetName);
            link.setProperty("Referer", referer);
            link.setProperty("cookies", cookiesString);
            ret.add(link);
        }
        return ret;
    }

    private static void addToResults(final PluginForDecrypt plugin, final List<DownloadLink> results, final Browser br, final URL url, final DownloadLink link) {
        if (StringUtils.endsWithCaseInsensitive(url.getPath(), ".m3u8")) {
            results.add(link);
        } else {
            final Browser brc = br.cloneBrowser();
            brc.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            try {
                con = brc.openRequestConnection(new HeadRequest(url));
                if (con.isOK() && (StringUtils.equalsIgnoreCase(con.getContentType(), "application/vnd.apple.mpegurl") || StringUtils.endsWithCaseInsensitive(url.getPath(), ".m3u8"))) {
                    link.setPluginPatternMatcher("m3u8" + url.toString().substring(4));
                    results.add(link);
                }
            } catch (final Throwable e) {
                plugin.getLogger().log(e);
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        }
    }
}