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
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

import org.appwork.utils.Files;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.HexFormatter;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter.ExtensionsFilterInterface;
import org.jdownloader.plugins.components.hds.HDSContainer;
import org.jdownloader.plugins.controller.crawler.LazyCrawlerPlugin.FEATURE;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "f4m" }, urls = { "https?://.+\\.f4m($|\\?[^\\s<>\"']*|#.*)" })
public class GenericF4MDecrypter extends PluginForDecrypt {
    @Override
    public Boolean siteTesterDisabled() {
        return Boolean.TRUE;
    }

    public GenericF4MDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.GENERIC };
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
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
        final String urlName = getFileNameFromURL(new URL(param.getCryptedUrl()));
        final ArrayList<DownloadLink> ret = parse(this, br, param.getCryptedUrl(), null, referer, cookiesString);
        if (ret.size() > 1 && isValidURLName(urlName)) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(urlName);
            fp.addLinks(ret);
        }
        return ret;
    }

    public static ArrayList<DownloadLink> parse(final PluginForDecrypt plugin, final Browser br, final String url, final String name, final String referer, final String cookiesString) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        br.getPage(url);
        br.followRedirect();
        if (br.containsHTML("#EXTM3U")) {
            final DownloadLink m3u8 = new DownloadLink(null, null, plugin.getHost(), br.getURL(), true);
            m3u8.setProperty("Referer", referer);
            m3u8.setProperty("cookies", cookiesString);
            ret.add(m3u8);
            return ret;
        }
        final List<HDSContainer> containers = HDSContainer.getHDSQualities(br);
        if (containers != null) {
            final String urlName = getFileNameFromURL(br._getURL());
            final String linkURL = "f4m" + url.substring(4);
            for (final HDSContainer container : containers) {
                final DownloadLink link = new DownloadLink(null, null, plugin.getHost(), linkURL, true);
                link.setProperty("Referer", referer);
                link.setProperty("cookies", cookiesString);
                String fileName = null;
                final ExtensionsFilterInterface fileType;
                if (container.getId() != null) {
                    fileType = CompiledFiletypeFilter.getExtensionsFilterInterface(Files.getExtension(container.getId()));
                } else {
                    fileType = null;
                }
                if (name != null) {
                    fileName = name;
                } else if (fileType != null) {
                    fileName = container.getId();
                } else {
                    if (!isValidURLName(urlName)) {
                        fileName = "Unknown";
                    } else {
                        fileName = urlName.replaceAll("\\.f4m", "");
                    }
                }
                if (container.getHeight() != -1 && container.getWidth() != -1) {
                    fileName += "_" + container.getWidth() + "x" + container.getHeight();
                }
                if (container.getBitrate() != -1) {
                    fileName += "_br" + container.getBitrate();
                }
                if (fileType == null) {
                    link.setFinalFileName(fileName + ".mp4");
                } else {
                    link.setFinalFileName(fileName);
                }
                link.setAvailable(true);
                if (container.getEstimatedFileSize() > 0) {
                    link.setDownloadSize(container.getEstimatedFileSize());
                }
                link.setLinkID("f4m://" + br.getHost() + "/" + container.getInternalID());
                container.write(link, null);
                ret.add(link);
            }
        }
        return ret;
    }

    private static final boolean isValidURLName(final String urlName) {
        return urlName != null && !"manifest.f4m".equalsIgnoreCase(urlName);
    }
}