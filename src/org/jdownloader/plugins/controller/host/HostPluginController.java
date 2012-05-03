package org.jdownloader.plugins.controller.host;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import jd.JDInitFlags;
import jd.nutils.Formatter;
import jd.plugins.HostPlugin;
import jd.plugins.PluginForHost;

import org.appwork.exceptions.WTFException;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.Application;
import org.appwork.utils.logging.Log;
import org.jdownloader.plugins.controller.PluginClassLoader;
import org.jdownloader.plugins.controller.PluginClassLoader.PluginClassLoaderChild;
import org.jdownloader.plugins.controller.PluginController;
import org.jdownloader.plugins.controller.PluginInfo;

public class HostPluginController extends PluginController<PluginForHost> {

    private static final HostPluginController INSTANCE = new HostPluginController();

    /**
     * get the only existing instance of HostPluginController. This is a singleton
     * 
     * @return
     */
    public static HostPluginController getInstance() {
        return HostPluginController.INSTANCE;
    }

    private List<LazyHostPlugin> list;

    private String getCache() {
        return "tmp/hosts.json";
    }

    /**
     * Create a new instance of HostPluginController. This is a singleton class. Access the only existing instance by using {@link #getInstance()}.
     */
    private HostPluginController() {
        this.list = null;
    }

    public void init(boolean noCache) {
        List<LazyHostPlugin> plugins = new ArrayList<LazyHostPlugin>();
        final long t = System.currentTimeMillis();
        try {
            if (noCache) {
                try {
                    /* do a fresh scan */
                    plugins = update();
                } catch (Throwable e) {
                    Log.L.severe("@HostPluginController: update failed!");
                    Log.exception(e);
                }
            } else {
                /* try to load from cache */
                try {
                    plugins = loadFromCache();
                } catch (Throwable e) {
                    Log.L.severe("@HostPluginController: cache failed!");
                    Log.exception(e);
                }
                if (plugins.size() == 0) {
                    try {
                        /* do a fresh scan */
                        plugins = update();
                    } catch (Throwable e) {
                        Log.L.severe("@HostPluginController: update failed!");
                        Log.exception(e);
                    }
                }
            }
        } finally {
            Log.L.info("@HostPluginController: init " + (System.currentTimeMillis() - t) + " :" + plugins.size());
        }
        if (plugins.size() == 0) {
            Log.L.severe("@HostPluginController: WTF, no plugins!");
        }
        try {
            Collections.sort(plugins, new Comparator<LazyHostPlugin>() {

                public int compare(LazyHostPlugin o1, LazyHostPlugin o2) {
                    return o1.getDisplayName().compareTo(o2.getDisplayName());
                }
            });
        } catch (final Throwable e) {
            Log.exception(e);
        }
        list = plugins;
        System.gc();
    }

    private List<LazyHostPlugin> loadFromCache() {
        ArrayList<AbstractHostPlugin> l = JSonStorage.restoreFrom(Application.getResource(getCache()), true, null, new TypeRef<ArrayList<AbstractHostPlugin>>() {
        }, new ArrayList<AbstractHostPlugin>());
        List<LazyHostPlugin> ret = new ArrayList<LazyHostPlugin>(l.size());
        PluginClassLoaderChild classLoader = PluginClassLoader.getInstance().getChild();
        /* use this classLoader for all cached plugins to load */
        for (AbstractHostPlugin ap : l) {
            ret.add(new LazyHostPlugin(ap, null, classLoader));
        }
        return ret;
    }

    private List<LazyHostPlugin> update() throws MalformedURLException {
        HashMap<String, AbstractHostPlugin> ret = new HashMap<String, AbstractHostPlugin>();
        HashMap<String, LazyHostPlugin> ret2 = new HashMap<String, LazyHostPlugin>();
        PluginClassLoaderChild classLoader = PluginClassLoader.getInstance().getChild();
        for (PluginInfo<PluginForHost> c : scan("jd/plugins/hoster")) {
            String simpleName = c.getClazz().getSimpleName();
            HostPlugin a = c.getClazz().getAnnotation(HostPlugin.class);
            if (a != null) {
                try {
                    long revision = Formatter.getRevision(a.revision());
                    String[] names = a.names();
                    String[] patterns = a.urls();
                    if (names.length == 0) {
                        /* create multiple hoster plugins from one source */
                        patterns = (String[]) c.getClazz().getDeclaredMethod("getAnnotationUrls", new Class[] {}).invoke(null, new Object[] {});
                        names = (String[]) c.getClazz().getDeclaredMethod("getAnnotationNames", new Class[] {}).invoke(null, new Object[] {});
                    }
                    if (patterns.length != names.length) throw new WTFException("names.length != patterns.length");
                    if (names.length == 0) { throw new WTFException("names.length=0"); }
                    for (int i = 0; i < names.length; i++) {
                        try {
                            String displayName = new String(names[i]);
                            AbstractHostPlugin existingPlugin = ret.get(displayName);
                            if (existingPlugin != null && existingPlugin.getInterfaceVersion() > a.interfaceVersion()) {
                                /* we already loaded a plugin with higher interfaceVersion, so skip older one */
                                continue;
                            }
                            AbstractHostPlugin ap = new AbstractHostPlugin(new String(c.getClazz().getSimpleName()));
                            ap.setDisplayName(displayName);
                            ap.setPattern(new String(patterns[i]));
                            ap.setVersion(revision);
                            ap.setInterfaceVersion(a.interfaceVersion());
                            LazyHostPlugin l = new LazyHostPlugin(ap, null, classLoader);
                            PluginForHost plg = l.newInstance();
                            ap.setPremium(plg.isPremiumEnabled());
                            String purl = plg.getBuyPremiumUrl();
                            if (purl != null) purl = new String(purl);
                            ap.setPremiumUrl(purl);
                            ap.setHasConfig(plg.hasConfig());
                            l.setHasConfig(plg.hasConfig());
                            l.setPremium(ap.isPremium());
                            l.setPremiumUrl(purl);
                            existingPlugin = ret.put(ap.getDisplayName(), ap);
                            if (existingPlugin != null) {
                                Log.L.finest("@HostPlugin replaced:" + simpleName + " " + names[i]);
                            }
                            ret2.put(ap.getDisplayName(), l);
                            Log.L.finer("@HostPlugin ok:" + simpleName + " " + names[i]);
                        } catch (Throwable e) {
                            Log.L.severe("@HostPlugin failed:" + simpleName + " " + names[i]);
                            Log.exception(e);
                        }
                    }
                } catch (final Throwable e) {
                    Log.L.severe("@HostPlugin failed:" + simpleName);
                    Log.exception(e);
                }
            } else {
                Log.L.severe("@HostPlugin missing:" + simpleName);
            }
        }
        save(new ArrayList<AbstractHostPlugin>(ret.values()));
        return new ArrayList<LazyHostPlugin>(ret2.values());
    }

    private void save(List<AbstractHostPlugin> save) {
        JSonStorage.saveTo(Application.getResource(getCache()), save);
    }

    public List<LazyHostPlugin> list() {
        ensureLoaded();
        return list;
    }

    public void setList(List<LazyHostPlugin> list) {
        if (list == null) return;
        try {
            Collections.sort(list, new Comparator<LazyHostPlugin>() {

                public int compare(LazyHostPlugin o1, LazyHostPlugin o2) {
                    return o1.getDisplayName().compareTo(o2.getDisplayName());
                }
            });
        } catch (final Throwable e) {
            Log.exception(e);
        }
        this.list = list;
    }

    public void ensureLoaded() {
        if (list != null) return;
        synchronized (this) {
            if (list != null) return;
            init(JDInitFlags.REFRESH_CACHE);
        }
    }

    public LazyHostPlugin get(String displayName) {
        ensureLoaded();
        List<LazyHostPlugin> llist = list;
        for (LazyHostPlugin p : llist) {
            if (p.getDisplayName().equalsIgnoreCase(displayName)) return p;
        }
        return null;
    }

}
