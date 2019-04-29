//jDownloader - Downloadmanager
//Copyright (C) 2016  JD-Team support@jdownloader.org
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

import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.components.YetiShareCore;

import jd.PluginWrapper;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = {}, urls = {})
public class LetsuploadCo extends YetiShareCore {
    public LetsuploadCo(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(getPurchasePremiumURL());
    }

    /**
     * DEV NOTES YetiShare<br />
     ****************************
     * mods: See overridden functions<br />
     * limit-info: 2019-04-25: no limits at all<br />
     * captchatype-info: 2019-04-25: null<br />
     * other: <br />
     */
    /* 1st domain = current domain! */
    public static String[] domains = new String[] { "letsupload.co" };

    public static String[] getAnnotationNames() {
        return new String[] { domains[0] };
    }

    /**
     * returns the annotation pattern array: 'https?://(?:www\\.)?(?:domain1|domain2)/[A-Za-z0-9]+(?:/[^/<>]+)?'
     *
     */
    public static String[] getAnnotationUrls() {
        final String host = getHostsPattern();
        return new String[] { host + "/(?!folder)[A-Za-z0-9]+(?:/[^/<>]+)?" };
    }

    /** Returns '(?:domain1|domain2)' */
    private static String getHostsPatternPart() {
        final StringBuilder pattern = new StringBuilder();
        for (final String name : domains) {
            pattern.append((pattern.length() > 0 ? "|" : "") + Pattern.quote(name));
        }
        return pattern.toString();
    }

    /** returns 'https?://(?:www\\.)?(?:domain1|domain2)' */
    private static String getHostsPattern() {
        final String hosts = "https?://(?:www\\.)?" + "(?:" + getHostsPatternPart() + ")";
        return hosts;
    }

    @Override
    public String[] siteSupportedNames() {
        return domains;
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

    public int getMaxChunks(final Account account) {
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

    public int getMaxSimultaneousFreeAccountDownloads() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    /** 2019-04-25: Special */
    @Override
    public String[] scanInfo(final String[] fileInfo) {
        if (supports_availablecheck_over_info_page()) {
            fileInfo[0] = br.getRegex("<span>Filename[^<]*?<p>([^<>\"]+)</p>").getMatch(0);
            fileInfo[1] = br.getRegex("<span>Filesize[^<]*?<p>([^<>\"]+)</p>").getMatch(0);
        } else {
            fileInfo[0] = br.getRegex("class=\"fa fa-file\\-text\"></i>([^<>\"]+)</div>").getMatch(0);
            fileInfo[1] = br.getRegex("size[^<]*?<p>([^<>\"]+)</p>").getMatch(0);
        }
        if (StringUtils.isEmpty(fileInfo[0]) || StringUtils.isEmpty(fileInfo[1])) {
            super.scanInfo(fileInfo);
        }
        return fileInfo;
    }

    public boolean supports_https() {
        return super.supports_https();
    }

    public boolean supports_availablecheck_over_info_page() {
        return super.supports_availablecheck_over_info_page();
    }

    public boolean requires_WWW() {
        return super.requires_WWW();
    }

    public boolean enable_random_user_agent() {
        return super.enable_random_user_agent();
    }
}