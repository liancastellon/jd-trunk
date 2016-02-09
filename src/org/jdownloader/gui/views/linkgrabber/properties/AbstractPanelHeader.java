package org.jdownloader.gui.views.linkgrabber.properties;

import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.table.JTableHeader;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtButton;
import org.appwork.utils.swing.SwingUtils;
import org.jdownloader.actions.AppAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.downloads.overviewpanel.CloseButton;
import org.jdownloader.gui.views.downloads.overviewpanel.SettingsButton;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.updatev2.gui.LAFOptions;

public abstract class AbstractPanelHeader extends MigPanel {
    private JButton   bt;
    private ExtButton options;
    private JLabel    lbl;
    private JLabel    icon;
    private String    labelString;

    private void updateLabelString() {
        if (labelString != null) {
            ;
            lbl.setText("");
            try {
                lbl.setText(org.appwork.sunwrapper.sun.swing.SwingUtilities2Wrapper.clipStringIfNecessary(lbl, lbl.getFontMetrics(getFont()), labelString, lbl.getWidth() - 15));
            } catch (Throwable e) {
                // http://www.oracle.com/technetwork/java/faq-sun-packages-142232.html
                e.printStackTrace();
                lbl.setText(labelString);
            }
        }
    }

    protected void setIcon(Icon icon) {
        this.icon.setIcon(icon);
    }

    protected void setText(String str) {
        labelString = str;
        updateLabelString();
    }

    private JTableHeader tableHeader;

    @Override
    public void paint(Graphics g) {
        // tableHeader.setPreferredSize(getPreferredSize());
        tableHeader.setSize(getSize());
        tableHeader.paint(g);
        super.paint(g);
    }

    public AbstractPanelHeader(String title, Icon imageIcon) {
        super("ins " + LAFOptions.getInstance().getExtension().customizePanelHeaderInsets(), "[]2[grow,fill][]0[]", "[grow,fill]");
        tableHeader = new JTableHeader();
        lbl = SwingUtils.toBold(new JLabel(""));
        this.addHierarchyBoundsListener(new HierarchyBoundsListener() {

            @Override
            public void ancestorResized(HierarchyEvent e) {
                updateLabelString();
            }

            @Override
            public void ancestorMoved(HierarchyEvent e) {
            }
        });

        add(icon = new JLabel(new AbstractIcon(IconKey.ICON_DOWNLOAD, 16)), "gapleft 1");
        add(lbl, "height 17!, wmax 100% - 61px");

        options = new SettingsButton(new AppAction() {
            {
                //

                setTooltipText(_GUI._.AbstractPanelHeader_AbstractPanelHeader_settings_tt());
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                onSettings(options);
            }
        });

        SwingUtils.setOpaque(lbl, false);

        setOpaque(false);

        bt = new CloseButton(new AppAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                onCloseAction();
            }

        });

        add(options, "height 17!,width 24!");
        add(bt, "width 17!,height 17!");
        setText(title);
        setIcon(imageIcon);
    }

    abstract protected void onSettings(ExtButton options);

    abstract protected void onCloseAction();
}
