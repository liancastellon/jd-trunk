//jDownloader - Downloadmanager
//Copyright (C) 2012  JD-Team support@jdownloader.org
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

import jd.PluginWrapper;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.jdownloader.plugins.components.antiDDoSForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "subscene.com" }, urls = { "https?://(\\w+\\.)?subscene\\.com/(subtitles/[a-z0-9\\-_]+/[a-z0-9\\-_]+/\\d+|[a-z0-9]+/[a-z0-9\\-]+/subtitle\\-\\d+\\.aspx)" })
public class SubSceneCom extends antiDDoSForHost {
    public SubSceneCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://subscene.com/";
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        try {
            getPage(link.getDownloadURL());
        } catch (PluginException e) {
            logger.log(e);
            if (Thread.currentThread() instanceof SingleDownloadController && e.getLinkStatus() == LinkStatus.ERROR_CAPTCHA) {
                return AvailableStatus.UNCHECKABLE;
            }
            throw e;
        }
        if (br.containsHTML("(>An error occurred while processing your request|>Server Error|>Page Not Found<)")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if ((br.containsHTML("<li class=\"deleted\">")) && (!br.containsHTML("mac"))) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String subtitleid = new Regex(link.getDownloadURL(), "subtitles/[a-z0-9\\-_]+/[a-z0-9\\-_]+/(\\d+)").getMatch(0);
        final String language = new Regex(link.getDownloadURL(), "subtitles/[a-z0-9\\-_]+/([a-z0-9\\-_]+)/").getMatch(0);
        String filename = br.getRegex("<strong>Release info[^<>\"]+</strong>([^\"]*?)</li>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<span itemprop=\"name\">([^<>\"]*?)</span>").getMatch(0);
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final String rlses[] = filename.split("\r\n\t\t\t\t\t\t\t<div>");
        if (rlses != null && rlses.length != 0) {
            for (String release : rlses) {
                release = release.trim();
                if (!release.equals("")) {
                    filename = release;
                    break;
                }
            }
        }
        filename = filename.replace("\r", "");
        filename = filename.replace("\t", "");
        filename = filename.replace("\n", "");
        filename = filename.replace("<div>", "").replace("</div>", "");
        filename = Encoding.htmlDecode(filename.trim());
        if (language != null) {
            filename += "_" + language;
        }
        if (subtitleid != null) {
            filename += "_" + subtitleid;
        }
        filename += ".zip";
        link.setName(filename);
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        String dllink = br.getRegex("\"(/subtitle/download\\?mac=[^<>\"]*?)\"").getMatch(0);
        if (dllink == null) {
            dllink = br.getRegex("class=\"download\">\\s*<a href=\"(/subtitles?/[^<>\"]*?)\"").getMatch(0);
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        // Resume and chunks disabled, not needed for such small files & can't
        // test
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setFinalFileName(Encoding.htmlDecode(getFileNameFromHeader(dl.getConnection())));
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}