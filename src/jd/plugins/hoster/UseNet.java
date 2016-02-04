package jd.plugins.hoster;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.http.BrowserSettingsThread;
import jd.http.NoGateWayException;
import jd.http.ProxySelectorInterface;
import jd.http.SocketConnectionFactory;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.UsenetFile;
import jd.plugins.components.UsenetFileSegment;
import jd.plugins.download.usenet.SimpleUseNetDownloadInterface;

import org.appwork.utils.Application;
import org.appwork.utils.net.httpconnection.HTTPProxy;
import org.appwork.utils.net.usenet.InvalidAuthException;
import org.appwork.utils.net.usenet.MessageBodyNotFoundException;
import org.appwork.utils.net.usenet.SimpleUseNet;
import org.appwork.utils.net.usenet.UnrecognizedCommandException;

@HostPlugin(revision = "$Revision: 31032 $", interfaceVersion = 2, names = { "usenet" }, urls = { "usenet://.+" }, flags = { 0 })
public class UseNet extends PluginForHost {

    private final String USENET_SELECTED_PORT    = "usenet_selected_port";
    private final String USENET_SELECTED_SSLPORT = "usenet_selected_sslport";
    private final String USENET_PREFER_SSL       = "usenet_prefer_ssl";

    public UseNet(PluginWrapper wrapper) {
        super(wrapper);
        setUseNetConfigElements();
    }

    protected boolean isUsenetLink(DownloadLink link) {
        return link != null && "usenet".equals(link.getHost());
    }

    public void setUseNetConfigElements() {
        if (!"usenet".equals(getHost())) {
            final Integer[] ports = getPortSelection(getAvailablePorts());
            if (ports.length > 1) {
                getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX_INDEX, getPluginConfig(), USENET_SELECTED_PORT, ports, "Select (Usenet)ServerPort").setDefaultValue(0));
            }
            if (supportsSSL()) {
                final Integer[] sslPorts = getPortSelection(getAvailableSSLPorts());
                if (sslPorts.length > 1) {
                    getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX_INDEX, getPluginConfig(), USENET_SELECTED_SSLPORT, sslPorts, "Select (Usenet)ServerPort(SSL)").setDefaultValue(0));
                }
                if (sslPorts.length > 0 && ports.length > 0) {
                    getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), USENET_PREFER_SSL, "Use (Usenet)SSL?").setDefaultValue(false));
                }
            }
        }
    }

    protected boolean supportsSSL() {
        return (getAvailableSSLPorts().length > 0 && Application.getJavaVersion() >= Application.JAVA17);
    }

    protected boolean useSSL() {
        return Application.getJavaVersion() >= Application.JAVA17 && getPluginConfig().getBooleanProperty(USENET_PREFER_SSL, false) && getAvailableSSLPorts().length > 0;
    }

    protected int getPort(final String ID, int[] ports) {
        if (ports.length == 0) {
            return -1;
        } else {
            final int index = getPluginConfig().getIntegerProperty(ID, 0);
            if (index >= ports.length) {
                return ports[0];
            } else {
                return ports[index];
            }
        }
    }

    protected String getUsername(final Account account) {
        return account.getUser();
    }

    protected String getPassword(final Account account) {
        return account.getPass();
    }

    protected Integer[] getPortSelection(int[] ports) {
        final Integer[] ret = new Integer[ports.length];
        for (int i = 0; i < ports.length; i++) {
            ret[i] = ports[i];
        }
        return ret;
    }

    @Override
    public String getAGBLink() {
        return null;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink parameter) throws Exception {
        if (isIncomplete(parameter)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return AvailableStatus.UNCHECKABLE;
    }

    protected ProxySelectorInterface getProxySelector() {
        return BrowserSettingsThread.getThreadProxySelector();
    }

    protected boolean isIncomplete(DownloadLink link) {
        return link.getBooleanProperty("incomplete", Boolean.FALSE);
    }

    protected void setIncomplete(DownloadLink link, boolean b) {
        link.setProperty("incomplete", Boolean.valueOf(b));
    }

    @Override
    public void handleFree(DownloadLink link) throws Exception {
        /* handle free should never be called */
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        /* handle premium should never be called */
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return 1;
    }

    protected String getServerAddress() throws Exception {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    protected String getSSLServerAddress() throws Exception {
        return null;
    }

    protected int[] getAvailablePorts() {
        return new int[] { 119 };
    }

    protected int[] getAvailableSSLPorts() {
        return new int[0];
    }

    private final AtomicReference<SimpleUseNet> client = new AtomicReference<SimpleUseNet>(null);

    @Override
    public void handleMultiHost(DownloadLink downloadLink, Account account) throws Exception {
        final UsenetFile usenetFile = UsenetFile._read(downloadLink);
        if (usenetFile == null) {
            logger.info("UsenetFile is missing!");
            setIncomplete(downloadLink, true);
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final List<HTTPProxy> proxies = selectProxies();
        final SimpleUseNet client = new SimpleUseNet(proxies.get(0), getLogger()) {

            @Override
            protected Socket createSocket() {
                return SocketConnectionFactory.createSocket(getProxy());
            }

        };
        this.client.set(client);
        try {
            final String username = getUsername(account);
            final String password = getPassword(account);
            final boolean useSSL;
            final String serverAddress;
            final int port;
            if (useSSL()) {
                final String sslServerAddress = getSSLServerAddress();
                if (sslServerAddress != null) {
                    serverAddress = sslServerAddress;
                } else {
                    serverAddress = getServerAddress();
                }
                useSSL = true;
                port = getPort(USENET_SELECTED_SSLPORT, getAvailableSSLPorts());
            } else {
                serverAddress = getServerAddress();
                useSSL = false;
                port = getPort(USENET_SELECTED_PORT, getAvailablePorts());
            }
            if (serverAddress == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (port == -1) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            client.connect(serverAddress, port, useSSL, username, password);
        } catch (InvalidAuthException e) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
        }
        // checkCompleteness(downloadLink, client, usenetFile);
        dl = new SimpleUseNetDownloadInterface(client, downloadLink, usenetFile);
        try {
            dl.startDownload();
        } catch (MessageBodyNotFoundException e) {
            setIncomplete(downloadLink, true);
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
    }

    @Override
    public long getAvailableStatusTimeout(DownloadLink link, AvailableStatus availableStatus) {
        if (isUsenetLink(link)) {
            if (availableStatus != null) {
                switch (availableStatus) {
                case TRUE:
                case FALSE:
                case UNCHECKABLE:
                    return 10 * 60 * 1000l;
                default:
                    return 2 * 60 * 1000l;
                }
            } else {
                return 1 * 60 * 1000l;
            }
        } else {
            return super.getAvailableStatusTimeout(link, availableStatus);
        }
    }

    protected AvailableStatus checkCompleteness(DownloadLink link, SimpleUseNet client, UsenetFile usenetFile) throws Exception {
        if (isIncomplete(link)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final long lastAvailableStatusChange = link.getLastAvailableStatusChange();
        final long availableStatusChangeTimeout = getAvailableStatusTimeout(link, link.getAvailableStatus());
        AvailableStatus status = AvailableStatus.UNCHECKED;
        if (lastAvailableStatusChange + availableStatusChangeTimeout < System.currentTimeMillis()) {
            try {
                for (final UsenetFileSegment segment : usenetFile.getSegments()) {
                    if (!client.isMessageExisting(segment.getMessageID())) {
                        setIncomplete(link, true);
                        status = AvailableStatus.FALSE;
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                }
                status = AvailableStatus.TRUE;
            } catch (final UnrecognizedCommandException e) {
                status = AvailableStatus.UNCHECKABLE;
            } finally {
                link.setAvailableStatus(status);
            }
        }
        return status;
    }

    @Override
    public void clean() {
        try {
            try {
                final SimpleUseNet client = this.client.getAndSet(null);
                if (client != null) {
                    client.quit();
                }
            } catch (final Throwable e) {
            }
        } finally {
            super.clean();
        }
    }

    protected List<HTTPProxy> selectProxies() throws IOException {
        final ProxySelectorInterface selector = getProxySelector();
        if (selector == null) {
            final ArrayList<HTTPProxy> ret = new ArrayList<HTTPProxy>();
            ret.add(HTTPProxy.NONE);
            return ret;
        }
        final List<HTTPProxy> list;
        try {
            list = selector.getProxiesByURI(new URI("socket://" + getHost()));
        } catch (Throwable e) {
            throw new NoGateWayException(selector, e);
        }
        if (list == null || list.size() == 0) {
            throw new NoGateWayException(selector, "No Gateway or Proxy Found");
        }
        return list;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public Boolean siteTesterDisabled() {
        return true;
    }
}
