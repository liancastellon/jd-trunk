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
package jd.plugins.hoster;

import java.io.IOException;
import java.util.Date;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.components.antiDDoSForHost;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "highporn.net" }, urls = { "highporndecrypted://\\d+" })
public class HighpornNet extends antiDDoSForHost {
    @Override
    public String[] siteSupportedNames() {
        return new String[] { "highporn.net", "tanix.net" };
    }

    public HighpornNet(PluginWrapper wrapper) {
        super(wrapper);
        setConfigElements();
    }

    /* DEV NOTES */
    // Tags:
    // protocol: no https
    // other:
    /* Extension which will be used if no correct extension is found */
    private static final String default_extension = ".mp4";
    /* Connection stuff */
    private final int           free_maxchunks    = 1;
    private final int           free_maxdownloads = 1;
    private String              dllink            = null;
    private String              fid               = null;
    private boolean             server_issues     = false;
    private SubConfiguration    cfg               = getPluginConfig();

    @Override
    public String getAGBLink() {
        return "http://highporn.net/static/terms";
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        server_issues = false;
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final String url_source = link.getStringProperty("mainlink", null);
        if (url_source == null) {
            /* Should never happen */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        getPage(url_source);
        dllink = br.getRegex("data-src=\"(http[^<>\"]+)\"").getMatch(0); // If single link, no videoID
        if (jd.plugins.decrypter.HighpornNet.isOffline(br)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        fid = new Regex(link.getDownloadURL(), "(\\d+)$").getMatch(0);
        String filename = link.getStringProperty("decryptername");
        if (filename == null) {
            /* Fallback */
            filename = fid;
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        final String ext;
        if (dllink != null) {
            ext = getFileNameExtensionFromString(dllink, default_extension);
        } else {
            ext = default_extension;
        }
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        /*
         * 2017-06-01: Disabled filesize check as this could lead to download issues as this host only allows a total of 1
         * stream-connection.
         */
        boolean Allow_filesize_check = cfg.getBooleanProperty("Allow_filesize_check", false);
        logger.info("Allow_filesize_check: " + Allow_filesize_check);
        if (dllink != null && Allow_filesize_check) {
            dllink = Encoding.htmlDecode(dllink);
            link.setFinalFileName(filename);
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                con = br2.openHeadConnection(dllink);
                if (!con.getContentType().contains("html")) {
                    link.setDownloadSize(con.getLongContentLength());
                    link.setProperty("directlink", dllink);
                } else {
                    server_issues = true;
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        } else {
            /* We cannot be sure whether we have the correct extension or not! */
            link.setName(filename);
        }
        PostRequest postRequest = new PostRequest("http://play.openhub.tv/playurl?random=" + (new Date().getTime() / 1000));
        postRequest.setContentType("application/x-www-form-urlencoded");
        postRequest.put("v", fid);
        postRequest.put("source_play", "highporn");
        String file = br.getPage(postRequest);
        final URLConnectionAdapter con = br.cloneBrowser().openHeadConnection(file);
        try {
            if (con.getResponseCode() == 200 && con.getLongContentLength() > 0 && !StringUtils.contains(con.getContentType(), "html")) {
                link.setVerifiedFileSize(con.getCompleteContentLength());
            }
        } finally {
            con.disconnect();
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        final boolean resumes = cfg.getBooleanProperty("Allow_resume", true);
        logger.info("resumes: " + resumes);
        dllink = downloadLink.getStringProperty("directlink");
        fid = new Regex(downloadLink.getDownloadURL(), "(\\d+)$").getMatch(0);
        PostRequest postRequest = new PostRequest("http://play.openhub.tv/playurl?random=" + (new Date().getTime() / 1000));
        postRequest.setContentType("application/x-www-form-urlencoded");
        postRequest.addVariable("v", fid);
        postRequest.addVariable("source_play", "highporn");
        dllink = br.getPage(postRequest);
        if (dllink != null) {
            // cached downloadlink doesn't have a browser session, which leads to 403.
            br.getHeaders().put("Referer", downloadLink.getStringProperty("mainlink", null));
            dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, dllink, resumes, free_maxchunks);
            if (dl.getConnection().getContentType().contains("html") || dl.getConnection().getResponseCode() == 403 || dl.getConnection().getLongContentLength() == -1 || (dl.getConnection().getLongContentLength() < 10 && dl.getConnection().getContentType().equals("application/octet-stream"))) {
                downloadLink.setProperty("directlink", Property.NULL);
                dllink = null;
                try {
                    dl.getConnection().disconnect();
                } catch (final Throwable e) {
                }
                dl = null;
                br = new Browser();
            }
        }
        if (dllink == null) {
            requestFileInformation(downloadLink);
            if (dllink == null) {
                final Browser br = this.br.cloneBrowser();
                br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                postPage(br, "/play.php", "v=" + fid);
                dllink = br.toString();
                if (br.toString().equals("fail")) {
                    server_issues = true;
                }
            }
        }
        if (server_issues) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 10 * 60 * 1000l);
        } else if (dllink == null || !dllink.startsWith("http")) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (dl == null) {
            dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, dllink, resumes, free_maxchunks);
            if (dl.getConnection().getContentType().contains("html")) {
                try {
                    dl.getConnection().setAllowedResponseCodes(new int[] { dl.getConnection().getResponseCode() });
                    br.followConnection();
                } catch (final IOException e) {
                    logger.log(e);
                }
                if (dl.getConnection().getResponseCode() == 403) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
                } else if (dl.getConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
                }
                try {
                    dl.getConnection().disconnect();
                } catch (final Throwable e) {
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        downloadLink.setProperty("directlink", dllink);
        dl.startDownload();
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "", JDL.L("plugins.hoster.HighpornNet.Allow_resume", "Allow resume")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "Allow_filesize_check", JDL.L("plugins.hoster.HighpornNet.Allow_filesize_check", "Allow filesize check")).setDefaultValue(false));
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_maxdownloads;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    public void getPage(final String page) throws Exception {
        super.getPage(page);
    }
}
