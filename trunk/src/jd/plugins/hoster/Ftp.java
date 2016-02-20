//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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

import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.WeakHashMap;

import jd.PluginWrapper;
import jd.controlling.linkcrawler.CrawledLink;
import jd.http.Browser;
import jd.nutils.SimpleFTP;
import jd.nutils.SimpleFTP.SimpleFTPListEntry;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.download.SimpleFTPDownloadInterface;

import org.appwork.utils.StringUtils;
import org.jdownloader.DomainInfo;

// DEV NOTES:
// - ftp filenames can contain & characters!

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "ftp" }, urls = { "ftpviajd://.*?\\.[a-zA-Z0-9]{2,}(:\\d+)?/[^\"\r\n ]+" }, flags = { 0 })
public class Ftp extends PluginForHost {

    public Ftp(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getHost(DownloadLink link, Account account) {
        if (link != null) {
            return Browser.getHost(link.getDownloadURL(), true);
        } else {
            return super.getHost(link, account);
        }
    }

    @Override
    public CrawledLink convert(DownloadLink link) {
        final String url = link.getPluginPatternMatcher();
        link.setPluginPatternMatcher(url.replace("ftpviajd://", "ftp://"));
        link.setLinkID(null);
        return super.convert(link);
    }

    @Override
    public boolean isSpeedLimited(DownloadLink link, Account account) {
        return false;
    }

    private void connect(SimpleFTP ftp, final DownloadLink downloadLink, URL url) throws Exception {
        try {
            ftp.connect(url);
        } catch (IOException e) {
            final String msg = e.getMessage();
            if (StringUtils.containsIgnoreCase(msg, "Sorry, the maximum number of clients") || StringUtils.startsWithCaseInsensitive(msg, "421")) {
                final String maxConnections = new Regex(e.getMessage(), "Sorry, the maximum number of clients \\((\\d+)\\)").getMatch(0);
                if (maxConnections != null) {
                    downloadLink.setProperty("MAX_FTP_CONNECTIONS", Integer.parseInt(maxConnections));
                } else {
                    downloadLink.setProperty("MAX_FTP_CONNECTIONS", 1);
                }
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Connection limit reached", 30 * 1000l);
            }
            throw e;
        }
    }

    public void download(String ftpurl, final DownloadLink downloadLink, boolean throwException) throws Exception {
        final SimpleFTP ftp = new SimpleFTP();
        try {
            ftp.setLogger(logger);
            URL url = new URL(ftpurl);
            /* cut off all ?xyz at the end */
            String filePath = new Regex(ftpurl, "://[^/]+/(.+?)(\\?|$)").getMatch(0);
            connect(ftp, downloadLink, url);
            checkFile(ftp, downloadLink, filePath);
            dl = new SimpleFTPDownloadInterface(ftp, downloadLink, filePath);
            dl.startDownload();
        } catch (IOException e) {
            if (throwException && e.getMessage() != null && e.getMessage().contains("530")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, "Login incorrect");
            } else {
                throw e;
            }
        } finally {
            try {
                ftp.disconnect();
            } catch (final Throwable e) {
            }
        }
    }

    private void checkFile(SimpleFTP ftp, DownloadLink downloadLink, String filePath) throws Exception {
        final String currentDir = ftp.getDir();
        /* switch binary mode */
        ftp.bin();
        /*
         * some servers do not allow to list the folder, so this may fail but file still might be online
         */
        if (!filePath.startsWith("/")) {
            filePath = "/" + filePath;
        }
        if (!filePath.startsWith(currentDir)) {
            if (currentDir.endsWith("/")) {
                filePath = currentDir + filePath.substring(1);
            } else {
                filePath = currentDir + filePath;
            }
        }

        long size = ftp.getSize(filePath);
        String name = null;
        if (size == -1) {
            if (ftp.wasLatestOperationNotPermitted()) {
                final SimpleFTPListEntry fileInfo = ftp.getFileInfo(filePath);
                if (fileInfo != null) {
                    size = fileInfo.getSize();
                    name = jd.plugins.decrypter.Ftp.BestEncodingGuessingURLDecode(name);
                }
            } else {
                /* some server need / at the beginning */
                filePath = "/" + filePath;
                size = ftp.getSize(filePath);
            }
        }
        if (size != -1) {
            if (name == null) {
                /* cut off all ?xyz at the end */
                name = new Regex(filePath, ".*/(.+?)(\\?|$)").getMatch(0);
                if (name != null) {
                    name = jd.plugins.decrypter.Ftp.BestEncodingGuessingURLDecode(name);
                } else {
                    logger.severe("could not get filename from ftpurl");
                    name = downloadLink.getName();
                }
            }
        }
        if (name == null) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (downloadLink.getFinalFileName() == null) {
            downloadLink.setFinalFileName(name);
        }
        if (size >= 0 && downloadLink.getVerifiedFileSize() < 0) {
            downloadLink.setVerifiedFileSize(size);
        }
    }

    @Override
    public String getAGBLink() {
        return "http://jdownloader.org";
    }

    private final String                            MAX_FTP_CONNECTIONS    = "MAX_FTP_CONNECTIONS";

    private static WeakHashMap<DomainInfo, Integer> MAX_FTP_CONNECTION_MAP = new WeakHashMap<DomainInfo, Integer>();

    @Override
    public int getMaxSimultanDownload(DownloadLink link, Account account) {
        if (link != null) {
            synchronized (MAX_FTP_CONNECTION_MAP) {
                final Integer ret = MAX_FTP_CONNECTION_MAP.get(link.getDomainInfo());
                if (ret != null) {
                    return ret;
                }
            }
            final int max = link.getIntegerProperty(MAX_FTP_CONNECTIONS, -1);
            if (max >= 1) {
                synchronized (MAX_FTP_CONNECTION_MAP) {
                    MAX_FTP_CONNECTION_MAP.put(link.getDomainInfo(), max);
                }
                return max;
            }
        }
        return super.getMaxSimultanDownload(link, account);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 20;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        download(downloadLink.getDownloadURL(), downloadLink, false);
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink downloadLink) throws Exception {
        final SimpleFTP ftp = new SimpleFTP();
        try {
            ftp.setLogger(logger);
            final URL url = new URL(downloadLink.getDownloadURL());
            /* cut off all ?xyz at the end */
            String filePath = new Regex(downloadLink.getDownloadURL(), "://[^/]+/(.+?)(\\?|$)").getMatch(0);
            connect(ftp, downloadLink, url);
            checkFile(ftp, downloadLink, filePath);
        } catch (ConnectException e) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } catch (UnknownHostException e) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } catch (IOException e) {
            logger.severe(e.getMessage());
            if (e.getMessage().contains("530")) {
                downloadLink.getLinkStatus().setErrorMessage("Login incorrect");
                return AvailableStatus.UNCHECKABLE;
            } else {
                throw e;
            }
        } catch (Exception e) {
            logger.severe(e.getMessage());
            throw e;
        } finally {
            try {
                ftp.disconnect();
            } catch (final Throwable e) {
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
        link.setProperty("RESUME", true);
        link.removeProperty(MAX_FTP_CONNECTIONS);
        synchronized (MAX_FTP_CONNECTION_MAP) {
            MAX_FTP_CONNECTION_MAP.remove(link.getDomainInfo());
        }
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public Boolean siteTesterDisabled() {
        return Boolean.TRUE;
    }
}