package jd.plugins.hoster;

import java.util.ArrayList;
import java.util.HashMap;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "geragera.com.br" }, urls = { "" })
public class GeraGeraComBr extends PluginForHost {
    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap = new HashMap<Account, HashMap<String, Long>>();
    private static Object                                  LOCK               = new Object();
    private static final String                            COOKIE_HOST        = "https://geragera.com.br";

    public GeraGeraComBr(PluginWrapper wrapper) {
        super(wrapper);
        setStartIntervall(1 * 1000l);
        this.enablePremium("https://geragera.com.br/premium");
    }

    @Override
    public String getAGBLink() {
        return "https://geragera.com.br/termos-de-uso";
    }

    private boolean isCookieSet(Browser br, String key) {
        final String value = br.getCookie(getHost(), key);
        return StringUtils.isNotEmpty(value) && !StringUtils.equalsIgnoreCase(value, "deleted");
    }

    private void login(final Account account) throws Exception {
        synchronized (account) {
            try {
                br.setCustomCharset("utf-8");
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    br.setCookies(getHost(), cookies);
                    br.getPage("https://geragera.com.br");
                    if (isCookieSet(br, "_cidsFG") && isCookieSet(br, "_cidFG")) {
                        account.saveCookies(br.getCookies(getHost()), "");
                        return;
                    } else {
                        account.clearCookies("");
                    }
                }
                br.setFollowRedirects(true);
                br.postPage("https://geragera.com.br/login", "email=" + Encoding.urlEncode(account.getUser()) + "&senha=" + Encoding.urlEncode(account.getPass()) + "&lembrar=1&login=1&entrar=ENTRAR");
                if (!isCookieSet(br, "_cidsFG") || !isCookieSet(br, "_cidFG")) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                account.saveCookies(br.getCookies(getHost()), "");
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ac = new AccountInfo();
        br.setConnectTimeout(60 * 1000);
        br.setReadTimeout(60 * 1000);
        login(account);
        br.getPage("https://geragera.com.br/meus-servidores");
        final String expireDays = br.getRegex("Expira em: (.*?)</div>").getMatch(0);
        if (expireDays != null) {
            account.setType(AccountType.PREMIUM);
            ac.setStatus("Premium Account");
            account.setMaxSimultanDownloads(20);
            ac.setValidUntil(TimeFormatter.getMilliSeconds(expireDays.trim(), "dd/MM/yyyy", null));
            if (!ac.isExpired()) {
                account.setConcurrentUsePossible(true);
            } else {
                account.setConcurrentUsePossible(false);
            }
        } else {
            account.setType(AccountType.FREE);
            account.setMaxSimultanDownloads(1);
            ac.setStatus("Free Account");
        }
        br.getPage("https://" + br.getHost() + "/");
        final ArrayList<String> supportedHosts = new ArrayList<String>();
        final String[] crippledHosts = br.getRegex("<div class=\"servidor ([^\"]+)\"").getColumn(0);
        for (final String crippledHost : crippledHosts) {
            supportedHosts.add(crippledHost);
        }
        ac.setMultiHostSupport(this, supportedHosts);
        return ac;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
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
        login(account);
        final String url = Encoding.urlEncode(link.getDownloadURL());
        br.getHeaders().put("Accept", "*/*");
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        showMessage(link, "Generating download link...");
        br.postPage("https://geragera.com.br/gerar-link", "link=" + Encoding.urlEncode(link.getDownloadURL()) + "&historico=1");
        final String error = PluginJSonUtils.getJsonValue(br, "erro");
        if (error != null && error.equals("2")) {
            // throw new PluginException(LinkStatus.ERROR_PREMIUM, "You are not premium"); // This will disable the account
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "You are not premium");
        }
        // String dllink = br.getRegex("\"download\":\"(.*?)\"\\}").getMatch(0);
        // dllink = dllink.replace("\\", "").replace("\"", "");
        final String dllink = PluginJSonUtils.getJsonValue(br, "download");
        if (dllink == null) {
            if (error != null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Erro:" + error);
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML("Erro \\d+")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: " + br.toString().trim(), 30 * 60 * 1000l);
            }
            logger.info("Unhandled download error on geragera.com.br: " + br.toString());
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return AvailableStatus.UNCHECKABLE;
    }

    private void tempUnavailableHoster(final Account account, final DownloadLink downloadLink, long timeout) throws PluginException {
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
    public boolean canHandle(final DownloadLink downloadLink, final Account account) throws Exception {
        return true;
    }

    private void showMessage(final DownloadLink link, final String message) {
        link.getLinkStatus().setStatusText(message);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}