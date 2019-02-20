package jd.controlling.linkcrawler;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import jd.controlling.linkcollector.LinkCollectingJob;
import jd.controlling.linkcollector.LinkCollector.JobLinkCrawler;
import jd.controlling.linkcollector.LinknameCleaner;
import jd.controlling.linkcrawler.LinkCrawlerConfig.DirectHTTPPermission;
import jd.http.Authentication;
import jd.http.AuthenticationFactory;
import jd.http.Browser;
import jd.http.CallbackAuthenticationFactory;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.DefaultAuthenticanFactory;
import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.http.URLUserInfoAuthentication;
import jd.http.requests.GetRequest;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.parser.html.HTMLParser;
import jd.parser.html.HTMLParser.HtmlParserCharSequence;
import jd.parser.html.HTMLParser.HtmlParserResultSet;
import jd.plugins.CryptedLink;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.Plugin;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.PluginsC;

import org.appwork.exceptions.WTFException;
import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.scheduler.DelayedRunnable;
import org.appwork.storage.config.JsonConfig;
import org.appwork.uio.CloseReason;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Files;
import org.appwork.utils.IO;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.Base64;
import org.appwork.utils.encoding.URLEncode;
import org.appwork.utils.logging2.ClearableLogInterface;
import org.appwork.utils.logging2.ClosableLogInterface;
import org.appwork.utils.logging2.LogInterface;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.net.URLHelper;
import org.appwork.utils.net.httpconnection.HTTPConnectionUtils.DispositionHeader;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.swing.dialog.LoginDialog;
import org.appwork.utils.swing.dialog.LoginDialogInterface;
import org.jdownloader.auth.AuthenticationController;
import org.jdownloader.auth.AuthenticationInfo;
import org.jdownloader.auth.AuthenticationInfo.Type;
import org.jdownloader.controlling.UniqueAlltimeID;
import org.jdownloader.controlling.UrlProtection;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.logging.LogController;
import org.jdownloader.myjdownloader.client.json.AvailableLinkState;
import org.jdownloader.plugins.controller.LazyPlugin;
import org.jdownloader.plugins.controller.PluginClassLoader;
import org.jdownloader.plugins.controller.PluginClassLoader.PluginClassLoaderChild;
import org.jdownloader.plugins.controller.UpdateRequiredClassNotFoundException;
import org.jdownloader.plugins.controller.container.ContainerPluginController;
import org.jdownloader.plugins.controller.crawler.CrawlerPluginController;
import org.jdownloader.plugins.controller.crawler.LazyCrawlerPlugin;
import org.jdownloader.plugins.controller.crawler.LazyCrawlerPlugin.FEATURE;
import org.jdownloader.plugins.controller.host.HostPluginController;
import org.jdownloader.plugins.controller.host.LazyHostPlugin;
import org.jdownloader.settings.GeneralSettings;
import org.jdownloader.translate._JDT;

public class LinkCrawler {
    private static enum DISTRIBUTE {
        STOP,
        BLACKLISTED,
        NEXT,
        CONTINUE
    }

    protected static enum DUPLICATE {
        CONTAINER,
        CRAWLER,
        FINAL,
        DEEP
    }

    private final static String                                      DIRECT_HTTP                 = "directhttp";
    private final static String                                      HTTP_LINKS                  = "http links";
    private final static int                                         MAX_THREADS;
    private java.util.List<CrawledLink>                              crawledLinks                = new ArrayList<CrawledLink>();
    private AtomicInteger                                            crawledLinksCounter         = new AtomicInteger(0);
    private java.util.List<CrawledLink>                              filteredLinks               = new ArrayList<CrawledLink>();
    private AtomicInteger                                            filteredLinksCounter        = new AtomicInteger(0);
    private java.util.List<CrawledLink>                              brokenLinks                 = new ArrayList<CrawledLink>();
    private AtomicInteger                                            brokenLinksCounter          = new AtomicInteger(0);
    private java.util.List<CrawledLink>                              unhandledLinks              = new ArrayList<CrawledLink>();
    private final AtomicInteger                                      unhandledLinksCounter       = new AtomicInteger(0);
    private final AtomicInteger                                      processedLinksCounter       = new AtomicInteger(0);
    private final AtomicBoolean                                      runningState                = new AtomicBoolean(false);
    private final AtomicInteger                                      crawler                     = new AtomicInteger(0);
    private final static AtomicInteger                               CRAWLER                     = new AtomicInteger(0);
    private final Map<String, Object>                                duplicateFinderContainer;
    private final Map<String, Set<Object>>                           duplicateFinderCrawler;
    private final Map<String, CrawledLink>                           duplicateFinderFinal;
    private final Map<String, Object>                                duplicateFinderDeep;
    private final Map<CrawledLink, Object>                           loopPreventionEmbedded;
    private LinkCrawlerHandler                                       handler                     = null;
    protected static final ThreadPoolExecutor                        threadPool;
    private LinkCrawlerFilter                                        filter                      = null;
    private final LinkCrawler                                        parentCrawler;
    private final long                                               created;
    public final static String                                       PACKAGE_ALLOW_MERGE         = "ALLOW_MERGE";
    public final static String                                       PACKAGE_ALLOW_INHERITANCE   = "ALLOW_INHERITANCE";
    public final static String                                       PACKAGE_CLEANUP_NAME        = "CLEANUP_NAME";
    public final static String                                       PACKAGE_IGNORE_VARIOUS      = "PACKAGE_IGNORE_VARIOUS";
    public static final UniqueAlltimeID                              PERMANENT_OFFLINE_ID        = new UniqueAlltimeID();
    private boolean                                                  doDuplicateFinderFinalCheck = true;
    private List<LazyCrawlerPlugin>                                  unsortedLazyCrawlerPlugins;
    protected final PluginClassLoaderChild                           classLoader;
    private final String                                             defaultDownloadFolder;
    private final AtomicReference<List<LazyCrawlerPlugin>>           sortedLazyCrawlerPlugins    = new AtomicReference<List<LazyCrawlerPlugin>>();
    private final AtomicReference<List<LazyHostPlugin>>              sortedLazyHostPlugins       = new AtomicReference<List<LazyHostPlugin>>();
    private final AtomicReference<List<LinkCrawlerRule>>             linkCrawlerRules            = new AtomicReference<List<LinkCrawlerRule>>();
    private LinkCrawlerDeepInspector                                 deepInspector               = null;
    private DirectHTTPPermission                                     directHTTPPermission        = DirectHTTPPermission.ALWAYS;
    protected final UniqueAlltimeID                                  uniqueAlltimeID             = new UniqueAlltimeID();
    protected final WeakHashMap<LinkCrawler, Object>                 children                    = new WeakHashMap<LinkCrawler, Object>();
    protected final static WeakHashMap<LinkCrawler, LinkCrawlerLock> LOCKS                       = new WeakHashMap<LinkCrawler, LinkCrawlerLock>();
    protected final AtomicReference<LinkCrawlerGeneration>           linkCrawlerGeneration       = new AtomicReference<LinkCrawlerGeneration>(null);

    protected LinkCrawlerGeneration getValidLinkCrawlerGeneration() {
        synchronized (linkCrawlerGeneration) {
            LinkCrawlerGeneration ret = linkCrawlerGeneration.get();
            if (ret == null || !ret.isValid()) {
                ret = new LinkCrawlerGeneration();
                linkCrawlerGeneration.set(ret);
            }
            return ret;
        }
    }

    public class LinkCrawlerGeneration {
        private final AtomicBoolean validFlag = new AtomicBoolean(true);

        public final boolean isValid() {
            return validFlag.get() && LinkCrawler.this.linkCrawlerGeneration.get() == this;
        }

        protected final void invalidate() {
            validFlag.set(false);
        }
    }

    protected LinkCrawlerLock getLinkCrawlerLock(final LazyCrawlerPlugin plugin, final CrawledLink crawledLink) {
        synchronized (LOCKS) {
            LinkCrawlerLock ret = null;
            for (final LinkCrawlerLock lock : LOCKS.values()) {
                if (lock.matches(plugin, crawledLink) && (ret == null || lock.maxConcurrency() < ret.maxConcurrency())) {
                    ret = lock;
                }
            }
            if (ret == null) {
                ret = new LinkCrawlerLock() {
                    private final String pluginID = getPluginID(plugin);

                    @Override
                    public boolean matches(LazyCrawlerPlugin plugin, CrawledLink crawledLink) {
                        return StringUtils.equals(pluginID, getPluginID(plugin));
                    }

                    @Override
                    public String toString() {
                        return pluginID + "|" + maxConcurrency();
                    }

                    @Override
                    public int maxConcurrency() {
                        return Math.max(1, plugin.getMaxConcurrentInstances());
                    }
                };
                LOCKS.put(getRoot(), ret);
            }
            return ret;
        }
    }

    public void addSequentialLockObject(LinkCrawlerLock lock) {
        if (lock != null) {
            synchronized (LOCKS) {
                LOCKS.put(getRoot(), lock);
            }
        }
    }

    public boolean removeSequentialLockObject(LinkCrawlerLock lock) {
        if (lock != null) {
            synchronized (LOCKS) {
                return LOCKS.values().remove(lock);
            }
        } else {
            return false;
        }
    }

    public UniqueAlltimeID getUniqueAlltimeID() {
        return uniqueAlltimeID;
    }

    public LinkCrawler getParent() {
        return parentCrawler;
    }

    private final static LinkCrawlerConfig CONFIG = JsonConfig.create(LinkCrawlerConfig.class);

    public static LinkCrawlerConfig getConfig() {
        return CONFIG;
    }

    public void setDirectHTTPPermission(DirectHTTPPermission directHTTPPermission) {
        if (directHTTPPermission == null) {
            this.directHTTPPermission = DirectHTTPPermission.ALWAYS;
        } else {
            this.directHTTPPermission = directHTTPPermission;
        }
    }

    protected final static ScheduledExecutorService TIMINGQUEUE = DelayedRunnable.getNewScheduledExecutorService();

    public boolean isDoDuplicateFinderFinalCheck() {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.isDoDuplicateFinderFinalCheck();
        }
        return doDuplicateFinderFinalCheck;
    }

    protected Long getDefaultAverageRuntime() {
        return null;
    }

    public int getMaxThreads() {
        return MAX_THREADS;
    }

    static {
        MAX_THREADS = Math.max(CONFIG.getMaxThreads(), 1);
        final int keepAlive = Math.max(CONFIG.getThreadKeepAlive(), 100);
        /**
         * PriorityBlockingQueue leaks last Item for some java versions
         *
         * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=7161229
         */
        threadPool = new ThreadPoolExecutor(MAX_THREADS, MAX_THREADS, keepAlive, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>(100, new Comparator<Runnable>() {
            public int compare(Runnable o1, Runnable o2) {
                if (o1 == o2) {
                    return 0;
                }
                long l1 = ((LinkCrawlerRunnable) o1).getAverageRuntime();
                long l2 = ((LinkCrawlerRunnable) o2).getAverageRuntime();
                return (l1 < l2) ? -1 : ((l1 == l2) ? 0 : 1);
            }
        }), new ThreadFactory() {
            public Thread newThread(Runnable r) {
                /*
                 * our thread factory so we have logger,browser settings available
                 */
                return new LinkCrawlerThread(r);
            }
        }, new ThreadPoolExecutor.AbortPolicy());
        threadPool.allowCoreThreadTimeOut(true);
    }
    private final static LinkCrawlerEventSender EVENTSENDER = new LinkCrawlerEventSender();

    public static LinkCrawlerEventSender getGlobalEventSender() {
        return EVENTSENDER;
    }

    public static LinkCrawler newInstance() {
        return newInstance(null, null);
    }

    public static LinkCrawler newInstance(final Boolean connectParent, final Boolean avoidDuplicates) {
        final LinkCrawler lc;
        if (Thread.currentThread() instanceof LinkCrawlerThread) {
            final LinkCrawlerThread thread = (LinkCrawlerThread) (Thread.currentThread());
            final Object owner = thread.getCurrentOwner();
            final CrawledLink source;
            if (owner instanceof PluginForDecrypt) {
                source = ((PluginForDecrypt) owner).getCurrentLink();
            } else if (owner instanceof PluginsC) {
                source = ((PluginsC) owner).getCurrentLink();
            } else {
                source = null;
            }
            final LinkCrawler parent = thread.getCurrentLinkCrawler();
            lc = new LinkCrawler(connectParent == null ? false : connectParent.booleanValue(), avoidDuplicates == null ? false : avoidDuplicates.booleanValue()) {
                @Override
                protected void attachLinkCrawler(final LinkCrawler linkCrawler) {
                    if (linkCrawler != null && linkCrawler != this) {
                        if (parent != null) {
                            parent.attachLinkCrawler(linkCrawler);
                        }
                        synchronized (children) {
                            children.put(linkCrawler, Boolean.TRUE);
                        }
                    }
                }

                @Override
                protected CrawledLink crawledLinkFactorybyURL(final CharSequence url) {
                    final CrawledLink ret;
                    if (parent != null) {
                        ret = parent.crawledLinkFactorybyURL(url);
                    } else {
                        ret = new CrawledLink(url);
                    }
                    if (source != null) {
                        ret.setSourceLink(source);
                    }
                    return ret;
                }

                @Override
                protected boolean distributeCrawledLink(CrawledLink crawledLink) {
                    return crawledLink != null && crawledLink.getSourceUrls() == null;
                }

                @Override
                protected void postprocessFinalCrawledLink(CrawledLink link) {
                }
            };
            parent.attachLinkCrawler(lc);
        } else {
            lc = new LinkCrawler(connectParent == null ? true : connectParent.booleanValue(), avoidDuplicates == null ? true : avoidDuplicates.booleanValue());
        }
        return lc;
    }

    protected PluginClassLoaderChild getPluginClassLoaderChild() {
        return classLoader;
    }

    public boolean addLinkCrawlerRule(LinkCrawlerRule rule) {
        if (rule != null) {
            boolean refresh = false;
            try {
                synchronized (LINKCRAWLERRULESLOCK) {
                    List<LinkCrawlerRuleStorable> rules = CONFIG.getLinkCrawlerRules();
                    if (rules == null) {
                        rules = new ArrayList<LinkCrawlerRuleStorable>();
                    }
                    for (final LinkCrawlerRuleStorable existingRule : rules) {
                        if (existingRule.getId() == rule.getId() || (existingRule.getRule() == rule.getRule() && StringUtils.equals(existingRule.getPattern(), rule.getPattern()))) {
                            return false;
                        }
                    }
                    rules.add(new LinkCrawlerRuleStorable(rule));
                    CONFIG.setLinkCrawlerRules(rules);
                    refresh = true;
                    return true;
                }
            } finally {
                if (refresh) {
                    synchronized (linkCrawlerRules) {
                        linkCrawlerRules.set(null);
                    }
                }
            }
        }
        return false;
    }

    private static final Object LINKCRAWLERRULESLOCK = new Object();

    protected void setLinkCrawlerRuleCookies(final long ruleID, final List<String[]> setCookies) {
        boolean refresh = false;
        try {
            synchronized (LINKCRAWLERRULESLOCK) {
                final List<LinkCrawlerRuleStorable> rules = CONFIG.getLinkCrawlerRules();
                if (rules != null) {
                    for (final LinkCrawlerRuleStorable rule : rules) {
                        if (rule.getId() == ruleID) {
                            rule.setCookies(setCookies);
                            CONFIG.setLinkCrawlerRules(new ArrayList<LinkCrawlerRuleStorable>(rules));
                            refresh = true;
                            return;
                        }
                    }
                }
            }
        } finally {
            if (refresh) {
                synchronized (linkCrawlerRules) {
                    linkCrawlerRules.set(null);
                }
            }
        }
    }

    protected List<String[]> getLinkCrawlerRuleCookies(final long ruleID) {
        synchronized (LINKCRAWLERRULESLOCK) {
            final List<LinkCrawlerRuleStorable> rules = CONFIG.getLinkCrawlerRules();
            if (rules == null) {
                return null;
            } else {
                for (final LinkCrawlerRuleStorable rule : rules) {
                    if (rule.getId() == ruleID) {
                        if (rule.getCookies() != null) {
                            return new ArrayList<String[]>(rule.getCookies());
                        } else {
                            return null;
                        }
                    }
                }
                return null;
            }
        }
    }

    protected List<LinkCrawlerRule> listLinkCrawlerRules() {
        final ArrayList<LinkCrawlerRule> ret = new ArrayList<LinkCrawlerRule>();
        if (CONFIG.isLinkCrawlerRulesEnabled()) {
            synchronized (LINKCRAWLERRULESLOCK) {
                final List<LinkCrawlerRuleStorable> rules = CONFIG.getLinkCrawlerRules();
                if (rules != null) {
                    for (final LinkCrawlerRuleStorable rule : rules) {
                        try {
                            if (rule.isEnabled()) {
                                ret.add(rule._getLinkCrawlerRule());
                            }
                        } catch (final Throwable e) {
                            LogController.CL().log(e);
                        }
                    }
                }
            }
        }
        return ret;
    }

    protected void attachLinkCrawler(final LinkCrawler linkCrawler) {
        if (linkCrawler != null && linkCrawler != this) {
            final LinkCrawler parent = getParent();
            if (parent != null) {
                parent.attachLinkCrawler(linkCrawler);
            }
            synchronized (children) {
                children.put(linkCrawler, Boolean.TRUE);
            }
        }
    }

    protected final AtomicReference<LazyHostPlugin> lazyDirect = new AtomicReference<LazyHostPlugin>();

    protected LazyHostPlugin getDirectHTTPPlugin() {
        if (parentCrawler != null) {
            return parentCrawler.getDirectHTTPPlugin();
        } else {
            LazyHostPlugin ret = lazyDirect.get();
            if (ret == null) {
                ret = HostPluginController.getInstance().get(DIRECT_HTTP);
                lazyDirect.set(ret);
            }
            return ret;
        }
    }

    protected final AtomicReference<LazyHostPlugin> lazyHttp = new AtomicReference<LazyHostPlugin>();

    protected LazyHostPlugin getGenericHttpPlugin() {
        if (parentCrawler != null) {
            return parentCrawler.getGenericHttpPlugin();
        } else {
            LazyHostPlugin ret = lazyHttp.get();
            if (ret == null) {
                ret = HostPluginController.getInstance().get(HTTP_LINKS);
                lazyHttp.set(ret);
            }
            return ret;
        }
    }

    protected final AtomicReference<LazyHostPlugin> lazyFtp = new AtomicReference<LazyHostPlugin>();

    protected LazyHostPlugin getGenericFtpPlugin() {
        if (parentCrawler != null) {
            return parentCrawler.getGenericFtpPlugin();
        } else {
            LazyHostPlugin ret = lazyFtp.get();
            if (ret == null) {
                ret = HostPluginController.getInstance().get("ftp");
                lazyFtp.set(ret);
            }
            return ret;
        }
    }

    public LinkCrawler(final boolean connectParentCrawler, final boolean avoidDuplicates) {
        setFilter(defaultFilterFactory());
        final LinkCrawlerThread thread = getCurrentLinkCrawlerThread();
        if (connectParentCrawler && thread != null) {
            /* forward crawlerGeneration from parent to this child */
            this.parentCrawler = thread.getCurrentLinkCrawler();
            this.parentCrawler.attachLinkCrawler(this);
            this.classLoader = parentCrawler.getPluginClassLoaderChild();
            this.directHTTPPermission = parentCrawler.directHTTPPermission;
            this.defaultDownloadFolder = parentCrawler.defaultDownloadFolder;
            this.duplicateFinderContainer = parentCrawler.duplicateFinderContainer;
            this.duplicateFinderCrawler = parentCrawler.duplicateFinderCrawler;
            this.duplicateFinderFinal = parentCrawler.duplicateFinderFinal;
            this.duplicateFinderDeep = parentCrawler.duplicateFinderDeep;
            this.loopPreventionEmbedded = parentCrawler.loopPreventionEmbedded;
            setHandler(parentCrawler.getHandler());
            setDeepInspector(parentCrawler.getDeepInspector());
        } else {
            duplicateFinderContainer = new HashMap<String, Object>();
            duplicateFinderCrawler = new HashMap<String, Set<Object>>();
            duplicateFinderFinal = new HashMap<String, CrawledLink>();
            duplicateFinderDeep = new HashMap<String, Object>();
            loopPreventionEmbedded = new HashMap<CrawledLink, Object>();
            setHandler(defaulHandlerFactory());
            setDeepInspector(defaultDeepInspector());
            defaultDownloadFolder = JsonConfig.create(GeneralSettings.class).getDefaultDownloadFolder();
            parentCrawler = null;
            classLoader = PluginClassLoader.getInstance().getChild();
        }
        this.created = System.currentTimeMillis();
        this.doDuplicateFinderFinalCheck = avoidDuplicates;
    }

    public long getCreated() {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.getCreated();
        }
        return created;
    }

    protected CrawledLink crawledLinkFactorybyURL(final CharSequence url) {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.crawledLinkFactorybyURL(url);
        } else {
            return new CrawledLink(url);
        }
    }

    protected CrawledLink crawledLinkFactorybyDownloadLink(final DownloadLink link) {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.crawledLinkFactorybyDownloadLink(link);
        } else {
            return new CrawledLink(link);
        }
    }

    protected CrawledLink crawledLinkFactorybyCryptedLink(final CryptedLink link) {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.crawledLinkFactorybyCryptedLink(link);
        } else {
            return new CrawledLink(link);
        }
    }

    protected CrawledLink crawledLinkFactory(final Object link) {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.crawledLinkFactory(link);
        } else {
            if (link instanceof DownloadLink) {
                return crawledLinkFactorybyDownloadLink((DownloadLink) link);
            } else if (link instanceof CryptedLink) {
                return crawledLinkFactorybyCryptedLink((CryptedLink) link);
            } else if (link instanceof CharSequence) {
                return crawledLinkFactorybyURL((CharSequence) link);
            } else {
                throw new IllegalArgumentException("Unsupported:" + link);
            }
        }
    }

    public void crawl(String text) {
        crawl(text, null, false);
    }

    private volatile Set<String> crawlerPluginBlacklist = new HashSet<String>();
    private volatile Set<String> hostPluginBlacklist    = new HashSet<String>();

    public void setCrawlerPluginBlacklist(String[] list) {
        HashSet<String> lcrawlerPluginBlacklist = new HashSet<String>();
        if (list != null) {
            for (String s : list) {
                lcrawlerPluginBlacklist.add(s);
            }
        }
        this.crawlerPluginBlacklist = lcrawlerPluginBlacklist;
    }

    public boolean isBlacklisted(LazyCrawlerPlugin plugin) {
        final LinkCrawler parent = getParent();
        if (parent != null && parent.isBlacklisted(plugin)) {
            return true;
        }
        return crawlerPluginBlacklist.contains(plugin.getDisplayName());
    }

    public boolean isBlacklisted(LazyHostPlugin plugin) {
        final LinkCrawler parent = getParent();
        if (parent != null && parent.isBlacklisted(plugin)) {
            return true;
        }
        return hostPluginBlacklist.contains(plugin.getDisplayName());
    }

    public void setHostPluginBlacklist(String[] list) {
        final HashSet<String> lhostPluginBlacklist = new HashSet<String>();
        if (list != null) {
            for (String s : list) {
                lhostPluginBlacklist.add(s);
            }
        }
        this.hostPluginBlacklist = lhostPluginBlacklist;
    }

    public void crawl(final String text, final String url, final boolean allowDeep) {
        if (!StringUtils.isEmpty(text)) {
            final LinkCrawlerGeneration generation = getValidLinkCrawlerGeneration();
            if (checkStartNotify(generation)) {
                try {
                    if (insideCrawlerPlugin()) {
                        final List<CrawledLink> links = find(generation, text, url, allowDeep, true);
                        crawl(generation, links);
                    } else {
                        if (checkStartNotify(generation)) {
                            threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                                @Override
                                public long getAverageRuntime() {
                                    final Long ret = getDefaultAverageRuntime();
                                    if (ret != null) {
                                        return ret;
                                    } else {
                                        return super.getAverageRuntime();
                                    }
                                }

                                @Override
                                void crawling() {
                                    final java.util.List<CrawledLink> links = find(generation, text, url, allowDeep, true);
                                    crawl(generation, links);
                                }
                            });
                        }
                    }
                } finally {
                    checkFinishNotify();
                }
            }
        }
    }

    public List<CrawledLink> find(final LinkCrawlerGeneration generation, String text, String baseURL, final boolean allowDeep, final boolean allowInstantCrawl) {
        final CrawledLink baseLink;
        if (StringUtils.isNotEmpty(baseURL)) {
            baseLink = crawledLinkFactorybyURL(baseURL);
        } else {
            baseLink = null;
        }
        final HtmlParserResultSet resultSet;
        if (allowInstantCrawl && getCurrentLinkCrawlerThread() != null && generation != null) {
            resultSet = new HtmlParserResultSet() {
                private final HashSet<HtmlParserCharSequence> fastResults = new HashSet<HtmlParserCharSequence>();

                @Override
                public boolean add(HtmlParserCharSequence e) {
                    final boolean ret = super.add(e);
                    if (ret && (!e.contains("...") && ((getBaseURL() != null && !e.equals(getBaseURL())) || isSkipBaseURL() == false))) {
                        fastResults.add(e);
                        final CrawledLink crawledLink;
                        if (true || e.getRetainedLength() > 10) {
                            crawledLink = crawledLinkFactorybyURL(e.toURL());
                        } else {
                            crawledLink = crawledLinkFactorybyURL(e.toCharSequenceURL());
                        }
                        crawledLink.setCrawlDeep(allowDeep);
                        if (crawledLink.getSourceLink() == null) {
                            crawledLink.setSourceLink(baseLink);
                        }
                        final ArrayList<CrawledLink> crawledLinks = new ArrayList<CrawledLink>(1);
                        crawledLinks.add(crawledLink);
                        crawl(generation, crawledLinks);
                    }
                    return ret;
                };

                private LogSource logger = null;

                @Override
                public LogInterface getLogger() {
                    if (logger == null) {
                        logger = LogController.getInstance().getClassLogger(LinkCrawler.class);
                    }
                    return logger;
                }

                @Override
                protected Collection<String> exportResults() {
                    final ArrayList<String> ret = new ArrayList<String>();
                    outerLoop: for (final HtmlParserCharSequence result : this.getResults()) {
                        if (!fastResults.contains(result)) {
                            final int index = result.indexOf("...");
                            if (index > 0) {
                                final HtmlParserCharSequence check = result.subSequence(0, index);
                                for (final HtmlParserCharSequence fastResult : fastResults) {
                                    if (fastResult.startsWith(check) && result != fastResult && !fastResult.contains("...")) {
                                        continue outerLoop;
                                    }
                                }
                            }
                            ret.add(result.toURL());
                        }
                    }
                    return ret;
                }
            };
        } else {
            resultSet = new HtmlParserResultSet() {
                private LogSource logger = null;

                @Override
                public LogInterface getLogger() {
                    if (logger == null) {
                        logger = LogController.getInstance().getClassLogger(LinkCrawler.class);
                    }
                    return logger;
                }
            };
        }
        final String[] possibleLinks = HTMLParser.getHttpLinks(preprocessFind(text, baseURL, allowDeep), baseURL, resultSet);
        if (possibleLinks != null && possibleLinks.length > 0) {
            final List<CrawledLink> possibleCryptedLinks = new ArrayList<CrawledLink>(possibleLinks.length);
            for (final String possibleLink : possibleLinks) {
                final CrawledLink crawledLink = crawledLinkFactorybyURL(possibleLink);
                crawledLink.setCrawlDeep(allowDeep);
                if (crawledLink.getSourceLink() == null) {
                    crawledLink.setSourceLink(baseLink);
                }
                possibleCryptedLinks.add(crawledLink);
            }
            return possibleCryptedLinks;
        }
        return null;
    }

    public String preprocessFind(String text, String url, final boolean allowDeep) {
        if (text != null) {
            text = text.replaceAll("/\\s*Sharecode\\[\\?\\]:\\s*/", "/");
            text = text.replaceAll("\\s*Sharecode\\[\\?\\]:\\s*", "");
            text = text.replaceAll("/?\\s*Sharecode:\\s*/?", "/");
        }
        return text;
    }

    public void crawl(final List<CrawledLink> possibleCryptedLinks) {
        crawl(getValidLinkCrawlerGeneration(), possibleCryptedLinks);
    }

    protected void crawl(final LinkCrawlerGeneration generation, final List<CrawledLink> possibleCryptedLinks) {
        if (possibleCryptedLinks != null && possibleCryptedLinks.size() > 0) {
            if (checkStartNotify(generation)) {
                try {
                    if (insideCrawlerPlugin()) {
                        /*
                         * direct decrypt this link because we are already inside a LinkCrawlerThread and this avoids deadlocks on plugin
                         * waiting for linkcrawler results
                         */
                        distribute(generation, possibleCryptedLinks);
                    } else {
                        /*
                         * enqueue this cryptedLink for decrypting
                         */
                        if (checkStartNotify(generation)) {
                            threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                                @Override
                                public long getAverageRuntime() {
                                    final Long ret = getDefaultAverageRuntime();
                                    if (ret != null) {
                                        return ret;
                                    } else {
                                        return super.getAverageRuntime();
                                    }
                                }

                                @Override
                                void crawling() {
                                    distribute(generation, possibleCryptedLinks);
                                }
                            });
                        }
                    }
                } finally {
                    checkFinishNotify();
                }
            }
        }
    }

    public static boolean insideCrawlerPlugin() {
        if (Thread.currentThread() instanceof LinkCrawlerThread) {
            final Object owner = ((LinkCrawlerThread) Thread.currentThread()).getCurrentOwner();
            if (owner != null && owner instanceof PluginForDecrypt) {
                return true;
            }
        }
        return false;
    }

    public static boolean insideHosterPlugin() {
        if (Thread.currentThread() instanceof LinkCrawlerThread) {
            final Object owner = ((LinkCrawlerThread) Thread.currentThread()).getCurrentOwner();
            if (owner != null && owner instanceof PluginForHost) {
                return true;
            }
        }
        return false;
    }

    /*
     * check if all known crawlers are done and notify all waiting listener + cleanup DuplicateFinder
     */
    protected void checkFinishNotify() {
        /* this LinkCrawler instance stopped, notify static counter */
        final boolean finished;
        final boolean stopped;
        synchronized (CRAWLER) {
            final boolean crawling = crawler.get() > 0 && crawler.decrementAndGet() > 0;
            if (!crawling && runningState.compareAndSet(true, false)) {
                stopped = true;
                finished = CRAWLER.get() > 0 && CRAWLER.decrementAndGet() == 0;
            } else {
                return;
            }
        }
        if (stopped) {
            synchronized (WAIT) {
                WAIT.notifyAll();
            }
            if (getParent() == null) {
                cleanup();
            }
            EVENTSENDER.fireEvent(new LinkCrawlerEvent(this, LinkCrawlerEvent.Type.STOPPED));
            crawlerStopped();
        }
        if (finished) {
            synchronized (WAIT) {
                WAIT.notifyAll();
            }
            cleanup();
            EVENTSENDER.fireEvent(new LinkCrawlerEvent(this, LinkCrawlerEvent.Type.FINISHED));
            crawlerFinished();
        }
    }

    protected void cleanup() {
        /*
         * all tasks are done , we can now cleanup our duplicateFinder
         */
        synchronized (duplicateFinderContainer) {
            duplicateFinderContainer.clear();
        }
        synchronized (duplicateFinderCrawler) {
            duplicateFinderCrawler.clear();
        }
        synchronized (duplicateFinderFinal) {
            duplicateFinderFinal.clear();
        }
        synchronized (duplicateFinderDeep) {
            duplicateFinderDeep.clear();
        }
        synchronized (loopPreventionEmbedded) {
            loopPreventionEmbedded.clear();
        }
    }

    protected void crawlerStopped() {
    }

    protected void crawlerStarted() {
    }

    protected void crawlerFinished() {
    }

    private boolean checkStartNotify(final LinkCrawlerGeneration generation) {
        if (generation != null && generation.isValid()) {
            final boolean event;
            synchronized (CRAWLER) {
                if (crawler.getAndIncrement() == 0) {
                    event = runningState.compareAndSet(false, true);
                    if (event) {
                        CRAWLER.incrementAndGet();
                    }
                } else {
                    event = false;
                }
            }
            if (event) {
                EVENTSENDER.fireEvent(new LinkCrawlerEvent(this, LinkCrawlerEvent.Type.STARTED));
                crawlerStarted();
            }
            return true;
        }
        return false;
    }

    protected URLConnectionAdapter openCrawlDeeperConnection(Browser br, CrawledLink source) throws IOException {
        return openCrawlDeeperConnection(br, source, 0);
    }

    protected URLConnectionAdapter openCrawlDeeperConnection(Browser br, CrawledLink source, int round) throws IOException {
        final HashSet<String> loopAvoid = new HashSet<String>();
        if (round == 0) {
            final CrawledLink sourceLink = source.getSourceLink();
            if (sourceLink != null && StringUtils.startsWithCaseInsensitive(sourceLink.getURL(), "http")) {
                br.setCurrentURL(sourceLink.getURL());
            }
        }
        Request request = new GetRequest(source.getURL());
        loopAvoid.add(request.getUrl());
        URLConnectionAdapter connection = null;
        for (int i = 0; i < 10; i++) {
            final List<AuthenticationFactory> authenticationFactories = new ArrayList<AuthenticationFactory>();
            if (request.getURL().getUserInfo() != null) {
                authenticationFactories.add(new URLUserInfoAuthentication());
            }
            authenticationFactories.addAll(AuthenticationController.getInstance().getSortedAuthenticationFactories(request.getURL(), null));
            authenticationFactories.add(new CallbackAuthenticationFactory() {
                protected Authentication remember = null;

                @Override
                protected Authentication askAuthentication(Browser browser, Request request, final String realm) {
                    final LoginDialog loginDialog = new LoginDialog(UIOManager.LOGIC_COUNTDOWN, _GUI.T.AskForPasswordDialog_AskForPasswordDialog_title_(), _JDT.T.Plugin_requestLogins_message(), new AbstractIcon(IconKey.ICON_PASSWORD, 32));
                    loginDialog.setTimeout(60 * 1000);
                    final LoginDialogInterface handle = UIOManager.I().show(LoginDialogInterface.class, loginDialog);
                    if (handle.getCloseReason() == CloseReason.OK) {
                        final Authentication ret = new DefaultAuthenticanFactory(request.getURL().getHost(), realm, handle.getUsername(), handle.getPassword()).buildAuthentication(browser, request);
                        addAuthentication(ret);
                        if (handle.isRememberSelected()) {
                            remember = ret;
                        }
                        return ret;
                    } else {
                        return null;
                    }
                }

                @Override
                public boolean retry(Authentication authentication, Browser browser, Request request) {
                    if (containsAuthentication(authentication) && remember == authentication && request.getAuthentication() == authentication && !requiresAuthentication(request)) {
                        final AuthenticationInfo auth = new AuthenticationInfo();
                        auth.setRealm(authentication.getRealm());
                        auth.setUsername(authentication.getUsername());
                        auth.setPassword(authentication.getPassword());
                        auth.setHostmask(authentication.getHost());
                        auth.setType(Type.HTTP);
                        AuthenticationController.getInstance().add(auth);
                    }
                    return super.retry(authentication, browser, request);
                }
            });
            authLoop: for (AuthenticationFactory authenticationFactory : authenticationFactories) {
                if (connection != null) {
                    connection.setAllowedResponseCodes(new int[] { connection.getResponseCode() });
                    try {
                        br.followConnection();
                    } catch (IOException e) {
                        if (br.getLogger() != null) {
                            br.getLogger().log(e);
                        }
                    }
                }
                br.setCustomAuthenticationFactory(authenticationFactory);
                connection = br.openRequestConnection(request);
                if (connection.getResponseCode() == 401 || connection.getResponseCode() == 403) {
                    if (connection.getHeaderField(HTTPConstants.HEADER_RESPONSE_WWW_AUTHENTICATE) == null) {
                        return openCrawlDeeperConnection(source, br, connection, round);
                    }
                    continue authLoop;
                } else if (connection.isOK()) {
                    break authLoop;
                } else {
                    return openCrawlDeeperConnection(source, br, connection, round);
                }
            }
            final String location = request.getLocation();
            if (location != null) {
                try {
                    br.followConnection();
                } catch (IOException e) {
                    if (br.getLogger() != null) {
                        br.getLogger().log(e);
                    }
                }
                if (loopAvoid.add(location) == false) {
                    return openCrawlDeeperConnection(source, br, connection, round);
                }
                request = br.createRedirectFollowingRequest(request);
            } else {
                return openCrawlDeeperConnection(source, br, connection, round);
            }
        }
        return openCrawlDeeperConnection(source, br, connection, round);
    }

    protected URLConnectionAdapter openCrawlDeeperConnection(CrawledLink source, Browser br, URLConnectionAdapter urlConnection, int round) throws IOException {
        if (round <= 2 && urlConnection != null) {
            if (round < 2 && (urlConnection.isOK() || urlConnection.getResponseCode() == 404) && br != null && !br.getCookies(br.getBaseURL()).isEmpty()) {
                final Cookies cookies = br.getCookies(br.getBaseURL());
                for (final Cookie cookie : cookies.getCookies()) {
                    if (StringUtils.contains(cookie.getKey(), "incap_ses")) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            urlConnection.disconnect();
                            throw new IOException(e);
                        }
                        break;
                    }
                }
                if (round < 1) {
                    br.setCurrentURL(source.getURL());
                } else {
                    br.setCurrentURL(URLHelper.parseLocation(new URL(source.getURL()), "/"));
                }
                urlConnection.disconnect();
                return openCrawlDeeperConnection(br, source, round + 1);
            }
            final LinkCollectingJob job = source.getSourceJob();
            if (job != null && job.getCustomSourceUrl() != null) {
                br.setCurrentURL(job.getCustomSourceUrl());
                urlConnection.disconnect();
                return openCrawlDeeperConnection(br, source, round + 1);
            }
        }
        return urlConnection;
    }

    protected boolean isCrawledLinkDuplicated(Map<String, Object> map, CrawledLink link) {
        final String url = link.getURL();
        final String urlDecodedURL = Encoding.urlDecode(url, false);
        final String value;
        if (StringUtils.equals(url, urlDecodedURL)) {
            value = url;
        } else {
            value = urlDecodedURL;
        }
        synchronized (map) {
            if (map.containsKey(value)) {
                return true;
            } else {
                map.put(value, this);
                return false;
            }
        }
    }

    public CrawledLink createDirectHTTPCrawledLink(URLConnectionAdapter con) {
        final Request request = con.getRequest();
        final DownloadLink link = new DownloadLink(null, null, "DirectHTTP", "directhttp://" + request.getUrl(), true);
        final String cookie = con.getRequestProperty("Cookie");
        if (StringUtils.isNotEmpty(cookie)) {
            link.setProperty("cookies", cookie);
        }
        final long contentLength = con.getLongContentLength();
        if (contentLength > 0) {
            link.setVerifiedFileSize(contentLength);
        }
        final DispositionHeader dispositionHeader = Plugin.parseDispositionHeader(con);
        if (dispositionHeader != null && StringUtils.isNotEmpty(dispositionHeader.getFilename())) {
            link.setFinalFileName(dispositionHeader.getFilename());
            if (dispositionHeader.getEncoding() == null) {
                try {
                    link.setFinalFileName(URLEncode.decodeURIComponent(dispositionHeader.getFilename(), "UTF-8", true));
                } catch (final IllegalArgumentException ignore) {
                } catch (final UnsupportedEncodingException ignore) {
                }
            }
        } else {
            final String urlFileName = Plugin.getFileNameFromURL(request.getURL());
            if (StringUtils.isNotEmpty(urlFileName)) {
                link.setName(urlFileName);
            }
        }
        link.setAvailable(true);
        final String requestRef = request.getHeaders().getValue(HTTPConstants.HEADER_REQUEST_REFERER);
        link.setProperty("refURL", requestRef);
        if (request instanceof PostRequest) {
            final String postString = ((PostRequest) request).getPostDataString();
            if (postString != null) {
                link.setProperty("post", postString);
            }
            return crawledLinkFactorybyDownloadLink(link);
        } else {
            return crawledLinkFactorybyDownloadLink(link);
        }
    }

    protected static interface DeeperOrMatchingRuleModifier extends CrawledLinkModifier {
        public CrawledLinkModifier getSourceCrawledLinkModifier();
    }

    protected void crawlDeeperOrMatchingRule(final LinkCrawlerGeneration generation, final CrawledLink source) {
        final CrawledLinkModifier sourceLinkModifier;
        if (source.getCustomCrawledLinkModifier() instanceof DeeperOrMatchingRuleModifier) {
            CrawledLinkModifier modifier = source.getCustomCrawledLinkModifier();
            while (modifier instanceof DeeperOrMatchingRuleModifier) {
                modifier = ((DeeperOrMatchingRuleModifier) modifier).getSourceCrawledLinkModifier();
            }
            sourceLinkModifier = modifier;
        } else {
            sourceLinkModifier = source.getCustomCrawledLinkModifier();
        }
        source.setCustomCrawledLinkModifier(null);
        source.setBrokenCrawlerHandler(null);
        if (source == null || source.getURL() == null) {
            return;
        } else if (isCrawledLinkDuplicated(duplicateFinderDeep, source)) {
            onCrawledLinkDuplicate(source, DUPLICATE.DEEP);
            return;
        } else if (this.isCrawledLinkFiltered(source)) {
            return;
        }
        final LinkCrawlerRule matchingRule = source.getMatchingRule();
        final List<CrawledLinkModifier> additionalModifier = new ArrayList<CrawledLinkModifier>();
        final CrawledLinkModifier lm = new DeeperOrMatchingRuleModifier() {
            public CrawledLinkModifier getSourceCrawledLinkModifier() {
                return sourceLinkModifier;
            }

            @Override
            public List<CrawledLinkModifier> getSubCrawledLinkModifier(CrawledLink link) {
                if (sourceLinkModifier == null && additionalModifier.size() > 0) {
                    return Collections.unmodifiableList(additionalModifier);
                } else {
                    final List<CrawledLinkModifier> ret = new ArrayList<CrawledLinkModifier>();
                    ret.add(sourceLinkModifier);
                    ret.addAll(additionalModifier);
                    return Collections.unmodifiableList(ret);
                }
            }

            public boolean modifyCrawledLink(CrawledLink link) {
                final boolean setContainerURL = link.getDownloadLink() != null && link.getDownloadLink().getContainerUrl() == null;
                boolean ret = false;
                if (sourceLinkModifier != null) {
                    if (sourceLinkModifier.modifyCrawledLink(link)) {
                        ret = true;
                    }
                }
                if (setContainerURL) {
                    link.getDownloadLink().setContainerUrl(source.getURL());
                    ret = true;
                }
                for (final CrawledLinkModifier modifier : additionalModifier) {
                    if (modifier.modifyCrawledLink(link)) {
                        ret = true;
                    }
                }
                return ret;
            }
        };
        if (checkStartNotify(generation)) {
            try {
                Browser br = null;
                try {
                    processedLinksCounter.incrementAndGet();
                    if (StringUtils.startsWithCaseInsensitive(source.getURL(), "file:/")) {
                        // file:/ -> not authority -> all fine
                        // file://xy/ -> xy authority -> java.lang.IllegalArgumentException: URI has an authority component
                        // file:/// -> empty authority -> all fine
                        final String currentURI = source.getURL().replaceFirst("file:///?", "file:///");
                        final File file = new File(new URI(currentURI));
                        if (file.exists() && file.isFile()) {
                            final int limit = CONFIG.getDeepDecryptFileSizeLimit();
                            final int readLimit = limit == -1 ? -1 : Math.max(1 * 1024 * 1024, limit);
                            final String fileContent = new String(IO.readFile(file, readLimit), "UTF-8");
                            final List<CrawledLink> fileContentLinks = find(generation, fileContent, null, false, false);
                            if (fileContentLinks != null) {
                                final String[] sourceURLs = getAndClearSourceURLs(source);
                                final boolean singleDest = fileContentLinks.size() == 1;
                                for (final CrawledLink fileContentLink : fileContentLinks) {
                                    forwardCrawledLinkInfos(source, fileContentLink, lm, sourceURLs, singleDest);
                                }
                                crawl(generation, fileContentLinks);
                            }
                        }
                    } else {
                        br = new Browser();
                        br.addAllowedResponseCodes(500);
                        if (LogController.getInstance().isDebugMode()) {
                            br.setLogger(LogController.CL());
                        }
                        final List<String[]> setCookies = matchingRule != null ? getLinkCrawlerRuleCookies(matchingRule.getId()) : null;
                        if (setCookies != null) {
                            for (final String cookie[] : setCookies) {
                                if (cookie != null) {
                                    if (cookie.length == 1) {
                                        br.setCookie(source.getURL(), cookie[0], null);
                                    } else if (cookie.length > 1) {
                                        br.setCookie(source.getURL(), cookie[0], cookie[1]);
                                    }
                                }
                            }
                        }
                        final URLConnectionAdapter connection = openCrawlDeeperConnection(br, source);
                        if (setCookies != null && matchingRule.isUpdateCookies()) {
                            final Cookies cookies = br.getCookies(source.getURL());
                            final List<String[]> currentCookies = new ArrayList<String[]>();
                            for (final Cookie cookie : cookies.getCookies()) {
                                if (!cookie.isExpired()) {
                                    currentCookies.add(new String[] { cookie.getKey(), cookie.getValue() });
                                }
                            }
                            setLinkCrawlerRuleCookies(matchingRule.getId(), currentCookies);
                        }
                        final CrawledLink deeperSource;
                        final String[] sourceURLs;
                        if (StringUtils.equals(connection.getRequest().getUrl(), source.getURL())) {
                            deeperSource = source;
                            sourceURLs = getAndClearSourceURLs(source);
                        } else {
                            deeperSource = crawledLinkFactorybyURL(connection.getRequest().getUrl());
                            forwardCrawledLinkInfos(source, deeperSource, lm, getAndClearSourceURLs(source), true);
                            sourceURLs = getAndClearSourceURLs(deeperSource);
                        }
                        if (matchingRule != null && LinkCrawlerRule.RULE.FOLLOWREDIRECT.equals(matchingRule.getRule())) {
                            try {
                                br.getHttpConnection().disconnect();
                            } catch (Throwable e) {
                            }
                            final ArrayList<CrawledLink> followRedirectLinks = new ArrayList<CrawledLink>();
                            followRedirectLinks.add(deeperSource);
                            crawl(generation, followRedirectLinks);
                        } else {
                            final LinkCrawlerDeepInspector lDeepInspector = getDeepInspector();
                            final List<CrawledLink> inspectedLinks = lDeepInspector.deepInspect(this, generation, br, connection, deeperSource);
                            /*
                             * downloadable content, we use directhttp and distribute the url
                             */
                            if (inspectedLinks != null) {
                                if (inspectedLinks.size() >= 0) {
                                    final boolean singleDest = inspectedLinks.size() == 1;
                                    for (final CrawledLink possibleCryptedLink : inspectedLinks) {
                                        forwardCrawledLinkInfos(deeperSource, possibleCryptedLink, lm, sourceURLs, singleDest);
                                    }
                                    crawl(generation, inspectedLinks);
                                }
                            } else {
                                final String finalPackageName;
                                if (matchingRule != null && matchingRule._getPackageNamePattern() != null) {
                                    final String packageName = br.getRegex(matchingRule._getPackageNamePattern()).getMatch(0);
                                    if (StringUtils.isNotEmpty(packageName)) {
                                        finalPackageName = Encoding.htmlDecode(packageName.trim());
                                    } else {
                                        finalPackageName = null;
                                    }
                                } else {
                                    finalPackageName = null;
                                }
                                /* try to load the webpage and find links on it */
                                if (matchingRule != null && LinkCrawlerRule.RULE.SUBMITFORM.equals(matchingRule.getRule())) {
                                    final Form[] forms = br.getForms();
                                    final Pattern formPattern = matchingRule._getFormPattern();
                                    final ArrayList<CrawledLink> formLinks = new ArrayList<CrawledLink>();
                                    if (forms != null && formPattern != null) {
                                        for (final Form form : forms) {
                                            if (formPattern.matcher(form.getAction()).matches()) {
                                                final Browser clone = br.cloneBrowser();
                                                clone.setFollowRedirects(false);
                                                final URLConnectionAdapter con = clone.openFormConnection(form);
                                                final String redirect = con.getRequest().getLocation();
                                                if (redirect != null) {
                                                    clone.followConnection();
                                                    formLinks.add(crawledLinkFactorybyURL(redirect));
                                                } else if (lDeepInspector.looksLikeDownloadableContent(con)) {
                                                    con.disconnect();
                                                    final CrawledLink formLink = createDirectHTTPCrawledLink(con);
                                                    if (formLink != null) {
                                                        formLinks.add(formLink);
                                                    }
                                                } else {
                                                    clone.followConnection();
                                                }
                                            }
                                        }
                                    }
                                    if (formLinks != null && formLinks.size() > 0) {
                                        final boolean singleDest = formLinks.size() == 1;
                                        for (final CrawledLink formLink : formLinks) {
                                            forwardCrawledLinkInfos(deeperSource, formLink, lm, sourceURLs, singleDest);
                                            PackageInfo dpi = formLink.getDesiredPackageInfo();
                                            if (dpi == null) {
                                                dpi = new PackageInfo();
                                            }
                                            dpi.setName(finalPackageName);
                                            formLink.setDesiredPackageInfo(dpi);
                                        }
                                        crawl(generation, formLinks);
                                    }
                                } else {
                                    // We need browser currentURL and not sourceURL, because of possible redirects will change domain and or
                                    // relative
                                    // path.
                                    final Request request = br.getRequest();
                                    final String brURL;
                                    if (request.getAuthentication() == null) {
                                        brURL = request.getUrl();
                                    } else {
                                        brURL = request.getAuthentication().getURLWithUserInfo(request.getURL());
                                    }
                                    final List<CrawledLink> possibleCryptedLinks = find(generation, brURL, null, false, false);
                                    if (possibleCryptedLinks != null) {
                                        final boolean singleDest = possibleCryptedLinks.size() == 1;
                                        for (final CrawledLink possibleCryptedLink : possibleCryptedLinks) {
                                            forwardCrawledLinkInfos(deeperSource, possibleCryptedLink, lm, sourceURLs, singleDest);
                                            PackageInfo dpi = possibleCryptedLink.getDesiredPackageInfo();
                                            if (dpi == null) {
                                                dpi = new PackageInfo();
                                            }
                                            dpi.setName(finalPackageName);
                                            possibleCryptedLink.setDesiredPackageInfo(dpi);
                                        }
                                        final CrawledLink deepLink;
                                        if (possibleCryptedLinks.size() == 1) {
                                            deepLink = possibleCryptedLinks.get(0);
                                        } else if (source.isCrawlDeep() || (matchingRule != null && LinkCrawlerRule.RULE.DEEPDECRYPT.equals(matchingRule.getRule()))) {
                                            CrawledLink deep = null;
                                            for (final CrawledLink possibleCryptedLink : possibleCryptedLinks) {
                                                if (StringUtils.equalsIgnoreCase(possibleCryptedLink.getURL(), source.getURL())) {
                                                    deep = possibleCryptedLink;
                                                    break;
                                                }
                                            }
                                            deepLink = deep;
                                        } else {
                                            deepLink = null;
                                        }
                                        if (deepLink != null) {
                                            final String finalBaseUrl = new Regex(brURL, "(https?://.*?)(\\?|$)").getMatch(0);
                                            final String crawlContent;
                                            if (matchingRule != null && matchingRule._getDeepPattern() != null) {
                                                final String[][] matches = new Regex(request.getHtmlCode(), matchingRule._getDeepPattern()).getMatches();
                                                if (matches != null) {
                                                    final HashSet<String> dups = new HashSet<String>();
                                                    final StringBuilder sb = new StringBuilder();
                                                    for (final String matcharray[] : matches) {
                                                        for (final String match : matcharray) {
                                                            if (StringUtils.isNotEmpty(match) && !brURL.equals(match) && dups.add(match)) {
                                                                if (sb.length() > 0) {
                                                                    sb.append("\r\n");
                                                                }
                                                                sb.append(match);
                                                                if (match.matches("^[^<>\"]+$")) {
                                                                    try {
                                                                        final String url = br.getURL(match).toString();
                                                                        if (dups.add(url)) {
                                                                            sb.append("\r\n").append(url);
                                                                        }
                                                                    } catch (final Throwable e) {
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    crawlContent = sb.toString();
                                                } else {
                                                    crawlContent = "";
                                                }
                                            } else {
                                                crawlContent = request.getHtmlCode();
                                            }
                                            if (matchingRule != null && matchingRule._getPasswordPattern() != null) {
                                                final String[][] matches = new Regex(request.getHtmlCode(), matchingRule._getPasswordPattern()).getMatches();
                                                if (matches != null) {
                                                    final HashSet<String> passwords = new HashSet<String>();
                                                    for (final String matcharray[] : matches) {
                                                        for (final String match : matcharray) {
                                                            if (StringUtils.isNotEmpty(match)) {
                                                                passwords.add(match);
                                                            }
                                                        }
                                                    }
                                                    if (passwords.size() > 0) {
                                                        additionalModifier.add(new CrawledLinkModifier() {
                                                            @Override
                                                            public List<CrawledLinkModifier> getSubCrawledLinkModifier(CrawledLink link) {
                                                                return null;
                                                            }

                                                            @Override
                                                            public boolean modifyCrawledLink(CrawledLink link) {
                                                                for (final String password : passwords) {
                                                                    link.getArchiveInfo().addExtractionPassword(password);
                                                                }
                                                                return true;
                                                            }
                                                        });
                                                    }
                                                }
                                            }
                                            /* first check if the url itself can be handled */
                                            deepLink.setUnknownHandler(new UnknownCrawledLinkHandler() {
                                                @Override
                                                public void unhandledCrawledLink(CrawledLink link, LinkCrawler lc) {
                                                    /* unhandled url, lets parse the content on it */
                                                    final List<CrawledLink> possibleCryptedLinks2 = lc.find(generation, crawlContent, finalBaseUrl, false, false);
                                                    if (possibleCryptedLinks2 != null && possibleCryptedLinks2.size() > 0) {
                                                        final boolean singleDest = possibleCryptedLinks2.size() == 1;
                                                        for (final CrawledLink possibleCryptedLink : possibleCryptedLinks2) {
                                                            forwardCrawledLinkInfos(deeperSource, possibleCryptedLink, lm, sourceURLs, singleDest);
                                                            PackageInfo dpi = possibleCryptedLink.getDesiredPackageInfo();
                                                            if (dpi == null) {
                                                                dpi = new PackageInfo();
                                                            }
                                                            dpi.setName(finalPackageName);
                                                            possibleCryptedLink.setDesiredPackageInfo(dpi);
                                                        }
                                                        lc.crawl(generation, possibleCryptedLinks2);
                                                    }
                                                }
                                            });
                                        }
                                        crawl(generation, possibleCryptedLinks);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    LogController.CL().log(e);
                } finally {
                    try {
                        if (br != null) {
                            br.getHttpConnection().disconnect();
                        }
                    } catch (Throwable e) {
                    }
                }
            } finally {
                checkFinishNotify();
            }
        }
    }

    protected boolean distributeCrawledLink(CrawledLink crawledLink) {
        return crawledLink != null && crawledLink.gethPlugin() == null;
    }

    protected boolean canHandle(final LazyPlugin<? extends Plugin> lazyPlugin, final String url, final CrawledLink link) {
        if (lazyPlugin.canHandle(url)) {
            try {
                Plugin plugin = lazyPlugin.getPrototype(getPluginClassLoaderChild());
                if (plugin != null && Plugin.implementsCanHandleString(plugin)) {
                    /* TODO: store implementsCanHandleString within LazyPlugin */
                    plugin = lazyPlugin.newInstance(getPluginClassLoaderChild());
                }
                if (plugin != null) {
                    /* now we run the plugin and let it find some links */
                    return plugin.canHandle(url);
                } else {
                    throw new WTFException("Plugin not available:" + lazyPlugin);
                }
            } catch (Throwable e) {
                LogController.CL().log(e);
            }
        }
        return false;
    }

    protected DISTRIBUTE distributePluginForHost(final LazyHostPlugin pluginForHost, final LinkCrawlerGeneration generation, final String url, final CrawledLink link) {
        try {
            if (canHandle(pluginForHost, url, link)) {
                if (isBlacklisted(pluginForHost)) {
                    return DISTRIBUTE.BLACKLISTED;
                }
                if (insideCrawlerPlugin()) {
                    if (!generation.isValid()) {
                        /* LinkCrawler got aborted! */
                        return DISTRIBUTE.STOP;
                    }
                    processHostPlugin(generation, pluginForHost, link);
                } else {
                    if (checkStartNotify(generation)) {
                        threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                            @Override
                            public long getAverageRuntime() {
                                final Long ret = getDefaultAverageRuntime();
                                if (ret != null) {
                                    return ret;
                                } else {
                                    return pluginForHost.getAverageParseRuntime();
                                }
                            }

                            @Override
                            void crawling() {
                                processHostPlugin(generation, pluginForHost, link);
                            }
                        });
                    } else {
                        /* LinkCrawler got aborted! */
                        return DISTRIBUTE.STOP;
                    }
                }
                return DISTRIBUTE.NEXT;
            }
        } catch (final Throwable e) {
            LogController.CL().log(e);
        }
        return DISTRIBUTE.CONTINUE;
    }

    /**
     * break PluginForDecrypt loop when PluginForDecrypt and PluginForHost listen on same urls
     *
     * @param pDecrypt
     * @param link
     * @return
     */
    protected boolean breakPluginForDecryptLoop(final LazyCrawlerPlugin pDecrypt, final CrawledLink link) {
        final boolean canHandle = canHandle(pDecrypt, link.getURL(), link.getSourceLink());
        if (canHandle) {
            if (!AvailableLinkState.UNKNOWN.equals(link.getLinkState())) {
                return true;
            }
            CrawledLink source = link.getSourceLink();
            final HashSet<String> dontRetry = new HashSet<String>();
            while (source != null) {
                if (source.getCryptedLink() != null) {
                    if (StringUtils.equals(link.getURL(), source.getURL())) {
                        final LazyCrawlerPlugin lazyC = source.getCryptedLink().getLazyC();
                        dontRetry.add(lazyC.getDisplayName() + lazyC.getClassName());
                    }
                }
                source = source.getSourceLink();
            }
            return dontRetry.contains(pDecrypt.getDisplayName() + pDecrypt.getClassName());
        } else {
            return false;
        }
    }

    protected DISTRIBUTE distributePluginForDecrypt(final LazyCrawlerPlugin pDecrypt, final LinkCrawlerGeneration generation, final String url, final CrawledLink link) {
        try {
            if (canHandle(pDecrypt, url, link)) {
                if (isBlacklisted(pDecrypt)) {
                    return DISTRIBUTE.BLACKLISTED;
                }
                if (!breakPluginForDecryptLoop(pDecrypt, link)) {
                    final java.util.List<CrawledLink> allPossibleCryptedLinks = getCryptedLinks(pDecrypt, link, link.getCustomCrawledLinkModifier());
                    if (allPossibleCryptedLinks != null) {
                        if (insideCrawlerPlugin()) {
                            /*
                             * direct decrypt this link because we are already inside a LinkCrawlerThread and this avoids deadlocks on
                             * plugin waiting for linkcrawler results
                             */
                            for (final CrawledLink decryptThis : allPossibleCryptedLinks) {
                                if (!generation.isValid()) {
                                    /* LinkCrawler got aborted! */
                                    return DISTRIBUTE.STOP;
                                }
                                crawl(generation, pDecrypt, decryptThis);
                            }
                        } else {
                            /*
                             * enqueue these cryptedLinks for decrypting
                             */
                            for (final CrawledLink decryptThis : allPossibleCryptedLinks) {
                                if (checkStartNotify(generation)) {
                                    threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                                        public long getAverageRuntime() {
                                            final Long ret = getDefaultAverageRuntime();
                                            if (ret != null) {
                                                return ret;
                                            } else {
                                                return pDecrypt.getAverageCrawlRuntime();
                                            }
                                        }

                                        @Override
                                        protected LinkCrawlerLock getLinkCrawlerLock() {
                                            return LinkCrawler.this.getLinkCrawlerLock(pDecrypt, decryptThis);
                                        }

                                        @Override
                                        void crawling() {
                                            crawl(generation, pDecrypt, decryptThis);
                                        }
                                    });
                                } else {
                                    /* LinkCrawler got aborted! */
                                    return DISTRIBUTE.STOP;
                                }
                            }
                        }
                    }
                    return DISTRIBUTE.NEXT;
                } else {
                    return DISTRIBUTE.CONTINUE;
                }
            }
        } catch (final Throwable e) {
            LogController.CL().log(e);
        }
        return DISTRIBUTE.CONTINUE;
    }

    protected Boolean distributePluginC(final PluginsC pluginC, final LinkCrawlerGeneration generation, final String url, final CrawledLink link) {
        try {
            if (pluginC.canHandle(url)) {
                final CrawledLinkModifier originalModifier = link.getCustomCrawledLinkModifier();
                final CrawledLinkModifier lm;
                if (pluginC.hideLinks()) {
                    final ArrayList<CrawledLinkModifier> modifiers = new ArrayList<CrawledLinkModifier>();
                    if (originalModifier != null) {
                        modifiers.add(originalModifier);
                    }
                    modifiers.add(new CrawledLinkModifier() {
                        @Override
                        public boolean modifyCrawledLink(CrawledLink link) {
                            /* we hide the links */
                            final DownloadLink dl = link.getDownloadLink();
                            if (dl != null) {
                                dl.setUrlProtection(UrlProtection.PROTECTED_CONTAINER);
                                return true;
                            }
                            return false;
                        }

                        @Override
                        public List<CrawledLinkModifier> getSubCrawledLinkModifier(CrawledLink link) {
                            return null;
                        }
                    });
                    lm = new CrawledLinkModifier() {
                        @Override
                        public List<CrawledLinkModifier> getSubCrawledLinkModifier(CrawledLink link) {
                            if (originalModifier != null) {
                                return Collections.unmodifiableList(modifiers);
                            } else {
                                return null;
                            }
                        }

                        @Override
                        public boolean modifyCrawledLink(CrawledLink link) {
                            boolean ret = false;
                            for (CrawledLinkModifier mod : modifiers) {
                                if (mod.modifyCrawledLink(link)) {
                                    ret = true;
                                }
                            }
                            return ret;
                        }
                    };
                } else {
                    lm = originalModifier;
                }
                final java.util.List<CrawledLink> allPossibleCryptedLinks = getCrawledLinks(pluginC.getSupportedLinks(), link, lm);
                if (allPossibleCryptedLinks != null) {
                    if (insideCrawlerPlugin()) {
                        /*
                         * direct decrypt this link because we are already inside a LinkCrawlerThread and this avoids deadlocks on plugin
                         * waiting for linkcrawler results
                         */
                        for (final CrawledLink decryptThis : allPossibleCryptedLinks) {
                            if (!generation.isValid()) {
                                /* LinkCrawler got aborted! */
                                return false;
                            }
                            container(generation, pluginC, decryptThis);
                        }
                    } else {
                        /*
                         * enqueue these cryptedLinks for decrypting
                         */
                        for (final CrawledLink decryptThis : allPossibleCryptedLinks) {
                            if (checkStartNotify(generation)) {
                                threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                                    @Override
                                    public long getAverageRuntime() {
                                        final Long ret = getDefaultAverageRuntime();
                                        if (ret != null) {
                                            return ret;
                                        } else {
                                            return super.getAverageRuntime();
                                        }
                                    }

                                    @Override
                                    void crawling() {
                                        container(generation, pluginC, decryptThis);
                                    }
                                });
                            } else {
                                /* LinkCrawler got aborted! */
                                return false;
                            }
                        }
                    }
                }
                return true;
            }
        } catch (final Throwable e) {
            LogController.CL().log(e);
        }
        return null;
    }

    protected DISTRIBUTE rewrite(final LinkCrawlerGeneration generation, final String url, final CrawledLink source) {
        try {
            final LinkCrawlerRule rule = getFirstMatchingRule(source, url, LinkCrawlerRule.RULE.REWRITE);
            if (rule != null && rule.getRewriteReplaceWith() != null) {
                source.setMatchingRule(rule);
                final String newURL = url.replaceAll(rule.getPattern(), rule.getRewriteReplaceWith());
                if (!url.equals(newURL)) {
                    final CrawledLinkModifier lm = source.getCustomCrawledLinkModifier();
                    source.setCustomCrawledLinkModifier(null);
                    source.setBrokenCrawlerHandler(null);
                    final CrawledLink rewritten = crawledLinkFactorybyURL(newURL);
                    forwardCrawledLinkInfos(source, rewritten, lm, getAndClearSourceURLs(source), true);
                    if (insideCrawlerPlugin()) {
                        if (!generation.isValid()) {
                            /* LinkCrawler got aborted! */
                            return DISTRIBUTE.STOP;
                        }
                        distribute(generation, rewritten);
                        return DISTRIBUTE.NEXT;
                    } else if (checkStartNotify(generation)) {
                        threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                            @Override
                            public long getAverageRuntime() {
                                final Long ret = getDefaultAverageRuntime();
                                if (ret != null) {
                                    return ret;
                                } else {
                                    return super.getAverageRuntime();
                                }
                            }

                            @Override
                            void crawling() {
                                distribute(generation, rewritten);
                            }
                        });
                        return DISTRIBUTE.NEXT;
                    } else {
                        return DISTRIBUTE.STOP;
                    }
                }
            }
        } catch (Throwable e) {
            LogController.CL().log(e);
        }
        return DISTRIBUTE.CONTINUE;
    }

    protected Boolean distributeDeeperOrMatchingRule(final LinkCrawlerGeneration generation, final String url, final CrawledLink link) {
        try {
            LinkCrawlerRule rule = null;
            /* do not change order, it is important to check redirect first */
            if ((rule = getFirstMatchingRule(link, url, LinkCrawlerRule.RULE.SUBMITFORM, LinkCrawlerRule.RULE.FOLLOWREDIRECT, LinkCrawlerRule.RULE.DEEPDECRYPT)) != null || link.isCrawlDeep()) {
                if (rule != null) {
                    link.setMatchingRule(rule);
                }
                /* the link is allowed to crawlDeep */
                if (insideCrawlerPlugin()) {
                    if (!generation.isValid()) {
                        /* LinkCrawler got aborted! */
                        return false;
                    }
                    crawlDeeperOrMatchingRule(generation, link);
                } else {
                    if (checkStartNotify(generation)) {
                        threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                            @Override
                            public long getAverageRuntime() {
                                final Long ret = getDefaultAverageRuntime();
                                if (ret != null) {
                                    return ret;
                                } else {
                                    return super.getAverageRuntime();
                                }
                            }

                            @Override
                            void crawling() {
                                crawlDeeperOrMatchingRule(generation, link);
                            }
                        });
                    } else {
                        return false;
                    }
                }
                return true;
            }
        } catch (final Throwable e) {
            LogController.CL().log(e);
        }
        return null;
    }

    protected void distribute(final LinkCrawlerGeneration generation, CrawledLink... possibleCryptedLinks) {
        if (possibleCryptedLinks != null && possibleCryptedLinks.length > 0) {
            distribute(generation, Arrays.asList(possibleCryptedLinks));
        }
    }

    protected CrawledLink createCopyOf(CrawledLink source) {
        final CrawledLink ret;
        if (source.getDownloadLink() != null) {
            ret = new CrawledLink(source.getDownloadLink());
        } else if (source.getCryptedLink() != null) {
            ret = new CrawledLink(source.getCryptedLink());
        } else {
            ret = new CrawledLink(source.getURL());
        }
        ret.setSourceLink(source);
        if (source.hasCollectingInfo()) {
            ret.setCollectingInfo(source.getCollectingInfo());
        }
        ret.setSourceJob(source.getSourceJob());
        ret.setSourceUrls(source.getSourceUrls());
        ret.setOrigin(source.getOrigin());
        ret.setCrawlDeep(source.isCrawlDeep());
        if (source.hasArchiveInfo()) {
            ret.setArchiveInfo(source.getArchiveInfo());
        }
        ret.setCustomCrawledLinkModifier(source.getCustomCrawledLinkModifier());
        ret.setBrokenCrawlerHandler(source.getBrokenCrawlerHandler());
        ret.setUnknownHandler(source.getUnknownHandler());
        ret.setMatchingFilter(source.getMatchingFilter());
        ret.setMatchingRule(source.getMatchingRule());
        // ret.setCreated(source.getCreated());#set in handleFinalCrawledLink
        ret.setEnabled(source.isEnabled());
        ret.setForcedAutoStartEnabled(source.isForcedAutoStartEnabled());
        ret.setAutoConfirmEnabled(source.isAutoConfirmEnabled());
        ret.setAutoStartEnabled(source.isAutoStartEnabled());
        if (source.isNameSet()) {
            ret.setName(source._getName());
        }
        ret.setDesiredPackageInfo(source.getDesiredPackageInfo());
        ret.setParentNode(source.getParentNode());
        return ret;
    }

    protected void distribute(final LinkCrawlerGeneration generation, List<CrawledLink> possibleCryptedLinks) {
        if (possibleCryptedLinks == null || possibleCryptedLinks.size() == 0) {
            return;
        }
        if (checkStartNotify(generation)) {
            try {
                mainloop: for (final CrawledLink possibleCryptedLink : possibleCryptedLinks) {
                    if (!generation.isValid()) {
                        /* LinkCrawler got aborted! */
                        return;
                    }
                    mainloopretry: while (true) {
                        final UnknownCrawledLinkHandler unnknownHandler = possibleCryptedLink.getUnknownHandler();
                        possibleCryptedLink.setUnknownHandler(null);
                        if (!distributeCrawledLink(possibleCryptedLink)) {
                            // direct forward, if we already have a final link.
                            this.handleFinalCrawledLink(possibleCryptedLink);
                            continue mainloop;
                        }
                        final String url = possibleCryptedLink.getURL();
                        if (url == null) {
                            /* WTF, no URL?! let's continue */
                            continue mainloop;
                        } else {
                            final DISTRIBUTE ret = rewrite(generation, url, possibleCryptedLink);
                            switch (ret) {
                            case STOP:
                                return;
                            case CONTINUE:
                                break;
                            case BLACKLISTED:
                                continue mainloop;
                            case NEXT:
                                handleUnhandledCryptedLink(possibleCryptedLink);
                                continue mainloop;
                            default:
                                LogController.CL().log(new IllegalStateException(ret.name()));
                                break;
                            }
                        }
                        final boolean isDirect = url.startsWith("directhttp://");
                        final boolean isFtp = url.startsWith("ftp://") || url.startsWith("ftpviajd://");
                        final boolean isFile = url.startsWith("file:/");
                        final boolean isHttpJD = url.startsWith("httpviajd://") || url.startsWith("httpsviajd://");
                        if (isFile) {
                            /*
                             * first we will walk through all available container plugins
                             */
                            for (final PluginsC pCon : ContainerPluginController.getInstance().list()) {
                                final Boolean ret = distributePluginC(pCon, generation, url, possibleCryptedLink);
                                if (Boolean.FALSE.equals(ret)) {
                                    return;
                                } else if (Boolean.TRUE.equals(ret)) {
                                    continue mainloop;
                                }
                            }
                        } else if (!isDirect && !isHttpJD) {
                            {
                                /*
                                 * first we will walk through all available decrypter plugins
                                 */
                                final List<LazyCrawlerPlugin> lazyCrawlerPlugins = getSortedLazyCrawlerPlugins();
                                final ListIterator<LazyCrawlerPlugin> it = lazyCrawlerPlugins.listIterator();
                                loop: while (it.hasNext()) {
                                    final LazyCrawlerPlugin pDecrypt = it.next();
                                    final DISTRIBUTE ret = distributePluginForDecrypt(pDecrypt, generation, url, possibleCryptedLink);
                                    switch (ret) {
                                    case STOP:
                                        return;
                                    case CONTINUE:
                                        break;
                                    case BLACKLISTED:
                                        continue mainloop;
                                    case NEXT:
                                        if (it.previousIndex() > lazyCrawlerPlugins.size() / 50) {
                                            resetSortedLazyCrawlerPlugins(lazyCrawlerPlugins);
                                        }
                                        continue mainloop;
                                    default:
                                        LogController.CL().log(new IllegalStateException(ret.name()));
                                        break;
                                    }
                                }
                            }
                            {
                                /* now we will walk through all available hoster plugins */
                                final List<LazyHostPlugin> sortedLazyHostPlugins = getSortedLazyHostPlugins();
                                final ListIterator<LazyHostPlugin> it = sortedLazyHostPlugins.listIterator();
                                loop: while (it.hasNext()) {
                                    final LazyHostPlugin pHost = it.next();
                                    final DISTRIBUTE ret = distributePluginForHost(pHost, generation, url, possibleCryptedLink);
                                    switch (ret) {
                                    case STOP:
                                        return;
                                    case CONTINUE:
                                        break;
                                    case BLACKLISTED:
                                        continue mainloop;
                                    case NEXT:
                                        if (it.previousIndex() > sortedLazyHostPlugins.size() / 50) {
                                            resetSortedLazyHostPlugins(sortedLazyHostPlugins);
                                        }
                                        continue mainloop;
                                    default:
                                        LogController.CL().log(new IllegalStateException(ret.name()));
                                        break;
                                    }
                                }
                            }
                        }
                        if (isFtp) {
                            final LazyHostPlugin ftpPlugin = getGenericFtpPlugin();
                            if (ftpPlugin != null) {
                                /* now we will check for generic ftp links */
                                final DISTRIBUTE ret = distributePluginForHost(ftpPlugin, generation, url, possibleCryptedLink);
                                switch (ret) {
                                case STOP:
                                    return;
                                case CONTINUE:
                                    break;
                                case BLACKLISTED:
                                case NEXT:
                                    continue mainloop;
                                default:
                                    LogController.CL().log(new IllegalStateException(ret.name()));
                                    break;
                                }
                            }
                        } else if (!isFile) {
                            final DirectHTTPPermission directHTTPPermission = getDirectHTTPPermission();
                            final LazyHostPlugin directPlugin = getDirectHTTPPlugin();
                            if (directPlugin != null) {
                                LinkCrawlerRule rule = null;
                                if (isDirect) {
                                    rule = possibleCryptedLink.getMatchingRule();
                                    if (DirectHTTPPermission.ALWAYS.equals(directHTTPPermission) || (DirectHTTPPermission.RULES_ONLY.equals(directHTTPPermission) && LinkCrawlerRule.RULE.DIRECTHTTP.equals(rule))) {
                                        /* now we will check for directPlugin links */
                                        final DISTRIBUTE ret = distributePluginForHost(directPlugin, generation, url, possibleCryptedLink);
                                        switch (ret) {
                                        case STOP:
                                            return;
                                        case CONTINUE:
                                            break;
                                        case BLACKLISTED:
                                        case NEXT:
                                            continue mainloop;
                                        default:
                                            LogController.CL().log(new IllegalStateException(ret.name()));
                                            break;
                                        }
                                    } else {
                                        // DirectHTTPPermission.FORBIDDEN
                                        continue mainloop;
                                    }
                                } else if ((rule = getFirstMatchingRule(possibleCryptedLink, url, LinkCrawlerRule.RULE.DIRECTHTTP)) != null) {
                                    if (!DirectHTTPPermission.FORBIDDEN.equals(directHTTPPermission)) {
                                        // no need to check directHTTPPermission as it is ALWAYS or RULES_ONLY
                                        final CrawledLink copy = createCopyOf(possibleCryptedLink);
                                        final CrawledLinkModifier linkModifier = copy.getCustomCrawledLinkModifier();
                                        copy.setCustomCrawledLinkModifier(null);
                                        final CrawledLink directHTTP;
                                        if (rule != null && rule.getCookies() != null) {
                                            final DownloadLink link = new DownloadLink(null, null, null, "directhttp://" + url, true);
                                            final StringBuilder sb = new StringBuilder();
                                            for (String[] cookie : rule.getCookies()) {
                                                if (cookie.length == 1) {
                                                    sb.append(cookie[0]).append("=;");
                                                } else if (cookie.length > 1) {
                                                    sb.append(cookie[0]).append("=").append(cookie[1]).append(";");
                                                }
                                            }
                                            link.setProperty("cookies", sb.toString());
                                            directHTTP = crawledLinkFactorybyDownloadLink(link);
                                        } else {
                                            directHTTP = crawledLinkFactorybyURL("directhttp://" + url);
                                        }
                                        directHTTP.setMatchingRule(rule);
                                        forwardCrawledLinkInfos(copy, directHTTP, linkModifier, getAndClearSourceURLs(copy), true);
                                        // modify sourceLink because directHTTP arise from possibleCryptedLink(convert to directhttp)
                                        directHTTP.setSourceLink(possibleCryptedLink.getSourceLink());
                                        final DISTRIBUTE ret = distributePluginForHost(directPlugin, generation, directHTTP.getURL(), directHTTP);
                                        switch (ret) {
                                        case STOP:
                                            return;
                                        case CONTINUE:
                                            break;
                                        case BLACKLISTED:
                                        case NEXT:
                                            continue mainloop;
                                        default:
                                            LogController.CL().log(new IllegalStateException(ret.name()));
                                            break;
                                        }
                                    } else {
                                        // DirectHTTPPermission.FORBIDDEN
                                        continue mainloop;
                                    }
                                }
                            }
                            final LazyHostPlugin httpPlugin = getGenericHttpPlugin();
                            if (httpPlugin != null && url.startsWith("http")) {
                                try {
                                    if (canHandle(httpPlugin, url, possibleCryptedLink) && getFirstMatchingRule(possibleCryptedLink, url.replaceFirst("(https?)(viajd)://", "$1://"), LinkCrawlerRule.RULE.SUBMITFORM, LinkCrawlerRule.RULE.FOLLOWREDIRECT, LinkCrawlerRule.RULE.DEEPDECRYPT) == null) {
                                        synchronized (loopPreventionEmbedded) {
                                            if (!loopPreventionEmbedded.containsKey(possibleCryptedLink)) {
                                                final UnknownCrawledLinkHandler unknownLinkHandler = new UnknownCrawledLinkHandler() {
                                                    @Override
                                                    public void unhandledCrawledLink(CrawledLink link, LinkCrawler lc) {
                                                        lc.distribute(generation, Arrays.asList(new CrawledLink[] { possibleCryptedLink }));
                                                    }
                                                };
                                                final DISTRIBUTE ret = distributeEmbeddedLink(generation, url, createCopyOf(possibleCryptedLink), unknownLinkHandler);
                                                switch (ret) {
                                                case STOP:
                                                    return;
                                                case CONTINUE:
                                                    break;
                                                case BLACKLISTED:
                                                    continue mainloop;
                                                case NEXT:
                                                    loopPreventionEmbedded.put(possibleCryptedLink, this);
                                                    handleUnhandledCryptedLink(possibleCryptedLink);
                                                    continue mainloop;
                                                default:
                                                    LogController.CL().log(new IllegalStateException(ret.name()));
                                                    break;
                                                }
                                            }
                                        }
                                        if (DirectHTTPPermission.ALWAYS.equals(directHTTPPermission)) {
                                            final DISTRIBUTE ret = distributePluginForHost(httpPlugin, generation, url, createCopyOf(possibleCryptedLink));
                                            switch (ret) {
                                            case STOP:
                                                return;
                                            case CONTINUE:
                                                break;
                                            case NEXT:
                                            case BLACKLISTED:
                                                continue mainloop;
                                            default:
                                                LogController.CL().log(new IllegalStateException(ret.name()));
                                                break;
                                            }
                                        } else {
                                            // DirectHTTPPermission.FORBIDDEN
                                            continue mainloop;
                                        }
                                    }
                                } catch (final Throwable e) {
                                    LogController.CL().log(e);
                                }
                            }
                        }
                        if (unnknownHandler != null) {
                            /*
                             * CrawledLink is unhandled till now , but has an UnknownHandler set, lets call it, maybe it makes the Link
                             * handable by a Plugin
                             */
                            try {
                                unnknownHandler.unhandledCrawledLink(possibleCryptedLink, this);
                            } catch (final Throwable e) {
                                LogController.CL().log(e);
                            }
                            /* lets retry this crawledLink */
                            continue mainloopretry;
                        }
                        if (!isFtp && !isHttpJD && !isDirect) {
                            // only process non directhttp/https?viajd/ftp
                            final Boolean deeperOrFollow = distributeDeeperOrMatchingRule(generation, url, possibleCryptedLink);
                            if (Boolean.FALSE.equals(deeperOrFollow)) {
                                return;
                            } else {
                                synchronized (loopPreventionEmbedded) {
                                    if (!loopPreventionEmbedded.containsKey(possibleCryptedLink)) {
                                        final DISTRIBUTE ret = distributeEmbeddedLink(generation, url, possibleCryptedLink, null);
                                        switch (ret) {
                                        case STOP:
                                            return;
                                        case CONTINUE:
                                            break;
                                        case BLACKLISTED:
                                            continue mainloop;
                                        case NEXT:
                                            loopPreventionEmbedded.put(possibleCryptedLink, this);
                                            handleUnhandledCryptedLink(possibleCryptedLink);
                                            continue mainloop;
                                        default:
                                            LogController.CL().log(new IllegalStateException(ret.name()));
                                            break;
                                        }
                                    }
                                }
                                if (Boolean.TRUE.equals(deeperOrFollow)) {
                                    continue mainloop;
                                }
                            }
                        }
                        break mainloopretry;
                    }
                    handleUnhandledCryptedLink(possibleCryptedLink);
                }
            } finally {
                checkFinishNotify();
            }
        }
    }

    protected DISTRIBUTE distributeEmbeddedLink(final LinkCrawlerGeneration generation, final String url, final CrawledLink source, UnknownCrawledLinkHandler unknownCrawledLinkHandler) {
        final LinkedHashSet<String> possibleEmbeddedLinks = new LinkedHashSet<String>();
        try {
            final String queryString = new Regex(source.getURL(), "\\?(.+)$").getMatch(0);
            if (StringUtils.isNotEmpty(queryString)) {
                final String[] parameters = queryString.split("\\&(?!#)", -1);
                for (final String parameter : parameters) {
                    try {
                        final String params[] = parameter.split("=", 2);
                        final String checkParam;
                        if (params.length == 1) {
                            checkParam = URLDecoder.decode(params[0], "UTF-8");
                        } else {
                            checkParam = URLDecoder.decode(params[1], "UTF-8");
                        }
                        if (checkParam.startsWith("aHR0c") || checkParam.startsWith("ZnRwOi")) {
                            String base64 = checkParam;
                            /* base64 http and ftp */
                            while (true) {
                                if (base64.length() % 4 != 0) {
                                    base64 += "=";
                                } else {
                                    break;
                                }
                            }
                            final byte[] decoded = Base64.decode(base64);
                            if (decoded != null) {
                                String possibleURLs = new String(decoded, "UTF-8");
                                if (HTMLParser.getProtocol(possibleURLs) == null) {
                                    possibleURLs = URLDecoder.decode(possibleURLs, "UTF-8");
                                }
                                if (HTMLParser.getProtocol(possibleURLs) != null) {
                                    possibleEmbeddedLinks.add(possibleURLs);
                                }
                            }
                        } else {
                            try {
                                final String maybeURL;
                                if (checkParam.contains("%3")) {
                                    maybeURL = URLDecoder.decode(checkParam, "UTF-8").replaceFirst("^:?/?/?", "");
                                } else {
                                    maybeURL = checkParam.replaceFirst("^:?/?/?", "");
                                }
                                final URL dummyURL;
                                if (HTMLParser.getProtocol(maybeURL) == null) {
                                    dummyURL = new URL("http://" + maybeURL.replaceFirst("^(.+?://)", ""));
                                } else {
                                    dummyURL = new URL(maybeURL);
                                }
                                if (dummyURL != null && dummyURL.getHost() != null && dummyURL.getHost().contains(".")) {
                                    possibleEmbeddedLinks.add(dummyURL.toString());
                                }
                            } catch (final MalformedURLException e) {
                            }
                        }
                    } catch (final Throwable e) {
                        LogController.CL().log(e);
                    }
                }
            }
            if (StringUtils.contains(source.getURL(), "aHR0c") || StringUtils.contains(source.getURL(), "ZnRwOi")) {
                String base64 = new Regex(source.getURL(), "(aHR0c[0-9a-zA-Z\\+\\/=]+(%3D){0,2})").getMatch(0);// http
                if (base64 == null) {
                    base64 = new Regex(source.getURL(), "(ZnRwOi[0-9a-zA-Z\\+\\/=]+(%3D){0,2})").getMatch(0);// ftp
                }
                if (base64 != null) {
                    if (base64.contains("%3D")) {
                        base64 = URLDecoder.decode(base64, "UTF-8");
                    }
                    while (true) {
                        if (base64.length() % 4 != 0) {
                            base64 += "=";
                        } else {
                            break;
                        }
                    }
                    final byte[] decoded = Base64.decode(base64);
                    if (decoded != null) {
                        String possibleURLs = new String(decoded, "UTF-8");
                        if (HTMLParser.getProtocol(possibleURLs) == null) {
                            possibleURLs = URLDecoder.decode(possibleURLs, "UTF-8");
                        }
                        if (HTMLParser.getProtocol(possibleURLs) != null) {
                            possibleEmbeddedLinks.add(possibleURLs);
                        }
                    }
                }
            }
        } catch (final Throwable e) {
            LogController.CL().log(e);
        }
        if (possibleEmbeddedLinks.size() > 0) {
            final ArrayList<CrawledLink> embeddedLinks = new ArrayList<CrawledLink>();
            for (final String possibleURL : possibleEmbeddedLinks) {
                final List<CrawledLink> links = find(generation, possibleURL, null, source.isCrawlDeep(), false);
                if (links != null) {
                    embeddedLinks.addAll(links);
                }
            }
            if (embeddedLinks.size() > 0) {
                final boolean singleDest = embeddedLinks.size() == 1;
                final String[] sourceURLs = getAndClearSourceURLs(source);
                final CrawledLinkModifier sourceLinkModifier = source.getCustomCrawledLinkModifier();
                source.setCustomCrawledLinkModifier(null);
                source.setBrokenCrawlerHandler(null);
                for (final CrawledLink embeddedLink : embeddedLinks) {
                    embeddedLink.setUnknownHandler(unknownCrawledLinkHandler);
                    forwardCrawledLinkInfos(source, embeddedLink, sourceLinkModifier, sourceURLs, singleDest);
                }
                crawl(generation, embeddedLinks);
                return DISTRIBUTE.NEXT;
            }
        }
        return DISTRIBUTE.CONTINUE;
    }

    public List<LinkCrawlerRule> getLinkCrawlerRules() {
        List<LinkCrawlerRule> ret = linkCrawlerRules.get();
        if (ret == null) {
            synchronized (LINKCRAWLERRULESLOCK) {
                ret = linkCrawlerRules.get();
                if (ret == null) {
                    linkCrawlerRules.set(listLinkCrawlerRules());
                    return getLinkCrawlerRules();
                }
            }
        }
        if (ret.size() == 0) {
            return null;
        } else {
            return ret;
        }
    }

    protected LinkCrawlerRule getFirstMatchingRule(CrawledLink link, String url, LinkCrawlerRule.RULE... ruleTypes) {
        final List<LinkCrawlerRule> rules = getLinkCrawlerRules();
        if (rules != null && (StringUtils.startsWithCaseInsensitive(url, "file:/") || StringUtils.startsWithCaseInsensitive(url, "http://") || StringUtils.startsWithCaseInsensitive(url, "https://"))) {
            for (final LinkCrawlerRule.RULE ruleType : ruleTypes) {
                for (final LinkCrawlerRule rule : rules) {
                    if (ruleType.equals(rule.getRule()) && rule.matches(url)) {
                        if (rule.getMaxDecryptDepth() == -1) {
                            return rule;
                        } else {
                            final Iterator<CrawledLink> it = link.iterator();
                            int depth = 0;
                            while (it.hasNext()) {
                                final CrawledLink next = it.next();
                                final LinkCrawlerRule matchingRule = next.getMatchingRule();
                                if (matchingRule != null && matchingRule.getId() == rule.getId()) {
                                    depth++;
                                }
                            }
                            if (depth <= rule.getMaxDecryptDepth()) {
                                // okay
                                return rule;
                            } else {
                                // too deep
                                continue;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    protected List<LazyCrawlerPlugin> getSortedLazyCrawlerPlugins() {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.getSortedLazyCrawlerPlugins();
        } else {
            if (unsortedLazyCrawlerPlugins == null) {
                unsortedLazyCrawlerPlugins = CrawlerPluginController.getInstance().list();
            }
            List<LazyCrawlerPlugin> ret = sortedLazyCrawlerPlugins.get();
            if (ret == null) {
                synchronized (sortedLazyCrawlerPlugins) {
                    ret = sortedLazyCrawlerPlugins.get();
                    if (ret == null) {
                        /* sort cHosts according to their usage */
                        ret = new ArrayList<LazyCrawlerPlugin>(unsortedLazyCrawlerPlugins.size());
                        final List<LazyCrawlerPlugin> allPlugins = new ArrayList<LazyCrawlerPlugin>(unsortedLazyCrawlerPlugins);
                        try {
                            final Map<String, Object> pluginMap = new HashMap<String, Object>();
                            for (final LazyCrawlerPlugin plugin : allPlugins) {
                                final Object entry = pluginMap.get(plugin.getDisplayName());
                                if (entry == null) {
                                    pluginMap.put(plugin.getDisplayName(), plugin);
                                } else if (entry instanceof List) {
                                    ((List<LazyCrawlerPlugin>) entry).add(plugin);
                                } else {
                                    final ArrayList<LazyCrawlerPlugin> list = new ArrayList<LazyCrawlerPlugin>();
                                    list.add((LazyCrawlerPlugin) entry);
                                    list.add(plugin);
                                    pluginMap.put(plugin.getDisplayName(), list);
                                }
                            }
                            Collections.sort(allPlugins, new Comparator<LazyCrawlerPlugin>() {
                                public final int compare(final long x, final long y) {
                                    return (x < y) ? 1 : ((x == y) ? 0 : -1);
                                }

                                public final int compare(final boolean x, final boolean y) {
                                    return (x == y) ? 0 : (x ? 1 : -1);
                                }

                                @Override
                                public int compare(LazyCrawlerPlugin o1, LazyCrawlerPlugin o2) {
                                    final int ret = compare(o1.getPluginUsage(), o2.getPluginUsage());
                                    if (ret == 0) {
                                        return compare(o1.hasFeature(FEATURE.GENERIC), o2.hasFeature(FEATURE.GENERIC));
                                    } else {
                                        return ret;
                                    }
                                }
                            });
                            for (final LazyCrawlerPlugin plugin : allPlugins) {
                                final Object entry = pluginMap.remove(plugin.getDisplayName());
                                if (entry == null) {
                                    if (pluginMap.isEmpty()) {
                                        break;
                                    } else {
                                        continue;
                                    }
                                } else if (entry instanceof LazyCrawlerPlugin) {
                                    ret.add((LazyCrawlerPlugin) entry);
                                } else {
                                    final List<LazyCrawlerPlugin> list = (List<LazyCrawlerPlugin>) entry;
                                    sortLazyCrawlerPluginByInterfaceVersion(list);
                                    ret.addAll(list);
                                }
                            }
                        } catch (final Throwable e) {
                            LogController.CL(true).log(e);
                        }
                        if (ret == null || ret.size() == 0) {
                            ret = allPlugins;
                        }
                        sortedLazyCrawlerPlugins.compareAndSet(null, ret);
                    }
                }
            }
            return ret;
        }
    }

    protected void sortLazyCrawlerPluginByInterfaceVersion(final List<LazyCrawlerPlugin> plugins) {
        Collections.sort(plugins, new Comparator<LazyCrawlerPlugin>() {
            @Override
            public final int compare(final LazyCrawlerPlugin lazyCrawlerPlugin1, final LazyCrawlerPlugin lazyCrawlerPlugin2) {
                final int i1 = lazyCrawlerPlugin1.getLazyPluginClass().getInterfaceVersion();
                final int i2 = lazyCrawlerPlugin2.getLazyPluginClass().getInterfaceVersion();
                if (i1 == i2) {
                    return 0;
                } else if (i1 > i2) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });
    }

    protected List<LazyHostPlugin> getSortedLazyHostPlugins() {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.getSortedLazyHostPlugins();
        } else {
            /* sort pHosts according to their usage */
            List<LazyHostPlugin> ret = sortedLazyHostPlugins.get();
            if (ret == null) {
                synchronized (sortedLazyHostPlugins) {
                    ret = sortedLazyHostPlugins.get();
                    if (ret == null) {
                        ret = new ArrayList<LazyHostPlugin>();
                        for (final LazyHostPlugin lazyHostPlugin : HostPluginController.getInstance().list()) {
                            if (!HTTP_LINKS.equals(lazyHostPlugin.getDisplayName()) && !"ftp".equals(lazyHostPlugin.getDisplayName()) && !DIRECT_HTTP.equals(lazyHostPlugin.getDisplayName())) {
                                ret.add(lazyHostPlugin);
                            }
                        }
                        try {
                            Collections.sort(ret, new Comparator<LazyHostPlugin>() {
                                public final int compare(long x, long y) {
                                    return (x < y) ? 1 : ((x == y) ? 0 : -1);
                                }

                                @Override
                                public final int compare(LazyHostPlugin o1, LazyHostPlugin o2) {
                                    return compare(o1.getPluginUsage(), o2.getPluginUsage());
                                }
                            });
                        } catch (final Throwable e) {
                            LogController.CL(true).log(e);
                        }
                        sortedLazyHostPlugins.compareAndSet(null, ret);
                    }
                }
            }
            return ret;
        }
    }

    protected boolean resetSortedLazyCrawlerPlugins(List<LazyCrawlerPlugin> resetSortedLazyCrawlerPlugins) {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.resetSortedLazyCrawlerPlugins(resetSortedLazyCrawlerPlugins);
        } else {
            return sortedLazyCrawlerPlugins.compareAndSet(resetSortedLazyCrawlerPlugins, null);
        }
    }

    protected boolean resetSortedLazyHostPlugins(List<LazyHostPlugin> lazyHostPlugins) {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.resetSortedLazyHostPlugins(lazyHostPlugins);
        } else {
            return sortedLazyHostPlugins.compareAndSet(lazyHostPlugins, null);
        }
    }

    protected DirectHTTPPermission getDirectHTTPPermission() {
        return directHTTPPermission;
    }

    public List<CrawledLink> getCryptedLinks(LazyCrawlerPlugin lazyC, CrawledLink source, CrawledLinkModifier modifier) {
        final String[] matches = getMatchingLinks(lazyC.getPattern(), source, modifier);
        if (matches != null && matches.length > 0) {
            final ArrayList<CrawledLink> ret = new ArrayList<CrawledLink>();
            for (final String match : matches) {
                final CryptedLink cryptedLink = new CryptedLink(match);
                cryptedLink.setLazyC(lazyC);
                final CrawledLink link = crawledLinkFactorybyCryptedLink(cryptedLink);
                forwardCrawledLinkInfos(source, link, modifier, null, null);
                if ((source.getUrlLink() == null || StringUtils.equals(source.getUrlLink(), link.getURL())) && (source.getDownloadLink() == null || source.getDownloadLink().getProperties().isEmpty())) {
                    // modify sourceLink because link arise from source(getMatchingLinks)
                    //
                    // keep DownloadLinks with non empty properties
                    link.setSourceLink(source.getSourceLink());
                    if (source.getMatchingRule() != null) {
                        link.setMatchingRule(source.getMatchingRule());
                    }
                }
                ret.add(link);
            }
            return ret;
        }
        return null;
    }

    protected String[] getMatchingLinks(Pattern pattern, CrawledLink source, CrawledLinkModifier modifier) {
        final String[] ret = new Regex(source.getURL(), pattern).getColumn(-1);
        if (ret != null && ret.length > 0) {
            for (int index = 0; index < ret.length; index++) {
                String match = ret[index];
                match = match.trim();
                while (match.length() > 0 && match.charAt(0) == '"') {
                    match = match.substring(1);
                }
                while (match.length() > 0 && match.charAt(match.length() - 1) == '"') {
                    match = match.substring(0, match.length() - 1);
                }
                ret[index] = match.trim();
                if (StringUtils.equals(source.getURL(), ret[index])) {
                    ret[index] = source.getURL();
                }
            }
            return ret;
        }
        return null;
    }

    public List<CrawledLink> getCrawledLinks(Pattern pattern, CrawledLink source, CrawledLinkModifier modifier) {
        final String[] matches = getMatchingLinks(pattern, source, modifier);
        if (matches != null && matches.length > 0) {
            final ArrayList<CrawledLink> ret = new ArrayList<CrawledLink>();
            for (final String match : matches) {
                final CrawledLink link = crawledLinkFactorybyURL(match);
                forwardCrawledLinkInfos(source, link, modifier, null, null);
                if ((source.getUrlLink() == null || StringUtils.equals(source.getUrlLink(), link.getURL())) && (source.getDownloadLink() == null || source.getDownloadLink().getProperties().isEmpty())) {
                    // modify sourceLink because link arise from source(getMatchingLinks)
                    //
                    // keep DownloadLinks with non empty properties
                    link.setSourceLink(source.getSourceLink());
                    if (source.getMatchingRule() != null) {
                        link.setMatchingRule(source.getMatchingRule());
                    }
                }
                ret.add(link);
            }
            return ret;
        }
        return null;
    }

    protected void processHostPlugin(final LinkCrawlerGeneration generation, LazyHostPlugin pHost, CrawledLink possibleCryptedLink) {
        final CrawledLinkModifier parentLinkModifier = possibleCryptedLink.getCustomCrawledLinkModifier();
        possibleCryptedLink.setCustomCrawledLinkModifier(null);
        possibleCryptedLink.setBrokenCrawlerHandler(null);
        if (pHost == null || possibleCryptedLink.getURL() == null || this.isCrawledLinkFiltered(possibleCryptedLink)) {
            return;
        }
        if (checkStartNotify(generation)) {
            try {
                final String[] sourceURLs = getAndClearSourceURLs(possibleCryptedLink);
                PluginForHost wplg = null;
                /*
                 * use a new PluginClassLoader here
                 */
                wplg = pHost.newInstance(getPluginClassLoaderChild());
                if (wplg != null) {
                    /* now we run the plugin and let it find some links */
                    final LinkCrawlerThread lct = getCurrentLinkCrawlerThread();
                    Object owner = null;
                    LinkCrawler previousCrawler = null;
                    boolean oldDebug = false;
                    boolean oldVerbose = false;
                    LogInterface oldLogger = null;
                    try {
                        LogInterface logger = LogController.getFastPluginLogger(wplg.getHost() + "_" + pHost.getClassName());
                        logger.info("Processing: " + possibleCryptedLink.getURL());
                        if (lct != null) {
                            /* mark thread to be used by crawler plugin */
                            owner = lct.getCurrentOwner();
                            lct.setCurrentOwner(wplg);
                            previousCrawler = lct.getCurrentLinkCrawler();
                            lct.setCurrentLinkCrawler(this);
                            /* save old logger/states */
                            oldLogger = lct.getLogger();
                            oldDebug = lct.isDebug();
                            oldVerbose = lct.isVerbose();
                            /* set new logger and set verbose/debug true */
                            lct.setLogger(logger);
                            lct.setVerbose(true);
                            lct.setDebug(true);
                        }
                        wplg.setBrowser(new Browser());
                        wplg.init();
                        wplg.setLogger(logger);
                        String url = possibleCryptedLink.getURL();
                        FilePackage sourcePackage = null;
                        if (possibleCryptedLink.getDownloadLink() != null) {
                            sourcePackage = possibleCryptedLink.getDownloadLink().getFilePackage();
                            if (FilePackage.isDefaultFilePackage(sourcePackage)) {
                                /* we don't want the various filePackage getting used */
                                sourcePackage = null;
                            }
                        }
                        final long startTime = System.currentTimeMillis();
                        final List<CrawledLink> crawledLinks = new ArrayList<CrawledLink>();
                        try {
                            wplg.setCurrentLink(possibleCryptedLink);
                            final List<DownloadLink> hosterLinks = wplg.getDownloadLinks(url, sourcePackage);
                            if (hosterLinks != null) {
                                final UrlProtection protection = wplg.getUrlProtection(hosterLinks);
                                if (protection != null && protection != UrlProtection.UNSET) {
                                    for (DownloadLink dl : hosterLinks) {
                                        if (dl.getUrlProtection() == UrlProtection.UNSET) {
                                            dl.setUrlProtection(protection);
                                        }
                                    }
                                }
                                for (final DownloadLink hosterLink : hosterLinks) {
                                    try {
                                        wplg.correctDownloadLink(hosterLink);
                                    } catch (final Throwable e) {
                                        LogController.CL().log(e);
                                    }
                                    crawledLinks.add(wplg.convert(hosterLink));
                                }
                            }
                            /* in case the function returned without exceptions, we can clear log */
                            if (logger instanceof ClearableLogInterface) {
                                ((ClearableLogInterface) logger).clear();
                            }
                        } finally {
                            wplg.setCurrentLink(null);
                            final long endTime = System.currentTimeMillis() - startTime;
                            wplg.getLazyP().updateParseRuntime(endTime);
                            /* close the logger */
                            if (logger instanceof ClosableLogInterface) {
                                ((ClosableLogInterface) logger).close();
                            }
                        }
                        if (crawledLinks.size() > 0) {
                            final boolean singleDest = crawledLinks.size() == 1;
                            for (final CrawledLink crawledLink : crawledLinks) {
                                forwardCrawledLinkInfos(possibleCryptedLink, crawledLink, parentLinkModifier, sourceURLs, singleDest);
                                if (possibleCryptedLink.getUrlLink() == null || StringUtils.equals(possibleCryptedLink.getUrlLink(), crawledLink.getURL())) {
                                    // modify sourceLink because crawledLink arise from possibleCryptedLink(wplg.getDownloadLinks)
                                    crawledLink.setSourceLink(possibleCryptedLink.getSourceLink());
                                    if (possibleCryptedLink.getMatchingRule() != null) {
                                        crawledLink.setMatchingRule(possibleCryptedLink.getMatchingRule());
                                    }
                                }
                                handleFinalCrawledLink(crawledLink);
                            }
                        }
                    } finally {
                        if (lct != null) {
                            /* reset thread to last known used state */
                            lct.setCurrentOwner(owner);
                            lct.setCurrentLinkCrawler(previousCrawler);
                            lct.setLogger(oldLogger);
                            lct.setVerbose(oldVerbose);
                            lct.setDebug(oldDebug);
                        }
                    }
                } else {
                    LogController.CL().info("Hoster Plugin not available:" + pHost.getDisplayName());
                }
            } catch (Throwable e) {
                LogController.CL().log(e);
            } finally {
                /* restore old ClassLoader for current Thread */
                checkFinishNotify();
            }
        }
    }

    public String[] getAndClearSourceURLs(final CrawledLink link) {
        final ArrayList<String> sources = new ArrayList<String>();
        CrawledLink next = link;
        CrawledLink previous = link;
        while (next != null) {
            final CrawledLink current = next;
            next = current.getSourceLink();
            final String currentURL = cleanURL(current.getURL());
            if (currentURL != null) {
                if (sources.size() == 0) {
                    sources.add(currentURL);
                } else {
                    if (current.getMatchingRule() == null || current.getMatchingRule() != previous.getMatchingRule()) {
                        final String previousURL = sources.get(sources.size() - 1);
                        if (!StringUtils.equals(currentURL, previousURL)) {
                            sources.add(currentURL);
                        }
                    }
                }
            }
            previous = current;
        }
        link.setSourceUrls(null);
        final String customSourceUrl = getReferrerUrl(link);
        if (customSourceUrl != null) {
            sources.add(customSourceUrl);
        }
        if (sources.size() == 0) {
            return null;
        } else {
            return sources.toArray(new String[] {});
        }
    }

    public String getReferrerUrl(final CrawledLink link) {
        if (link != null) {
            final LinkCollectingJob job = link.getSourceJob();
            if (job != null) {
                final String customSourceUrl = job.getCustomSourceUrl();
                if (customSourceUrl != null) {
                    return customSourceUrl;
                }
            }
        }
        if (this instanceof JobLinkCrawler) {
            final LinkCollectingJob job = ((JobLinkCrawler) this).getJob();
            if (job != null) {
                return job.getCustomSourceUrl();
            }
        }
        return null;
    }

    public static String getUnsafeName(String unsafeName, String currentName) {
        if (unsafeName != null) {
            String extension = Files.getExtension(unsafeName);
            if (extension == null && unsafeName.indexOf('.') < 0) {
                String unsafeSourceModified = null;
                if (unsafeName.indexOf('_') > 0) {
                    unsafeSourceModified = unsafeName.replaceAll("_", ".");
                    extension = Files.getExtension(unsafeSourceModified);
                }
                if (extension == null && unsafeName.indexOf('-') > 0) {
                    unsafeSourceModified = unsafeName.replaceAll("-", ".");
                    extension = Files.getExtension(unsafeSourceModified);
                }
                if (extension != null) {
                    unsafeName = unsafeSourceModified;
                }
            }
            if (extension != null && !StringUtils.equals(currentName, unsafeName)) {
                return unsafeName;
            }
        }
        return null;
    }

    /**
     * in case link contains rawURL/CryptedLink we return downloadLink from sourceLink
     *
     * @param link
     * @return
     */
    private DownloadLink getLatestDownloadLink(CrawledLink link) {
        final DownloadLink ret = link.getDownloadLink();
        if (ret == null && link.getSourceLink() != null) {
            return link.getSourceLink().getDownloadLink();
        } else {
            return ret;
        }
    }

    private CryptedLink getLatestCryptedLink(CrawledLink link) {
        final CryptedLink ret = link.getCryptedLink();
        if (ret == null && link.getSourceLink() != null) {
            return link.getSourceLink().getCryptedLink();
        } else {
            return ret;
        }
    }

    private void forwardCryptedLinkInfos(final CrawledLink sourceCrawledLink, final CryptedLink destCryptedLink) {
        if (sourceCrawledLink != null && destCryptedLink != null) {
            String pw = null;
            final CrawledLink sourceCrawledLink2 = sourceCrawledLink.getSourceLink();
            if (sourceCrawledLink2 != null && sourceCrawledLink2.getDownloadLink() != null) {
                pw = sourceCrawledLink2.getDownloadLink().getDownloadPassword();
            }
            final CryptedLink sourceCryptedLink = getLatestCryptedLink(sourceCrawledLink);
            if (sourceCryptedLink != null) {
                if (pw == null) {
                    pw = sourceCryptedLink.getDecrypterPassword();
                }
            }
            if (pw == null && LinkCrawler.this instanceof JobLinkCrawler && ((JobLinkCrawler) LinkCrawler.this).getJob() != null) {
                pw = ((JobLinkCrawler) LinkCrawler.this).getJob().getCrawlerPassword();
            }
            destCryptedLink.setDecrypterPassword(pw);
        }
    }

    protected void forwardCrawledLinkInfos(final CrawledLink sourceCrawledLink, final CrawledLink destCrawledLink, final CrawledLinkModifier sourceLinkModifier, final String sourceURLs[], final Boolean singleDestCrawledLink) {
        if (sourceCrawledLink != null && destCrawledLink != null && sourceCrawledLink != destCrawledLink) {
            destCrawledLink.setSourceLink(sourceCrawledLink);
            destCrawledLink.setOrigin(sourceCrawledLink.getOrigin());
            destCrawledLink.setSourceUrls(sourceURLs);
            destCrawledLink.setMatchingFilter(sourceCrawledLink.getMatchingFilter());
            forwardCryptedLinkInfos(sourceCrawledLink, destCrawledLink.getCryptedLink());
            forwardDownloadLinkInfos(getLatestDownloadLink(sourceCrawledLink), destCrawledLink.getDownloadLink(), singleDestCrawledLink);
            if (Boolean.TRUE.equals(singleDestCrawledLink) && sourceCrawledLink.isNameSet()) {
                // forward customized name, eg from container plugins
                destCrawledLink.setName(sourceCrawledLink.getName());
            }
            final CrawledLinkModifier destCustomModifier = destCrawledLink.getCustomCrawledLinkModifier();
            if (destCustomModifier == null) {
                destCrawledLink.setCustomCrawledLinkModifier(sourceLinkModifier);
            } else if (sourceLinkModifier != null) {
                destCrawledLink.setCustomCrawledLinkModifier(new CrawledLinkModifier() {
                    private final ArrayList<CrawledLinkModifier> modifiers = new ArrayList<CrawledLinkModifier>();
                    {
                        modifiers.add(sourceLinkModifier);
                        modifiers.add(destCustomModifier);
                    }

                    @Override
                    public List<CrawledLinkModifier> getSubCrawledLinkModifier(CrawledLink link) {
                        return Collections.unmodifiableList(modifiers);
                    }

                    @Override
                    public boolean modifyCrawledLink(CrawledLink link) {
                        boolean ret = false;
                        for (CrawledLinkModifier mod : modifiers) {
                            if (mod.modifyCrawledLink(link)) {
                                ret = true;
                            }
                        }
                        return ret;
                    }
                });
            }
            // if we decrypted a dlc,source.getDesiredPackageInfo() is null, and dest might already have package infos from the container.
            // maybe it would be even better to merge the packageinfos
            // However. if there are crypted links in the container, it may be up to the decrypterplugin to decide
            // example: share-links.biz uses CNL to post links to localhost. the dlc origin get's lost on such a way
            final PackageInfo dpi = sourceCrawledLink.getDesiredPackageInfo();
            if (dpi != null) {
                destCrawledLink.setDesiredPackageInfo(dpi.getCopy());
            }
            final ArchiveInfo destArchiveInfo;
            if (destCrawledLink.hasArchiveInfo()) {
                destArchiveInfo = destCrawledLink.getArchiveInfo();
            } else {
                destArchiveInfo = null;
            }
            if (sourceCrawledLink.hasArchiveInfo()) {
                if (destArchiveInfo == null) {
                    destCrawledLink.setArchiveInfo(new ArchiveInfo().migrate(sourceCrawledLink.getArchiveInfo()));
                } else {
                    destArchiveInfo.migrate(sourceCrawledLink.getArchiveInfo());
                }
            }
            convertFilePackageInfos(destCrawledLink);
            permanentOffline(destCrawledLink);
        }
    }

    private PackageInfo convertFilePackageInfos(CrawledLink link) {
        if (link.getDownloadLink() != null) {
            final FilePackage fp = link.getDownloadLink().getFilePackage();
            if (!FilePackage.isDefaultFilePackage(fp)) {
                fp.remove(link.getDownloadLink());
                if (link.getDesiredPackageInfo() != null && Boolean.TRUE.equals(link.getDesiredPackageInfo().isAllowInheritance())) {
                    if (!fp.hasProperty(PACKAGE_ALLOW_INHERITANCE) || fp.getBooleanProperty(PACKAGE_ALLOW_INHERITANCE, false) == false) {
                        return link.getDesiredPackageInfo();
                    }
                }
                PackageInfo fpi = null;
                if (StringUtils.isNotEmpty(fp.getDownloadDirectory()) && !fp.getDownloadDirectory().equals(defaultDownloadFolder)) {
                    // do not set downloadfolder if it is the defaultfolder
                    if (link.getDesiredPackageInfo() == null) {
                        fpi = new PackageInfo();
                    } else {
                        fpi = link.getDesiredPackageInfo();
                    }
                    fpi.setDestinationFolder(CrossSystem.fixPathSeparators(fp.getDownloadDirectory() + File.separator));
                }
                final String name;
                if (Boolean.FALSE.equals(fp.getBooleanProperty(PACKAGE_CLEANUP_NAME, true))) {
                    name = fp.getName();
                } else {
                    name = LinknameCleaner.cleanFileName(fp.getName(), false, true, LinknameCleaner.EXTENSION_SETTINGS.REMOVE_KNOWN, true);
                }
                if (StringUtils.isNotEmpty(name)) {
                    if (fpi == null) {
                        if (link.getDesiredPackageInfo() == null) {
                            fpi = new PackageInfo();
                        } else {
                            fpi = link.getDesiredPackageInfo();
                        }
                    }
                    fpi.setName(name);
                }
                if (fp.hasProperty(PACKAGE_ALLOW_MERGE)) {
                    if (Boolean.FALSE.equals(fp.getBooleanProperty(PACKAGE_ALLOW_MERGE, false))) {
                        if (fpi == null) {
                            if (link.getDesiredPackageInfo() == null) {
                                fpi = new PackageInfo();
                            } else {
                                fpi = link.getDesiredPackageInfo();
                            }
                        }
                        fpi.setUniqueId(fp.getUniqueID());
                    } else {
                        if (fpi == null) {
                            fpi = link.getDesiredPackageInfo();
                        }
                        if (fpi != null) {
                            fpi.setUniqueId(null);
                        }
                    }
                }
                if (fp.hasProperty(PACKAGE_IGNORE_VARIOUS)) {
                    if (fpi == null) {
                        if (link.getDesiredPackageInfo() == null) {
                            fpi = new PackageInfo();
                        } else {
                            fpi = link.getDesiredPackageInfo();
                        }
                    }
                    if (Boolean.TRUE.equals(fp.getBooleanProperty(PACKAGE_IGNORE_VARIOUS, false))) {
                        fpi.setIgnoreVarious(true);
                    } else {
                        fpi.setIgnoreVarious(false);
                    }
                }
                if (fp.hasProperty(PACKAGE_ALLOW_INHERITANCE)) {
                    if (fpi == null) {
                        if (link.getDesiredPackageInfo() == null) {
                            fpi = new PackageInfo();
                        } else {
                            fpi = link.getDesiredPackageInfo();
                        }
                    }
                    fpi.setAllowInheritance(fp.getBooleanProperty(PACKAGE_ALLOW_INHERITANCE));
                }
                if (fpi != null) {
                    link.setDesiredPackageInfo(fpi);
                }
                return fpi;
            }
        }
        return null;
    }

    private void permanentOffline(CrawledLink link) {
        final DownloadLink dl = link.getDownloadLink();
        try {
            if (dl != null && dl.getDefaultPlugin().getLazyP().getClassName().endsWith("r.Offline")) {
                final PackageInfo dpi;
                if (link.getDesiredPackageInfo() == null) {
                    dpi = new PackageInfo();
                } else {
                    dpi = link.getDesiredPackageInfo();
                }
                dpi.setUniqueId(PERMANENT_OFFLINE_ID);
                link.setDesiredPackageInfo(dpi);
            }
        } catch (final Throwable e) {
        }
    }

    protected void forwardDownloadLinkInfos(final DownloadLink sourceDownloadLink, final DownloadLink destDownloadLink, final Boolean singleDestDownloadLink) {
        if (sourceDownloadLink != null && destDownloadLink != null && sourceDownloadLink != destDownloadLink) {
            /* create a copy of ArrayList */
            final List<String> srcPWs = sourceDownloadLink.getSourcePluginPasswordList();
            if (srcPWs != null && srcPWs.size() > 0) {
                destDownloadLink.setSourcePluginPasswordList(new ArrayList<String>(srcPWs));
            }
            if (sourceDownloadLink.getComment() != null && destDownloadLink.getComment() == null) {
                destDownloadLink.setComment(sourceDownloadLink.getComment());
            }
            if (sourceDownloadLink.getContainerUrl() != null && destDownloadLink.getContainerUrl() == null) {
                destDownloadLink.setContainerUrl(sourceDownloadLink.getContainerUrl());
            }
            if (destDownloadLink.getUrlProtection() == UrlProtection.UNSET && sourceDownloadLink.getUrlProtection() != UrlProtection.UNSET) {
                destDownloadLink.setUrlProtection(sourceDownloadLink.getUrlProtection());
            }
            if (Boolean.TRUE.equals(singleDestDownloadLink)) {
                if (!destDownloadLink.isNameSet()) {
                    if (sourceDownloadLink.isNameSet()) {
                        destDownloadLink.setName(sourceDownloadLink.getName());
                    } else {
                        final String name = getUnsafeName(sourceDownloadLink.getName(), destDownloadLink.getName());
                        if (name != null) {
                            destDownloadLink.setName(name);
                        }
                    }
                }
                if (sourceDownloadLink.getForcedFileName() != null && destDownloadLink.getForcedFileName() == null) {
                    destDownloadLink.setForcedFileName(sourceDownloadLink.getForcedFileName());
                }
                if (sourceDownloadLink.getFinalFileName() != null && destDownloadLink.getFinalFileName() == null) {
                    destDownloadLink.setFinalFileName(sourceDownloadLink.getFinalFileName());
                }
                if (sourceDownloadLink.isAvailabilityStatusChecked() && sourceDownloadLink.getAvailableStatus() != destDownloadLink.getAvailableStatus() && !destDownloadLink.isAvailabilityStatusChecked()) {
                    destDownloadLink.setAvailableStatus(sourceDownloadLink.getAvailableStatus());
                }
                if (sourceDownloadLink.getContentUrl() != null && destDownloadLink.getContentUrl() == null) {
                    destDownloadLink.setContentUrl(sourceDownloadLink.getContentUrl());
                }
                if (sourceDownloadLink.getVerifiedFileSize() >= 0 && destDownloadLink.getVerifiedFileSize() < 0) {
                    destDownloadLink.setVerifiedFileSize(sourceDownloadLink.getVerifiedFileSize());
                }
                if (sourceDownloadLink.hasTempProperties()) {
                    destDownloadLink.getTempProperties().setProperties(sourceDownloadLink.getTempProperties().getProperties());
                }
                final Map<String, Object> sourceProperties = sourceDownloadLink.getProperties();
                if (sourceProperties != null && !sourceProperties.isEmpty()) {
                    final Map<String, Object> destProperties = destDownloadLink.getProperties();
                    if (destProperties == null || destProperties.isEmpty()) {
                        destDownloadLink.setProperties(sourceProperties);
                    } else {
                        for (Entry<String, Object> property : sourceProperties.entrySet()) {
                            if (!destDownloadLink.hasProperty(property.getKey())) {
                                destDownloadLink.setProperty(property.getKey(), property.getValue());
                            }
                        }
                    }
                }
                if (sourceDownloadLink.getView().getBytesTotal() >= 0 && destDownloadLink.getKnownDownloadSize() < 0) {
                    destDownloadLink.setDownloadSize(sourceDownloadLink.getView().getBytesTotal());
                }
            }
        }
    }

    public void stopCrawling() {
        stopCrawling(true);
    }

    public void stopCrawling(final boolean stopChildren) {
        final LinkCrawlerGeneration generation = linkCrawlerGeneration.getAndSet(null);
        if (generation != null) {
            generation.invalidate();
        }
        if (stopChildren) {
            for (final LinkCrawler child : getChildren()) {
                child.stopCrawling(true);
            }
        }
    }

    public boolean waitForCrawling() {
        return waitForCrawling(true);
    }

    private final static Object WAIT = new Object();

    public boolean waitForCrawling(final boolean waitForChildren) {
        while (isRunning(waitForChildren)) {
            synchronized (WAIT) {
                if (isRunning(waitForChildren)) {
                    try {
                        WAIT.wait(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }
        return isRunning(waitForChildren) == false;
    }

    public List<LinkCrawler> getChildren() {
        synchronized (children) {
            return new ArrayList<LinkCrawler>(children.keySet());
        }
    }

    public LinkCrawler getRoot() {
        final LinkCrawler parent = getParent();
        if (parent != null) {
            return parent.getRoot();
        }
        return this;
    }

    public boolean isRunning() {
        return isRunning(true);
    }

    public boolean isRunning(final boolean checkChildren) {
        if (runningState.get()) {
            return true;
        }
        if (checkChildren) {
            for (final LinkCrawler child : getChildren()) {
                if (child.isRunning(true)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isCrawling() {
        return CRAWLER.get() > 0;
    }

    protected void container(final LinkCrawlerGeneration generation, PluginsC oplg, final CrawledLink cryptedLink) {
        final CrawledLinkModifier parentLinkModifier = cryptedLink.getCustomCrawledLinkModifier();
        cryptedLink.setCustomCrawledLinkModifier(null);
        cryptedLink.setBrokenCrawlerHandler(null);
        if (oplg == null || cryptedLink.getURL() == null) {
            return;
        } else if (isCrawledLinkDuplicated(duplicateFinderContainer, cryptedLink)) {
            onCrawledLinkDuplicate(cryptedLink, DUPLICATE.CONTAINER);
            return;
        } else if (this.isCrawledLinkFiltered(cryptedLink)) {
            return;
        }
        if (checkStartNotify(generation)) {
            final String[] sourceURLs = getAndClearSourceURLs(cryptedLink);
            try {
                processedLinksCounter.incrementAndGet();
                /* set new PluginClassLoaderChild because ContainerPlugin maybe uses Hoster/Crawler */
                PluginsC plg = null;
                try {
                    plg = oplg.newPluginInstance();
                } catch (final Throwable e) {
                    LogController.CL().log(e);
                    return;
                }
                /* now we run the plugin and let it find some links */
                final LinkCrawlerThread lct = getCurrentLinkCrawlerThread();
                Object owner = null;
                LinkCrawler previousCrawler = null;
                boolean oldDebug = false;
                boolean oldVerbose = false;
                LogInterface oldLogger = null;
                try {
                    final LogInterface logger = LogController.getFastPluginLogger(plg.getName());
                    if (lct != null) {
                        /* mark thread to be used by crawler plugin */
                        owner = lct.getCurrentOwner();
                        lct.setCurrentOwner(plg);
                        previousCrawler = lct.getCurrentLinkCrawler();
                        lct.setCurrentLinkCrawler(this);
                        /* save old logger/states */
                        oldLogger = lct.getLogger();
                        oldDebug = lct.isDebug();
                        oldVerbose = lct.isVerbose();
                        /* set new logger and set verbose/debug true */
                        lct.setLogger(logger);
                        lct.setVerbose(true);
                        lct.setDebug(true);
                    }
                    plg.setLogger(logger);
                    try {
                        final List<CrawledLink> decryptedPossibleLinks = plg.decryptContainer(cryptedLink);
                        /* in case the function returned without exceptions, we can clear log */
                        if (logger instanceof ClearableLogInterface) {
                            ((ClearableLogInterface) logger).clear();
                        }
                        if (decryptedPossibleLinks != null && decryptedPossibleLinks.size() > 0) {
                            /* we found some links, distribute them */
                            final boolean singleDest = decryptedPossibleLinks.size() == 1;
                            for (CrawledLink decryptedPossibleLink : decryptedPossibleLinks) {
                                forwardCrawledLinkInfos(cryptedLink, decryptedPossibleLink, parentLinkModifier, sourceURLs, singleDest);
                            }
                            if (insideCrawlerPlugin()) {
                                /*
                                 * direct decrypt this link because we are already inside a LinkCrawlerThread and this avoids deadlocks on
                                 * plugin waiting for linkcrawler results
                                 */
                                if (!generation.isValid()) {
                                    /* LinkCrawler got aborted! */
                                    return;
                                }
                                distribute(generation, decryptedPossibleLinks);
                            } else {
                                if (checkStartNotify(generation)) {
                                    /* enqueue distributing of the links */
                                    threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                                        @Override
                                        public long getAverageRuntime() {
                                            final Long ret = getDefaultAverageRuntime();
                                            if (ret != null) {
                                                return ret;
                                            } else {
                                                return super.getAverageRuntime();
                                            }
                                        }

                                        @Override
                                        void crawling() {
                                            LinkCrawler.this.distribute(generation, decryptedPossibleLinks);
                                        }
                                    });
                                }
                            }
                        }
                    } finally {
                        /* close the logger */
                        if (logger instanceof ClosableLogInterface) {
                            ((ClosableLogInterface) logger).close();
                        }
                    }
                } finally {
                    if (lct != null) {
                        /* reset thread to last known used state */
                        lct.setCurrentOwner(owner);
                        lct.setCurrentLinkCrawler(previousCrawler);
                        lct.setLogger(oldLogger);
                        lct.setVerbose(oldVerbose);
                        lct.setDebug(oldDebug);
                    }
                }
            } finally {
                /* restore old ClassLoader for current Thread */
                checkFinishNotify();
            }
        }
    }

    private boolean isDuplicatedCrawling(LazyPlugin<?> lazyC, final CrawledLink cryptedLink) {
        final String url = cryptedLink.getURL();
        final String urlDecodedURL = Encoding.urlDecode(url, false);
        final String value;
        if (StringUtils.equals(url, urlDecodedURL)) {
            value = url;
        } else {
            value = urlDecodedURL;
        }
        synchronized (duplicateFinderCrawler) {
            Set<Object> set = duplicateFinderCrawler.get(value);
            if (set == null) {
                set = new HashSet<Object>();
                duplicateFinderCrawler.put(value, set);
            }
            if (true) {
                return !set.add(lazyC);
            } else {
                return !set.add(lazyC.getDisplayName() + "_" + lazyC.getClassName());
            }
        }
    }

    protected LinkCrawlerThread getCurrentLinkCrawlerThread() {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof LinkCrawlerThread) {
            return (LinkCrawlerThread) currentThread;
        } else {
            return null;
        }
    }

    protected void crawl(final LinkCrawlerGeneration generation, LazyCrawlerPlugin lazyC, final CrawledLink cryptedLink) {
        final CrawledLinkModifier parentLinkModifier = cryptedLink.getCustomCrawledLinkModifier();
        cryptedLink.setCustomCrawledLinkModifier(null);
        final BrokenCrawlerHandler brokenCrawler = cryptedLink.getBrokenCrawlerHandler();
        cryptedLink.setBrokenCrawlerHandler(null);
        if (lazyC == null || cryptedLink.getCryptedLink() == null) {
            return;
        } else if (isDuplicatedCrawling(lazyC, cryptedLink)) {
            onCrawledLinkDuplicate(cryptedLink, DUPLICATE.CRAWLER);
            return;
        } else if (this.isCrawledLinkFiltered(cryptedLink)) {
            return;
        }
        if (checkStartNotify(generation)) {
            try {
                final String[] sourceURLs = getAndClearSourceURLs(cryptedLink);
                processedLinksCounter.incrementAndGet();
                final PluginForDecrypt wplg;
                /*
                 * we want a fresh pluginClassLoader here
                 */
                try {
                    wplg = lazyC.newInstance(getPluginClassLoaderChild());
                } catch (UpdateRequiredClassNotFoundException e1) {
                    LogController.CL().log(e1);
                    return;
                }
                final AtomicReference<LinkCrawler> nextLinkCrawler = new AtomicReference<LinkCrawler>(this);
                wplg.setBrowser(new Browser());
                wplg.init();
                LogInterface oldLogger = null;
                boolean oldVerbose = false;
                boolean oldDebug = false;
                final LogInterface logger = LogController.getFastPluginLogger(wplg.getHost() + "_" + lazyC.getClassName());
                logger.info("Crawling: " + cryptedLink.getURL());
                wplg.setLogger(logger);
                /* now we run the plugin and let it find some links */
                final LinkCrawlerThread lct = getCurrentLinkCrawlerThread();
                Object owner = null;
                LinkCrawlerDistributer dist = null;
                LinkCrawler previousCrawler = null;
                List<DownloadLink> decryptedPossibleLinks = null;
                try {
                    final boolean useDelay = wplg.getDistributeDelayerMinimum() > 0;
                    final DelayedRunnable finalLinkCrawlerDistributerDelayer;
                    final List<CrawledLink> distributedLinks;
                    if (useDelay) {
                        distributedLinks = new ArrayList<CrawledLink>();
                        final int minimumDelay = Math.max(10, wplg.getDistributeDelayerMinimum());
                        int maximumDelay = wplg.getDistributeDelayerMaximum();
                        if (maximumDelay == 0) {
                            maximumDelay = -1;
                        }
                        finalLinkCrawlerDistributerDelayer = new DelayedRunnable(TIMINGQUEUE, minimumDelay, maximumDelay) {
                            @Override
                            public String getID() {
                                return "LinkCrawler";
                            }

                            @Override
                            public void delayedrun() {
                                /* we are now in IOEQ, thats why we create copy and then push work back into LinkCrawler */
                                final List<CrawledLink> linksToDistribute;
                                synchronized (distributedLinks) {
                                    if (distributedLinks.size() == 0) {
                                        return;
                                    } else {
                                        linksToDistribute = new ArrayList<CrawledLink>(distributedLinks);
                                        distributedLinks.clear();
                                    }
                                }
                                if (checkStartNotify(generation)) {
                                    /* enqueue distributing of the links */
                                    threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                                        @Override
                                        public long getAverageRuntime() {
                                            final Long ret = getDefaultAverageRuntime();
                                            if (ret != null) {
                                                return ret;
                                            } else {
                                                return super.getAverageRuntime();
                                            }
                                        }

                                        @Override
                                        void crawling() {
                                            nextLinkCrawler.get().distribute(generation, linksToDistribute);
                                        }
                                    });
                                }
                            }
                        };
                    } else {
                        finalLinkCrawlerDistributerDelayer = null;
                        distributedLinks = null;
                    }
                    /*
                     * set LinkCrawlerDistributer in case the plugin wants to add links in realtime
                     */
                    wplg.setDistributer(dist = new LinkCrawlerDistributer() {
                        final HashSet<DownloadLink> fastDuplicateDetector = new HashSet<DownloadLink>();
                        final AtomicInteger         distributed           = new AtomicInteger(0);
                        final HashSet<DownloadLink> distribute            = new HashSet<DownloadLink>();

                        public synchronized void distribute(DownloadLink... links) {
                            if (links == null || (links.length == 0 && wplg.getDistributer() != null)) {
                                return;
                            }
                            for (final DownloadLink link : links) {
                                if (link != null && link.getPluginPatternMatcher() != null && !fastDuplicateDetector.contains(link)) {
                                    distribute.add(link);
                                }
                            }
                            if (wplg.getDistributer() != null && (distribute.size() + distributed.get()) <= 1) {
                                /**
                                 * crawler is still running, wait for finish or multiple distributed
                                 */
                                return;
                            }
                            final List<CrawledLink> possibleCryptedLinks = new ArrayList<CrawledLink>(distribute.size());
                            final boolean distributeMultipleLinks = (distribute.size() + distributed.get()) > 1;
                            final String cleanURL = cleanURL(cryptedLink.getCryptedLink().getCryptedUrl());
                            for (final DownloadLink link : distribute) {
                                if (link.getPluginPatternMatcher() != null && fastDuplicateDetector.add(link)) {
                                    distributed.incrementAndGet();
                                    if (cleanURL != null) {
                                        if (isTempDecryptedURL(link.getPluginPatternMatcher())) {
                                            /**
                                             * some plugins have same regex for hoster/decrypter, so they add decrypted.com at the end
                                             */
                                            if (distributeMultipleLinks) {
                                                if (link.getContainerUrl() == null) {
                                                    link.setContainerUrl(cleanURL);
                                                }
                                            } else {
                                                if (link.getContentUrl() == null) {
                                                    link.setContentUrl(cleanURL);
                                                }
                                            }
                                        } else {
                                            /**
                                             * this plugin returned multiple links, so we set containerURL (if not set yet)
                                             */
                                            if (distributeMultipleLinks && link.getContainerUrl() == null) {
                                                link.setContainerUrl(cleanURL);
                                            }
                                        }
                                    }
                                    final CrawledLink crawledLink = wplg.convert(link);
                                    forwardCrawledLinkInfos(cryptedLink, crawledLink, parentLinkModifier, sourceURLs, !distributeMultipleLinks);
                                    possibleCryptedLinks.add(crawledLink);
                                }
                            }
                            distribute.clear();
                            if (useDelay && wplg.getDistributer() != null) {
                                /* we delay the distribute */
                                synchronized (distributedLinks) {
                                    /* synchronized adding */
                                    distributedLinks.addAll(possibleCryptedLinks);
                                }
                                /* restart delayer to distribute links */
                                finalLinkCrawlerDistributerDelayer.run();
                            } else if (possibleCryptedLinks.size() > 0) {
                                /* we do not delay the distribute */
                                if (checkStartNotify(generation)) {
                                    /* enqueue distributing of the links */
                                    threadPool.execute(new LinkCrawlerRunnable(LinkCrawler.this, generation) {
                                        @Override
                                        public long getAverageRuntime() {
                                            final Long ret = getDefaultAverageRuntime();
                                            if (ret != null) {
                                                return ret;
                                            } else {
                                                return super.getAverageRuntime();
                                            }
                                        }

                                        @Override
                                        void crawling() {
                                            nextLinkCrawler.get().distribute(generation, possibleCryptedLinks);
                                        }
                                    });
                                }
                            }
                        }
                    });
                    if (lct != null) {
                        /* mark thread to be used by decrypter plugin */
                        owner = lct.getCurrentOwner();
                        lct.setCurrentOwner(wplg);
                        previousCrawler = lct.getCurrentLinkCrawler();
                        lct.setCurrentLinkCrawler(this);
                        /* save old logger/states */
                        oldLogger = lct.getLogger();
                        oldDebug = lct.isDebug();
                        oldVerbose = lct.isVerbose();
                        /* set new logger and set verbose/debug true */
                        lct.setLogger(logger);
                        lct.setVerbose(true);
                        lct.setDebug(true);
                    }
                    final long startTime = System.currentTimeMillis();
                    try {
                        wplg.setCrawler(this);
                        wplg.setLinkCrawlerGeneration(generation);
                        final LinkCrawler pluginNextLinkCrawler = wplg.getCustomNextCrawler();
                        if (pluginNextLinkCrawler != null) {
                            nextLinkCrawler.set(pluginNextLinkCrawler);
                        }
                        decryptedPossibleLinks = wplg.decryptLink(cryptedLink);
                        /* remove distributer from plugin to process remaining/returned links */
                        wplg.setDistributer(null);
                        if (finalLinkCrawlerDistributerDelayer != null) {
                            finalLinkCrawlerDistributerDelayer.setDelayerEnabled(false);
                            /* make sure we dont have any unprocessed delayed Links */
                            finalLinkCrawlerDistributerDelayer.delayedrun();
                        }
                        if (decryptedPossibleLinks != null) {
                            /* distribute remaining/returned links */
                            dist.distribute(decryptedPossibleLinks.toArray(new DownloadLink[decryptedPossibleLinks.size()]));
                        }
                        /* in case we return normally, clear the logger */
                        if (logger instanceof ClearableLogInterface) {
                            ((ClearableLogInterface) logger).clear();
                        }
                    } finally {
                        /* close the logger */
                        wplg.setLinkCrawlerGeneration(null);
                        wplg.setCurrentLink(null);
                        final long endTime = System.currentTimeMillis() - startTime;
                        lazyC.updateCrawlRuntime(endTime);
                        if (logger instanceof ClosableLogInterface) {
                            ((ClosableLogInterface) logger).close();
                        }
                    }
                } finally {
                    if (lct != null) {
                        /* reset thread to last known used state */
                        lct.setCurrentOwner(owner);
                        lct.setCurrentLinkCrawler(previousCrawler);
                        lct.setLogger(oldLogger);
                        lct.setVerbose(oldVerbose);
                        lct.setDebug(oldDebug);
                    }
                }
                if (decryptedPossibleLinks == null) {
                    this.handleBrokenCrawledLink(cryptedLink);
                }
                if (brokenCrawler != null) {
                    try {
                        brokenCrawler.brokenCrawler(cryptedLink, this);
                    } catch (final Throwable e) {
                        LogController.CL().log(e);
                    }
                }
            } finally {
                /* restore old ClassLoader for current Thread */
                checkFinishNotify();
            }
        }
    }

    public java.util.List<CrawledLink> getCrawledLinks() {
        return crawledLinks;
    }

    public java.util.List<CrawledLink> getFilteredLinks() {
        return filteredLinks;
    }

    public java.util.List<CrawledLink> getBrokenLinks() {
        return brokenLinks;
    }

    public java.util.List<CrawledLink> getUnhandledLinks() {
        return unhandledLinks;
    }

    protected void handleBrokenCrawledLink(CrawledLink link) {
        this.brokenLinksCounter.incrementAndGet();
        getHandler().handleBrokenLink(link);
    }

    protected void handleUnhandledCryptedLink(CrawledLink link) {
        this.unhandledLinksCounter.incrementAndGet();
        getHandler().handleUnHandledLink(link);
    }

    private String getContentURL(final CrawledLink link) {
        final DownloadLink downloadLink = link.getDownloadLink();
        final PluginForHost plugin = downloadLink.getDefaultPlugin();
        if (downloadLink != null && plugin != null) {
            final String pluginURL = downloadLink.getPluginPatternMatcher();
            final Iterator<CrawledLink> it = link.iterator();
            while (it.hasNext()) {
                final CrawledLink next = it.next();
                if (next == link) {
                    continue;
                }
                if (next.getDownloadLink() != null || next.getCryptedLink() == null) {
                    final String nextURL = cleanURL(next.getURL());
                    if (nextURL != null && !StringUtils.equals(pluginURL, nextURL)) {
                        final String[] hits = new Regex(nextURL, plugin.getSupportedLinks()).getColumn(-1);
                        if (hits != null) {
                            if (hits.length == 1 && hits[0] != null && !StringUtils.equals(pluginURL, hits[0])) {
                                return hits[0];
                            }
                            return null;
                        }
                    }
                    if (next.getDownloadLink() != null) {
                        continue;
                    }
                }
                break;
            }
        }
        return null;
    }

    private String getOriginURL(final CrawledLink link) {
        final DownloadLink downloadLink = link.getDownloadLink();
        if (downloadLink != null) {
            final String pluginURL = downloadLink.getPluginPatternMatcher();
            final Iterator<CrawledLink> it = link.iterator();
            String originURL = null;
            while (it.hasNext()) {
                final CrawledLink next = it.next();
                if (next == link || (next.getDownloadLink() != null && next.getDownloadLink().getUrlProtection() != UrlProtection.UNSET)) {
                    originURL = null;
                    continue;
                }
                final String nextURL = cleanURL(next.getURL());
                if (nextURL != null && !StringUtils.equals(pluginURL, nextURL)) {
                    originURL = nextURL;
                }
            }
            return originURL;
        }
        return null;
    }

    protected void postprocessFinalCrawledLink(CrawledLink link) {
        final DownloadLink downloadLink = link.getDownloadLink();
        if (downloadLink != null) {
            final HashSet<String> knownURLs = new HashSet<String>();
            knownURLs.add(downloadLink.getPluginPatternMatcher());
            if (downloadLink.getContentUrl() != null) {
                if (StringUtils.equals(downloadLink.getPluginPatternMatcher(), downloadLink.getContentUrl())) {
                    downloadLink.setContentUrl(null);
                }
                knownURLs.add(downloadLink.getContentUrl());
            } else if (true || downloadLink.getContainerUrl() == null) {
                /**
                 * remove true in case we don't want a contentURL when containerURL is already set
                 */
                final String contentURL = getContentURL(link);
                if (contentURL != null && knownURLs.add(contentURL)) {
                    downloadLink.setContentUrl(contentURL);
                }
            }
            if (downloadLink.getContainerUrl() != null) {
                /**
                 * containerURLs are only set by crawl or crawlDeeper or manually
                 */
                knownURLs.add(downloadLink.getContainerUrl());
            }
            if (downloadLink.getOriginUrl() != null) {
                knownURLs.add(downloadLink.getOriginUrl());
            } else {
                final String originURL = getOriginURL(link);
                if (originURL != null && knownURLs.add(originURL)) {
                    downloadLink.setOriginUrl(originURL);
                }
            }
            if (StringUtils.equals(downloadLink.getOriginUrl(), downloadLink.getContainerUrl())) {
                downloadLink.setContainerUrl(null);
            }
            if (downloadLink.getReferrerUrl() == null) {
                final String referrerURL = getReferrerUrl(link);
                if (referrerURL != null && knownURLs.add(referrerURL)) {
                    downloadLink.setReferrerUrl(referrerURL);
                }
            }
        }
    }

    public static boolean isTempDecryptedURL(final String url) {
        if (url != null) {
            final String host = Browser.getHost(url, true);
            return StringUtils.containsIgnoreCase(host, "decrypted") || StringUtils.containsIgnoreCase(host, "yt.not.allowed");
        }
        return false;
    }

    public static String cleanURL(String cUrl) {
        final boolean isSupportedProtocol = HTMLParser.isSupportedProtocol(cUrl);
        if (isSupportedProtocol) {
            final String host = Browser.getHost(cUrl, true);
            if (!StringUtils.containsIgnoreCase(host, "decrypted") && !StringUtils.containsIgnoreCase(host, "dummydirect.jdownloader.org") && !StringUtils.containsIgnoreCase(host, "dummycnl.jdownloader.org") && !StringUtils.containsIgnoreCase(host, "yt.not.allowed")) {
                if (cUrl.startsWith("http://") || cUrl.startsWith("https://") || cUrl.startsWith("ftp://") || cUrl.startsWith("file:/")) {
                    return cUrl;
                } else if (cUrl.startsWith("directhttp://")) {
                    return cUrl.substring("directhttp://".length());
                } else if (cUrl.startsWith("httpviajd://")) {
                    return "http://".concat(cUrl.substring("httpviajd://".length()));
                } else if (cUrl.startsWith("httpsviajd://")) {
                    return "https://".concat(cUrl.substring("httpsviajd://".length()));
                } else if (cUrl.startsWith("ftpviajd://")) {
                    return "ftp://".concat(cUrl.substring("ftpviajd://".length()));
                }
            }
        }
        return null;
    }

    protected void handleFinalCrawledLink(CrawledLink link) {
        if (link != null) {
            final CrawledLink origin = link.getOriginLink();
            if (link.getCreated() == -1) {
                link.setCreated(getCreated());
                final CrawledLinkModifier customModifier = link.getCustomCrawledLinkModifier();
                link.setCustomCrawledLinkModifier(null);
                if (customModifier != null) {
                    try {
                        customModifier.modifyCrawledLink(link);
                    } catch (final Throwable e) {
                        LogController.CL().log(e);
                    }
                }
                postprocessFinalCrawledLink(link);
                /* clean up some references */
                link.setBrokenCrawlerHandler(null);
                link.setUnknownHandler(null);
            }
            if (isDoDuplicateFinderFinalCheck()) {
                /* specialHandling: Crypted A - > B - > Final C , and A equals C */
                // if link comes from flashgot, origin might be null
                final boolean specialHandling = origin != null && (origin != link) && (StringUtils.equals(origin.getLinkID(), link.getLinkID()));
                if (!specialHandling) {
                    CrawledLink existing = null;
                    synchronized (duplicateFinderFinal) {
                        final String key = Encoding.urlDecode(link.getLinkID(), false);
                        existing = duplicateFinderFinal.get(key);
                        if (existing == null) {
                            duplicateFinderFinal.put(key, link);
                        }
                    }
                    if (existing != null) {
                        final PluginForHost hPlugin = link.gethPlugin();
                        if (hPlugin == null || hPlugin.onLinkCrawlerDupeFilterEnabled(existing, link)) {
                            onCrawledLinkDuplicate(link, DUPLICATE.FINAL);
                            return;
                        }
                    }
                }
            }
            if (isCrawledLinkFiltered(link) == false) {
                /* link is not filtered, so we can process it normally */
                crawledLinksCounter.incrementAndGet();
                getHandler().handleFinalLink(link);
            }
        }
    }

    protected boolean isCrawledLinkFiltered(CrawledLink link) {
        final LinkCrawler parent = getParent();
        if (parent != null && getFilter() != parent.getFilter()) {
            if (parent.isCrawledLinkFiltered(link)) {
                return true;
            }
        }
        if (getFilter().dropByUrl(link)) {
            filteredLinksCounter.incrementAndGet();
            getHandler().handleFilteredLink(link);
            return true;
        }
        return false;
    }

    protected void onCrawledLinkDuplicate(CrawledLink link, DUPLICATE duplicate) {
    }

    public int getCrawledLinksFoundCounter() {
        return crawledLinksCounter.get();
    }

    public int getFilteredLinksFoundCounter() {
        return filteredLinksCounter.get();
    }

    public int getBrokenLinksFoundCounter() {
        return brokenLinksCounter.get();
    }

    public int getUnhandledLinksFoundCounter() {
        return unhandledLinksCounter.get();
    }

    public int getProcessedLinksCounter() {
        return crawledLinksCounter.get() + filteredLinksCounter.get() + brokenLinksCounter.get() + processedLinksCounter.get();
    }

    public LinkCrawlerDeepInspector defaultDeepInspector() {
        return new LinkCrawlerDeepInspector() {
            @Override
            public List<CrawledLink> deepInspect(LinkCrawler lc, final LinkCrawlerGeneration generation, Browser br, URLConnectionAdapter urlConnection, CrawledLink link) throws Exception {
                final int limit = Math.max(1 * 1024 * 1024, CONFIG.getDeepDecryptLoadLimit());
                if (br != null) {
                    br.setLoadLimit(limit);
                }
                final LinkCrawlerRule rule = link.getMatchingRule();
                if (rule == null) {
                    final boolean hasContentType = urlConnection.getHeaderField(HTTPConstants.HEADER_REQUEST_CONTENT_TYPE) != null;
                    if (urlConnection.getRequest().getLocation() == null && urlConnection.getResponseCode() == 200 && (urlConnection.isContentDisposition() || !StringUtils.containsIgnoreCase(urlConnection.getContentType(), "text") || urlConnection.getCompleteContentLength() > limit)) {
                        if (!hasContentType && !urlConnection.isContentDisposition()) {
                            try {
                                br.followConnection();
                                if (br.containsHTML("<!DOCTYPE html>") || (br.containsHTML("</html") && br.containsHTML("<html"))) {
                                    return null;
                                }
                            } catch (final Throwable e) {
                            }
                        }
                        try {
                            urlConnection.disconnect();
                        } catch (Throwable e) {
                        }
                        final ArrayList<CrawledLink> ret = new ArrayList<CrawledLink>();
                        final CrawledLink direct = createDirectHTTPCrawledLink(urlConnection);
                        if (direct != null) {
                            ret.add(direct);
                        }
                        return ret;
                    }
                }
                br.followConnection();
                return null;
            }
        };
    }

    public LinkCrawlerHandler defaulHandlerFactory() {
        return new LinkCrawlerHandler() {
            public void handleFinalLink(CrawledLink link) {
                synchronized (crawledLinks) {
                    crawledLinks.add(link);
                }
            }

            public void handleFilteredLink(CrawledLink link) {
                synchronized (filteredLinks) {
                    filteredLinks.add(link);
                }
            }

            @Override
            public void handleBrokenLink(CrawledLink link) {
                synchronized (brokenLinks) {
                    brokenLinks.add(link);
                }
            }

            @Override
            public void handleUnHandledLink(CrawledLink link) {
                synchronized (unhandledLinks) {
                    unhandledLinks.add(link);
                }
            }
        };
    }

    public LinkCrawlerFilter defaultFilterFactory() {
        return new LinkCrawlerFilter() {
            public boolean dropByUrl(CrawledLink link) {
                return false;
            }

            public boolean dropByFileProperties(CrawledLink link) {
                return false;
            };
        };
    }

    public void setFilter(LinkCrawlerFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter is null");
        }
        this.filter = filter;
    }

    public void setHandler(LinkCrawlerHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler is null");
        }
        this.handler = handler;
    }

    public void setDeepInspector(LinkCrawlerDeepInspector deepInspector) {
        if (deepInspector == null) {
            throw new IllegalArgumentException("deepInspector is null");
        }
        this.deepInspector = deepInspector;
    }

    public LinkCrawlerFilter getFilter() {
        return filter;
    }

    public LinkCrawlerHandler getHandler() {
        return this.handler;
    }

    public LinkCrawlerDeepInspector getDeepInspector() {
        return this.deepInspector;
    }
}
