//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program  is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSSE the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://gnu.org/licenses/>.

package jd.plugins.optional.hjsplit;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.filechooser.FileFilter;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.MenuItem;
import jd.config.SubConfiguration;
import jd.controlling.ProgressController;
import jd.controlling.SingleDownloadController;
import jd.event.ControlEvent;
import jd.event.ControlListener;
import jd.gui.skins.simple.SimpleGUI;
import jd.gui.skins.simple.components.JDFileChooser;
import jd.nutils.jobber.JDRunnable;
import jd.nutils.jobber.Jobber;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginForHost;
import jd.plugins.PluginOptional;
import jd.plugins.optional.hjsplit.jaxe.JAxeJoiner;
import jd.plugins.optional.hjsplit.jaxe.JoinerFactory;
import jd.plugins.optional.hjsplit.jaxe.ProgressEvent;
import jd.plugins.optional.hjsplit.jaxe.ProgressEventListener;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;

public class JDHJSplit extends PluginOptional implements ControlListener {

    private static final String CONFIG_KEY_REMOVE_MERGED = "REMOVE_MERGED";
    private static final String DUMMY_HOSTER = "dum.my";
    private static final int ARCHIVE_TYPE_NONE = -1;
    private static final int ARCHIVE_TYPE_NORMAL = 0;
    private static final int ARCHIVE_TYPE_UNIX = 1;
    private static final int ARCHIVE_TYPE_7Z = 2;
    private static final int ARCHIVE_TYPE_RAR = 2;
    private static final String CONFIG_KEY_OVERWRITE = "OVERWRITE";

    // Wird als reihe für anstehende extracthjobs verwendet
    private Jobber queue;

    public static int getAddonInterfaceVersion() {
        return 2;
    }

    public JDHJSplit(PluginWrapper wrapper) {
        super(wrapper);
        this.queue = new Jobber(1);
        initConfig();
    }
    public String getIconKey(){
        return "gui.images.addons.merge";
    }
    /**
     * Das controllevent fängt heruntergeladene file ab und wertet sie aus.
     * CONTROL_PLUGIN_INACTIVE: Wertet die frisch fertig gewordenen Downloads
     * aus. CONTROL_ON_FILEOUTPUT: wertet frisch fertig verarbeitete files aus,
     * z.B. frisch entpackte CONTROL_LINKLIST_CONTEXT_MENU: wird verwendet um
     * ins kontextmenü der gui die menüpunkte zu schreiben
     */
    @SuppressWarnings("unchecked")
    @Override
    public void controlEvent(ControlEvent event) {
        super.controlEvent(event);
        DownloadLink link;

        switch (event.getID()) {
        case ControlEvent.CONTROL_PLUGIN_INACTIVE:
            if (!(event.getSource() instanceof PluginForHost)) { return; }
            if (this.getPluginConfig().getBooleanProperty("ACTIVATED", true)) {
                link = ((SingleDownloadController) event.getParameter()).getDownloadLink();
                File file = new File(link.getFileOutput());

                if (link.getLinkStatus().hasStatus(LinkStatus.FINISHED)) {
                    if (link.getFilePackage().isExtractAfterDownload()) {
                        file = this.getStartFile(file);
                        if (file == null) return;
                        if (this.validateArchive(file)) {
                            addFileList(new File[] { file });
                        }
                    }
                }
            }
            break;
        case ControlEvent.CONTROL_ON_FILEOUTPUT:
            addFileList((File[]) event.getParameter());
            break;

        case ControlEvent.CONTROL_LINKLIST_CONTEXT_MENU:
            ArrayList<MenuItem> items = (ArrayList<MenuItem>) event.getParameter();
            MenuItem m;
            if (event.getSource() instanceof DownloadLink) {
                link = (DownloadLink) event.getSource();

                items.add(m = new MenuItem(MenuItem.NORMAL, JDLocale.L("plugins.optional.jdhjsplit.linkmenu.merge", "Merge"), 1000).setActionListener(this));
                m.setEnabled(false);
                if (link.getLinkStatus().hasStatus(LinkStatus.FINISHED) && this.isStartVolume(new File(link.getFileOutput()))) m.setEnabled(true);
                if (new File(link.getFileOutput()).exists() && link.getName().matches(".*rar$")) m.setEnabled(true);

                m.setProperty("LINK", link);

            } else {
                FilePackage fp = (FilePackage) event.getSource();
                items.add(m = new MenuItem(MenuItem.NORMAL, JDLocale.L("plugins.optional.jdhjsplit.linkmenu.package.merge", "Merge package"), 1001).setActionListener(this));
                m.setProperty("PACKAGE", fp);
            }
            break;
        }
    }

    @Override
    public ArrayList<MenuItem> createMenuitems() {
        ArrayList<MenuItem> menu = new ArrayList<MenuItem>();
        MenuItem m;

        menu.add(m = new MenuItem(MenuItem.TOGGLE, JDLocale.L("plugins.optional.hjsplit.menu.toggle", "Activate"), 1).setActionListener(this));
        m.setSelected(this.getPluginConfig().getBooleanProperty("ACTIVATED", true));

      

        menu.add(new MenuItem(MenuItem.NORMAL, JDLocale.L("plugins.optional.hjsplit.menu.extract.singlefils", "Merge archive(s)"), 21).setActionListener(this));

       

        menu.add(new MenuItem(MenuItem.NORMAL, JDLocale.L("plugins.optional.hjsplit.menu.config", "Settings"), 4).setActionListener(this));

        return menu;
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof MenuItem) {
            menuitemActionPerformed(e, (MenuItem) e.getSource());
        }
    }

    private void menuitemActionPerformed(ActionEvent e, MenuItem source) {
        SubConfiguration cfg = this.getPluginConfig();
        switch (source.getActionID()) {
        case 1:
            cfg.setProperty("ACTIVATED", !cfg.getBooleanProperty("ACTIVATED", true));
            cfg.save();
            break;
        case 21:
            JDFileChooser fc = new JDFileChooser("_JDHJSPLIT_");
            fc.setMultiSelectionEnabled(true);
            FileFilter ff = new FileFilter() {
                public boolean accept(File pathname) {
                    if (isStartVolume(pathname)) return true;
                    if (pathname.isDirectory()) return true;
                    return false;
                }

                @Override
                public String getDescription() {
                    return JDLocale.L("plugins.optional.hjsplit.filefilter", "HJSPLIT-Startvolumes");
                }

            };
            fc.setFileFilter(ff);
            if (fc.showOpenDialog(SimpleGUI.CURRENTGUI) == JDFileChooser.APPROVE_OPTION) {
                File[] list = fc.getSelectedFiles();
                if (list == null) return;
                addFileList(list);
            }
            break;
        case 4:
            SimpleGUI.showConfigDialog(SimpleGUI.CURRENTGUI, config);
            break;

        case 1000:
            File file = new File(((DownloadLink) source.getProperty("LINK")).getFileOutput());
            file = this.getStartFile(file);
            if (this.validateArchive(file)) {
                addFileList(new File[] { file });
            }
            break;

        case 1001:

            FilePackage fp = (FilePackage) source.getProperty("PACKAGE");
            ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            for (DownloadLink l : fp.getDownloadLinks()) {
                if (l.getLinkStatus().hasStatus(LinkStatus.FINISHED)) {
                    file = new File(l.getFileOutput());
                    if (this.validateArchive(file)) {
                        links.add(l);
                    }
                }
            }
            if (links.size() <= 0) return;
            addFileList(links.toArray(new File[] {}));
            break;

        }
    }

    /**
     * Fügt lokale files der mergqueue hinzu. Es muss sich bereits zum
     * startarchive handeln.
     * 
     * @param list
     */
    private void addFileList(File[] list) {
        DownloadLink link;
        for (File archiveStartFile : list) {
            if (!isStartVolume(archiveStartFile)) continue;
            boolean b = validateArchive(archiveStartFile);
            if (!b) {
                logger.info("Archive " + archiveStartFile + " is incomplete or no archive. Validation failed");
                return;
            }
            link = JDUtilities.getController().getDownloadLinkByFileOutput(archiveStartFile, LinkStatus.FINISHED);
            if (link == null) link = createDummyLink(archiveStartFile);

            final DownloadLink finalLink = link;

            addToQueue(finalLink);
        }
    }

    // Startet das Abwarbeiten der extractqueue
    private void addToQueue(final DownloadLink link) {

        queue.add(new JDRunnable() {

            public void go() {

                final ProgressController progress = new ProgressController("Default HJMerge", 100);

                JAxeJoiner join = JoinerFactory.getJoiner(new File(link.getFileOutput()));
                final File output = getOutputFile(new File(link.getFileOutput()));

                join.setProgressEventListener(new ProgressEventListener() {

                    long last = System.currentTimeMillis() + 1000;

                    public void handleEvent(ProgressEvent pe) {
                        try {
                            if (System.currentTimeMillis() - last > 100) {
                                progress.setStatus((int) (pe.getCurrent() * 100 / pe.getMax()));
                                last = System.currentTimeMillis();
                                progress.setStatusText(output.getName() + ": " + (pe.getCurrent() / 1048576) + " MB merged");
                            }
                        } catch (Exception e) {
                            // TODO: handle exception
                        }
                    }
                });

                if (getPluginConfig().getBooleanProperty(CONFIG_KEY_OVERWRITE, false)) {

                    if (output.exists()) output.delete();
                }
                join.run();
                if (getPluginConfig().getBooleanProperty(CONFIG_KEY_REMOVE_MERGED, false)) {
                    ArrayList<File> list = getFileList(new File(link.getFileOutput()));
                    for (File f : list) {
                        f.delete();
                        f.deleteOnExit();
                    }
                }
                progress.finalize();
                JDUtilities.getController().fireControlEvent(new ControlEvent(this, ControlEvent.CONTROL_ON_FILEOUTPUT, new File[] { output }));
            }
        });
        queue.start();
    }

    /**
     * Gibt die zu entpackende Datei zurück.
     * 
     * @param file
     * @return
     */
    private File getOutputFile(File file) {
        int type = getArchiveType(file);
        switch (type) {
        case ARCHIVE_TYPE_UNIX:
            return new File(file.getParentFile(), file.getName().replaceFirst("\\.a.$", ""));
        case ARCHIVE_TYPE_NORMAL:
            return new File(file.getParentFile(), file.getName().replaceFirst("\\.[\\d]+($|\\.[^\\d]*$)", ""));
        default:
            return null;
        }
    }

    /**
     * Validiert ein Archiv. Archive haben zwei formen: unix: *.aa..*.ab..*.ac .
     * das zweite a wird hochgezählt normal: *.001...*.002
     * 
     * Die Funktion versucht zu prüfen ob das Archiv komplett heruntergeladen
     * wurde und ob es ein gültoges Archiv ist.
     * 
     * @param file
     * @return
     */
    private boolean validateArchive(File file) {
        File startFile = getStartFile(file);
        if (startFile == null || !startFile.exists() || !startFile.isFile()) return false;
        int type = getArchiveType(file);

        switch (type) {
        case ARCHIVE_TYPE_UNIX:
            return validateUnixType(startFile) != null;
        case ARCHIVE_TYPE_NORMAL:
            return validateNormalType(startFile) != null;
        default:
            return false;
        }
    }

    /**
     * Gibt alle files die zum Archiv von file gehören zurück
     * 
     * @param file
     * @return
     */
    private ArrayList<File> getFileList(File file) {

        File startFile = getStartFile(file);
        if (startFile == null || !startFile.exists() || !startFile.isFile()) return null;
        int type = getArchiveType(file);

        switch (type) {
        case ARCHIVE_TYPE_UNIX:
            return validateUnixType(startFile);
        case ARCHIVE_TYPE_NORMAL:
            return validateNormalType(startFile);
        default:
            return null;
        }
    }

    /**
     * Validiert typ normal (siehe validateArchiv)
     * 
     * @param file
     * @return
     */
    private ArrayList<File> validateNormalType(File file) {
        final String matcher = file.getName().replaceFirst("\\.[\\d]+($|\\.[^\\d]*$)", "\\\\.[\\\\d]+$1");
        ArrayList<DownloadLink> missing = JDUtilities.getController().getDownloadLinksByNamePattern(matcher);
        for (DownloadLink miss : missing) {
            File par1 = new File(miss.getFileOutput()).getParentFile();
            File par2 = file.getParentFile();
            if (par1.equals(par2)) {

                if (!new File(miss.getFileOutput()).exists()) { return null; }
            }
        }
        File[] files = file.getParentFile().listFiles(new java.io.FileFilter() {
            public boolean accept(File pathname) {
                if (pathname.isFile() && pathname.getName().matches(matcher)) { return true; }
                return false;
            }
        });
        int c = 1;
        ArrayList<File> ret = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            String volume = JDUtilities.fillString(c + "", "0", "", 3);
            File newFile;
            if ((newFile = new File(file.getParentFile(), file.getName().replaceFirst("\\.[\\d]+($|\\.[^\\d]*$)", "\\." + volume + "$1"))).exists()) {

                c++;
                ret.add(newFile);
            } else {
                return null;
            }
        }
        return ret;
    }

    /**
     * Validiert das archiv auf 2 arten 1. wird ind er downloadliste nach
     * passenden unfertigen archiven gesucht 2. wird das archiv durchnummeriert
     * und geprüft ob es lücken/fehlende files gibts siehe (validateArchiv)
     * 
     * @param file
     * @return
     */
    private ArrayList<File> validateUnixType(File file) {

        final String matcher = file.getName().replaceFirst("\\.a.($|\\..*)", "\\\\.a.$1");
        ArrayList<DownloadLink> missing = JDUtilities.getController().getDownloadLinksByNamePattern(matcher);
        for (DownloadLink miss : missing) {
            if (new File(miss.getFileOutput()).exists() && new File(miss.getFileOutput()).getParentFile().equals(file.getParentFile())) continue;
            return null;
        }
        File[] files = file.getParentFile().listFiles(new java.io.FileFilter() {
            public boolean accept(File pathname) {
                if (pathname.isFile() && pathname.getName().matches(matcher)) { return true; }
                return false;
            }
        });
        ArrayList<File> ret = new ArrayList<File>();
        char c = 'a';
        for (int i = 0; i < files.length; i++) {

            File newFile;
            if ((newFile = new File(file.getParentFile(), file.getName().replaceFirst("\\.a.($|\\..*)", "\\.a" + c + "$1"))).exists()) {
                ret.add(newFile);
                c++;
            } else {
                return null;
            }
        }
        return ret;
    }

    /**
     * Sucht den Dateinamen und den Pfad der des Startvolumes heraus
     * 
     * @param file
     * @return
     */
    private File getStartFile(File file) {
        int type = getArchiveType(file);
        switch (type) {
        case ARCHIVE_TYPE_UNIX:
            return new File(file.getParentFile(), file.getName().replaceFirst("\\.a.$", ".aa"));
        case ARCHIVE_TYPE_NORMAL:
            return new File(file.getParentFile(), file.getName().replaceFirst("\\.[\\d]+($|\\.[^\\d]*$)", ".001$1"));
        default:
            return null;
        }
    }

    /**
     * Gibt zurück ob es sich bei der Datei um ein hjsplit Startvolume handelt.
     * 
     * @param file
     * @return
     */
    private boolean isStartVolume(File file) {
        if (file.getName().matches("(?is).*\\.7z\\.[\\d]+$")) return false;

        if (file.getName().matches(".*\\.aa$")) return true;

        if (file.getName().matches(".*\\.001($|\\.[^\\d]*$)")) return true;

        return false;
    }

    /**
     * Gibt den Archivtyp zurück. möglich sind: ARCHIVE_TYPE_7Z (bad)
     * ARCHIVE_TYPE_NONE (bad) ARCHIVE_TYPE_UNIX ARCHIVE_TYPE_NORMAL
     * 
     * @param file
     * @return
     */
    private int getArchiveType(File file) {
        String name = file.getName();

        if (name.matches("(?is).*\\.7z\\.[\\d]+$")) return ARCHIVE_TYPE_7Z;
        if (name.matches(".*\\.a.$")) {
            try {
                Signature fs = FileSignatures.getFileSignature(file);
                if (fs != null && fs.getId().equals("RAR"))
                    return ARCHIVE_TYPE_RAR;
                else if (fs != null && fs.getId().equals("﻿7Z")) return ARCHIVE_TYPE_7Z;
            } catch (IOException e) {
            }

            return ARCHIVE_TYPE_UNIX;

        }
        if (name.matches(".*\\.[\\d]+($|\\.[^\\d]*$)")) return ARCHIVE_TYPE_NORMAL;
        {
            try {
                Signature fs = FileSignatures.getFileSignature(file);
                if (fs != null && fs.getId().equals("RAR"))
                    return ARCHIVE_TYPE_RAR;
                else if (fs != null && fs.getId().equals("﻿7Z")) return ARCHIVE_TYPE_7Z;
            } catch (IOException e) {
            }

            return ARCHIVE_TYPE_NONE;
        }
    }

    /**
     * Erstellt einen Dummy Downloadlink. Dieser dient nur als container für die
     * Datei. Adapterfunktion
     * 
     * @param archiveStartFile
     * @return
     */
    private DownloadLink createDummyLink(File archiveStartFile) {

        DownloadLink link = new DownloadLink(null, archiveStartFile.getName(), DUMMY_HOSTER, "", true);
        link.setDownloadSize(archiveStartFile.length());
        FilePackage fp = new FilePackage();
        fp.setDownloadDirectory(archiveStartFile.getParent());
        link.setFilePackage(fp);
        return link;
    }

    @Override
    public String getHost() {
        return JDLocale.L("plugins.optional.jdhjsplit.name", "JD-HJMerge");
    }

    @Override
    public String getRequirements() {
        return "JRE 1.5+";
    }

    @Override
    public String getVersion() {
        return getVersion("$Revision$");
    }

    @Override
    public boolean initAddon() {
        JDUtilities.getController().addControlListener(this);
        return true;
    }

    public void initConfig() {
        SubConfiguration subConfig = getPluginConfig();
        ConfigEntry ce;

        config.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, subConfig, CONFIG_KEY_REMOVE_MERGED, JDLocale.L("gui.config.hjsplit.remove_merged", "Delete archive after merging")));
        ce.setDefaultValue(true);
        config.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, subConfig, CONFIG_KEY_OVERWRITE, JDLocale.L("gui.config.hjsplit.overwrite", "Overwrite existing files")));
        ce.setDefaultValue(true);
    }

    @Override
    public void onExit() {
        JDUtilities.getController().removeControlListener(this);
    }
}