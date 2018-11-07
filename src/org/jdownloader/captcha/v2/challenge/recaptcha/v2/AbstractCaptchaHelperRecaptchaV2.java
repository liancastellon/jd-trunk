package org.jdownloader.captcha.v2.challenge.recaptcha.v2;

import java.util.regex.Pattern;

import jd.controlling.linkcrawler.CrawledLink;
import jd.http.Browser;
import jd.plugins.DownloadLink;
import jd.plugins.Plugin;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;

import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogInterface;
import org.jdownloader.logging.LogController;

public abstract class AbstractCaptchaHelperRecaptchaV2<T extends Plugin> {
    public static enum TYPE {
        NORMAL,
        INVISIBLE
    }

    protected final T       plugin;
    protected LogInterface  logger;
    protected final Browser br;
    protected String        siteKey;
    protected String        secureToken;

    public String getSecureToken() {
        return getSecureToken(br != null ? br.toString() : null);
    }

    protected String getSecureToken(final String source) {
        if (secureToken == null) {
            // from fallback url
            secureToken = new Regex(source, "&stoken=([^\"]+)").getMatch(0);
            if (secureToken == null) {
                secureToken = new Regex(source, "data-stoken\\s*=\\s*\"\\s*([^\"]+)").getMatch(0);
            }
        }
        return secureToken;
    }

    public TYPE getType() {
        return getType(br != null ? br.toString() : null);
    }

    protected TYPE getType(String source) {
        if (source != null) {
            final String[] divs = getDIVs(source);
            if (divs != null) {
                for (final String div : divs) {
                    if (new Regex(div, "class\\s*=\\s*('|\")(?:.*?\\s+)?g-recaptcha(\\1|\\s+)").matches()) {
                        final String siteKey = new Regex(div, "data-sitekey\\s*=\\s*('|\")\\s*(" + apiKeyRegex + ")\\s*\\1").getMatch(1);
                        if (siteKey != null && StringUtils.equals(siteKey, getSiteKey())) {
                            final boolean isInvisible = new Regex(div, "data-size\\s*=\\s*('|\")\\s*(invisible)\\s*\\1").matches();
                            if (isInvisible) {
                                return TYPE.INVISIBLE;
                            }
                        }
                    }
                }
            }
        }
        return TYPE.NORMAL;
    }

    public void setSecureToken(String secureToken) {
        this.secureToken = secureToken;
    }

    public String getSiteDomain() {
        return siteDomain;
    }

    protected String getSiteUrl() {
        final String siteDomain = getSiteDomain();
        String url = null;
        if (plugin != null) {
            if (plugin instanceof PluginForHost) {
                final DownloadLink downloadLink = ((PluginForHost) plugin).getDownloadLink();
                if (downloadLink != null) {
                    url = downloadLink.getPluginPatternMatcher();
                }
            } else if (plugin instanceof PluginForDecrypt) {
                final CrawledLink crawledLink = ((PluginForDecrypt) plugin).getCurrentLink();
                if (crawledLink != null) {
                    url = crawledLink.getURL();
                }
            }
            if (url != null && (StringUtils.startsWithCaseInsensitive(url, "https://") || StringUtils.startsWithCaseInsensitive(url, "http://"))) {
                if (br != null && br.getRequest() != null) {
                    if (StringUtils.startsWithCaseInsensitive(br.getURL(), "https")) {
                        url = url.replaceAll("^(?i)(https?://)", "https://");
                    } else {
                        url = url.replaceAll("^(?i)(https?://)", "http://");
                    }
                }
            } else if (StringUtils.equals(url, siteDomain) || StringUtils.equals(url, plugin.getHost())) {
                url = "http://" + url;
                if (br != null && br.getRequest() != null) {
                    if (StringUtils.startsWithCaseInsensitive(br.getURL(), "https")) {
                        url = url.replaceAll("^(?i)(https?://)", "https://");
                    } else {
                        url = url.replaceAll("^(?i)(https?://)", "http://");
                    }
                }
            } else {
                url = null;
            }
        }
        if (url == null) {
            url = br.getURL();
        }
        if (url != null) {
            url = url.replaceAll("(#.+)", "");
            final String urlDomain = Browser.getHost(url, true);
            if (!StringUtils.equalsIgnoreCase(urlDomain, siteDomain)) {
                url = url.replaceFirst(Pattern.quote(urlDomain), siteDomain);
            }
            return url;
        } else {
            return "http://" + siteDomain;
        }
    }

    private final String siteDomain;

    public AbstractCaptchaHelperRecaptchaV2(final T plugin, final Browser br, final String siteKey, final String secureToken, boolean boundToDomain) {
        this.plugin = plugin;
        this.br = br.cloneBrowser();
        if (br.getRequest() == null) {
            throw new IllegalStateException("Browser.getRequest() == null!");
        }
        this.siteDomain = Browser.getHost(br.getURL(), true);
        logger = plugin == null ? null : plugin.getLogger();
        if (logger == null) {
            createFallbackLogger();
        }
        this.siteKey = siteKey;
        this.secureToken = secureToken;
    }

    protected void createFallbackLogger() {
        Class<?> cls = getClass();
        String name = cls.getSimpleName();
        while (StringUtils.isEmpty(name)) {
            cls = cls.getSuperclass();
            name = cls.getSimpleName();
        }
        logger = LogController.getInstance().getLogger(name);
    }

    protected void runDdosPrevention() throws InterruptedException {
        if (plugin != null) {
            plugin.runCaptchaDDosProtection(RecaptchaV2Challenge.RECAPTCHAV2);
        }
    }

    public T getPlugin() {
        return plugin;
    }

    /**
     *
     *
     * @author raztoki
     * @since JD2
     * @return
     */
    public String getSiteKey() {
        return getSiteKey(br != null ? br.toString() : null);
    }

    private final static String apiKeyRegex = "[\\w-]+";

    protected String[] getDIVs(String source) {
        return new Regex(source, "<\\s*(div|button)(?:[^>]*>.*?</\\1>|[^>]*\\s*/\\s*>)").getColumn(-1);
    }

    /**
     * will auto find api key, based on google default &lt;div&gt;, @Override to make customised finder.
     *
     * @author raztoki
     * @since JD2
     * @return
     */
    protected String getSiteKey(final String source) {
        if (siteKey != null) {
            return siteKey;
        }
        if (source == null) {
            return null;
        }
        {
            // lets look for defaults
            final String[] divs = getDIVs(source);
            if (divs != null) {
                for (final String div : divs) {
                    if (new Regex(div, "class\\s*=\\s*('|\")(?:.*?\\s+)?g-recaptcha(\\1|\\s+)").matches()) {
                        siteKey = new Regex(div, "data-sitekey\\s*=\\s*('|\")\\s*(" + apiKeyRegex + ")\\s*\\1").getMatch(1);
                        if (siteKey != null) {
                            return siteKey;
                        }
                    }
                }
            }
        }
        {
            // can also be within <script> (for example cloudflare)
            final String[] scripts = new Regex(source, "<\\s*script\\s+(?:.*?<\\s*/\\s*script\\s*>|[^>]+\\s*/\\s*>)").getColumn(-1);
            if (scripts != null) {
                for (final String script : scripts) {
                    siteKey = new Regex(script, "data-sitekey=('|\")\\s*(" + apiKeyRegex + ")\\s*\\1").getMatch(1);
                    if (siteKey != null) {
                        return siteKey;
                    }
                }
            }
        }
        {
            // within iframe
            final String[] iframes = new Regex(source, "<\\s*iframe\\s+(?:.*?<\\s*/\\s*iframe\\s*>|[^>]+\\s*/\\s*>)").getColumn(-1);
            if (iframes != null) {
                for (final String iframe : iframes) {
                    siteKey = new Regex(iframe, "google\\.com/recaptcha/api/fallback\\?k=(" + apiKeyRegex + ")").getMatch(0);
                    if (siteKey != null) {
                        return siteKey;
                    }
                }
            }
        }
        {
            // json values in script or json
            final String jssource = new Regex(source, "recaptcha\\.render\\s*\\(.*?,\\s*\\{(.*?)\\}\\);").getMatch(0);
            if (jssource != null) {
                siteKey = new Regex(jssource, "('|\"|)sitekey\\1\\s*:\\s*('|\"|)\\s*(" + apiKeyRegex + ")\\s*\\2").getMatch(2);
            }
        }
        return siteKey;
    }
}
