//    jDownloader - Downloadmanager
//    Copyright (C) 2012  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.HashSet;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.Request;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

import org.jdownloader.plugins.components.antiDDoSForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "pic5you.ru", "image2you.ru", "picsee.net", "imagecurl.com", "twitpic.com", "pic4you.ru", "turboimagehost.com", "imagebam.com", "freeimagehosting.net", "girlswithmuscle.com" }, urls = { "http://pic5you\\.ru/\\d+/\\d+/", "http://(?:www\\.)?image2you\\.ru/\\d+/\\d+/", "http://(www\\.)?picsee\\.net/\\d{4}-\\d{2}-\\d{2}/.*?\\.html", "http://(?:www\\.)?imagecurl\\.com/viewer\\.php\\?file=[\\w-]+\\.[a-z]{2,4}", "https?://(www\\.)?twitpic\\.com/show/[a-z]+/[a-z0-9]+", "http://(?:www\\.)?pic4you\\.ru/\\d+/\\d+/", "https?://(?:www\\.)?turboimagehost\\.com/p/\\d+/.*?\\.html", "https?://[\\w\\.]*imagebam\\.com/(image|gallery)/[a-z0-9]+", "http://[\\w\\.]*?freeimagehosting\\.net/image\\.php\\?.*?\\..{3,4}", "https?://(www.)?girlswithmuscle\\.com/\\d+/?" })
public class ImageHosterDecrypter extends antiDDoSForDecrypt {
    public ImageHosterDecrypter(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(false);
        String finallink = null;
        String finalfilename = null;
        if (parameter.contains("imagebam.com")) {
            br.getPage(parameter);
            /* Error handling */
            if (br.containsHTML("The gallery you are looking for")) {
                logger.info("Link offline: " + parameter);
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            if (br.containsHTML("Image not found|>Image violated our terms of service|>The requested image could not be located|>The image has been deleted")) {
                logger.info("Link offline: " + parameter);
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            if (parameter.contains("/gallery/")) {
                // note: you can still get dupes of images (filenames), but they have different download path.
                final HashSet<String> dupes = new HashSet<String>();
                String name = new Regex(parameter, "/gallery/(.+)").getMatch(0);
                if (name == null) {
                    name = "ImageBamGallery";
                } else {
                    name = "ImageBamGallery_" + name;
                }
                final FilePackage fp = FilePackage.getInstance();
                fp.setName(name);
                final String pages[] = br.getRegex("class=\"pagination_(current|link)\">(\\d+)<").getColumn(1);
                if (pages != null && pages.length > 0) {
                    for (final String page : pages) {
                        br.getPage(parameter + "/" + page);
                        if (br.containsHTML("The gallery you are looking for")) {
                            continue;
                        }
                        final String links[] = br.getRegex("'(https?://[\\w\\.]*imagebam\\.com/image/[a-z0-9]+)'").getColumn(0);
                        for (final String link : links) {
                            if (dupes.add(link)) {
                                final DownloadLink dl = handleImageBam(br, Encoding.htmlDecode(link), true);
                                if (dl != null) {
                                    decryptedLinks.add(dl);
                                }
                            }
                        }
                    }
                } else {
                    final String links[] = br.getRegex("'(https?://[\\w\\.]*imagebam\\.com/image/[a-z0-9]+)'").getColumn(0);
                    for (final String link : links) {
                        if (dupes.add(link)) {
                            final DownloadLink dl = handleImageBam(br, Encoding.htmlDecode(link), true);
                            if (dl != null) {
                                decryptedLinks.add(dl);
                            }
                        }
                    }
                }
                if (decryptedLinks.size() > 0) {
                    fp.addLinks(decryptedLinks);
                    return decryptedLinks;
                } else {
                    return null;
                }
            }
            final DownloadLink dl;
            if (br.containsHTML("Continue to your image")) {
                dl = handleImageBam(br, parameter, true);
            } else {
                dl = handleImageBam(br, null, false);
            }
            if (dl != null) {
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
        } else if (parameter.contains("freeimagehosting.net")) {
            br.getPage(parameter);
            /* Error handling */
            if (!br.containsHTML("uploads/")) {
                return decryptedLinks;
            }
            finallink = parameter.replace("image.php?", "uploads/");
        } else if (parameter.contains("sharenxs.com/")) {
            br.getPage(parameter + "&offset=original");
            finallink = br.getRegex("<img[^>]+class=\"view_photo\" src=\"(.*?)\"").getMatch(0);
            if (finallink == null) {
                finallink = br.getRegex("<a\\s+href=\"#\" onclick='imgsize\\(\\)' ><img[\t\n\r ]+src=\"(http://[^<>\"]*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("\"(http://cache\\.sharenxs\\.com/images/[^<>\"]*?)\"").getMatch(0);
                }
            }
            finallink = Request.getLocation(finallink, br.getRequest());
            if (finallink == null && br.containsHTML(">\\(Unnamed Gallery\\)<")) {
                return decryptedLinks;
            }
        } else if (parameter.contains("turboimagehost.com/")) {
            br.setFollowRedirects(true);
            br.getPage(parameter);
            if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("(don`t exist on our server|\\- Invalid link<)")) {
                logger.info("Link offline: " + parameter);
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            finallink = br.getRegex("\"(https?://s\\d+d\\d+\\.(?:turboimagehost\\.com|turboimg\\.net)/sp/[a-z0-9]+/.*?)\"").getMatch(0);
        } else if (parameter.contains("picsapart.com/")) {
            finallink = parameter.replace("/photo/", "/download/");
            finalfilename = new Regex(parameter, "picsapart\\.com/photo/(\\d+)").getMatch(0) + ".jpg";
        } else if (parameter.contains("pic4you.ru/")) {
            br.getPage(parameter);
            finallink = br.getRegex("\"(http://s\\d+\\.pic4you\\.ru/[^<>\"]+\\-thumb\\.[A-Za-z]+)\"").getMatch(0);
            if (finallink != null) {
                finallink = finallink.replace("-thumb", "");
            }
            if (this.br.getRedirectLocation() != null) {
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
        } else if (parameter.contains("twitpic.com/")) {
            br.setFollowRedirects(false);
            br.getPage(parameter);
            finallink = br.getRedirectLocation();
        } else if (parameter.contains("imagecurl.com/")) {
            br.getPage(parameter);
            finallink = br.getRegex("\\('<br/><a href=\"(http://cdn\\.imagecurl\\.com/images/\\w+\\.[a-z]{2,4})\">").getMatch(0);
            if (finallink == null) {
                finallink = br.getRegex("To view its <a href=\"(http://cdn\\.imagecurl\\.com/images/\\w+\\.[a-z]{2,4})\">true size<").getMatch(0) + finallink;
            }
        } else if (parameter.contains("picsee.net/")) {
            finallink = parameter.replace("picsee.net/", "picsee.net/upload/").replace(".html", "");
        } else if (parameter.contains("image2you.ru/")) {
            br.getPage(parameter);
            if (this.br.getHttpConnection().getResponseCode() == 404) {
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            finallink = br.getRegex("\"(/allimages/[^<>\"]*?)\"><br /><br /><br />").getMatch(0);
            if (finallink == null) {
                finallink = br.getRegex("\"(/allimages/[^<>\"]*?)\"").getMatch(0);
            }
            if (finallink != null) {
                finallink = finallink.replace("allimages/2_", "allimages/");
                finallink = "http://image2you.ru" + finallink;
            }
        } else if (parameter.contains("pic5you.ru/")) {
            br.getPage(parameter);
            if (this.br.getHttpConnection().getResponseCode() == 404) {
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            br.getPage(parameter + "/1");
            finallink = this.br.getRegex("<img src=\"(https?://s\\d+\\.pic4you\\.ru/[^<>\"]*?)\"").getMatch(0);
            if (finallink != null) {
                finallink = finallink.replace("-thumb", "");
            }
        } else if (parameter.contains("girlswithmuscle.com/")) {
            String fuid = new Regex(parameter, "([\\d+]+)/?$").getMatch(0);
            if (!parameter.endsWith("/")) {
                parameter = parameter + "/";
            }
            br.getPage(parameter);
            finallink = br.getRegex("<a href=\"([^\"]+)\">Link to full-size").getMatch(0);
            String ext = new Regex(finallink, "(\\.[a-z0-9]+$)").getMatch(0);
            finalfilename = fuid + " " + br.getRegex("<title>([^<>]+)</title>").getMatch(0) + ext;
        }
        if (finallink == null) {
            logger.warning("Imagehoster-Decrypter broken for link: " + parameter);
            return null;
        }
        finallink = Encoding.htmlDecode("directhttp://" + finallink);
        final DownloadLink dl = createDownloadlink(finallink);
        dl.setUrlDownload(finallink);
        if (finalfilename != null) {
            dl.setFinalFileName(Encoding.htmlDecode(finalfilename));
        }
        decryptedLinks.add(dl);
        return decryptedLinks;
    }

    private DownloadLink handleImageBam(Browser br, String url, boolean refresh) throws Exception {
        final Browser brc;
        if (refresh == true) {
            brc = br.cloneBrowser();
            brc.getPage(url);
        } else {
            brc = br;
        }
        if (brc.containsHTML("Continue to your image")) {
            brc.getPage(url);
        }
        // note: long filenames wont have extensions! server header doesn't specify the file extension either!
        String finallink = brc.getRegex("('|\")(https?://\\d+\\.imagebam\\.com/download/[^<>\\s]+)\\1").getMatch(1);
        if (finallink == null) {
            finallink = brc.getRegex("onclick=\"scale\\(this\\);\" src=\"(https?://.*?)\"").getMatch(0);
            if (finallink == null) {
                // no large image provided, we need to get the image on site
                finallink = brc.getRegex("<div class=\"container-full\">\\s*<div class=\"image-container\">\\s*<img class=\"image\" id=\"\\w+\" src=\"(.*?)\"").getMatch(0);
            }
        }
        if (finallink == null) {
            throw new DecrypterException("Decrypter broken for link: " + url);
        }
        finallink = Encoding.htmlDecode(finallink);
        DownloadLink dl = createDownloadlink("directhttp://" + finallink);
        final String finalfilename = extractFileNameFromURL(finallink);
        if (finalfilename != null) {
            // if has extension don't set, if hasn't extension set default one.
            dl.setFinalFileName(Encoding.htmlDecode(finalfilename + (getFileNameExtensionFromString(finalfilename) != null ? "" : ".jpg")));
        }
        return dl;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}