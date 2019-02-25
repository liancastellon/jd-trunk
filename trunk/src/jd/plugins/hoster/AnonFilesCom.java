//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
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
package jd.plugins.hoster;

import java.io.IOException;

import jd.PluginWrapper;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "anonfiles.com" }, urls = { "https?://(?:www\\.)?anonfiles?\\.com/(?:file/)?[A-Za-z0-9]+" })
public class AnonFilesCom extends PluginForHost {
    public AnonFilesCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "https://anonfiles.com/terms";
    }

    // do not add @Override here to keep 0.* compatibility
    public boolean hasAutoCaptcha() {
        return false;
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return false;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("free"))) {
            /* free accounts also have captchas */
            return false;
        }
        return false;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.setCookie(this.getHost(), "lang", "us");
        br.getPage("https://anonfiles.com/api/v2/file/" + new Regex(link.getPluginPatternMatcher(), "([A-Za-z0-9]+)$").getMatch(0) + "/info");
        if (!br.containsHTML("\"status\":true") || br.getHttpConnection().getResponseCode() == 404) {
            br.getPage(link.getPluginPatternMatcher());
            final String filename = br.getRegex("<h1 class=\"text-center text-wordwrap\"\\s*>\\s*(.*?)\\s*</h1>").getMatch(0);
            final String filesize = br.getRegex(">\\s*Download\\s*\\(([0-9\\.]+\\s*[TBKMG]+)\\)\\s*<").getMatch(0);
            if (filename != null && filesize != null) {
                link.setName(Encoding.htmlDecode(filename.trim()));
                link.setDownloadSize(SizeFormatter.getSize(filesize));
                return AvailableStatus.TRUE;
            }
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else {
            final String filename = PluginJSonUtils.getJson(br, "name");
            final String filesize = PluginJSonUtils.getJson(br, "bytes");
            if (filename == null || filesize == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            link.setName(Encoding.htmlDecode(filename.trim()));
            link.setDownloadSize(Long.parseLong(filesize));
            return AvailableStatus.TRUE;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        br.getPage(downloadLink.getDownloadURL());
        /* Check this, maybe API fails sometimes */
        if (br.containsHTML(">The file you are looking for does not") || br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String dllink = br.getRegex("\"(https?://[a-z0-9\\-\\.]+\\.anon[^/\"]+/[^<>\"]+)\"").getMatch(0);
        if (dllink == null) {
            dllink = this.br.getRegex("id=\"download\\-url\" class=\"btn btn\\-primary btn\\-block\" href=\"(https[^<>\"]*?)\"").getMatch(0);
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 30 * 60 * 1000l);
        }
        fixFilename(downloadLink);
        dl.startDownload();
    }

    private void fixFilename(final DownloadLink downloadLink) {
        String oldName = downloadLink.getFinalFileName();
        if (oldName == null) {
            oldName = downloadLink.getName();
        }
        final String serverFilename = Encoding.htmlDecode(getFileNameFromHeader(dl.getConnection()));
        String newExtension = null;
        // some streaming sites do not provide proper file.extension within headers (Content-Disposition or the fail over getURL()).
        if (serverFilename.contains(".")) {
            newExtension = serverFilename.substring(serverFilename.lastIndexOf("."));
        } else {
            logger.info("HTTP headers don't contain filename.extension information");
        }
        if (newExtension != null && !oldName.endsWith(newExtension)) {
            String oldExtension = null;
            if (oldName.contains(".")) {
                oldExtension = oldName.substring(oldName.lastIndexOf("."));
            }
            if (oldExtension != null && oldExtension.length() <= 5) {
                downloadLink.setFinalFileName(oldName.replace(oldExtension, newExtension));
            } else {
                downloadLink.setFinalFileName(oldName + newExtension);
            }
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }
}