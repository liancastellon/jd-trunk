//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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

package jd.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.regex.Pattern;

import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledLinkModifier;
import jd.nutils.Formatter;
import jd.nutils.encoding.Encoding;
import jd.utils.JDUtilities;

import org.appwork.exceptions.WTFException;
import org.appwork.utils.Files;
import org.appwork.utils.Hash;
import org.appwork.utils.IO;
import org.appwork.utils.Regex;
import org.appwork.utils.logging.Log;

/**
 * Dies ist die Oberklasse für alle Plugins, die Containerdateien nutzen können
 * 
 * @author astaldo/JD-Team
 */

public abstract class PluginsC {

    private Pattern pattern;

    private String  name;

    private long    version;

    public PluginsC(String name, String pattern, String rev) {
        this.pattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        this.name = name;
        try {
            version = Formatter.getRevision(rev);
        } catch (Throwable e) {
            version = -1;
        }
    }

    private static final int         STATUS_NOTEXTRACTED     = 0;

    private static final int         STATUS_ERROR_EXTRACTING = 1;

    protected ArrayList<CrawledLink> cls                     = new ArrayList<CrawledLink>();

    protected String                 md5;
    protected byte[]                 k;

    private int                      status                  = STATUS_NOTEXTRACTED;

    public abstract ContainerStatus callDecryption(File file);

    // @Override
    public synchronized boolean canHandle(final String data) {
        if (data == null) { return false; }
        final String match = new Regex(data, this.getSupportedLinks()).getMatch(-1);
        return match != null && match.equalsIgnoreCase(data);
    }

    public String createContainerString(ArrayList<DownloadLink> downloadLinks) {
        return null;
    }

    public Pattern getSupportedLinks() {
        return pattern;
    }

    public String getName() {
        return name;
    }

    public long getVersion() {
        return version;
    }

    /* hide links by default */
    public boolean hideLinks() {
        return true;
    }

    /**
     * Erstellt eine Kopie des Containers im Homedir.
     * 
     * @throws IOException
     */
    private synchronized void doDecryption(final String parameter) throws IOException {
        Log.L.info("DO STEP");
        final String file = parameter;
        if (status == STATUS_ERROR_EXTRACTING) {
            Log.L.severe("Expired JD Version. Could not extract links");
            return;
        }
        if (file == null) {
            Log.L.severe("Containerfile == null");
            return;
        }
        final File f = JDUtilities.getResourceFile(file);
        if (md5 == null) {
            md5 = Hash.getMD5(f);
        }

        final String extension = Files.getExtension(f.getAbsolutePath());
        if (f.exists()) {
            final File res = JDUtilities.getResourceFile("container/" + md5 + "." + extension, true);
            if (!res.exists()) {
                IO.copyFile(f, res);
            }
            if (!res.exists()) {
                Log.L.severe("Could not copy file to homedir");
            }
            callDecryption(res);
        }
        return;
    }

    public abstract String[] encrypt(String plain);

    /**
     * Diese Methode liefert eine URL zurück, von der aus der Download gestartet
     * werden kann
     * 
     * @param downloadLink
     *            Der DownloadLink, dessen URL zurückgegeben werden soll
     * @return Die URL als String
     */
    public synchronized String extractDownloadURL(final DownloadLink downloadLink) {
        throw new WTFException("TODO: this should not happen at the moment");
    }

    /**
     * Liefert alle in der Containerdatei enthaltenen Dateien als DownloadLinks
     * zurück.
     * 
     * @param filename
     *            Die Containerdatei
     * @return Ein ArrayList mit DownloadLinks
     */
    public ArrayList<CrawledLink> getContainedDownloadlinks() {
        return cls == null ? new ArrayList<CrawledLink>() : cls;
    }

    public synchronized void initContainer(String filename, final byte[] bs) throws IOException {
        if (filename == null) return;
        final File rel = JDUtilities.getResourceFile(filename);
        final File ab = new File(filename);
        final String md;

        if (!rel.exists() && ab.exists()) {
            final String extension = Files.getExtension(filename);
            md = Hash.getMD5(ab);
            final File newFile = JDUtilities.getResourceFile("container/" + md + "." + extension, true);
            if (!newFile.exists()) {
                IO.copyFile(ab, newFile);
            }
            filename = "container/" + md + "." + extension;
        }

        if (cls == null || cls.size() == 0) {
            Log.L.info("Init Container");
            if (bs != null) k = bs;
            try {
                doDecryption(filename);
            } catch (Throwable e) {
                Log.L.severe(e.toString());
            }
        }
    }

    public ArrayList<CrawledLink> getContainerLinks(String data) {
        /*
         * we dont need memory optimization here as downloadlink, crypted link
         * itself take care of this
         */
        String[] hits = new Regex(data, getSupportedLinks()).setMemoryOptimized(false).getColumn(-1);
        ArrayList<CrawledLink> chits = null;
        if (hits != null && hits.length > 0) {
            chits = new ArrayList<CrawledLink>(hits.length);
        } else {
            chits = new ArrayList<CrawledLink>();
        }
        if (hits != null && hits.length > 0) {
            for (String hit : hits) {
                String file = hit;
                file = file.trim();
                /* cut of any unwanted chars */
                while (file.length() > 0 && file.charAt(0) == '"') {
                    file = file.substring(1);
                }
                while (file.length() > 0 && file.charAt(file.length() - 1) == '"') {
                    file = file.substring(0, file.length() - 1);
                }
                file = file.trim();

                CrawledLink cli;
                chits.add(cli = new CrawledLink(file));
                cli.setCustomCrawledLinkModifier(new CrawledLinkModifier() {
                    /*
                     * set new LinkModifier, hides the url if needed
                     */
                    public void modifyCrawledLink(CrawledLink link) {
                        if (hideLinks()) {
                            /* we hide the links */
                            DownloadLink dl = link.getDownloadLink();
                            if (dl != null) dl.setLinkType(DownloadLink.LINKTYPE_CONTAINER);
                        }
                    }
                });
                cli.setcPlugin(this);
            }
        }
        return chits;
    }

    public ArrayList<CrawledLink> decryptContainer(CrawledLink source) {
        if (source.getURL() == null) return null;
        ArrayList<CrawledLink> retLinks = null;
        boolean showException = true;
        try {
            /* extract filename from url */
            String file = new Regex(source.getURL(), "file://(.+)").getMatch(0);
            file = Encoding.urlDecode(file, false);
            if (file != null && new File(file).exists()) {
                initContainer(file, null);
                retLinks = getContainedDownloadlinks();
            } else {
                throw new Throwable("Invalid Container: " + source.getURL());
            }
        } catch (Throwable e) {
            /*
             * damn, something must have gone really really bad, lets keep the
             * log
             */

            Log.L.log(Level.SEVERE, "Exception", e);
        }
        if (retLinks == null && showException) {
            /*
             * null as return value? something must have happened, do not clear
             * log
             */
            Log.L.severe("ContainerPlugin out of date: " + this + " :" + getVersion());

        }
        return retLinks;
    }

}