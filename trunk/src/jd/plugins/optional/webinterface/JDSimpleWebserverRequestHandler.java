package jd.plugins.optional.webinterface;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import jd.config.Configuration;
import jd.config.SubConfiguration;
import jd.controlling.DistributeData;
import jd.event.ControlEvent;
import jd.gui.skins.simple.SimpleGUI;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForHost;
import jd.unrar.JUnrar;
import jd.utils.JDUtilities;
import jd.utils.Reconnecter;

public class JDSimpleWebserverRequestHandler {

    private HashMap<String, String> headers;
    private JDSimpleWebserverResponseCreator response;

    private SubConfiguration guiConfig = JDUtilities.getSubConfig(SimpleGUI.GUICONFIGNAME);
    public static final String PROPERTY_AUTOPACKAGE_LIMIT = "AUTOPACKAGE_LIMIT";

    public static final String PROPERTY_ONLINE_CHECK = "DO_ONLINE_CHECK";

    private Logger logger = JDUtilities.getLogger();

    public JDSimpleWebserverRequestHandler(HashMap<String, String> headers, JDSimpleWebserverResponseCreator response) {
        this.response = response;
        this.headers = headers;
    }

    private String removeExtension(String a) {
        if (a == null) return a;
        a = a.replaceAll("\\.part([0-9]+)", "");
        a = a.replaceAll("\\.html", "");
        a = a.replaceAll("\\.htm", "");
        int i = a.lastIndexOf(".");
        String ret;
        if (i <= 1 || (a.length() - i) > 5) {
            ret = a.toLowerCase().trim();
        } else {
            ret = a.substring(0, i).toLowerCase().trim();
        }

        if (a.equals(ret)) return ret;
        return (ret);

    }

    private int comparepackages(String a, String b) {

        int c = 0;
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) == b.charAt(i)) c++;
        }

        if (Math.min(a.length(), b.length()) == 0) return 0;
        return (c * 100) / (b.length());
    }

    private String getSimString(String a, String b) {

        String ret = "";
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) == b.charAt(i)) {
                ret += a.charAt(i);
            } else {
                // return ret;
            }
        }
        return ret;
    }

    private void attachLinkTopackage(DownloadLink link) {
        synchronized (JDWebinterface.Link_Adder_Packages) {
            int bestSim = 0;
            int bestIndex = -1;
            for (int i = 0; i < JDWebinterface.Link_Adder_Packages.size(); i++) {

                int sim = comparepackages(JDWebinterface.Link_Adder_Packages.get(i).getName(), removeExtension(link.getName()));
                if (sim > bestSim) {
                    bestSim = sim;
                    bestIndex = i;
                }
            }
            if (bestSim < guiConfig.getIntegerProperty(PROPERTY_AUTOPACKAGE_LIMIT, 98)) {

                FilePackage fp = new FilePackage();
                fp.setName(removeExtension(link.getName()));
                fp.add(link);
                JDWebinterface.Link_Adder_Packages.add(fp);
            } else {
                String newpackageName = getSimString(JDWebinterface.Link_Adder_Packages.get(bestIndex).getName(), removeExtension(link.getName()));
                JDWebinterface.Link_Adder_Packages.get(bestIndex).setName(newpackageName);
                JDWebinterface.Link_Adder_Packages.get(bestIndex).add(link);

            }
        }
    }

    @SuppressWarnings("static-access")
    public void handle() {

        String request = headers.get(null);

        String[] requ = request.split(" ");

        String cPath = requ[1];
        String path, querry;
        path = cPath.substring(1);
        String[] params;
        HashMap<String, String> requestParameter = new HashMap<String, String>();

        /* bekanntgebung der mehrfach belegbaren parameter */
        requestParameter.put("package_all_downloads_counter", "0");
        requestParameter.put("package_single_download_counter", "0");
        requestParameter.put("package_all_add_counter", "0");
        requestParameter.put("package_single_add_counter", "0");

        if (cPath.indexOf("?") >= 0) {
            querry = cPath.substring(cPath.indexOf("?") + 1);
            path = cPath.substring(1, cPath.indexOf("?"));
            params = querry.split("\\&");

            for (String entry : params) {
                entry = entry.trim();
                int index = entry.indexOf("=");
                String key = entry;

                String value = null;
                if (index >= 0) {
                    key = entry.substring(0, index);
                    value = entry.substring(index + 1);
                }

                if (requestParameter.containsKey(key) || requestParameter.containsKey(key + "_counter")) {
                    /*
                     * keys mit _counter können mehrfach belegt werden, müssen
                     * vorher aber bekannt gegeben werden
                     */
                    if (requestParameter.containsKey(key + "_counter")) {
                        Integer keycounter = 0;
                        keycounter = JDUtilities.filterInt(requestParameter.get(key + "_counter"));
                        keycounter++;
                        requestParameter.put(key + "_counter", keycounter.toString());
                        requestParameter.put(key + "_" + keycounter.toString(), value);
                    }

                } else
                    requestParameter.put(key, value);
            }
        }

        String url = path.replaceAll("\\.\\.", "");

        /* parsen der paramter */
        if (requestParameter.containsKey("do")) {
            if (requestParameter.get("do").compareToIgnoreCase("submit") == 0) {
                if (requestParameter.containsKey("speed")) {
                    int setspeed = JDUtilities.filterInt(requestParameter.get("speed"));
                    if (setspeed < 0) setspeed = 0;
                    JDUtilities.getSubConfig("DOWNLOAD").setProperty(Configuration.PARAM_DOWNLOAD_MAX_SPEED, setspeed);
                }

                if (requestParameter.containsKey("maxdls")) {
                    int maxdls = JDUtilities.filterInt(requestParameter.get("maxdls"));
                    if (maxdls < 1) maxdls = 1;
                    JDUtilities.getSubConfig("DOWNLOAD").setProperty(Configuration.PARAM_DOWNLOAD_MAX_SIMULTAN, maxdls);
                }

                if (requestParameter.containsKey("autoreconnect")) {
                    JDUtilities.getConfiguration().setProperty(Configuration.PARAM_DISABLE_RECONNECT, false);
                } else
                    JDUtilities.getConfiguration().setProperty(Configuration.PARAM_DISABLE_RECONNECT, true);

                if (requestParameter.containsKey("package_single_add_counter")) {
                    synchronized (JDWebinterface.Link_Adder_Packages) {
                        /* aktionen in der adder liste ausführen */
                        Integer download_id = 0;
                        Integer package_id = 0;
                        String[] ids;
                        int counter_max = JDUtilities.filterInt(requestParameter.get("package_single_add_counter"));
                        int counter_index = 0;
                        DownloadLink link;
                        int index;
                        Vector<DownloadLink> links = new Vector<DownloadLink>();
                        for (counter_index = 1; counter_index <= counter_max; counter_index++) {
                            if (requestParameter.containsKey("package_single_add_" + counter_index)) {
                                ids = requestParameter.get("package_single_add_" + counter_index).toString().split("[+]", 2);
                                package_id = JDUtilities.filterInt(ids[0].toString());
                                download_id = JDUtilities.filterInt(ids[1].toString());
                                links.add(JDWebinterface.Link_Adder_Packages.get(package_id).get(download_id));
                            }
                        }
                        if (requestParameter.containsKey("selected_dowhat_link_adder")) {
                            String dowhat = requestParameter.get("selected_dowhat_link_adder");
                            /* packages-namen des link-adders aktuell halten */
                            synchronized (JDWebinterface.Link_Adder_Packages) {
                                for (int i = 0; i < JDWebinterface.Link_Adder_Packages.size(); i++) {
                                    if (requestParameter.containsKey("adder_package_name_" + i)) {
                                        JDWebinterface.Link_Adder_Packages.get(i).setName(JDUtilities.htmlDecode(requestParameter.get("adder_package_name_" + i).toString()));
                                    }
                                }
                            }
                            if (dowhat.compareToIgnoreCase("remove") == 0) {
                                /* entfernen */
                                for (Iterator<DownloadLink> it = links.iterator(); it.hasNext();) {
                                    link = it.next();
                                    link.getFilePackage().remove(link);
                                }
                            } else if (dowhat.compareToIgnoreCase("remove+offline") == 0) {
                                /* entfernen(offline) */
                                for (int i = 0; i < JDWebinterface.Link_Adder_Packages.size(); i++) {
                                    for (int ii = 0; ii < JDWebinterface.Link_Adder_Packages.get(i).size(); ii++) {
                                        links.add(JDWebinterface.Link_Adder_Packages.get(i).get(ii));
                                    }
                                }
                                for (Iterator<DownloadLink> it = links.iterator(); it.hasNext();) {
                                    link = it.next();
                                    if (link.isAvailabilityChecked() == true && link.isAvailable() == false) link.getFilePackage().remove(link);
                                }
                            } else if (dowhat.compareToIgnoreCase("add") == 0) {
                                /* link adden */
                                for (Iterator<DownloadLink> it = links.iterator(); it.hasNext();) {
                                    link = it.next();
                                    FilePackage fp = null;

                                    for (int i = 0; i < JDUtilities.getController().getPackages().size(); i++) {
                                        /*
                                         * files mit selben packages namen
                                         * sollen auch ins gleiche package?!
                                         */
                                        if (link.getFilePackage().getName().compareToIgnoreCase(JDUtilities.getController().getPackages().get(i).getName()) == 0) {
                                            fp = JDUtilities.getController().getPackages().get(i);
                                            /*
                                             * package bereits im controller
                                             * gefunden
                                             */
                                        }
                                    }
                                    if (fp == null) {
                                        /* neues package erzeugen */
                                        fp = new FilePackage();
                                        fp.setName(link.getFilePackage().getName());
                                        /* use packagename as subfolder */
                                        if (JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_USE_PACKETNAME_AS_SUBFOLDER, false)) {
                                            File file = new File(new File(fp.getDownloadDirectory()), fp.getName());
                                            if (!file.exists()) {
                                                file.mkdirs();
                                            }
                                            if (file.exists()) {
                                                fp.setDownloadDirectory(file.getAbsolutePath());
                                            } else {
                                                fp.setDownloadDirectory(fp.getDownloadDirectory());
                                            }
                                        }
                                    }
                                    fp.add(link);
                                    link.setFilePackage(fp);
                                    JDUtilities.getController().addLink(link);
                                }
                                JDUtilities.getController().fireControlEvent(new ControlEvent(this, ControlEvent.CONTROL_LINKLIST_STRUCTURE_CHANGED, null));

                            }
                            /* leere packages aus der add liste entfernen */
                            /*
                             * von oben nach unten, damit keine fehler
                             * entstehen, falls mittendrin was gelöscht wird
                             */
                            for (index = JDWebinterface.Link_Adder_Packages.size() - 1; index >= 0; index--) {
                                if (JDWebinterface.Link_Adder_Packages.get(index).size() == 0) JDWebinterface.Link_Adder_Packages.remove(index);
                            }
                        }

                    }
                }

                if (requestParameter.containsKey("package_single_download_counter")) {
                    /* aktionen in der download-liste ausführen */
                    Integer download_id = 0;
                    Integer package_id = 0;
                    String[] ids;
                    int counter_max = JDUtilities.filterInt(requestParameter.get("package_single_download_counter"));
                    int counter_index = 0;
                    DownloadLink link;
                    Vector<DownloadLink> links = new Vector<DownloadLink>();
                    for (counter_index = 1; counter_index <= counter_max; counter_index++) {
                        if (requestParameter.containsKey("package_single_download_" + counter_index)) {
                            ids = requestParameter.get("package_single_download_" + counter_index).toString().split("[+]", 2);
                            package_id = JDUtilities.filterInt(ids[0].toString());
                            download_id = JDUtilities.filterInt(ids[1].toString());

                            links.add(JDUtilities.getController().getPackages().get(package_id).getDownloadLinks().get(download_id));
                        }
                    }

                    if (requestParameter.containsKey("selected_dowhat_index")) {
                        String dowhat = requestParameter.get("selected_dowhat_index");
                        if (dowhat.compareToIgnoreCase("activate") == 0) {
                            /* aktivieren */
                            for (Iterator<DownloadLink> it = links.iterator(); it.hasNext();) {
                                link = it.next();
                                link.setEnabled(true);
                            }
                            JDUtilities.getController().fireControlEvent(new ControlEvent(this, ControlEvent.CONTROL_ALL_DOWNLOADLINKS_DATA_CHANGED, this));
                        }
                        if (dowhat.compareToIgnoreCase("deactivate") == 0) {
                            /* deaktivieren */
                            for (Iterator<DownloadLink> it = links.iterator(); it.hasNext();) {
                                link = it.next();
                                link.setEnabled(false);
                            }
                            JDUtilities.getController().fireControlEvent(new ControlEvent(this, ControlEvent.CONTROL_ALL_DOWNLOADLINKS_DATA_CHANGED, this));
                        }
                        if (dowhat.compareToIgnoreCase("reset") == 0) {
                            /*
                             * reset
                             */
                            for (Iterator<DownloadLink> it = links.iterator(); it.hasNext();) {
                                link = it.next();
                                link.setStatus(DownloadLink.STATUS_TODO);
                                link.setStatusText("");
                                link.reset();
                            }
                            JDUtilities.getController().fireControlEvent(new ControlEvent(this, ControlEvent.CONTROL_ALL_DOWNLOADLINKS_DATA_CHANGED, this));
                        }
                        if (dowhat.compareToIgnoreCase("remove") == 0) {
                            /*
                             * entfernen
                             */
                            JDUtilities.getController().removeDownloadLinks(links);
                            JDUtilities.getController().fireControlEvent(new ControlEvent(this, ControlEvent.CONTROL_LINKLIST_STRUCTURE_CHANGED, this));
                        }
                        if (dowhat.compareToIgnoreCase("abort") == 0) {
                            /*
                             * abbrechen
                             */
                            for (Iterator<DownloadLink> it = links.iterator(); it.hasNext();) {
                                link = it.next();
                                link.setAborted(true);
                            }
                            JDUtilities.getController().fireControlEvent(new ControlEvent(this, ControlEvent.CONTROL_ALL_DOWNLOADLINKS_DATA_CHANGED, this));
                        }

                    }

                }

            } else if (requestParameter.get("do").compareToIgnoreCase("reconnect") == 0) {
                class JDReconnect implements Runnable {
                    /*
                     * zeitverzögertes neustarten
                     */
                    JDReconnect() {
                        new Thread(this).start();
                    }

                    public void run() {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        boolean tmp = JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_DISABLE_RECONNECT, true);
                        JDUtilities.getConfiguration().setProperty(Configuration.PARAM_DISABLE_RECONNECT, false);
                        if (JDUtilities.getController().getRunningDownloadNum() > 0) {
                            JDUtilities.getController().stopDownloads();
                        }
                        if (Reconnecter.waitForNewIP(1)) {
                            logger.info("Reconnect erfolgreich");
                        } else {
                            logger.info("Reconnect fehlgeschlagen");
                        }
                        JDUtilities.getConfiguration().setProperty(Configuration.PARAM_DISABLE_RECONNECT, tmp);
                    }
                }
                @SuppressWarnings("unused")
                JDReconnect jdrc = new JDReconnect();

            } else if (requestParameter.get("do").compareToIgnoreCase("close") == 0) {
                class JDClose implements Runnable { /* zeitverzögertes beenden */
                    JDClose() {
                        new Thread(this).start();
                    }

                    public void run() {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        JDUtilities.getController().exit();
                    }
                }
                @SuppressWarnings("unused")
                JDClose jdc = new JDClose();

            } else if (requestParameter.get("do").compareToIgnoreCase("start") == 0) {
                JDUtilities.getController().startDownloads();
            } else if (requestParameter.get("do").compareToIgnoreCase("stop") == 0) {
                JDUtilities.getController().stopDownloads();
            } else if (requestParameter.get("do").compareToIgnoreCase("restart") == 0) {
                class JDRestart implements Runnable {
                    /*
                     * zeitverzögertes neustarten
                     */
                    JDRestart() {
                        new Thread(this).start();
                    }

                    public void run() {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        JDUtilities.restartJD();
                    }
                }
                @SuppressWarnings("unused")
                JDRestart jdrs = new JDRestart();

            } else if (requestParameter.get("do").compareToIgnoreCase("add") == 0) {
                if (requestParameter.containsKey("addlinks")) {
                    /*
                     * TODO: offline links markieren, dupe check
                     */
                    String AddLinks = JDUtilities.htmlDecode(requestParameter.get("addlinks"));
                    Vector<DownloadLink> waitingLinkList = new DistributeData(AddLinks).findLinks();

                    DownloadLink link;
                    DownloadLink next;
                    while (waitingLinkList.size() > 0) {
                        link = waitingLinkList.remove(0);
                        if (!guiConfig.getBooleanProperty(PROPERTY_ONLINE_CHECK, false)) {
                            attachLinkTopackage(link);
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                            }
                        } else {
                            if (!link.isAvailabilityChecked()) {
                                Iterator<DownloadLink> it = waitingLinkList.iterator();
                                Vector<String> links = new Vector<String>();
                                Vector<DownloadLink> dlLinks = new Vector<DownloadLink>();
                                links.add(link.getDownloadURL());
                                dlLinks.add(link);
                                while (it.hasNext()) {
                                    next = it.next();
                                    if (next.getPlugin().getClass() == link.getPlugin().getClass()) {
                                        dlLinks.add(next);
                                        links.add(next.getDownloadURL());
                                    }
                                }
                                if (links.size() > 1) {
                                    boolean[] ret = ((PluginForHost) link.getPlugin()).checkLinks(links.toArray(new String[] {}));
                                    if (ret != null) {
                                        for (int ii = 0; ii < links.size(); ii++) {
                                            dlLinks.get(ii).setAvailable(ret[ii]);
                                        }
                                    }
                                }
                            }
                            if (link.isAvailable() || ((PluginForHost) link.getPlugin()).isListOffline()) {

                                attachLinkTopackage(link);

                            }
                        }
                    }
                }
            }

        }
        /* passwortliste verändern */
        if (requestParameter.containsKey("passwd")) {
            if (requestParameter.get("passwd").compareToIgnoreCase("save") == 0) {
                if (requestParameter.containsKey("password_list")) {
                    String password_list = JDUtilities.htmlDecode(requestParameter.get("password_list"));
                    JUnrar unrar = new JUnrar(false);
                    unrar.editPasswordlist(JDUtilities.splitByNewline(password_list));
                }
            }
        }

        File fileToRead = JDUtilities.getResourceFile("plugins/webinterface/" + url);
        if (!fileToRead.isFile()) {
            /*
             * default soll zur index.tmpl gehen, fall keine angabe gemacht
             * wurde
             */
            String tempurl = url + "index.tmpl";
            File fileToRead2 = JDUtilities.getResourceFile("plugins/webinterface/" + tempurl);
            if (fileToRead2.isFile()) {
                url = tempurl;
                fileToRead = JDUtilities.getResourceFile("plugins/webinterface/" + url);
            }

        }

        if (!fileToRead.exists()) {
            response.setNotFound(url);
        } else {
            if (url.endsWith(".tmpl")) {
                JDSimpleWebserverTemplateFileRequestHandler filerequest;
                filerequest = new JDSimpleWebserverTemplateFileRequestHandler(this.response);
                filerequest.handleRequest(url, requestParameter);
            } else {
                JDSimpleWebserverStaticFileRequestHandler filerequest;
                filerequest = new JDSimpleWebserverStaticFileRequestHandler(this.response);
                filerequest.handleRequest(url, requestParameter);
            }

        }

    }
}
