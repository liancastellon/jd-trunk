package org.jdownloader.settings;

import java.util.ArrayList;
import java.util.HashMap;

import org.appwork.storage.config.ConfigInterface;
import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.AllowStorage;
import org.appwork.storage.config.annotations.CryptedStorage;
import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.DefaultLongValue;
import org.appwork.storage.config.annotations.DefaultStorageSyncMode;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.appwork.storage.config.annotations.DevConfig;
import org.appwork.storage.config.annotations.SpinnerValidator;
import org.appwork.utils.IO.SYNC;

public interface AccountSettings extends ConfigInterface {
    // @AboutConfig
    @AllowStorage({ Object.class })
    @CryptedStorage(key = { 1, 6, 4, 5, 2, 7, 4, 3, 12, 61, 14, 75, -2, -7, -44, 33 })
    @DefaultStorageSyncMode(SYNC.META_AND_DATA)
    HashMap<String, ArrayList<AccountData>> getAccounts();

    @AllowStorage({ Object.class })
    void setAccounts(HashMap<String, ArrayList<AccountData>> data);

    @AboutConfig
    @DefaultIntValue(5)
    @SpinnerValidator(min = 1, max = Integer.MAX_VALUE)
    @DescriptionForConfigEntry("Default temporary disabled timeout (in minutes) on unknown errors while account checking!")
    int getTempDisableOnErrorTimeout();

    void setTempDisableOnErrorTimeout(int j);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DevConfig
    boolean isAutoAccountRefreshEnabled();

    void setAutoAccountRefreshEnabled(boolean b);

    @DefaultLongValue(-1)
    long getListID();

    void setListID(long id);

    @DefaultLongValue(-1)
    long getListVersion();

    void setListVersion(long id);
}
