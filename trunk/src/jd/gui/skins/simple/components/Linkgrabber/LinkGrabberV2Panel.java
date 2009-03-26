package jd.gui.skins.simple.components.Linkgrabber;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import jd.config.Configuration;
import jd.config.SubConfiguration;
import jd.controlling.ProgressController;
import jd.event.UIEvent;
import jd.gui.skins.simple.JTabbedPanel;
import jd.gui.skins.simple.LinkGrabber;
import jd.gui.skins.simple.SimpleGUI;
import jd.gui.skins.simple.components.JDFileChooser;
import jd.gui.skins.simple.tasks.LinkGrabberTaskPane;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginForHost;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;
import net.miginfocom.swing.MigLayout;

import org.jdesktop.swingx.JXCollapsiblePane;

public class LinkGrabberV2Panel extends JTabbedPanel implements ActionListener, UpdateListener {

    private static final long serialVersionUID = 1607433619381447389L;
    protected static Vector<LinkGrabberV2FilePackage> packages = new Vector<LinkGrabberV2FilePackage>();
    public static final String PROPERTY_ONLINE_CHECK = "DO_ONLINE_CHECK_V2";
    public static final String PROPERTY_AUTOPACKAGE = "PROPERTY_AUTOPACKAGE";

    private Vector<DownloadLink> waitingList = new Vector<DownloadLink>();

    private LinkGrabberV2TreeTable internalTreeTable;

    protected Logger logger = JDUtilities.getLogger();

    private SubConfiguration guiConfig;
    private Thread gatherer;
    private String PACKAGENAME_UNSORTED;
    private String PACKAGENAME_UNCHECKED;
    private ProgressController pc;
    private JXCollapsiblePane collapsepane;
    private LinkGrabberV2FilePackageInfo FilePackageInfo;
    private Timer gathertimer;

    private UpdateBroadcaster upc = new UpdateBroadcaster();

    final AbstractButton close = new JButton();
    final ImageIcon imgCloseMouseOver = new ImageIcon(JDUtilities.getResourceFile("/jd/img/button_close_mouseover.png").getAbsolutePath());
    final ImageIcon imgClose = new ImageIcon(JDUtilities.getResourceFile("/jd/img/button_close.png").getAbsolutePath());

    public LinkGrabberV2Panel(SimpleGUI parent) {
        super(new MigLayout());
        PACKAGENAME_UNSORTED = JDLocale.L("gui.linkgrabber.package.unsorted", "various");
        PACKAGENAME_UNCHECKED = JDLocale.L("gui.linkgrabber.package.unchecked", "unchecked");
        guiConfig = JDUtilities.getSubConfig(SimpleGUI.GUICONFIGNAME);
        internalTreeTable = new LinkGrabberV2TreeTable(new LinkGrabberV2TreeTableModel(this), this);
        JScrollPane scrollPane = new JScrollPane(internalTreeTable);
        this.add(scrollPane, "cell 0 0, width 100%, height 100%");
        FilePackageInfo = new LinkGrabberV2FilePackageInfo();
        collapsepane = new JXCollapsiblePane();
        collapsepane.setCollapsed(true);
        collapsepane.add(FilePackageInfo);

        this.add(close, "id close, pos (pane.w-(close.w/2)) (pane.y+(close.h/2))");
        this.add(collapsepane, "cell 0 1, width 100%, id pane");

        close.setIcon(imgClose);
        close.setMaximumSize(new Dimension(16, 16));
        close.setForeground(new Color(255, 0, 0));

        close.addMouseListener(new MouseListener() {

            public void mouseClicked(MouseEvent arg0) {
                // TODO Auto-generated method stub
            }

            public void mouseEntered(MouseEvent arg0) {
                close.setIcon(imgCloseMouseOver);
            }

            public void mouseExited(MouseEvent arg0) {
                close.setIcon(imgClose);
            }

            public void mousePressed(MouseEvent arg0) {
                // TODO Auto-generated method stub
                hideFilePackageInfo();
            }

            public void mouseReleased(MouseEvent arg0) {
                // TODO Auto-generated method stub
            }
        });
    }

    public void showFilePackageInfo(LinkGrabberV2FilePackage fp) {
        FilePackageInfo.setPackage(fp);
        collapsepane.setCollapsed(false);
    }

    public void hideFilePackageInfo() {
        collapsepane.setCollapsed(true);
    }

    public UpdateBroadcaster getUpdateBroadcaster() {
        return upc;
    }

    public Vector<LinkGrabberV2FilePackage> getPackages() {
        return packages;
    }

    public synchronized void fireTableChanged(final int id, final Object param) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                synchronized (packages) {
                    internalTreeTable.fireTableChanged(id, param);
                }
            }
        });
    }

    @Override
    public void onHide() {
        // TODO Auto-generated method stub

    }

    public synchronized void addLinks(DownloadLink[] linkList) {
        for (DownloadLink element : linkList) {
            if (isDupe(element)) continue;
            addToWaitingList(element);
        }
        fireTableChanged(1, null);
        if (gathertimer != null) {
            gathertimer.stop();
            gathertimer.removeActionListener(LinkGrabberV2Panel.this);
            gathertimer = null;

        }
        gathertimer = new Timer(2000, LinkGrabberV2Panel.this);
        gathertimer.setInitialDelay(2000);
        gathertimer.setRepeats(false);
        gathertimer.start();
    }

    public synchronized void addToWaitingList(DownloadLink element) {
        waitingList.add(element);
        checkAlreadyinList(element);
        attachToPackagesFirstStage(element);
        upc.fireUpdateEvent(UpdateEvent.UPDATE_EVENT);
    }

    private void attachToPackagesFirstStage(DownloadLink link) {
        synchronized (packages) {
            String packageName;
            LinkGrabberV2FilePackage fp = null;
            if (link.getFilePackage() != FilePackage.getDefaultFilePackage()) {
                packageName = link.getFilePackage().getName();
                fp = getFPwithName(packageName);
                if (fp == null) {
                    fp = new LinkGrabberV2FilePackage(packageName, this);
                }
            }
            if (fp == null) {
                if (guiConfig.getBooleanProperty(PROPERTY_ONLINE_CHECK, true)) {
                    fp = getFPwithName(PACKAGENAME_UNCHECKED);
                    if (fp == null) {
                        fp = new LinkGrabberV2FilePackage(PACKAGENAME_UNCHECKED, this);
                    }
                } else {
                    fp = getFPwithName(PACKAGENAME_UNSORTED);
                    if (fp == null) {
                        fp = new LinkGrabberV2FilePackage(PACKAGENAME_UNSORTED, this);
                    }
                }
            }
            addToPackages(fp, link);
        }
    }

    private LinkGrabberV2FilePackage getFPwithName(String name) {
        synchronized (packages) {
            if (name == null) return null;
            LinkGrabberV2FilePackage fp = null;
            for (Iterator<LinkGrabberV2FilePackage> it = packages.iterator(); it.hasNext();) {
                fp = it.next();
                if (fp.getName().equalsIgnoreCase(name)) return fp;
            }
            return null;
        }
    }

    private LinkGrabberV2FilePackage getFPwithLink(DownloadLink link) {
        synchronized (packages) {
            if (link == null) return null;
            LinkGrabberV2FilePackage fp = null;
            for (Iterator<LinkGrabberV2FilePackage> it = packages.iterator(); it.hasNext();) {
                fp = it.next();
                if (fp.contains(link)) return fp;
            }
            return null;
        }
    }

    private boolean isDupe(DownloadLink link) {
        synchronized (packages) {
            if (link == null) return false;
            if (link.getBooleanProperty("ALLOW_DUPE", false)) return false;
            LinkGrabberV2FilePackage fp = null;
            DownloadLink dl = null;
            for (Iterator<LinkGrabberV2FilePackage> it = packages.iterator(); it.hasNext();) {
                fp = it.next();
                for (Iterator<DownloadLink> it2 = fp.getDownloadLinks().iterator(); it2.hasNext();) {
                    dl = it2.next();
                    if (dl.getDownloadURL().trim().equalsIgnoreCase(link.getDownloadURL().trim())) return true;
                }
            }
            return false;
        }
    }

    private void addToPackages(LinkGrabberV2FilePackage fp, DownloadLink link) {
        synchronized (packages) {
            LinkGrabberV2FilePackage fptmp = getFPwithLink(link);
            if (fptmp != null) fptmp.remove(link);
            if (!packages.contains(fp)) packages.add(fp);
            fp.add(link);
        }
    }

    private void removeFromPackages(DownloadLink link) {
        if (link == null) return;
        synchronized (packages) {
            LinkGrabberV2FilePackage fptmp = getFPwithLink(link);
            if (fptmp != null) fptmp.remove(link);
        }
    }

    private void stopLinkGatherer() {
        if (gatherer != null && gatherer.isAlive()) {
            gatherer.interrupt();
        }
    }

    private void startLinkGatherer() {
        if (gatherer != null && gatherer.isAlive()) { return; }
        gatherer = new Thread() {
            public void run() {
                setName("LinkGrabber");
                String statusstring = "onlinecheck";
                if (!guiConfig.getBooleanProperty(PROPERTY_ONLINE_CHECK, true)) statusstring = "linkgrabber";
                pc = new ProgressController(statusstring);
                pc.setRange(0);
                while (waitingList.size() != 0) {
                    Vector<DownloadLink> currentList = new Vector<DownloadLink>();
                    synchronized (waitingList) {
                        /*
                         * erstma aus der Waittinglist in verarbeitungsliste
                         * übernehmen
                         */
                        for (int i = waitingList.size() - 1; i >= 0; i--) {
                            currentList.add(waitingList.remove(i));
                        }
                        pc.addToMax(currentList.size());
                    }
                    if (!guiConfig.getBooleanProperty(PROPERTY_ONLINE_CHECK, true)) {
                        /* kein online check, kein multithreaded nötig */
                        afterLinkGrabber(currentList);
                    } else {
                        /* onlinecheck, multithreaded damit schneller */
                        HashMap<String, Vector<DownloadLink>> map = new HashMap<String, Vector<DownloadLink>>();
                        for (Iterator<DownloadLink> it = currentList.iterator(); it.hasNext();) {
                            /*
                             * aufteilung in hosterlisten, um schnellere checks
                             * zu ermöglichen
                             */
                            DownloadLink l = it.next();
                            it.remove();
                            Vector<DownloadLink> localList = map.get(l.getPlugin().getHost());
                            if (localList == null) {
                                localList = new Vector<DownloadLink>();
                                map.put(l.getPlugin().getHost(), localList);
                            }
                            if (l.getPlugin().getHost().contains("rapid")) localList.add(l);
                        }
                        Vector<DownloadLink> hosterList;
                        for (Iterator<Vector<DownloadLink>> it = map.values().iterator(); it.hasNext();) {
                            hosterList = it.next();
                            checkHosterList(hosterList);
                        }
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                }
                pc.finalize();
            }
        };
        gatherer.start();
    }

    private void checkHosterList(Vector<DownloadLink> hosterList) {
        Vector<DownloadLink> links = new Vector<DownloadLink>();
        for (int i = hosterList.size() - 1; i >= 0; i--) {
            /*
             * decrypter können online status ebenfalls setzen, daher nicht
             * extra nochma prüfen
             */
            DownloadLink link = hosterList.get(i);
            if (link.isAvailabilityChecked()) {
                links.add(hosterList.remove(i));
            }
        }
        afterLinkGrabber(links);
        if (hosterList.size() != 0) {
            DownloadLink link = hosterList.get(0);
            boolean ret = ((PluginForHost) link.getPlugin()).checkLinks(hosterList.toArray(new DownloadLink[] {}));
            if (!ret) {
                for (int i = 0; i < hosterList.size(); i++) {
                    link = hosterList.get(i);
                    link.isAvailable();
                }
            }
            afterLinkGrabber(hosterList);
        }
    }

    private synchronized void afterLinkGrabber(Vector<DownloadLink> links) {
        for (DownloadLink link : links) {
            attachToPackagesSecondStage(link);
        }
        pc.increase(links.size());
        fireTableChanged(1, null);
    }

    private String getNameMatch(String name, String pattern) {
        String match = new Regex(name, pattern).getMatch(0);
        if (match != null) return match;
        return name;
    }

    private String cleanFileName(String name) {
        /** remove rar extensions */

        name = getNameMatch(name, "(.*)\\.part[0]*[1].rar$");
        name = getNameMatch(name, "(.*)\\.part[0-9]+.rar$");
        name = getNameMatch(name, "(.*)\\.rar$");

        name = getNameMatch(name, "(.*)\\.r\\d+$");

        /**
         * remove 7zip and hjmerge extensions
         */

        name = getNameMatch(name, "(?is).*\\.7z\\.[\\d]+$");
        name = getNameMatch(name, "(.*)\\.a.$");

        name = getNameMatch(name, "(.*)\\.[\\d]+($|\\.(7z|rar|divx|avi|xvid|bz2|doc|gz|jpg|jpeg|m4a|mdf|mkv|mp3|mp4|mpg|mpeg|pdf|wma|wmv|xcf|zip|jar|swf|class|bmp|cue|bin|dll|cab|png|ico|exe|gif|iso|flv|cso)$)");

        int lastPoint = name.lastIndexOf(".");
        if (lastPoint <= 0) return name;
        String extension = name.substring(name.length() - lastPoint + 1);
        if (extension.length() > 0 && extension.length() < 6) {
            name = name.substring(0, lastPoint);
        }
        return JDUtilities.removeEndingPoints(name);
    }

    private synchronized void attachToPackagesSecondStage(DownloadLink link) {
        String packageName;
        boolean autoPackage = false;
        if (link.getFilePackage() != FilePackage.getDefaultFilePackage()) {
            packageName = link.getFilePackage().getName();
        } else {
            autoPackage = true;
            packageName = cleanFileName(link.getName());
        }
        synchronized (packages) {
            int bestSim = 0;
            int bestIndex = -1;
            for (int i = 0; i < packages.size(); i++) {
                int sim = comparepackages(packages.get(i).getName(), packageName);
                if (sim > bestSim) {
                    bestSim = sim;
                    bestIndex = i;
                }
            }
            if (bestSim < 99) {
                LinkGrabberV2FilePackage fp = new LinkGrabberV2FilePackage(packageName, this);
                addToPackages(fp, link);
            } else {
                String newPackageName = autoPackage ? JDUtilities.getSimString(packages.get(bestIndex).getName(), packageName) : packageName;
                packages.get(bestIndex).setName(newPackageName);
                addToPackages(packages.get(bestIndex), link);
            }
        }
    }

    private int comparepackages(String a, String b) {

        int c = 0;
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) == b.charAt(i)) {
                c++;
            }
        }
        if (Math.min(a.length(), b.length()) == 0) { return 0; }
        return c * 100 / Math.min(a.length(), b.length());
    }

    @Override
    public void onDisplay() {
        // TODO Auto-generated method stub

    }

    public void UpdateEvent(UpdateEvent event) {
        synchronized (packages) {
            if (event.getSource() instanceof LinkGrabberV2FilePackage && event.getID() == UpdateEvent.EMPTY_EVENT) {
                ((LinkGrabberV2FilePackage) event.getSource()).getUpdateBroadcaster().removeUpdateListener(this);
                if (FilePackageInfo.getPackage() == ((LinkGrabberV2FilePackage) event.getSource()) || FilePackageInfo.getPackage() == null) {
                    this.hideFilePackageInfo();
                }
                packages.remove(event.getSource());
                if (packages.size() == 0) upc.fireUpdateEvent(UpdateEvent.EMPTY_EVENT);
            }
        }

    }

    @SuppressWarnings("unchecked")
    public void actionPerformed(ActionEvent arg0) {
        if (arg0.getSource() == this.gathertimer) {
            gathertimer.stop();
            gathertimer.removeActionListener(this);
            gathertimer = null;
            if (waitingList.size() > 0) {
                startLinkGatherer();
            }
            return;
        }
        Vector<LinkGrabberV2FilePackage> selected_packages = new Vector<LinkGrabberV2FilePackage>();
        Vector<DownloadLink> selected_links = new Vector<DownloadLink>();
        int prio = 0;
        String pw = "";
        HashMap<String, Object> prop = new HashMap<String, Object>();
        LinkGrabberV2FilePackage fp;
        JDFileChooser fc;
        int col = 0;
        if (arg0.getSource() instanceof LinkGrabberTaskPane) {
            switch (arg0.getID()) {
            case LinkGrabberV2TreeTableAction.ADD_ALL:
                selected_packages = new Vector<LinkGrabberV2FilePackage>(packages);
                break;
            case LinkGrabberV2TreeTableAction.CLEAR:
                stopLinkGatherer();
                selected_packages = new Vector<LinkGrabberV2FilePackage>(packages);
                break;
            case LinkGrabberV2TreeTableAction.ADD_SELECTED:
                selected_packages = new Vector<LinkGrabberV2FilePackage>(this.internalTreeTable.getSelectedFilePackages());
                break;
            }
        } else if (arg0.getSource() instanceof JMenuItem) {
            switch (arg0.getID()) {
            case LinkGrabberV2TreeTableAction.ADD_ALL:
                selected_packages = new Vector<LinkGrabberV2FilePackage>(packages);
                break;
            case LinkGrabberV2TreeTableAction.ADD_SELECTED:
            case LinkGrabberV2TreeTableAction.PACKAGE_EDIT_DIR:
            case LinkGrabberV2TreeTableAction.PACKAGE_SORT:
                col = (Integer) ((LinkGrabberV2TreeTableAction) ((JMenuItem) arg0.getSource()).getAction()).getProperty().getProperty("col");
                selected_packages = new Vector<LinkGrabberV2FilePackage>(this.internalTreeTable.getSelectedFilePackages());
                break;
            case LinkGrabberV2TreeTableAction.DOWNLOAD_PRIO:
                prop = (HashMap<String, Object>) ((LinkGrabberV2TreeTableAction) ((JMenuItem) arg0.getSource()).getAction()).getProperty().getProperty("infos");
                selected_links = (Vector<DownloadLink>) prop.get("downloadlinks");
                break;
            case LinkGrabberV2TreeTableAction.DELETE:
            case LinkGrabberV2TreeTableAction.SET_PW:
            case LinkGrabberV2TreeTableAction.NEW_PACKAGE:
                selected_links = (Vector<DownloadLink>) ((LinkGrabberV2TreeTableAction) ((JMenuItem) arg0.getSource()).getAction()).getProperty().getProperty("links");
                break;
            }
        }
        switch (arg0.getID()) {
        case LinkGrabberV2TreeTableAction.PACKAGE_SORT:
            for (LinkGrabberV2FilePackage fp2 : selected_packages) {
                fp2.sort(col);
            }
            fireTableChanged(1, null);
            break;
        case LinkGrabberV2TreeTableAction.PACKAGE_EDIT_DIR:
            fc = new JDFileChooser();
            fc.setApproveButtonText(JDLocale.L("gui.btn_ok", "OK"));
            fc.setFileSelectionMode(JDFileChooser.DIRECTORIES_ONLY);
            fc.setCurrentDirectory(new File(selected_packages.get(0).getDownloadDirectory()));
            if (fc.showOpenDialog(this) == JDFileChooser.APPROVE_OPTION) {
                if (fc.getSelectedFile() != null) {
                    for (LinkGrabberV2FilePackage fp2 : selected_packages) {
                        fp2.setDownloadDirectory(fc.getSelectedFile().getAbsolutePath());
                    }
                }
            }
            break;
        case LinkGrabberV2TreeTableAction.NEW_PACKAGE:
            fp = this.getFPwithLink(selected_links.get(0));
            String name = SimpleGUI.CURRENTGUI.showUserInputDialog(JDLocale.L("gui.linklist.newpackage.message", "Name of the new package"), fp.getName());
            if (name != null) {
                LinkGrabberV2FilePackage nfp = new LinkGrabberV2FilePackage(name, this);
                for (DownloadLink link : selected_links) {
                    removeFromPackages(link);
                    addToPackages(nfp, link);
                }
                fireTableChanged(1, null);
            }
            return;
        case LinkGrabberV2TreeTableAction.SET_PW:
            pw = SimpleGUI.CURRENTGUI.showUserInputDialog(JDLocale.L("gui.linklist.setpw.message", "Set download password"), null);
            for (int i = 0; i < selected_links.size(); i++) {
                fp = this.getFPwithLink(selected_links.elementAt(i));
                selected_links.elementAt(i).setProperty("pass", pw);
                if (fp != null) fp.getUpdateBroadcaster().fireUpdateEvent(new UpdateEvent(fp, UpdateEvent.UPDATE_EVENT));
            }
            return;
        case LinkGrabberV2TreeTableAction.ADD_ALL:
        case LinkGrabberV2TreeTableAction.ADD_SELECTED:
            confirmPackages(selected_packages);
            fireTableChanged(1, null);
            return;
        case LinkGrabberV2TreeTableAction.DELETE:
            for (DownloadLink link : selected_links) {
                removeFromPackages(link);
            }
            fireTableChanged(1, null);
            return;
        case LinkGrabberV2TreeTableAction.CLEAR:
            for (LinkGrabberV2FilePackage fp2 : selected_packages) {
                fp2.setDownloadLinks(new Vector<DownloadLink>());
            }
            fireTableChanged(1, null);
            return;
        case LinkGrabberV2TreeTableAction.DOWNLOAD_PRIO:
            prio = (Integer) prop.get("prio");
            for (int i = 0; i < selected_links.size(); i++) {
                selected_links.elementAt(i).setPriority(prio);
            }
            return;
        }
    }

    private void confirmPackages(Vector<LinkGrabberV2FilePackage> all) {
        for (int i = 0; i < all.size(); ++i) {
            confirmPackage(all.get(i), null);
        }
    }

    private void addToDownloadDirs(String downloadDirectory, String packageName) {
        if (packageName.length() < 5 || downloadDirectory.equalsIgnoreCase(JDUtilities.getConfiguration().getDefaultDownloadDirectory())) return;
        getDownloadDirList().add(new String[] { downloadDirectory, packageName });
        guiConfig.save();
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String[]> getDownloadDirList() {
        return ((ArrayList<String[]>) guiConfig.getProperty("DOWNLOADDIR_LIST", new ArrayList<String[]>()));
    }

    private void confirmPackage(LinkGrabberV2FilePackage fpv2, String host) {
        if (fpv2 == null) return;
        Vector<DownloadLink> linkList = fpv2.getDownloadLinks();
        if (linkList.isEmpty()) return;

        FilePackage fp = new FilePackage();
        fp.setName(fpv2.getName());
        fp.setComment(fpv2.getComment());
        fp.setPassword(fpv2.getPassword());
        fp.setExtractAfterDownload(fpv2.isExtractAfterDownload());
        addToDownloadDirs(fpv2.getDownloadDirectory(), fpv2.getName());

        if (fpv2.useSubDir()) {
            File file = new File(new File(fpv2.getDownloadDirectory()), fp.getName());
            if (JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_CREATE_SUBFOLDER_BEFORE_DOWNLOAD, false)) {
                if (!file.exists()) file.mkdirs();
            }
            fp.setDownloadDirectory(file.getAbsolutePath());
        } else {
            fp.setDownloadDirectory(fpv2.getDownloadDirectory());
        }
        int files = 0;
        if (host == null) {
            files = linkList.size();
            fp.setDownloadLinks(linkList);
            for (DownloadLink link : linkList) {
                boolean avail = true;
                if (link.isAvailabilityChecked()) avail = link.isAvailable();
                link.getLinkStatus().reset();
                if (!avail) link.getLinkStatus().addStatus(LinkStatus.ERROR_FILE_NOT_FOUND);
                link.setFilePackage(fp);
            }
            fpv2.setDownloadLinks(new Vector<DownloadLink>());
        } else {
            Vector<DownloadLink> linkListHost = new Vector<DownloadLink>();
            for (int i = fpv2.getDownloadLinks().size() - 1; i >= 0; --i) {
                if (linkList.elementAt(i).getHost().compareTo(host) == 0) {
                    DownloadLink link = linkList.remove(i);
                    boolean avail = true;
                    if (link.isAvailabilityChecked()) avail = link.isAvailable();
                    link.getLinkStatus().reset();
                    if (!avail) link.getLinkStatus().addStatus(LinkStatus.ERROR_FILE_NOT_FOUND);
                    linkListHost.add(link);
                    link.setFilePackage(fp);
                    ++files;
                }
            }
            if (files == 0) return;
            fp.setDownloadLinks(linkListHost);
            fpv2.setDownloadLinks(linkList);
        }
        JDUtilities.getGUI().fireUIEvent(new UIEvent(this, UIEvent.UI_PACKAGE_GRABBED, fp));
    }

    public void checkAlreadyinList(DownloadLink link) {
        if (JDUtilities.getController().hasDownloadLinkURL(link.getDownloadURL())) {
            link.getLinkStatus().setErrorMessage("Already in Downloadlist");
            link.getLinkStatus().addStatus(LinkStatus.ERROR_ALREADYEXISTS);
        }
    }
}
