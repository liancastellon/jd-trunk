//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jdownloader.plugins.components.XFileSharingProBasic;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class DdlTo extends XFileSharingProBasic {
    private final String   maxSimultaneousDownloads_LIMIT = "MaxSimultaneousDownloads_LIMIT_2019_06";
    private final String[] maxSimultaneousDownloads       = new String[] { "DEFAULT", "2", "1" };

    public DdlTo(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(super.getPurchasePremiumURL());
        setConfigElements();
    }

    /**
     * DEV NOTES XfileSharingProBasic Version SEE SUPER-CLASS<br />
     * mods: See overridden functions<br />
     * limit-info: 2019-05-22: premium untested, set FREE account limits <br />
     * captchatype-info: 2019-05-22: null<br />
     * other:<br />
     */
    private static String[] domains = new String[] { "ddl.to", "wi.to" };

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
    public int getMaxChunks(final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return 1;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return 1;
        } else {
            /* Free(anonymous) and unknown account type */
            return 1;
        }
    }

    public int getMaxDownloadSelect() {
        final int chosenDownloadLimit = getPluginConfig().getIntegerProperty(maxSimultaneousDownloads_LIMIT, 0);
        try {
            return Integer.parseInt(maxSimultaneousDownloads[chosenDownloadLimit]);
        } catch (final Throwable e) {
            /* Return default limit */
            logger.log(e);
            return 1;
        }
    }

    @Override
    public int getMaxSimultaneousFreeAnonymousDownloads() {
        return getMaxDownloadSelect();
    }

    @Override
    public int getMaxSimultaneousFreeAccountDownloads() {
        return getMaxDownloadSelect();
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return 2;
    }

    public static String[] getAnnotationNames() {
        /*
         * only return the first/valid domain, else rewrite won't happen when the other domain is still signaled as existing!
         */
        return new String[] { domains[0] };
    }

    @Override
    public String[] siteSupportedNames() {
        return domains;
    }

    public static String[] getAnnotationUrls() {
        final List<String> ret = new ArrayList<String>();
        for (int i = 0; i < domains.length; i++) {
            if (i == 0) {
                /* Match all URLs on first (=current) domain */
                ret.add("https?://(?:www\\.)?" + getHostsPatternPart() + XFileSharingProBasic.getDefaultAnnotationPatternPart());
            } else {
                break;
            }
        }
        return ret.toArray(new String[0]);
    }

    /** Returns '(?:domain1|domain2)' */
    public static String getHostsPatternPart() {
        final StringBuilder pattern = new StringBuilder();
        pattern.append("(?:");
        for (final String name : domains) {
            pattern.append((pattern.length() > 0 ? "|" : "") + Pattern.quote(name));
        }
        pattern.append(")");
        return pattern.toString();
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX_INDEX, getPluginConfig(), maxSimultaneousDownloads_LIMIT, maxSimultaneousDownloads, "Max. simultaneous downloads (Free+Free account)").setDefaultValue(0));
    }
}