package org.jdownloader.container;

import java.awt.Dialog.ModalityType;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import jd.controlling.downloadcontroller.DownloadController;
import jd.controlling.linkcollector.CrawledPackageStorable;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcollector.LinkOrigin;
import jd.controlling.linkcollector.LinkOriginDetails;
import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledPackage;
import jd.plugins.ContainerStatus;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.FilePackage;
import jd.plugins.PluginsC;

import org.appwork.uio.ComboBoxDialogInterface;
import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.event.queue.QueueAction;
import org.appwork.utils.swing.dialog.ComboBoxDialog;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.appwork.utils.swing.dialog.DialogNoAnswerException;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;

public class JD2Import extends PluginsC {
    public JD2Import() {
        super("JD2 Import", "file:/.+((downloadList|linkcollector)(\\d+)?(\\.zip|\\.zip\\.backup))$", "$Revision: 21176 $");
    }

    public JD2Import newPluginInstance() {
        return new JD2Import();
    }

    @Override
    public ArrayList<CrawledLink> decryptContainer(final CrawledLink source) {
        final LinkOriginDetails origin = source.getOrigin();
        if (origin != null && LinkOrigin.CLIPBOARD.equals(origin.getOrigin())) {
            return null;
        } else {
            return super.decryptContainer(source);
        }
    }

    protected boolean showConfimDialog() {
        final CrawledLink link = getCurrentLink();
        if (link != null) {
            final LinkOriginDetails origin = link.getOrigin();
            return !(origin != null && LinkOrigin.EXTENSION.equals(origin.getOrigin()));
        }
        return true;
    }

    public ContainerStatus callDecryption(File importFile) {
        final ContainerStatus cs = new ContainerStatus(importFile);
        cls = new ArrayList<CrawledLink>();
        try {
            if (importFile.getName().matches("downloadList(\\d+)?\\.zip(\\.backup)?")) {
                final LinkedList<FilePackage> filePackages = DownloadController.getInstance().loadFile(importFile, true);
                int linksCounter = 0;
                for (final FilePackage filePackage : filePackages) {
                    filePackage.getUniqueID().refresh();
                    for (final DownloadLink downloadLink : filePackage.getChildren()) {
                        downloadLink.getUniqueID().refresh();
                    }
                    linksCounter += filePackage.size();
                }
                boolean importToDownloadList = true;
                if (showConfimDialog()) {
                    final ComboBoxDialog d = new ComboBoxDialog(UIOManager.LOGIC_COUNTDOWN, _GUI.T.jd2_import_title(), _GUI.T.jd2_import_message(filePackages.size(), linksCounter, ""), new String[] { _GUI.T.jd_gui_swing_jdgui_views_downloadview_tab_title(), _GUI.T.jd_gui_swing_jdgui_views_linkgrabberview_tab_title() }, 0, new AbstractIcon(IconKey.ICON_QUESTION, 16), null, null, null) {
                        @Override
                        public ModalityType getModalityType() {
                            return ModalityType.MODELESS;
                        }
                    };
                    final ComboBoxDialogInterface cd = UIOManager.I().show(ComboBoxDialogInterface.class, d);
                    cd.throwCloseExceptions();
                    switch (cd.getSelectedIndex()) {
                    case 0:
                        importToDownloadList = true;
                        break;
                    case 1:
                        importToDownloadList = false;
                        break;
                    default:
                        throw new Exception("Unexpected selectionIndex:" + cd.getSelectedIndex());
                    }
                }
                if (importToDownloadList) {
                    DownloadController.getInstance().getQueue().add(new QueueAction<Void, Throwable>() {
                        @Override
                        protected Void run() throws Throwable {
                            DownloadController.getInstance().importList(filePackages);
                            return null;
                        }
                    });
                } else {
                    final LinkedList<CrawledPackage> crawledPackages = new LinkedList<CrawledPackage>();
                    for (final FilePackage filePackage : filePackages) {
                        final CrawledPackage crawledPackage = new CrawledPackage();
                        crawledPackage.setDownloadFolder(filePackage.getDownloadDirectory());
                        crawledPackage.setName(filePackage.getName());
                        crawledPackage.setComment(filePackage.getComment());
                        crawledPackage.setEnabled(filePackage.isEnabled());
                        crawledPackage.setExpanded(filePackage.isExpanded());
                        for (final DownloadLink downloadLink : filePackage.getChildren()) {
                            final AvailableStatus status = downloadLink.getAvailableStatus();
                            downloadLink.reset(null);
                            downloadLink.setAvailableStatus(status);
                            final CrawledLink crawledLink = new CrawledLink(downloadLink);
                            crawledLink.setEnabled(downloadLink.isEnabled());
                            crawledPackage.getChildren().add(crawledLink);
                            crawledLink.setParentNode(crawledPackage);
                        }
                        crawledPackages.add(crawledPackage);
                    }
                    LinkCollector.getInstance().getQueue().addWait(new QueueAction<Void, Throwable>() {
                        @Override
                        protected Void run() throws Throwable {
                            LinkCollector.getInstance().importList(crawledPackages, null);
                            return null;
                        }
                    });
                }
                cs.setStatus(ContainerStatus.STATUS_FINISHED);
                return cs;
            } else if (importFile.getName().matches("linkcollector(\\d+)?\\.zip(\\.backup)?")) {
                final HashMap<CrawledPackage, CrawledPackageStorable> restoreMap = new HashMap<CrawledPackage, CrawledPackageStorable>();
                final LinkedList<CrawledPackage> packages = LinkCollector.getInstance().loadFile(importFile, restoreMap, true);
                int links = 0;
                for (final CrawledPackage p : packages) {
                    p.getUniqueID().refresh();
                    for (final CrawledLink downloadLink : p.getChildren()) {
                        downloadLink.getUniqueID().refresh();
                    }
                    links += p.getChildren().size();
                }
                if (showConfimDialog()) {
                    final ConfirmDialog d = new ConfirmDialog(UIOManager.LOGIC_COUNTDOWN, _GUI.T.jd2_import_title(), _GUI.T.jd2_import_message(packages.size(), links, _GUI.T.jd_gui_swing_jdgui_views_linkgrabberview_tab_title()), new AbstractIcon(IconKey.ICON_QUESTION, 16), null, null) {
                        @Override
                        public ModalityType getModalityType() {
                            return ModalityType.MODELESS;
                        }
                    };
                    UIOManager.I().show(ConfirmDialogInterface.class, d).throwCloseExceptions();
                }
                LinkCollector.getInstance().getQueue().addWait(new QueueAction<Void, Throwable>() {
                    @Override
                    protected Void run() throws Throwable {
                        LinkCollector.getInstance().importList(packages, restoreMap);
                        return null;
                    }
                });
                cs.setStatus(ContainerStatus.STATUS_FINISHED);
                return cs;
            } else {
                cs.setStatus(ContainerStatus.STATUS_FAILED);
                return cs;
            }
        } catch (DialogNoAnswerException e) {
            cs.setStatus(ContainerStatus.STATUS_ABORT);
            return cs;
        } catch (Throwable e) {
            logger.log(e);
            cs.setStatus(ContainerStatus.STATUS_FAILED);
            return cs;
        }
    }

    @Override
    public String[] encrypt(String plain) {
        return null;
    }
}
