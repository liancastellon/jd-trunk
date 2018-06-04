package jd.plugins.hoster;

import jd.PluginWrapper;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

/**
 * This plugin only serves as a FallBack in case another plugin could not be initiated
 *
 * @author daniel
 *
 */
@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "UpdateRequired" }, urls = { "LXISAWESOME" })
public class UpdateRequiredHostPlugin extends PluginForHost {
    public UpdateRequiredHostPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return null;
    }

    public String getHost(DownloadLink link, Account account) {
        if (link != null) {
            return link.getHost();
        }
        if (account != null) {
            return account.getHoster();
        }
        return null;
    };

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        ai.setStatus("Update required:" + account.getHoster());
        return ai;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink parameter) throws Exception {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Update required:" + parameter.getHost());
    }

    @Override
    public void handleFree(DownloadLink link) throws Exception {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Update required:" + link.getHost());
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Update required:" + link.getHost());
    }

    @Override
    public void handleMultiHost(DownloadLink downloadLink, Account account) throws Exception {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Update required:" + downloadLink.getHost());
    }

    @Override
    public boolean assignPlugin(DownloadLink link) {
        if (link != null) {
            link.setDefaultPlugin(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean assignPlugin(Account account) {
        if (account != null) {
            account.setPlugin(this);
            return true;
        }
        return false;
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.INTERNAL };
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
