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

package jd.captcha;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileFilter;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;

import jd.captcha.utils.UTILITIES;
import jd.gui.skins.simple.components.BrowseFile;
import jd.utils.JDLocale;
import net.miginfocom.swing.MigLayout;

public class JACController {
    private static BrowseFile chooser;
    private static JComboBox methods;

    public static void showDialog(final boolean isTrain) {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new MigLayout("wrap 2"));
        if (isTrain) {
            frame.setTitle(JDLocale.L("train.chooser.title", "jDownloader :: TrainAll"));
        } else {
            frame.setTitle(JDLocale.L("showcaptcha.chooser.title", "jDownloader :: Show Captcha"));
        }
        frame.setAlwaysOnTop(true);
        frame.setLocation(20, 20);
        File[] meths = new File(UTILITIES.getMethodDir()).listFiles(new FileFilter() {

            public boolean accept(File pathname) {
                if (pathname.isDirectory()) return true;
                return false;
            }
        });
        methods = new JComboBox(meths);
        chooser = new BrowseFile();
        if (isTrain) {
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        }
        chooser.setEditable(true);

        String chooserText = "";
        if (isTrain) {
            chooserText = JDLocale.L("train.choose.folder", "Wählen sie einen Ordner aus");
        } else {
            chooserText = JDLocale.L("showcaptcha.choose.file", "Wählen sie eine Bilddatei aus");
        }
        frame.add(new JLabel(JDLocale.L("train.method", "Wählen sie eine Methode aus:")));
        frame.add(methods, "growx, spanx");
        frame.add(new JLabel(chooserText));
        frame.add(chooser, "growx, spanx");
        JButton btnOK = new JButton(JDLocale.L("gui.btn_ok", "OK"));
        btnOK.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {

                    public void run() {
                        JACController jacc = new JACController(new File(chooser.getText()), ((File) methods.getSelectedItem()).getName());
                        if (isTrain) {
                            jacc.train();
                        } else {
                            jacc.showCaptcha();
                        }

                    }

                }).start();

            }

        });
        frame.add(btnOK, "span, right");
        frame.pack();
        frame.setResizable(false);
        frame.setVisible(true);
    }

    JAntiCaptcha jac;
    private File path;

    public JACController(File path, String methode) {
        this.path = path;
        jac = new JAntiCaptcha(UTILITIES.getMethodDir(), methode);
    }

    public void showCaptcha() {
        jac.showPreparedCaptcha(path);
    }

    public void train() {
        jac.trainAllCaptchas(path.getAbsolutePath());
        jac.saveMTHFile();
    }
}
