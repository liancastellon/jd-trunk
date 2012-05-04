package jd.controlling.linkcrawler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import jd.controlling.packagecontroller.AbstractPackageNode;
import jd.controlling.packagecontroller.PackageController;

import org.appwork.storage.config.JsonConfig;
import org.appwork.utils.StringUtils;
import org.appwork.utils.os.CrossSystem;
import org.jdownloader.controlling.packagizer.PackagizerController;
import org.jdownloader.settings.GeneralSettings;

public class CrawledPackage implements AbstractPackageNode<CrawledLink, CrawledPackage> {

    protected static final String                          PACKAGETAG            = "<jd:" + PackagizerController.PACKAGENAME + ">";

    public static final Comparator<CrawledLink>            SORTER_ASC            = new Comparator<CrawledLink>() {

                                                                                     @Override
                                                                                     public int compare(CrawledLink o1, CrawledLink o2) {
                                                                                         String o1s = o1.getName();
                                                                                         String o2s = o2.getName();
                                                                                         if (o1s == null) {
                                                                                             o1s = "";
                                                                                         }
                                                                                         if (o2s == null) {
                                                                                             o2s = "";
                                                                                         }
                                                                                         return o1s.compareToIgnoreCase(o2s);

                                                                                     }
                                                                                 };
    public static final Comparator<CrawledLink>            SORTER_DESC           = new Comparator<CrawledLink>() {

                                                                                     @Override
                                                                                     public int compare(CrawledLink o1, CrawledLink o2) {
                                                                                         String o1s = o1.getName();
                                                                                         String o2s = o2.getName();
                                                                                         if (o1s == null) {
                                                                                             o1s = "";
                                                                                         }
                                                                                         if (o2s == null) {
                                                                                             o2s = "";
                                                                                         }
                                                                                         return o2s.compareToIgnoreCase(o1s);

                                                                                     }
                                                                                 };
    private boolean                                        autoExtractionEnabled = true;

    private ArrayList<CrawledLink>                         children;
    private String                                         comment               = null;
    private PackageController<CrawledPackage, CrawledLink> controller            = null;

    private long                                           created               = -1;

    private String                                         name                  = null;

    private String                                         downloadFolder        = JsonConfig.create(GeneralSettings.class).getDefaultDownloadFolder();

    private boolean                                        downloadFolderSet     = false;

    private boolean                                        expanded              = false;

    private HashSet<String>                                extractionPasswords   = new HashSet<String>();
    protected CrawledPackageView                           view;

    private Comparator<CrawledLink>                        sorter;

    public CrawledPackage() {
        children = new ArrayList<CrawledLink>();
        view = new CrawledPackageView();
        sorter = SORTER_ASC;
    }

    public void copyPropertiesTo(CrawledPackage dest) {
        if (dest == null || dest == this) return;
        dest.name = name;
        dest.comment = comment;
        if (this.isDownloadFolderSet()) dest.setDownloadFolder(getRawDownloadFolder());
        dest.getExtractionPasswords().addAll(extractionPasswords);
    }

    @Override
    public void sort(Comparator<CrawledLink> comparator) {
        synchronized (this) {
            if (comparator != null) sorter = comparator;
            Collections.sort(children, sorter);
        }
    }

    public List<CrawledLink> getChildren() {
        return children;
    }

    /**
     * @return the comment
     */
    public String getComment() {
        return comment;
    }

    public PackageController<CrawledPackage, CrawledLink> getControlledBy() {
        return controller;
    }

    public long getCreated() {
        return created;
    }

    public String getDownloadFolder() {

        // replace variables in downloadfolder
        return downloadFolder.replace(PACKAGETAG, CrossSystem.alleviatePathParts(getName()));
    }

    public HashSet<String> getExtractionPasswords() {
        return extractionPasswords;
    }

    public long getFinishedDate() {
        return 0;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns the raw Downloadfolder String. This link may contain wildcards like <jd:packagename>. Use {@link #getDownloadFolder()} to
     * return the actuall downloadloadfolder
     * 
     * @return
     */
    public String getRawDownloadFolder() {
        return downloadFolder;
    }

    public boolean isAutoExtractionEnabled() {
        return autoExtractionEnabled;
    }

    public boolean isDownloadFolderSet() {
        return downloadFolderSet;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void notifyStructureChanges() {

    }

    public void setAutoExtractionEnabled(boolean autoExtractionEnabled) {
        this.autoExtractionEnabled = autoExtractionEnabled;
    }

    /**
     * @param comment
     *            the comment to set
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setControlledBy(PackageController<CrawledPackage, CrawledLink> controller) {
        this.controller = controller;
    }

    /**
     * @param created
     *            the created to set
     */
    public void setCreated(long created) {
        this.created = created;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDownloadFolder(String downloadFolder) {
        if (!StringUtils.isEmpty(downloadFolder)) {
            downloadFolderSet = true;
            this.downloadFolder = downloadFolder;
        } else {
            this.downloadFolder = JsonConfig.create(GeneralSettings.class).getDefaultDownloadFolder();
            this.downloadFolderSet = false;
        }
    }

    public void setEnabled(boolean b) {
    }

    public void setExpanded(boolean b) {
        this.expanded = b;
    }

    public CrawledPackageView getView() {
        return view;
    }

    public boolean isEnabled() {
        return getView().isEnabled();
    }

    public void nodeUpdated(CrawledLink source) {
        notifyChanges();
    }

    private void notifyChanges() {
        PackageController<CrawledPackage, CrawledLink> n = getControlledBy();
        if (n != null) n.nodeUpdated(this);
    }

    public int indexOf(CrawledLink child) {
        synchronized (this) {
            return children.indexOf(child);
        }
    }

    @Override
    public Comparator<CrawledLink> getCurrentSorter() {
        return sorter;
    }

}