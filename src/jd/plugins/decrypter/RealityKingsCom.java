//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;
import jd.utils.JDUtilities;

import org.appwork.utils.formatter.SizeFormatter;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "realitykings.com" }, urls = { "https?://(?:new\\.)?members\\.realitykings\\.com/video/(?:full/\\d+(?:/[a-z0-9\\-_]+/?)?|pics/\\d+(?:/[a-z0-9\\-_]+/?)?)" })
public class RealityKingsCom extends PluginForDecrypt {

    public RealityKingsCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String TYPE_VIDEO = "https?://(?:new\\.)?members\\.realitykings\\.com/video/full/\\d+(?:/[a-z0-9\\-_]+/?)?";
    private static final String TYPE_PHOTO = "https?://(?:new\\.)?members\\.realitykings\\.com/video/pics/\\d+(?:/[a-z0-9\\-_]+/?)?";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        final String fid = new Regex(parameter, "/(\\d+)/").getMatch(0);
        // Login if possible
        if (!getUserLogin(false)) {
            logger.info("No account present --> Cannot decrypt anything!");
            return decryptedLinks;
        }
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            final DownloadLink offline = this.createOfflinelink(parameter);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        String title = br.getRegex("<title>([^<>\"]*?) / Reality Kings</title>").getMatch(0);
        if (title == null) {
            /* Fallback to id from inside url */
            title = fid;
        }
        if (parameter.matches(TYPE_VIDEO)) {
            final String base_url = new Regex(this.br.getURL(), "(https?://[^/]+)/").getMatch(0);
            final String htmldownload = this.br.getRegex("class=\"fa fa\\-download\"></i>[^<>]*?</div>(.*?)</div>").getMatch(0);
            final String[] dlinfo = htmldownload.split("</a>");
            for (final String video : dlinfo) {
                final String dlurl = new Regex(video, "\"(/[^<>\"]*?download/[^<>\"]+/)\"").getMatch(0);
                final String quality = new Regex(video, "<span>([^<>\"]+)</span>").getMatch(0);
                final String filesize = new Regex(video, "<var>([^<>\"]+)</var>").getMatch(0);
                if (dlurl == null || quality == null) {
                    continue;
                }
                final DownloadLink dl = this.createDownloadlink(base_url + dlurl);
                if (filesize != null) {
                    dl.setDownloadSize(SizeFormatter.getSize(filesize));
                    dl.setAvailable(true);
                }
                dl.setName(title + "_" + quality + ".mp4");
                dl.setProperty("fid", fid);
                dl.setProperty("quality", quality);
                decryptedLinks.add(dl);
            }
        } else {
            final String pictures[] = getPictureArray(this.br);
            for (String finallink : pictures) {
                final String number_formatted = new Regex(finallink, "(\\d+)\\.jpg").getMatch(0);
                finallink = finallink.replaceAll("https?://", "http://realitykingsdecrypted");
                final DownloadLink dl = this.createDownloadlink(finallink);
                dl.setFinalFileName(title + "_" + number_formatted + ".jpg");
                dl.setAvailable(true);
                dl.setProperty("fid", fid);
                dl.setProperty("picnumber_formatted", number_formatted);
                decryptedLinks.add(dl);
            }
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(title);
        fp.addLinks(decryptedLinks);

        return decryptedLinks;
    }

    /**
     * JD2 CODE: DO NOIT USE OVERRIDE FÒR COMPATIBILITY REASONS!!!!!
     */
    public boolean isProxyRotationEnabledForLinkCrawler() {
        return false;
    }

    private boolean getUserLogin(final boolean force) throws Exception {
        final PluginForHost hostPlugin = JDUtilities.getPluginForHost(this.getHost());
        final Account aa = AccountController.getInstance().getValidAccount(hostPlugin);
        if (aa == null) {
            logger.warning("There is no account available, stopping...");
            return false;
        }
        try {
            ((jd.plugins.hoster.RealityKingsCom) hostPlugin).login(this.br, aa, force);
        } catch (final PluginException e) {

            aa.setValid(false);
            return false;
        }
        return true;
    }

    public static String[] getPictureArray(final Browser br) {
        final String[] picarray = br.getRegex("data\\-flickity\\-lazyload=\"(http://[^<>\"]+\\d+\\.jpg[^<>\"]+nvb=[^<>\"]+)\"").getColumn(0);
        return picarray;
    }

    public static String getVideoUrlFree(final String fid) {
        return "http://www.realitykings.com/tour/video/watch/" + fid + "/";
    }

    public static String getVideoUrlPremium(final String fid) {
        return "http://new.members.realitykings.com/video/full/" + fid + "/";
    }

    public static String getPicUrl(final String fid) {
        return "http://new.members.realitykings.com/video/pics/" + fid + "/";
    }

    public static boolean isOffline(final Browser br) {
        return br.getHttpConnection().getResponseCode() == 404;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.PornPortal;
    }

}