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

package jd.gui.skins.simple.config.subpanels;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import jd.config.Configuration;
import jd.controlling.interaction.PackageManager;
import jd.gui.skins.simple.config.ConfigPanel;
import jd.update.PackageData;
import jd.utils.JDLocale;
import net.miginfocom.swing.MigLayout;

/**
 * @author JD-Team
 */
public class SubPanelOptionalInstaller extends ConfigPanel implements ActionListener {

    private class InternalTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return getValueAt(0, columnIndex).getClass();
        }

        public int getColumnCount() {
            return 5;
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
            case 0:
                return JDLocale.L("gui.config.packagemanager.column_name", "Paket");
            case 1:
                return JDLocale.L("gui.config.packagemanager.column_category", "Kategorie");
            case 2:
                return JDLocale.L("gui.config.packagemanager.column_latestVersion", "Akt. Version");
            case 3:
                return JDLocale.L("gui.config.packagemanager.column_installedVersion", "Inst. Version");
            case 4:
                return JDLocale.L("gui.config.packagemanager.column_select", "Auswählen");
            }
            return super.getColumnName(column);
        }

        public int getRowCount() {
            return packageData.size();
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            try {
                PackageData element = packageData.get(rowIndex);

                switch (columnIndex) {
                case 0:
                    return element.getStringProperty("name");
                case 1:
                    return element.getStringProperty("category");
                case 2:
                    return element.getStringProperty("version");
                case 3:
                    return String.valueOf(element.getInstalledVersion());
                case 4:
                    return element.isSelected();
                }
            } catch (Exception e) {

            }
            return "";
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 4;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 4) {
                PackageData element = packageData.get(row);
                element.setSelected(!element.isSelected());
            }
        }
    }

    private static final long serialVersionUID = 1L;

    private JButton btnReset;

    private ArrayList<PackageData> packageData = new ArrayList<PackageData>();

    private JTable table;

    private InternalTableModel tableModel;

    public SubPanelOptionalInstaller(Configuration configuration) {
        super();
        initPanel();
        load();
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnReset) {
            for (PackageData pkg : packageData) {
                pkg.setInstalledVersion(0);
                pkg.setUpdating(false);
                pkg.setDownloaded(false);
            }
            tableModel.fireTableDataChanged();
        }
    }

    @Override
    public void initPanel() {
        packageData = new PackageManager().getPackageData();
        Collections.sort(packageData);

        tableModel = new InternalTableModel();
        table = new JTable(tableModel);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        TableColumn column = null;
        for (int c = 0; c < tableModel.getColumnCount(); ++c) {
            column = table.getColumnModel().getColumn(c);
            switch (c) {
            case 0:
                column.setPreferredWidth(250);
                break;
            case 1:
                column.setPreferredWidth(100);
                column.setMaxWidth(150);
                break;
            case 2:
                column.setPreferredWidth(80);
                column.setMaxWidth(100);
                break;
            case 3:
                column.setPreferredWidth(80);
                column.setMaxWidth(100);
                break;
            case 4:
                column.setPreferredWidth(70);
                column.setMaxWidth(70);
                column.setMinWidth(70);
                break;
            }
        }

        btnReset = new JButton(JDLocale.L("gui.config.packagemanager.reset", "Addons neu herunterladen"));
        btnReset.addActionListener(this);

        setLayout(new MigLayout("ins 5,wrap 1", "[fill,grow]", "[fill,grow][]"));
        add(new JScrollPane(table));
        add(btnReset, "w pref!, dock south");
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {
    }

}
