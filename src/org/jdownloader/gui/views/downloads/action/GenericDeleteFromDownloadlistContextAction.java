package org.jdownloader.gui.views.downloads.action;

import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

import org.jdownloader.controlling.contextmenu.TableContext;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.gui.views.linkgrabber.LinkGrabberTable;
import org.jdownloader.gui.views.linkgrabber.bottombar.IncludedSelectionSetup;

public class GenericDeleteFromDownloadlistContextAction extends GenericDeleteFromDownloadlistAction {
    private final TableContext tableContext;

    public GenericDeleteFromDownloadlistContextAction() {
        super();
        addContextSetup(tableContext = new TableContext(false, true));
    }

    protected void initIncludeSelectionSupport() {
        addContextSetup(includedSelection = new IncludedSelectionSetup(LinkGrabberTable.getInstance(), this, this) {
            @Override
            public void updateListeners() {
            }
        });
    }

    @Override
    public void requestUpdate(Object requestor) {
        super.requestUpdate(requestor);
        final SelectionInfo<FilePackage, DownloadLink> selection = this.selection.get();
        final boolean hasSelection = selection != null && !selection.isEmpty();
        if (hasSelection) {
            if (tableContext.isItemVisibleForSelections()) {
                setVisible(true);
            } else {
                setVisible(false);
                setEnabled(false);
            }
        } else {
            if (tableContext.isItemVisibleForEmptySelection()) {
                setVisible(true);
            } else {
                setVisible(false);
                setEnabled(false);
            }
        }
    }

    @Override
    public void initContextDefaults() {
        includedSelection.setIncludeSelectedLinks(true);
        includedSelection.setIncludeUnselectedLinks(false);
    }
}
