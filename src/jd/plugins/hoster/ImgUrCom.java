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

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.http.Browser.BrowserException;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.nutils.encoding.HTMLEntities;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

/**
 * IMPORTANT: Never grab IDs bigger than 7 characters because these are Thumbnails - see API description: http://api.imgur.com/models/image
 * (scroll down to "Image thumbnails"
 */
@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "imgur.com" }, urls = { "https?://imgurdecrypted\\.com/download/([A-Za-z0-9]{7}|[A-Za-z0-9]{5})" }, flags = { 2 })
public class ImgUrCom extends PluginForHost {

    public ImgUrCom(PluginWrapper wrapper) {
        super(wrapper);
        setConfigElements();
    }

    @Override
    public String getAGBLink() {
        return "http://imgur.com/tos";
    }

    @SuppressWarnings("deprecation")
    @Override
    public void correctDownloadLink(final DownloadLink link) throws Exception {
        link.setUrlDownload(link.getDownloadURL().replace("imgurdecrypted.com/", "imgur.com/"));
    }

    /* User settings */
    private static final String SETTING_CLIENTID               = "SETTING_CLIENTID";
    private static final String SETTING_USE_API                = "SETTING_USE_API";

    /* Constants */
    public static final long    view_filesizelimit             = 20447232l;

    /* Variables */
    private String              dllink                         = null;
    private boolean             dl_IMPOSSIBLE_APILIMIT_REACHED = false;
    private String              imgUID                         = null;
    private boolean             start_DL                       = false;

    /**
     * TODO: 1. Maybe add a setting to download albums as .zip (if possible via site). 2. Maybe add a setting to add numbers in front of the
     * filenames (same way imgur does it when you download .zip files of albums).
     */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        imgUID = getImgUID(link);
        final String filetype = getFiletype(link);
        final String finalfilename = link.getStringProperty("decryptedfinalfilename", null);
        final long filesize = getLongProperty(link, "decryptedfilesize", -1);
        dllink = link.getStringProperty("directlink", null);

        br.setFollowRedirects(true);
        /* Avoid unneccessary requests --> If we have the directlink, filesize and a nice filename, do not access site/API! */
        if (dllink == null || filesize == -1 || finalfilename == null || filetype == null) {
            boolean api_failed = false;
            if (!this.getPluginConfig().getBooleanProperty(SETTING_USE_API, false)) {
                api_failed = true;
            } else {
                br.getHeaders().put("Authorization", getAuthorization());
                try {
                    br.getPage("https://api.imgur.com/3/image/" + imgUID);
                } catch (final BrowserException e) {
                    if (br.getHttpConnection().getResponseCode() == 429) {
                        api_failed = true;
                    } else {
                        throw e;
                    }
                }
            }
            if (!api_failed) {
                br.getRequest().setHtmlCode(br.toString().replace("\\", ""));
                if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("Unable to find an image with the id")) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                parseAPIData(link);
                /*
                 * Note that for pictures/especially GIFs over 20 MB, the "link" value will only contain a link which leads to a preview or
                 * low quality version of the picture. This is why we need a little workaround for this case (works from 19.5++ MB).
                 */
                /** TODO: Add a workaround for .gif files and/or wait for them to fix their current issues. */
                if (link.getDownloadSize() >= view_filesizelimit) {
                    logger.info("File is bigger than 20 (19.5) MB --> Using /downloadlink as API-workaround");
                    dllink = getBigFileDownloadlink(link);
                } else {
                    dllink = getJson(br.toString(), "link");
                }
            } else {
                /*
                 * Workaround for API limit reached or in case user disabled API - second way does return 503 response in case API limit is
                 * reached: http://imgur.com/download/ + imgUID. This code should never be reached!
                 */
                br.clearCookies("http://imgur.com/");
                br.getHeaders().put("Referer", null);
                br.getHeaders().put("Authorization", null);
                br.getPage("http://imgur.com/" + imgUID);
                if (br.getHttpConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                String api_like_json = br.getRegex("image[\t\n\r ]+:[\t\n\r ]+\\{(.*?)\\}").getMatch(0);
                if (api_like_json == null) {
                    api_like_json = br.getRegex("bind\\(analytics\\.popAndLoad, analytics, \\{(.*?)\\}").getMatch(0);
                }
                /* This would usually mean out of date but we keep it simple in this case */
                if (api_like_json == null) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                br.getRequest().setHtmlCode(api_like_json.replace("\\", ""));
                parseAPIData(link);
                /*
                 * Note that for pictures/especially GIFs over 20 MB, the "link" value will only contain a link which leads to a preview or
                 * low quality version of the picture. This is why we need a little workaround for this case (works from 19.5++ MB).
                 */
                if (link.getDownloadSize() >= view_filesizelimit) {
                    logger.info("File is bigger than 20 (19.5) MB --> Using /downloadlink as API-workaround");
                    dllink = getBigFileDownloadlink(link);
                } else {
                    dllink = getURLView(link);
                }
            }
            link.setProperty("directlink", dllink);
        } else if (!start_DL) {
            /*
             * Only check available link if user is NOT starting the download --> Avoid to access it twice in a small amount of time -->
             * Keep server load down.
             */
            URLConnectionAdapter con = null;
            try {
                try {
                    con = br.openGetConnection(dllink);
                } catch (final BrowserException e) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                if (con.getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                } else if (con.getContentType().contains("html")) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        start_DL = true;
        requestFileInformation(downloadLink);
        if (dl_IMPOSSIBLE_APILIMIT_REACHED) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Api limit reached", 10 * 60 * 1000l);
        } else if (dllink == null) {
            /* Should never happen */
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        /* Disable chunks as servers are fast and we usually only download small files. */
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            } else if (dl.getConnection().getResponseCode() == 503) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 503", 1 * 60 * 60 * 1000l);
            }
            logger.warning("Finallink leads to HTML code --> Following connection");
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    /**
     * Parses json either from API, or also if previously set in the browser - it's basically the same as a response similar to the API isa
     * stored in the html code when accessing normal content links: imgur.com/xXxXx
     */
    private void parseAPIData(final DownloadLink dl) throws PluginException {
        final long filesize = Long.parseLong(getJson(br.toString(), "size"));
        if (filesize == 0) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String title = getJson(br.toString(), "title");
        /* "mimetype" = site, "type" = API */
        String filetype = br.getRegex("\"(mime)?type\":\"image/([^<>\"]*?)\"").getMatch(1);
        if (filetype == null) {
            filetype = "jpeg";
        }
        String finalfilename;
        if (title == null || title.equals("")) {
            finalfilename = imgUID + "." + filetype;
        } else {
            title = Encoding.htmlDecode(title);
            title = HTMLEntities.unhtmlentities(title);
            title = HTMLEntities.unhtmlAmpersand(title);
            title = HTMLEntities.unhtmlAngleBrackets(title);
            title = HTMLEntities.unhtmlSingleQuotes(title);
            title = HTMLEntities.unhtmlDoubleQuotes(title);
            finalfilename = title + "." + filetype;
        }
        dl.setDownloadSize(filesize);
        dl.setProperty("decryptedfilesize", filesize);
        dl.setProperty("filetype", filetype);
        dl.setProperty("decryptedfinalfilename", finalfilename);
        dl.setFinalFileName(finalfilename);
    }

    public static String getBigFileDownloadlink(final DownloadLink dl) {
        final String imgUID = getImgUID(dl);
        final String filetype = getFiletype(dl);
        String downloadlink;
        if (filetype.equals("gif")) {
            /* Small workaround for gif files. */
            downloadlink = getURLView(dl);
        } else {
            downloadlink = getURLDownload(imgUID);
        }
        return downloadlink;
    }

    /** Returns a link for the user to open in browser. */
    public static final String getURLContent(final String imgUID) {
        String url_content;
        if (isJDStable()) {
            url_content = "http://imgur.com/" + imgUID;
        } else {
            url_content = "https://imgur.com/" + imgUID;
        }
        {
        }
        return url_content;
    }

    /** Returns downloadable imgur link. */
    public static final String getURLDownload(final String imgUID) {
        return "http://imgur.com/download/" + imgUID;
    }

    /** Returns viewable/downloadable imgur link. */
    public static final String getURLView(final DownloadLink dl) {
        final String imgUID = getImgUID(dl);
        final String filetype = getFiletype(dl);
        final String link_view = "http://i.imgur.com/" + imgUID + "." + filetype;
        return link_view;
    }

    public static String getImgUID(final DownloadLink dl) {
        return dl.getStringProperty("imgUID", null);
    }

    public static String getFiletype(final DownloadLink dl) {
        return dl.getStringProperty("filetype", null);
    }

    private String getJson(final String source, final String parameter) {
        String result = new Regex(source, "\"" + parameter + "\":([\t\n\r ]+)?([0-9\\.]+)").getMatch(1);
        if (result == null) {
            result = new Regex(source, "\"" + parameter + "\":([\t\n\r ]+)?\"([^<>\"]*?)\"").getMatch(1);
        }
        return result;
    }

    public static final String OAUTH_CLIENTID = "Mzc1YmE4Y2FmNjA0ZDQy";

    @SuppressWarnings("deprecation")
    public static final String getAuthorization() {
        String authorization;
        final String clientid = SubConfiguration.getConfig("imgur.com").getStringProperty(SETTING_CLIENTID, defaultClientID);
        if (clientid.contains("JDDEFAULT")) {
            authorization = Encoding.Base64Decode(OAUTH_CLIENTID);
        } else {
            authorization = clientid;
        }
        authorization = "Client-ID " + authorization;
        return authorization;
    }

    /* Stable workaround */
    public static long getLongProperty(final Property link, final String key, final long def) {
        try {
            return link.getLongProperty(key, def);
        } catch (final Throwable e) {
            try {
                Object r = link.getProperty(key, def);
                if (r instanceof String) {
                    r = Long.parseLong((String) r);
                } else if (r instanceof Integer) {
                    r = ((Integer) r).longValue();
                }
                final Long ret = (Long) r;
                return ret;
            } catch (final Throwable e2) {
                return def;
            }
        }
    }

    public static boolean isJDStable() {
        return System.getProperty("jd.revision.jdownloaderrevision") == null;
    }

    @Override
    public String getDescription() {
        return "This Plugin can download galleries/albums/images from imgur.com.";
    }

    private final static String defaultClientID = "JDDEFAULT";

    private void setConfigElements() {
        final ConfigEntry cfg = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), SETTING_USE_API, JDL.L("plugins.hoster.ImgUrCom.useAPI", "Use API (recommended!)")).setDefaultValue(true);
        getConfig().addEntry(cfg);
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), SETTING_CLIENTID, JDL.L("plugins.hoster.ImgUrCom.oauthClientID", "Enter your own imgur Oauth Client-ID:")).setDefaultValue(defaultClientID).setEnabledCondidtion(cfg, true));
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}