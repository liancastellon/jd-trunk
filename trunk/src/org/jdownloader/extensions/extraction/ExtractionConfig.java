package org.jdownloader.extensions.extraction;

import jd.plugins.ExtensionConfigInterface;

import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DefaultEnumValue;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.DefaultJsonObject;
import org.appwork.storage.config.annotations.DefaultStringArrayValue;
import org.appwork.storage.config.annotations.DefaultStringValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.appwork.storage.config.annotations.SpinnerValidator;
import org.jdownloader.controlling.FileCreationManager;
import org.jdownloader.settings.IfFileExistsAction;

public interface ExtractionConfig extends ExtensionConfigInterface {

    @DefaultStringArrayValue(value = { "##Lines with XX are comments", "##Skip deep extraction of archives that contain exe files", ".*\\.exe" })
    @AboutConfig
    @DescriptionForConfigEntry("A list of regular expressions. Use to avoid deep extracting.")
    String[] getDeepExtractionBlacklistPatterns();

    void setDeepExtractionBlacklistPatterns(String[] patterns);

    @DefaultStringArrayValue(value = { "##Lines with XX are comments", "##Example to skip extraction of JPEG files", "##.*\\.jpe?g" })
    @AboutConfig
    @DescriptionForConfigEntry("A list of regular expressions. Use to avoid extracting certain filetypes.")
    String[] getBlacklistPatterns();

    @DefaultEnumValue("HIGH")
    @AboutConfig
    CPUPriority getCPUPriority();

    @AboutConfig
    @DescriptionForConfigEntry("Absolute path to the folder where all archives should be extracted to")
    String getCustomExtractionPath();

    @AboutConfig
    @DefaultStringValue("%PACKAGENAME%")
    String getSubPath();

    @DefaultJsonObject("[]")
    @AboutConfig
    @DescriptionForConfigEntry("A List of passwords for automatic extraction of password protected archives.")
    java.util.List<String> getPasswordList();

    void setPasswordList(java.util.List<String> list);

    @DefaultBooleanValue(false)
    boolean isOldPWListImported();

    void setOldPWListImported(boolean b);

    /**
     * Only use subpath if archive conatins more than X files
     *
     * @return
     */

    @org.appwork.storage.config.annotations.DescriptionForConfigEntry("Only use subfolders if the archive ROOT contains at least *** files")
    @AboutConfig
    @DefaultIntValue(0)
    @SpinnerValidator(min = 0, max = 30)
    int getSubPathMinFilesTreshhold();

    @org.appwork.storage.config.annotations.DescriptionForConfigEntry("Only use subfolders if the archive ROOT contains at least *** folders")
    @AboutConfig
    @DefaultIntValue(0)
    @SpinnerValidator(min = 0, max = 30)
    int getSubPathMinFoldersTreshhold();

    @org.appwork.storage.config.annotations.DescriptionForConfigEntry("Only use subfolders if the archive ROOT contains at least *** folders or folders")
    @AboutConfig
    @DefaultIntValue(2)
    @SpinnerValidator(min = 0, max = 30)
    int getSubPathMinFilesOrFoldersTreshhold();

    @DefaultBooleanValue(true)
    @AboutConfig
    @DescriptionForConfigEntry("Shall Extraction Extension ask you for passwords if the correct password has not been found in password cache?")
    boolean isAskForUnknownPasswordsEnabled();

    @DescriptionForConfigEntry("Enabled usage of custom extraction paths")
    @DefaultBooleanValue(false)
    @AboutConfig
    boolean isCustomExtractionPathEnabled();

    @DefaultBooleanValue(true)
    @AboutConfig
    @DescriptionForConfigEntry("Extraction Extension autoextracts sub-archives. If you do not want this, disable this option.")
    boolean isDeepExtractionEnabled();

    @DescriptionForConfigEntry("Delete archives after successful extraction?")
    @DefaultEnumValue("NO_DELETE")
    @AboutConfig
    FileCreationManager.DeleteOption getDeleteArchiveFilesAfterExtractionAction();

    @DescriptionForConfigEntry("Delete archive DownloadLinks after successful extraction?")
    @DefaultBooleanValue(false)
    @AboutConfig
    boolean isDeleteArchiveDownloadlinksAfterExtraction();

    void setDeleteArchiveDownloadlinksAfterExtraction(boolean b);

    @DescriptionForConfigEntry("Info File Extension is able to create Info files for all downloaded files. Extraction Extension can remove these files")
    @DefaultBooleanValue(false)
    @AboutConfig
    boolean isDeleteInfoFilesAfterExtraction();

    @AboutConfig
    @DefaultBooleanValue(false)
    boolean isSubpathEnabled();

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Use original filedate if possible")
    boolean isUseOriginalFileDate();

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Restore file permissions if possible")
    boolean isRestoreFilePermissions();

    void setRestoreFilePermissions(boolean b);

    void setAskForUnknownPasswordsEnabled(boolean enabled);

    void setBlacklistPatterns(String[] patterns);

    void setCPUPriority(CPUPriority priority);

    void setCustomExtractionPath(String path);

    void setCustomExtractionPathEnabled(boolean enabled);

    void setDeepExtractionEnabled(boolean enabled);

    void setDeleteInfoFilesAfterExtraction(boolean enabled);

    void setSubPath(String path);

    void setSubpathEnabled(boolean enabled);

    void setSubPathMinFilesTreshhold(int treshold);

    void setSubPathMinFoldersTreshhold(int treshold);

    void setSubPathMinFilesOrFoldersTreshhold(int treshold);

    @AboutConfig
    @DescriptionForConfigEntry("max bytes the extractor may test for finding correct password when no signature is found")
    @SpinnerValidator(min = 1000 * 1024, max = Integer.MAX_VALUE)
    @DefaultIntValue(1000 * 1024)
    int getMaxCheckedFileSizeDuringOptimizedPasswordFindingInBytes();

    void setMaxCheckedFileSizeDuringOptimizedPasswordFindingInBytes(int size);

    void setUseOriginalFileDate(boolean enabled);

    @DefaultBooleanValue(true)
    @AboutConfig
    @DescriptionForConfigEntry("This option improves password find speed a lot, but may result in finding errors.")
    boolean isPasswordFindOptimizationEnabled();

    void setPasswordFindOptimizationEnabled(boolean b);

    @DefaultBooleanValue(true)
    @AboutConfig
    @DescriptionForConfigEntry("Extract Log files in logs/extraction/...")
    boolean isWriteExtractionLogEnabled();

    void setWriteExtractionLogEnabled(boolean b);

    @DefaultBooleanValue(true)
    @AboutConfig
    @DescriptionForConfigEntry("Show Bubbles for Extration Jobs")
    boolean isBubbleEnabledIfArchiveExtractionIsInProgress();

    void setBubbleEnabledIfArchiveExtractionIsInProgress(boolean b);

    @DefaultBooleanValue(true)
    @AboutConfig
    boolean isBubbleContentDurationVisible();

    void setBubbleContentDurationVisible(boolean b);

    @DefaultBooleanValue(true)
    @AboutConfig
    boolean isBubbleContentCircleProgressVisible();

    void setBubbleContentCircleProgressVisible(boolean b);

    @DefaultBooleanValue(true)
    @AboutConfig
    boolean isBubbleContentStatusVisible();

    void setBubbleContentStatusVisible(boolean b);

    @DefaultBooleanValue(true)
    @AboutConfig
    boolean isBubbleContentCurrentFileVisible();

    void setBubbleContentCurrentFileVisible(boolean b);

    @DefaultBooleanValue(true)
    @AboutConfig
    boolean isBubbleContentExtractToFolderVisible();

    void setBubbleContentExtractToFolderVisible(boolean b);

    @DefaultBooleanValue(true)
    @AboutConfig
    boolean isBubbleContentArchivenameVisible();

    void setBubbleContentArchivenameVisible(boolean b);

    void setDeleteArchiveFilesAfterExtractionAction(FileCreationManager.DeleteOption selectedItem);

    @AboutConfig
    @DefaultEnumValue("SKIP_FILE")
    IfFileExistsAction getIfFileExistsAction();

    void setIfFileExistsAction(IfFileExistsAction action);

    IfFileExistsAction getLatestIfFileExistsAction();

    void setLatestIfFileExistsAction(IfFileExistsAction action);

    @AboutConfig
    @DescriptionForConfigEntry("Set the timeout for the 'Ask for archive password' dialog")
    @DefaultIntValue(10 * 60 * 1000)
    int getAskForPasswordDialogTimeoutInMS();

    void setAskForPasswordDialogTimeoutInMS(int ms);

    @AboutConfig
    String getLastWorkingLibID();

    public void setLastWorkingLibID(String libID);

}