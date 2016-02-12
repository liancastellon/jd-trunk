package org.jdownloader.controlling;

import javax.swing.Icon;

import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;

public enum Priority {
    HIGHEST(3, _GUI.T.gui_treetable_tooltip_priority3()),
    HIGHER(2, _GUI.T.gui_treetable_tooltip_priority2()),
    HIGH(1, _GUI.T.gui_treetable_tooltip_priority1()),
    DEFAULT(0, _GUI.T.gui_treetable_tooltip_priority0()),
    LOWER(-1, _GUI.T.gui_treetable_tooltip_priority_1());

    private int id;

    public int getId() {
        return id;
    }

    private String translation;

    private Priority(int p, String translation) {
        id = p;
        this.translation = translation;
    }

    public String T() {
        return translation;
    }

    public Icon loadIcon(int size) {
        return new AbstractIcon("prio_" + id, size);
    }

    public static Priority getPriority(int p) {
        if (p > 3) {
            p = 3;
        }
        if (p < -1) {
            p = -1;
        }
        switch (p) {
        case 3:
            return HIGHEST;
        case 2:
            return HIGHER;
        case 1:
            return HIGH;
        case 0:
            return DEFAULT;
        case -1:
            return LOWER;
        default:
            return DEFAULT;
        }
    }
}
