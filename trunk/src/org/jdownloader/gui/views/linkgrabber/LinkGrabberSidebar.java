package org.jdownloader.gui.views.linkgrabber;

import java.awt.Color;

import javax.swing.Box;

import jd.gui.swing.laf.LookAndFeelController;

import org.appwork.app.gui.MigPanel;
import org.jdownloader.controlling.filter.LinkFilterSettings;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.linkgrabber.quickfilter.QuickFilterCustomTable;
import org.jdownloader.gui.views.linkgrabber.quickfilter.QuickFilterHosterTable;
import org.jdownloader.gui.views.linkgrabber.quickfilter.QuickFilterTypeTable;

public class LinkGrabberSidebar extends MigPanel {

    /**
     * 
     */
    private static final long      serialVersionUID = 4006309139115917564L;
    private MigPanel               quicksettings;
    private QuickFilterHosterTable hosterFilterTable;
    private QuickFilterTypeTable   filetypeFilterTable;
    private Header                 hosterFilter;
    private Header                 filetypeFilter;
    private Header                 quickSettingsHeader;
    private QuickFilterCustomTable customFilterTable;

    private Header                 customFilter;

    public LinkGrabberSidebar(LinkGrabberTable table) {
        super("ins 0,wrap 1", "[grow,fill]", "[][][][][][][grow,fill][]");
        int c = LookAndFeelController.getInstance().getLAFOptions().getPanelBackgroundColor();

        if (c >= 0) {
            setBackground(new Color(c));
            setOpaque(true);
        }

        hosterFilterTable = new QuickFilterHosterTable(table);
        hosterFilterTable.setVisible(LinkFilterSettings.LG_QUICKFILTER_HOSTER_VISIBLE.getValue());
        filetypeFilterTable = new QuickFilterTypeTable(table);
        filetypeFilterTable.setVisible(LinkFilterSettings.LG_QUICKFILTER_TYPE_VISIBLE.getValue());

        customFilterTable = new QuickFilterCustomTable(table);
        customFilterTable.setVisible(LinkFilterSettings.LG_QUICKFILTER_CUSTOM_VISIBLE.getValue());

        quicksettings = new MigPanel("ins 0,wrap 1", "[grow,fill]", "[]0[]0[]");

        quicksettings.add(new Checkbox(LinkFilterSettings.ADD_AT_TOP, _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_addtop(), _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_addtop_tt()));
        quicksettings.add(new Checkbox(LinkFilterSettings.AUTO_CONFIRM_ENABLED, _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_autoconfirm(), _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_autoconfirm_tt()));
        quicksettings.add(new Checkbox(LinkFilterSettings.AUTO_START_ENABLED, _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_autostart(), _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_autostart_tt()));

        quicksettings.add(new Checkbox(LinkFilterSettings.LINK_FILTER_ENABLED, _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_globfilter(), _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_globfilter_tt()));
        hosterFilter = new Header(LinkFilterSettings.LG_QUICKFILTER_HOSTER_VISIBLE, _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_hosterfilter()) {

            /**
             * 
             */
            private static final long serialVersionUID = 1488271787955778046L;

            @Override
            protected void setContentsVisible(boolean selected) {
                hosterFilterTable.setVisible(selected);
            }

        };

        filetypeFilter = new Header(LinkFilterSettings.LG_QUICKFILTER_TYPE_VISIBLE, _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_extensionfilter()) {

            /**
             * 
             */
            private static final long serialVersionUID = 2113097293812798851L;

            @Override
            protected void setContentsVisible(boolean selected) {
                filetypeFilterTable.setVisible(selected);
            }

        };

        customFilter = new Header(LinkFilterSettings.LG_QUICKFILTER_CUSTOM_VISIBLE, _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_customfilter()) {

            /**
             * 
             */
            private static final long serialVersionUID = 2113097293812798851L;

            @Override
            protected void setContentsVisible(boolean selected) {
                customFilterTable.setVisible(selected);
            }

        };

        quickSettingsHeader = new Header(LinkFilterSettings.LG_QUICKSETTINGS_VISIBLE, _GUI._.LinkGrabberSidebar_LinkGrabberSidebar_settings()) {

            /**
             * 
             */
            private static final long serialVersionUID = 1847991962274945882L;

            @Override
            protected void setContentsVisible(boolean selected) {
                quicksettings.setVisible(selected);
            }

        };

        add(hosterFilter, "gaptop 7");
        add(hosterFilterTable, "hidemode 2");
        add(filetypeFilter, "gaptop 7");
        add(filetypeFilterTable, "hidemode 2");
        add(customFilter, "gaptop 7");
        add(customFilterTable, "hidemode 2");
        add(Box.createGlue());
        add(quickSettingsHeader, "gaptop 7");
        add(quicksettings, "hidemode 2");

    }

}
