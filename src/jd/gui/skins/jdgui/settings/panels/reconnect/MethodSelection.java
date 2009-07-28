//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.gui.skins.jdgui.settings.panels.reconnect;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.ConfigGroup;
import jd.config.Configuration;
import jd.config.ConfigEntry.PropertyType;
import jd.controlling.JDLogger;
import jd.controlling.ProgressController;
import jd.controlling.reconnect.BatchReconnect;
import jd.controlling.reconnect.ExternReconnect;
import jd.controlling.reconnect.HTTPLiveHeader;
import jd.controlling.reconnect.ReconnectMethod;
import jd.controlling.reconnect.Reconnecter;
import jd.gui.skins.jdgui.settings.ConfigPanel;
import jd.gui.skins.jdgui.settings.GUIConfigEntry;
import jd.gui.skins.jdgui.settings.subpanels.SubPanelCLRReconnect;
import jd.gui.skins.jdgui.settings.subpanels.SubPanelLiveHeaderReconnect;
import jd.gui.skins.jdgui.swing.GuiRunnable;
import jd.gui.skins.swing.Factory;
import jd.nutils.Formatter;
import jd.utils.JDTheme;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;
import net.miginfocom.swing.MigLayout;

public class MethodSelection extends ConfigPanel implements ActionListener {
    public String getBreadcrum() {
        return JDL.L(this.getClass().getName() + ".breadcrum", this.getClass().getSimpleName());
    }

    public static String getTitle() {
        return JDL.L(JDL_PREFIX + "reconnect.title", "Reconnection");
    }

    private static final String JDL_PREFIX = "jd.gui.skins.jdgui.settings.panels.reconnect.MethodSelection.";
    private static final long serialVersionUID = 3383448498625377495L;

    private JButton btn;

    private Configuration configuration;

    private JTabbedPane tabbed;

    private JLabel currentip;

    private JLabel success;

    private JLabel beforeIPLabel;

    private JLabel beforeIP;

    private JLabel message;

    private JLabel timeLabel;

    private JLabel time;

    private JPanel method;

    public MethodSelection(Configuration configuration) {
        super();
        this.configuration = configuration;
        initPanel();
        load();
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btn) {
            save();

            JDLogger.addHeader("Reconnect Testing");

            final ProgressController progress = new ProgressController(JDL.L("gui.warning.reconnect.pleaseWait", "Bitte Warten...Reconnect läuft"), 100);

            logger.info("Start Reconnect");
            message.setText(JDL.L("gui.warning.reconnect.running", "running..."));
            message.setEnabled(true);
            beforeIP.setText(currentip.getText());
            beforeIP.setEnabled(true);
            beforeIPLabel.setEnabled(true);
            currentip.setText("?");
            final long timel = System.currentTimeMillis();

            final Thread timer = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        new GuiRunnable<Object>() {

                            @Override
                            public Object runSave() {
                                time.setText(Formatter.formatSeconds((System.currentTimeMillis() - timel) / 1000));
                                time.setEnabled(true);
                                timeLabel.setEnabled(true);
                                return null;
                            }

                        }.start();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            };
            timer.start();
            JDUtilities.getConfiguration().setProperty(ReconnectMethod.PARAM_RETRIES, 0);
            progress.setStatus(30);
            new Thread() {
                @Override
                public void run() {
                    if (Reconnecter.doManualReconnect()) {
                        progress.setStatusText(JDL.L("gui.warning.reconnectSuccess", "Reconnect successfull"));

                        message.setText(JDL.L("gui.warning.reconnectSuccess", "Reconnect successfull"));
                        success.setIcon(JDTheme.II("gui.images.selected", 32, 32));
                        success.setEnabled(true);
                        currentip.setText(JDUtilities.getIPAddress(null));
                    } else {
                        progress.setStatusText(JDL.L("gui.warning.reconnectFailed", "Reconnect failed!"));
                        progress.setColor(Color.RED);

                        message.setText(JDL.L("gui.warning.reconnectFailed", "Reconnect failed!"));
                        success.setIcon(JDTheme.II("gui.images.unselected", 32, 32));
                        success.setEnabled(true);
                        currentip.setText(JDUtilities.getIPAddress(null));
                    }

                    timer.interrupt();
                    progress.setStatus(100);
                    progress.finalize(5000);

                }
            }.start();
        }
    }

    @Override
    public void initPanel() {

        /* 0=LiveHeader, 1=Extern, 2=Batch,3=CLR */

        method = new JPanel(new MigLayout("ins 0 0 0 0,wrap 2", "[fill,grow 10]10[fill,grow]"));

        tabbed = new JTabbedPane();
        method.add(tabbed, "spanx,pushy,growy");
        // tabbed.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        // tabbed.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbed.setTabPlacement(SwingConstants.TOP);

        addLiveheader();
        addExtern();
        addBatch();
        addCLR();
        // tabbed.setSelectedIndex(configuration.getIntegerProperty(
        // ReconnectMethod.PARAM_RECONNECT_TYPE, ReconnectMethod.LIVEHEADER));
        method.add(Factory.createHeader(new ConfigGroup(JDL.L("gui.config.reconnect.test", "Showcase"), JDTheme.II("gui.images.config.network_local", 32, 32))), "spanx,gaptop 15,gapleft 20,gapright 15");
        JPanel p = new JPanel(new MigLayout(" ins 0,wrap 7", "[]5[fill]5[align right]20[align right]20[align right]20[align right]20[align right]", "[][]"));
        method.add(p, "spanx,gapright 20,gapleft 54");
        btn = new JButton(JDL.L("gui.config.reconnect.showcase.reconnect", "Change IP"));
        btn.addActionListener(this);
        p.add(btn, "spany, aligny top");
        p.add(new JPanel(), "height 32!,spany,alignx left,pushx");

        p.add(timeLabel = new JLabel(JDL.L("gui.config.reconnect.showcase.time", "Reconnect duration")));
        p.add(time = new JLabel("---"));

        timeLabel.setEnabled(false);
        time.setEnabled(false);
        p.add(new JLabel(JDL.L("gui.config.reconnect.showcase.currentip", "Your current IP")));
        p.add(currentip = new JLabel("---"));

        success = new JLabel(JDTheme.II("gui.images.selected", 32, 32));
        success.setEnabled(false);
        p.add(success, "spany,alignx right");

        p.add(message = new JLabel(JDL.L("gui.config.reconnect.showcase.message.none", "Not tested yet")), "spanx 2");
        message.setEnabled(false);

        p.add(beforeIPLabel = new JLabel(JDL.L("gui.config.reconnect.showcase.lastip", "Ip before reconnect")));
        p.add(beforeIP = new JLabel("---"));
        beforeIPLabel.setEnabled(false);
        beforeIP.setEnabled(false);

        new Thread() {
            @Override
            public void run() {
                String ip = JDUtilities.getIPAddress(null);
                currentip.setText(ip);
            }
        }.start();

        // maintabbed.addTab(JDL.L("gui.config.reconnect.methodtab",
        // "Reconnect method"), JDTheme.II("gui.images.config.network_local",
        // 16, 16), method);
        // maintabbed.addTab(JDL.L("gui.config.reconnect.settingstab",
        // "Advanced Settings"), JDTheme.II("gui.images.reconnect_settings", 16,
        // 16), cep = new ConfigEntriesPanel(container));
        setLayout(new MigLayout("ins 0,wrap 1", "[fill,grow 10]", "[fill,grow]"));
        // panel.add();
        JTabbedPane tabbed = new JTabbedPane();
        tabbed.add(getBreadcrum(), method);

        this.add(tabbed);

    }

    private void addCLR() {
        String name = JDL.L("modules.reconnect.types.clr", "CLR Script");

        tabbed.addTab(name, new SubPanelCLRReconnect(configuration));

    }

    private void addBatch() {
        String name = JDL.L("modules.reconnect.types.batch", "Batch");

        ConfigPanel cp;
        tabbed.addTab(name, cp = new ConfigPanel() {

            private static final long serialVersionUID = -6178073263504701330L;

            @Override
            public void initPanel() {
                ConfigContainer container = new BatchReconnect().getConfig();

                for (ConfigEntry cfgEntry : container.getEntries()) {
                    GUIConfigEntry ce = new GUIConfigEntry(cfgEntry);
                    if (ce != null) addGUIConfigEntry(ce);
                }

                add(panel);
            }

            @Override
            public void load() {
                loadConfigEntries();
            }

            @Override
            public void save() {
                saveConfigEntries();

            }

        });
        cp.initPanel();
        cp.load();

    }

    private void addExtern() {
        String name = JDL.L("modules.reconnect.types.extern", "Extern");

        ConfigPanel cp;
        tabbed.addTab(name, cp = new ConfigPanel() {

            private static final long serialVersionUID = 1086423194283483561L;

            @Override
            public void initPanel() {
                ConfigContainer container = new ExternReconnect().getConfig();

                for (ConfigEntry cfgEntry : container.getEntries()) {
                    GUIConfigEntry ce = new GUIConfigEntry(cfgEntry);
                    if (ce != null) addGUIConfigEntry(ce);
                }

                add(panel);
            }

            @Override
            public void load() {
                loadConfigEntries();
            }

            @Override
            public void save() {
                saveConfigEntries();

            }

        });
        cp.initPanel();
        cp.load();

    }

    private void addLiveheader() {
        String name = JDL.L("modules.reconnect.types.liveheader", "LiveHeader/Curl");

        tabbed.addTab(name, new SubPanelLiveHeaderReconnect(configuration, new HTTPLiveHeader()));

    }

    @Override
    public void load() {
        tabbed.setSelectedIndex(configuration.getIntegerProperty(ReconnectMethod.PARAM_RECONNECT_TYPE, 0));
        loadConfigEntries();
    }

    @Override
    public ConfigEntry.PropertyType hasChanges() {
        ConfigEntry.PropertyType ret = tabbed.getSelectedIndex() != configuration.getIntegerProperty(ReconnectMethod.PARAM_RECONNECT_TYPE, ReconnectMethod.LIVEHEADER) ? PropertyType.NORMAL : PropertyType.NONE;
        return PropertyType.getMax(ret, super.hasChanges(), ((ConfigPanel) tabbed.getSelectedComponent()).hasChanges());
    }

    @Override
    public void save() {

        saveConfigEntries();
        configuration.setProperty(ReconnectMethod.PARAM_RECONNECT_TYPE, tabbed.getSelectedIndex());
        ((ConfigPanel) tabbed.getSelectedComponent()).save();
        ((ConfigPanel) tabbed.getSelectedComponent()).saveConfigEntries();

    }

    // public PropertyType hasChanges() {
    //
    // PropertyType ret = PropertyType.getMax(super.hasChanges(),
    // cep.hasChanges());
    //
    // if (lh != null) {
    //
    // return lh.hasChanges().getMax(ret); }
    // if (er != null) { return er.hasChanges().getMax(ret); }
    // if (lhclr != null) { return lhclr.hasChanges().getMax(ret); }
    // return ret;
    // }

    // private void setReconnectType() {
    // if (lh != null) {
    // container.remove(lh);
    // lh = null;
    // } else if (er != null) {
    // container.remove(er);
    // er = null;
    // } else if (lhclr != null) {
    // container.remove(lhclr);
    // lhclr = null;
    // }
    // container.setBorder(BorderFactory.createTitledBorder(types[box.
    // getSelectedIndex()]));
    // switch (box.getSelectedIndex()) {
    // case 0:
    //           
    //
    // container.add(lh);
    // break;
    // case 1:
    //           
    //
    // container.add(er);
    // break;
    // case 2:
    //           
    //
    // container.add(er);
    // break;
    // case 3:
    //          
    //
    // container.add(lhclr);
    // break;
    // }
    //
    // validate();
    // }
}
