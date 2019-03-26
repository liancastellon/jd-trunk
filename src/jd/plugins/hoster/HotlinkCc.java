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

import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.PluginException;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.components.XFileSharingProBasic;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class HotlinkCc extends XFileSharingProBasic {
    public HotlinkCc(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(super.getPurchasePremiumURL());
    }

    /**
     * DEV NOTES XfileSharingProBasic Version SEE SUPER-CLASS<br />
     * mods: See overridden functions<br />
     * limit-info:<br />
     * captchatype-info: 2019-02-11: null<br />
     * other:<br />
     */
    private static String[] domains = new String[] { "hotlink.cc" };

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
            return 0;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 1;
        }
    }

    @Override
    public int getMaxSimultaneousFreeAnonymousDownloads() {
        return 1;
    }

    @Override
    public int getMaxSimultaneousFreeAccountDownloads() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public boolean supports_https() {
        return true;
    }

    @Override
    public boolean isAudiohoster() {
        return false;
    }

    @Override
    public boolean isVideohoster() {
        return false;
    }

    @Override
    public boolean isVideohoster_2() {
        return false;
    }

    @Override
    public boolean isVideohoster_enforce_video_filename() {
        return false;
    }

    @Override
    public boolean supports_precise_expire_date() {
        // remove after next full core update, pattern changes pending in XFileSharingPro
        return false;
    }

    @Override
    public void doFree(DownloadLink link, Account account) throws Exception, PluginException {
        if (checkShowFreeDialog(getHost())) {
            showFreeDialog(getHost());
        }
        super.doFree(link, account);
    }

    @Override
    public boolean isImagehoster() {
        return false;
    }

    @Override
    public boolean supports_availablecheck_alt() {
        return true;
    }

    @Override
    public boolean supports_availablecheck_filesize_alt_fast() {
        return true;
    }

    @Override
    public boolean supports_availablecheck_filename_abuse() {
        return true;
    }

    @Override
    public boolean supports_availablecheck_filesize_html() {
        return true;
    }

    @Override
    public boolean isOffline(final DownloadLink link) {
        final String fuid = super.getFUIDFromURL(link);
        boolean isOffline = super.isOffline(this.br, link);
        if (!br.getURL().contains(fuid) || (br.getRedirectLocation() != null && !br.getRedirectLocation().contains(fuid))) {
            /* 2018-11-15: Special - redirect to: https://takefile.link/upgrade */
            isOffline = true;
        }
        return isOffline;
    }

    @Override
    public String regExTrafficLeft() {
        String trafficleft = super.regExTrafficLeft();
        if (StringUtils.isEmpty(trafficleft)) {
            trafficleft = new Regex(correctedBR, "Traffic available today</TD></TR>\\s*?</thead>\\s*?<TR><TD><b>([^<>\"]+)</b><").getMatch(0);
        }
        return trafficleft;
    }

    public static String[] getAnnotationNames() {
        return new String[] { domains[0] };
    }

    @Override
    public String[] siteSupportedNames() {
        return domains;
    }

    /**
     * returns the annotation pattern array: 'https?://(?:www\\.)?(?:domain1|domain2)/(?:embed\\-)?[a-z0-9]{12}'
     *
     */
    public static String[] getAnnotationUrls() {
        // construct pattern
        final String host = getHostsPattern();
        return new String[] { host + "/(?:embed\\-)?[a-z0-9]{12}" };
    }

    /** returns 'https?://(?:www\\.)?(?:domain1|domain2)' */
    private static String getHostsPattern() {
        final String hosts = "https?://(?:www\\.)?" + "(?:" + getHostsPatternPart() + ")";
        return hosts;
    }

    /** Returns '(?:domain1|domain2)' */
    public static String getHostsPatternPart() {
        final StringBuilder pattern = new StringBuilder();
        for (final String name : domains) {
            pattern.append((pattern.length() > 0 ? "|" : "") + Pattern.quote(name));
        }
        return pattern.toString();
    }
}