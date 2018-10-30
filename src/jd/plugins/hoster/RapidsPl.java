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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.MultiHosterManagement;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "rapids.pl" }, urls = { "" })
public class RapidsPl extends PluginForHost {
    private static MultiHosterManagement                     mhm                     = new MultiHosterManagement("rapids.pl");
    private static final String                              NOCHUNKS                = "NOCHUNKS";
    private static final String                              NICE_HOST               = "rapids.pl";
    private static final String                              NICE_HOSTproperty       = NICE_HOST.replaceAll("(\\.|\\-)", "");
    private static final String                              COOKIE_HOST             = "http://" + NICE_HOST;
    private static WeakHashMap<Account, Map<String, Object>> SUPPORTED_HOST_SETTINGS = new WeakHashMap<Account, Map<String, Object>>();

    public RapidsPl(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://rapids.pl/doladuj");
    }

    @Override
    public String getAGBLink() {
        return "http://rapids.pl/pomoc/regulamin";
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    private Browser newBrowser() {
        br = new Browser();
        br.setCookiesExclusive(true);
        // define custom browser headers and language settings.
        br.getHeaders().put("User-Agent", "JDownloader");
        br.setCustomCharset("utf-8");
        return br;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        this.br = newBrowser();
        final AccountInfo ac = new AccountInfo();
        login(account, true);
        // check if account is valid
        account.setMaxSimultanDownloads(-1);
        account.setConcurrentUsePossible(true);
        final Map<String, Object> transfer = callAPI(account, null, "jd_transfer");
        if (transfer != null) {
            final Number trafficAvailable = (Number) transfer.get("transfer");
            if (trafficAvailable != null) {
                ac.setStatus("Premium Account");
                ac.setTrafficLeft(trafficAvailable.longValue());
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        } else {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final Map<String, Object> services = callAPI(account, null, "jd_services");
        final HashSet<String> supportedServices = new HashSet<String>();
        final HashSet<String> brokenServices = new HashSet<String>();
        for (final Entry<String, Object> service : services.entrySet()) {
            if (service.getValue() instanceof Map) {
                final Map<String, Object> settings = (Map<String, Object>) service.getValue();
                if (Boolean.TRUE.equals(settings.get("direct")) || Boolean.TRUE.equals(settings.get("server"))) {
                    supportedServices.add(service.getKey());
                } else {
                    brokenServices.add(service.getKey());
                }
            }
        }
        final List<String> support = new ArrayList<String>(supportedServices);
        final List<String> mapping = ac.setMultiHostSupport(this, support);
        if (mapping != null) {
            for (int index = 0; index < support.size(); index++) {
                final String search = support.get(index);
                final String replace = mapping.get(index);
                if (replace != null && !StringUtils.equals(search, replace)) {
                    services.put(replace, services.remove(search));
                }
            }
            synchronized (SUPPORTED_HOST_SETTINGS) {
                SUPPORTED_HOST_SETTINGS.put(account, services);
            }
        }
        return ac;
    }

    public Map<String, Object> callAPI(final Account account, final DownloadLink downloadLink, final String methodName, Object[]... parameters) throws PluginException, IOException, InterruptedException {
        final String apiKey;
        if (account != null) {
            apiKey = account.getStringProperty("apikey", null);
        } else {
            apiKey = null;
        }
        PostRequest postRequest = new PostRequest("https://rapids.pl/api/" + methodName);
        postRequest.setContentType("application/json; charset=UTF-8");
        final Map<String, Object> requestJson = new HashMap<String, Object>();
        requestJson.put("api_key", apiKey);
        if (parameters != null) {
            for (Object[] parameter : parameters) {
                if (parameter != null && parameter.length == 2) {
                    requestJson.put(parameter[0].toString(), parameter[1].toString());
                }
            }
        }
        postRequest.setPostBytes(JSonStorage.getMapper().objectToByteArray(requestJson));
        br.getPage(postRequest);
        final Map<String, Object> response = JSonStorage.restoreFromString(postRequest.getHtmlCode(), TypeRef.HASHMAP, null);
        if (Boolean.TRUE.equals(response.get("success"))) {
            return (Map<String, Object>) response.get("data");
        } else {
            try {
                final String code = (String) response.get("code");
                final String message = (String) response.get("message");
                if ("jx1001".equals(code)) {
                    if (apiKey == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, message == null ? "No api key" : message);
                    }
                } else if ("jx1002".equals(code)) {
                    if (account != null) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, message == null ? "No access for API" : message, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                } else if ("jx1003".equals(code)) {
                    // Missing required parameters
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Parameter missing");
                } else if ("jx1004".equals(code)) {
                    // Invalid download type
                    mhm.handleErrorGeneric(account, downloadLink, "error_invalid_download_type", 10);
                } else if ("jx1005".equals(code)) {
                    // Link was not found
                    mhm.handleErrorGeneric(account, downloadLink, "error_invalid_offline", 10);
                } else if ("jx1006".equals(code)) {
                    // Service is not supported
                    if (account != null && downloadLink != null) {
                        final AccountInfo ai = account.getAccountInfo();
                        if (ai != null) {
                            ai.removeMultiHostSupport(downloadLink.getHost());
                            throw new PluginException(LinkStatus.ERROR_RETRY);
                        }
                    }
                } else if ("jx1007".equals(code)) {
                    // At the moment the service is not supported
                    if (account != null && downloadLink != null) {
                        mhm.putError(account, downloadLink, 60 * 60 * 1000l, message == null ? "At the moment the service is not supported" : message);
                    }
                } else if ("jx1008".equals(code)) {
                    // Link was not recognized
                    mhm.handleErrorGeneric(account, downloadLink, "link_was_not_recognized", 10);
                } else if ("jx1009".equals(code)) {
                    /* Not enough traffic left */
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                } else if ("jx1010".equals(code)) {
                    // Download available after account recharging --> Not enough traffic left(?)
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                } else if ("jx1011".equals(code)) {
                    // Server error
                    mhm.handleErrorGeneric(account, downloadLink, "server_error", 10);
                }
                if (code != null) {
                    // catch all errors
                    mhm.handleErrorGeneric(account, downloadLink, "error_unknown", 20);
                }
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    dumpAccountInfos(account);
                }
                throw e;
            }
        }
        return response;
    }

    @Override
    public int getMaxSimultanDownload(DownloadLink link, Account account) {
        if (link != null && account != null) {
            synchronized (SUPPORTED_HOST_SETTINGS) {
                // only available after first fetchAccountInfo
                // TODO: save/restore map
                final Map<String, Object> settings = SUPPORTED_HOST_SETTINGS.get(account);
                if (settings != null) {
                    final Map<String, Object> host = (Map<String, Object>) settings.get(link.getHost());
                    if (host != null) {
                        final Number max = (Number) host.get("max");
                        if (max != null) {
                            return max.intValue();
                        }
                    }
                }
            }
        }
        return super.getMaxSimultanDownload(link, account);
    }

    private void dumpAccountInfos(final Account account) {
        synchronized (SUPPORTED_HOST_SETTINGS) {
            SUPPORTED_HOST_SETTINGS.remove(account);
        }
        account.removeProperty("apikey");
        account.clearCookies("");
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        mhm.runCheck(account, link);
        login(account, false);
        this.br = newBrowser();
        final Map<String, Object> checkLink = callAPI(account, link, "jd_check_link", new Object[] { "link", link.getDefaultPlugin().buildExternalDownloadURL(link, this) });
        boolean resumeFlag = true;
        if (Boolean.FALSE.equals(checkLink.get("resume"))) {
            resumeFlag = false;
        }
        int maxConnections = 0;
        if (resumeFlag == false) {
            maxConnections = 1;
        } else {
            final Number max = (Number) checkLink.get("max");
            if (max != null) {
                maxConnections = -(Math.abs(max.intValue()));
            }
        }
        final String downloadURL = (String) checkLink.get("download_url");
        if (downloadURL == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (Boolean.TRUE.equals(checkLink.get("server"))) {
            final String hash = (String) checkLink.get("hash");
            if (hash == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            waitLoop: while (true) {
                final Map<String, Object> statusLink = callAPI(account, null, "jd_status_link", new Object[] { "hash", hash });
                final Number statusCode = (Number) statusLink.get("status_code");
                final String status = (String) statusLink.get("status");
                if (statusCode != null) {
                    switch (statusCode.intValue()) {
                    case 1:// ready;
                        break waitLoop;
                    case 2:// pending
                        sleep(30 * 1000, link, status == null ? "Pending" : status);
                        break;
                    case 3:// initialization
                        sleep(30 * 1000, link, status == null ? "Initialization" : status);
                        break;
                    case 4:// downloading
                        sleep(30 * 1000, link, status == null ? "Downloading" : status);
                        break;
                    case 5:// error
                    default:
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, status);
                    }
                } else {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
        } else if (!Boolean.TRUE.equals(checkLink.get("direct"))) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, downloadURL, resumeFlag, maxConnections);
        if (dl.getConnection().getContentType().contains("text")) {
            br.followConnection();
            final String errorCode = new Regex(br.getURL(), "/download/error/(\\d+)").getMatch(0);
            if (errorCode != null) {
                switch (Integer.parseInt(errorCode)) {
                case 1: // No access
                    break;
                case 2: // File not Found
                    break;
                case 3: // You do not have enough transfer to download this file
                    break;
                case 4: // Not found an available host
                    break;
                case 5: // Can't download the selected file
                    break;
                case 6: // Temporarily unable to download the selected file. Please try again later
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Temporarily unable to download the selected file. Please try again later", 60 * 60 * 1000l);
                case 7: // At the moment from your IP is downloaded file
                    break;
                case 8:// Your session has expired. Before you download a file, please AGAIN log in to your account
                    dumpAccountInfos(account);
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                case 9:// Unknown issue
                default:
                    break;
                }
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        try {
            if (!this.dl.startDownload()) {
                try {
                    if (dl.externalDownloadStop()) {
                        return;
                    }
                } catch (final Throwable e) {
                }
                /* unknown error, we disable multiple chunks */
                if (link.getBooleanProperty(RapidsPl.NOCHUNKS, false) == false) {
                    link.setProperty(RapidsPl.NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            }
        } catch (final PluginException e) {
            // New V2 errorhandling
            /* unknown error, we disable multiple chunks */
            if (e.getLinkStatus() != LinkStatus.ERROR_RETRY && link.getBooleanProperty(RapidsPl.NOCHUNKS, false) == false) {
                link.setProperty(RapidsPl.NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            throw e;
        }
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        return AvailableStatus.UNCHECKABLE;
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) throws Exception {
        return true;
    }

    private void login(final Account account, final boolean force) throws Exception {
        synchronized (account) {
            try {
                br.setCookiesExclusive(true);
                newBrowser();
                final Cookies cookies = account.loadCookies("");
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (cookies != null && !force) {
                    br.setCookies(account.getHoster(), cookies);
                    return;
                }
                br.setFollowRedirects(true);
                br.postPage("https://rapids.pl/konto/loguj", "login=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
                if (br.getCookie(COOKIE_HOST, "remember_me") == null) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                br.getPage("/profil/api");
                // 64-bit key (changed 08.08.2016)
                String apikey = br.getRegex("<strong>Klucz:\\s*([a-z0-9]{64})\\s*<").getMatch(0);
                if (apikey == null) {
                    // 32 bit key (old)
                    apikey = br.getRegex("<strong>Klucz:\\s*([a-z0-9]{32})\\s*<").getMatch(0);
                }
                if (apikey == null) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    account.setProperty("apikey", apikey);
                }
                account.saveCookies(br.getCookies(this.getHost()), "");
            } catch (final PluginException e) {
                dumpAccountInfos(account);
                throw e;
            }
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
        if (link != null) {
            link.removeProperty(RapidsPl.NOCHUNKS);
        }
    }
}