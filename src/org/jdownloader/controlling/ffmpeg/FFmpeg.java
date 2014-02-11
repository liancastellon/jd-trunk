package org.jdownloader.controlling.ffmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Locale;

import jd.plugins.PluginProgress;

import org.appwork.storage.config.JsonConfig;
import org.appwork.utils.Application;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.processes.ProcessBuilderFactory;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.GeneralSettings;

public class FFmpeg {

    private String[] execute(final int timeout, PluginProgress progess, File runin, String... cmds) throws InterruptedException, IOException {

        final ProcessBuilder pb = ProcessBuilderFactory.create(cmds);
        if (runin != null) pb.directory(runin);
        final StringBuilder sb = new StringBuilder();
        final StringBuilder sb2 = new StringBuilder();
        final Process process = pb.start();

        final Thread reader1 = new Thread("ffmpegReader") {
            public void run() {
                try {
                    readInputStreamToString(sb, process.getInputStream());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        final Thread reader2 = new Thread("ffmpegReader") {
            public void run() {
                try {
                    readInputStreamToString(sb2, process.getErrorStream());

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        reader1.start();
        reader2.start();
        if (timeout > 0) {
            final boolean[] interrupted = new boolean[] { false };
            Thread timouter = new Thread("ffmpegReaderTimeout") {
                public void run() {

                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException e) {
                        return;
                    }
                    interrupted[0] = true;
                    reader1.interrupt();
                    reader2.interrupt();

                    process.destroy();
                }
            };
            timouter.start();

            process.waitFor();
            reader1.join();
            reader2.join();
            timouter.interrupt();
            if (interrupted[0]) { throw new InterruptedException("Timeout!"); }
            return new String[] { sb.toString(), sb2.toString() };
        } else {
            process.waitFor();
            reader1.join();
            reader2.join();
            return new String[] { sb.toString(), sb2.toString() };
        }

    }

    public String readInputStreamToString(StringBuilder ret, final InputStream fis) throws UnsupportedEncodingException, IOException {
        BufferedReader f = null;
        try {
            f = new BufferedReader(new InputStreamReader(fis, "UTF8"));
            String line;

            final String sep = System.getProperty("line.separator");
            while ((line = f.readLine()) != null) {
                synchronized (ret) {

                    if (ret.length() > 0) {
                        ret.append(sep);
                    } else if (line.startsWith("\uFEFF")) {
                        /*
                         * Workaround for this bug: http://bugs.sun.com/view_bug.do?bug_id=4508058
                         * http://bugs.sun.com/view_bug.do?bug_id=6378911
                         */

                        line = line.substring(1);
                    }
                    ret.append(line);
                }
            }

            return ret.toString();
        } catch (IOException e) {

            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } finally {
            // don't close streams this might ill the process
        }
    }

    private FFmpegSetup config;
    private LogSource   logger;
    private String      path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public FFmpeg() {
        config = JsonConfig.create(FFmpegSetup.class);
        logger = LogController.getInstance().getLogger(FFmpeg.class.getName());
        path = config.getBinaryPath();
    }

    public FFmpeg(String path) {
        this();
        this.path = path;
    }

    public boolean validateBinary() {
        String fp = getFullPath();
        logger.info("Validate FFmpeg Binary: " + fp);
        if (StringUtils.isEmpty(fp)) {
            logger.info("Binary does not exist");
            return false;
        }

        if (fp.toLowerCase(Locale.ENGLISH).endsWith("ffmpeg") || fp.toLowerCase(Locale.ENGLISH).endsWith("ffmpeg.exe")) {

            // only check if the binary is ffmpeg
            for (int i = 0; i < 5; i++) {
                try {
                    logger.info("Start ");
                    long t = System.currentTimeMillis();
                    String[] result = execute(-1, null, null, fp, "-version");
                    logger.info(result[0]);
                    logger.info(result[1]);
                    logger.info("Done in" + (System.currentTimeMillis() - t));
                    boolean ret = result != null && result.length == 2 && result[0] != null && result[0].toLowerCase(Locale.ENGLISH).contains("ffmpeg");
                    if (ret) {
                        logger.info("Binary is ok: " + ret);
                        return ret;
                    }
                } catch (InterruptedException e) {
                    logger.log(e);
                    logger.info("Binary is ok(i): " + false);
                    return false;
                } catch (IOException e) {
                    logger.log(e);
                }
            }
        }
        logger.info("Binary is ok: " + false);
        return false;
    }

    private String getFullPath() {
        try {

            if (StringUtils.isEmpty(path)) return null;

            File file = new File(path);
            if (!file.isAbsolute()) {
                file = Application.getResource(path);
            }
            if (!file.exists()) return null;
            return file.getCanonicalPath();
        } catch (Exception e) {
            logger.log(e);
            return null;

        }
    }

    public boolean isAvailable() {
        return validateBinary();
    }

    public boolean muxToMp4(FFMpegProgress progress, String out, String videoIn, String audioIn) throws InterruptedException, IOException, FFMpegException {
        logger.info("Merging " + videoIn + " + " + audioIn + " = " + out);

        long lastModifiedVideo = new File(videoIn).lastModified();
        long lastModifiedAudio = new File(audioIn).lastModified();

        ArrayList<String> commandLine = fillCommand(out, videoIn, audioIn, config.getMuxToMp4Command());
        if (runCommand(progress, commandLine)) {

            try {

                if (JsonConfig.create(GeneralSettings.class).isUseOriginalLastModified()) {
                    new File(out).setLastModified(Math.max(lastModifiedAudio, lastModifiedVideo));
                }
            } catch (final Throwable e) {
                LogSource.exception(logger, e);
            }
            return true;
        }
        return false;

    }

    /**
     * @param out
     * @param videoIn
     * @param audioIn
     * @param mc
     * @return
     */
    public ArrayList<String> fillCommand(String out, String videoIn, String audioIn, String[] mc) {
        ArrayList<String> commandLine = new ArrayList<String>();
        commandLine.add(getFullPath());
        for (int i = 0; i < mc.length; i++) {
            String param = mc[i];
            param = param.replace("%video", videoIn == null ? "" : videoIn);
            param = param.replace("%audio", audioIn == null ? "" : audioIn);
            param = param.replace("%out", out);
            commandLine.add(param);
        }

        return commandLine;
    }

    public boolean generateM4a(FFMpegProgress progress, String out, String audioIn) throws IOException, InterruptedException, FFMpegException {

        long lastModifiedAudio = new File(audioIn).lastModified();

        ArrayList<String> commandLine = fillCommand(out, null, audioIn, config.getDash2M4aCommand());
        if (runCommand(progress, commandLine)) {

            try {

                if (JsonConfig.create(GeneralSettings.class).isUseOriginalLastModified()) {
                    new File(out).setLastModified(lastModifiedAudio);
                }
            } catch (final Throwable e) {
                LogSource.exception(logger, e);
            }
            return true;
        }
        return false;

    }

    public boolean runCommand(FFMpegProgress progress, ArrayList<String> commandLine) throws IOException, InterruptedException, FFMpegException {

        final ProcessBuilder pb = ProcessBuilderFactory.create(commandLine);

        final StringBuilder sb = new StringBuilder();
        final StringBuilder sb2 = new StringBuilder();
        final Process process = pb.start();

        final Thread reader1 = new Thread("ffmpegReader") {
            public void run() {
                try {
                    readInputStreamToString(sb, process.getInputStream());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        final Thread reader2 = new Thread("ffmpegReader") {
            public void run() {
                try {
                    readInputStreamToString(sb2, process.getErrorStream());

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        reader1.start();
        reader2.start();
        try {
            long start = System.currentTimeMillis();
            long lastUpdate = System.currentTimeMillis();
            long lastLength = 0;
            while (true) {
                synchronized (sb2) {

                    String duration = new Regex(sb2.toString(), "Duration\\: (.*?).?\\d*?\\, start").getMatch(0);
                    if (duration != null) {
                        long ms = formatStringToMilliseconds(duration);

                        String[] times = new Regex(sb2.toString(), "time=(.*?).?\\d*? ").getColumn(0);
                        if (times != null && times.length > 0) {
                            long msDone = formatStringToMilliseconds(times[times.length - 1]);

                            System.out.println(msDone + "/" + ms);
                            if (progress != null) progress.updateValues(msDone, ms);
                        }
                    }
                }
                try {
                    int exitCode = process.exitValue();
                    reader1.join();
                    reader2.join();

                    logger.info(sb.toString());
                    logger.info(sb2.toString());

                    boolean ret = exitCode == 0;
                    if (!ret) { throw new FFMpegException(sb.toString() + "\r\n" + sb2.toString()); }
                } catch (IllegalThreadStateException e) {
                    // still running;
                }
                if (lastLength != sb2.length()) {
                    lastUpdate = System.currentTimeMillis();
                }
                lastLength = sb2.length();

                if (System.currentTimeMillis() - lastUpdate > 60000) {
                    // 60 seconds without any ffmpeg update. interrupt
                    process.destroy();
                    throw new InterruptedException("FFMPeg does not answer");
                }
            }
        } catch (InterruptedException e) {
            process.destroy();
            logger.log(e);
            throw e;

        }
    }

    public boolean generateAac(FFMpegProgress progress, String out, String audioIn) throws InterruptedException, IOException, FFMpegException {

        long lastModifiedAudio = new File(audioIn).lastModified();

        ArrayList<String> commandLine = fillCommand(out, null, audioIn, config.getDash2AacCommand());
        if (runCommand(progress, commandLine)) {

            try {

                if (JsonConfig.create(GeneralSettings.class).isUseOriginalLastModified()) {
                    new File(out).setLastModified(lastModifiedAudio);
                }
            } catch (final Throwable e) {
                LogSource.exception(logger, e);
            }
            return true;
        }
        return false;

    }

    public static long formatStringToMilliseconds(final String text) {
        final String[] found = new Regex(text, "(\\d+):(\\d+):(\\d+)").getRow(0);
        if (found == null) { return 0; }
        int hours = Integer.parseInt(found[0]);
        int minutes = Integer.parseInt(found[1]);
        int seconds = Integer.parseInt(found[2]);

        return hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000;
    }

    public boolean demuxAAC(FFMpegProgress progress, String out, String audioIn) throws InterruptedException, IOException, FFMpegException {

        long lastModifiedAudio = new File(audioIn).lastModified();

        ArrayList<String> commandLine = fillCommand(out, null, audioIn, config.getDemux2AacCommand());
        if (runCommand(progress, commandLine)) {

            try {

                if (JsonConfig.create(GeneralSettings.class).isUseOriginalLastModified()) {
                    new File(out).setLastModified(lastModifiedAudio);
                }
            } catch (final Throwable e) {
                LogSource.exception(logger, e);
            }
            return true;
        }
        return false;
    }

    public boolean demuxMp3(FFMpegProgress progress, String out, String audioIn) throws InterruptedException, IOException, FFMpegException {
        long lastModifiedAudio = new File(audioIn).lastModified();

        ArrayList<String> commandLine = fillCommand(out, null, audioIn, config.getDemux2Mp3Command());
        if (runCommand(progress, commandLine)) {

            try {

                if (JsonConfig.create(GeneralSettings.class).isUseOriginalLastModified()) {
                    new File(out).setLastModified(lastModifiedAudio);
                }
            } catch (final Throwable e) {
                LogSource.exception(logger, e);
            }
            return true;
        }
        return false;
    }

    public boolean demuxMp4(FFMpegProgress progress, String out, String audioIn) throws InterruptedException, IOException, FFMpegException {
        long lastModifiedAudio = new File(audioIn).lastModified();

        ArrayList<String> commandLine = fillCommand(out, null, audioIn, config.getDemux2M4aCommand());
        if (runCommand(progress, commandLine)) {

            try {

                if (JsonConfig.create(GeneralSettings.class).isUseOriginalLastModified()) {
                    new File(out).setLastModified(lastModifiedAudio);
                }
            } catch (final Throwable e) {
                LogSource.exception(logger, e);
            }
            return true;
        }
        return false;
    }

}
