//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
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
package jd.plugins.decrypter;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.requests.GetRequest;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

import org.appwork.storage.JSonStorage;
import org.appwork.utils.Hash;
import org.appwork.utils.StringUtils;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "instagram.com" }, urls = { "https?://(www\\.)?instagram\\.com/(?!explore/)(p/[A-Za-z0-9_-]+|[^/]+)" })
public class InstaGramComDecrypter extends PluginForDecrypt {
    public InstaGramComDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String           TYPE_GALLERY           = ".+/p/[A-Za-z0-9_-]+/?$";
    private String                        username_url           = null;
    private final ArrayList<DownloadLink> decryptedLinks         = new ArrayList<DownloadLink>();
    private boolean                       prefer_server_filename = jd.plugins.hoster.InstaGramCom.defaultPREFER_SERVER_FILENAMES;
    private Boolean                       isPrivate              = false;
    private FilePackage                   fp                     = null;
    private String                        parameter              = null;

    private Object get(Map<String, Object> entries, final String... paths) {
        for (String path : paths) {
            final Object ret = JavaScriptEngineFactory.walkJson(entries, path);
            if (ret != null) {
                return ret;
            }
        }
        return null;
    }

    private void getPage(CryptedLink link, final Browser br, String url, final String rhxGis, final String variables) throws Exception {
        int retry = 0;
        while (retry < 10 && !isAbort()) {
            final GetRequest get = br.createGetRequest(url);
            if (rhxGis != null && variables != null) {
                if (false) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } else {
                    final String sig = Hash.getMD5(rhxGis + ":" + variables);
                    get.getHeaders().put("X-Instagram-GIS", sig);
                }
            }
            br.getPage(get);
            final int responsecode = br.getHttpConnection().getResponseCode();
            if (responsecode == 502) {
                sleep(20000 + 15000 * retry++, link);
            } else if (responsecode == 403 || responsecode == 429) {
                if (SubConfiguration.getConfig(this.getHost()).getBooleanProperty(jd.plugins.hoster.InstaGramCom.QUIT_ON_RATE_LIMIT_REACHED, jd.plugins.hoster.InstaGramCom.defaultQUIT_ON_RATE_LIMIT_REACHED)) {
                    logger.info("abort_on_rate_limit_reached setting active --> Rate limit has been reached --> Aborting");
                    break;
                } else {
                    sleep(20000 + 15000 * retry++, link);
                }
            } else {
                break;
            }
        }
        if (br.getHttpConnection().getResponseCode() == 502) {
            throw br.new BrowserException(new IOException("ResponseCode:502"), br.getRequest());
        }
    }

    private static Map<String, String> QUERY_HASH = new HashMap<String, String>();

    // https://www.diggernaut.com/blog/how-to-scrape-pages-infinite-scroll-extracting-data-from-instagram/
    private static String getByUserIDQueryHash(Browser br) throws Exception {
        synchronized (QUERY_HASH) {
            final String profilePageContainer = br.getRegex("(/static/bundles/(?:metro|base)/ProfilePageContainer.js/[a-f0-9]+.js)").getMatch(0);
            if (profilePageContainer != null) {
                final String ret = QUERY_HASH.get(profilePageContainer);
                if (ret != null) {
                    return ret;
                }
                final Browser brc = br.cloneBrowser();
                brc.getPage(profilePageContainer);
                String queryHash = brc.getRegex("profilePosts\\.byUserId.get\\(t\\)\\)\\?[a-z]\\.pagination:[a-z]\\},queryI(?:d|D)\\s*:\\s*\"([0-9A-z]{32,32})\"").getMatch(0);
                if (queryHash == null) {
                    queryHash = brc.getRegex("profilePosts\\.byUserId.get\\([a-z]\\)\\)[^}]*?[a-z]\\.pagination(?::[a-z])?\\},queryI(?:d|D)\\s*:\\s*\"([0-9A-z]{32,32})\"").getMatch(0);
                }
                if (queryHash != null) {
                    QUERY_HASH.put(profilePageContainer, queryHash);
                    return queryHash;
                } else {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            } else {
                return null;
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        br = new Browser();
        br.addAllowedResponseCodes(502);
        prefer_server_filename = SubConfiguration.getConfig(this.getHost()).getBooleanProperty(jd.plugins.hoster.InstaGramCom.PREFER_SERVER_FILENAMES, jd.plugins.hoster.InstaGramCom.defaultPREFER_SERVER_FILENAMES);
        fp = FilePackage.getInstance();
        fp.setProperty("ALLOW_MERGE", true);
        // https and www. is required!
        parameter = param.toString().replaceFirst("^http://", "https://").replaceFirst("://in", "://www.in");
        if (parameter.contains("?private_url=true")) {
            isPrivate = Boolean.TRUE;
            /* Remove this from url as it is only required for decrypter */
            parameter = parameter.replace("?private_url=true", "");
        }
        if (!parameter.endsWith("/")) {
            /* Add slash to the end to prevent 302 redirect to speed up the crawl process a tiny bit. */
            parameter += "/";
        }
        final PluginForHost hostplugin = JDUtilities.getPluginForHost(this.getHost());
        boolean logged_in = false;
        final Account aa = AccountController.getInstance().getValidAccount(hostplugin);
        if (aa != null) {
            /* Login whenever possible */
            try {
                jd.plugins.hoster.InstaGramCom.login(this.br, aa, false);
                logged_in = true;
            } catch (final PluginException e) {
                handleAccountException(aa, e);
            }
        }
        if (isPrivate && !logged_in) {
            logger.info("Account required to crawl this url");
            return decryptedLinks;
        }
        jd.plugins.hoster.InstaGramCom.prepBR(this.br);
        br.addAllowedResponseCodes(502);
        getPage(param, br, parameter, null, null);
        final String rhxGis = br.getRegex("\"rhx_gis\"\\s*:\\s*\"([a-f0-9]{32})\"").getMatch(0);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        final String queryHash = getByUserIDQueryHash(br);
        final String json = br.getRegex(">window\\._sharedData\\s*?=\\s*?(\\{.*?);</script>").getMatch(0);
        if (json == null) {
            return null;
        }
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json);
        ArrayList<Object> resource_data_list;
        if (parameter.matches(TYPE_GALLERY)) {
            /* Crawl single images & galleries */
            resource_data_list = (ArrayList) JavaScriptEngineFactory.walkJson(entries, "entry_data/PostPage");
            for (final Object galleryo : resource_data_list) {
                entries = (LinkedHashMap<String, Object>) galleryo;
                entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "graphql/shortcode_media");
                username_url = (String) JavaScriptEngineFactory.walkJson(entries, "owner/username");
                this.isPrivate = ((Boolean) JavaScriptEngineFactory.walkJson(entries, "owner/is_private")).booleanValue();
                if (username_url != null) {
                    fp.setName(username_url);
                }
                decryptAlbum(entries);
            }
            if (decryptedLinks.size() == 0) {
                System.out.println("WTF");
            }
            return decryptedLinks;
        } else {
            if (!this.br.containsHTML("user\\?username=.+")) {
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            /* Crawl all items of a user */
            String id_owner = (String) get(entries, "entry_data/ProfilePage/{0}/user/id", "entry_data/ProfilePage/{0}/graphql/user/id");
            if (id_owner == null) {
                id_owner = br.getRegex("\"owner\": ?\\{\"id\": ?\"(\\d+)\"\\}").getMatch(0);
            }
            username_url = new Regex(parameter, "instagram\\.com/([^/]+)").getMatch(0);
            final boolean isPrivate = ((Boolean) get(entries, "entry_data/ProfilePage/{0}/user/is_private", "entry_data/ProfilePage/{0}/graphql/user/is_private")).booleanValue();
            if (username_url != null) {
                fp.setName(username_url);
            }
            final boolean only_grab_x_items = SubConfiguration.getConfig(this.getHost()).getBooleanProperty(jd.plugins.hoster.InstaGramCom.ONLY_GRAB_X_ITEMS, jd.plugins.hoster.InstaGramCom.defaultONLY_GRAB_X_ITEMS);
            final long maX_items = SubConfiguration.getConfig(this.getHost()).getLongProperty(jd.plugins.hoster.InstaGramCom.ONLY_GRAB_X_ITEMS_NUMBER, jd.plugins.hoster.InstaGramCom.defaultONLY_GRAB_X_ITEMS_NUMBER);
            String nextid = (String) get(entries, "entry_data/ProfilePage/{0}/user/media/page_info/end_cursor", "entry_data/ProfilePage/{0}/graphql/user/edge_owner_to_timeline_media/page_info/end_cursor");
            resource_data_list = (ArrayList) get(entries, "entry_data/ProfilePage/{0}/user/media/nodes", "entry_data/ProfilePage/{0}/graphql/user/edge_owner_to_timeline_media/edges");
            final long count = JavaScriptEngineFactory.toLong(get(entries, "entry_data/ProfilePage/{0}/user/media/count", "entry_data/ProfilePage/{0}/graphql/user/edge_owner_to_timeline_media/count"), -1);
            if (isPrivate && !logged_in && count != -1 && resource_data_list == null) {
                logger.info("Cannot parse url as profile is private");
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            if (id_owner == null) {
                // this isn't a error persay! check https://www.instagram.com/israbox/
                return decryptedLinks;
            }
            int page = 0;
            int decryptedLinksLastSize = 0;
            int decryptedLinksCurrentSize = 0;
            do {
                if (this.isAbort()) {
                    logger.info("User aborted decryption");
                    return decryptedLinks;
                } else if (only_grab_x_items && decryptedLinks.size() >= maX_items) {
                    logger.info("Number of items selected in plugin setting has been crawled --> Done");
                    break;
                }
                if (page > 0) {
                    final Browser br = this.br.cloneBrowser();
                    /* Access next page - 403 error may happen once for logged in users - reason unknown - will work fine on 2nd request! */
                    if (this.isAbort()) {
                        logger.info("User aborted decryption");
                        return decryptedLinks;
                    }
                    // prepBRAjax(br, username_url, maxid);
                    br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                    br.getHeaders().put("Accept", "*/*");
                    final Map<String, Object> vars = new HashMap<String, Object>();
                    vars.put("id", id_owner);
                    vars.put("first", 12);
                    vars.put("after", nextid);
                    if (queryHash == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    final String jsonString = JSonStorage.toString(vars).replaceAll("[\r\n]+", "").replaceAll("\\s+", "");
                    getPage(param, br, "/graphql/query/?query_hash=" + queryHash + "&variables=" + URLEncoder.encode(jsonString, "UTF-8"), rhxGis, jsonString);
                    final int responsecode = br.getHttpConnection().getResponseCode();
                    if (responsecode == 404) {
                        logger.warning("Error occurred: 404");
                        return decryptedLinks;
                    }
                    /* Stop on too many 403s as 403 is not a rate limit issue! */
                    if (responsecode == 403 || responsecode == 429) {
                        logger.warning("Failed to bypass rate-limit!");
                        return decryptedLinks;
                    } else if (responsecode == 439) {
                        logger.info("Seems like user is using an unverified account - cannot grab more items");
                        break;
                    }
                    entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(br.toString());
                    resource_data_list = (ArrayList) JavaScriptEngineFactory.walkJson(entries, "data/user/edge_owner_to_timeline_media/edges");
                    nextid = (String) JavaScriptEngineFactory.walkJson(entries, "data/user/edge_owner_to_timeline_media/page_info/end_cursor");
                }
                if (resource_data_list == null || resource_data_list.size() == 0) {
                    logger.info("Found no new links on page " + page + " --> Stopping decryption");
                    break;
                }
                decryptedLinksLastSize = decryptedLinks.size();
                for (final Object o : resource_data_list) {
                    final LinkedHashMap<String, Object> result = (LinkedHashMap<String, Object>) o;
                    // pages > 0, have a additional nodes entry
                    if (result.size() == 1 && result.containsKey("node")) {
                        decryptAlbum((LinkedHashMap<String, Object>) result.get("node"));
                    } else {
                        decryptAlbum(result);
                    }
                }
                decryptedLinksCurrentSize = decryptedLinks.size();
                page++;
            } while (nextid != null && decryptedLinksCurrentSize > decryptedLinksLastSize && decryptedLinksCurrentSize < count);
            if (decryptedLinks.size() == 0) {
                System.out.println("WTF");
            }
            return decryptedLinks;
        }
    }

    private void decryptAlbum(LinkedHashMap<String, Object> entries) {
        long date = JavaScriptEngineFactory.toLong(entries.get("date"), 0);
        if (date == 0) {
            date = JavaScriptEngineFactory.toLong(entries.get("taken_at_timestamp"), 0);
        }
        // is this id? // final String linkid_main = (String) entries.get("id");
        final String typename = (String) entries.get("__typename");
        String linkid_main = (String) entries.get("code");
        // page > 0, now called 'shortcode'
        if (linkid_main == null) {
            linkid_main = (String) entries.get("shortcode");
        }
        String description = (String) entries.get("caption");
        if (description == null) {
            try {
                final Map<String, Object> edge_media_to_caption = ((Map<String, Object>) entries.get("edge_media_to_caption"));
                final List<Map<String, Object>> edges = (List<Map<String, Object>>) edge_media_to_caption.get("edges");
                if (edges.size() > 0) {
                    final Map<String, Object> node = (Map<String, Object>) edges.get(0).get("node");
                    description = (String) node.get("text");
                }
            } catch (final Throwable e) {
                logger.log(e);
            }
        }
        final ArrayList<Object> resource_data_list = (ArrayList) JavaScriptEngineFactory.walkJson(entries, "edge_sidecar_to_children/edges");
        if (StringUtils.equalsIgnoreCase("GraphSidecar", typename) && !this.parameter.matches(TYPE_GALLERY) && (resource_data_list == null || resource_data_list.size() > 1)) {
            final DownloadLink dl = this.createDownloadlink(createSingle_P_url(linkid_main));
            this.decryptedLinks.add(dl);
            distribute(dl);
        } else if (StringUtils.equalsIgnoreCase("GraphImage", typename) && (resource_data_list == null || resource_data_list.size() == 0)) {
            /* Single image */
            decryptSingleImage(entries, linkid_main, date, description, null);
        } else if (StringUtils.equalsIgnoreCase("GraphVideo", typename) && (resource_data_list == null || resource_data_list.size() == 0)) {
            /* Single video */
            decryptSingleImage(entries, linkid_main, date, description, null);
        } else if (typename != null && typename.matches("Graph[A-Z][a-zA-Z0-9]+") && resource_data_list == null && !this.parameter.matches(TYPE_GALLERY)) {
            /*
             * 2017-05-09: User has added a 'User' URL and in this case a single post contains multiple images (=album) but at this stage
             * the json does not contain the other images --> This has to go back into the decrypter and get crawled as a single item.
             */
            final DownloadLink dl = this.createDownloadlink(createSingle_P_url(linkid_main));
            this.decryptedLinks.add(dl);
            distribute(dl);
        } else if (resource_data_list != null && resource_data_list.size() > 0) {
            final int padLength = getPadLength(resource_data_list.size());
            int counter = 0;
            /* Album */
            for (final Object pictureo : resource_data_list) {
                counter++;
                final String orderid_formatted = String.format(Locale.US, "%0" + padLength + "d", counter);
                entries = (LinkedHashMap<String, Object>) pictureo;
                entries = (LinkedHashMap<String, Object>) entries.get("node");
                decryptSingleImage(entries, linkid_main, date, description, orderid_formatted);
            }
        } else {
            /* Single image */
            decryptSingleImage(entries, linkid_main, date, description, null);
        }
    }

    private void decryptSingleImage(LinkedHashMap<String, Object> entries, String linkid_main, final long date, final String description, final String orderid) {
        String server_filename = null;
        final String shortcode = (String) entries.get("shortcode");
        if (linkid_main == null && shortcode != null) {
            // link uid, with /p/ its shortcode
            linkid_main = shortcode;
        }
        final boolean isVideo = ((Boolean) entries.get("is_video")).booleanValue();
        String dllink;
        if (isVideo) {
            dllink = (String) entries.get("video_url");
        } else {
            dllink = (String) entries.get("display_src");
            if (dllink == null || !dllink.startsWith("http")) {
                dllink = (String) entries.get("display_url");
            }
            if (dllink == null || !dllink.startsWith("http")) {
                dllink = (String) entries.get("thumbnail_src");
            }
            /*
             * 2017-04-28: By removing the resolution inside the URL, we can download the original image - usually, resolution will be
             * higher than before then but it can also get smaller - which is okay as it is the original content.
             */
            // final String resolution_inside_url = new Regex(dllink, "(/s\\d+x\\d+/)").getMatch(0);
            // if (resolution_inside_url != null) {
            // dllink = dllink.replace(resolution_inside_url, "/"); // Invalid URL signature 2018-01-17
            // } Moved to hoster plugin
        }
        if (!StringUtils.isEmpty(dllink)) {
            try {
                server_filename = getFileNameFromURL(new URL(dllink));
            } catch (final Throwable e) {
            }
        }
        String filename;
        final String ext;
        if (isVideo) {
            ext = ".mp4";
        } else {
            ext = ".jpg";
        }
        if (prefer_server_filename && server_filename != null) {
            server_filename = jd.plugins.hoster.InstaGramCom.fixServerFilename(server_filename, ext);
            filename = server_filename;
        } else {
            if (StringUtils.isNotEmpty(username_url)) {
                filename = username_url + " - " + linkid_main;
            } else {
                filename = linkid_main;
            }
            if (!StringUtils.isEmpty(shortcode) && !shortcode.equals(linkid_main)) {
                filename += "_" + shortcode;
            }
            filename += ext;
        }
        String hostplugin_url = "instagrammdecrypted://" + linkid_main;
        if (!StringUtils.isEmpty(shortcode)) {
            // hostplugin_url += "/" + shortcode; // Refresh directurl will fail
        }
        final DownloadLink dl = this.createDownloadlink(hostplugin_url);
        final String linkid;
        if (dllink != null) {
            /* 2017-05-24: Prefer this method over the ID as it is more reliable. */
            linkid = new Regex(dllink, "https?://[^/]+/(.+)").getMatch(0);
        } else {
            linkid = linkid_main + shortcode != null ? shortcode : "";
        }
        String content_url = createSingle_P_url(linkid_main);
        if (isPrivate) {
            /*
             * Without account, private urls look exactly the same as offline urls --> Save private status for better host plugin
             * errorhandling.
             */
            content_url += "?private_url=true";
            dl.setProperty("private_url", true);
        }
        dl.setContentUrl(content_url);
        dl.setLinkID(linkid);
        if (fp != null && !"Various".equals(fp.getName())) {
            fp.add(dl);
        }
        dl.setAvailable(true);
        dl.setProperty("decypter_filename", filename);
        dl.setFinalFileName(filename);
        if (date > 0) {
            jd.plugins.hoster.InstaGramCom.setReleaseDate(dl, date);
        }
        if (!StringUtils.isEmpty(shortcode)) {
            dl.setProperty("shortcode", shortcode);
        }
        if (!StringUtils.isEmpty(dllink)) {
            dl.setProperty("directurl", dllink);
        }
        if (!StringUtils.isEmpty(description)) {
            dl.setComment(description);
        }
        if (!StringUtils.isEmpty(orderid)) {
            /* For custom packagizer filenames */
            dl.setProperty("orderid", orderid);
        }
        decryptedLinks.add(dl);
        distribute(dl);
    }

    private String createSingle_P_url(final String p_id) {
        return String.format("https://www.instagram.com/p/%s", p_id);
    }

    private void prepBRAjax(final Browser br, final String username_url, final String maxid) {
        final String csrftoken = br.getCookie("instagram.com", "csrftoken");
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.getHeaders().put("X-Instagram-AJAX", "1");
        br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        if (csrftoken != null) {
            br.getHeaders().put("X-CSRFToken", csrftoken);
        }
        if (maxid != null) {
            br.getHeaders().put("Referer", "https://www.instagram.com/" + username_url + "/?max_id=" + maxid);
        }
        br.setCookie(this.getHost(), "ig_vw", "1680");
    }

    @Override
    public int getMaxConcurrentProcessingInstances() {
        return 4;
    }

    private final int getPadLength(final int size) {
        if (size < 10) {
            return 1;
        } else if (size < 100) {
            return 2;
        } else if (size < 1000) {
            return 3;
        } else if (size < 10000) {
            return 4;
        } else if (size < 100000) {
            return 5;
        } else if (size < 1000000) {
            return 6;
        } else if (size < 10000000) {
            return 7;
        } else {
            return 8;
        }
    }
}
