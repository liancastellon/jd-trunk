package jd.plugins.hoster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.appwork.utils.Exceptions;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.net.usenet.InvalidAuthException;
import org.jdownloader.plugins.components.usenet.UsenetAccountConfigInterface;
import org.jdownloader.plugins.components.usenet.UsenetServer;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "newshosting.com" }, urls = { "" })
public class NewsHostingCom extends UseNet {
    public NewsHostingCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://controlpanel.newshosting.com/signup/signup.php");
    }

    @Override
    public String getAGBLink() {
        return "https://www.newshosting.com/terms-of-service.php";
    }

    private final String USENET_USERNAME = "USENET_USERNAME";

    @Override
    protected String getUsername(Account account) {
        return account.getStringProperty(USENET_USERNAME, account.getUser());
    }

    public static interface NewsHostingComConfigInterface extends UsenetAccountConfigInterface {
    };

    private Form getLoginForm(Browser br) {
        Form login = br.getFormbyActionRegex("^$");
        if (login == null) {
            login = br.getFormbyActionRegex("^/customer/login.php$");
            if (login == null) {
                login = br.getFormbyActionRegex("^/customer/index.php$");
            }
        }
        return login;
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        setBrowserExclusive();
        synchronized (account) {
            final AccountInfo ai = new AccountInfo();
            br.setFollowRedirects(true);
            final Cookies cookies = account.loadCookies("");
            try {
                if (cookies != null) {
                    br.setCookies(getHost(), cookies);
                    br.getPage("https://controlpanel.newshosting.com/customer/login.php");
                    final Form login = getLoginForm(br);
                    if (login != null && login.containsHTML("username") && login.containsHTML("password")) {
                        br.getCookies(getHost()).clear();
                    } else if (br.getCookie(getHost(), "sessionID") == null) {
                        br.getCookies(getHost()).clear();
                    }
                }
                if (br.getCookie(getHost(), "sessionID") == null) {
                    account.clearCookies("");
                    final String userName = account.getUser();
                    br.getPage("https://controlpanel.newshosting.com/customer/login.php");
                    Form login = getLoginForm(br);
                    if (login == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    login.put("username", Encoding.urlEncode(userName));
                    login.put("password", Encoding.urlEncode(account.getPass()));
                    br.submitForm(login);
                    login = getLoginForm(br);
                    if (login != null && login.containsHTML("username") && login.containsHTML("password")) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if (br.getCookie(getHost(), "sessionID") == null) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.saveCookies(br.getCookies(getHost()), "");
                final String userName = br.getRegex("Username:</strong>\\s*(.*?)<").getMatch(0);
                final String customerID = br.getRegex("Customer ID:</strong>\\s*(\\d+)").getMatch(0);
                if (userName == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } else {
                    account.setProperty(USENET_USERNAME, userName.trim());
                }
                final String nntpStatus = br.getRegex("NNTP Status:</strong>\\s*<span.*?>(.+?)<").getMatch(0);
                if (!StringUtils.equalsIgnoreCase(nntpStatus, "active")) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "NNTP Status:" + nntpStatus, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                final String validUntil = br.getRegex("Next Bill:</strong>\\s*(.*?)<").getMatch(0);
                final String bucketType = br.getRegex("Plan:</strong>\\s*(.*?)<").getMatch(0);
                if (bucketType != null) {
                    ai.setStatus(Encoding.htmlOnlyDecode(bucketType));
                    // https://www.ssl-news.info/signup.php
                    if (StringUtils.containsIgnoreCase(bucketType, "lite")) {
                        account.setMaxSimultanDownloads(30);
                    } else if (StringUtils.containsIgnoreCase(bucketType, "Unlimited")) {
                        account.setMaxSimultanDownloads(30);
                    } else if (StringUtils.containsIgnoreCase(bucketType, "Powerpack")) {
                        account.setMaxSimultanDownloads(60);
                    } else {
                        // smallest number of connections
                        account.setMaxSimultanDownloads(5);
                    }
                } else {
                    account.setMaxSimultanDownloads(1);
                    ai.setStatus("Unknown Type");
                }
                if (validUntil != null) {
                    final long date = TimeFormatter.getMilliSeconds(validUntil, "MMM dd',' yyyy", null);
                    if (date > 0) {
                        ai.setValidUntil(date + (24 * 60 * 60 * 1000l));
                    }
                }
                final String trafficTotal = br.getRegex("Byte Allott?ment:</strong>\\s*(\\d+)").getMatch(0);
                final String trafficLeft = br.getRegex("Bytes Remaining:</strong>\\s*(.*?)<").getMatch(0);
                if (trafficLeft != null && trafficTotal != null) {
                    ai.setTrafficMax(Long.parseLong(trafficTotal));
                    ai.setTrafficLeft(trafficLeft);
                } else if (StringUtils.equalsIgnoreCase(trafficLeft, "unlimited")) {
                    ai.setUnlimitedTraffic();
                }
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            } catch (IOException e) {
                logger.log(e);
                try {
                    verifyUseNetLogins(account);
                    account.setProperty(Account.PROPERTY_REFRESH_TIMEOUT, 1 * 60 * 60 * 1000l);
                    ai.setProperty("multiHostSupport", Arrays.asList(new String[] { "usenet" }));
                    return ai;
                } catch (InvalidAuthException e2) {
                    if (account.getProperty(USENET_USERNAME) != null) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, null, PluginException.VALUE_ID_PREMIUM_DISABLE, e2);
                    } else {
                        Exceptions.addSuppressed(e, e2);
                        throw e;
                    }
                }
            }
            account.setProperty(Account.PROPERTY_REFRESH_TIMEOUT, 2 * 60 * 60 * 1000l);
            ai.setProperty("multiHostSupport", Arrays.asList(new String[] { "usenet" }));
            return ai;
        }
    }

    @Override
    public List<UsenetServer> getAvailableUsenetServer() {
        final List<UsenetServer> ret = new ArrayList<UsenetServer>();
        ret.addAll(UsenetServer.createServerList("news.newshosting.com", false, 119, 23, 25, 80, 3128));
        ret.addAll(UsenetServer.createServerList("news.newshosting.com", true, 563, 563));
        return ret;
    }
}
