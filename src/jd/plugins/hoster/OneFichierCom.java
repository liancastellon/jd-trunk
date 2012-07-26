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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "1fichier.com" }, urls = { "https?://(?!www\\.)[a-z0-9\\-]+\\.(dl4free\\.com|alterupload\\.com|cjoint\\.net|desfichiers\\.com|dfichiers\\.com|megadl\\.fr|mesfichiers\\.org|piecejointe\\.net|pjointe\\.com|tenvoi\\.com|1fichier\\.com)/?" }, flags = { 2 })
public class OneFichierCom extends PluginForHost {

    private static AtomicInteger maxPrem        = new AtomicInteger(1);
    private static final String  PASSWORDTEXT   = "(Accessing this file is protected by password|Please put it on the box bellow|Veuillez le saisir dans la case ci-dessous)";
    private static final String  IPBLOCKEDTEXTS = "(/>Téléchargements en cours|>veuillez patienter avant de télécharger un autre fichier|>You already downloading (some|a) file|>You can download only one file at a time|>Please wait a few seconds before downloading new ones|>You must wait for another download)";
    private static final String  FREELINK       = "freeLink";
    private static final String  PREMLINK       = "premLink";
    private static final String  SSL_CONNECTION = "SSL_CONNECTION";
    private boolean              pwProtected    = false;

    public OneFichierCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www.1fichier.com/en/register.pl");
        setConfigElements();
    }

    public void correctDownloadLink(DownloadLink link) {
        // Remove everything after the domain
        if (!link.getDownloadURL().endsWith("/")) {
            Regex idhostandName = new Regex(link.getDownloadURL(), "https?://(.*?)\\.(.*?)(/|$)");
            link.setUrlDownload("http://" + idhostandName.getMatch(0) + "." + idhostandName.getMatch(1) + "/");
        }
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws IOException, PluginException {
        pwProtected = false;
        this.setBrowserExclusive();
        prepareBrowser(br);
        br.setFollowRedirects(false);
        br.setCustomCharset("utf-8");
        br.getPage(link.getDownloadURL() + "?e=1");
        if (br.containsHTML(">Software error:<") || br.toString().matches("(\\s+)?wait(\\s+)?")) return AvailableStatus.UNCHECKABLE;
        if (br.containsHTML("password")) {
            pwProtected = true;
            link.getLinkStatus().setStatusText(JDL.L("plugins.hoster.onefichiercom.passwordprotected", "This link is password protected"));
            return AvailableStatus.UNCHECKABLE;
        }
        String[][] linkInfo = br.getRegex("https?://[^;]+;([^;]+);([0-9]+);(\\d+)").getMatches();
        if (linkInfo == null || linkInfo.length == 0) {
            logger.warning("Available Status broken for link");
            return null;
        }
        String filename = linkInfo[0][0];
        if (filename == null) filename = br.getRegex(">File name :</th><td>([^<>\"]*?)</td>").getMatch(0);
        String filesize = linkInfo[0][1];
        if (filesize == null) filesize = br.getRegex(">File size :</th><td>([^<>\"]*?)</td></tr>").getMatch(0);
        if (filename != null) link.setFinalFileName(Encoding.htmlDecode(filename.trim()));
        if (filesize != null) {
            long size = 0;
            link.setDownloadSize(size = SizeFormatter.getSize(filesize));
            if (size > 0) link.setProperty("VERIFIEDFILESIZE", size);
        }
        if ("1".equalsIgnoreCase(linkInfo[0][2])) {
            link.setProperty("HOTLINK", true);
        } else {
            link.setProperty("HOTLINK", Property.NULL);
        }

        if (filename == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        return AvailableStatus.TRUE;
    }

    public boolean bypassMaxSimultanDownloadNum(DownloadLink link, Account acc) {
        return acc == null && link.getProperty("HOTLINK", null) != null;
    }

    public void doFree(DownloadLink downloadLink) throws Exception, PluginException {
        String dllink = downloadLink.getStringProperty(FREELINK, null);
        if (dllink != null) {
            /* try to resume existing file */
            br.setFollowRedirects(true);
            // at times the second chunk creates 404 errors!
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
            if (dl.getConnection().getContentType().contains("html")) {
                /* could not resume, fetch new link */
                br.followConnection();
                downloadLink.setProperty(FREELINK, Property.NULL);
                dllink = null;
                br.setFollowRedirects(false);
                br.setCustomCharset("utf-8");
            } else {
                /* resume download */
                dl.startDownload();
                return;
            }
        }
        // use the English page, less support required
        br.getPage(downloadLink.getDownloadURL() + "en/index.html");
        if (br.containsHTML(">Software error:<")) throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error");
        if (br.containsHTML(IPBLOCKEDTEXTS)) throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Short wait period, Reconnection not necessary", 90 * 1000l);
        String passCode = null;
        if (br.containsHTML(PASSWORDTEXT) || pwProtected) {
            if (downloadLink.getStringProperty("pass", null) == null) {
                passCode = Plugin.getUserInput("Password?", downloadLink);
            } else {
                /* gespeicherten PassCode holen */
                passCode = downloadLink.getStringProperty("pass", null);
            }
            br.postPage(br.getURL(), "pass=" + passCode);
            if (br.containsHTML(PASSWORDTEXT)) {
                downloadLink.setProperty("pass", Property.NULL);
                throw new PluginException(LinkStatus.ERROR_RETRY, JDL.L("plugins.hoster.onefichiercom.wrongpassword", "Password wrong!"));
            }
        } else {
            // ddlink is within the ?e=1 page request! but it seems you need to
            // do the following posts to be able to use the link
            // dllink = br.getRegex("(http.+/get/" + new
            // Regex(downloadLink.getDownloadURL(),
            // "https?://([^\\.]+)").getMatch(0) +
            // "[^;]+)").getMatch(0);
            br.postPage(downloadLink.getDownloadURL() + "en/", "submit=Download+the+file");
        }
        if (dllink == null) dllink = br.getRedirectLocation();
        if (dllink == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        br.setFollowRedirects(true);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, -2);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (passCode != null) downloadLink.setProperty("pass", passCode);
        downloadLink.setProperty(FREELINK, dllink);
        dl.startDownload();
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        /* reset maxPrem workaround on every fetchaccount info */
        maxPrem.set(1);
        br.getPage("https://1fichier.com/console/account.pl?user=" + Encoding.urlEncode(account.getUser()) + "&pass=" + Encoding.urlEncode(JDHash.getMD5(account.getPass())));
        String timeStamp = br.getRegex("(\\d+)").getMatch(0);
        String freeCredits = br.getRegex("0[\r\n]+([0-9\\.]+)").getMatch(0);
        if (timeStamp == null || "error".equalsIgnoreCase(timeStamp)) {
            ai.setStatus("Username/Password invalid");
            account.setProperty("type", Property.NULL);
            account.setValid(false);
            return ai;
        } else if ("0".equalsIgnoreCase(timeStamp)) {
            if (freeCredits != null && Float.parseFloat(freeCredits) > 0) {
                /* not finished yet */
                account.setValid(true);
                account.setProperty("type", "FREE");
                ai.setStatus("Free User (Credits available)");
                ai.setTrafficLeft(SizeFormatter.getSize(freeCredits + " GB"));
                try {
                    maxPrem.set(1);
                    account.setMaxSimultanDownloads(1);
                    account.setConcurrentUsePossible(false);
                } catch (final Throwable e) {
                }
            } else {
                ai.setStatus("Free User (No credits left)");
                account.setProperty("type", Property.NULL);
                account.setValid(false);
                return ai;
            }
            return ai;
        } else {
            account.setValid(true);
            account.setProperty("type", "PREMIUM");
            ai.setStatus("Premium User");
            ai.setValidUntil(Long.parseLong(timeStamp) * 1000l + (24 * 60 * 60 * 1000l));
            ai.setUnlimitedTraffic();
            try {
                maxPrem.set(20);
                account.setMaxSimultanDownloads(-1);
                account.setConcurrentUsePossible(true);
            } catch (final Throwable e) {
            }
            return ai;
        }
    }

    @Override
    public String getAGBLink() {
        return "http://www.1fichier.com/en/cgu.html";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /* workaround for free/premium issue on stable 09581 */
        return maxPrem.get();
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink);
    }

    private String handlePassword(DownloadLink downloadLink, String passCode) throws IOException, PluginException {
        logger.info("This link seems to be password protected, continuing...");
        if (downloadLink.getStringProperty("pass", null) == null) {
            passCode = Plugin.getUserInput("Password?", downloadLink);
        } else {
            /* gespeicherten PassCode holen */
            passCode = downloadLink.getStringProperty("pass", null);
        }
        br.postPage(br.getURL(), "pass=" + passCode);
        if (br.containsHTML(PASSWORDTEXT)) {
            downloadLink.setProperty("pass", Property.NULL);
            throw new PluginException(LinkStatus.ERROR_RETRY, JDL.L("plugins.hoster.onefichiercom.wrongpassword", "Password wrong!"));
        }
        return passCode;
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        requestFileInformation(link);
        String passCode = null;
        if (maxPrem.get() == 1 && "FREE".equalsIgnoreCase(account.getStringProperty("type", null))) {
            doFree(link);
            return;
        }
        String dllink = link.getStringProperty(PREMLINK, null);
        boolean useSSL = getPluginConfig().getBooleanProperty(SSL_CONNECTION, true);
        if (dllink != null) {
            /* try to resume existing file */
            if (useSSL) dllink = dllink.replaceFirst("http://", "https://");
            br.setFollowRedirects(true);
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
            if (dl.getConnection().getContentType().contains("html")) {
                /* could not resume, fetch new link */
                br.followConnection();
                link.setProperty(PREMLINK, Property.NULL);
                dllink = null;
            } else {
                /* resume download */
                dl.startDownload();
                return;
            }
        }
        br.setFollowRedirects(false);
        sleep(2 * 1000l, link);
        String url = link.getDownloadURL().replace("en/index.html", "");
        url = url + "?u=" + Encoding.urlEncode(account.getUser()) + "&p=" + Encoding.urlEncode(JDHash.getMD5(account.getPass()));
        URLConnectionAdapter con = br.openGetConnection(url);
        if (con.getResponseCode() == 401) {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
        }
        br.followConnection();
        if (pwProtected || br.containsHTML("password")) passCode = handlePassword(link, passCode);
        dllink = br.getRedirectLocation();
        if (dllink != null && br.containsHTML(IPBLOCKEDTEXTS)) throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Too many simultan downloads", 45 * 1000l);
        if (dllink == null) {
            logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        String useDllink = dllink;
        if (useSSL) useDllink = useDllink.replaceFirst("http://", "https://");
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, useDllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (passCode != null) {
            link.setProperty("pass", passCode);
        }
        link.setProperty(PREMLINK, dllink);
        dl.startDownload();
    }

    private void setConfigElements() {
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), SSL_CONNECTION, JDL.L("plugins.hoster.rapidshare.com.ssl2", "Use Secure Communication over SSL")).setDefaultValue(true));
    }

    private void prepareBrowser(final Browser br) {
        try {
            if (br == null) { return; }
            br.getHeaders().put("User-Agent", "Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.2.16) Gecko/20110323 Ubuntu/10.10 (maverick) Firefox/3.6.16");
            br.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            br.getHeaders().put("Accept-Language", "en-us,en;q=0.5");
            br.getHeaders().put("Pragma", null);
            br.getHeaders().put("Cache-Control", null);
        } catch (Throwable e) {
            /* setCookie throws exception in 09580 */
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}