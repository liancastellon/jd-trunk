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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import jd.PluginWrapper;
import jd.config.Property;
import jd.controlling.ProgressController;
import jd.gui.UserIO;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "flickr.com" }, urls = { "http://(www\\.)?flickr\\.com/photos/[^<>\"/]+(/galleries)?/(page\\d+|sets/\\d+)" }, flags = { 0 })
public class FlickrCom extends PluginForDecrypt {

    public FlickrCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final Object  LOCK         = new Object();
    private static final String  MAINPAGE     = "http://flickr.com/";
    private static final Integer MAXCOOKIEUSE = 500;

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        ArrayList<Integer> pages = new ArrayList<Integer>();
        ArrayList<String> addLinks = new ArrayList<String>();
        pages.add(1);
        br.setCookiesExclusive(true);
        String parameter = param.toString();
        br.setFollowRedirects(true);
        br.setCookie(MAINPAGE, "localization", "en-us%3Bde%3Bde");
        br.getPage(parameter);
        if (br.containsHTML("Page Not Found<")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        /** Login is not always needed but we force it to get all pictures */
        getUserLogin();
        br.getPage(parameter);
        String fpName = br.getRegex("<title>Flickr: ([^<>\"/]+)</title>").getMatch(0);
        if (fpName == null) fpName = br.getRegex("\"search_default\":\"Search ([^<>\"/]+)\"").getMatch(0);
        /**
         * Handling for albums/sets Only decrypt all pages if user did NOT add a
         * direct page link
         * */
        if (!parameter.contains("/page")) {
            final String[] picpages = br.getRegex("data\\-track=\"page\\-(\\d+)\"").getColumn(0);
            if (picpages != null && picpages.length != 0) {
                for (String picpage : picpages)
                    pages.add(Integer.parseInt(picpage));
            }
        }
        final int lastPage = pages.get(pages.size() - 1);
        for (int i = 1; i <= lastPage; i++) {
            if (i != 1) br.getPage(parameter + "/page" + i);
            final String[] regexes = { "data\\-track=\"photo\\-click\" href=\"(/photos/[^<>\"\\'/]+/\\d+)" };
            for (String regex : regexes) {
                String[] links = br.getRegex(regex).getColumn(0);
                if (links != null && links.length != 0) {
                    for (String singleLink : links) {
                        if (!addLinks.contains(singleLink)) addLinks.add(singleLink);
                    }
                }
            }
        }
        if (addLinks == null || addLinks.size() == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        for (String aLink : addLinks) {
            DownloadLink dl = createDownloadlink("http://www.flickr.com" + aLink);
            dl.setAvailable(true);
            decryptedLinks.add(dl);
        }
        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    @SuppressWarnings("unchecked")
    private boolean getUserLogin() throws Exception {
        br.setFollowRedirects(true);
        String username = null;
        String password = null;
        synchronized (LOCK) {
            int loginCounter = this.getPluginConfig().getIntegerProperty("logincounter");
            // Only login every x th time to prevent getting banned | else just
            // set cookies (re-use them)
            if (loginCounter > MAXCOOKIEUSE || loginCounter == -1) {
                username = this.getPluginConfig().getStringProperty("user", null);
                password = this.getPluginConfig().getStringProperty("pass", null);
                for (int i = 0; i < 3; i++) {
                    if (username == null || password == null) {
                        username = UserIO.getInstance().requestInputDialog("Enter Loginname for " + this.getHost() + " :");
                        if (username == null) return false;
                        password = UserIO.getInstance().requestInputDialog("Enter password for " + this.getHost() + " :");
                        if (password == null) return false;
                    }
                    if (!loginSite(username, password)) {
                        username = null;
                        password = null;
                        continue;
                    } else {
                        if (loginCounter > MAXCOOKIEUSE) {
                            loginCounter = 0;
                        } else {
                            loginCounter++;
                        }
                        this.getPluginConfig().setProperty("user", username);
                        this.getPluginConfig().setProperty("pass", password);
                        this.getPluginConfig().setProperty("logincounter", loginCounter);
                        final HashMap<String, String> cookies = new HashMap<String, String>();
                        final Cookies add = this.br.getCookies(this.getHost());
                        for (final Cookie c : add.getCookies()) {
                            cookies.put(c.getKey(), c.getValue());
                        }
                        this.getPluginConfig().setProperty("cookies", cookies);
                        this.getPluginConfig().save();
                        return true;
                    }

                }
            } else {
                final Object ret = this.getPluginConfig().getProperty("cookies", null);
                if (ret != null && ret instanceof HashMap<?, ?>) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    for (Map.Entry<String, String> entry : cookies.entrySet()) {
                        this.br.setCookie(this.getHost(), entry.getKey(), entry.getValue());
                    }
                    loginCounter++;
                    this.getPluginConfig().setProperty("logincounter", loginCounter);
                    this.getPluginConfig().save();
                    return true;
                }
            }
        }
        this.getPluginConfig().setProperty("user", Property.NULL);
        this.getPluginConfig().setProperty("pass", Property.NULL);
        this.getPluginConfig().setProperty("logincounter", "-1");
        this.getPluginConfig().setProperty("cookies", Property.NULL);
        this.getPluginConfig().save();
        throw new DecrypterException("Login or/and password for " + this.getHost() + " is wrong!");
    }

    private boolean loginSite(String username, String password) throws Exception {
        br.clearCookies("flickr.com");
        br.clearCookies("yahoo.com");
        br.getPage("http://www.flickr.com/signin/");
        final String u = br.getRegex("type=\"hidden\" name=\"\\.u\" value=\"([^<>\"\\'/]+)\"").getMatch(0);
        final String challenge = br.getRegex("type=\"hidden\" name=\"\\.challenge\" value=\"([^<>\"\\'/]+)\"").getMatch(0);
        final String done = br.getRegex("type=\"hidden\" name=\"\\.done\" value=\"([^<>\"\\']+)\"").getMatch(0);
        final String pd = br.getRegex("type=\"hidden\" name=\"\\.pd\" value=\"([^<>\"\\'/]+)\"").getMatch(0);
        if (u == null || challenge == null || done == null || pd == null) return false;
        br.postPage("https://login.yahoo.com/config/login", ".tries=1&.src=flickrsignin&.md5=&.hash=&.js=&.last=&promo=&.intl=us&.lang=en-US&.bypass=&.partner=&.u=" + u + "&.v=0&.challenge=" + Encoding.urlEncode(challenge) + "&.yplus=&.emailCode=&pkg=&stepid=&.ev=&hasMsgr=0&.chkP=Y&.done=" + Encoding.urlEncode(done) + "&.pd=" + Encoding.urlEncode(pd) + "&.ws=1&.cp=0&pad=15&aad=15&popup=1&login=" + Encoding.urlEncode(username) + "&passwd=" + Encoding.urlEncode(password) + "&.persistent=y&.save=&passwd_raw=");
        if (br.containsHTML("\"status\" : \"error\"")) return false;
        String stepForward = br.getRegex("\"url\" : \"(https?://[^<>\"\\']+)\"").getMatch(0);
        if (stepForward == null) return false;
        br.getPage(stepForward);
        stepForward = br.getRegex("Please <a href=\"(http://(www\\.)?flickr\\.com/[^<>\"]+)\"").getMatch(0);
        if (stepForward == null) return false;
        br.getPage(stepForward);
        return true;
    }

}
