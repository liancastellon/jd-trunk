/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jdownloader.extensions.neembuu;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import javax.swing.JPanel;

import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.download.DownloadInterface.Chunk;
import jpfm.FormatterEvent;
import jpfm.MountListener;
import jpfm.mount.Mount;
import jpfm.mount.MountParams.ParamType;
import jpfm.mount.MountParamsBuilder;
import jpfm.mount.Mounts;
import neembuu.diskmanager.DiskManager;
import neembuu.diskmanager.DiskManagers;
import neembuu.rangearray.UnsyncRangeArrayCopy;
import neembuu.vfs.file.MonitoredHttpFile;
import neembuu.vfs.file.SeekableConnectionFile;
import neembuu.vfs.file.SeekableConnectionFileParams;
import neembuu.vfs.file.TroubleHandler;
import neembuu.vfs.progresscontrol.ThrottleFactory;
import neembuu.vfs.readmanager.ReadRequestState;
import neembuu.vfs.readmanager.impl.SeekableConnectionFileImplBuilder;
import neembuu.vfs.test.MonitoredSeekableHttpFilePanel;

import org.jdownloader.extensions.neembuu.newconnectionprovider.JD_HTTP_Download_Manager;

/**
 * 
 * @author Shashank Tulsyan
 * @author Coalado
 */
final class WatchAsYouDownloadSessionImpl implements WatchAsYouDownloadSession {
    private final SeekableConnectionFile         file;
    private final MonitoredHttpFile              httpFile;
    private final MonitoredSeekableHttpFilePanel filePanel;
    private final NBVirtualFileSystem            virtualFileSystem;
    private final DownloadSession                jdds;
    private final Object                         lock          = new Object();
    private Mount                                mount         = null;

    private volatile long                        totalDownload = 0;

    static WatchAsYouDownloadSessionImpl makeNew(DownloadSession jdds) throws Exception {
        NBVirtualFileSystem fileSystem = NBVirtualFileSystem.newInstance();

        // read the javadoc to know what this does
        TroubleHandler troubleHandler = new NBTroubleHandler(jdds);

        // JD team might like to change this to something they like.
        // this default diskmanager saves each chunk in a different file
        // for this reason, chucks are placed in a directory.
        // In the default implementation, I chose not to save in a single file
        // because that would require to also matin progress information in a
        // separete file.
        // The name of the file contains the offset from which it starts, and
        // size of the file
        // tells us how much was downloaded. Very important logs are also saved
        // along with these files.
        // They are in html format, and I would strongly suggest if an alternate
        // implementation to
        // this is provided, leave the html logs as they are. 2 log files are
        // created for every unique chunk,
        // and one for each file which logs overall working of differnt
        // chunks/regions.
        // For this reason the download folder would contain a lot of files.
        // These logs are highly essential.
        DiskManager diskManager = DiskManagers.getDefaultManager(new File(jdds.getDownloadLink().getFileOutput()).getParentFile().getAbsolutePath());

        // this is the only thing that neembuu requires JD to provide
        // all other things are handled by neembuu.
        // You will notice that the code is not much.
        // The same logic may be used to make a NewConnecitonProvider
        // for FTP and other protocols as the case maybe.
        JD_HTTP_Download_Manager newConnectionProvider = new JD_HTTP_Download_Manager(jdds);

        // all paramters below are compulsary. None of these may be left
        // unspecified.
        SeekableConnectionFile file = new SeekableConnectionFileImplBuilder().build(new SeekableConnectionFileParams.Builder().setDiskManager(diskManager).setTroubleHandler(troubleHandler).setFileName(jdds.getDownloadLink().getFinalFileName()).setFileSize(jdds.getDownloadLink().getDownloadSize()).setNewConnectionProvider(newConnectionProvider).setThrottleFactory(ThrottleFactory.General.SINGLETON).setParent(fileSystem.getVectorRootDirectory())
        // throttle is a very unique speed
        // measuring, and controlling unit. Limiting
        // download speed is crucial
        // to prevent starvation of regions which really require
        // speed, and make the system respond quickly.
        // The main work of throttle is not to limit
        // speed as much as it is to kill connections to improve
        // speed where it is actually
        // required. This makes a HUGE difference in watch as
        // you download experience of the user.
        // Making a decent throttle is one
        // of the biggest challenges. The program as such is
        // simple, but the key issue is same
        // approach doesn't work for all files.
        // For avi's you might need a different approach, for
        // mkv still another.
        // This GeneralThrottle is however pretty decent and
        // should be able to
        // handle most of the files.
                .build());

        MonitoredHttpFile httpFile = new MonitoredHttpFile(file, newConnectionProvider);

        MonitoredSeekableHttpFilePanel httpFilePanel = new MonitoredSeekableHttpFilePanel(httpFile);
        fileSystem.getVectorRootDirectory().add(httpFile);
        WatchAsYouDownloadSessionImpl sessionImpl = new WatchAsYouDownloadSessionImpl(file, httpFile, httpFilePanel, fileSystem, jdds);
        jdds.setWatchAsYouDownloadSession(sessionImpl);
        sessionImpl.mount();

        return sessionImpl;
    }

    WatchAsYouDownloadSessionImpl(SeekableConnectionFile file, MonitoredHttpFile httpFile, MonitoredSeekableHttpFilePanel filePanel, NBVirtualFileSystem virtualFileSystem, DownloadSession jdds) {
        this.file = file;
        this.httpFile = httpFile;
        this.filePanel = filePanel;
        this.virtualFileSystem = virtualFileSystem;
        this.jdds = jdds;
    }

    // @Override
    public final NBVirtualFileSystem getVirtualFileSystem() {
        return virtualFileSystem;
    }

    // @Override
    public final SeekableConnectionFile getSeekableConnectionFile() {
        return httpFile;
    }

    // @Override
    public JPanel getFilePanel() {
        return filePanel;
    }

    // @Override
    public boolean isMounted() {
        synchronized (lock) {
            if (mount == null) return false;
            return mount.isMounted();
        }
    }

    // @Override
    public File getMountLocation() {
        return mount.getMountLocation().getAsFile();
    }

    // @Override
    public void mount() throws Exception {
        synchronized (lock) {
            if (isMounted()) { throw new IllegalStateException("Already mounted"); }

            final String mountLocation = makeMountLocation();

            Mount m = Mounts.mount(new MountParamsBuilder().set(ParamType.LISTENER, new MountListener() {
                // @Override
                public void eventOccurred(FormatterEvent event) {
                    if (event.getEventType() == FormatterEvent.EVENT.SUCCESSFULLY_MOUNTED) {
                        try {
                            java.awt.Desktop.getDesktop().open(new File(mountLocation));
                        } catch (IOException ioe) {
                            jdds.getDownloadInterface().logger.log(Level.INFO, event.getMessage(), event.getException());
                        }
                    } else if (event.getEventType() == FormatterEvent.EVENT.DETACHED) {
                        NeembuuExtension.getInstance().getGUI().removeSession(jdds);
                    }
                }
            }).set(ParamType.FILE_SYSTEM, virtualFileSystem).set(ParamType.MOUNT_LOCATION, mountLocation).build());
            this.mount = m;
        }
    }

    private String makeMountLocation() throws IOException {
        File baseDir = new File(System.getProperty("user.home") + File.separator + "NeembuuWatchAsYouDownload");
        if (!baseDir.exists()) baseDir.mkdir();
        File mountLoc = new File(baseDir, jdds.getDownloadLink().getFinalFileName());
        mountLoc.deleteOnExit();

        if (mountLoc.exists()) {
            mountLoc = new File(mountLoc.toString() + Math.random());
        }
        if (!jpfm.util.PreferredMountTypeUtil.isFolderAPreferredMountLocation()) {
            mountLoc.createNewFile();
        } else {
            mountLoc.mkdir();
        }
        return mountLoc.getAbsolutePath();
    }

    // @Override
    public void unMount() throws Exception {
        synchronized (lock) {
            mount.unMount();
        }
    }

    // @Override
    public void waitForDownloadToFinish() throws Exception {
        jdds.getDownloadInterface().addChunksDownloading(1);
        Chunk ch = jdds.getDownloadInterface().new Chunk(0, 0, null, null) {

            /*
             * @Override public long getBytesLoaded() { return totalDownload; }
             * 
             * @Override public long getCurrentBytesPosition() { return
             * totalDownload; }
             */

        };
        ch.setInProgress(true);
        jdds.getDownloadInterface().getChunks().add(ch);
        jdds.getDownloadLink().getLinkStatus().addStatus(LinkStatus.DOWNLOADINTERFACE_IN_PROGRESS);

        UPDATE_LOOP: while (totalDownload < jdds.getDownloadLink().getDownloadSize() && jdds.getWatchAsYouDownloadSession().isMounted()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {

            }

            updateProgress(ch);

            if (jdds.getDownloadInterface().externalDownloadStop()) {
                break UPDATE_LOOP;
            }
        }

        jdds.getDownloadLink().getLinkStatus().removeStatus(LinkStatus.DOWNLOADINTERFACE_IN_PROGRESS);
        jdds.getDownloadLink().setDownloadInstance(null);
        jdds.getDownloadLink().getLinkStatus().setStatusText(null);
        ch.setInProgress(false);

        if (jdds.getWatchAsYouDownloadSession().isMounted()) {
            jdds.getWatchAsYouDownloadSession().unMount();
        }

        // make sure that the file is close
        // jdds.getWatchAsYouDownloadSession().getSeekableConnectionFile().close();

        if (totalDownload >= jdds.getDownloadLink().getDownloadSize()) {
            jdds.getDownloadLink().getLinkStatus().addStatus(LinkStatus.FINISHED);
            jdds.getWatchAsYouDownloadSession().getSeekableConnectionFile().getFileStorageManager().completeSession(new File(jdds.getDownloadLink().getFileOutput()), jdds.getDownloadLink().getDownloadSize());
        } else {
            jdds.getWatchAsYouDownloadSession().getSeekableConnectionFile().getFileStorageManager().close();
            throw new PluginException(LinkStatus.ERROR_DOWNLOAD_INCOMPLETE);
        }
    }

    private void updateProgress(Chunk ch) {
        UnsyncRangeArrayCopy<ReadRequestState> handlers = jdds.getWatchAsYouDownloadSession().getSeekableConnectionFile().getTotalFileReadStatistics().getReadRequestStates();
        // ReadRequestState is equivalent to a JD Chunk

        long total = 0;
        for (int i = 0; i < handlers.size(); i++) {
            total += handlers.get(i).ending() - handlers.get(i).starting() + 1;
        }

        totalDownload = total;

        jdds.getDownloadLink().setDownloadCurrent(total);
        jdds.getDownloadLink().setChunksProgress(new long[] { total });
        jdds.getDownloadInterface().addToChunksInProgress(total);
    }

}
