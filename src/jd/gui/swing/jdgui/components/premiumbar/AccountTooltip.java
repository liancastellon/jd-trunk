package jd.gui.swing.jdgui.components.premiumbar;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import jd.config.Property;
import jd.controlling.AccountController;
import jd.gui.swing.jdgui.views.settings.panels.accountmanager.AccountEntry;
import jd.plugins.Account;
import jd.plugins.AccountInfo;

import org.appwork.swing.components.tooltips.PanelToolTip;
import org.appwork.swing.components.tooltips.TooltipPanel;
import org.appwork.utils.swing.SwingUtils;
import org.jdownloader.DomainInfo;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.plugins.controller.host.HostPluginController;
import org.jdownloader.plugins.controller.host.LazyHostPlugin;
import org.jdownloader.updatev2.gui.LAFOptions;

public class AccountTooltip extends PanelToolTip {
    private Color                 color;
    private AccountListTable      table;
    private AccountListTableModel model;
    private PremiumStatus         owner;

    public Point getDesiredLocation(JComponent activeComponent, Point ttPosition) {
        ttPosition.y = activeComponent.getLocationOnScreen().y - getPreferredSize().height;
        ttPosition.x = activeComponent.getLocationOnScreen().x;
        return ttPosition;
    }

    public AccountTooltip(PremiumStatus owner, AccountCollection accountCollection) {

        super(new TooltipPanel("ins 0,wrap 1", "[grow,fill]", "[][grow,fill]"));
        this.owner = owner;
        color = (LAFOptions.getInstance().getColorForTooltipForeground());
        List<Account> accs = AccountController.getInstance().list();

        final LinkedList<AccountEntry> domains = new LinkedList<AccountEntry>();
        for (Account acc : accountCollection) {

            domains.add(new AccountEntry(acc));

        }

        table = new AccountListTable(model = new AccountListTableModel(this));
        model.setData(domains);
        model.addTableModelListener(new TableModelListener() {

            @Override
            public void tableChanged(TableModelEvent e) {
                AccountTooltip.this.owner.redraw();
                table.getTableHeader().repaint();
            }
        });
        table.getTableHeader().setOpaque(false);

        JScrollPane sp;
        String txt = accountCollection.getDomainInfo().getTld();
        if (accountCollection.isMulti()) {
            txt = _GUI._.AccountTooltip_AccountTooltip_multi(accountCollection.getDomainInfo().getTld());
        }
        JLabel label = new JLabel(txt, accountCollection.getDomainInfo().getFavIcon(), JLabel.LEFT);
        SwingUtils.toBold(label);
        label.setForeground(LAFOptions.getInstance().getColorForTooltipForeground());
        panel.add(label, "gapleft 5");
        panel.add(table.getTableHeader());
        panel.add(table);

        if (accountCollection.isMulti()) {
            label = new JLabel(_GUI._.AccountTooltip_AccountTooltip_supported_hosters());
            SwingUtils.toBold(label);
            label.setForeground(LAFOptions.getInstance().getColorForTooltipForeground());
            panel.add(label);
            for (DomainInfo info : getDomainInfos(accountCollection)) {
                label = new JLabel(info.getTld(), info.getFavIcon(), JLabel.LEFT);

                label.setForeground(LAFOptions.getInstance().getColorForTooltipForeground());
                panel.add(label);
            }
        }
        // panel.add(sp = new JScrollPane(table));
        // sp.setBackground(null);
        // table.setBackground(LAFOptions.getInstance().getColorForTooltipBackground());
        // table.setOpaque(true);
        // table.getTableHeader().setBackground(LAFOptions.getInstance().getColorForTooltipBackground());

        // panel.setPreferredSize(new Dimension(500, 100));
        // panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, 400));
    }

    private List<DomainInfo> getDomainInfos(AccountCollection accountCollection) {
        ArrayList<DomainInfo> ret = new ArrayList<DomainInfo>();
        HashSet<DomainInfo> domains = new HashSet<DomainInfo>();
        HashSet<String> plugins = new HashSet<String>();
        for (Account acc : accountCollection) {
            AccountInfo ai = acc.getAccountInfo();
            if (ai != null) {
                ;
                Object supported = null;
                synchronized (ai) {
                    /*
                     * synchronized on accountinfo because properties are not threadsafe
                     */
                    supported = ai.getProperty("multiHostSupport", Property.NULL);
                }
                if (Property.NULL != supported && supported != null) {

                    synchronized (supported) {
                        /*
                         * synchronized on list because plugins can change the list in runtime
                         */

                        if (supported instanceof ArrayList) {
                            for (String sup : (java.util.List<String>) supported) {
                                LazyHostPlugin plg = HostPluginController.getInstance().get((String) sup);
                                if (plg != null && plugins.add(plg.getClassname())) {
                                    if (domains.add(DomainInfo.getInstance(plg.getHost()))) {
                                        ret.add(DomainInfo.getInstance(plg.getHost()));
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
        Collections.sort(ret, new Comparator<DomainInfo>() {

            @Override
            public int compare(DomainInfo o1, DomainInfo o2) {
                return o1.getTld().compareToIgnoreCase(o2.getTld());
            }
        });
        return ret;
    }

    public void update() {

        table.getModel().fireTableStructureChanged();
    }
}
