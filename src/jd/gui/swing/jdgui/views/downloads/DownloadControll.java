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

package jd.gui.swing.jdgui.views.downloads;

import javax.swing.ImageIcon;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import jd.gui.swing.jdgui.views.settings.components.ComboBox;
import jd.gui.swing.jdgui.views.settings.components.Spinner;

import org.appwork.storage.config.JsonConfig;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.storage.config.listeners.ModifiedListener;
import org.appwork.utils.swing.EDTRunner;
import org.jdownloader.gui.settings.AbstractConfigPanel;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.NewTheme;
import org.jdownloader.settings.CleanAfterDownloadAction;
import org.jdownloader.settings.GeneralSettings;
import org.jdownloader.settings.IfFileExistsAction;
import org.jdownloader.translate._JDT;

public class DownloadControll extends AbstractConfigPanel implements ChangeListener {

    private static final long                  serialVersionUID = 1L;
    private Spinner                            maxSimPerHost;
    private ComboBox<CleanAfterDownloadAction> remove;
    private ComboBox<IfFileExistsAction>       ifFileExists;
    private Spinner                            maxSim;
    private GeneralSettings                    config;
    private Spinner                            maxchunks;

    public String getTitle() {
        return _JDT._.gui_settings_downloadcontroll_title();
    }

    public DownloadControll() {
        super();

        this.addHeader(_JDT._.gui_settings_downloadcontroll_title(), NewTheme.I().getIcon("downloadmanagment", 32));
        this.addDescription(_JDT._.gui_settings_downloadcontroll_description());

        maxSimPerHost = new Spinner(0, 20);

        maxSim = new Spinner(1, 20);

        maxchunks = new Spinner(1, 20);
        maxSimPerHost.addChangeListener(this);
        maxSim.addChangeListener(this);
        maxchunks.addChangeListener(this);
        String[] removeDownloads = new String[] { _GUI._.gui_config_general_toDoWithDownloads_immediate(), _GUI._.gui_config_general_toDoWithDownloads_atstart(), _GUI._.gui_config_general_toDoWithDownloads_packageready(), _GUI._.gui_config_general_toDoWithDownloads_never() };

        remove = new ComboBox<CleanAfterDownloadAction>(CleanAfterDownloadAction.values(), removeDownloads);

        String[] fileExists = new String[] { _GUI._.system_download_triggerfileexists_overwrite(), _GUI._.system_download_triggerfileexists_skip(), _GUI._.system_download_triggerfileexists_rename(), _GUI._.system_download_triggerfileexists_askpackage(), _GUI._.system_download_triggerfileexists_ask() };
        ifFileExists = new ComboBox<IfFileExistsAction>(IfFileExistsAction.values(), fileExists);
        this.addPair(_GUI._.gui_config_download_simultan_downloads(), maxSim);
        this.addPair(_GUI._.gui_config_download_simultan_downloads_per_host(), maxSimPerHost);
        this.addPair(_GUI._.gui_config_download_max_chunks(), maxchunks);

        this.addPair(_GUI._.gui_config_general_todowithdownloads(), remove);
        this.addPair(_GUI._.system_download_triggerfileexists(), ifFileExists);
        config = JsonConfig.create(GeneralSettings.class);
        config.getStorageHandler().getEventSender().addListener(new ModifiedListener(GeneralSettings.MAX_CHUNKS_PER_FILE, GeneralSettings.MAX_SIMULTANE_DOWNLOADS) {

            @Override
            protected void onChanged(KeyHandler<?> handler, Object newValue) {
                if (isShown()) {
                    new EDTRunner() {

                        @Override
                        protected void runInEDT() {
                            if (isShown()) {
                                updateContents();
                            }
                        }
                    };
                }
            }

        });
    }

    @Override
    public ImageIcon getIcon() {
        return NewTheme.I().getIcon("downloadmanagment", 32);
    }

    @Override
    public void save() {

        config.setMaxChunksPerFile((Integer) maxchunks.getValue());
        config.setCleanupAfterDownloadAction(remove.getValue());
        config.setIfFileExistsAction(this.ifFileExists.getValue());
        config.setMaxSimultaneDownloadsPerHost((Integer) maxSimPerHost.getValue());
        config.setMaxSimultaneDownloads((Integer) maxSim.getValue());
    }

    @Override
    public void updateContents() {
        maxchunks.setValue(config.getMaxChunksPerFile());
        maxSim.setValue(config.getMaxSimultaneDownloads());
        this.remove.setValue(config.getCleanupAfterDownloadAction());
        this.ifFileExists.setValue(config.getIfFileExistsAction());
        this.maxSimPerHost.setValue(config.getMaxSimultaneDownloadsPerHost());

    }

    public void stateChanged(ChangeEvent e) {
        save();
    }
}