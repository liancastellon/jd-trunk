//    jDownloader - Downloadmanager
//    Copyright (C) 2013  JD-Team support@jdownloader.org
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
package jd.plugins.hoster;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtPasswordField;
import org.appwork.utils.StringUtils;
import org.jdownloader.gui.InputChangedCallbackInterface;
import org.jdownloader.plugins.accounts.AccountBuilderInterface;
import org.jdownloader.plugins.components.usenet.UsenetAccountConfigInterface;
import org.jdownloader.plugins.components.usenet.UsenetConfigPanel;
import org.jdownloader.plugins.components.usenet.UsenetServer;
import org.jdownloader.plugins.config.AccountConfigInterface;
import org.jdownloader.plugins.config.Order;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;
import org.jdownloader.translate._JDT;

import jd.PluginWrapper;
import jd.gui.swing.components.linkbutton.JLink;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.PluginConfigPanelNG;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "premiumize.me" }, urls = { "premiumizedecrypted://.+" })
public class PremiumizeMe extends ZeveraCore {
    public PremiumizeMe(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www." + this.getHost() + "/premium");
    }

    @Override
    public String getClientID() {
        return "616325511";
    }

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return true;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return true;
        } else {
            /* Free(anonymous) and unknown account type */
            return true;
        }
    }

    @Override
    public int getDownloadModeMaxChunks(final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return 0;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 0;
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public int getMaxSimultaneousFreeAccountDownloads() {
        /* 2019-02-19: premiumize.me/free */
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void setFreeAccountTraffic(final AccountInfo ai) {
        ai.setTrafficLeft(5000000000l);
    }

    @Override
    protected PluginConfigPanelNG createConfigPanel() {
        return new UsenetConfigPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean showKeyHandler(KeyHandler<?> keyHandler) {
                return "ssldownloadsenabled".equals(keyHandler.getKey());
            }

            @Override
            protected boolean useCustomUI(KeyHandler<?> keyHandler) {
                return !"ssldownloadsenabled".equals(keyHandler.getKey());
            }

            @Override
            protected void initAccountConfig(PluginForHost plgh, Account acc, Class<? extends AccountConfigInterface> cf) {
                super.initAccountConfig(plgh, acc, cf);
                extend(this, getHost(), getAvailableUsenetServer(), getAccountJsonConfig(acc));
            }
        };
    }

    /**
     * TODO: Maybe add a setting to not add .nzb and .torrent files when adding cloud folders as JD will automatically add the contents of
     * .nzb files after downloading them but in this case that makes no sense as when users add cloud URLs these will contain the
     * downloaded- and extracted contents of .nzb(and .torrent) files already.
     */
    public static interface PremiumizeMeConfigInterface extends UsenetAccountConfigInterface {
        public class Translation {
            public String getSSLDownloadsEnabled_label() {
                return _JDT.T.lit_ssl_enabled();
            }
        }

        public static final PremiumizeMeConfigInterface.Translation TRANSLATION = new Translation();

        @DefaultBooleanValue(true)
        @Order(10)
        boolean isSSLDownloadsEnabled();

        void setSSLDownloadsEnabled(boolean b);
    };

    @Override
    public AccountBuilderInterface getAccountFactory(InputChangedCallbackInterface callback) {
        return new PremiumizeAccountFactory(callback);
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) throws Exception {
        if (StringUtils.equals(getHost(), downloadLink.getHost()) && account == null) {
            // generated links do not require an account
            return true;
        } else {
            return account != null;
        }
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST, FEATURE.USENET };
    }

    @Override
    public List<UsenetServer> getAvailableUsenetServer() {
        final List<UsenetServer> ret = new ArrayList<UsenetServer>();
        ret.addAll(UsenetServer.createServerList("usenet.premiumize.me", false, 119));
        ret.addAll(UsenetServer.createServerList("usenet.premiumize.me", true, 563));
        return ret;
    }

    public static class PremiumizeAccountFactory extends MigPanel implements AccountBuilderInterface {
        /**
         *
         */
        private static final long serialVersionUID = 1L;
        private final String      PINHELP          = "Enter your Apikey";

        private String getPassword() {
            if (this.pass == null) {
                return null;
            }
            if (EMPTYPW.equals(new String(this.pass.getPassword()))) {
                return null;
            }
            return new String(this.pass.getPassword());
        }

        public boolean updateAccount(Account input, Account output) {
            boolean changed = false;
            if (!StringUtils.equals(input.getUser(), output.getUser())) {
                output.setUser(input.getUser());
                changed = true;
            }
            if (!StringUtils.equals(input.getPass(), output.getPass())) {
                output.setPass(input.getPass());
                changed = true;
            }
            return changed;
        }

        private final ExtPasswordField pass;
        private static String          EMPTYPW = "                 ";

        public PremiumizeAccountFactory(final InputChangedCallbackInterface callback) {
            super("ins 0, wrap 2", "[][grow,fill]", "");
            add(new JLabel("Click here to find your API-Key / PIN:"));
            add(new JLink("https://www.premiumize.me/account"));
            add(new JLabel("API-Key / PIN:"));
            add(this.pass = new ExtPasswordField() {
                @Override
                public void onChanged() {
                    callback.onChangedInput(this);
                }
            }, "");
            pass.setHelpText(PINHELP);
        }

        @Override
        public JComponent getComponent() {
            return this;
        }

        @Override
        public void setAccount(Account defaultAccount) {
            if (defaultAccount != null) {
                // name.setText(defaultAccount.getUser());
                pass.setText(defaultAccount.getPass());
            }
        }

        @Override
        public boolean validateInputs() {
            // final String userName = getUsername();
            // if (userName == null || !userName.trim().matches("^\\d{9}$")) {
            // idLabel.setForeground(Color.RED);
            // return false;
            // }
            // idLabel.setForeground(Color.BLACK);
            return getPassword() != null;
        }

        @Override
        public Account getAccount() {
            return new Account(null, getPassword());
        }
    }
}