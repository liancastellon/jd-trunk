package jd.gui.skins.simple.config;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.util.logging.Level;

import javax.swing.JFileChooser;
import javax.swing.JLabel;



import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Configuration;
import jd.gui.UIInterface;
import jd.gui.skins.simple.components.BrowseFile;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;

public class ConfigPanelGeneral extends ConfigPanel {
    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 3383448498625377495L;
    private JLabel lblHomeDir;
    private BrowseFile brsHomeDir;
    private Configuration configuration;
    public ConfigPanelGeneral(Configuration configuration, UIInterface uiinterface) {
        super(uiinterface);
        this.configuration = configuration;
        initPanel();
        load();
    }
    public void save() {
        this.saveConfigEntries();

        JDUtilities.getLogger().setLevel((Level) configuration.getProperty(Configuration.PARAM_LOGGER_LEVEL));
        if (JDUtilities.getHomeDirectory() != null && !JDUtilities.getHomeDirectory().equalsIgnoreCase(brsHomeDir.getText().trim())) {
            JDUtilities.writeJDHomeDirectoryToWebStartCookie(brsHomeDir.getText().trim());

        }
    }
    @Override
    public void initPanel() {
        GUIConfigEntry ce;
   
        
        ce = new GUIConfigEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX, configuration, Configuration.PARAM_LOGGER_LEVEL, new Level[]{Level.ALL, Level.FINEST, Level.FINER, Level.FINE, Level.INFO, Level.WARNING, Level.SEVERE, Level.OFF}, JDLocale.L("gui.config.general.loggerLevel", "Level für's Logging")).setDefaultValue(Level.WARNING).setExpertEntry(true));
        addGUIConfigEntry(ce);
        ce = new GUIConfigEntry(new ConfigEntry(ConfigContainer.TYPE_BROWSEFOLDER, configuration, Configuration.PARAM_DOWNLOAD_DIRECTORY, JDLocale.L("gui.config.general.downloadDirectory", "Downloadverzeichnis")).setDefaultValue(JDUtilities.getJDHomeDirectoryFromEnvironment().getAbsolutePath()));
        addGUIConfigEntry(ce);
        ce = new GUIConfigEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX, configuration, Configuration.PARAM_FINISHED_DOWNLOADS_ACTION, new String[]{Configuration.FINISHED_DOWNLOADS_REMOVE, Configuration.FINISHED_DOWNLOADS_REMOVE_AT_START, Configuration.FINISHED_DOWNLOADS_NO_REMOVE}, JDLocale.L("gui.config.general.toDoWithDownloads", "Fertig gestellte Downloads ...")).setDefaultValue(Configuration.FINISHED_DOWNLOADS_REMOVE_AT_START).setExpertEntry(true));
        addGUIConfigEntry(ce);
 
       

        // if(JDUtilities.getJavaVersion()>=1.6d){
        // ce = new GUIConfigEntry(new
        // ConfigEntry(ConfigContainer.TYPE_CHECKBOX, configuration,
        // Configuration.PARAM_NO_TRAY, "Trayicon
        // deaktivieren").setDefaultValue(false));
        //               
        // }else{
        // ce = new GUIConfigEntry(new
        // ConfigEntry(ConfigContainer.TYPE_CHECKBOX, configuration,
        // Configuration.PARAM_NO_TRAY, "Trayicon
        // deaktivieren").setDefaultValue(true).setEnabled(false));
        // }
        // addGUIConfigEntry(ce);

      

        ce = new GUIConfigEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, configuration, Configuration.PARAM_USE_PACKETNAME_AS_SUBFOLDER, JDLocale.L("gui.config.general.createSubFolders", "Wenn möglich Unterordner mit Paketname erstellen")).setDefaultValue(false));
        addGUIConfigEntry(ce);





        if (JDUtilities.getHomeDirectory() != null) {
            brsHomeDir = new BrowseFile();
            brsHomeDir.setText(JDUtilities.getHomeDirectory());
            brsHomeDir.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            JDUtilities.addToGridBag(panel, lblHomeDir, GridBagConstraints.RELATIVE, GridBagConstraints.RELATIVE, 1, 1, 1, 1, insets, GridBagConstraints.NONE, GridBagConstraints.EAST);
            JDUtilities.addToGridBag(panel, brsHomeDir, GridBagConstraints.RELATIVE, GridBagConstraints.REMAINDER, 1, 1, 1, 1, insets, GridBagConstraints.NONE, GridBagConstraints.WEST);
        }
        add(panel, BorderLayout.NORTH);
    }
    @Override
    public void load() {
        this.loadConfigEntries();
    }
    @Override
    public String getName() {
        return JDLocale.L("gui.config.general.name", "Allgemein");
    }
}
