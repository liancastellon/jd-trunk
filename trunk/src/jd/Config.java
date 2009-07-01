package jd;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import jd.config.Configuration;
import jd.config.SubConfiguration;
import jd.gui.UserIO;
import jd.gui.userio.SimpleUserIO;
import jd.gui.userio.dialog.AbstractDialog;
import jd.update.JDUpdateUtils;
import jd.utils.JDTheme;
import jd.utils.JDUtilities;
import net.miginfocom.swing.MigLayout;

import org.jdesktop.swingx.JXTable;

public class Config {

    private Configuration mainConfig;
    private ArrayList<SubConfiguration> configs;
    private JComboBox configSelection;
    private SubConfiguration currentConfig;
    private JXTable table;
    private ConfigTableModel tableModel;

    private ArrayList<Object> values;
    private ArrayList<String> keys;
    private JButton add;
    private JButton edit;
    private JButton remove;

    public Config() {
        JDInit jdi = new JDInit();
        jdi.init();
        JDUpdateUtils.backupDataBase();
        System.out.println("Backuped Database");
        mainConfig = JDUtilities.getConfiguration();
        configs = JDUtilities.getDatabaseConnector().getSubConfigurationKeys();

        configs.add(0, mainConfig);

        sort();
        setCurrentConfig(configs.get(0));
        initGUI();

    }

    private void sort() {
        Collections.sort(configs, new Comparator<SubConfiguration>() {

            public int compare(SubConfiguration o1, SubConfiguration o2) {

                return o1.toString().compareToIgnoreCase(o2.toString());
            }

        });

    }

    private void setCurrentConfig(SubConfiguration cfg) {
        currentConfig = cfg;

        keys = new ArrayList<String>();
        values = new ArrayList<Object>();

        createMap(cfg.getProperties(), keys, values, "");
        if (tableModel != null) this.tableModel.fireTableDataChanged();

    }

    @SuppressWarnings("unchecked")
    private void createMap(HashMap hashMap, ArrayList<String> keys, ArrayList<Object> values, String pre) {
        for (Iterator<Entry> it = hashMap.entrySet().iterator(); it.hasNext();) {
            Entry next = it.next();
            String key = pre.length() > 0 ? pre + "/" + next.getKey() : next.getKey() + "";
            if (next.getValue() instanceof HashMap) {
                keys.add(key);
                values.add(next.getValue());
                createMap((HashMap) next.getValue(), keys, values, key);
            } else {
                keys.add(key);
                values.add(next.getValue());

            }
        }

    }

    private void initGUI() {
        JFrame frame = new JFrame("JDownloader Config - leave any warranty behind you!");
        frame.setLayout(new MigLayout("ins 10,wrap 1", "[grow,fill]", "[][grow,fill]"));
        frame.setMinimumSize(new Dimension(800, 600));

        configSelection = new JComboBox(configs.toArray(new SubConfiguration[] {}));
        configSelection.setEditable(true);
        configSelection.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (configSelection.getSelectedItem() instanceof String) {
                    SubConfiguration conf;
                    configs.add(conf = SubConfiguration.getConfig(configSelection.getSelectedItem().toString()));
                    sort();
                    configSelection.setModel(new DefaultComboBoxModel(configs.toArray(new SubConfiguration[] {})));
                    configSelection.setSelectedItem(conf);
                    setCurrentConfig(conf);
                    tableModel.fireTableDataChanged();

                } else {
                    setCurrentConfig((SubConfiguration) configSelection.getSelectedItem());
                    tableModel.fireTableDataChanged();
                }
            }

        });
        table = new JXTable(tableModel = new ConfigTableModel());

        table.getTableHeader().setReorderingAllowed(false);

        table.getColumn(0).setPreferredWidth(100);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent e) {
                int row = table.getSelectedRow();
                Object key = tableModel.getValueAt(row, 0);
                Object value = tableModel.getValueAt(row, 1);
                try {
                    new ObjectConverter().toString(value);
                    edit.setEnabled(true);
                    remove.setEnabled(true);
                } catch (Exception e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                    edit.setEnabled(false);
                    remove.setEnabled(false);
                }

            }

        });
        add = new JButton(JDTheme.II("gui.images.add", 24, 24));
        add.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                String key = SimpleUserIO.getInstance().requestInputDialog(UserIO.NO_COUNTDOWN, "Enter Key", "Enter your key use / deliminator to create new sub-maps", "NEW_KEY", null, "Create Entry", "Cancel");
                if (key == null) { return; }
                if (keys.contains(key)) {
                    SimpleUserIO.getInstance().requestMessageDialog("Key " + key + " is already available. Try Edit feature");
                    return;
                }
                String result = SimpleUserIO.getInstance().requestInputDialog(UserIO.STYLE_LARGE | UserIO.NO_COUNTDOWN, "Edit value for " + key, "Please take care to keep xml structure", "<classtype>VALUE</classtype>\r\n e.g.: <boolean>true</boolean>", null, "Save", "Cancel");
                if (result == null) return;
                try {

                    if (result != null) {
                        ObjectConverter oc = new ObjectConverter();
                        oc.toString(new Object());
                        Object object = oc.toObject(result);
                        String[] configKeys = key.toString().split("/");

                        HashMap<String, Object> props = currentConfig.getProperties();

                        System.out.println("Save Object " + key);

                        for (int i = 0; i < configKeys.length; i++) {
                            String k = configKeys[i];
                            if (i < configKeys.length - 1) {
                                Object next = props.get(k);

                                if (next instanceof HashMap) {
                                    System.out.println("sub Hashmap " + k);
                                    props = (HashMap) next;
                                } else {
                                    System.out.println("create sub Hashmap " + k);
                                    props.put(k, props = new HashMap<String, Object>());
                                }
                            }
                        }

                        props.put(configKeys[configKeys.length - 1], object);
                        currentConfig.save();
                        setCurrentConfig(currentConfig);
                    }

                } catch (Exception e1) {
                    SimpleUserIO.getInstance().requestMessageDialog("Could not save object. Failures in XML structure!");

                }
            }

        });
        edit = new JButton(JDTheme.II("gui.images.findandreplace", 24, 24));
        edit.setEnabled(false);
        edit.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                int row = table.getSelectedRow();
                Object key = tableModel.getValueAt(row, 0);
                Object value = tableModel.getValueAt(row, 1);

                try {
                    AbstractDialog.setDefaultDimension(new Dimension(550, 400));
                    ObjectConverter oc = new ObjectConverter();
                    String valuess = oc.toString(value);

                    String result = SimpleUserIO.getInstance().requestInputDialog(UserIO.STYLE_LARGE | UserIO.NO_COUNTDOWN, "Edit value for " + key, "Please take care to keep xml structure", valuess, null, "Save", "Cancel");
                    try {

                        if (result != null) {
                            Object object = oc.toObject(result);
                            String[] configKeys = key.toString().split("/");

                            HashMap<String, Object> props = currentConfig.getProperties();
                            String myKey = null;
                            System.out.println("Save Object " + key);

                            for (String k : configKeys) {
                                Object next = props.get(k);
                                if (next instanceof HashMap) {
                                    System.out.println("sub Hashmap " + k);
                                    props = (HashMap) next;
                                } else {
                                    myKey = k;
                                    System.out.println("Save Object to key " + k);
                                    break;
                                }
                            }

                            props.put(myKey, object);
                            currentConfig.save();
                            setCurrentConfig(currentConfig);
                        }

                    } catch (Exception e1) {
                        SimpleUserIO.getInstance().requestMessageDialog("Could not save object. Failures in XML structure!");

                    }
                } catch (Exception e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }

            }

        });
        remove = new JButton(JDTheme.II("gui.images.delete", 24, 24));
        remove.setEnabled(false);
        remove.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                int row = table.getSelectedRow();
                Object key = tableModel.getValueAt(row, 0);
                Object value = tableModel.getValueAt(row, 1);
                String[] keys = key.toString().split("/");
                if (keys[keys.length - 1].equals("null")) {
                    keys[keys.length - 1] = null;
                }
                if (keys.length == 1) {
                    currentConfig.getProperties().remove(keys[0]);
                    currentConfig.save();
                    setCurrentConfig(currentConfig);
                } else {

                    HashMap<String, Object> props = currentConfig.getProperties();

                    for (String k : keys) {
                        Object next = props.get(k);
                        if (next instanceof HashMap) {
                            System.out.println("sub Hashmap " + k);
                            props = (HashMap) next;
                        } else if (k != keys[keys.length - 1]) {

                            System.out.println("error key " + k);
                            return;

                        }
                    }

                    props.remove(keys[keys.length - 1]);
                    currentConfig.save();
                    setCurrentConfig(currentConfig);

                }

            }

        });
        add.setOpaque(false);
        add.setBorderPainted(false);
        add.setContentAreaFilled(false);
        edit.setOpaque(false);
        edit.setBorderPainted(false);
        edit.setContentAreaFilled(false);
        remove.setOpaque(false);
        remove.setBorderPainted(false);
        remove.setContentAreaFilled(false);
        frame.add(configSelection, "split 4,pushx,growx");
        frame.add(add, "alignx right");
        frame.add(remove, "alignx right");
        frame.add(edit, "alignx right");
        frame.add(new JScrollPane(table));
        frame.setVisible(true);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    }

    private class ConfigTableModel extends AbstractTableModel {

        private static final long serialVersionUID = -5434313385327397539L;

        private String[] columnNames = { "Key", "Value" };

        public int getColumnCount() {
            return columnNames.length;
        }

        public int getRowCount() {
            return values.size();
        }

        public String getColumnName(int col) {
            return columnNames[col];
        }

        public Object getValueAt(int row, int col) {
            try {
                switch (col) {
                case 0:
                    return keys.get(row);
                case 1:
                    try {
                        new ObjectConverter().toString(values.get(row));
                        return values.get(row);
                    } catch (Exception e) {
                        return values.get(row);
                    }
                }
            } catch (Exception e) {

            }
            return "";
        }

        private Entry<String, Object> getEntry(int row, int col) {
            Iterator<Entry<String, Object>> it = currentConfig.getProperties().entrySet().iterator();
            Entry<String, Object> ret = null;
            while (it.hasNext()) {
                ret = it.next();

            }
            return ret;

        }

        public Class<?> getColumnClass(int c) {
            return String.class;
        }

        public boolean isCellEditable(int row, int col) {
            return col == 5;
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == 2) {

            }
        }

    }

}
