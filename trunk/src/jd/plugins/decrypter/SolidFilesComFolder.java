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

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Request;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "solidfiles.com" }, urls = { "https?://(?:www\\.)?solidfiles\\.com/(?:folder|v)/[a-z0-9]+/?" })
public class SolidFilesComFolder extends PluginForDecrypt {
    public SolidFilesComFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        br.openGetConnection(parameter);
        if (!br.getHttpConnection().getContentType().contains("text/html") || br.getHttpConnection().getContentLength() > br.getLoadLimit()) {
            br.getHttpConnection().disconnect();
            // direct downloadable
            final DownloadLink dl = createDownloadlink(parameter);
            dl.setProperty("directDownload", true);
            final String fileName = getFileNameFromDispositionHeader(br.getHttpConnection());
            if (fileName != null) {
                dl.setFinalFileName(fileName);
            } else {
                dl.setName(getFileNameFromHeader(br.getHttpConnection()));
            }
            dl.setVerifiedFileSize(br.getHttpConnection().getContentLength());
            dl.setAvailable(true);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        br.followConnection();
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML(">Not found<|>We couldn't find the file you requested|>This folder is empty\\.<|This file/folder has been disabled")) {
            logger.info("Link offline: " + parameter);
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String fpName = br.getRegex("<title>([^<>\"]*?)(?:-|\\|) Solidfiles</title>").getMatch(0);
        if (fpName == null) {
            fpName = new Regex(parameter, "([a-z0-9]+)/$").getMatch(0);
        }
        final PluginForHost solidfiles_host = JDUtilities.getPluginForHost("solidfiles.com");
        final boolean decryptFolders = solidfiles_host.getPluginConfig().getBooleanProperty(jd.plugins.hoster.SolidFilesCom.DECRYPTFOLDERS, false);
        String filelist = br.getRegex("<ul>(.+?)</ul>").getMatch(0);
        String[] finfos = new Regex(filelist, "(<a href=(?:'|\"|).*?</a>)").getColumn(0);
        if (finfos == null || finfos.length == 0) {
            if (br.containsHTML("id=\"file-list\"")) {
                logger.info("Empty folder: " + parameter);
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            } else {
                // single file-let hoster plugin take over
                decryptedLinks.add(this.createDownloadlink(parameter));
                return decryptedLinks;
            }
        }
        if (finfos != null && finfos.length != 0) {
            for (final String finfo : finfos) {
                final Regex urlfilename = new Regex(finfo, "<a href=(\"|')(/(?:d|v|folder)/.*?)\\1.*?>([^<>]+)</a>");
                String url = urlfilename.getMatch(1);
                String filename = urlfilename.getMatch(2);
                // final String filesize = new Regex(finfo, "(\\d+(?:\\.\\d+)? ?(bytes|KB|MB|GB))").getMatch(0);
                if (url == null || filename == null) {
                    logger.info("finfo: " + finfo);
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                if (url.startsWith("/folder/")) {
                    if (decryptFolders) {
                        url = Request.getLocation(url, br.getRequest());
                        decryptedLinks.add(createDownloadlink(url));
                    }
                } else {
                    url = Request.getLocation(url, br.getRequest());
                    final DownloadLink dl = createDownloadlink(url);
                    filename = Encoding.htmlDecode(filename);
                    dl.setName(filename.replace(" ", "_"));// spaces are replaced by _
                    // dl.setDownloadSize(SizeFormatter.getSize(filesize));
                    dl.setAvailable(true);
                    decryptedLinks.add(dl);
                }
            }
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(Encoding.htmlDecode(fpName.trim()));
        fp.addLinks(decryptedLinks);
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}