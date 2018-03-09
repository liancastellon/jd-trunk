package org.jdownloader.gui.views.downloads.contextmenumanager;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

import javax.swing.JComponent;

import jd.plugins.DownloadLink;

import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.contextmenu.MenuItemData;
import org.jdownloader.controlling.contextmenu.MenuLink;
import org.jdownloader.extensions.ExtensionNotLoadedException;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo.PluginView;
import org.jdownloader.gui.views.downloads.table.DownloadsTable;

public class DownloadsTablePluginLink extends MenuItemData implements MenuLink {
    @Override
    public String getName() {
        return _GUI.T.DownloadsTablePluginLink_getName_object_();
    }

    @Override
    public List<AppAction> createActionsToLink() {
        return null;
    }

    @Override
    public JComponent createSettingsPanel() {
        return null;
    }

    @Override
    public String getIconKey() {
        return IconKey.ICON_PLUGIN;
    }

    @Override
    public JComponent addTo(JComponent root) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, SecurityException, ExtensionNotLoadedException {
        final Collection<PluginView<DownloadLink>> views = DownloadsTable.getInstance().getSelectionInfo().getPluginViews();
        for (PluginView<DownloadLink> pv : views) {
            pv.getPlugin().extendDownloadsTableContextMenu(root, pv, views);
        }
        return null;
    }
}
