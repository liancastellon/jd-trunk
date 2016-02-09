package jd.gui.swing.jdgui.views.settings.panels.linkgrabberfilter;

import java.awt.event.ActionEvent;

import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.filter.LinkgrabberFilterRule;
import org.jdownloader.gui.IconKey;
import org.jdownloader.translate._JDT;

public class DuplicateAction extends AppAction {

    private LinkgrabberFilterRule contextObject;
    private AbstractFilterTable   filterTable;

    public DuplicateAction(LinkgrabberFilterRule contextObject, AbstractFilterTable filterTable) {
        setName(_JDT.T.DuplicateAction_DuplicateAction_());
        setIconKey(IconKey.ICON_COPY);
        this.contextObject = contextObject;
        this.filterTable = filterTable;
    }

    public boolean isEnabled() {
        return contextObject != null;
    }

    public void actionPerformed(ActionEvent e) {
        LinkgrabberFilterRule newRule = contextObject.duplicate();
        NewAction.add(newRule, filterTable);
    }

}
