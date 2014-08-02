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
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "inclouddrive.com" }, urls = { "https?://(www\\.)?inclouddrive\\.com/(link_download/\\?token=[A-Za-z0-9=_]+|(#/)?(file_download|file|link)/[0-9a-zA-Z=_-]+)" }, flags = { 0 })
public class InCloudDriveCom extends PluginForHost {

    // DEV NOTE:
    // links are not correctable to a standard url format

    public InCloudDriveCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "https://www.inclouddrive.com/#/terms_condition";
    }

    private int    link_type;
    private String fuid;

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        setFUID(link);
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        if (link_type == 1) {
            br.postPage("https://www.inclouddrive.com/index.php/link", "user_id=&user_loged_in=no&link_value=" + Encoding.urlEncode(fuid));
        } else if (link_type == 2) {
            // br.postPage("https://www.inclouddrive.com/index.php/file_download/" + fuid, "user_id=");
            // https://www.inclouddrive.com/#/link/MTM0QEBAQEBAQEAzNA - doesn't wprk with above way
            br.postPage("https://www.inclouddrive.com/index.php/link", "user_id=&user_loged_in=no&link_value=" + Encoding.urlEncode(fuid));
        } else {
            // unsupported type
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (br.containsHTML(">A Database Error Occurred<|This link has been removed from system.")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String filename = br.getRegex("class=\"propreties-file-count\">[\t\n\r ]+<b>([^<>\"]+)</b>").getMatch(0);
        final String filesize = br.getRegex(">Total size:</span><span class=\"propreties-dark-txt\">([^<>\"]+)</span>").getMatch(0);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(encodeUnicode(Encoding.htmlDecode(filename.trim())));
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        String dllink = checkDirectLink(downloadLink, "directlink");
        if (dllink == null) {
            final String uplid = br.getRegex("uploader_id=\"(\\d+)\"").getMatch(0);
            final String fileid = br.getRegex("file_id=\"(\\d+)\"").getMatch(0);
            if (uplid == null || fileid == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.postPage("https://www.inclouddrive.com/index.php/download_page_captcha", "type=yes");
            for (int i = 1; i <= 5; i++) {
                final String code = getCaptchaCode("https://www.inclouddrive.com/captcha/php/captcha.php", downloadLink);
                br.postPage("https://www.inclouddrive.com/captcha/php/check_captcha.php", "captcha_code=" + Encoding.urlEncode(code));
                if (br.toString().equals("not_match")) {
                    continue;
                }
                break;
            }
            if (br.toString().equals("not_match")) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            br.postPage("https://www.inclouddrive.com/index.php/get_download_server/download_page_link", "contact_id=" + uplid + "&table_id=" + fileid);
            dllink = br.toString();
            if (dllink == null || !dllink.startsWith("http")) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            // dllink = Encoding.htmlDecode(dllink);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty("directlink", dllink);
        dl.startDownload();
    }

    private void setFUID(final DownloadLink dl) {
        fuid = new Regex(dl.getDownloadURL(), "/link_download/\\?token=([A-Za-z0-9=_]+)").getMatch(0);
        if (fuid != null) {
            link_type = 1;
        }
        if (fuid == null) {
            fuid = new Regex(dl.getDownloadURL(), "/(?:#/)?(?:file_download|file|link)/([0-9a-zA-Z_-]+)").getMatch(0);
            if (fuid != null) {
                link_type = 2;
            }
        }
        if (fuid != null) {
            try {
                dl.setLinkID(fuid);
            } catch (final Throwable e) {
            }
        }
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    /* Avoid chars which are not allowed in filenames under certain OS' */
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

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

}