package org.jdownloader.gui.views.downloads.columns;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;

import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.controlling.packagecontroller.AbstractNode;
import jd.controlling.proxy.PacProxySelectorImpl;
import jd.controlling.proxy.ProxyController;
import jd.controlling.proxy.SelectedProxy;
import jd.controlling.reconnect.ipcheck.BalancedWebIPCheck;
import jd.controlling.reconnect.ipcheck.IP;
import jd.controlling.reconnect.ipcheck.IPCheckException;
import jd.gui.swing.jdgui.GUIUtils;
import jd.http.ProxySelectorInterface;
import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForHost;
import jd.plugins.download.DownloadInterface;
import net.miginfocom.swing.MigLayout;

import org.appwork.scheduler.DelayedRunnable;
import org.appwork.swing.MigPanel;
import org.appwork.swing.components.tooltips.ExtTooltip;
import org.appwork.swing.components.tooltips.ToolTipController;
import org.appwork.swing.components.tooltips.TooltipPanel;
import org.appwork.swing.exttable.ExtColumn;
import org.appwork.swing.exttable.ExtDefaultRowSorter;
import org.appwork.utils.net.httpconnection.HTTPProxy;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.SwingUtils;
import org.appwork.utils.swing.renderer.RenderLabel;
import org.appwork.utils.swing.renderer.RendererMigPanel;
import org.jdownloader.DomainInfo;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.NewTheme;
import org.jdownloader.plugins.SkipReason;

public class ConnectionColumn extends ExtColumn<AbstractNode> {
    /**
     *
     */
    private static final long serialVersionUID   = 1L;
    private MigPanel          panel;
    private RenderLabel[]     labels;
    private final Icon        resumeIndicator;
    private final Icon        directConnection;
    private final Icon        proxyConnection;
    private final Icon        connections;
    private final int         DEFAULT_ICON_COUNT = 4;
    private final Icon        skipped;
    private final Icon        forced;
    private DownloadWatchDog  dlWatchdog;
    private final Icon        url;

    public JPopupMenu createHeaderPopup() {
        return FileColumn.createColumnPopup(this, getMinWidth() == getMaxWidth() && getMaxWidth() > 0);
    }

    public ConnectionColumn() {
        super(_GUI.T.ConnectionColumn_ConnectionColumn(), null);
        panel = new RendererMigPanel("ins 0 0 0 0", "[]", "[grow,fill]");
        labels = new RenderLabel[DEFAULT_ICON_COUNT + 1];
        // panel.add(Box.createGlue(), "pushx,growx");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= DEFAULT_ICON_COUNT; i++) {
            labels[i] = new RenderLabel();
            // labels[i].setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1,
            // Color.RED));
            labels[i].setOpaque(false);
            labels[i].setBackground(null);
            if (sb.length() > 0) {
                sb.append("1");
            }
            sb.append("[18!]");
            panel.add(labels[i]);
        }
        dlWatchdog = DownloadWatchDog.getInstance();
        skipped = NewTheme.I().getIcon(IconKey.ICON_SKIPPED, 16);
        forced = NewTheme.I().getIcon(IconKey.ICON_MEDIA_PLAYBACK_START_FORCED, 16);
        resumeIndicator = NewTheme.I().getIcon(IconKey.ICON_REFRESH, 16);
        directConnection = NewTheme.I().getIcon(IconKey.ICON_MODEM, 16);
        proxyConnection = NewTheme.I().getIcon(IconKey.ICON_PROXY_ROTATE, 16);
        connections = NewTheme.I().getIcon(IconKey.ICON_CHUNKS, 16);
        url = NewTheme.I().getIcon(IconKey.ICON_URL, 16);
        panel.setLayout(new MigLayout("ins 0 0 0 0", sb.toString(), "[grow,fill]"));
        // panel.add(Box.createGlue(), "pushx,growx");
        this.setRowSorter(new ExtDefaultRowSorter<AbstractNode>() {
            @Override
            public int compare(final AbstractNode o1, final AbstractNode o2) {
                final long l1 = getDownloads(o1);
                final long l2 = getDownloads(o2);
                if (l1 == l2) {
                    return 0;
                }
                if (this.getSortOrderIdentifier() == ExtColumn.SORT_ASC) {
                    return l1 > l2 ? -1 : 1;
                } else {
                    return l1 < l2 ? -1 : 1;
                }
            }
        });
        resetRenderer();
    }

    @Override
    public boolean onSingleClick(MouseEvent e, AbstractNode obj) {
        return super.onSingleClick(e, obj);
    }

    @Override
    public boolean onDoubleClick(MouseEvent e, AbstractNode obj) {
        if (obj instanceof DownloadLink) {
            ConnectionTooltip tt = new ConnectionTooltip((DownloadLink) obj) {
                public boolean isLastHiddenEnabled() {
                    return false;
                }
            };
            ToolTipController.getInstance().show(tt);
            return true;
        }
        return false;
    }

    private int getDownloads(AbstractNode value) {
        if (value instanceof DownloadLink) {
            SingleDownloadController dlc = ((DownloadLink) value).getDownloadLinkController();
            if (dlc != null) {
                DownloadInterface dli = dlc.getDownloadInstance();
                if (dli != null) {
                    return 1;
                }
            }
        } else if (value instanceof FilePackage) {
            return DownloadWatchDog.getInstance().getDownloadsbyFilePackage((FilePackage) value);
        }
        return 0;
    }

    @Override
    public Object getCellEditorValue() {
        return null;
    }

    @Override
    protected boolean isDefaultResizable() {
        return false;
    }

    @Override
    public boolean isEditable(AbstractNode obj) {
        return false;
    }

    @Override
    public boolean isEnabled(AbstractNode obj) {
        if (obj instanceof DownloadLink) {
            return ((DownloadLink) obj).isEnabled();
        }
        return false;
    }

    @Override
    public boolean isSortable(AbstractNode obj) {
        return true;
    }

    @Override
    public void setValue(Object value, AbstractNode object) {
    }

    @Override
    public int getDefaultWidth() {
        return DEFAULT_ICON_COUNT * 19 + 14;
    }

    public void configureRendererComponent(AbstractNode value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof DownloadLink) {
            final DownloadLink dlLink = (DownloadLink) value;
            final SingleDownloadController sdc = dlLink.getDownloadLinkController();
            final DownloadInterface dli;
            if (sdc != null) {
                dli = sdc.getDownloadInstance();
            } else {
                dli = null;
            }
            int index = 0;
            if (dlLink.isSkipped()) {
                labels[index].setIcon(skipped);
                labels[index].setVisible(true);
                index++;
            }
            if (dlWatchdog.isLinkForced(dlLink)) {
                labels[index].setIcon(forced);
                labels[index].setVisible(true);
                index++;
            }
            if (dlLink.isResumeable()) {
                labels[index].setIcon(resumeIndicator);
                labels[index].setVisible(true);
                index++;
            }
            if (dli != null && sdc != null) {
                HTTPProxy proxy = sdc.getUsedProxy();
                if (proxy != null && proxy.isRemote()) {
                    labels[index].setIcon(proxyConnection);
                    labels[index].setVisible(true);
                } else {
                    labels[index].setIcon(directConnection);
                    labels[index].setVisible(true);
                }
                index++;
                if (sdc.getAccount() != null && sdc.getAccount().getPlugin() != null) {
                    final PluginForHost plugin = sdc.getAccount().getPlugin();
                    final DomainInfo domainInfo = DomainInfo.getInstance(plugin.getHost(dlLink, sdc.getAccount()));
                    if (domainInfo != null) {
                        final Icon icon = domainInfo.getFavIcon();
                        labels[index].setIcon(icon);
                        labels[index].setVisible(true);
                        index++;
                    }
                }
                labels[index].setText("" + dli.getManagedConnetionHandler().size());
                labels[index].setIcon(connections);
                labels[index].setVisible(true);
            }
        }
    }

    @Override
    public JComponent getEditorComponent(AbstractNode value, boolean isSelected, int row, int column) {
        return null;
    }

    @Override
    public JComponent getRendererComponent(AbstractNode value, boolean isSelected, boolean hasFocus, int row, int column) {
        return panel;
    }

    @Override
    public void resetEditor() {
    }

    @Override
    public ExtTooltip createToolTip(Point position, AbstractNode obj) {
        if (obj instanceof DownloadLink) {
            ConnectionTooltip ret = new ConnectionTooltip((DownloadLink) obj);
            if (ret.getComponentCount() > 0) {
                return ret;
            }
        }
        return null;
    }

    private static final AtomicLong               TASK      = new AtomicLong(0);
    private static final ScheduledExecutorService SCHEDULER = DelayedRunnable.getNewScheduledExecutorService();

    private class ConnectionTooltip extends ExtTooltip {
        /**
         *
         */
        private static final long serialVersionUID = -6581783135666367021L;

        public ConnectionTooltip(final DownloadLink link) {
            JLabel lbl;
            this.panel = new TooltipPanel("ins 3,wrap 1", "[grow,fill]", "[grow,fill]");
            final SingleDownloadController sdc = link.getDownloadLinkController();
            final DownloadInterface dli;
            if (sdc != null) {
                dli = sdc.getDownloadInstance();
            } else {
                dli = null;
            }
            {
                if (dlWatchdog.isLinkForced(link)) {
                    panel.add(lbl = new JLabel(_GUI.T.ConnectionColumn_DownloadIsForced(), forced, JLabel.LEADING));
                    SwingUtils.setOpaque(lbl, false);
                    lbl.setForeground(new Color(this.getConfig().getForegroundColor()));
                }
                final SkipReason skipReason = link.getSkipReason();
                if (skipReason != null) {
                    panel.add(lbl = new JLabel(skipReason.getExplanation(ConnectionColumn.this), skipped, JLabel.LEADING));
                    SwingUtils.setOpaque(lbl, false);
                    lbl.setForeground(new Color(this.getConfig().getForegroundColor()));
                }
                /* is the Link resumeable */
                if (link.isResumeable()) {
                    panel.add(lbl = new JLabel(_GUI.T.ConnectionColumn_DownloadIsResumeable(), resumeIndicator, JLabel.LEADING));
                    SwingUtils.setOpaque(lbl, false);
                    lbl.setForeground(new Color(this.getConfig().getForegroundColor()));
                }
            }
            if (sdc != null) {
                {
                    /* connection? */
                    HTTPProxy proxy = sdc.getUsedProxy();
                    if (proxy == null) {
                        proxy = HTTPProxy.NONE;
                    }
                    final SelectedProxy selectedProxy = ProxyController.getSelectedProxy(proxy);
                    final String proxyString;
                    if (selectedProxy != null && selectedProxy.getSelector() != null) {
                        if (selectedProxy.getSelector() instanceof PacProxySelectorImpl) {
                            proxyString = selectedProxy.getSelector().toDetailsString() + "@" + proxy.toString();
                        } else {
                            proxyString = selectedProxy.getSelector().toDetailsString();
                        }
                    } else {
                        proxyString = proxy.toString();
                    }
                    panel.add(lbl = new JLabel(_GUI.T.ConnectionColumn_getStringValue_connection(proxyString + " (000.000.000.000)"), proxy.isRemote() ? proxyConnection : directConnection, JLabel.LEADING));
                    SwingUtils.setOpaque(lbl, false);
                    lbl.setForeground(new Color(this.getConfig().getForegroundColor()));
                    final HTTPProxy finalProxy = proxy;
                    final JLabel finalLbl = lbl;
                    final long taskID = TASK.incrementAndGet();
                    SCHEDULER.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (taskID == TASK.get()) {
                                final List<HTTPProxy> proxies = new ArrayList<HTTPProxy>();
                                proxies.add(finalProxy);
                                final BalancedWebIPCheck ipCheck = new BalancedWebIPCheck(new ProxySelectorInterface() {
                                    @Override
                                    public boolean updateProxy(Request request, int retryCounter) {
                                        return false;
                                    }

                                    @Override
                                    public boolean reportConnectException(Request request, int retryCounter, IOException e) {
                                        return false;
                                    }

                                    @Override
                                    public List<HTTPProxy> getProxiesByURL(URL uri) {
                                        return proxies;
                                    }
                                });
                                try {
                                    final IP ip = ipCheck.getExternalIP();
                                    new EDTRunner() {
                                        @Override
                                        protected void runInEDT() {
                                            finalLbl.setText(_GUI.T.ConnectionColumn_getStringValue_connection(proxyString + " (" + ip.getIP() + ")"));
                                        }
                                    };
                                } catch (IPCheckException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                    });
                }
                if (sdc.getAccount() != null && sdc.getAccount().getPlugin() != null) {
                    /* account in use? */
                    final PluginForHost plugin = sdc.getAccount().getPlugin();
                    final DomainInfo domainInfo = DomainInfo.getInstance(plugin.getHost(link, sdc.getAccount()));
                    if (domainInfo != null) {
                        final Icon icon = domainInfo.getFavIcon();
                        panel.add(lbl = new JLabel(_GUI.T.ConnectionColumn_DownloadUsesAccount(GUIUtils.getAccountName(sdc.getAccount().getUser())), icon, JLabel.LEADING));
                        SwingUtils.setOpaque(lbl, false);
                        lbl.setForeground(new Color(this.getConfig().getForegroundColor()));
                    }
                }
            }
            if (dli != null) {
                final URLConnectionAdapter con = dli.getConnection();
                if (con != null) {
                    panel.add(lbl = new JLabel(_GUI.T.ConnectionColumn_getStringValue_from(con.getURL().getProtocol() + "@" + dli.getDownloadable().getHost()), url, JLabel.LEADING));
                } else {
                    panel.add(lbl = new JLabel(_GUI.T.ConnectionColumn_getStringValue_from(dli.getDownloadable().getHost()), url, JLabel.LEADING));
                }
                SwingUtils.setOpaque(lbl, false);
                lbl.setForeground(new Color(this.getConfig().getForegroundColor()));
                panel.add(lbl = new JLabel(_GUI.T.ConnectionColumn_getStringValue_chunks(dli.getManagedConnetionHandler().size()), connections, JLabel.LEADING));
                SwingUtils.setOpaque(lbl, false);
                lbl.setForeground(new Color(this.getConfig().getForegroundColor()));
            }
            this.panel.setOpaque(false);
            if (panel.getComponentCount() > 0) {
                add(panel);
            }
        }

        @Override
        public TooltipPanel createContent() {
            return null;
        }

        @Override
        public String toText() {
            return null;
        }
    }

    @Override
    public void resetRenderer() {
        for (int i = 0; i <= DEFAULT_ICON_COUNT; i++) {
            labels[i].setVisible(false);
            labels[i].setText(null);
        }
        this.panel.setOpaque(false);
        this.panel.setBackground(null);
    }

    @Override
    public void configureEditorComponent(AbstractNode value, boolean isSelected, int row, int column) {
    }
}