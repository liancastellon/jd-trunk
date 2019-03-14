package org.jdownloader.plugins.components;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.http.requests.GetRequest;
import jd.http.requests.HeadRequest;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.UserAgents;
import jd.plugins.components.UserAgents.BrowserName;

import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogInterface;
import org.appwork.utils.parser.UrlQuery;
import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.scripting.JavaScriptEngineFactory;
import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ScriptableObject;

/**
 *
 * @author raztoki
 *
 */
@SuppressWarnings({ "deprecation", "unused" })
public abstract class antiDDoSForHost extends PluginForHost {
    public antiDDoSForHost(PluginWrapper wrapper) {
        super(wrapper);
    }

    protected boolean useRUA() {
        return false;
    }

    protected BrowserName setBrowserName() {
        return null;
    }

    private static final String                   cfRequiredCookies     = "__cfduid|cf_clearance";
    private static final String                   icRequiredCookies     = "visid_incap_\\d+|incap_ses_\\d+_\\d+";
    private static final String                   suRequiredCookies     = "sucuri_cloudproxy_uuid_[a-f0-9]+";
    private static final String                   bfRequiredCookies     = "rcksid|BLAZINGFAST-WEB-PROTECT";
    protected static HashMap<String, Cookies>     antiDDoSCookies       = new HashMap<String, Cookies>();
    protected static AtomicReference<String>      userAgent             = new AtomicReference<String>(null);
    private static AtomicReference<BrowserName>   browserName           = new AtomicReference<BrowserName>(null);
    protected final WeakHashMap<Browser, Boolean> browserPrepped        = new WeakHashMap<Browser, Boolean>();
    public final static String                    antiDDoSCookiePattern = cfRequiredCookies + "|" + icRequiredCookies + "|" + suRequiredCookies + "|" + bfRequiredCookies;

    protected Browser prepBrowser(final Browser prepBr, final String host) {
        if ((browserPrepped.containsKey(prepBr) && browserPrepped.get(prepBr) == Boolean.TRUE)) {
            return prepBr;
        }
        // define custom browser headers and language settings.
        // required for native cloudflare support, without the need to repeat requests.
        prepBr.addAllowedResponseCodes(new int[] { 429, 503, 504, 520, 521, 522, 523, 524, 525 });
        loadAntiDDoSCookies(prepBr, host);
        if (setBrowserName() != null && browserName.get() == null) {
            browserName.set(setBrowserName());
        }
        if (useRUA()) {
            if (userAgent.get() == null) {
                userAgent.set(browserName.get() != null ? UserAgents.stringUserAgent(browserName.get()) : UserAgents.stringUserAgent());
            }
            prepBr.getHeaders().put("User-Agent", userAgent.get());
        }
        prepBr.getHeaders().put("Accept-Language", "en-gb, en;q=0.8");
        prepBr.getHeaders().put("Accept-Charset", null);
        prepBr.getHeaders().put("Pragma", null);
        // we now set
        browserPrepped.put(prepBr, Boolean.TRUE);
        return prepBr;
    }

    protected void loadAntiDDoSCookies(Browser prepBr, final String host) {
        synchronized (antiDDoSCookies) {
            if (!antiDDoSCookies.isEmpty()) {
                for (final Map.Entry<String, Cookies> cookieEntry : antiDDoSCookies.entrySet()) {
                    final String key = cookieEntry.getKey();
                    if (key != null && key.equals(host)) {
                        try {
                            prepBr.setCookies(key, cookieEntry.getValue(), false);
                        } catch (final Throwable e) {
                        }
                    }
                }
            }
        }
    }

    /**
     * Wrapper into getPage(importBrowser, page), where browser = br;
     *
     * @author raztoki
     *
     */
    protected void getPage(final String page) throws Exception {
        getPage(br, page);
    }

    /**
     * Gets page <br />
     * - natively supports silly cloudflare anti DDoS crapola
     *
     * @author raztoki
     */
    protected void getPage(final Browser ibr, final String page) throws Exception {
        if (ibr == null || page == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        sendRequest(ibr, ibr.createGetRequest(page));
    }

    protected void postPage(final Browser ibr, String page, final String postData) throws Exception {
        if (ibr == null || page == null || postData == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        sendRequest(ibr, ibr.createPostRequest(page, postData));
    }

    /**
     * Wrapper into postPage(importBrowser, page, postData), where browser == this.br;
     *
     * @author raztoki
     *
     */
    protected void postPage(final String page, final String postData) throws Exception {
        postPage(br, page, postData);
    }

    protected void postPage(final Browser ibr, String page, final LinkedHashMap<String, String> param) throws Exception {
        if (ibr == null || page == null || param == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        sendRequest(ibr, ibr.createPostRequest(page, UrlQuery.get(param), null));
    }

    /**
     * Wrapper into postPage(importBrowser, page, param), where browser == this.br;
     *
     * @author raztoki
     *
     */
    protected void postPage(final String page, final LinkedHashMap<String, String> param) throws Exception {
        postPage(br, page, param);
    }

    protected void postPageRaw(final Browser ibr, final String page, final String post, final String encoding) throws Exception {
        if (ibr == null || page == null || post == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final PostRequest request = ibr.createPostRequest(page, new UrlQuery(), encoding);
        request.setPostDataString(post);
        sendRequest(ibr, request);
    }

    /**
     * Wrapper into postPageRaw(importBrowser, page, post, encoding)
     *
     * @author raztoki
     *
     */
    protected void postPageRaw(final Browser ibr, final String page, final String post, final boolean isJson) throws Exception {
        postPageRaw(ibr, page, post, isJson ? "application/json" : null);
    }

    /**
     * Wrapper into postPageRaw(importBrowser, page, post), where browser == this.br;
     *
     * @author raztoki
     *
     */
    protected void postPageRaw(final String page, final String post, final boolean isJson) throws Exception {
        postPageRaw(br, page, post, isJson);
    }

    /**
     * Wrapper into postPageRaw(importBrowser, page, post, null);
     *
     * @param ibr
     * @param page
     * @param post
     * @author raztoki
     * @throws Exception
     */
    protected void postPageRaw(final Browser ibr, final String page, final String post) throws Exception {
        postPageRaw(ibr, page, post, null);
    }

    /**
     * Wrapper into postPageRaw(importBrowser, page, post), where browser == this.br;
     *
     * @author raztoki
     *
     */
    protected void postPageRaw(final String page, final String post) throws Exception {
        postPageRaw(br, page, post);
    }

    protected void submitForm(final Browser ibr, final Form form) throws Exception {
        if (ibr == null || form == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        sendRequest(ibr, ibr.createFormRequest(form));
    }

    /**
     * Wrapper into sendForm(importBrowser, form), where browser == this.br;
     *
     * @author raztoki
     *
     */
    protected void submitForm(final Form form) throws Exception {
        submitForm(br, form);
    }

    /**
     * Wrapper into sendRequest(importBrowser, form), where browser == this.br;
     *
     * @author raztoki
     *
     */
    protected void sendRequest(final Request request) throws Exception {
        sendRequest(br, request);
    }

    protected void sendRequest(final Browser ibr, final Request request) throws Exception {
        final String host = Browser.getHost(request.getUrl());
        prepBrowser(ibr, host);
        int i = 0;
        while (true) {
            i++;
            // lazy lock
            if (isLocked()) {
                // we will wait, and we will randomise. This will help when lock is removed, not all threads will instantly submit request.
                Thread.sleep(getRandomWait());
                continue;
            }
            if (i > 1) {
                // we now need to update to the refreshed/latest cookie session
                loadAntiDDoSCookies(ibr, host);
                // reset request
                request.resetConnection();
            }
            final URLConnectionAdapter con = ibr.openRequestConnection(request);
            try {
                readConnection(con, ibr);
            } finally {
                con.disconnect();
            }
            try {
                antiDDoS(ibr);
                break;
            } catch (final ConcurrentLockException cle) {
                continue;
            }
        }
        runPostRequestTask(ibr);
    }

    /**
     * clone of sendRequest without disconnect and runPostRequestTask & and returns the http connection!
     *
     * @author Jiaz
     * @author raztoki
     */
    protected URLConnectionAdapter openAntiDDoSRequestConnection(final Browser ibr, Request request) throws Exception {
        final String host = Browser.getHost(request.getUrl());
        prepBrowser(ibr, host);
        int i = 0;
        while (true) {
            i++;
            // lazy lock
            if (isLocked()) {
                // we will wait, and we will randomise. This will help when lock is removed, not all threads will instantly submit request.
                Thread.sleep(getRandomWait());
                continue;
            }
            if (i > 1) {
                // we now need to update to the refreshed/latest cookie session
                loadAntiDDoSCookies(ibr, host);
                // reset request
                request.resetConnection();
            }
            ibr.openRequestConnection(request);
            try {
                antiDDoS(ibr, request);
                break;
            } catch (final ConcurrentLockException cle) {
                continue;
            }
        }
        return ibr.getHttpConnection();
    }

    /**
     * @author razotki
     * @author jiaz
     * @param con
     * @param ibr
     * @throws IOException
     * @throws PluginException
     */
    protected void readConnection(final URLConnectionAdapter con, final Browser ibr) throws IOException, PluginException {
        final Request request;
        if (con.getRequest() != null) {
            request = con.getRequest();
        } else {
            request = ibr.getRequest();
        }
        if (con.getRequest() != null && con.getRequest().getHtmlCode() != null) {
            return;
        } else if (con.getRequest() != null && !con.getRequest().isRequested()) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Request not sent yet!");
        } else if (!con.isConnected()) {
            // getInputStream/getErrorStream call connect!
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Connection is not connected!");
        }
        final InputStream is = getInputStream(con, ibr);
        request.setReadLimit(ibr.getLoadLimit());
        final byte[] responseBytes = Request.read(con, request.getReadLimit());
        request.setResponseBytes(responseBytes);
        LogInterface log = ibr.getLogger();
        if (log == null) {
            log = logger;
        }
        log.fine("\r\n" + request.getHtmlCode());
        if (request.isKeepByteArray() || ibr.isKeepResponseContentBytes()) {
            request.setKeepByteArray(true);
            request.setResponseBytes(responseBytes);
        }
    }

    /**
     * Override when you want to run a post request task. This is run after getPage/postPage/submitForm/sendRequest
     *
     * @param ibr
     */
    protected void runPostRequestTask(final Browser ibr) throws Exception {
    }

    protected InputStream getInputStream(final URLConnectionAdapter con, final Browser br) throws IOException {
        final int responseCode = con.getResponseCode();
        switch (responseCode) {
        case 502:
            // Bad Gateway
            break;
        case 542:
            // A timeout occurred
            break;
        default:
            con.setAllowedResponseCodes(new int[] { responseCode });
            break;
        }
        return con.getInputStream();
    }

    private int     a_responseCode429    = 0;
    private int     a_responseCode5xx    = 0;
    private boolean a_captchaRequirement = false;

    protected final boolean hasAntiddosCaptchaRequirement() {
        return a_captchaRequirement;
    }

    /**
     * wrapper for antiDDoS(Browser)
     *
     * @param ibr
     * @throws Exception
     */
    protected final void antiDDoS(final Browser ibr) throws Exception {
        antiDDoS(ibr, null);
    }

    /**
     * Performs Cloudflare, Incapsula, Sucuri requirements.<br />
     * Auto fill out the required fields and updates antiDDoSCookies session.<br />
     * Always called after Browser Request!
     *
     * @version 0.03
     * @author raztoki
     **/
    protected final void antiDDoS(final Browser ibr, final Request request) throws Exception {
        if (ibr == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final Cookies cookies = new Cookies();
        if (ibr.getHttpConnection() != null) {
            final Object lockObject = Thread.currentThread();
            try {
                // Cloudflare
                // if (requestHeadersHasKeyNValueContains(ibr, "server", "cloudflare-nginx")) {
                if (containsCloudflareCookies(ibr)) {
                    processCloudflare(lockObject, ibr, request, cookies);
                }
                // Incapsula
                else if (containsIncapsulaCookies(ibr)) {
                    processIncapsula(lockObject, ibr, cookies);
                }
                // Sucuri
                else if (containsSucuri(ibr)) {
                    processSucuri(ibr, cookies);
                }
                // BlazingFast
                else if (containsBlazingFast(ibr)) {
                    processBlazingFast(ibr, cookies);
                }
                // ddosprotectionru
                else if (containsDDoSProtectionRu(ibr)) {
                    processDDoSProtectionRu(lockObject, ibr, request);
                }
                // save the session!
                synchronized (antiDDoSCookies) {
                    antiDDoSCookies.put(ibr.getHost(), cookies);
                }
            } finally {
                releaseLock(lockObject);
            }
        }
    }

    private static AtomicReference<Object> concurrentLock = new AtomicReference<Object>(null);

    protected boolean isLocked() {
        final Object lock = concurrentLock.get();
        return lock != null && Thread.currentThread() != lock;
    }

    protected boolean acquireLock(Object lockObject) {
        return concurrentLock.compareAndSet(null, lockObject) || Thread.currentThread() == concurrentLock.get();
    }

    protected void releaseLock(Object lockObject) {
        concurrentLock.compareAndSet(lockObject, null);
    }

    private void processCloudflare(final Object lockObject, final Browser ibr, final Request request, final Cookies cookies) throws Exception {
        final int responseCode = ibr.getHttpConnection().getResponseCode();
        // all cloudflare events are behind text/html
        if (StringUtils.startsWithCaseInsensitive(ibr.getHttpConnection().getContentType(), "text/html")) {
            if (responseCode == 200) {
                // this has to be run here.. as if you put it down with the 200 mode below task can get confused.
                if (request != null) {
                    // used soley by openAntiDDoSRequestConnection, when open connection is used.
                    if (request instanceof HeadRequest) {
                        openAntiDDoSRequestConnection(ibr, new GetRequest(request));
                        return;
                    }
                }
            } else {
                if (request != null) {
                    // used soley by openAntiDDoSRequestConnection, when open connection is used.
                    if (request instanceof HeadRequest && isCloudFlareProtectionMode(responseCode)) {
                        openAntiDDoSRequestConnection(ibr, new GetRequest(request));
                        return;
                    }
                    ibr.followConnection();
                }
                // start
                if (ibr.getHttpConnection().getResponseCode() == 403 && ibr.containsHTML("<p>The owner of this website \\([^\\)]*" + Pattern.quote(ibr.getHost()) + "\\) has banned your IP address") && ibr.containsHTML("<title>Access denied \\| [^<]*" + Pattern.quote(ibr.getHost()) + " used CloudFlare to restrict access</title>")) {
                    // website address could be www. or what ever prefixes, need to make sure
                    // eg. within 403 response code,
                    // <p>The owner of this website (www.premiumax.net) has banned your IP address (x.x.x.x).</p>
                    // also common when proxies are used?? see keep2share.cc jdlog://5562413173041
                    String ip = ibr.getRegex("your IP address \\((.*?)\\)\\.</p>").getMatch(0);
                    String message = ibr.getHost() + " has banned your IP Address" + (inValidate(ip) ? "!" : "! " + ip);
                    logger.warning(message);
                    throw new PluginException(LinkStatus.ERROR_FATAL, message);
                } else if (responseCode == 521) {
                    // this basically indicates that the site is down, no need to retry.
                    // HTTP/1.1 521 Origin Down || <title>api.share-online.biz | 521: Web server is down</title>
                    a_responseCode5xx++;
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "CloudFlare says \"521 Origin Sever\" is down!", 5 * 60 * 1000l);
                } else if (responseCode == 504 || responseCode == 520 || responseCode == 522 || responseCode == 523 || responseCode == 524 || responseCode == 525) {
                    // these warrant retry instantly, as it could be just slave issue? most hosts have 2 DNS response to load balance.
                    // additional request could work via additional IP
                    /**
                     * @see clouldflare_504_snippet.html
                     */
                    // HTTP/1.1 504 Gateway Time-out
                    // HTTP/1.1 520 Origin Error
                    // HTTP/1.1 522 Origin Connection Time-out
                    /**
                     * @see cloudflare_523_snippet.html
                     */
                    // HTTP/1.1 523 Origin Unreachable
                    // 524: A timeout occurred, https://support.cloudflare.com/hc/en-us/articles/200171926-Error-524-A-timeout-occurred
                    // HTTP/1.1 525 Origin SSL Handshake Error || >CloudFlare is unable to establish an SSL connection to the origin
                    // server.<
                    // cache system with possible origin dependency... we will wait and retry
                    if (a_responseCode5xx == 4) {
                        // this only shows the last error in request, not the previous retries.
                        throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "CloudFlare says \"" + responseCode + " " + ibr.getHttpConnection().getResponseMessage() + "\"", 5 * 60 * 1000l);
                    }
                    a_responseCode5xx++;
                    // this html based cookie, set by <meta (for responseCode 522)
                    // <meta http-equiv="set-cookie" content="cf_use_ob=0; expires=Sat, 14-Jun-14 14:35:38 GMT; path=/">
                    String[] metaCookies = ibr.getRegex("<meta http-equiv=\"set-cookie\" content=\"(.*?; expries=.*?; path=.*?\";?(?: domain=.*?;?)?)\"").getColumn(0);
                    if (metaCookies != null && metaCookies.length != 0) {
                        final List<String> cookieHeaders = Arrays.asList(metaCookies);
                        final String date = ibr.getHeaders().get("Date");
                        final String host = Browser.getHost(ibr.getURL());
                        // get current cookies
                        final Cookies ckies = ibr.getCookies(host);
                        // add meta cookies to current previous request cookies
                        for (int i = 0; i < cookieHeaders.size(); i++) {
                            final String header = cookieHeaders.get(i);
                            ckies.add(Cookies.parseCookies(header, host, date));
                        }
                        // set ckies as current cookies
                        ibr.getHttpConnection().getRequest().setCookies(ckies);
                    }
                    Thread.sleep(2500);
                    // effectively refresh page!
                    try {
                        sendRequest(ibr, ibr.getRequest().cloneRequest());
                    } catch (final Exception t) {
                        // we want to preserve proper exceptions!
                        if (t instanceof PluginException) {
                            throw t;
                        }
                        t.printStackTrace();
                        throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Unexpected CloudFlare related issue", 5 * 60 * 1000l);
                    }
                    // new sendRequest saves cookie session
                    return;
                } else if (responseCode == 429 && ibr.containsHTML("<title>Access denied \\| \\S*" + Pattern.quote(ibr.getHost()) + " used Cloudflare to restrict access</title>")) {
                    // lock to prevent multiple queued events, other threads will need to listen to event and resumbit
                    if (acquireLock(lockObject)) {
                        if (a_responseCode429 == 4) {
                            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE);
                        }
                        a_responseCode429++;
                        // been blocked! need to wait 1min before next request. (says k2sadmin, each site could be configured differently)
                        Thread.sleep(61000);
                        // try again! -NOTE: this isn't stable compliant-
                        try {
                            sendRequest(ibr, ibr.getRequest().cloneRequest());
                        } catch (final Exception t) {
                            // we want to preserve proper exceptions!
                            if (t instanceof PluginException) {
                                throw t;
                            }
                            t.printStackTrace();
                            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE);
                        }
                        // new sendRequest saves cookie session
                        return;
                    } else {
                        // we need togo back and re-request!
                        throw new ConcurrentLockException();
                    }
                    // new code here...
                    // <script type="text/javascript">
                    // //<![CDATA[
                    // try{if (!window.CloudFlare) {var
                    // CloudFlare=[{verbose:0,p:1408958160,byc:0,owlid:"cf",bag2:1,mirage2:0,oracle:0,paths:{cloudflare:"/cdn-cgi/nexp/dokv=88e434a982/"},atok:"661da6801927b0eeec95f9f3e160b03a",petok:"107d6db055b8700cf1e7eec1324dbb7be6b978d0-1408974417-1800",zone:"fileboom.me",rocket:"0",apps:{}}];CloudFlare.push({"apps":{"ape":"3a15e211d076b73aac068065e559c1e4"}});!function(a,b){a=document.createElement("script"),b=document.getElementsByTagName("script")[0],a.async=!0,a.src="//ajax.cloudflare.com/cdn-cgi/nexp/dokv=97fb4d042e/cloudflare.min.js",b.parentNode.insertBefore(a,b)}()}}catch(e){};
                    // //]]>
                    // </script>
                } else {
                    final Form cloudflare = getCloudflareChallengeForm(ibr);
                    final Request originalRequest = ibr.getRequest();
                    if (responseCode == 403 && cloudflare != null) {
                        // lock to prevent multiple queued events, other threads will need to listen to event and resumbit
                        if (acquireLock(lockObject)) {
                            // set boolean value
                            a_captchaRequirement = true;
                            // recapthcha v2
                            if (cloudflare.containsHTML("class=\"g-recaptcha\"")) {
                                final DownloadLink dllink = new DownloadLink(null, (this.getDownloadLink() != null ? this.getDownloadLink().getName() + " :: " : "") + "antiDDoS Provider 'Clouldflare' requires Captcha", this.getHost(), "http://" + this.getHost(), true);
                                this.setDownloadLink(dllink);
                                final Form cf = cloudflare;
                                final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, ibr) {
                                    @Override
                                    public String getSiteKey() {
                                        return getSiteKey(cf.getHtmlCode());
                                    }

                                    @Override
                                    public String getSecureToken() {
                                        return getSecureToken(cf.getHtmlCode());
                                    }
                                }.getToken();
                                // Wed 1 Mar 2017 11:29:43 UTC, now additional inputfield constructed via javascript from html components
                                final String rayId = getRayID(ibr);
                                if (inValidate(rayId)) {
                                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                                }
                                cloudflare.put("id", Encoding.urlEncode(rayId));
                                cloudflare.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                            }
                            // recapthca v1
                            else if (cloudflare.hasInputFieldByName("recaptcha_response_field")) {
                                // they seem to add multiple input fields which is most likely meant to be corrected by js ?
                                // we will manually remove all those
                                while (cloudflare.hasInputFieldByName("recaptcha_response_field")) {
                                    cloudflare.remove("recaptcha_response_field");
                                }
                                while (cloudflare.hasInputFieldByName("recaptcha_challenge_field")) {
                                    cloudflare.remove("recaptcha_challenge_field");
                                }
                                // this one is null, needs to be ""
                                if (cloudflare.hasInputFieldByName("message")) {
                                    cloudflare.remove("message");
                                    cloudflare.put("messsage", "\"\"");
                                }
                                // recaptcha bullshit,
                                String apiKey = cloudflare.getRegex("/recaptcha/api/(?:challenge|noscript)\\?k=([A-Za-z0-9%_\\+\\- ]+)").getMatch(0);
                                if (apiKey == null) {
                                    apiKey = ibr.getRegex("/recaptcha/api/(?:challenge|noscript)\\?k=([A-Za-z0-9%_\\+\\- ]+)").getMatch(0);
                                    if (apiKey == null) {
                                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                                    }
                                }
                                final DownloadLink dllink = new DownloadLink(null, (this.getDownloadLink() != null ? this.getDownloadLink().getName() + " :: " : "") + "antiDDoS Provider 'Clouldflare' requires Captcha", this.getHost(), "http://" + this.getHost(), true);
                                final Recaptcha rc = new Recaptcha(ibr, this);
                                rc.setId(apiKey);
                                rc.load();
                                final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                                final String response = getCaptchaCode("recaptcha", cf, dllink);
                                if (inValidate(response)) {
                                    throw new PluginException(LinkStatus.ERROR_CAPTCHA, "CloudFlare, invalid captcha response!");
                                }
                                cloudflare.put("recaptcha_challenge_field", rc.getChallenge());
                                cloudflare.put("recaptcha_response_field", Encoding.urlEncode(response));
                            }
                            if (request != null) {
                                ibr.openFormConnection(cloudflare);
                            } else {
                                ibr.submitForm(cloudflare);
                            }
                            if (getCloudflareChallengeForm(ibr) != null) {
                                logger.warning("Wrong captcha");
                                throw new PluginException(LinkStatus.ERROR_CAPTCHA, "CloudFlare, incorrect captcha response!");
                            }
                            // on success cf_clearance cookie is set and a redirect will be present!
                            // we have a problem here when site expects POST request and redirects are always are GETS
                            if (originalRequest instanceof PostRequest) {
                                try {
                                    // resend originalRequest
                                    originalRequest.resetConnection();
                                    sendRequest(ibr, originalRequest);
                                } catch (final Exception t) {
                                    // we want to preserve proper exceptions!
                                    if (t instanceof PluginException) {
                                        throw t;
                                    }
                                    t.printStackTrace();
                                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Unexpected CloudFlare related issue", 5 * 60 * 1000l);
                                }
                                // because next round could be 200 response code, you need to nullify this value here.
                                a_captchaRequirement = false;
                                // new sendRequest saves cookie session
                                return;
                            } else if (!ibr.isFollowingRedirects() && ibr.getRedirectLocation() != null) {
                                ibr.getPage(ibr.getRedirectLocation());
                            }
                            a_captchaRequirement = false;
                        } else {
                            // we need togo back and re-request!
                            throw new ConcurrentLockException();
                        }
                    } else if (responseCode == 503 && cloudflare != null) {
                        // lock to prevent multiple queued events, other threads will need to listen to event and resumbit
                        if (acquireLock(lockObject)) {
                            // 503 response code with javascript math section && with 5 second pause
                            final String[] line1 = ibr.getRegex("var (?:t,r,a,f,|s,t,o,[a-z,]+) (\\w+)=\\{\"(\\w+)\":([^\\}]+)").getRow(0);
                            String line2 = ibr.getRegex("(\\;" + line1[0] + "." + line1[1] + ".*?t\\.length\\;)").getMatch(0);
                            if (line2 == null) {
                                // new 14.03.2019
                                line2 = ibr.getRegex("(\\;" + line1[0] + "." + line1[1] + ".*?t\\.length.*?;)").getMatch(0);
                            }
                            if (line2 == null) {
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }
                            final StringBuilder sb = new StringBuilder();
                            sb.append("var a={};\r\nvar t=\"" + Browser.getHost(ibr.getURL(), true) + "\";\r\n");
                            sb.append("var " + line1[0] + "={\"" + line1[1] + "\":" + line1[2] + "}\r\n");
                            sb.append(line2);
                            final ScriptEngineManager mgr = JavaScriptEngineFactory.getScriptEngineManager(this);
                            final ScriptEngine engine = mgr.getEngineByName("JavaScript");
                            final Object result = engine.eval(sb.toString());
                            if (result == null) {
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }
                            final String answer = result.toString();
                            cloudflare.getInputFieldByName("jschl_answer").setValue(answer + "");
                            Thread.sleep(5500);
                            // if it works, there should be a redirect.
                            if (request != null) {
                                ibr.openFormConnection(cloudflare);
                            } else {
                                ibr.submitForm(cloudflare);
                            }
                            /*
                             * ok we have issue here like below.. when request post redirect isn't the same as what came in! ie post > gets
                             * > need to resubmit original request.
                             */
                            if (originalRequest instanceof PostRequest) {
                                try {
                                    // resend originalRequest
                                    originalRequest.resetConnection();
                                    sendRequest(ibr, originalRequest);
                                } catch (final Exception t) {
                                    // we want to preserve proper exceptions!
                                    if (t instanceof PluginException) {
                                        throw t;
                                    }
                                    t.printStackTrace();
                                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Unexpected CloudFlare related issue", 5 * 60 * 1000l);
                                }
                                // new sendRequest saves cookie session
                                return;
                            } else if (!ibr.isFollowingRedirects() && ibr.getRedirectLocation() != null) {
                                // since we might not be following redirect, we need to get this one so we have correct html!
                                ibr.getPage(ibr.getRedirectLocation());
                            }
                        } else {
                            // we need togo back and re-request!
                            throw new ConcurrentLockException();
                        }
                    } else {
                        // unsupported mode? or just provider throwing weird codes
                    }
                }
            }
            /*
             * since we can call standard browser requests above, we need to match full conditions!
             */
            if (ibr.getHttpConnection().getResponseCode() == 200 && StringUtils.startsWithCaseInsensitive(ibr.getHttpConnection().getContentType(), "text/html")) {
                // active browser wont be a head request at this time. but request might not be followed yet due to open connections above.
                if (request != null) {
                    ibr.followConnection();
                }
                if (ibr.containsHTML("<title>Suspected phishing site\\s*\\|\\s*CloudFlare</title>")) {
                    final Form phishing = ibr.getFormbyAction("/cdn-cgi/phish-bypass");
                    if (phishing == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    if (request != null) {
                        ibr.openFormConnection(phishing);
                    } else {
                        ibr.submitForm(phishing);
                    }
                }
                /*
                 * cleanup stupid cloudflare email protections, done centrally as it messes with every site! And run this is because,
                 * filenames can contain @ char and this will mask them and break plugins.
                 */
                // note: must be LAST!
                cleanupCloudFlareEmailProtection(ibr, null);
            }
        }
        // get cookies we want/need.
        // refresh these with every getPage/postPage/submitForm?
        final Cookies add = ibr.getCookies(ibr.getHost());
        for (final Cookie c : add.getCookies()) {
            if (new Regex(c.getKey(), cfRequiredCookies).matches()) {
                cookies.add(c);
            }
        }
    }

    private boolean isCloudFlareProtectionMode(int responseCode) {
        // keep in sync cloudflare protection modes
        final boolean result = responseCode == 403 || responseCode == 429 || responseCode == 503 || responseCode == 504 || (responseCode >= 520 && responseCode <= 525);
        return result;
    }

    /**
     * id = rayid located within headers, and html. sixteen chars hex.
     */
    private String getRayID(Browser ibr) {
        String rayID = ibr.getRegex("data-ray=\"([a-f0-9]{16})\"").getMatch(0);
        if (inValidate(rayID)) {
            rayID = ibr.getRegex("Cloudflare Ray ID:\\s*<strong>([a-f0-9]{16})</strong>").getMatch(0);
            if (inValidate(rayID)) {
                // header response
                final String header = ibr.getRequest().getResponseHeader("CF-RAY");
                if (header != null) {
                    rayID = new Regex(header, "^([a-f0-9]{16})").getMatch(0);
                }
            }
        }
        return rayID;
    }

    private Form getCloudflareChallengeForm(final Browser ibr) {
        // speed things up, maintain our own code vs using br.getformby each time has to search and construct forms/inputfields! this is
        // slow!
        final Form[] forms = ibr.getForms();
        for (final Form form : forms) {
            if (form.getStringProperty("id") != null && form.getStringProperty("id").matches("challenge-form|ChallengeForm")) {
                return form;
            }
        }
        return null;
    }

    private void processIncapsula(final Object lockObject, final Browser ibr, final Cookies cookies) throws Exception {
        // they also rdns there servers to themsevles. we could use this.. but I think cookie is fine for now
        // nslookup 103.28.250.173
        // Name: 103.28.250.173.ip.incapdns.net
        // Address: 103.28.250.173
        // they also have additional header response, X-Iinfo or ("X-CDN", "Incapsula")**. ** = optional
        // not sure if this is the best way to detect this. could be done via line count (13) or html tag count (13 also including
        // closing tags)..
        final String functionz = ibr.getRegex("function\\(\\)\\s*\\{\\s*var z\\s*=\\s*\"\";.*?\\}\\)\\(\\);").getMatch(-1);
        final String[] crudeyes = ibr.toString().split("[\r\n]{1,2}");
        if (functionz != null && crudeyes != null && crudeyes.length < 15) {
            final String b = new Regex(functionz, "var b\\s*=\\s*\"(.*?)\";").getMatch(0);
            if (b != null) {
                String z = "";
                for (int i = 0; i < b.length(); i += 2) {
                    z = z + Integer.parseInt(b.substring(i, i + 2), 16) + ",";
                }
                z = z.substring(0, z.length() - 1);
                String a = "";
                for (String zz : z.split(",")) {
                    final int zzz = Integer.parseInt(zz);
                    a += Character.toString((char) zzz);
                }
                // now z contains two requests, first one unlocks, second is feedback, third is failover. don't think any
                // feedbacks/failovers are required but most likely improves your cookie health.
                final String c = new Regex(a, "xhr\\.open\\(\"GET\",\"(/_Incapsula_Resource\\?.*?)\"").getMatch(0);
                if (c != null) {
                    final Browser ajax = ibr.cloneBrowser();
                    ajax.getHeaders().put("Accept", "*/*");
                    ajax.getHeaders().put("Cache-Control", null);
                    ajax.getPage(c);
                    // now it should say "window.location.reload(true);"
                    if (ajax.containsHTML("window\\.location\\.reload\\(true\\);")) {
                        ibr.getPage(ibr.getURL());
                    } else {
                        // lag/delay between = no html
                        // should only happen in debug mode breakpointing!
                        // System.out.println("error");
                    }
                }
            }
        }
        // written support based on logged output from JDownloader.. not the best, but here goes! refine if it fails!
        // recaptcha events are loaded from iframe,
        // z reference is within the script src url (contains md5checksum), once decoded their is no magic within, unlike above.
        // on a single line
        if (crudeyes != null && crudeyes.length == 1) {
            // xinfo in the iframe is the same as header info...
            final String xinfo = ibr.getHttpConnection().getHeaderField("X-Iinfo");
            // so far in logs ive only seen this trigger after one does NOT answer a function z..
            final String azas = "<iframe[^<]*\\s+src=\"(/_Incapsula_Resource\\?(?:[^\"]+&|)xinfo=[^\"]*)\"";
            String iframe = ibr.getRegex(azas).getMatch(0);
            if (iframe != null && StringUtils.isNotEmpty(xinfo)) {
                final String validateXinfo = new Regex(iframe, "xinfo=(.*?)($|&)").getMatch(0);
                if (!StringUtils.equals(xinfo, validateXinfo) && !StringUtils.equals(xinfo, URLDecoder.decode(validateXinfo, "UTF-8"))) {
                    iframe = null;
                }
            }
            if (iframe != null) {
                final Browser ifr = ibr.cloneBrowser();
                // will need referrer,, but what other custom headers??
                ifr.getPage(iframe);
                if (acquireLock(lockObject)) {
                    a_captchaRequirement = true;
                    // recaptcha v2
                    if (ifr.containsHTML("class=\"g-recaptcha\"")) {
                        final DownloadLink dllink = new DownloadLink(null, (this.getDownloadLink() != null ? this.getDownloadLink().getName() + " :: " : "") + "antiDDoS Provider 'Incapsula' requires Captcha", this.getHost(), "http://" + this.getHost(), true);
                        this.setDownloadLink(dllink);
                        final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, ifr).getToken();
                        ifr.postPage("/_Incapsula_Resource?SWCGHOEL=v2", "g-recaptcha-response=" + Encoding.urlEncode(recaptchaV2Response));
                        if (ifr.containsHTML("class=\"g-recaptcha\"")) {
                            logger.warning("Wrong captcha");
                            throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                        } else if (ifr.containsHTML(">window\\.parent\\.location\\.reload\\(true\\);<")) {
                            // they show z again after captcha...
                            getPage(ibr.getURL());
                            // above request saves, as it re-enters this method!
                            a_captchaRequirement = false;
                            return;
                        }
                    } else if (ifr.containsHTML("captcha-form")) {
                        // I doubt that old recaptchav1 is still in use
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                } else {
                    // we need togo back and re-request!
                    throw new ConcurrentLockException();
                }
            }
        }
        // get cookies we want/need.
        // refresh these with every getPage/postPage/submitForm?
        final Cookies add = ibr.getCookies(ibr.getHost());
        for (final Cookie c : add.getCookies()) {
            if (new Regex(c.getKey(), icRequiredCookies).matches()) {
                cookies.add(c);
            }
        }
    }

    /**
     * <a href="https://kb.sucuri.net/cloudproxy/index">CloudProxy</a> antiDDoS method by <a href="https://sucuri.net/">Sucuri</a>,
     *
     * @author raztoki
     * @throws Exception
     */
    private void processSucuri(final Browser ibr, final Cookies cookies) throws Exception {
        if (ibr.containsHTML("<title>You are being redirected\\.\\.\\.</title>")) {
            // S = base encoded, the js is to just undo that.
            final String base64 = ibr.getRegex("(?i-)S\\s*=\\s*'([^']+)';").getMatch(0);
            if (base64 == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            String decode = Encoding.Base64Decode(base64);
            decode = decode.replace("location.reload();", "").replace("document.cookie", "y");
            Object result = new Object();
            if (base64 != null) {
                try {
                    final ScriptEngineManager manager = JavaScriptEngineFactory.getScriptEngineManager(this);
                    final ScriptEngine engine = manager.getEngineByName("javascript");
                    engine.eval("document = \"\";");
                    engine.eval(decode);
                    final ConsString y = (ConsString) engine.get("y");
                    ibr.setCookie(ibr.getHost(), y.toString().split("=")[0], y.toString().split("=")[1]);
                } catch (final Throwable e) {
                    e.printStackTrace();
                }
                ibr.getPage(ibr.getURL());
            }
        }
        // get cookies we want/need.
        // refresh these with every getPage/postPage/submitForm?
        final Cookies add = ibr.getCookies(ibr.getHost());
        for (final Cookie c : add.getCookies()) {
            if (new Regex(c.getKey(), suRequiredCookies).matches()) {
                cookies.add(c);
            }
        }
    }

    /**
     * <a href="https://blazingfast.io/">Blazingfast.io</a> 'web/vps/dedicated server' service provider with antiDDoS functionality within
     * product <a href="https://blazingfast.io/web">"web"</a>
     *
     * @author raztoki
     * @throws Exception
     */
    private void processBlazingFast(final Browser ibr, final Cookies cookies) throws Exception {
        // only one known protection measure (at this time)
        final Browser br = ibr.cloneBrowser();
        // javascript based checks.
        String xhr = br.getRegex("xhr\\.open\\(\"GET\",\"(.*?)\",true").getMatch(0);
        // ww is just screen size
        final String ww = "1920";
        xhr = xhr.replaceFirst("\"\\s*\\s*\\+\\s*ww\\s*\\+\\s*\"", ww);
        br.getHeaders().put("Accept", "*/*");
        // javascript.
        br.getPage(xhr);
        // replace window/document references, remove the redirect
        final String js = br.toString().replace("redir();", "").replaceFirst("if\\(\\$\\(window\\)\\.width\\(\\)>0\\)\\s*\\{.*?\\}", "");
        try {
            final ScriptEngineManager manager = JavaScriptEngineFactory.getScriptEngineManager(this);
            final ScriptEngine engine = manager.getEngineByName("javascript");
            engine.eval(js);
            engine.eval("var result = toHex(BFCrypt.decrypt(c,2,a,b));");
            final String result = (String) engine.get("result");
            ibr.setCookie(ibr.getURL(), "BLAZINGFAST-WEB-PROTECT", result);
        } catch (final Throwable e) {
            e.printStackTrace();
        }
        // redirect happens via js to specified url but its always current url.
        ibr.getPage(ibr.getURL());
        // get cookies we want/need.
        // refresh these with every getPage/postPage/submitForm?
        final Cookies add = ibr.getCookies(ibr.getHost());
        for (final Cookie c : add.getCookies()) {
            if (new Regex(c.getKey(), bfRequiredCookies).matches()) {
                cookies.add(c);
            }
        }
    }

    /**
     * one known method, Javascript. this is within text/html and request code 200? don't believe they set cookie, think they just track by
     * IP.
     *
     *
     * @author coalado
     * @author raztoki
     */
    private void processDDoSProtectionRu(final Object lockObject, final Browser ibr, final Request request) throws Exception {
        if (request != null) {
            // used soley by openAntiDDoSRequestConnection, when open connection is used.
            if (request instanceof HeadRequest) {
                openAntiDDoSRequestConnection(ibr, new GetRequest(request));
                return;
            }
            ibr.followConnection();
        }
        final String[] jsRedirectScripts = ibr.getRegex("<script language=\"JavaScript\">(.*?)</script>").getColumn(0);
        if (jsRedirectScripts != null && jsRedirectScripts.length == 1) {
            final int c = ibr.getRegex("\n").count();
            final boolean isJsRedirect = ibr.getRegex("<html><head><meta http-equiv=\"Content-Type\" content=\"[\\w\\-/;=]{20,50}\"></head>").matches();
            if (c <= 1 && isJsRedirect) {
                /* final jsredirectcheck */
                final int scriptLen = jsRedirectScripts[0].length();
                final int jsFactor = Math.round((float) scriptLen / (float) ibr.toString().length() * 100);
                /* min 75% of html contains js */
                if (jsFactor > 75) {
                    if (acquireLock(lockObject)) {
                        final String windowLocation = DDoSProtectionRu.getWindowLocation(jsRedirectScripts[0]);
                        // we really only are interested in the parameter as its window location. then add it to the original request?
                        if (windowLocation == null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        if (request != null) {
                            // open connection
                            final Request originalRequest = ibr.getRequest();
                            ibr.openGetConnection(windowLocation);
                            Thread.sleep(1250);
                            // resubmit original request.
                            originalRequest.resetConnection();
                            ibr.openRequestConnection(originalRequest);
                        } else {
                            // standard request
                            final Request originalRequest = ibr.getRequest();
                            ibr.getPage(windowLocation);
                            Thread.sleep(1250);
                            // resubmit original request.
                            originalRequest.resetConnection();
                            ibr.getPage(originalRequest);
                        }
                    } else {
                        // we need togo back and re-request!
                        throw new ConcurrentLockException();
                    }
                }
            }
        }
    }

    /**
     * returns true if browser contains cookies that match expected
     *
     * @author raztoki
     * @param ibr
     * @return
     */
    protected boolean containsCloudflareCookies(final Browser ibr) {
        final Cookies add = ibr.getCookies(ibr.getHost());
        for (final Cookie c : add.getCookies()) {
            if (new Regex(c.getKey(), cfRequiredCookies).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * returns true if browser contains cookies that match expected
     *
     * @author raztoki
     * @param ibr
     * @return
     */
    protected boolean containsIncapsulaCookies(final Browser ibr) {
        final Cookies add = ibr.getCookies(ibr.getHost());
        for (final Cookie c : add.getCookies()) {
            if (new Regex(c.getKey(), icRequiredCookies).matches()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSucuri(Browser ibr) {
        // newest 201710
        if (requestHeadersHasKeyNValueRegex(ibr, "X-Sucuri-ID", "^\\d+$")) {
            return true;
        }
        if (requestHeadersHasKeyNValueContains(ibr, "server", "Sucuri/Cloudproxy")) {
            return true;
        }
        return false;
    }

    protected boolean containsBlazingFast(final Browser ibr) {
        final boolean result = ibr.containsHTML("<title>Just a moment please\\.\\.\\.</title>") && ibr.containsHTML(">Verifying your browser, please wait\\.\\.\\.<br>DDoS Protection by</font> Blazingfast\\.io<");
        return result;
    }

    /**
     * they do not seem to have any identifiers that I could find outside of DNS. No unique header, or cookie
     *
     * @author raztoki
     */
    private boolean containsDDoSProtectionRu(final Browser ibr) {
        try {
            // only seen within 200 response code (but we won't use that) and html. for now just use text/html.
            if (StringUtils.startsWithCaseInsensitive(ibr.getHttpConnection().getContentType(), "text/html")) {
                // best way confirmation at this time is via dns response of the end server.
                final String ip = ((InetSocketAddress) ibr.getRequest().getHttpConnection().getEndPointSocketAddress()).getAddress().getHostAddress();
                if (new Regex(ip, "^195\\.211\\.22[0-3]\\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$").matches()) {
                    return true;
                }
            }
        } catch (final Exception e) {
            // make non fatal. it will fail somewhere else. no biggy
            e.printStackTrace();
        }
        return false;
    }

    /**
     *
     * @author raztoki
     */
    @SuppressWarnings("unused")
    private boolean requestHeadersHasKeyNValueStartsWith(final Browser ibr, final String k, final String v) {
        if (k == null || v == null || ibr == null || ibr.getHttpConnection() == null) {
            return false;
        }
        if (ibr.getHttpConnection().getHeaderField(k) != null && ibr.getHttpConnection().getHeaderField(k).toLowerCase(Locale.ENGLISH).startsWith(v.toLowerCase(Locale.ENGLISH))) {
            return true;
        }
        return false;
    }

    /**
     *
     * @author raztoki
     */
    private boolean requestHeadersHasKeyNValueContains(final Browser ibr, final String k, final String v) {
        if (k == null || v == null || ibr == null || ibr.getHttpConnection() == null) {
            return false;
        }
        if (ibr.getHttpConnection().getHeaderField(k) != null && ibr.getHttpConnection().getHeaderField(k).toLowerCase(Locale.ENGLISH).contains(v.toLowerCase(Locale.ENGLISH))) {
            return true;
        }
        return false;
    }

    /**
     *
     * @author raztoki
     */
    private boolean requestHeadersHasKeyNValueRegex(final Browser ibr, final String k, final String v) {
        if (k == null || v == null || ibr == null || ibr.getHttpConnection() == null) {
            return false;
        }
        final String value = ibr.getHttpConnection().getHeaderField(k);
        if (value != null && new Regex(value, v).matches()) {
            return true;
        }
        return false;
    }

    /**
     * Wrapper to return all browser cookies except cloudflare session cookies.
     *
     * @param host
     * @return
     */
    protected final HashMap<String, String> fetchCookies(final String host) {
        return fetchCookies(br, host);
    }

    /**
     * Generic method return all browser cookies except cloudflare session cookies.
     *
     * @param br
     * @param host
     * @return
     */
    protected final HashMap<String, String> fetchCookies(final Browser br, final String host) {
        final HashMap<String, String> cookies = new HashMap<String, String>();
        final Cookies add = br.getCookies(host);
        for (final Cookie c : add.getCookies()) {
            if (!c.getKey().matches(antiDDoSCookiePattern)) {
                cookies.put(c.getKey(), c.getValue());
            }
        }
        return cookies;
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     */
    protected boolean inValidate(final String s) {
        if (s == null || s.equals("") || s.matches("\\s+")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * supports cloudflare email protection crap. because email could be multiple times on a page and to reduce false positives input the
     * specified component to decode.
     *
     *
     * @author raztoki
     * @return
     */
    public static final String getStringFromCloudFlareEmailProtection(final String input) {
        // js component. hardcoded for now.
        final String a = new Regex(input, "data-cfemail\\s*=\\s*\"([a-f0-9]+)\"").getMatch(0);
        Object result = new Object();
        if (a != null) {
            Context cx = null;
            try {
                cx = ContextFactory.getGlobal().enterContext();
                ScriptableObject scope = cx.initStandardObjects();
                result = cx.evaluateString(scope, "var e, r, n, i, a = '" + a + "';if (a) { for (e = \"\", r = parseInt(a.substr(0, 2), 16), n = 2; a.length - n; n += 2) { i = parseInt(a.substr(n, 2), 16) ^ r; e += String.fromCharCode(i); } }", "<cmd>", 1, null);
            } catch (final Throwable e) {
                e.printStackTrace();
            } finally {
                Context.exit();
            }
        }
        return result != null ? result.toString() : null;
    }

    /**
     * method used to return String and if Browser is provided it will sethtml to current browser request.
     *
     * @author raztoki
     */
    public static final String cleanupCloudFlareEmailProtection(final Browser export, String input) throws PluginException {
        if (export == null && input == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Incorrect use of method");
        }
        if (input == null) {
            input = export.toString();
        }
        final String[] results = new Regex(input, "<a(?:\\s+[^>]+)?\\s+href=\"/cdn-cgi/l/email-protection\"[^>]*>[^<]+</a>").getColumn(-1);
        if (results != null) {
            // simple hashset to reduce potential cpu cycles
            final HashSet<String> dupe = new HashSet<String>();
            String messswithme = input;
            for (final String result : results) {
                if (dupe.add(result)) {
                    messswithme = messswithme.replace(result, getStringFromCloudFlareEmailProtection(result));
                }
            }
            if (export != null) {
                export.getRequest().setHtmlCode(messswithme);
            }
            // has changed
            return messswithme;
        }
        // hasn't changed
        return input;
    }

    private long getRandomWait() {
        long wait = 0;
        do {
            wait = (new Random().nextInt(150)) * (new Random().nextInt(100));
        } while (wait > 15000 && wait < 500);
        return wait;
    }
}
