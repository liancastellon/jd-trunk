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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import jd.PluginWrapper;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "esoubory.cz" }, urls = { "http://(www\\.)?esoubory\\.cz/soubor/[a-z0-9]+/([a-z0-9\\-]+/|[a-z0-9\\-]+\\.html)" })
public class EsouboryCz extends PluginForHost {
    public EsouboryCz(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.esoubory.cz/credits/buy/");
    }

    /* Using similar API (and same owner): esoubory.cz, filesloop.com */
    @Override
    public String getAGBLink() {
        return "http://www.esoubory.cz/";
    }

    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap = new HashMap<Account, HashMap<String, Long>>();

    private void prepBr() {
        br.getHeaders().put("User-Agent", "JDownloader");
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) throws Exception {
        return true;
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final Account aa = AccountController.getInstance().getValidAccount(this);
        link.setName(new Regex(link.getDownloadURL(), "esoubory\\.cz/soubor/([a-z0-9]+)/").getMatch(0));
        String filename;
        String filesize;
        if (aa != null) {
            /* Prefer API */
            br.getPage("http://www.esoubory.cz/api/exists?token=" + getToken(aa) + "&url=" + Encoding.urlEncode(link.getDownloadURL()));
            if (!br.containsHTML("\"exists\":true")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            /* Basically just a workaround for "\"" in json response - should not happen anymore after the 04.12.14 */
            filename = br.getRegex("\"filename\":\"([^<>]+)\",").getMatch(0);
            if (filename == null) {
                filename = getJson("filename");
            }
            filename = Encoding.unicodeDecode(filename);
            filename = Encoding.htmlDecode(filename).trim();
            filename = filename.replace("\\", "");
            /* Basically just a workaround for "\"" in json response */
            filename = encodeUnicode(filename);
            filesize = getJson("filesize");
            link.setDownloadSize(Long.parseLong(filesize));
            link.setFinalFileName(filename);
        } else {
            br.getPage(link.getDownloadURL());
            if (br.getURL().contains("/search/")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            // final Regex linkinfo = br.getRegex("<h1>([^<>\"]*?)<span class=\"bluetext upper\">\\(([^<>\"]*?)\\)</span>");
            final Regex linkinfo = br.getRegex("<h1>\\s*([^<>\\(]*?)\\((\\d+(,\\d+)? (K|M|G)B)\\)\\s*</h1>");
            filename = linkinfo.getMatch(0);
            filesize = linkinfo.getMatch(1);
            if (filename == null || filesize == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            filename = Encoding.htmlDecode(filename).trim();
            filesize = filesize.replace(",", ".");
            link.setDownloadSize(SizeFormatter.getSize(filesize));
            /* Do not set the final filename here as we'll have the API when downloading via account anyways! */
            link.setName(filename);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        try {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        } catch (final Throwable e) {
            if (e instanceof PluginException) {
                throw (PluginException) e;
            }
        }
        throw new PluginException(LinkStatus.ERROR_FATAL, "This file can only be downloaded by premium users");
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        handleDL(link, account);
    }

    @SuppressWarnings("deprecation")
    private void handleDL(final DownloadLink link, final Account account) throws Exception {
        String finallink = checkDirectLink(link, "esouborydirectlink");
        if (finallink == null) {
            br.getPage("http://www.esoubory.cz/api/filelink?token=" + getToken(account) + "&url=" + Encoding.urlEncode(link.getDownloadURL()));
            if (br.containsHTML("\"error\":\"not\\-enough\\-credits\"")) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
            finallink = getJson("link");
            finallink = finallink.replace("\\", "");
            link.setProperty("esouborydirectlink", finallink);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, finallink, true, -2);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    /** no override to keep plugin compatible to old stable */
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap != null) {
                Long lastUnavailable = unavailableMap.get(link.getHost());
                if (lastUnavailable != null && System.currentTimeMillis() < lastUnavailable) {
                    final long wait = lastUnavailable - System.currentTimeMillis();
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Host is temporarily unavailable via " + this.getHost(), wait);
                } else if (lastUnavailable != null) {
                    unavailableMap.remove(link.getHost());
                    if (unavailableMap.size() == 0) {
                        hostUnavailableMap.remove(account);
                    }
                }
            }
        }
        handleDL(link, account);
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        account.setValid(true);
        account.setConcurrentUsePossible(true);
        account.setMaxSimultanDownloads(-1);
        prepBr();
        br.getPage("http://www.esoubory.cz/api/accountinfo?token=" + getToken(account));
        ai.setTrafficLeft(SizeFormatter.getSize(getJson("credit") + "MB"));
        br.getPage("http://www.esoubory.cz/api/list");
        String hostsSup = br.getRegex("\"list\":\"(.*?)\"").getMatch(0);
        if (hostsSup != null) {
            hostsSup = hostsSup.replace("\\", "");
            hostsSup = hostsSup.replaceAll("https?://(www\\.)?", "");
            final String[] hosts = hostsSup.split(";");
            final ArrayList<String> supportedHosts = new ArrayList<String>(Arrays.asList(hosts));
            ai.setMultiHostSupport(this, supportedHosts);
        }
        ai.setStatus("Premium account");
        return ai;
    }

    private String getToken(final Account account) throws Exception {
        String token = account.getStringProperty("token", null);
        if (token == null) {
            br.getPage("http://www.esoubory.cz/api/login?email=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
            if (br.containsHTML("\"error\":\"login\\-failed\"")) {
                final String lang = System.getProperty("user.language");
                if ("de".equalsIgnoreCase(lang)) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            token = getJson("token");
            account.setProperty("token", token);
        }
        return token;
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
            } catch (Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    private String getJson(final String parameter) {
        String result = br.getRegex("\"" + parameter + "\":(\\d+)").getMatch(0);
        if (result == null) {
            result = br.getRegex("\"" + parameter + "\":\"([^<>\"]*?)\"").getMatch(0);
        }
        return result;
    }

    @SuppressWarnings("unused")
    private void tempUnavailableHoster(final Account account, final DownloadLink downloadLink, final long timeout) throws PluginException {
        if (downloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unable to handle this errorcode!");
        }
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap == null) {
                unavailableMap = new HashMap<String, Long>();
                hostUnavailableMap.put(account, unavailableMap);
            }
            /* wait to retry this host */
            unavailableMap.put(downloadLink.getHost(), (System.currentTimeMillis() + timeout));
        }
        throw new PluginException(LinkStatus.ERROR_RETRY);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}