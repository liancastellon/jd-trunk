package jd.controlling;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jd.controlling.ClipboardMonitoring.ClipboardChangeDetector.CHANGE_FLAG;
import jd.controlling.linkcollector.LinkCollectingJob;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcollector.LinkOrigin;
import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledLinkModifier;
import jd.parser.html.HTMLParser;

import org.appwork.exceptions.WTFException;
import org.appwork.utils.IO;
import org.appwork.utils.IO.BOM;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.os.CrossSystem.OperatingSystem;
import org.jdownloader.controlling.PasswordUtils;
import org.jdownloader.gui.views.components.packagetable.dragdrop.PackageControllerTableTransferable;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.GraphicalUserInterfaceSettings;

import sun.awt.datatransfer.SunClipboard;

public class ClipboardMonitoring {
    public static class HTMLFragment {
        private final String sourceURL;

        public final String getSourceURL() {
            return sourceURL;
        }

        public final String getFragment() {
            return fragment;
        }

        private final String fragment;

        private HTMLFragment(final String sourceURL, final String fragment) {
            this.sourceURL = sourceURL;
            this.fragment = fragment;
        }
    }

    private static class ClipboardHash {
        private final int hash;
        private final int length;

        private ClipboardHash(int hash, int length) {
            this.hash = hash;
            this.length = length;
        }

        private int getClipboardHash() {
            return hash;
        }

        private int getClipboardLength() {
            return length;
        }

        private ClipboardHash(String string) {
            if (string == null) {
                this.hash = 0;
                this.length = -1;
            } else {
                this.hash = string.hashCode();
                this.length = string.length();
            }
        }

        @Override
        public String toString() {
            return "ClipboardHash:" + getClipboardHash() + "|ClipboardLength:" + getClipboardLength();
        }

        private boolean equals(final HTMLFragment fragment) {
            if (fragment != null) {
                return equals(fragment.getFragment());
            } else {
                return equals((String) null);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            } else if (obj == this) {
                return true;
            } else if (obj instanceof String) {
                return equals((String) obj);
            } else if (obj instanceof HTMLFragment) {
                return equals((HTMLFragment) obj);
            } else if (obj instanceof ClipboardHash) {
                final ClipboardHash o = (ClipboardHash) obj;
                return length == o.length && hash == o.hash;
            }
            return false;
        }

        private boolean equals(String string) {
            if (string == null) {
                return length == -1 && hash == 0;
            } else {
                return length == string.length() && hash == string.hashCode();
            }
        }
    }

    private static class WindowsClipboardHack {
        Method                  openClipboard    = null;
        Method                  closeClipboard   = null;
        Method                  getClipboardData = null;
        long                    cf_html          = -1;
        final Clipboard         clipboard;
        private final LogSource logger;

        private WindowsClipboardHack(Clipboard clipboard, final LogSource logger) throws Exception {
            this.clipboard = clipboard;
            this.logger = logger;
            try {
                final Field cf_html_field = Class.forName("sun.awt.windows.WDataTransferer").getDeclaredField("CF_HTML");
                if (cf_html_field != null) {
                    cf_html_field.setAccessible(true);
                    cf_html = (Long) cf_html_field.get(null);
                    openClipboard = clipboard.getClass().getDeclaredMethod("openClipboard", new Class[] { SunClipboard.class });
                    openClipboard.setAccessible(true);
                    closeClipboard = clipboard.getClass().getDeclaredMethod("closeClipboard", new Class[] {});
                    closeClipboard.setAccessible(true);
                    getClipboardData = clipboard.getClass().getDeclaredMethod("getClipboardData", new Class[] { long.class });
                    getClipboardData.setAccessible(true);
                } else {
                    throw new WTFException("CF_HTML not available");
                }
            } catch (final Throwable e) {
                throw new Exception(e);
            }
        }

        private String getURLFromCF_HTML() {
            try {
                try {
                    openClipboard.invoke(clipboard, new Object[] { null });
                    final String sstr = new String((byte[]) getClipboardData.invoke(clipboard, new Object[] { cf_html }), "UTF-8");
                    return new Regex(sstr, "SourceURL:([^\r\n]*)").getMatch(0);
                } finally {
                    closeClipboard.invoke(clipboard, new Object[] {});
                }
            } catch (final InvocationTargetException ignore) {
            } catch (final IllegalStateException ignore) {
            } catch (final IOException ignore) {
            } catch (final Throwable e) {
                logger.log(e);
            }
            return null;
        }
    }

    protected static class ClipboardChangeDetector {
        protected static enum CHANGE_FLAG {
            DETECTED,
            INTERRUPTED,
            TIMEOUT,
            SKIP,
            BLACKLISTED,
            FALSE
        }

        protected volatile int                       waitTimeout;
        private final AtomicReference<AtomicBoolean> skipChangeFlag;

        protected ClipboardChangeDetector(final AtomicReference<AtomicBoolean> skipChangeFlag) {
            this.skipChangeFlag = skipChangeFlag;
            waitTimeout = getMinWaitTimeout();
        }

        protected boolean isSkipFlagSet() {
            final AtomicBoolean flag = skipChangeFlag.get();
            return flag != null && flag.get();
        }

        protected CHANGE_FLAG waitForClipboardChanges() {
            while (true) {
                final CHANGE_FLAG ret = hasChanges();
                switch (ret) {
                case DETECTED:
                case TIMEOUT:
                case INTERRUPTED:
                    return ret;
                default:
                    break;
                }
            }
        }

        protected int getCurrentWaitTimeout() {
            return waitTimeout;
        }

        protected int getMinWaitTimeout() {
            return 200;
        }

        protected int getMaxWaitTimeout() {
            return 1000;
        }

        protected int getWaitTimeoutInc() {
            return 200;
        }

        protected void reset() {
        }

        protected CHANGE_FLAG hasChanges() {
            final CHANGE_FLAG ret;
            if (isSkipFlagSet()) {
                waitTimeout = getMinWaitTimeout();
                ret = CHANGE_FLAG.SKIP;
            } else {
                waitTimeout = Math.min(waitTimeout + getWaitTimeoutInc(), getMaxWaitTimeout());
                ret = CHANGE_FLAG.TIMEOUT;
            }
            try {
                synchronized (this) {
                    this.wait(getCurrentWaitTimeout());
                }
                return ret;
            } catch (InterruptedException e) {
                return CHANGE_FLAG.INTERRUPTED;
            }
        }

        protected void slowDown(Throwable e) {
            waitTimeout = 5000;
        }

        protected void restart() {
            waitTimeout = getMinWaitTimeout();
        }
    }

    public static class ClipboardContent {
        private final String content;
        private final String browserURL;

        public String getContent() {
            return content;
        }

        public String getBrowserURL() {
            return browserURL;
        }

        private ClipboardContent(String content, String browserURL) {
            this.content = content;
            this.browserURL = browserURL;
        }
    }

    private static final ClipboardMonitoring                                                 INSTANCE            = new ClipboardMonitoring();
    private static final DataFlavor                                                          URLFLAVOR;
    private static final DataFlavor                                                          URILISTFLAVOR;
    static {
        DataFlavor ret = null;
        try {
            ret = new DataFlavor("application/x-java-url; class=java.net.URL");
        } catch (final Throwable e) {
            LogController.CL().info("urlFlavor not supported");
        } finally {
            URLFLAVOR = ret;
        }
        try {
            ret = null;
            ret = new DataFlavor("text/uri-list; class=java.lang.String");
        } catch (final Throwable e) {
            LogController.CL().info("uriListFlavor not supported");
        } finally {
            URILISTFLAVOR = ret;
        }
    }
    private final AtomicReference<Object>                                                    monitoringThread    = new AtomicReference<Object>(null);
    private final Clipboard                                                                  clipboard;
    private static final AtomicReference<GraphicalUserInterfaceSettings.CLIPBOARD_SKIP_MODE> CLIPBOARD_SKIP_MODE = new AtomicReference<GraphicalUserInterfaceSettings.CLIPBOARD_SKIP_MODE>(GraphicalUserInterfaceSettings.CLIPBOARD_SKIP_MODE.ON_STARTUP);
    private final WindowsClipboardHack                                                       windowsClipboardHack;
    private final AtomicReference<AtomicBoolean>                                             skipChangeDetection = new AtomicReference<AtomicBoolean>(null);
    private final static AtomicBoolean                                                       HTML_FLAVOR_ALLOWED = new AtomicBoolean(true);
    private final ClipboardChangeDetector                                                    clipboardChangeDetector;
    private final LogSource                                                                  logger;

    public static boolean isHtmlFlavorAllowed() {
        return HTML_FLAVOR_ALLOWED.get();
    }

    public static void setHtmlFlavorAllowed(final boolean htmlFlavor) {
        ClipboardMonitoring.HTML_FLAVOR_ALLOWED.set(htmlFlavor);
    }

    public static void setClipboardSkipMode(GraphicalUserInterfaceSettings.CLIPBOARD_SKIP_MODE mode) {
        if (mode == null) {
            CLIPBOARD_SKIP_MODE.set(GraphicalUserInterfaceSettings.CLIPBOARD_SKIP_MODE.ON_STARTUP);
        } else {
            CLIPBOARD_SKIP_MODE.set(mode);
        }
    }

    public static enum IGNORE {
        PACKAGECONTROLLER,
        LOCAL,
        JVM,
        EMPTY,
        NO
    }

    private IGNORE ignoreTransferable(Transferable transferable, DataFlavor[] dataFlavors) {
        if (transferable == null && (dataFlavors == null || dataFlavors.length == 0)) {
            return IGNORE.EMPTY;
        }
        if (isDataFlavorSupported(transferable, dataFlavors, PackageControllerTableTransferable.FLAVOR)) {
            /* we have Package/Children in clipboard, skip them */
            return IGNORE.PACKAGECONTROLLER;
        }
        try {
            if (transferable != null && transferable.getClass().getName().contains("TransferableProxy")) {
                final Field isLocal = transferable.getClass().getDeclaredField("isLocal");
                if (isLocal != null) {
                    isLocal.setAccessible(true);
                    if (Boolean.TRUE.equals(isLocal.getBoolean(transferable))) {
                        return IGNORE.LOCAL;
                    }
                }
            } else if (dataFlavors != null) {
                for (final DataFlavor dataFlavor : dataFlavors) {
                    if (StringUtils.equals(dataFlavor.getHumanPresentableName(), "application/x-java-jvm-local-objectref")) {
                        return IGNORE.JVM;
                    }
                }
            }
        } catch (final Throwable e) {
            e.printStackTrace();
        }
        return IGNORE.NO;
    }

    public static Transferable getTransferable() {
        return getINSTANCE().clipboard.getContents(null);
    }

    public synchronized void startMonitoring() {
        if (clipboard != null) {
            if (!isMonitoring()) {
                final boolean skipFirstRound = (GraphicalUserInterfaceSettings.CLIPBOARD_SKIP_MODE.ON_STARTUP.equals(CLIPBOARD_SKIP_MODE.get()) && monitoringThread.get() == null) || (GraphicalUserInterfaceSettings.CLIPBOARD_SKIP_MODE.ON_ENABLE.equals(CLIPBOARD_SKIP_MODE.get()));
                final Thread newMonitoringThread = new Thread() {
                    private final AtomicLong roundIndex       = new AtomicLong(0);
                    private ClipboardHash    oldStringContent = new ClipboardHash(0, -1);
                    private ClipboardHash    oldListContent   = new ClipboardHash(0, -1);
                    private ClipboardHash    oldHTMLFragment  = new ClipboardHash(0, -1);

                    private String handleThisRound(final String existing, final String add) {
                        if (existing == null) {
                            if (StringUtils.isNotEmpty(add)) {
                                return add;
                            } else {
                                return null;
                            }
                        } else {
                            if (StringUtils.isNotEmpty(add)) {
                                if (existing.equals(add)) {
                                    return existing;
                                } else {
                                    return existing + "\r\n" + add;
                                }
                            } else {
                                return existing;
                            }
                        }
                    }

                    @Override
                    public void run() {
                        try {
                            clipboardChangeDetector.reset();
                            while (Thread.currentThread() == ClipboardMonitoring.this.monitoringThread.get()) {
                                final CHANGE_FLAG changeFlag = clipboardChangeDetector.waitForClipboardChanges();
                                if (Thread.currentThread() != ClipboardMonitoring.this.monitoringThread.get()) {
                                    logger.finer("Changed ClipBoard Monitoring Thread");
                                    return;
                                } else if (CHANGE_FLAG.INTERRUPTED.equals(changeFlag)) {
                                    logger.finer("Interrupted ClipBoard Monitoring Thread");
                                    return;
                                }
                                try {
                                    final Transferable currentContent;
                                    final DataFlavor[] dataFlavors;
                                    if (true) {
                                        // more lightweight way beause only fetch content when required
                                        dataFlavors = clipboard.getAvailableDataFlavors();
                                        currentContent = null;
                                    } else {
                                        // heavy way because all content is fetched at once
                                        currentContent = clipboard.getContents(null);
                                        dataFlavors = null;
                                    }
                                    final IGNORE ignoreFlag = ignoreTransferable(currentContent, dataFlavors);
                                    switch (ignoreFlag) {
                                    case EMPTY:
                                    case JVM:
                                    case LOCAL:
                                    case PACKAGECONTROLLER:
                                        if (CHANGE_FLAG.DETECTED.equals(changeFlag)) {
                                            roundIndex.getAndIncrement();
                                        }
                                        continue;
                                    case NO:
                                    default:
                                        break;
                                    }
                                    String handleThisRound = null;
                                    boolean macOSWorkaroundNeeded = false;
                                    String debugListContent = null;
                                    try {
                                        /* change detection for List/URI content */
                                        final String newListContent = getListTransferData(currentContent, dataFlavors);
                                        try {
                                            if (!oldListContent.equals(newListContent)) {
                                                debugListContent = newListContent;
                                                handleThisRound = handleThisRound(handleThisRound, newListContent);
                                            }
                                        } finally {
                                            oldListContent = new ClipboardHash(newListContent);
                                        }
                                    } catch (final Throwable e) {
                                        if (CrossSystem.isMac() && e instanceof IOException) {
                                            /**
                                             * Couldn't get a file system path for a URL: file:///.file/id=6571367.1715588 2014-02-28
                                             * 09:52:10.362 java[1637:507] Looked for URLs on the pasteboard, but found none.
                                             **/
                                            macOSWorkaroundNeeded = true;
                                        }
                                    }
                                    String browserURL = null;
                                    final boolean htmlFlavorAllowed = isHtmlFlavorAllowed();
                                    String debugStringContent = null;
                                    String debugHtmlFragment = null;
                                    /* change detection for String/HTML content */
                                    if (StringUtils.isEmpty(handleThisRound) || CrossSystem.isMac()) {
                                        final String newStringContent = getStringTransferData(currentContent, dataFlavors);
                                        try {
                                            if (!oldStringContent.equals(newStringContent)) {
                                                debugStringContent = newStringContent;
                                                /*
                                                 * we only use normal String Content to detect a change
                                                 */
                                                handleThisRound = handleThisRound(handleThisRound, newStringContent);
                                                try {
                                                    /*
                                                     * lets fetch fresh HTML Content if available
                                                     */
                                                    final HTMLFragment htmlFragment = getHTMLFragment(currentContent, dataFlavors);
                                                    if (htmlFragment != null) {
                                                        if (!oldHTMLFragment.equals(htmlFragment.getFragment())) {
                                                            oldHTMLFragment = new ClipboardHash(htmlFragment.getFragment());
                                                            debugHtmlFragment = htmlFragment.getFragment();
                                                            /*
                                                             * remember that we had HTML content this round
                                                             */
                                                            if (htmlFlavorAllowed) {
                                                                handleThisRound = handleThisRound(handleThisRound, htmlFragment.getFragment());
                                                            }
                                                            browserURL = htmlFragment.getSourceURL();
                                                        }
                                                    } else {
                                                        oldHTMLFragment = new ClipboardHash(null);
                                                    }
                                                } catch (final Throwable e) {
                                                }
                                            } else {
                                                /*
                                                 * no String Content change detected, let's verify if the HTML content hasn't changed
                                                 */
                                                try {
                                                    /*
                                                     * lets fetch fresh HTML Content if available
                                                     */
                                                    final HTMLFragment htmlFragment = getHTMLFragment(currentContent, dataFlavors);
                                                    if (htmlFragment != null) {
                                                        /*
                                                         * remember that we had HTML content this round
                                                         */
                                                        if (!oldHTMLFragment.equals(htmlFragment.getFragment())) {
                                                            oldHTMLFragment = new ClipboardHash(htmlFragment.getFragment());
                                                            debugHtmlFragment = htmlFragment.getFragment();
                                                            if (htmlFlavorAllowed) {
                                                                handleThisRound = handleThisRound(newStringContent, htmlFragment.getFragment());
                                                            }
                                                            browserURL = htmlFragment.getSourceURL();
                                                        }
                                                    } else {
                                                        oldHTMLFragment = new ClipboardHash(null);
                                                    }
                                                } catch (final Throwable e) {
                                                }
                                            }
                                        } finally {
                                            oldStringContent = new ClipboardHash(newStringContent);
                                        }
                                    }
                                    if (handleThisRound != null || CHANGE_FLAG.DETECTED.equals(changeFlag)) {
                                        final ClipboardHash stringContent = oldStringContent;
                                        final ClipboardHash htmlFragment = oldHTMLFragment;
                                        final ClipboardHash listContent = oldListContent;
                                        final String stringDebugContent = debugStringContent;
                                        final String listDebugContent = debugListContent;
                                        final String htmlFragmentDebugContent = debugHtmlFragment;
                                        final long round = roundIndex.getAndIncrement();
                                        if (StringUtils.isNotEmpty(handleThisRound) && (round > 0 || !skipFirstRound)) {
                                            clipboardChangeDetector.restart();
                                            // minimum stringContent length: ftp://a,7 or file:/1,7
                                            // minimum htmlFragment length: <*>ftp://a</*>
                                            // minimum listContent length: file:/1,7
                                            if (stringContent.getClipboardLength() > 7 || htmlFragment.getClipboardLength() > 14 || listContent.getClipboardLength() > 7) {
                                                final LinkCollectingJob job = new LinkCollectingJob(LinkOrigin.CLIPBOARD.getLinkOriginDetails(), handleThisRound) {
                                                    @Override
                                                    public String toString() {
                                                        if (false && LogController.getInstance().isDebugMode()) {
                                                            return super.toString() + "|ChangeFlag:" + changeFlag + "|Round:" + round + "|StringContent:(" + stringContent + "|" + stringDebugContent + ")|HTMLFragment:(" + htmlFragment + "|" + htmlFragmentDebugContent + ")|ListContent:(" + listContent + "|" + listDebugContent + ")";
                                                        } else {
                                                            return super.toString() + "|ChangeFlag:" + changeFlag + "|Round:" + round + "|StringContent:(" + (stringDebugContent != null) + "|" + stringContent + ")|HTMLFragment:(" + (htmlFragmentDebugContent != null) + "|" + htmlFragment + ")|ListContent:(" + (listDebugContent != null) + "|" + listContent + ")";
                                                        }
                                                    };
                                                };
                                                final HashSet<String> pws = PasswordUtils.getPasswords(handleThisRound);
                                                if (pws != null && pws.size() > 0) {
                                                    job.addPrePackagizerModifier(new CrawledLinkModifier() {
                                                        @Override
                                                        public List<CrawledLinkModifier> getSubCrawledLinkModifier(CrawledLink link) {
                                                            return null;
                                                        }

                                                        @Override
                                                        public boolean modifyCrawledLink(CrawledLink link) {
                                                            link.getArchiveInfo().getExtractionPasswords().addAll(pws);
                                                            return true;
                                                        }
                                                    });
                                                }
                                                job.setCustomSourceUrl(browserURL);
                                                LinkCollector.getInstance().addCrawlerJob(job);
                                            }
                                        }
                                    }
                                    if (macOSWorkaroundNeeded) {
                                        setCurrentContent(handleThisRound);
                                    }
                                } catch (final Throwable e) {
                                    clipboardChangeDetector.slowDown(e);
                                    final String message = e.getMessage();
                                    if (!StringUtils.containsIgnoreCase(message, "Font transform has") && !StringUtils.containsIgnoreCase(message, "Failed to retrieve atom name") && !StringUtils.containsIgnoreCase(message, "cannot open system clipboard") && !StringUtils.containsIgnoreCase(message, "Owner failed to convert data") && !StringUtils.containsIgnoreCase(message, "Owner timed out") && !StringUtils.containsIgnoreCase(message, "system clipboard data unavailable")) {
                                        if (message != null) {
                                            logger.severe(message);
                                        }
                                        logger.log(e);
                                    }
                                }
                            }
                        } finally {
                            oldStringContent = null;
                            oldHTMLFragment = null;
                            oldListContent = null;
                            ClipboardMonitoring.this.monitoringThread.compareAndSet(Thread.currentThread(), ClipboardMonitoring.this);
                        }
                    }
                };
                newMonitoringThread.setName("ClipboardMonitor");
                newMonitoringThread.setDaemon(true);
                final Object oldMonitoringThread = ClipboardMonitoring.this.monitoringThread.getAndSet(newMonitoringThread);
                newMonitoringThread.start();
                if (oldMonitoringThread != null && oldMonitoringThread instanceof Thread) {
                    ((Thread) oldMonitoringThread).interrupt();
                }
            }
        }
    }

    public synchronized ClipboardContent getCurrentContent(Transferable currentContent) {
        if (currentContent != null) {
            String stringContent = null;
            try {
                stringContent = getStringTransferData(currentContent, null);
            } catch (final Throwable e) {
            }
            HTMLFragment htmlFragment = null;
            try {
                /* lets fetch fresh HTML Content if available */
                htmlFragment = getHTMLFragment(currentContent, null);
            } catch (final Throwable e) {
                e.printStackTrace();
            }
            final StringBuilder sb = new StringBuilder();
            if (stringContent != null) {
                sb.append("<");
                sb.append(stringContent);
                sb.append(">");
            }
            if (isHtmlFlavorAllowed() && htmlFragment != null && StringUtils.isNotEmpty(htmlFragment.getFragment())) {
                if (sb.length() > 0) {
                    sb.append("\r\n");
                }
                sb.append("<");
                sb.append(htmlFragment.getFragment());
                sb.append(">");
            }
            if (sb.length() > 0) {
                if (htmlFragment != null) {
                    return new ClipboardContent(sb.toString(), htmlFragment.getSourceURL());
                } else {
                    return new ClipboardContent(sb.toString(), null);
                }
            }
        }
        return null;
    }

    public synchronized ClipboardContent getCurrentContent() {
        if (clipboard != null) {
            try {
                final Transferable currentContent = clipboard.getContents(null);
                return getCurrentContent(currentContent);
            } catch (final Throwable e) {
            }
        }
        return null;
    }

    public synchronized void setCurrentContent(final String string) {
        if (clipboard != null) {
            try {
                final AtomicBoolean skipFlag = new AtomicBoolean(true);
                skipChangeDetection.set(skipFlag);
                clipboard.setContents(new StringSelection(string), new ClipboardOwner() {
                    public void lostOwnership(Clipboard clipboard, Transferable contents) {
                        skipFlag.set(false);
                    }
                });
            } catch (final Throwable e) {
                final AtomicBoolean old = skipChangeDetection.getAndSet(null);
                if (old != null) {
                    old.set(false);
                }
            }
        }
    }

    public synchronized void setCurrentContent(Transferable object) {
        if (clipboard != null) {
            try {
                final AtomicBoolean skipFlag = new AtomicBoolean(true);
                skipChangeDetection.set(skipFlag);
                clipboard.setContents(object, new ClipboardOwner() {
                    public void lostOwnership(Clipboard clipboard, Transferable contents) {
                        skipFlag.set(false);
                    }
                });
            } catch (final Throwable e) {
                final AtomicBoolean old = skipChangeDetection.getAndSet(null);
                if (old != null) {
                    old.set(false);
                }
            }
        }
    }

    public synchronized void stopMonitoring() {
        final Object thread = monitoringThread.getAndSet(this);
        if (thread != null && thread instanceof Thread) {
            ((Thread) thread).interrupt();
        }
    }

    public boolean isMonitoring() {
        final Object thread = monitoringThread.get();
        return thread != null && thread instanceof Thread && ((Thread) thread).isAlive();
    }

    public ClipboardMonitoring() {
        logger = LogController.CL(ClipboardMonitoring.class);
        Clipboard lclipboard = null;
        WindowsClipboardHack lclipboardHack = null;
        ClipboardChangeDetector clipboardChangeDetector = null;
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                lclipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (lclipboard != null && CrossSystem.isWindows()) {
                    try {
                        lclipboardHack = new WindowsClipboardHack(lclipboard, logger);
                    } catch (final Throwable th) {
                        logger.log(th);
                    }
                    try {
                        if (CrossSystem.getOS().isMinimum(OperatingSystem.WINDOWS_XP)) {
                            clipboardChangeDetector = new WindowsClipboardChangeDetector(skipChangeDetection, logger);
                        }
                    } catch (final Throwable th) {
                        logger.log(th);
                    }
                }
            }
        } catch (final Throwable th) {
            logger.log(th);
        }
        clipboard = lclipboard;
        windowsClipboardHack = lclipboardHack;
        if (clipboard != null) {
            if (clipboardChangeDetector == null) {
                this.clipboardChangeDetector = new ClipboardChangeDetector(skipChangeDetection);
            } else {
                this.clipboardChangeDetector = clipboardChangeDetector;
            }
        } else {
            this.clipboardChangeDetector = null;
        }
    }

    @Deprecated
    public static String getHTMLTransferData(final Transferable transferable) throws UnsupportedFlavorException, IOException {
        final HTMLFragment ret = getHTMLFragment(transferable, null);
        if (ret != null) {
            return ret.getFragment();
        } else {
            return null;
        }
    }

    public static HTMLFragment getHTMLFragment(final Transferable transferable, DataFlavor[] dataFlavors) throws UnsupportedFlavorException, IOException {
        DataFlavor htmlFlavor = null;
        final Class<?> preferClass = byte[].class;
        /*
         * for our workaround for https://bugzilla.mozilla.org/show_bug.cgi?id=385421, it would be good if we have utf8 charset
         */
        final DataFlavor[] flavors;
        if (transferable != null) {
            flavors = transferable.getTransferDataFlavors();
        } else if (dataFlavors != null) {
            flavors = dataFlavors;
        } else {
            return null;
        }
        for (final DataFlavor flav : flavors) {
            if (flav.getMimeType().contains("html") && flav.getRepresentationClass().isAssignableFrom(preferClass)) {
                /*
                 * we use first hit and search UTF-8
                 */
                if (htmlFlavor == null) {
                    htmlFlavor = flav;
                }
                final String charSet = new Regex(flav.toString(), "charset=(.*?)]").getMatch(0);
                if ("UTF-8".equalsIgnoreCase(charSet)) {
                    /* we found utf-8 encoding, so lets use that */
                    htmlFlavor = flav;
                    break;
                }
            }
        }
        final byte[] htmlDataBytes = getBytes(transferable, dataFlavors, null, htmlFlavor);
        if (htmlDataBytes != null && htmlDataBytes.length != 0) {
            final String charSet = new Regex(htmlFlavor.toString(), "charset=(.*?)]").getMatch(0);
            final String result = convertBytes(htmlDataBytes, charSet, true);
            if (CrossSystem.isWindows()) {
                final String sourceURL = new Regex(result, "EndFragment:\\d+[\r\n]*SourceURL:(.*?)(\r|\n)").getMatch(0);
                final String fragment = new Regex(result, "<!--StartFragment-->(.*?)<!--EndFragment-->").getMatch(0);
                if (fragment != null) {
                    if (!StringUtils.isEmpty(sourceURL) && HTMLParser.getProtocol(sourceURL) != null) {
                        return new HTMLFragment(sourceURL, fragment);
                    }
                    final String browserURL = getCurrentBrowserURL(transferable, dataFlavors, result);
                    return new HTMLFragment(browserURL, fragment);
                }
            }
            final String browserURL = getCurrentBrowserURL(transferable, dataFlavors, result);
            return new HTMLFragment(browserURL, result);
        }
        return null;
    }

    private final static boolean startsWith(byte[] array, int startIndex, byte[] check) {
        if (array != null && check != null && array.length >= startIndex + check.length) {
            for (int index = 0; index < check.length; index++) {
                if (array[startIndex + index] != check[index]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private final static byte[] REPLACEMENT_CHARACTER = new byte[] { (byte) 0xef, (byte) 0xbf, (byte) 0xbd };

    private static String convertBytes(byte[] bytes, String charSet, boolean linuxWorkaround) throws UnsupportedEncodingException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        if (StringUtils.isEmpty(charSet)) {
            charSet = "UTF-8";
        }
        if (CrossSystem.isUnix()) {
            /*
             * workaround for firefox bug https://bugzilla .mozilla.org/show_bug .cgi?id=385421
             */
            /*
             * write check to skip broken first bytes and discard 0 bytes if they are in intervalls
             */
            int indexOriginal = 0;
            int i = 0;
            bom: while (true) {
                if (startsWith(bytes, i, REPLACEMENT_CHARACTER)) {
                    i += REPLACEMENT_CHARACTER.length;
                    continue bom;
                }
                for (final BOM bom : BOM.values()) {
                    if (startsWith(bytes, i, bom.getBOM())) {
                        i += bom.length();
                        charSet = bom.getCharSet();
                        continue bom;
                    }
                }
                break;
            }
            for (; i < bytes.length - 1; i++) {
                if (bytes[i] != 0) {
                    /* copy byte */
                    bytes[indexOriginal++] = bytes[i];
                }
            }
            return new String(bytes, 0, indexOriginal, charSet);
        } else {
            return new String(bytes, charSet);
        }
    }

    public static boolean hasSupportedTransferData(final Transferable transferable) {
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            /*
             * string and html always come together, so no need to check for html
             */
            return true;
        } else if (URLFLAVOR != null && transferable.isDataFlavorSupported(URLFLAVOR)) {
            return true;
        } else if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return true;
        } else if (URILISTFLAVOR != null && transferable.isDataFlavorSupported(URILISTFLAVOR)) {
            return true;
        } else {
            return false;
        }
    }

    public static void processSupportedTransferData(final Transferable transferable, LinkOrigin origin) {
        try {
            final DataFlavor[] dataFlavors = null;
            final String listContent = getListTransferData(transferable, dataFlavors);
            final String stringContent = getStringTransferData(transferable, dataFlavors);
            final HTMLFragment htmlFragment;
            if (StringUtils.isNotEmpty(stringContent)) {
                htmlFragment = getHTMLFragment(transferable, null);
            } else {
                htmlFragment = null;
            }
            final String sourceURL;
            if (htmlFragment != null && StringUtils.isNotEmpty(htmlFragment.getSourceURL())) {
                sourceURL = htmlFragment.getSourceURL();
            } else {
                if (htmlFragment == null) {
                    sourceURL = getCurrentBrowserURL(transferable, dataFlavors, null);
                } else {
                    sourceURL = getCurrentBrowserURL(transferable, dataFlavors, htmlFragment.getFragment());
                }
            }
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isNotEmpty(listContent)) {
                sb.append("<");
                sb.append(listContent);
                sb.append(">\r\n\r\n");
            }
            if (StringUtils.isEmpty(listContent) && StringUtils.isNotEmpty(stringContent)) {
                sb.append("<");
                sb.append(stringContent);
                sb.append(">\r\n\r\n");
            }
            if (isHtmlFlavorAllowed() && htmlFragment != null && StringUtils.isNotEmpty(htmlFragment.getFragment())) {
                sb.append("<");
                sb.append(htmlFragment.getFragment());
                sb.append(">");
            }
            final String content = sb.toString();
            if (!StringUtils.isEmpty(content)) {
                final LinkCollectingJob job = new LinkCollectingJob(origin.getLinkOriginDetails(), content);
                job.setCustomSourceUrl(sourceURL);
                final HashSet<String> pws = PasswordUtils.getPasswords(content);
                if (pws != null && pws.size() > 0) {
                    job.addPrePackagizerModifier(new CrawledLinkModifier() {
                        @Override
                        public List<CrawledLinkModifier> getSubCrawledLinkModifier(CrawledLink link) {
                            return null;
                        }

                        @Override
                        public boolean modifyCrawledLink(CrawledLink link) {
                            link.getArchiveInfo().getExtractionPasswords().addAll(pws);
                            return true;
                        }
                    });
                }
                LinkCollector.getInstance().addCrawlerJob(job);
            }
        } catch (final Throwable e) {
        }
    }

    public static String getStringTransferData(final Transferable transferable, DataFlavor[] dataFlavors) throws UnsupportedFlavorException, IOException {
        if (isDataFlavorSupported(transferable, dataFlavors, DataFlavor.stringFlavor)) {
            Object ret = null;
            try {
                ret = getTransferData(transferable, ClipboardMonitoring.getINSTANCE().clipboard, DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException e) {
                if (StringUtils.containsIgnoreCase(e.getMessage(), "Unicode String")) {
                    return null;
                } else {
                    throw e;
                }
            }
            if (ret == null) {
                return null;
            } else {
                return StringUtils.removeBOM(ret.toString());
            }
        }
        return null;
    }

    public static String getURLTransferData(final Transferable transferable, DataFlavor[] dataFlavors) throws UnsupportedFlavorException, IOException {
        if (URLFLAVOR != null && isDataFlavorSupported(transferable, dataFlavors, URLFLAVOR)) {
            final Object ret = getTransferData(transferable, ClipboardMonitoring.getINSTANCE().clipboard, URLFLAVOR);
            if (ret == null) {
                return null;
            } else {
                final URL url = (URL) ret;
                if (StringUtils.isEmpty(url.getFile())) {
                    return null;
                } else {
                    return url.toExternalForm();
                }
            }
        }
        return null;
    }

    public static boolean isDataFlavorSupported(final Transferable transferable, DataFlavor[] dataFlavors, DataFlavor dataFlavor) {
        if (transferable != null && transferable.isDataFlavorSupported(dataFlavor)) {
            return true;
        } else if (dataFlavors != null) {
            for (int index = 0; index < dataFlavors.length; index++) {
                if (dataFlavors[index].equals(dataFlavor)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Object getTransferData(final Transferable transferable, Clipboard clipboard, DataFlavor dataFlavor) throws UnsupportedFlavorException, IOException {
        if (transferable != null) {
            return transferable.getTransferData(dataFlavor);
        } else if (clipboard != null) {
            return clipboard.getData(dataFlavor);
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static String getListTransferData(final Transferable transferable, DataFlavor[] dataFlavors) throws UnsupportedFlavorException, IOException, URISyntaxException {
        final StringBuilder sb = new StringBuilder("");
        if (URILISTFLAVOR != null && isDataFlavorSupported(transferable, dataFlavors, URILISTFLAVOR)) {
            /* url-lists are defined by rfc 2483 as crlf-delimited */
            final Object ret = getTransferData(transferable, ClipboardMonitoring.getINSTANCE().clipboard, URILISTFLAVOR);
            if (ret != null) {
                final StringTokenizer izer = new StringTokenizer((String) ret, "\r\n");
                while (izer.hasMoreTokens()) {
                    if (sb.length() > 0) {
                        sb.append("\r\n");
                    }
                    final String next = izer.nextToken();
                    if (StringUtils.isNotEmpty(next)) {
                        // file:/ -> not authority -> all fine
                        // file://xy/ -> xy will be authority -> java.lang.IllegalArgumentException: URI has an authority component
                        // file:/// -> empty authority -> all fine
                        sb.append(next.replaceFirst("file:///", "file:/"));
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        if (isDataFlavorSupported(transferable, dataFlavors, DataFlavor.javaFileListFlavor)) {
            final Object ret = getTransferData(transferable, ClipboardMonitoring.getINSTANCE().clipboard, DataFlavor.javaFileListFlavor);
            if (ret != null) {
                final List<File> list = (List<File>) ret;
                for (final File f : list) {
                    if (!f.isAbsolute()) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append("\r\n");
                    }
                    sb.append(f.toURI().toString());
                }
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    /**
     * @return the iNSTANCE
     */
    public static ClipboardMonitoring getINSTANCE() {
        return INSTANCE;
    }

    private static byte[] getBytes(final Transferable transferable, DataFlavor[] dataFlavors, String mimeType, DataFlavor dataFlavor) throws UnsupportedFlavorException, IOException {
        if (mimeType == null && dataFlavor == null) {
            return null;
        }
        final Class<?> preferClass = byte[].class;
        /*
         * for our workaround for https://bugzilla.mozilla.org/show_bug.cgi?id=385421, it would be good if we have utf8 charset
         */
        final DataFlavor flavor;
        if (dataFlavor != null) {
            flavor = dataFlavor;
        } else if (transferable != null) {
            DataFlavor found = null;
            for (final DataFlavor test : transferable.getTransferDataFlavors()) {
                if (test.getMimeType().contains(mimeType) && test.getRepresentationClass().isAssignableFrom(preferClass)) {
                    found = test;
                    break;
                }
            }
            flavor = found;
        } else if (dataFlavors != null) {
            DataFlavor found = null;
            for (final DataFlavor test : dataFlavors) {
                if (test.getMimeType().contains(mimeType) && test.getRepresentationClass().isAssignableFrom(preferClass)) {
                    found = test;
                    break;
                }
            }
            flavor = found;
        } else {
            flavor = null;
        }
        if (flavor != null) {
            byte[] htmlBytes = null;
            /* this can throw exception on some java versions when content is >256kb */
            if (flavor.getRepresentationClass().isAssignableFrom(byte[].class)) {
                htmlBytes = (byte[]) getTransferData(transferable, ClipboardMonitoring.getINSTANCE().clipboard, flavor);
            } else if (flavor.getRepresentationClass().isAssignableFrom(InputStream.class)) {
                InputStream is = null;
                try {
                    is = (InputStream) getTransferData(transferable, ClipboardMonitoring.getINSTANCE().clipboard, flavor);
                    htmlBytes = IO.readStream(-1, is);
                } finally {
                    try {
                        is.close();
                    } catch (final Throwable ignore) {
                    }
                }
            }
            return htmlBytes;
        }
        return null;
    }

    public static String getCurrentBrowserURL(final Transferable transferable) throws UnsupportedFlavorException, IOException {
        return getCurrentBrowserURL(transferable, null, null);
    }

    public static String getCurrentBrowserURL(final Transferable transferable, final DataFlavor[] dataFlavors, final String htmlFlavor) throws UnsupportedFlavorException, IOException {
        if (ClipboardMonitoring.getINSTANCE().windowsClipboardHack != null) {
            final String ret = ClipboardMonitoring.getINSTANCE().windowsClipboardHack.getURLFromCF_HTML();
            if (!StringUtils.isEmpty(ret) && HTMLParser.getProtocol(ret) != null) {
                return ret;
            }
        }
        String ret = getBrowserMime(transferable, dataFlavors, "x-moz-url");
        if (!StringUtils.isEmpty(ret)) {
            // https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API/Recommended_drag_types
            // it holds the URL of the link followed by the title of the link, separated by a linebreak. For example:
            final String[] lines = Regex.getLines(ret);
            if (lines != null && lines.length > 0) {
                ret = lines[0];
            }
        } else {
            ret = getBrowserMime(transferable, dataFlavors, "x-moz-url-priv");
        }
        if (!StringUtils.isEmpty(ret)) {
            if (HTMLParser.getProtocol(ret) != null) {
                return ret;
            } else {
                final String viewSource = new Regex(ret, "^view-source:(https?://.+)").getMatch(0);
                if (!StringUtils.isEmpty(viewSource) && HTMLParser.getProtocol(viewSource) != null) {
                    return viewSource;
                }
            }
        }
        if (htmlFlavor != null) {
            String viewSource = new Regex(htmlFlavor, "<a href=\"view-source:(https?://.*?)\"").getMatch(0);
            if (!StringUtils.isEmpty(viewSource) && HTMLParser.getProtocol(viewSource) != null) {
                return viewSource;
            }
            viewSource = new Regex(htmlFlavor, "EndFragment:\\d+[\r\n]*SourceURL:(.*?)(\r|\n)").getMatch(0);
            if (!StringUtils.isEmpty(viewSource) && HTMLParser.getProtocol(viewSource) != null) {
                return viewSource;
            }
        }
        return null;
    }

    public static String getBrowserMime(final Transferable transferable, final DataFlavor[] dataFlavors, final String mime) throws UnsupportedFlavorException, IOException {
        final byte[] xmozurlprivBytes = getBytes(transferable, dataFlavors, mime, null);
        return convertBytes(xmozurlprivBytes, "UTF-8", false);
    }
}
