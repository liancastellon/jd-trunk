package org.jdownloader.extensions.eventscripter.sandboxobjects;

import java.util.ArrayList;
import java.util.List;

import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledPackage;
import jd.controlling.linkcrawler.CrawledPackageView;
import jd.controlling.packagecontroller.PackageController;

import org.appwork.utils.Application;
import org.jdownloader.extensions.eventscripter.ScriptAPI;
import org.jdownloader.extensions.extraction.Archive;
import org.jdownloader.extensions.extraction.contextmenu.downloadlist.ArchiveValidator;

@ScriptAPI(description = "The context linkgabber list package")
public class CrawledPackageSandbox {

    private final CrawledPackage filePackage;

    public CrawledPackageSandbox(CrawledPackage parentNode) {
        this.filePackage = parentNode;
    }

    public CrawledPackageSandbox() {
        this(null);
    }

    public ArchiveSandbox[] getArchives() {
        if (filePackage == null) {
            return null;
        } else {
            final ArrayList<ArchiveSandbox> list = new ArrayList<ArchiveSandbox>();
            filePackage.getModifyLock().runReadLock(new Runnable() {
                @Override
                public void run() {
                    final List<Archive> archives = ArchiveValidator.getArchivesFromPackageChildren(filePackage.getChildren());
                    if (archives != null) {
                        for (final Archive archive : archives) {
                            list.add(new ArchiveSandbox(archive));
                        }
                    }
                }
            });
            return list.toArray(new ArchiveSandbox[] {});
        }
    }

    public boolean remove() {
        if (filePackage != null) {
            final PackageController<CrawledPackage, CrawledLink> controller = filePackage.getControlledBy();
            if (controller != null) {
                controller.removePackage(filePackage);
                return true;
            }
        }
        return false;
    }

    public CrawledLinkSandbox[] getDownloadLinks() {
        if (filePackage == null) {
            return null;
        } else {
            final ArrayList<CrawledLinkSandbox> ret = new ArrayList<CrawledLinkSandbox>();
            filePackage.getModifyLock().runReadLock(new Runnable() {

                @Override
                public void run() {
                    for (CrawledLink link : filePackage.getChildren()) {
                        ret.add(new CrawledLinkSandbox(link));
                    }

                }
            });
            return ret.toArray(new CrawledLinkSandbox[] {});
        }
    }

    public long getBytesTotal() {
        if (filePackage == null) {
            return 0;
        } else {
            return new CrawledPackageView(filePackage).aggregate().getFileSize();
        }
    }

    public String getComment() {
        if (filePackage == null) {
            return null;
        } else {
            return filePackage.getComment();
        }
    }

    public void setComment(String comment) {
        if (filePackage != null) {
            filePackage.setComment(comment);
        }
    }

    public void setName(String name) {
        if (filePackage != null) {
            filePackage.setName(name);
        }
    }

    public String getDownloadFolder() {
        if (filePackage == null) {
            return Application.getResource("").getAbsolutePath();
        } else {
            return filePackage.getDownloadFolder();
        }
    }

    @Override
    public String toString() {
        return "CrawledPackage Instance: " + getName();
    }

    public String getName() {
        if (filePackage == null) {
            return "Example CrawledPackage Name";
        } else {
            return filePackage.getName();
        }
    }

}
