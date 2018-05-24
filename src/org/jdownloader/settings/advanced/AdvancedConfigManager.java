package org.jdownloader.settings.advanced;

import java.util.ArrayList;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import jd.controlling.downloadcontroller.DownloadControllerConfig;
import jd.controlling.faviconcontroller.FavIconsConfig;
import jd.controlling.linkchecker.LinkCheckerConfig;
import jd.controlling.linkcrawler.LinkCrawlerConfig;

import org.appwork.storage.config.ConfigInterface;
import org.appwork.storage.config.JsonConfig;
import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DevConfig;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.utils.Application;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogConfig;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.swing.dialog.ExtFileSystemViewSettings;
import org.jdownloader.controlling.ffmpeg.FFmpegSetup;
import org.jdownloader.gui.notify.gui.CFG_BUBBLE;
import org.jdownloader.gui.shortcuts.ShortcutSettings;
import org.jdownloader.logging.LogController;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.plugins.controller.PluginClassLoader;
import org.jdownloader.plugins.controller.PluginClassLoader.PluginClassLoaderChild;
import org.jdownloader.plugins.controller.crawler.CrawlerPluginController;
import org.jdownloader.plugins.controller.crawler.LazyCrawlerPlugin;
import org.jdownloader.plugins.controller.host.HostPluginController;
import org.jdownloader.plugins.controller.host.LazyHostPlugin;
import org.jdownloader.settings.AccountSettings;
import org.jdownloader.settings.RtmpdumpSettings;
import org.jdownloader.settings.SoundSettings;
import org.jdownloader.settings.staticreferences.CFG_API;
import org.jdownloader.settings.staticreferences.CFG_CAPTCHA;
import org.jdownloader.settings.staticreferences.CFG_GENERAL;
import org.jdownloader.settings.staticreferences.CFG_GUI;
import org.jdownloader.settings.staticreferences.CFG_LINKCOLLECTOR;
import org.jdownloader.settings.staticreferences.CFG_LINKFILTER;
import org.jdownloader.settings.staticreferences.CFG_LINKGRABBER;
import org.jdownloader.settings.staticreferences.CFG_MYJD;
import org.jdownloader.settings.staticreferences.CFG_PACKAGIZER;
import org.jdownloader.settings.staticreferences.CFG_RECONNECT;
import org.jdownloader.settings.staticreferences.CFG_SILENTMODE;
import org.jdownloader.updatev2.InternetConnectionSettings;
import org.jdownloader.updatev2.LastChanceSettings;
import org.jdownloader.updatev2.UpdateSettings;
import org.jdownloader.updatev2.gui.LAFOptions;

public class AdvancedConfigManager {
    private static final AdvancedConfigManager INSTANCE = new AdvancedConfigManager();

    public static AdvancedConfigManager getInstance() {
        return AdvancedConfigManager.INSTANCE;
    }

    private final Set<AdvancedConfigEntry>             configInterfaces;
    private final AdvancedConfigEventSender            eventSender;
    private final LogSource                            logger;
    private final WeakHashMap<ConfigInterface, Object> knownInterfaces = new WeakHashMap<ConfigInterface, Object>();

    private AdvancedConfigManager() {
        logger = LogController.getInstance().getLogger(AdvancedConfigManager.class.getName());
        configInterfaces = new CopyOnWriteArraySet<AdvancedConfigEntry>();
        eventSender = new AdvancedConfigEventSender();
        // REFERENCE via static CFG_* classes if possible. this way, we get error messages if there are error in the static refs
        this.register(CFG_GENERAL.CFG);
        register(CFG_LINKFILTER.CFG);
        register(JsonConfig.create(InternetConnectionSettings.PATH, InternetConnectionSettings.class));
        register(CFG_MYJD.CFG);
        register(JsonConfig.create(AccountSettings.class));
        register(CFG_GUI.CFG);
        register(JsonConfig.create(LinkCheckerConfig.class));
        register(JsonConfig.create(LinkCrawlerConfig.class));
        register(JsonConfig.create(DownloadControllerConfig.class));
        register(CFG_LINKGRABBER.CFG);
        register(CFG_LINKCOLLECTOR.CFG);
        register(CFG_RECONNECT.CFG);
        register(CFG_API.CFG);
        register(CFG_PACKAGIZER.CFG);
        register(JsonConfig.create(FFmpegSetup.class));
        register(JsonConfig.create(LogConfig.class));
        register(JsonConfig.create(ShortcutSettings.class));
        register(JsonConfig.create(RtmpdumpSettings.class));
        register(JsonConfig.create(UpdateSettings.class));
        register(JsonConfig.create(LastChanceSettings.class));
        register(JsonConfig.create(ExtFileSystemViewSettings.class));
        register(JsonConfig.create(FavIconsConfig.class));
        register(JsonConfig.create(SoundSettings.class));
        register(CFG_BUBBLE.CFG);
        register(CFG_CAPTCHA.CFG);
        register(CFG_SILENTMODE.CFG);
        if (!org.appwork.utils.Application.isHeadless()) {
            try {
                register(LAFOptions.getInstance().getCfg());
            } catch (Exception e) {
                // we need to take care that LookAndFeelController has been initialized before we init the advancedconfigManager!
                logger.log(e);
            }
        }
    }

    public AdvancedConfigEventSender getEventSender() {
        return eventSender;
    }

    public void register(ConfigInterface cf) {
        synchronized (this) {
            if (knownInterfaces.put(cf, this) != null) {
                return;
            }
        }
        logger.info("Register " + cf._getStorageHandler().getConfigInterface());
        for (KeyHandler m : cf._getStorageHandler().getKeyHandler()) {
            if (m.getAnnotation(AboutConfig.class) != null && (m.getAnnotation(DevConfig.class) == null || !Application.isJared(null))) {
                if (m.getSetMethod() == null) {
                    throw new RuntimeException("Setter for " + m.getKey() + " missing");
                } else if (m.getGetMethod() == null) {
                    throw new RuntimeException("Getter for " + m.getKey() + " missing");
                } else {
                    configInterfaces.add(new AdvancedConfigEntry(cf, m));
                }
            }
        }
        eventSender.fireEvent(new AdvancedConfigEvent(this, AdvancedConfigEvent.Types.UPDATED, cf));
    }

    @SuppressWarnings("unchecked")
    public java.util.List<AdvancedConfigEntry> listPluginsInterfaces() {
        final ArrayList<AdvancedConfigEntry> ret = new ArrayList<AdvancedConfigEntry>();
        final PluginClassLoaderChild pluginClassLoader = PluginClassLoader.getInstance().getChild();
        for (final LazyHostPlugin hplg : HostPluginController.getInstance().list()) {
            final String ifName = hplg.getConfigInterface();
            if (StringUtils.isNotEmpty(ifName)) {
                try {
                    final PluginConfigInterface cf = PluginJsonConfig.get((Class<PluginConfigInterface>) pluginClassLoader.loadClass(ifName));
                    for (KeyHandler m : cf._getStorageHandler().getKeyHandler()) {
                        if (m.getAnnotation(AboutConfig.class) != null && (m.getAnnotation(DevConfig.class) == null || !Application.isJared(null))) {
                            if (m.getSetMethod() == null) {
                                throw new RuntimeException("Setter for " + m.getKey() + " missing");
                            } else if (m.getGetMethod() == null) {
                                throw new RuntimeException("Getter for " + m.getKey() + " missing");
                            } else {
                                ret.add(new AdvancedConfigEntry(cf, m));
                            }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                System.out.println(ifName);
            }
        }
        for (final LazyCrawlerPlugin cplg : CrawlerPluginController.getInstance().list()) {
            final String ifName = cplg.getConfigInterface();
            if (StringUtils.isNotEmpty(ifName)) {
                System.out.println(ifName);
            }
        }
        return ret;
    }

    public java.util.List<AdvancedConfigEntry> list() {
        ArrayList<AdvancedConfigEntry> ret = new ArrayList<AdvancedConfigEntry>(configInterfaces);
        ret.addAll(listPluginsInterfaces());
        return ret;
    }
}
