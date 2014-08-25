package jd.plugins.hoster;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import jd.PluginWrapper;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.Request;
import jd.http.RequestHeader;
import jd.http.URLConnectionAdapter;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

import org.appwork.utils.net.HTTPHeader;
import org.appwork.utils.os.CrossSystem;

/**
 * Abstract class supporting keep2share/fileboom/publish2<br/>
 * <a href="https://github.com/keep2share/api/">Github documentation</a>
 *
 * @author raztoki
 *
 */
public abstract class K2SApi extends PluginForHost {

    private String          authToken;
    protected String        directlinkproperty;
    protected int           chunks;
    protected boolean       resumes;
    protected boolean       isFree;
    private final String    lng                    = System.getProperty("user.language");
    private final String    AUTHTOKEN              = "auth_token";
    private int             authTokenFail          = 0;

    // plugin config definition
    protected final String  USE_API                = "USE_API_2";
    protected final boolean default_USE_API        = true;
    protected final String  SSL_CONNECTION         = "SSL_CONNECTION_2";
    protected final boolean default_SSL_CONNECTION = true;

    public K2SApi(PluginWrapper wrapper) {
        super(wrapper);
    }

    /**
     * sets domain the API will use!
     *
     */
    protected abstract String getDomain();

    /**
     * Does the site enforce HTTPS? <br />
     * <br />
     *
     * @Override this when incorrect<br />
     * <br />
     *           <b>NOTE:</b> When setting to true, make sure that supportsHTTPS
     *
     * @return
     */
    protected boolean enforcesHTTPS() {
        return false;
    }

    /**
     * Does the site support HTTPS? <br />
     * <br />
     *
     * @Override this when incorrect
     *
     * @return
     */
    protected boolean supportsHTTPS() {
        return default_SSL_CONNECTION;
    }

    /**
     * useAPI frame work? <br />
     * <br />
     *
     * @Override this when incorrect
     *
     * @return
     */
    protected boolean useAPI() {
        return getPluginConfig().getBooleanProperty(USE_API, default_USE_API);
    }

    protected boolean useRUA() {
        return true;
    }

    protected String getApiUrl() {
        return getProtocol() + getDomain() + "/api/v1";
    }

    /**
     * Returns plugin specific user setting. <br />
     * <b>NOTE:</b> public method, so that the decrypter can use it!
     *
     * @author raztoki
     * @return
     */
    public String getProtocol() {
        return (isSecure() ? "https://" : "http://");
    }

    protected boolean isSecure() {
        if (enforcesHTTPS() && supportsHTTPS()) {
            // prevent bad setter from enforcing secure
            return true;
        } else if (supportsHTTPS() && getPluginConfig().getBooleanProperty(SSL_CONNECTION, default_SSL_CONNECTION)) {
            return true;
        } else {
            return false;
        }
    }

    protected String getFUID(final DownloadLink downloadLink) {
        return getFUID(downloadLink.getDownloadURL());
    }

    public String getFUID(final String link) {
        return new Regex(link, "/([a-z0-9]+)$").getMatch(0);
    }

    private static HashMap<String, String> antiDDoSCookies = new HashMap<String, String>();
    private static AtomicReference<String> agent           = new AtomicReference<String>(null);
    private boolean                        prepBrSet       = false;

    protected Browser prepADB(final Browser prepBr) {
        // define custom browser headers and language settings.
        // required for native cloudflare support, without the need to repeat requests.
        try {
            /* not available in old stable */
            prepBr.setAllowedResponseCodes(new int[] { 503, 522 });
        } catch (Throwable e) {
        }
        synchronized (antiDDoSCookies) {
            if (!antiDDoSCookies.isEmpty()) {
                for (final Map.Entry<String, String> cookieEntry : antiDDoSCookies.entrySet()) {
                    final String key = cookieEntry.getKey();
                    final String value = cookieEntry.getValue();
                    prepBr.setCookie(this.getHost(), key, value);
                }
            }
        }
        if (useRUA()) {
            if (agent.get() == null) {
                /* we first have to load the plugin, before we can reference it */
                JDUtilities.getPluginForHost("mediafire.com");
                agent.set(jd.plugins.hoster.MediafireCom.stringUserAgent());
            }
        }
        prepBr.getHeaders().put("User-Agent", agent.get());
        prepBr.getHeaders().put("Accept-Language", "en-gb, en;q=0.8");
        prepBr.getHeaders().put("Accept-Charset", null);
        // prepBr.getHeaders().put("Cache-Control", null);
        prepBr.getHeaders().put("Pragma", null);
        prepBrSet = true;
        return prepBr;
    }

    private Browser prepAPI(final Browser prepBr) {
        // prep site (this will load cloudflare)
        prepBrowser(prepBr);
        try {
            // response codes that API spews out.
            prepBr.setAllowedResponseCodes(400, 403, 406, 429);
        } catch (final Throwable t) {
            // not in stable;
        }
        return prepBr;
    }

    protected abstract Browser prepBrowser(final Browser prepBr);

    /**
     * sets DownloadLink LinkID property
     *
     * @param downloadLink
     * @throws PluginException
     */
    private void setFUID(final DownloadLink downloadLink) throws PluginException {
        String linkID = getFUID(downloadLink);
        if (linkID != null) {
            linkID = getDomain() + "://" + linkID;
            try {
                downloadLink.setLinkID(linkID);
            } catch (Throwable e) {
                // not in stable
                downloadLink.setProperty("LINKDUPEID", linkID);
            }
        } else {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
    }

    public boolean checkLinks(final DownloadLink[] urls) {
        // required to get overrides to work
        final Browser br = prepAPI(newBrowser());
        try {
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            while (true) {
                final StringBuilder sb = new StringBuilder();
                while (true) {
                    if (links.size() > 100 || index == urls.length) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                for (final DownloadLink dl : links) {
                    sb.append("\"" + getFUID(dl) + "\"");
                    sb.append(",");
                }
                // lets remove last ","
                sb.delete(sb.length() - 1, sb.length());
                postPageRaw(br, "/getfilesinfo", "{\"ids\":[" + sb.toString() + "]}", null);
                for (final DownloadLink dl : links) {
                    final String fuid = getFUID(dl);
                    final String filter = br.getRegex("(\\{\"id\":\"" + fuid + "\",[^\\}]+\\})").getMatch(0);
                    if (filter == null) {
                        return false;
                    }
                    final String status = getJson(filter, "is_available");
                    if (!"true".equalsIgnoreCase(status)) {
                        dl.setAvailable(false);
                    } else {
                        dl.setAvailable(true);
                    }
                    final String name = getJson(filter, "name");
                    final String size = getJson(filter, "size");
                    final String md5 = getJson(filter, "md5");
                    final String prem = getJson(filter, "premiumOnly");
                    final String pass = getJson(filter, "password");
                    if (!inValidate(name)) {
                        dl.setName(name);
                    }
                    if (!inValidate(size)) {
                        dl.setVerifiedFileSize(Long.parseLong(size));
                    }
                    if (!inValidate(md5)) {
                        dl.setMD5Hash(md5);
                    }
                    if (!inValidate(prem)) {
                        dl.setProperty("premiumRequired", Boolean.parseBoolean(prem));
                    }
                    if (!inValidate(pass)) {
                        dl.setProperty("passwordRequired", Boolean.parseBoolean(pass));
                    }
                    setFUID(dl);
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            return false;
        }
        return true;
    }

    /*
     * IMPORTANT: Current implementation seems to be correct - admin told us that there are no lifetime accounts (anymore)
     */
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        // required to get overrides to work
        br = prepAPI(newBrowser());
        postPageRaw(br, "/accountinfo", "{\"auth_token\":\"" + getAuthToken(account) + "\"}", account);
        final String available_traffic = getJson("available_traffic");
        final String account_expires = getJson("account_expires");
        if ("false".equalsIgnoreCase(account_expires)) {
            account.setProperty("free", true);
            ai.setStatus("Free Account");
        } else {
            account.setProperty("free", false);
            if (!inValidate(account_expires)) {
                ai.setValidUntil(Long.parseLong(account_expires) * 1000l);
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            ai.setStatus("Premium Account");
        }
        if (!inValidate(available_traffic)) {
            ai.setTrafficLeft(Long.parseLong(available_traffic));
        }
        setAccountLimits(account);
        account.setValid(true);
        return ai;
    }

    protected void setAccountLimits(final Account account) {
    }

    public void handleDownload(final DownloadLink downloadLink, final Account account) throws Exception {
        // linkcheck here..
        reqFileInformation(downloadLink);
        String fuid = getFUID(downloadLink);
        String dllink = checkDirectLink(downloadLink, directlinkproperty);
        // required to get overrides to work
        br = prepAPI(newBrowser());
        if (inValidate(dllink)) {
            if (account == null || account.getBooleanProperty("free", false)) {
                // free non account (still waiting on free download method
                postPageRaw(br, "/requestcaptcha", "", account);
                final String challenge = getJson("challenge");
                final String captcha_url = getJson("captcha_url");
                // Dependency
                if (inValidate(challenge) || inValidate(captcha_url)) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                final String code = getCaptchaCode(captcha_url, downloadLink);
                postPageRaw(br, "/geturl", "{\"file_id\":\"" + fuid + "\",\"free_download_key\":null,\"captcha_challenge\":\"" + challenge + "\",\"captcha_response\":\"" + Encoding.urlEncode(code) + "\"}", account);
                final String free_download_key = getJson("free_download_key");
                if (inValidate(free_download_key)) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                if (!inValidate(getJson("time_wait"))) {
                    sleep(Integer.parseInt(getJson("time_wait")) * 1001l, downloadLink);
                } else {
                    // fail over
                    sleep(31 * 1001l, downloadLink);
                }
                postPageRaw(br, "/geturl", "{\"file_id\":\"" + fuid + "\",\"free_download_key\":\"" + free_download_key + "\",\"captcha_challenge\":null,\"captcha_response\":null}", account);
            } else {
                // premium
                postPageRaw(br, "/geturl", "{\"auth_token\":\"" + getAuthToken(account) + "\",\"file_id\":\"" + fuid + "\"}", account);
            }
            dllink = getJson("url");
            if (inValidate(dllink)) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        logger.info("dllink = " + dllink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resumes, chunks);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            handleGeneralServerErrors(account);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty(directlinkproperty, dllink);
        // add download slot
        controlSlot(+1, account);
        try {
            dl.startDownload();
        } finally {
            // remove download slot
            controlSlot(-1, account);
        }
    }

    /**
     * We have to reinvent the wheel. With the help of @Override openPostConnection created us openRequestConnection in postRaw format.
     *
     * @author raztoki
     * @return
     */
    protected Browser newBrowser() {
        Browser nbr = new Browser() {

            /**
             * overrides openPostConnection and turns it into openPostRawConnection
             *
             * @author raztoki
             */
            @Override
            public URLConnectionAdapter openPostConnection(final String url, final String post) throws IOException {
                return this.openRequestConnection(this.createPostRawRequest(url, post));
            }

            /**
             * creates new Post Raw Request! merge components from JD2 Browser stripped of Appwork references.
             *
             * @author raztoki
             * @param url
             * @param post
             * @return
             * @throws MalformedURLException
             */
            public Request createPostRawRequest(final String url, final String post) throws MalformedURLException {
                final PostRequest request = new PostRequest(this.getURL(url));
                request.setPostDataString(post);

                String requestContentType = null;
                final RequestHeader lHeaders = this.getHeaders();
                if (lHeaders != null) {
                    final String browserContentType = lHeaders.remove("Content-Type");
                    if (requestContentType == null) {
                        requestContentType = browserContentType;
                    }
                }
                if (requestContentType == null) {
                    requestContentType = "application/x-www-form-urlencoded";
                }
                request.setContentType(requestContentType);
                return request;
            }

        };
        return nbr;
    }

    /**
     * general handling postPage requests! It's stable compliant with various response codes. It then passes to error handling!
     *
     * @param ibr
     * @param url
     * @param arg
     * @param account
     * @author raztoki
     * @throws Exception
     */
    public void postPageRaw(final Browser ibr, final String url, final String arg, final Account account) throws Exception {
        URLConnectionAdapter con = null;
        try {
            con = ibr.openPostConnection(getApiUrl() + url, arg);
            readConnection(con, ibr);
            antiDDoS(ibr);
            if (sessionTokenInvalid(account, ibr)) {
                // we retry once after failure!
                if (authTokenFail > 1) {
                    // not sure what todo here.. not really plugin defect
                    throw new PluginException(LinkStatus.ERROR_FATAL);
                }
                // lets retry
                try {
                    // a short wait
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
                // arg contains auth_key, we need to update the original request with new auth_token
                if (arg.contains("\"auth_token\"")) {
                    final String r = arg.replace(getJson(arg, "auth_token"), getAuthToken(account));
                    // re-enter using new auth_token
                    postPageRaw(ibr, url, r, account);
                    // error handling has been done by above re-entry
                    return;
                }
            }
            // we want to process handleErrors after each request. Lets hope centralised approach isn't used against us.
            handleErrors(account, ibr);
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
    }

    /**
     * @author razotki
     * @author jiaz
     * @param con
     * @param ibr
     * @throws IOException
     * @throws PluginException
     */
    private void readConnection(final URLConnectionAdapter con, final Browser ibr) throws IOException, PluginException {
        InputStream is = null;
        try {
            /* beta */
            try {
                con.setAllowedResponseCodes(new int[] { con.getResponseCode() });
            } catch (final Throwable e2) {
            }
            is = con.getInputStream();
        } catch (IOException e) {
            // stable fail over
            is = con.getErrorStream();
        }
        String t = readInputStream(is);
        if (t != null) {
            logger.fine("\r\n" + t);
            ibr.getRequest().setHtmlCode(t);
        }
    }

    /**
     * @author razotki
     * @author jiaz
     * @param es
     * @return
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    private String readInputStream(final InputStream is) throws UnsupportedEncodingException, IOException {
        BufferedReader f = null;
        try {
            f = new BufferedReader(new InputStreamReader(is, "UTF8"));
            String line;
            final StringBuilder ret = new StringBuilder();
            final String sep = System.getProperty("line.separator");
            while ((line = f.readLine()) != null) {
                if (ret.length() > 0) {
                    ret.append(sep);
                }
                ret.append(line);
            }
            return ret.toString();
        } finally {
            try {
                is.close();
            } catch (final Throwable e) {
            }
        }
    }

    private boolean sessionTokenInvalid(final Account account, final Browser ibr) {
        if ("error".equalsIgnoreCase(getJson(ibr, "status")) && ("10".equalsIgnoreCase(getJson(ibr, "errorCode")))) {
            // expired sessionToken
            dumpAuthToken(account);
            authTokenFail++;
            return true;
        } else {
            return false;
        }
    }

    public static Object ACCLOCK = new Object();

    private String getAuthToken(final Account account) throws Exception {
        synchronized (ACCLOCK) {
            if (authToken == null) {
                authToken = account.getStringProperty(AUTHTOKEN, null);
                if (authToken == null) {
                    // we don't want to pollute this.br
                    Browser auth = prepBrowser(newBrowser());
                    postPageRaw(auth, "/login", "{\"username\":\"" + account.getUser() + "\",\"password\":\"" + account.getPass() + "\"}", account);
                    authToken = getJson(auth, "auth_token");
                    if (authToken == null) {
                        // problemo?
                        logger.warning("problem in the old carel");
                        throw new PluginException(LinkStatus.ERROR_FATAL);
                    } else {
                        account.setProperty(AUTHTOKEN, authToken);
                    }
                }
            }
            return authToken;
        }
    }

    private void handleErrors(final Account account, final Browser ibr) throws PluginException {
        handleErrors(account, ibr.toString(), false);
    }

    private void handleErrors(final Account account, final String iString, final boolean subErrors) throws PluginException {
        if (inValidate(iString)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if ("success".equalsIgnoreCase(getJson(iString, "status")) && "200".equalsIgnoreCase(getJson(iString, "code")) && !subErrors) {
            return;
        }
        // let the error handling begin!
        String errCode = getJson(iString, "errorCode");
        if (inValidate(errCode) && subErrors) {
            // subErrors
            errCode = getJson(iString, "code");
        }
        if (!inValidate(errCode) && errCode.matches("\\d+")) {
            final int err = Integer.parseInt(errCode);
            String msg = getErrorMessage(err);
            try {
                switch (err) {
                case 1:
                    // DOWNLOAD_COUNT_EXCEEDED = 1; //'Download count files exceed'
                    // assume non account/free account
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, msg);
                case 2:
                    // DOWNLOAD_TRAFFIC_EXCEEDED = 2; //'Traffic limit exceed'
                    // assume all types
                    if (account == null) {
                        throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, msg);
                    } else {
                        final AccountInfo ai = new AccountInfo();
                        ai.setTrafficLeft(0);
                        account.setAccountInfo(ai);
                        throw new PluginException(LinkStatus.ERROR_RETRY, msg);
                    }
                case 3:
                    // DOWNLOAD_FILE_SIZE_EXCEEDED = 3;
                    // //"Free user can't download large files. Upgrade to PREMIUM and forget about limits."
                    premiumDownloadRestriction(msg);
                case 4:
                    // DOWNLOAD_NO_ACCESS = 4; //'You no can access to this file'
                    // not sure about this...
                    throw new PluginException(LinkStatus.ERROR_FATAL, msg);
                case 5:
                    // DOWNLOAD_WAITING = 5; //'Please wait to download this file'
                    // {"message":"Download not available","status":"error","code":406,"errorCode":42,"errors":[{"code":5,"timeRemaining":"2521.000000"}]}
                    // think timeRemaining is in seconds
                    String time = getJson(iString, "timeRemaining");
                    if (!inValidate(time) && time.matches("[\\d\\.]+")) {
                        time = time.substring(0, time.indexOf("."));
                        throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, getErrorMessage(err), Integer.parseInt(time) * 1000);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, getErrorMessage(err), 15 * 60 * 1000);
                    }
                case 6:
                    // DOWNLOAD_FREE_THREAD_COUNT_TO_MANY = 6; //'Free account does not allow to download more than one file at the same
                    // time'
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, msg);
                case 10:
                    // ERROR_YOU_ARE_NEED_AUTHORIZED = 10;
                    // this should never happen, as its handled within postPage and auth_token should be valid for download
                    dumpAuthToken(account);
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                case 20:
                case 21:
                case 22:
                    // ERROR_FILE_NOT_FOUND = 20;
                    // ERROR_FILE_IS_NOT_AVAILABLE = 21;
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, msg);
                    // ERROR_FILE_IS_BLOCKED = 22;
                    // what does this mean? premium only link ? treating as 'file not found'
                case 30:
                    // ERROR_CAPTCHA_REQUIRED = 30;
                    // this shouldn't happen in dl method.. be aware website login can contain captcha, api not of yet.
                    if (account != null) {
                        // {"message":"You need send request for free download with captcha fields","status":"error","code":406,"errorCode":30}
                        // false positive! dump cookies and retry
                        dumpAuthToken(account);
                        throw new PluginException(LinkStatus.ERROR_RETRY);
                    }
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                case 31:
                    // ERROR_CAPTCHA_INVALID = 31;
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA, msg);
                case 40:
                    // ERROR_WRONG_FREE_DOWNLOAD_KEY = 40;
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, msg);
                case 41:
                case 42:
                    // ERROR_NEED_WAIT_TO_FREE_DOWNLOAD = 41;
                    // ERROR_DOWNLOAD_NOT_AVAILABLE = 42;
                    // {"message":"Download is not available","status":"error","code":406,"errorCode":21,"errors":[{"code":2,"message":"Traffic limit exceed"}]}
                    // {"message":"Download not available","status":"error","code":406,"errorCode":42,"errors":[{"code":3}]}
                    // {"message":"Download not available","status":"error","code":406,"errorCode":42,"errors":[{"code":5,"timeRemaining":"2521.000000"}]}
                    // {"message":"Download is not available","status":"error","code":406,"errorCode":42,"errors":[{"code":6,"message":" Free account does not allow to download more than one file at the same time"}]}
                    // sub error, pass it back into itself.
                    handleErrors(account, getJsonArray(iString, "errors"), true);
                case 70:
                    // ERROR_INCORRECT_USERNAME_OR_PASSWORD = 70;
                    dumpAuthToken(account);
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, msg, PluginException.VALUE_ID_PREMIUM_DISABLE);
                case 71:
                    // ERROR_LOGIN_ATTEMPTS_EXCEEDED = 71;
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\n" + msg, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                case 72:
                    // ERROR_ACCOUNT_BANNED = 72;
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\n" + msg, PluginException.VALUE_ID_PREMIUM_DISABLE);
                case 73:
                case 74:
                    // ERROR_NO_ALLOW_ACCESS_FROM_NETWORK = 73;
                    // ERROR_UNKNOWN_LOGIN_ERROR = 74;
                    throw new PluginException(LinkStatus.ERROR_FATAL, msg);
                default:
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            } catch (final PluginException p) {
                logger.warning("ERROR :: " + msg);
                throw p;
            }
        } else if (errCode != null && !errCode.matches("\\d+")) {
            logger.warning("WTF LIKE!!!");
        } else {
            logger.info("all is good!");
        }
    }

    /**
     * Provides translation service
     *
     * @param code
     * @return
     */
    private String getErrorMessage(final int code) {
        String msg = null;
        if ("de".equalsIgnoreCase(lng)) {
            if (code == 70) {
                msg = "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.";
            }
        } else if ("es".equalsIgnoreCase(lng)) {

        }
        if (inValidate(msg)) {
            // default english!
            if (code == 1) {
                msg = "You've downloaded the maximum amount of files!";
            } else if (code == 2) {
                msg = "Traffic limit reached!";
            } else if (code == 3) {
                msg = "File size limitation";
            } else if (code == 4) {
                msg = "No access to this file!";
            } else if (code == 5) {
                msg = "Wait time detected!";
            } else if (code == 6) {
                msg = "Maximium number pararell downloads reached!";
            } else if (code == 10) {
                msg = "auth_token has expired!";
            } else if (code == 30) {
                msg = "Captcha required!";
            } else if (code == 31) {
                msg = "Invalid captcha";
            } else if (code == 40) {
                msg = "Wrong download key";
            } else if (code == 41) {
                msg = "Wait time detetected!";
            } else if (code == 42) {
                msg = "Download not avaiable at this time!";
            } else if (code == 70) {
                msg = "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.";
            } else if (code == 71) {
                msg = "You've tried logging in too many times!";
            } else if (code == 72) {
                msg = "Your account has been banned!";
            } else if (code == 73) {
                msg = "You can not access " + getDomain() + " from your current network connection!";
            } else if (code == 74) {
                msg = "Unknown login error!";
            }
        }
        return msg;
    }

    /**
     * When premium only download restriction (eg. filesize), throws exception with given message
     *
     * @param msg
     * @throws PluginException
     */
    public void premiumDownloadRestriction(final String msg) throws PluginException {
        try {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, msg, PluginException.VALUE_ID_PREMIUM_ONLY);
        } catch (final Throwable e) {
            if (e instanceof PluginException) {
                throw (PluginException) e;
            }
        }
        throw new PluginException(LinkStatus.ERROR_FATAL, msg);
    }

    private void dumpAuthToken(Account account) {
        synchronized (ACCLOCK) {
            // only wipe token when authToken equals current storable
            final String propertyToken = account.getStringProperty(AUTHTOKEN, null);
            if (authToken != null && propertyToken != null && authToken.equals(propertyToken)) {
                account.setProperty(AUTHTOKEN, Property.NULL);
                authToken = null;
            }
        }
    }

    protected void handleGeneralServerErrors(final Account account) throws PluginException {
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return reqFileInformation(link);
    }

    private AvailableStatus reqFileInformation(final DownloadLink link) throws Exception {
        correctDownloadLink(link);
        if (!checkLinks(new DownloadLink[] { link }) || !link.isAvailabilityStatusChecked()) {
            link.setAvailableStatus(AvailableStatus.UNCHECKABLE);
        } else if (!link.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return getAvailableStatus(link);
    }

    private AvailableStatus getAvailableStatus(DownloadLink link) {
        try {
            final Field field = link.getClass().getDeclaredField("availableStatus");
            field.setAccessible(true);
            Object ret = field.get(link);
            if (ret != null && ret instanceof AvailableStatus) {
                return (AvailableStatus) ret;
            }
        } catch (final Throwable e) {
        }
        return AvailableStatus.UNCHECKED;
    }

    protected String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property, null);
        if (!inValidate(dllink)) {
            URLConnectionAdapter con = null;
            try {
                Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return dllink;
    }

    /**
     * Tries to return value of key from JSon response, from String source.
     *
     * @author raztoki
     * */
    protected String getJson(final String source, final String key) {
        String result = new Regex(source, "\"" + key + "\":(-?\\d+(\\.\\d+)?|true|false|null)").getMatch(0);
        if (result == null) {
            result = new Regex(source, "\"" + key + "\":\"([^\"]+)\"").getMatch(0);
        }
        if (result != null) {
            result = result.replaceAll("\\\\/", "/");
        }
        return result;
    }

    /**
     * Tries to return value of key from JSon response, from default 'br' Browser.
     *
     * @author raztoki
     * */
    protected String getJson(final String key) {
        return getJson(br.toString(), key);
    }

    /**
     * Tries to return value of key from JSon response, from provided Browser.
     *
     * @author raztoki
     * */
    protected String getJson(final Browser ibr, final String key) {
        return getJson(ibr.toString(), key);
    }

    /**
     * Tries to return value given JSon Array of Key from JSon response provided String source.
     *
     * @author raztoki
     * */
    protected String getJsonArray(final String source, final String key) {
        String result = new Regex(source, "\"" + key + "\":(\\[[^\\]]+\\])").getMatch(0);
        if (result != null) {
            result = result.replaceAll("\\\\/", "/");
        }
        return result;
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     * */
    protected boolean inValidate(final String s) {
        if (s == null || s.matches("[\r\n\t ]+") || s.equals("")) {
            return true;
        } else {
            return false;
        }
    }

    private static Object                CTRLLOCK                     = new Object();
    protected static AtomicInteger       maxPrem                      = new AtomicInteger(1);
    protected static AtomicInteger       maxFree                      = new AtomicInteger(1);

    /**
     * Connection Management<br />
     * <b>NOTE:</b> CAN NOT be negative or zero! (ie. -1 or 0) Otherwise math sections fail. .:. use [1-20]<br />
     * <b>@Override</b> when incorrect
     **/
    protected static final AtomicInteger totalMaxSimultanFreeDownload = new AtomicInteger(1);

    /**
     * Prevents more than one free download from starting at a given time. One step prior to dl.startDownload(), it adds a slot to maxFree
     * which allows the next singleton download to start, or at least try.
     *
     * This is needed because xfileshare(website) only throws errors after a final dllink starts transferring or at a given step within pre
     * download sequence. But this template(XfileSharingProBasic) allows multiple slots(when available) to commence the download sequence,
     * this.setstartintival does not resolve this issue. Which results in x(20) captcha events all at once and only allows one download to
     * start. This prevents wasting peoples time and effort on captcha solving and|or wasting captcha trading credits. Users will experience
     * minimal harm to downloading as slots are freed up soon as current download begins.
     *
     * @param controlSlot
     *            (+1|-1)
     * @author raztoki
     * */
    protected void controlSlot(final int num, final Account account) {
        synchronized (CTRLLOCK) {
            if (account == null) {
                int was = maxFree.get();
                maxFree.set(Math.min(Math.max(1, maxFree.addAndGet(num)), totalMaxSimultanFreeDownload.get()));
                logger.info("maxFree was = " + was + " && maxFree now = " + maxFree.get());
            } else {
                int was = maxPrem.get();
                maxPrem.set(Math.min(Math.max(1, maxPrem.addAndGet(num)), account.getIntegerProperty("totalMaxSim", 20)));
                logger.info("maxPrem was = " + was + " && maxPrem now = " + maxPrem.get());
            }
        }
    }

    public void checkShowFreeDialog(final String domain) {
        SubConfiguration config = null;
        try {
            config = getPluginConfig();
            if (config.getBooleanProperty("premAdShown", Boolean.FALSE) == false) {
                if (config.getProperty("premAdShown2") == null) {
                    File checkFile = JDUtilities.getResourceFile("tmp/" + domain.toLowerCase().replaceAll("[\\.\\-]+", ""));
                    if (!checkFile.exists()) {
                        checkFile.mkdirs();
                        showFreeDialog(domain);
                    }
                } else {
                    config = null;
                }
            } else {
                config = null;
            }
        } catch (final Throwable e) {
        } finally {
            if (config != null) {
                config.setProperty("premAdShown", Boolean.TRUE);
                config.setProperty("premAdShown2", "shown");
                config.save();
            }
        }
    }

    protected void showFreeDialog(final String domain) {
        if (System.getProperty("org.jdownloader.revision") != null) { /* JD2 ONLY! */
            super.showFreeDialog(domain);
            return;
        }
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    try {
                        String lng = System.getProperty("user.language");
                        String message = null;
                        String title = null;
                        String tab = "                        ";
                        if ("de".equalsIgnoreCase(lng)) {
                            title = domain + " Free Download";
                            message = "Du lädst im kostenlosen Modus von " + domain + ".\r\n";
                            message += "Wie bei allen anderen Hostern holt JDownloader auch hier das Beste für dich heraus!\r\n\r\n";
                            message += tab + "  Falls du allerdings mehrere Dateien\r\n" + "          - und das möglichst mit Fullspeed und ohne Unterbrechungen - \r\n" + "             laden willst, solltest du dir den Premium Modus anschauen.\r\n\r\nUnserer Erfahrung nach lohnt sich das - Aber entscheide am besten selbst. Jetzt ausprobieren?  ";
                        } else {
                            title = domain + " Free Download";
                            message = "You are using the " + domain + " Free Mode.\r\n";
                            message += "JDownloader always tries to get the best out of each hoster's free mode!\r\n\r\n";
                            message += tab + "   However, if you want to download multiple files\r\n" + tab + "- possibly at fullspeed and without any wait times - \r\n" + tab + "you really should have a look at the Premium Mode.\r\n\r\nIn our experience, Premium is well worth the money. Decide for yourself, though. Let's give it a try?   ";
                        }
                        if (CrossSystem.isOpenBrowserSupported()) {
                            int result = JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null);
                            if (JOptionPane.OK_OPTION == result) {
                                CrossSystem.openURL(new URL("http://update3.jdownloader.org/jdserv/BuyPremiumInterface/redirect?" + domain + "&freedialog"));
                            }
                        }
                    } catch (Throwable e) {
                    }
                }
            });
        } catch (Throwable e) {
        }
    }

    // cloudflare

    /**
     * Gets page <br />
     * - natively supports silly cloudflare anti DDoS crapola
     *
     * @author raztoki
     */
    public void getPage(final Browser ibr, final String page) throws Exception {
        if (page == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!prepBrSet) {
            prepBrowser(ibr);
        }
        final boolean follows_redirects = ibr.isFollowingRedirects();
        URLConnectionAdapter con = null;
        ibr.setFollowRedirects(true);
        try {
            con = ibr.openGetConnection(page);
            readConnection(con, ibr);
            antiDDoS(ibr);
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
            ibr.setFollowRedirects(follows_redirects);
        }
    }

    /**
     * Wrapper into getPage(importBrowser, page), where browser = br;
     *
     * @author raztoki
     *
     * */
    public void getPage(final String page) throws Exception {
        getPage(br, page);
    }

    public void postPage(final Browser ibr, String page, final String postData) throws Exception {
        if (page == null || postData == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!prepBrSet) {
            prepBrowser(ibr);
        }
        // stable sucks
        if (isJava7nJDStable() && page.startsWith("https")) {
            page = page.replaceFirst("^https://", "http://");
        }
        ibr.getHeaders().put("Content-Type", "application/x-www-form-urlencoded");
        URLConnectionAdapter con = null;
        try {
            con = ibr.openPostConnection(page, postData);
            readConnection(con, ibr);
            antiDDoS(ibr);
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
            ibr.getHeaders().put("Content-Type", null);
        }
    }

    /**
     * Wrapper into postPage(importBrowser, page, postData), where browser == this.br;
     *
     * @author raztoki
     *
     * */
    public void postPage(String page, final String postData) throws Exception {
        postPage(br, page, postData);
    }

    public void sendForm(final Browser ibr, final Form form) throws Exception {
        if (form == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!prepBrSet) {
            prepBrowser(br);
        }
        // stable sucks && lame to the max, lets try and send a form outside of desired protocol. (works with oteupload)
        if (Form.MethodType.POST.equals(form.getMethod())) {
            // if the form doesn't contain an action lets set one based on current br.getURL().
            if (form.getAction() == null || form.getAction().equals("")) {
                form.setAction(br.getURL());
            }
            if (isJava7nJDStable() && (form.getAction().contains("https://") || /* relative path */(!form.getAction().startsWith("http")))) {
                if (!form.getAction().startsWith("http") && br.getURL().contains("https://")) {
                    // change relative path into full path, with protocol correction
                    String basepath = new Regex(br.getURL(), "(https?://.+)/[^/]+$").getMatch(0);
                    String basedomain = new Regex(br.getURL(), "(https?://[^/]+)").getMatch(0);
                    String path = form.getAction();
                    String finalpath = null;
                    if (path.startsWith("/")) {
                        finalpath = basedomain.replaceFirst("https://", "http://") + path;
                    } else if (!path.startsWith(".")) {
                        finalpath = basepath.replaceFirst("https://", "http://") + path;
                    } else {
                        // lacking builder for ../relative paths. this will do for now.
                        logger.info("Missing relative path builder. Must abort now... Try upgrading to JDownloader 2");
                        throw new PluginException(LinkStatus.ERROR_FATAL);
                    }
                    form.setAction(finalpath);
                } else {
                    form.setAction(form.getAction().replaceFirst("https?://", "http://"));
                }
                if (!stableSucks.get()) {
                    showSSLWarning(this.getHost());
                }
            }
            br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded");
        }
        URLConnectionAdapter con = null;
        try {
            con = ibr.openFormConnection(form);
            readConnection(con, ibr);
            antiDDoS(ibr);
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
            br.getHeaders().put("Content-Type", null);
        }
    }

    /**
     * Wrapper into sendForm(importBrowser, form), where browser == this.br;
     *
     * @author raztoki
     *
     * */
    public void sendForm(final Form form) throws Exception {
        sendForm(br, form);
    }

    /**
     * Performs Cloudflare and Incapsula requirements.<br />
     * Auto fill out the required fields and updates antiDDoSCookies session.<br />
     * Always called after Browser Request!
     *
     * @version 0.03
     * @author raztoki
     **/
    private void antiDDoS(final Browser ibr) throws Exception {
        if (ibr == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final HashMap<String, String> cookies = new HashMap<String, String>();
        if (ibr.getHttpConnection() != null) {
            final String URL = ibr.getURL();
            if (requestHeadersHasKeyNValueContains(ibr, "server", "cloudflare-nginx")) {
                Form cloudflare = ibr.getFormbyProperty("id", "ChallengeForm");
                if (cloudflare == null) {
                    cloudflare = ibr.getFormbyProperty("id", "challenge-form");
                }
                if (ibr.getHttpConnection().getResponseCode() == 403 && cloudflare != null) {
                    // new method seems to be within 403
                    if (cloudflare.hasInputFieldByName("recaptcha_response_field")) {
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
                        // recaptcha bullshit
                        String apiKey = cloudflare.getRegex("/recaptcha/api/(?:challenge|noscript)\\?k=([A-Za-z0-9%_\\+\\- ]+)").getMatch(0);
                        if (apiKey == null) {
                            apiKey = ibr.getRegex("/recaptcha/api/(?:challenge|noscript)\\?k=([A-Za-z0-9%_\\+\\- ]+)").getMatch(0);
                            if (apiKey == null) {
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }
                        }
                        final DownloadLink dllink = new DownloadLink(null, "antiDDoS Provider 'Clouldflare' requires Captcha", getDomain(), getProtocol() + getDomain(), true);
                        final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                        final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
                        rc.setId(apiKey);
                        rc.load();
                        final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                        final String response = getCaptchaCode(cf, dllink);
                        cloudflare.put("recaptcha_challenge_field", rc.getChallenge());
                        cloudflare.put("recaptcha_response_field", Encoding.urlEncode(response));
                        ibr.submitForm(cloudflare);
                        if (ibr.getFormbyProperty("id", "ChallengeForm") != null || ibr.getFormbyProperty("id", "challenge-form") != null) {
                            logger.warning("Possible plugin error within cloudflare handling");
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                    }
                } else if (ibr.getHttpConnection().getResponseCode() == 503 && cloudflare != null) {
                    // 503 response code with javascript math section
                    String host = new Regex(URL, "https?://([^/]+)(:\\d+)?/").getMatch(0);
                    String math = ibr.getRegex("\\$\\('#jschl_answer'\\)\\.val\\(([^\\)]+)\\);").getMatch(0);
                    if (math == null) {
                        math = ibr.getRegex("a\\.value = ([\\d\\-\\.\\+\\*/]+);").getMatch(0);
                    }
                    if (math == null) {
                        String variableName = ibr.getRegex("(\\w+)\\s*=\\s*\\$\\('#jschl_answer'\\);").getMatch(0);
                        if (variableName != null) {
                            variableName = variableName.trim();
                        }
                        math = ibr.getRegex(variableName + "\\.val\\(([^\\)]+)\\)").getMatch(0);
                    }
                    if (math == null) {
                        logger.warning("Couldn't find 'math'");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    // use js for now, but change to Javaluator as the provided string doesn't get evaluated by JS according to Javaluator
                    // author.
                    ScriptEngineManager mgr = jd.plugins.hoster.DummyScriptEnginePlugin.getScriptEngineManager(this);
                    ScriptEngine engine = mgr.getEngineByName("JavaScript");
                    final long value = ((Number) engine.eval("(" + math + ") + " + host.length())).longValue();
                    cloudflare.put("jschl_answer", value + "");
                    Thread.sleep(5500);
                    ibr.submitForm(cloudflare);
                    if (ibr.getFormbyProperty("id", "ChallengeForm") != null || ibr.getFormbyProperty("id", "challenge-form") != null) {
                        logger.warning("Possible plugin error within cloudflare handling");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                } else {
                    // nothing wrong, or something wrong (unsupported format)....
                    // commenting out return prevents caching of cookies per request
                    // return;
                }
                // get cookies we want/need.
                // refresh these with every getPage/postPage/submitForm?
                final Cookies add = ibr.getCookies(this.getHost());
                for (final Cookie c : add.getCookies()) {
                    if (new Regex(c.getKey(), "(cfduid|cf_clearance)").matches()) {
                        cookies.put(c.getKey(), c.getValue());
                    }
                }
            }
            // save the session!
            synchronized (antiDDoSCookies) {
                antiDDoSCookies.clear();
                antiDDoSCookies.putAll(cookies);
            }
        }
    }

    /**
     *
     * @author raztoki
     * */
    private boolean requestHeadersHasKeyNValueStartsWith(final Browser ibr, final String k, final String v) {
        if (k == null || v == null) {
            return false;
        }
        for (HTTPHeader s : ibr.getRequest().getHeaders()) {
            if (s.getKey().startsWith(k) && s.getValue().startsWith(v)) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @author raztoki
     * */
    private boolean requestHeadersHasKeyNValueContains(final Browser ibr, final String k, final String v) {
        if (k == null || v == null) {
            return false;
        }
        for (HTTPHeader s : ibr.getRequest().getHeaders()) {
            if (s.getKey().contains(k) && s.getValue().contains(v)) {
                return true;
            }
        }
        return false;
    }

    // end of cloudflare module.

    // stable browser is shite.

    private static boolean isJava7nJDStable() {
        if (System.getProperty("jd.revision.jdownloaderrevision") == null && System.getProperty("java.version").matches("1\\.[7-9].+")) {
            return true;
        } else {
            return false;
        }
    }

    private static AtomicBoolean stableSucks = new AtomicBoolean(false);

    public static void showSSLWarning(final String domain) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    try {
                        String lng = System.getProperty("user.language");
                        String message = null;
                        String title = null;
                        boolean xSystem = CrossSystem.isOpenBrowserSupported();
                        if ("de".equalsIgnoreCase(lng)) {
                            title = domain + " :: Java 7+ && HTTPS Post Requests.";
                            message = "Wegen einem Bug in in Java 7+ in dieser JDownloader version koennen wir keine HTTPS Post Requests ausfuehren.\r\n";
                            message += "Wir haben eine Notloesung ergaenzt durch die man weiterhin diese JDownloader Version nutzen kann.\r\n";
                            message += "Bitte bedenke, dass HTTPS Post Requests als HTTP gesendet werden. Nutzung auf eigene Gefahr!\r\n";
                            message += "Falls du keine unverschluesselten Daten versenden willst, update bitte auf JDownloader 2!\r\n";
                            if (xSystem) {
                                message += "JDownloader 2 Installationsanleitung und Downloadlink: Klicke -OK- (per Browser oeffnen)\r\n ";
                            } else {
                                message += "JDownloader 2 Installationsanleitung und Downloadlink:\r\n" + new URL("http://board.jdownloader.org/showthread.php?t=37365") + "\r\n";
                            }
                        } else if ("es".equalsIgnoreCase(lng)) {
                            title = domain + " :: Java 7+ && HTTPS Solicitudes Post.";
                            message = "Debido a un bug en Java 7+, al utilizar esta versión de JDownloader, no se puede enviar correctamente las solicitudes Post en HTTPS\r\n";
                            message += "Por ello, hemos añadido una solución alternativa para que pueda seguir utilizando esta versión de JDownloader...\r\n";
                            message += "Tenga en cuenta que las peticiones Post de HTTPS se envían como HTTP. Utilice esto a su propia discreción.\r\n";
                            message += "Si usted no desea enviar información o datos desencriptados, por favor utilice JDownloader 2!\r\n";
                            if (xSystem) {
                                message += " Las instrucciones para descargar e instalar Jdownloader 2 se muestran a continuación: Hacer Click en -Aceptar- (El navegador de internet se abrirá)\r\n ";
                            } else {
                                message += " Las instrucciones para descargar e instalar Jdownloader 2 se muestran a continuación, enlace :\r\n" + new URL("http://board.jdownloader.org/showthread.php?t=37365") + "\r\n";
                            }
                        } else {
                            title = domain + " :: Java 7+ && HTTPS Post Requests.";
                            message = "Due to a bug in Java 7+ when using this version of JDownloader, we can not successfully send HTTPS Post Requests.\r\n";
                            message += "We have added a work around so you can continue to use this version of JDownloader...\r\n";
                            message += "Please be aware that HTTPS Post Requests are sent as HTTP. Use at your own discretion.\r\n";
                            message += "If you do not want to send unecrypted data, please upgrade to JDownloader 2!\r\n";
                            if (xSystem) {
                                message += "Jdownloader 2 install instructions and download link: Click -OK- (open in browser)\r\n ";
                            } else {
                                message += "JDownloader 2 install instructions and download link:\r\n" + new URL("http://board.jdownloader.org/showthread.php?t=37365") + "\r\n";
                            }
                        }
                        int result = JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.CLOSED_OPTION, JOptionPane.CLOSED_OPTION);
                        if (xSystem && JOptionPane.OK_OPTION == result) {
                            CrossSystem.openURL(new URL("http://board.jdownloader.org/showthread.php?t=37365"));
                        }
                        stableSucks.set(true);
                    } catch (Throwable e) {
                    }
                }
            });
        } catch (Throwable e) {
        }
    }

}
