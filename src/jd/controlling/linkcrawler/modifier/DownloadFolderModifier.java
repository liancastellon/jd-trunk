package jd.controlling.linkcrawler.modifier;

import java.io.File;

import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledLinkModifier;
import jd.controlling.linkcrawler.PackageInfo;

import org.appwork.utils.StringUtils;
import org.appwork.utils.os.CrossSystem;

public class DownloadFolderModifier implements CrawledLinkModifier {
    protected final String  folder;
    protected final boolean overwriteFlag;

    public DownloadFolderModifier(final String folder, final boolean overwriteFlag) {
        this.folder = StringUtils.isNotEmpty(folder) ? CrossSystem.fixPathSeparators(folder + File.separator) : null;
        this.overwriteFlag = overwriteFlag;
    }

    @Override
    public void modifyCrawledLink(CrawledLink link) {
        PackageInfo existing = link.getDesiredPackageInfo();
        if (overwriteFlag || existing == null || StringUtils.isEmpty(existing.getDestinationFolder())) {
            if (existing == null) {
                existing = new PackageInfo();
            }
            existing.setDestinationFolder(folder);
            if (overwriteFlag) {
                existing.setIgnoreVarious(true);
            }
            existing.setUniqueId(null);
            link.setDesiredPackageInfo(existing);
        }
    }
}
