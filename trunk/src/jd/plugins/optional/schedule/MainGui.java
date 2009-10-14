//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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

package jd.plugins.optional.schedule;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import jd.gui.swing.jdgui.interfaces.SwitchPanel;
import jd.utils.locale.JDL;
import net.miginfocom.swing.MigLayout;

public class MainGui extends SwitchPanel implements ActionListener, MouseListener {
    private static final long serialVersionUID = 3439995751143746593L;

    private static final String JDL_PREFIX = "jd.plugins.optional.schedule.MainGui.";

    private Schedule schedule;

    private SchedulerTable table;

    private JButton add;

    private JButton delete;

    private JButton edit;

    private JTabbedPane tabs;

    public MainGui(Schedule schedule) {
        this.schedule = schedule;

        tabs = new JTabbedPane();

        table = new SchedulerTable(schedule);
        table.addMouseListener(this);

        add = new JButton("+");
        add.addActionListener(this);

        delete = new JButton("-");
        delete.addActionListener(this);
        delete.setEnabled(false);

        edit = new JButton(JDL.L(JDL_PREFIX + "edit", "Edit"));
        edit.addActionListener(this);
        edit.setEnabled(false);

        JPanel buttons = new JPanel();
        buttons.add(add);
        buttons.add(delete);
        buttons.add(edit);

        JPanel p = new JPanel(new MigLayout("ins 5,wrap 1", "[fill,grow]", "[fill,grow][]"));
        p.add(new JScrollPane(table));
        p.add(buttons);

        tabs.addTab("Main", p);

        this.setLayout(new MigLayout("ins 5,wrap 1", "[fill,grow]", "[fill,grow][]"));
        this.add(tabs);

        table.getModel().fireTableDataChanged();
    }

    public void addAction(Actions act) {
        table.getModel().fireTableDataChanged();

        removeTab(act);
    }

    public void updateAction(Actions act) {
        table.getModel().fireTableDataChanged();
        schedule.saveActions();
        removeTab(act);
        schedule.updateTable();
    }

    public void removeTab(Actions act) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getTitleAt(i).equals(act.getName())) {
                tabs.remove(i);
                return;
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == add) {
            tabs.addTab("Schedule " + Integer.valueOf(schedule.getActions().size() + 1), new AddGui(schedule, this, new Actions("Schedule " + Integer.valueOf(schedule.getActions().size() + 1)), false));
            tabs.setSelectedIndex(tabs.getTabCount() - 1);
        } else if (e.getSource() == delete) {
            if (table.getSelectedRow() < 0) return;
            removeTab(schedule.getActions().get(table.getSelectedRow()));
            schedule.removeAction(table.getSelectedRow());
            table.getModel().fireTableDataChanged();
            updateButtons();
        } else if (e.getSource() == edit) {
            if (table.getSelectedRow() < 0) return;
            Actions a = schedule.getActions().get(table.getSelectedRow());
            tabs.addTab(a.getName(), new AddGui(schedule, this, a, true));
            tabs.setSelectedIndex(tabs.getTabCount() - 1);
        }
    }

    public void changeTabText(String oldText, String newText) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getTitleAt(i).equals(oldText)) {
                tabs.setTitleAt(i, newText);
                return;
            }
        }
    }

    public void mouseClicked(MouseEvent e) {
        updateButtons();
    }

    private void updateButtons() {
        boolean b = (table.getSelectedRow() >= 0);
        delete.setEnabled(b);
        edit.setEnabled(b);
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    @Override
    protected void onHide() {
    }

    @Override
    protected void onShow() {
        table.getModel().refreshModel();
    }

    public void updateTable() {
        table.getModel().refreshModel();
        table.getModel().fireTableDataChanged();
    }

}
