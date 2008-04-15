//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
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

package jd.plugins.optional;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

import javax.swing.SwingUtilities;
import jd.gui.skins.simple.SimpleGUI;
import jd.plugins.PluginOptional;
import jd.utils.JDLocale;
import jd.utils.JDTheme;
import jd.utils.JDUtilities;

public class JDLightTray extends PluginOptional implements MouseListener {

    private TrayIcon trayIcon;

    private TrayIconPopup popup;

    @Override
    public String getCoder() {
        return "jD-Team";
    }

    @Override
    public String getPluginID() {
        return getPluginName() + " " + getVersion();
    }

    @Override
    public String getPluginName() {
        return JDLocale.L("plugins.optional.JDLightTray.name", "JDLightTrayIcon");
    }

    @Override
    public String getVersion() {
        return "0.1";
    }

    @Override
    public void enable(boolean enable) throws Exception {
        if (JDUtilities.getJavaVersion() >= 1.6) {
            if (enable) {
                JDUtilities.getController().addControlListener(this);
                logger.info("Systemtray OK");
                initGUI();
            } else {
                if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
            }
        } else {
            logger.severe("Error initializing SystemTray: Tray is supported since Java 1.6. your Version: " + JDUtilities.getJavaVersion());
        }
    }

    private void initGUI() {

        trayIcon = new TrayIcon(JDUtilities.getImage(JDTheme.V("gui.images.jd_logo")));
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(this);
        trayIcon.addMouseListener(this);

        SystemTray systemTray = SystemTray.getSystemTray();
        try {
            systemTray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public void actionPerformed(ActionEvent e) {

    }

    @Override
    public String getRequirements() {
        return "JRE 1.6+";
    }

    @Override
    public ArrayList<String> createMenuitems() {
        return null;
    }

    public void mousePressed(MouseEvent e) {
        SimpleGUI simplegui = SimpleGUI.CURRENTGUI;
        if (e.getSource() instanceof TrayIcon) {
            if (e.getClickCount() > 1) {
                simplegui.getFrame().setVisible(!simplegui.getFrame().isVisible());
            } else {
                if (popup != null && popup.isShowing()) {
                    popup.dispose();
                    popup = null;
                } else if (SwingUtilities.isRightMouseButton(e)) {
                	
                	int x = e.getPoint().x;
                	int y = e.getPoint().y;
                	
                	Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                	int limitX = (int) screenSize.getWidth() / 2;
                	int limitY = (int) screenSize.getHeight() / 2;
                	
                    popup = new TrayIconPopup(this);
                    
                    if ( x <= limitX && y <= limitY ) {
                    	// top left
                    	popup.setLocation(x, y);
                    } else if ( x <= limitX && y >= limitY ) {
                    	// bottom left
                    	popup.setLocation(x, y-popup.getHeight());
                    } else if ( x >= limitX && y <= limitY ) {
                    	// top right
                    	popup.setLocation(x-popup.getWidth(), y);
                    } else if ( x >= limitX && y >= limitY ) {
                    	// bottom right
                    	popup.setLocation(x-popup.getWidth(), y-popup.getHeight());
                    }
                    
                }
            }

        }

    }

    public void mouseReleased(MouseEvent e) {
      

    }



    public void mouseClicked(MouseEvent e) {
        // TODO Auto-generated method stub

    }

    public void mouseEntered(MouseEvent e) {
        // TODO Auto-generated method stub

    }

    public void mouseExited(MouseEvent e) {
        // TODO Auto-generated method stub

    }

}
