package org.jdownloader.plugins.controller.host;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jd.SecondLevelLaunch;
import jd.controlling.downloadcontroller.DownloadController;
import jd.controlling.linkcollector.LinkCollector;
import jd.nutils.Formatter;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.PluginForHost;

import org.appwork.exceptions.WTFException;
import org.appwork.shutdown.ShutdownController;
import org.appwork.shutdown.ShutdownEvent;
import org.appwork.shutdown.ShutdownRequest;
import org.appwork.storage.config.ConfigInterface;
import org.appwork.utils.Application;
import org.appwork.utils.ModifyLock;
import org.appwork.utils.logging2.LogSource;
import org.jdownloader.controlling.FileCreationManager;
import org.jdownloader.logging.LogController;
import org.jdownloader.plugins.controller.PluginClassLoader.PluginClassLoaderChild;
import org.jdownloader.plugins.controller.PluginController;
import org.jdownloader.plugins.controller.PluginInfo;
import org.jdownloader.plugins.controller.UpdateRequiredClassNotFoundException;

public class HostPluginController extends PluginController<PluginForHost> {

    public static final String                TMP_INVALIDPLUGINS = "invalidplugins";
    private static final HostPluginController INSTANCE           = new HostPluginController();

    /**
     * get the only existing instance of HostPluginController. This is a singleton
     * 
     * @return
     */
    public static HostPluginController getInstance() {
        return HostPluginController.INSTANCE;
    }

    private volatile Map<String, LazyHostPlugin> list;
    private volatile List<LazyHostPlugin>        lastKnownPlugins = null;
    private final AtomicLong                     lastModification = new AtomicLong(-1l);
    private volatile LazyHostPlugin              fallBackPlugin   = null;
    private final ModifyLock                     lock             = new ModifyLock();

    public LazyHostPlugin getFallBackPlugin() {
        ensureLoaded();
        return fallBackPlugin;
    }

    private String getCache() {
        return "hosterCache";
    }

    /**
     * Create a new instance of HostPluginController. This is a singleton class. Access the only existing instance by using
     * {@link #getInstance()}.
     */
    private HostPluginController() {
        this.list = null;
        try {
            // load config
            Class.forName("org.jdownloader.container.Config");
        } catch (Throwable e) {
        }
        ShutdownController.getInstance().addShutdownEvent(new ShutdownEvent() {

            @Override
            public void onShutdown(ShutdownRequest shutdownRequest) {
                save(lastKnownPlugins, new AtomicLong(lastModification.get()));
            }
        });
    }

    public synchronized Map<String, LazyHostPlugin> init() {
        final LogSource logger = LogController.CL(false);
        logger.info("HostPluginController: init");
        logger.setAllowTimeoutFlush(false);
        logger.setAutoFlushOnThrowable(true);
        LogController.setRebirthLogger(logger);
        final long completeTimeStamp = System.currentTimeMillis();
        try {
            /* try to load from cache */
            long timeStamp = System.currentTimeMillis();
            if (lastKnownPlugins == null || lastModification.get() <= 0) {
                try {
                    lastKnownPlugins = loadFromCache(lastModification);
                } catch (Throwable e) {
                    if (lastModification != null) {
                        lastModification.set(-1l);
                    }
                    logger.log(e);
                    logger.severe("@HostPluginController: cache failed!");
                } finally {
                    if (lastKnownPlugins != null && lastKnownPlugins.size() > 0) {
                        logger.info("@HostPluginController: loadFromCache took " + (System.currentTimeMillis() - timeStamp) + "ms for " + lastKnownPlugins.size());
                    }
                }
            }
            List<LazyHostPlugin> plugins = null;
            timeStamp = System.currentTimeMillis();
            try {
                /* do a fresh scan */
                plugins = update(logger, lastKnownPlugins, lastModification);
            } catch (Throwable e) {
                if (lastModification != null) {
                    lastModification.set(-1l);
                }
                logger.log(e);
                logger.severe("@HostPluginController: update failed!");
            } finally {
                if (plugins != null && plugins.size() > 0) {
                    logger.info("@HostPluginController: update took " + (System.currentTimeMillis() - timeStamp) + "ms for " + plugins.size());
                }
            }
            if (plugins == null || plugins.size() == 0) {
                if (plugins == null) {
                    plugins = new ArrayList<LazyHostPlugin>();
                }
                logger.severe("@HostPluginController: WTF, no plugins!");
            }
            lastKnownPlugins = new ArrayList<LazyHostPlugin>(plugins);
            timeStamp = System.currentTimeMillis();
            try {
                if (false) {
                    Collections.sort(plugins, new Comparator<LazyHostPlugin>() {
                        public final boolean smallestCharacter(char a, char b) {
                            return Character.toLowerCase(a) < Character.toLowerCase(b);
                        }

                        public int compare(LazyHostPlugin o1, LazyHostPlugin o2) {
                            char a = o1.getDisplayName().charAt(0);
                            char b = o2.getDisplayName().charAt(0);
                            if (a == b) {
                                return 0;
                            }
                            return smallestCharacter(a, b) ? -1 : 1;
                        }
                    });
                } else {
                    Collections.sort(plugins, new Comparator<LazyHostPlugin>() {

                        public int compare(LazyHostPlugin o1, LazyHostPlugin o2) {
                            return o1.getDisplayName().compareTo(o2.getDisplayName());
                        }
                    });
                }
            } catch (final Throwable e) {
                logger.log(e);
                logger.severe("@HostPluginController: sort failed!");
            } finally {
                logger.info("@HostPluginController: sort took " + (System.currentTimeMillis() - timeStamp) + "ms for " + plugins.size());
            }
            timeStamp = System.currentTimeMillis();
            final LinkedHashMap<String, LazyHostPlugin> retMap = new LinkedHashMap<String, LazyHostPlugin>();
            LazyHostPlugin fallBackPlugin = null;
            for (final LazyHostPlugin plugin : plugins) {
                plugin.setPluginClass(null);
                plugin.setClassLoader(null);
                if (fallBackPlugin == null && "UpdateRequired".equalsIgnoreCase(plugin.getDisplayName())) {
                    fallBackPlugin = plugin;
                    this.fallBackPlugin = plugin;
                    continue;
                }
                final String pluginID = plugin.getDisplayName().toLowerCase(Locale.ENGLISH);
                final LazyHostPlugin existingPlugin = retMap.put(pluginID, plugin);
                if (existingPlugin != null) {
                    if (existingPlugin.getLazyPluginClass().getInterfaceVersion() > plugin.getLazyPluginClass().getInterfaceVersion()) {
                        retMap.put(pluginID, existingPlugin);
                        // logger.finest("@HostPlugin keep:" + existingPlugin.getLazyPluginClass() + ":" + existingPlugin.getVersion() +
                        // " instead " + plugin.getLazyPluginClass() + ":" + plugin.getVersion());
                    } else {
                        // logger.finest("@HostPlugin replaced:" + existingPlugin.getLazyPluginClass() + ":" + existingPlugin.getVersion() +
                        // " with " + plugin.getLazyPluginClass() + ":" + plugin.getVersion());
                    }
                }
            }
            logger.info("@HostPluginController: mapping took " + (System.currentTimeMillis() - timeStamp) + "ms for " + plugins.size());
            list = retMap;
        } finally {
            final Map<String, LazyHostPlugin> llist = list;
            if (llist != null) {
                logger.info("@HostPluginController: init took " + (System.currentTimeMillis() - completeTimeStamp) + "ms for " + llist.size());
            } else {
                logger.info("@HostPluginController: init took " + (System.currentTimeMillis() - completeTimeStamp));
            }
            LogController.setRebirthLogger(null);
            validateCache();
            if (lastKnownPlugins != null) {
                final AtomicLong lastModification = new AtomicLong(this.lastModification.get());
                final Thread saveThread = new Thread("@HostPluginController:save") {
                    public void run() {
                        save(lastKnownPlugins, lastModification);
                    };
                };
                saveThread.setDaemon(true);
                saveThread.start();
            }
            logger.close();
            System.gc();
        }
        SecondLevelLaunch.INIT_COMPLETE.executeWhenReached(new Runnable() {

            @Override
            public void run() {
                LinkCollector.getInstance().checkPluginUpdates();
                DownloadController.getInstance().checkPluginUpdates();
            }
        });
        return list;
    }

    private List<LazyHostPlugin> loadFromCache(final AtomicLong lastFolderModification) throws IOException {
        final boolean readL = lock.readLock();
        try {
            return LazyHostPluginCache.read(Application.getTempResource(getCache()), lastFolderModification);
        } finally {
            lock.readUnlock(readL);
        }
    }

    @Override
    protected long[] getInfos(Class<PluginForHost> clazz) {
        final HostPlugin infos = clazz.getAnnotation(HostPlugin.class);
        return new long[] { infos.interfaceVersion(), Formatter.getRevision(infos.revision()) };
    }

    private List<LazyHostPlugin> update(LogSource logger, final List<LazyHostPlugin> updateCache, final AtomicLong lastFolderModification) throws Exception {
        final List<LazyHostPlugin> retList = new ArrayList<LazyHostPlugin>();
        for (PluginInfo<PluginForHost> pluginInfo : scan(logger, "jd/plugins/hoster", updateCache, lastFolderModification)) {
            if (pluginInfo.getLazyPlugin() != null) {
                final LazyHostPlugin plugin = (LazyHostPlugin) pluginInfo.getLazyPlugin();
                retList.add(plugin);
                // logger.finer("@HostPlugin ok(cached):" + plugin.getClassName() + " " + plugin.getDisplayName() + " " +
                // plugin.getVersion());
            } else {
                final String simpleName = new String(pluginInfo.getClazz().getSimpleName());
                final HostPlugin a = pluginInfo.getClazz().getAnnotation(HostPlugin.class);
                if (a != null) {
                    try {
                        final long revision = pluginInfo.getLazyPluginClass().getRevision();
                        String[] names = a.names();
                        String[] patterns = a.urls();
                        int[] flags = a.flags();
                        if (names.length == 0) {
                            /* create multiple hoster plugins from one source */
                            patterns = (String[]) pluginInfo.getClazz().getDeclaredMethod("getAnnotationUrls", new Class[] {}).invoke(null, new Object[] {});
                            names = (String[]) pluginInfo.getClazz().getDeclaredMethod("getAnnotationNames", new Class[] {}).invoke(null, new Object[] {});
                            flags = (int[]) pluginInfo.getClazz().getDeclaredMethod("getAnnotationFlags", new Class[] {}).invoke(null, new Object[] {});
                        }
                        if (patterns.length != names.length) {
                            //
                            throw new WTFException("names.length != patterns.length");
                        }
                        if (flags.length != names.length && a.interfaceVersion() == 2) {
                            /* interfaceVersion 2 is for Stable/Nightly */
                            logger.log((new WTFException("PLUGIN STABLE ISSUE!! names.length(" + names.length + ")!= flags.length(" + flags.length + ")->" + simpleName)));
                        }
                        if (names.length == 0) {
                            //
                            throw new WTFException("names.length=0");
                        }
                        final PluginClassLoaderChild classLoader = (PluginClassLoaderChild) pluginInfo.getClazz().getClassLoader();
                        /* during init we dont want dummy libs being created */
                        classLoader.setCreateDummyLibs(false);
                        for (int i = 0; i < names.length; i++) {
                            LazyHostPlugin lazyHostPlugin = null;
                            try {
                                lazyHostPlugin = new LazyHostPlugin(pluginInfo.getLazyPluginClass(), new String(patterns[i]), new String(names[i]), pluginInfo.getClazz(), classLoader);
                                if (list != null) {
                                    final LazyHostPlugin previousLazyHostPlugin = list.get(lazyHostPlugin.getDisplayName());
                                    if (previousLazyHostPlugin != null) {
                                        lazyHostPlugin.setPluginUsage(previousLazyHostPlugin.getPluginUsage());
                                    }
                                }
                                try {
                                    /* check for stable compatibility */
                                    classLoader.setPluginClass(pluginInfo.getClazz().getName());
                                    classLoader.setCheckStableCompatibility(pluginInfo.getLazyPluginClass().getInterfaceVersion() == 2);
                                    final PluginForHost plg = lazyHostPlugin.newInstance(classLoader);
                                    /* set configinterface */
                                    final Class<? extends ConfigInterface> configInterface = plg.getConfigInterface();
                                    if (configInterface != null) {
                                        lazyHostPlugin.setConfigInterface(new String(configInterface.getName()));
                                    } else {
                                        lazyHostPlugin.setConfigInterface(null);
                                    }
                                    /* set premium */
                                    if (plg.isPremiumEnabled()) {
                                        lazyHostPlugin.setPremium(true);
                                        /* set premiumUrl */
                                        final String purl = plg.getBuyPremiumUrl();
                                        if (purl != null) {
                                            lazyHostPlugin.setPremiumUrl(new String(purl));
                                        }
                                    } else {
                                        lazyHostPlugin.setPremium(false);
                                    }
                                    /* set hasConfig */
                                    lazyHostPlugin.setHasConfig(plg.hasConfig());
                                    /* set hasAccountRewrite */
                                    try {
                                        lazyHostPlugin.setHasAccountRewrite(PluginForHost.implementsRewriteHost(plg, Account.class));
                                    } catch (Throwable e) {
                                        logger.log(e);
                                        lazyHostPlugin.setHasAccountRewrite(false);
                                    }

                                    try {
                                        lazyHostPlugin.setHasAllowHandle(PluginForHost.implementsAllowHandle(plg));
                                    } catch (Throwable e) {
                                        logger.log(e);
                                        lazyHostPlugin.setHasAllowHandle(false);
                                    }

                                    try {
                                        lazyHostPlugin.setHasRewrite(PluginForHost.implementsRewriteHost(plg, String.class));
                                    } catch (Throwable e) {
                                        logger.log(e);
                                        lazyHostPlugin.setHasRewrite(false);
                                    }

                                    /* set hasLinkRewrite */
                                    try {
                                        lazyHostPlugin.setHasLinkRewrite(PluginForHost.implementsRewriteHost(plg, DownloadLink.class));
                                    } catch (Throwable e) {
                                        logger.log(e);
                                        lazyHostPlugin.setHasLinkRewrite(false);
                                    }
                                } catch (Throwable e) {
                                    if (e instanceof UpdateRequiredClassNotFoundException) {
                                        logger.log(e);
                                        logger.finest("@HostPlugin incomplete:" + simpleName + " " + new String(names[i]) + " " + e.getMessage() + " " + revision);
                                    } else {
                                        throw e;
                                    }
                                }
                                if (lazyHostPlugin != null) {
                                    retList.add(lazyHostPlugin);
                                    // logger.finer("@HostPlugin ok:" + simpleName + " " + new String(names[i]) + " " + revision);
                                }
                            } catch (Throwable e) {
                                logger.log(e);
                                logger.severe("@HostPlugin failed:" + simpleName + " " + new String(names[i]) + " " + revision);
                            } finally {
                                /* now the pluginClassLoad may create dummy libraries */
                                if (lazyHostPlugin != null) {
                                    lazyHostPlugin.setClassLoader(null);
                                    lazyHostPlugin.setPluginClass(null);
                                }
                            }
                        }
                    } catch (final Throwable e) {
                        logger.severe("@HostPlugin failed:" + simpleName);
                        logger.log(e);
                    }
                } else {
                    logger.severe("@HostPlugin missing:" + simpleName);
                }
            }
        }
        return retList;
    }

    private final AtomicBoolean cacheInvalidated = new AtomicBoolean(false);

    public boolean isCacheInvalidated() {
        return cacheInvalidated.get();
    }

    public void invalidateCache() {
        cacheInvalidated.set(true);
    }

    protected void validateCache() {
        cacheInvalidated.set(false);
    }

    private void save(List<LazyHostPlugin> save, final AtomicLong lastFolderModification) {
        lock.writeLock();
        final File cache = Application.getTempResource(getCache());
        try {
            LazyHostPluginCache.write(save, cache, lastFolderModification);
        } catch (final IOException e) {
            e.printStackTrace();
            cache.delete();
        } finally {
            lock.writeUnlock();
            FileCreationManager.getInstance().delete(Application.getTempResource(TMP_INVALIDPLUGINS), null);
        }
    }

    public Collection<LazyHostPlugin> list() {
        return ensureLoaded().values();
    }

    public Map<String, LazyHostPlugin> ensureLoaded() {
        Map<String, LazyHostPlugin> localList = list;
        if (localList != null && isCacheInvalidated() == false) {
            return localList;
        }
        synchronized (this) {
            localList = list;
            if (localList != null && isCacheInvalidated() == false) {
                return localList;
            }
            return init();
        }
    }

    public LazyHostPlugin get(String displayName) {
        if (displayName != null) {
            final LazyHostPlugin ret = ensureLoaded().get(displayName.toLowerCase(Locale.ENGLISH));
            if (ret != null) {
                return ret;
            } else {
                if ("UpdateRequired".equalsIgnoreCase(displayName)) {
                    return fallBackPlugin;
                }
            }
        }
        return null;
    }

    public void invalidateCacheIfRequired() {
        if (Application.getTempResource(TMP_INVALIDPLUGINS).exists()) {
            invalidateCache();
        }
    }

}
