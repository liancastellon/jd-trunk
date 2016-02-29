package jd.controlling.reconnect.pluginsinc.speedporthybrid;

import org.appwork.storage.config.ConfigInterface;
import org.appwork.storage.config.annotations.CryptedStorage;

public interface SpeedPortHybridReconnectConfig extends ConfigInterface {
    @CryptedStorage(key = { 1, 6, 4, 5, 2, 7, 4, 3, 12, 61, 14, 75, -2, -7, -44, 33 })
    public String getPassword();

    public void setPassword(String pw);

    public String getRouterIP();

    public void setRouterIP(String pw);
}
