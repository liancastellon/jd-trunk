package jd;

import java.io.File;
import java.util.ArrayList;

import org.appwork.storage.config.StorageHandler;
import org.appwork.utils.Application;

public class GeneralSettingsDefaults implements GeneralSettings {

    public String getDefaultDownloadFolder() {

        File home = new File(System.getProperty("user.home"));
        if (home.exists() && home.isDirectory()) {
            // new File(home, "downloads").mkdirs();
            return new File(home, "downloads").getAbsolutePath();

        } else {
            return Application.getResource("downloads").getAbsolutePath();

        }

    }

    public ArrayList<String[]> getDownloadFolderHistory() {
        return new ArrayList<String[]>();
    }

    public StorageHandler<?> getStorageHandler() {
        return null;
    }

    public boolean isAddNewLinksOnTop() {
        return false;
    }

    public boolean isAutoaddLinksAfterLinkcheck() {
        return false;
    }

    public boolean isAutoDownloadStartAfterAddingEnabled() {
        return true;
    }

    public boolean isAutoOpenContainerAfterDownload() {
        return true;
    }

    public boolean isAutoStartDownloadsOnStartupEnabled() {
        return false;
    }

    public boolean isCreatePackageNameSubFolderEnabled() {
        return true;
    }

    public boolean isHashCheckEnabled() {
        return true;
    }

    public void setAddNewLinksOnTop(boolean selected) {
    }

    public void setAutoaddLinksAfterLinkcheck(boolean selected) {
    }

    public void setAutoDownloadStartAfterAddingEnabled(boolean selected) {
    }

    public void setAutoOpenContainerAfterDownload(boolean b) {
    }

    public void setAutoStartDownloadsOnStartupEnabled(boolean b) {
    }

    public void setCreatePackageNameSubFolderEnabled(boolean b) {
    }

    public void setDefaultDownloadFolder(String ddl) {
    }

    public void setDownloadFolderHistory(ArrayList<String[]> history) {
    }

    public void setHashCheckEnabled(boolean b) {
    }

}
