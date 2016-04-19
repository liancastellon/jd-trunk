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
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "porn.com" }, urls = { "http://(?:www\\.)?porn\\.com/videos/[^<>\"/]+\\d+(?:\\.html)?" }, flags = { 2 })
public class PornCom extends antiDDoSForHost {

    /* DEV NOTES */
    /* Porn_plugin */

    /* Connection stuff */
    private static final boolean FREE_RESUME       = true;
    private static final int     FREE_MAXCHUNKS    = 0;
    private static final int     FREE_MAXDOWNLOADS = -1;
    // private static final boolean ACCOUNT_FREE_RESUME = true;
    // private static final int ACCOUNT_FREE_MAXCHUNKS = 0;
    // private static final int ACCOUNT_FREE_MAXDOWNLOADS = 20;
    // private static final boolean ACCOUNT_PREMIUM_RESUME = true;
    // private static final int ACCOUNT_PREMIUM_MAXCHUNKS = 0;
    // private static final int ACCOUNT_PREMIUM_MAXDOWNLOADS = 20;

    private String               DLLINK            = null;
    private String               vq                = null;

    /* don't touch the following! */
    private static AtomicInteger maxPrem           = new AtomicInteger(1);

    public PornCom(PluginWrapper wrapper) {
        super(wrapper);
        this.setConfigElements();
        this.enablePremium("http://www.porn.com/profile/premium");
    }

    @Override
    public String getAGBLink() {
        return "http://www.porn.com/terms.html";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return FREE_MAXDOWNLOADS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa != null) {
            try {
                login(br, aa, false);
            } catch (final Throwable e) {
            }
        }
        br.getPage(downloadLink.getDownloadURL().replace("/embed/", "/"));
        if (br.containsHTML("(id=\"error\"><h2>404|No such video|<title>PORN\\.COM</title>)")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String q = downloadLink.getStringProperty("q", null);

        String filename = br.getRegex("<h1>(.*?)</h1>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<title>(.*?)</title>").getMatch(0);
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = Encoding.htmlDecode(filename.trim());
        if (q != null) {
            final HashMap<String, String> matches = jd.plugins.decrypter.PornCom.getQualities(this.br);
            DLLINK = matches.get(q);
        } else {
            get_dllink(this.br);
        }
        /* A little trick to download videos that are usually only available for registered users WITHOUT account :) */
        if (DLLINK == null) {
            final String fid = new Regex(downloadLink.getDownloadURL(), "(\\d+)(?:\\.html)?$").getMatch(0);
            final Browser brc = br.cloneBrowser();
            /* This way we can access links which are usually only accessible for registered users */
            brc.getPage("http://www.porn.com/videos/embed/" + fid + ".html");
            if (q != null) {
                DLLINK = brc.getRegex(q + "\",url:\"(https?:.*?)\"").getMatch(0);
            } else {
                get_dllink(brc);
            }
        }
        if (DLLINK == null) {
            if (br.containsHTML(">Sorry, this video is only available to members")) {
                if (q == null) {
                    downloadLink.setName(filename + ".mp4");
                }
                downloadLink.getLinkStatus().setStatusText("Only available for registered users");
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        DLLINK = Encoding.htmlDecode(DLLINK).replace("\\", "");
        if (q == null) {
            String ext = DLLINK.substring(DLLINK.lastIndexOf("."));
            if (ext == null || ext.length() > 5) {
                ext = ".mp4";
            }
            if (vq == null) {
                downloadLink.setFinalFileName(filename + ext);
            } else {
                downloadLink.setFinalFileName(filename + "." + vq + ext);
            }
        }
        Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openHeadConnection(DLLINK);
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void get_dllink(final Browser brc) {
        final SubConfiguration cfg = getPluginConfig();
        boolean q240 = cfg.getBooleanProperty("240p", false);
        boolean q360 = cfg.getBooleanProperty("360p", false);
        boolean q480 = cfg.getBooleanProperty("480p", false);
        boolean q720 = cfg.getBooleanProperty("720p", false);
        if (q720) {
            DLLINK = brc.getRegex("720p\",url:\"(http:.*?)\"").getMatch(0);
            vq = "720p";
        }
        if (DLLINK == null) {
            if (q480) {
                DLLINK = brc.getRegex("480p\",url:\"(http:.*?)\"").getMatch(0);
                vq = "480p";
            }
        }
        if (DLLINK == null) {
            if (q360) {
                DLLINK = brc.getRegex("360p\",url:\"(http:.*?)\"").getMatch(0);
                vq = "360p";
            }
        }
        if (DLLINK == null) {
            DLLINK = brc.getRegex("240p\",url:\"(http:.*?)\"").getMatch(0); // Default
            vq = "240p";
        }
        if (DLLINK != null) {
            return;
        }
        logger.info("Video quality selection failed.");
        // json
        final String a = getJsonArray("streams");
        final String[] array = getJsonResultsFromArray(a);
        if (array != null) {
            int highestQual = 0;
            String bestUrl = null;
            for (final String aa : array) {
                final String quality = getJson(aa, "name");
                final String q = quality != null ? new Regex(quality, "\\d+").getMatch(-1) : null;
                final int qual = q != null ? Integer.parseInt(q) : 0;
                if (qual > highestQual) {
                    highestQual = qual;
                    final String url = getJson(aa, "url");
                    if (url != null) {
                        bestUrl = url;
                    }
                }
            }
            DLLINK = bestUrl;
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        doFree(downloadLink);
    }

    private void doFree(final DownloadLink downloadLink) throws Exception {
        if (DLLINK == null && br.containsHTML(">Sorry, this video is only available to members")) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, FREE_RESUME, FREE_MAXCHUNKS);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private static final String MAINPAGE = "http://porn.com";
    private static Object       LOCK     = new Object();

    /** Login e.g. needed for videos that are only available to registered users and/or premium content. */
    public static void login(final Browser br, final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null && !force) {
                    br.setCookies("porn.com", cookies);
                    return;
                }
                br.setFollowRedirects(false);
                br.getPage("http://www.porn.com/");
                // br.getHeaders().put("X-CSRF-Token", "");
                br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                br.postPage("/login/ajax-login.json", "captcha=&captcha_hash=&remember=yes&form_id=login&username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
                br.getPage("http://www.porn.com/");
                if (br.getCookie(MAINPAGE, "auth") == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.saveCookies(br.getCookies("porn.com"), "");
            } catch (final PluginException e) {
                if (e.getLinkStatus() == PluginException.VALUE_ID_PREMIUM_DISABLE) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(br, account, true);
        ai.setUnlimitedTraffic();
        maxPrem.set(FREE_MAXDOWNLOADS);
        try {
            account.setType(AccountType.FREE);
            /* free accounts can still have captcha */
            account.setMaxSimultanDownloads(maxPrem.get());
            account.setConcurrentUsePossible(false);
        } catch (final Throwable e) {
            /* not available in old Stable 0.9.581 */
        }
        ai.setStatus("Registered (free) user");
        account.setValid(true);
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        doFree(link);
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /* workaround for free/premium issue on stable 09581 */
        return maxPrem.get();
    }

    @Override
    public String getDescription() {
        return "Only highest quality video available that you choose will be chosen.";
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "ALLOW_BEST", JDL.L("plugins.hoster.PornCom.checkbest", "Only grab the best available resolution")).setDefaultValue(false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "240p", JDL.L("plugins.hoster.PornCom.check360p", "Choose 240p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "360p", JDL.L("plugins.hoster.PornCom.check360p", "Choose 360p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "480p", JDL.L("plugins.hoster.PornCom.check480p", "Choose 480p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "720p", JDL.L("plugins.hoster.PornCom.check720p", "Choose 720p?")).setDefaultValue(true));
    }

    @Override
    public void reset() {
        DLLINK = null;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}