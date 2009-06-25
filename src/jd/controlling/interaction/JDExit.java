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

package jd.controlling.interaction;

import java.io.Serializable;

import jd.gui.UserIO;
import jd.nutils.JDFlags;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

/**
 * Diese Interaktion beendet den JDownloader.
 * 
 * @author JD-Team
 */
public class JDExit extends Interaction implements Serializable {

    private static final long serialVersionUID = -4825002404662625527L;

    @Override
    public boolean doInteraction(Object arg) {
        logger.info("Starting Exit");
        int ret = UserIO.getInstance().requestConfirmDialog(0, JDL.L("interaction.jdexit.title", "JD will close itself!"), JDL.L("interaction.jdexit.message2", "JD will close itself if you do not abort!"), UserIO.getInstance().getIcon(UserIO.ICON_WARNING), null, null);
        if (JDFlags.hasSomeFlags(ret, UserIO.RETURN_OK, UserIO.RETURN_COUNTDOWN_TIMEOUT)) {
            JDUtilities.getController().exit();
        }
        return true;
    }

    @Override
    public String getInteractionName() {
        return JDL.L("interaction.jdexit.name", "JD Beenden");
    }

    @Override
    public void initConfig() {
    }

    @Override
    public String toString() {
        return JDL.L("interaction.jdexit.name", "JD Beenden");
    }

}
