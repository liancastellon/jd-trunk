package jd.plugins.decrypter;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "mangastream.com" }, urls = { "http://(www\\.)?(mangastream|readms)\\.com/(read|r)/([a-z0-9\\-_%]+/){2}\\d+(\\?page=\\d+)?" }, flags = { 0 })
public class MngStrm extends PluginForDecrypt {

    public MngStrm(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink parameter, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        br.setFollowRedirects(true);

        String part = new Regex(parameter.getCryptedUrl(), "\\?page=(\\d+)").getMatch(0);
        int requestedPage = part == null ? -1 : Integer.parseInt(part);
        String url = parameter.toString().replace("readms.com/", "mangastream.com/");

        url = url.replaceAll("\\?page=\\d+$", "");
        url = url.replace("/r/", "/read/");
        if (!parameter.equals(url)) {
            parameter.setCryptedUrl(url);
        }
        br.getPage(url + "/1");
        if (br.containsHTML(">Page Not Found<|but unfortunately that chapter has expired or been removed from the website\\.[\r\n]</p>")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        String title = br.getRegex("<title>([^<>\"]*?) \\- Manga Stream</title>").getMatch(0);
        if (title == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        String lastP = br.getRegex(">Last Page \\((\\d+)\\)<").getMatch(0);
        if (lastP == null) {
            lastP = "1";
        }
        final int lastPage = Integer.parseInt(lastP);
        final NumberFormat formatter = new DecimalFormat("00");
        final String urlPart = new Regex(url, "mangastream\\.com(/read/.+)").getMatch(0);
        for (int i = 1; i <= lastPage; i++) {
            if (requestedPage > 0 && i != requestedPage) {
                continue;
            }
            final DownloadLink link = createDownloadlink("mangastream://" + urlPart + "/" + i);
            final String contenturl = url + "?page=" + i;
            try {
                link.setContentUrl(contenturl);
                link.setContainerUrl(url);
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
            }
            link.setFinalFileName(title.trim() + " – page " + formatter.format(i) + ".png");
            link.setProperty("page_url", contenturl);
            link.setAvailableStatus(AvailableStatus.TRUE);
            decryptedLinks.add(link);
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(title.trim());
        fp.addLinks(decryptedLinks);

        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}