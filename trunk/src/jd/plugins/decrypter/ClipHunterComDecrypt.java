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
import java.util.LinkedHashMap;
import java.util.Random;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "cliphunter.com" }, urls = { "http://(www\\.)?cliphunter\\.com/w/\\d+/\\w+" }, flags = { 0 })
public class ClipHunterComDecrypt extends PluginForDecrypt {

    public ClipHunterComDecrypt(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* [0] = Value for LinkedHashMap, [1] or higher = To display in filename. */
    /**
     * sync with hoster
     */
    private final String[][] qualities = { { "_fhd.mp4", "_p1080" }, { "_hd.mp4", "_p720" }, { "_h.flv", "540p.flv", "_p540" }, { "_p.mp4", "_p480", "480p.mp4" }, { "_l.flv", "_p360", "360pflv.flv" }, { "_i.mp4", "360p.mp4" } };

    @SuppressWarnings("deprecation")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        br.setCookie("cliphunter.com", "qchange", "h");
        br.setAllowedResponseCodes(410);
        br.getPage(parameter);
        if (br.getURL().contains("error/missing") || br.containsHTML("(>Ooops, This Video is not available|>This video was removed and is no longer available at our site|<title></title>|var flashVars = \\{d: \\'\\'\\};)") || this.br.getHttpConnection().getResponseCode() == 404 || this.br.getHttpConnection().getResponseCode() == 410) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            dl.setContentUrl(parameter);
            dl.setName(new Regex(parameter, "cliphunter\\.com/w/\\d+/(.+)").getMatch(0));
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        String filename = br.getRegex("<title>(.*?) \\-.*?</title>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<h1 style=\"font\\-size: 2em;\">(.*?) </h1>").getMatch(0);
        }
        if (filename == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        filename = Encoding.htmlDecode(filename.trim());

        final LinkedHashMap<String, String> foundQualities = jd.plugins.hoster.ClipHunterCom.findAvailableVideoQualities(this.br);
        if (foundQualities == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        } else if (foundQualities.isEmpty()) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            dl.setContentUrl(parameter);
            dl.setName(new Regex(parameter, "cliphunter\\.com/w/\\d+/(.+)").getMatch(0));
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        /** Decrypt qualities, selected by the user */
        final ArrayList<String> selectedQualities = new ArrayList<String>();
        final SubConfiguration cfg = SubConfiguration.getConfig("cliphunter.com");
        boolean q360p = cfg.getBooleanProperty("ALLOW_360P", false);
        boolean q360pflv = cfg.getBooleanProperty("ALLOW_360PFLV", false);
        boolean q480p = cfg.getBooleanProperty("ALLOW_480P", false);
        boolean q540p = cfg.getBooleanProperty("ALLOW_540P", false);
        boolean q720p = cfg.getBooleanProperty("ALLOW_720P", false);
        boolean q1080p = cfg.getBooleanProperty("ALLOW_1080P", false);
        if (q360p == false && q360pflv == false && q480p == false && q540p == false && q720p == false && q1080p == false) {
            q360p = true;
            q360pflv = true;
            q480p = true;
            q540p = true;
            q720p = true;
            q1080p = true;
        }
        if (q360p) {
            selectedQualities.add("_i.mp4");
        }
        if (q360pflv) {
            selectedQualities.add("_l.flv");
        }
        if (q480p) {
            selectedQualities.add("_p.mp4");
        }
        if (q540p) {
            selectedQualities.add("_h.flv");
        }
        if (q720p) {
            selectedQualities.add("_hd.mp4");
        }
        if (q1080p) {
            selectedQualities.add("_fhd.mp4");
        }
        final boolean fastLinkcheck = cfg.getBooleanProperty("FASTLINKCHECK", false);
        if (cfg.getBooleanProperty("ALLOW_BEST", false)) {
            String dllink = null;
            for (final String quality[] : qualities) {
                dllink = foundQualities.get(quality[0]);
                if (dllink != null) {
                    final DownloadLink dl = createDownloadlink("http://cliphunterdecrypted.com/" + System.currentTimeMillis() + new Random().nextInt(100000));
                    dl.setContentUrl(parameter);
                    dl.setFinalFileName(filename + "_" + quality[1]);
                    dl.setProperty("directlink", dllink);
                    dl.setProperty("originallink", parameter);
                    dl.setProperty("selectedquality", quality[0]);
                    if (fastLinkcheck) {
                        dl.setAvailable(true);
                    }
                    decryptedLinks.add(dl);
                    break;
                }
            }
        } else {
            for (final String selectedQuality : selectedQualities) {
                final String dllink = foundQualities.get(selectedQuality);
                for (final String[] quality : qualities) {
                    final String currentQualityValue = quality[0];
                    if ((currentQualityValue == selectedQuality) && dllink != null) {
                        final DownloadLink dl = createDownloadlink("http://cliphunterdecrypted.com/" + System.currentTimeMillis() + new Random().nextInt(100000));
                        dl.setContentUrl(parameter);
                        dl.setFinalFileName(filename + "_" + quality[1]);
                        dl.setProperty("directlink", dllink);
                        dl.setProperty("originallink", parameter);
                        dl.setProperty("selectedquality", quality[0]);
                        if (fastLinkcheck) {
                            dl.setAvailable(true);
                        }
                        decryptedLinks.add(dl);
                        break;
                    }
                }
            }
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(filename);
        fp.addLinks(decryptedLinks);
        return decryptedLinks;
    }

}
