package org.jdownloader.gui.views.downloads;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import jd.gui.swing.laf.LookAndFeelController;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

import org.appwork.app.gui.MigPanel;
import org.appwork.storage.config.JsonConfig;
import org.appwork.swing.components.ExtButton;
import org.jdownloader.actions.AppAction;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.components.SearchCategory;
import org.jdownloader.gui.views.downloads.action.ClearAction;
import org.jdownloader.gui.views.downloads.action.RemoveOptionsAction;
import org.jdownloader.gui.views.downloads.table.DownloadsTable;
import org.jdownloader.gui.views.linkgrabber.SearchField;
import org.jdownloader.gui.views.linkgrabber.actions.AddLinksAction;
import org.jdownloader.gui.views.linkgrabber.actions.AddOptionsAction;
import org.jdownloader.settings.GraphicalUserInterfaceSettings;
import org.jdownloader.settings.staticreferences.GUI;

public class BottomBar extends MigPanel {

    private GraphicalUserInterfaceSettings         config;
    private JButton                                addLinks;

    private JButton                                clearAll;
    private JButton                                popup;
    private JButton                                popupRemove;
    private SearchField<FilePackage, DownloadLink> searchField;
    private JComboBox                              combo;

    // private JToggleButton showHideSidebar;

    public BottomBar(DownloadsTable table) {
        super("ins 0 0 1 0", "[]1[][]1[]", "[]");

        config = JsonConfig.create(GraphicalUserInterfaceSettings.class);

        addLinks = new JButton(new AddLinksAction());

        clearAll = new JButton(new ClearAction());
        popup = new JButton(new AddOptionsAction(addLinks)) {
            /**
             * 
             */
            private static final long serialVersionUID = 1L;

            public void setBounds(int x, int y, int width, int height) {
                super.setBounds(x - 2, y, width + 2, height);
            }
        };
        popupRemove = new JButton(new RemoveOptionsAction(table, clearAll)) {
            /**
             * 
             */
            private static final long serialVersionUID = 1L;

            public void setBounds(int x, int y, int width, int height) {
                super.setBounds(x - 2, y, width + 2, height);
            }
        };
        searchField = new SearchField<FilePackage, DownloadLink>(table) {

            @Override
            public void setSelectedCategory(SearchCategory selectedCategory) {
                super.setSelectedCategory(selectedCategory);
                JsonConfig.create(GraphicalUserInterfaceSettings.class).setSelectedDownloadSearchCategory(selectedCategory);
            }

        };
        searchField.setSelectedCategory(JsonConfig.create(GraphicalUserInterfaceSettings.class).getSelectedDownloadSearchCategory());
        searchField.setCategories(new SearchCategory[] { SearchCategory.FILENAME, SearchCategory.HOSTER, SearchCategory.PACKAGE });
        searchField.addKeyListener(new KeyListener() {

            public void keyTyped(KeyEvent e) {
            }

            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.setText("");

                }
            }

            public void keyPressed(KeyEvent e) {
            }
        });
        add(addLinks, "height 24!,aligny top");

        add(popup, "height 24!,width 12!,aligny top");
        add(clearAll, "width 24!,height 24!,aligny top");
        add(popupRemove, "height 24!,width 12!,aligny top");

        add(searchField, "height 24!,aligny top,gapleft 3,pushx,growx");
        combo = new JComboBox(new View[] { View.ALL, View.RUNNING, View.FAILED, View.SUCCESSFUL });
        final ListCellRenderer org = combo.getRenderer();
        combo.setRenderer(new ListCellRenderer() {

            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component comp = org.getListCellRendererComponent(list, ((View) value).getLabel(), index, isSelected, cellHasFocus);
                try {
                    JLabel ret = (JLabel) comp;
                    ret.setIcon(((View) value).getIcon());
                    comp = ret;
                } catch (Throwable e) {

                }
                return comp;
            }
        });
        combo.setSelectedItem(GUI.DOWNLOAD_VIEW.getValue());
        add(combo, "height 24!,aligny top");
        combo.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                JsonConfig.create(GraphicalUserInterfaceSettings.class).setDownloadView((View) combo.getSelectedItem());
            }
        });
        if (config.isShowMoveToTopButton()) {

            addButton(table.getMoveTopAction());
        }
        if (config.isShowMoveUpButton()) {

            addButton(table.getMoveUpAction());
        }
        if (config.isShowMoveDownButton()) {

            addButton(table.getMoveDownAction());
        }
        if (config.isShowMoveToBottomButton()) {

            addButton(table.getMoveToBottomAction());
        }
        addButton(new AppAction() {
            {
                setTooltipText(_GUI._.BottomBar_BottomBar_settings());
                setIconKey("settings");
                // setIconSizes(18);
            }

            public void actionPerformed(ActionEvent e) {
                QuickSettingsPopup pu = new QuickSettingsPopup();
                int[] insets = LookAndFeelController.getInstance().getLAFOptions().getPopupBorderInsets();
                pu.show((Component) e.getSource(), -pu.getPreferredSize().width + insets[3] + ((Component) e.getSource()).getWidth() + 1, -pu.getPreferredSize().height + insets[2]);
                // new CleanupMenu()
            }
        });
        // showHideSidebar = new JToggleButton(new AppAction() {
        // /**
        // *
        // */
        // private static final long serialVersionUID = 1L;
        //
        // {
        //
        // putValue(SMALL_ICON, NewTheme.I().getIcon("sidebar", -1));
        // setTooltipText(_GUI._.LinkGrabberPanel_LinkGrabberPanel_btn_showsidebar_tt_up());
        //
        // }
        //
        // public void actionPerformed(ActionEvent e) {
        // org.jdownloader.settings.statics.GUI.CFG.setDownloadViewSidebarVisible(!org.jdownloader.settings.statics.GUI.CFG.isDownloadViewSidebarVisible());
        // }
        // });
        //
        // if
        // (org.jdownloader.settings.statics.GUI.DOWNLOAD_VIEW_SIDEBAR_TOGGLE_BUTTON_ENABLED.getValue()
        // &&
        // org.jdownloader.settings.statics.GUI.DOWNLOAD_VIEW_SIDEBAR_ENABLED.getValue())
        // {
        // //
        // add(showHideSidebar, "height 24!,width 24!,gapleft 3,aligny top");
        // }

    }

    private void addButton(AppAction action) {
        ExtButton bt = new ExtButton(action);
        // bt.setText("");
        // bt.setRolloverEffectEnabled(true);

        add(bt, "width 24!,height 24!,gapleft 3,aligny top");
    }

}
