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

package jd.gui.skins.simple;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JDialog;
import javax.swing.JLabel;

import jd.gui.skins.simple.components.DragNDrop;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;

/**
 * Die Dropperklasse zeigt einen DropTarget Dialog an. Zieht man links oder text
 * darauf, so wird das eingefügt.
 * 
 * @author Tom
 */
public class Dropper extends JDialog {

    private static final long serialVersionUID = 8764525546298642601L;

    private JLabel label;

    private DragNDrop target;

    public Dropper() {
        super();
        setModal(false);
        setLayout(new GridBagLayout());
        target = new DragNDrop();
        label = new JLabel(JDLocale.L("gui.droptarget.label", "Ziehe Links auf mich!"));
        JDUtilities.addToGridBag(this, target, GridBagConstraints.RELATIVE, GridBagConstraints.RELATIVE, GridBagConstraints.REMAINDER, 1, 1, 1, null, GridBagConstraints.NONE, GridBagConstraints.NORTH);
        JDUtilities.addToGridBag(this, label, GridBagConstraints.RELATIVE, GridBagConstraints.RELATIVE, GridBagConstraints.REMAINDER, 1, 0, 0, null, GridBagConstraints.NONE, GridBagConstraints.SOUTH);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(50, 70);
        setResizable(false);
        setUndecorated(false);
        setTitle(JDLocale.L("gui.droptarget.title", "Linksammler aktiv (D&D + Clipboard)"));

        setLocation(20, 20);
        setAlwaysOnTop(true);

        pack();
    }

    /**
     * Setzt den südlichen Text im Target
     * 
     * @param text
     */
    public void setText(String text) {
        label.setText(text);
        pack();
    }

}
