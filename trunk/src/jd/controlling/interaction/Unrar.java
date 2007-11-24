package jd.controlling.interaction;

import java.io.Serializable;

import jd.config.Configuration;
import jd.controlling.JDController;
import jd.controlling.ProgressController;
import jd.plugins.DownloadLink;
import jd.unrar.jdUnrar;
import jd.utils.JDUtilities;

/**
 * Diese Klasse fürs automatische Entpacken
 * 
 * @author DwD
 */
public class Unrar extends Interaction implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 2467582501274722811L;
    /**
     * serialVersionUID
     */

    private static final String NAME = "Unrar";
    public static final String PROPERTY_UNRARCOMMAND = "PROPERTY_UNRARCMD";
    public static final String PROPERTY_AUTODELETE = "PROPERTY_AUTODELETE";
    public static final String PROPERTY_OVERWRITE_FILES = "PROPERTY_OVERWRITE_FILES";
    public static final String PROPERTY_MAX_FILESIZE = "PROPERTY_MAX_FILESIZE";
    @Override
    public boolean doInteraction(Object arg) {
        start();
        return true;

    }
    @Override
    public String toString() {
        return "Unrar Programm ausführen";
    }
    @Override
    public String getInteractionName() {
        return NAME;
    }
    @Override
    public void run() {
        ProgressController progress = new ProgressController(3);
        progress.setStatusText("Unrar");
        JDController controller = JDUtilities.getController();
        DownloadLink dLink = controller.getLastFinishedDownloadLink();
        String password = null;
        if (dLink != null)
            password = dLink.getFilePackage().getPassword();
        progress.increase(1);
        jdUnrar unrar = new jdUnrar(JDUtilities.getConfiguration().getStringProperty(Configuration.PARAM_DOWNLOAD_DIRECTORY));
        if (password!=null && !password.matches("[\\s]*")) {
            if (!password.matches("\\{\".*\"\\}$"))
                unrar.standardPassword = password;
            unrar.addToPasswordlist(password);
        }
        unrar.overwriteFiles = getBooleanProperty(Unrar.PROPERTY_OVERWRITE_FILES, false);
        unrar.autoDelete = getBooleanProperty(Unrar.PROPERTY_AUTODELETE, false);
        unrar.unrar = getStringProperty(Unrar.PROPERTY_UNRARCOMMAND);
        unrar.maxFilesize = getIntegerProperty(Unrar.PROPERTY_MAX_FILESIZE, 2);
        progress.setStatusText("Unrar last finished file: "+dLink.getFileOutput());
        unrar.unrar();
        progress.increase(1);
        unrar = new jdUnrar(JDUtilities.getConfiguration().getStringProperty(Configuration.PARAM_DOWNLOAD_DIRECTORY));
        unrar.overwriteFiles = getBooleanProperty(Unrar.PROPERTY_OVERWRITE_FILES, false);
        unrar.autoDelete = getBooleanProperty(Unrar.PROPERTY_AUTODELETE, false);
        unrar.unrar = getStringProperty(Unrar.PROPERTY_UNRARCOMMAND);
        unrar.maxFilesize = getIntegerProperty(Unrar.PROPERTY_MAX_FILESIZE, 2);
        progress.setStatusText("Unrar directory");
        unrar.unrar();
        progress.increase(1);
        progress.finalize();
        this.setCallCode(Interaction.INTERACTION_CALL_SUCCESS);
    }
    @Override
    public void initConfig() {

    }
    @Override
    public void resetInteraction() {
    }
}
