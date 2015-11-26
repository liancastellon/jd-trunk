package org.jdownloader.extensions.extraction.contextmenu.downloadlist.action;

import java.awt.event.ActionEvent;
import java.util.List;

import jd.gui.swing.jdgui.JDGui;
import jd.gui.swing.jdgui.WarnLevel;

import org.appwork.utils.swing.dialog.Dialog;
import org.jdownloader.controlling.FileCreationManager;
import org.jdownloader.extensions.extraction.Archive;
import org.jdownloader.extensions.extraction.BooleanStatus;
import org.jdownloader.extensions.extraction.contextmenu.downloadlist.AbstractExtractionContextAction;
import org.jdownloader.gui.IconKey;

public class CleanupAutoDeleteFilesEnabledToggleAction extends AbstractExtractionContextAction {

    public CleanupAutoDeleteFilesEnabledToggleAction() {
        super();
        setName(org.jdownloader.extensions.extraction.translate.T._.contextmenu_autodeletefiles());
        setSmallIcon(new ExtractIconVariant(IconKey.ICON_DELETE, 18, 14, 0, 0).crop());
        setSelected(false);
    }

    @Override
    protected void onAsyncInitDone() {
        super.onAsyncInitDone();
        final List<Archive> lArchives = getArchives();
        if (lArchives != null && lArchives.size() > 0) {
            setSelected(_getExtension().getRemoveFilesAfterExtractAction(lArchives.get(0)) != FileCreationManager.DeleteOption.NO_DELETE);
        }
    }

    public void actionPerformed(ActionEvent e) {
        final List<Archive> lArchives = getArchives();
        if (!isEnabled() || lArchives == null) {
            return;
        } else {
            for (Archive archive : lArchives) {
                archive.getSettings().setRemoveFilesAfterExtraction(isSelected() ? BooleanStatus.TRUE : BooleanStatus.FALSE);
            }
            if (JDGui.bugme(WarnLevel.NORMAL)) {
                Dialog.getInstance().showMessageDialog(Dialog.STYLE_SHOW_DO_NOT_DISPLAY_AGAIN, isSelected() ? org.jdownloader.extensions.extraction.translate.T._.set_autoremovefiles_true() : org.jdownloader.extensions.extraction.translate.T._.set_autoremovefiles_false());
            }
        }
    }
}
