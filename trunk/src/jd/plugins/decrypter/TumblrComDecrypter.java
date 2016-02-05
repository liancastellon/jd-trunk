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

import java.net.UnknownHostException;
import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser.BrowserException;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "tumblr.com" }, urls = { "https?://(?!\\d+\\.media\\.tumblr\\.com/.+)[\\w\\.\\-]+?tumblr\\.com(?:/(audio|video)_file/\\d+/tumblr_[A-Za-z0-9]+|/image/\\d+|/post/\\d+|.+)" }, flags = { 0 })
public class TumblrComDecrypter extends PluginForDecrypt {

    public TumblrComDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String     GENERALOFFLINE = ">Not found\\.<";

    private static final String     TYPE_FILE      = ".+tumblr\\.com/(audio|video)_file/\\d+/tumblr_[A-Za-z0-9]+";
    private static final String     TYPE_POST      = ".+tumblr\\.com/post/\\d+";
    private static final String     TYPE_IMAGE     = ".+tumblr\\.com/image/\\d+";

    private ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
    private String                  parameter      = null;

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        parameter = param.toString().replace("www.", "");
        try {
            if (parameter.matches(TYPE_FILE)) {
                decryptFile();
            } else if (parameter.matches(TYPE_POST)) {
                decryptPost();
            } else if (parameter.matches(TYPE_IMAGE)) {
                decryptImage();
            } else {
                decryptUser();
            }
        } catch (final BrowserException e) {
            logger.info("Server error, couldn't decrypt link: " + parameter);
            return decryptedLinks;
        } catch (final UnknownHostException eu) {
            logger.info("UnknownHostException, couldn't decrypt link: " + parameter);
            return decryptedLinks;
        }
        return decryptedLinks;
    }

    private void decryptFile() throws Exception {
        br.setFollowRedirects(false);
        br.getPage(parameter);
        if (br.containsHTML(GENERALOFFLINE) || br.containsHTML(">Die angeforderte URL konnte auf dem Server")) {
            this.decryptedLinks.add(this.createOfflinelink(parameter));
            return;
        }
        String finallink = br.getRedirectLocation();
        // if (parameter.matches(".+tumblr\\.com/video_file/\\d+/tumblr_[A-Za-z0-9]+")) {
        // getPage(finallink);
        // finallink = br.getRedirectLocation();
        // }
        if (finallink == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            throw new DecrypterException("Decrypter broken");
        }
        decryptedLinks.add(createDownloadlink(finallink));
    }

    private void decryptPost() throws Exception {
        // lets identify the unique id for this post, only use it for tumblr hosted content
        final String puid = new Regex(parameter, "/post/(\\d+)").getMatch(0);

        // Single posts
        br.setFollowRedirects(false);
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            logger.info("Link offline (error 404): " + parameter);
            return;
        }
        /* Workaround for bad redirects --> Redirectloop */
        String redirect = br.getRedirectLocation();
        if (br.getRedirectLocation() != null) {
            final String redirect_remove = new Regex(redirect, "(#.+)").getMatch(0);
            if (redirect_remove != null) {
                redirect = redirect.replace(redirect_remove, "");
            }
            br.getPage(redirect);
        }
        String fpName = br.getRegex("<title>([^/\"]*?)</title>").getMatch(0);
        if (fpName == null) {
            fpName = "Tumblr post " + new Regex(parameter, "(\\d+)$").getMatch(0);
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        fpName = fpName.replace("\n", "");

        String externID = br.getRegex("(https?://(www\\.)?gasxxx\\.com/media/player/config_embed\\.php\\?vkey=\\d+)\"").getMatch(0);
        if (externID != null) {
            br.getPage(externID);
            externID = br.getRegex("<src>(https?://.+\\.flv)</src>").getMatch(0);
            if (externID == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                throw new DecrypterException("Decrypter broken");
            }
            final DownloadLink dl = createDownloadlink("directhttp://" + externID);
            dl.setFinalFileName(fpName + ".flv");
            decryptedLinks.add(dl);
            return;
        }
        externID = br.getRegex("\"(https?://video\\.vulture\\.com/video/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            br.getPage(Encoding.htmlDecode(externID));
            String cid = br.getRegex("\\&media_type=video\\&content=([A-Z0-9]+)\\&").getMatch(0);
            if (cid == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                throw new DecrypterException("Decrypter broken");
            }
            br.getPage("//video.vulture.com/item/player_embed.js/" + cid);
            externID = br.getRegex("(https?://videos\\.cache\\.magnify\\.net/[^<>\"]*?)\\'").getMatch(0);
            if (externID == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                throw new DecrypterException("Decrypter broken");
            }
            final DownloadLink dl = createDownloadlink("directhttp://" + externID);
            dl.setFinalFileName(fpName + externID.substring(externID.lastIndexOf(".")));
            decryptedLinks.add(dl);
            return;
        }
        externID = br.getRegex("\"(https?://(www\\.)?facebook\\.com/v/\\d+)\"").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink(externID.replace("/v/", "/video/video.php?v="));
            decryptedLinks.add(dl);
            return;
        }
        externID = br.getRegex("name=\"twitter:player\" content=\"(https?://(www\\.)?youtube\\.com/v/[A-Za-z0-9\\-_]+)\\&").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink(externID);
            decryptedLinks.add(dl);
            return;
        }
        externID = br.getRegex("class=\"vine\\-embed\" src=\"(https?://vine\\.co/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink(externID);
            decryptedLinks.add(dl);
            return;
        }
        externID = br.getRegex("\\\\x3csource src=\\\\x22(https?://[^<>\"]*?)\\\\x22").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("'(https?://(www\\.)?tumblr\\.com/video/[^<>\"']*?)'").getMatch(0);
            if (externID != null) {
                // the puid stays the same throughout all these requests
                br.getPage(externID);
                externID = br.getRegex("\"(https?://(www\\.)?tumblr\\.com/video_file/[^<>\"]*?)\"").getMatch(0);
            }
        }
        if (externID != null) {
            if (externID.matches(".+tumblr\\.com/video_file/.+")) {
                br.setFollowRedirects(false);
                // the puid stays the same throughout all these requests
                br.getPage(externID);
                externID = br.getRedirectLocation();
                if (externID != null && externID.matches("https?://www\\.tumblr\\.com/video_file/.+")) {
                    br.getPage(externID);
                    externID = br.getRedirectLocation();
                }
                externID = externID.replace("#_=_", "");
                final DownloadLink dl = createDownloadlink(externID);
                String extension = externID.substring(externID.lastIndexOf("."));
                /* Correct regexed extension */
                extension = new Regex(extension, "(\\.[a-z0-9]+)").getMatch(0);
                if (extension == null) {
                    extension = ".mp4"; // DirectHTTP
                }
                dl.setLinkID(getHost() + "://" + puid);
                dl.setFinalFileName(puid + " - " + fpName + extension);
                decryptedLinks.add(dl);
            } else {
                final DownloadLink dl = createDownloadlink("directhttp://" + externID);
                dl.setLinkID(getHost() + "://" + puid);
                decryptedLinks.add(dl);
            }
            return;
        }
        String[] pics = null;
        // TODO: determine the correct unique id, and set linkid, either /([md5 hash])/ or /tumblr_([id?]).ext?

        /* Access link if possible to get higher qualities e.g. *1280 --> Only needed/possible for single links. */
        final String picturelink = br.getRegex("class=\"photo\">[\t\n\r ]+<a href=\"(https?://[a-z0-9\\-]+\\.tumblr\\.com/image/\\d+)\"").getMatch(0);
        if (picturelink != null) {
            br.getPage(picturelink);
            externID = getBiggestPicture();
            if (externID != null) {
                final DownloadLink dl = createDownloadlink("directhttp://" + externID);
                dl.setAvailable(true);
                decryptedLinks.add(dl);
                return;
            }
        } else {
            pics = br.getRegex("property=\"og:image\" content=\"(http://\\d+\\.media\\.tumblr\\.com/[^<>\"]*?)\"").getColumn(0);
            if (pics != null && pics.length != 0) {
                for (final String pic : pics) {
                    final DownloadLink dl = createDownloadlink("directhttp://" + pic);
                    dl.setAvailable(true);
                    decryptedLinks.add(dl);
                }
                return;
            }
        }
        logger.info("Found nothing here so the decrypter is either broken or there isn't anything to decrypt. Link: " + parameter);
        return;
    }

    private void decryptImage() throws Exception {
        br.setFollowRedirects(false);
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            logger.info("Link offline (error 404): " + parameter);
            return;
        }
        String finallink = null;
        if (parameter.contains("demo.tumblr.com/image/")) {
            finallink = br.getRegex("data\\-src=\"(http://(www\\.)?tumblr\\.com/photo/[^<>\"]*?)\"").getMatch(0);
        } else {
            finallink = getBiggestPicture();
        }
        if (finallink == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            throw new DecrypterException("Decrypter broken");
        }
        final DownloadLink dl = createDownloadlink("directhttp://" + finallink);
        dl.setAvailable(true);
        decryptedLinks.add(dl);
        return;
    }

    private void decryptUser() throws Exception {
        String nextPage = "";
        int counter = 1;
        boolean decryptSingle = parameter.matches(".+tumblr\\.com/page/\\d+");
        br.getPage(parameter);
        if (br.containsHTML(GENERALOFFLINE)) {
            logger.info("Link offline: " + parameter);
            return;
        }
        final FilePackage fp = FilePackage.getInstance();
        String fpName = new Regex(parameter, "//(.+?)\\.tumblr").getMatch(0);
        fp.setName(fpName);
        do {
            if (this.isAbort()) {
                logger.info("Decryption aborted by user");
                return;
            }
            if (!"".equals(nextPage)) {
                br.getPage(nextPage);
            }
            final String[] allPosts = br.getRegex("\"(https?://(www\\.)?[\\w\\.\\-]*?\\.tumblr\\.com/post/\\d+)").getColumn(0);
            if (allPosts == null || allPosts.length == 0) {
                logger.info("Found nothing here so the decrypter is either broken or there isn't anything to decrypt. Link: " + parameter);
                return;
            }
            for (final String post : allPosts) {
                final DownloadLink fpost = createDownloadlink(post);
                fpost.setProperty("nopackagename", true);
                fp.add(fpost);
                distribute(fpost);
                decryptedLinks.add(fpost);
            }
            if (decryptSingle) {
                break;
            }
            nextPage = parameter.contains("/archive") ? br.getRegex("\"(/archive(?:/[^\"]*)?\\?before_time=\\d+)\">Next Page").getMatch(0) : br.getRegex("\"(/page/" + ++counter + ")\"").getMatch(0);
        } while (nextPage != null);
        logger.info("Decryption done - last 'nextPage' value was: " + nextPage);
    }

    private String getBiggestPicture() {
        String image = null;
        // Try to find the biggest picture
        for (int i = 2000; i >= 10; i--) {
            image = br.getRegex("\"(http://\\d+\\.media\\.tumblr\\.com(/[a-z0-9]{32})?/tumblr_[A-Za-z0-9_]+_" + i + "\\.(jpg|gif|png))\"").getMatch(0);
            if (image != null) {
                break;
            }
        }
        return image;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}