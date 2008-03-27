//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program  is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSSee the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://wnu.org/licenses/>.

package jd.plugins.download;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.logging.Logger;

import jd.config.Configuration;
import jd.plugins.DownloadLink;
import jd.plugins.HTTPConnection;
import jd.plugins.PluginForHost;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;

public class ChunkFileDownload extends DownloadInterface {
    public ChunkFileDownload(PluginForHost plugin, DownloadLink downloadLink, HTTPConnection urlConnection) {
        super(plugin, downloadLink, urlConnection);

    }

    protected long writeTimer = System.currentTimeMillis();

    protected long writeCount = 0;

    protected long hdWritesPerSecond;
    protected FileChannel[]     channels;

    protected File[]                    partFiles;
    private long   debugtimer;

    protected void addBytes(Chunk chunk) {
        try {
            if (speedDebug) {
                if ((System.currentTimeMillis() - writeTimer) >= 1000) {
                    this.hdWritesPerSecond = writeCount / 1;
                    writeTimer = System.currentTimeMillis();
                    writeCount = 0;
                    logger.info("HD ZUgriffe: " + hdWritesPerSecond);
                }
                this.writeCount++;
            }

            channels[chunk.getID()].write(chunk.buffer);
            logger.info(chunk.currentBytePosition + " <<size " + chunk.buffer.limit() + " of " + chunk.getID() + " - " + partFiles[chunk.getID()].length());
            if (maxBytes > 0 && getChunkNum() == 1 && this.bytesLoaded >= maxBytes) {
                error(ERROR_NIBBLE_LIMIT_REACHED);
            }
            // if (chunk.getID() >= 0)
            // downloadLink.getChunksProgress()[chunk.getID()] = (int)
            // chunk.currentBytePosition + chunk.buffer.capacity();

        }
        catch (Exception e) {

            e.printStackTrace();
            error(ERROR_LOCAL_IO);
        }

    }



    @Override
    protected void setupChunks() throws DownloadFailedException {
        try {
            boolean correctChunks = JDUtilities.getSubConfig("DOWNLOAD").getBooleanProperty("PARAM_DOWNLOAD_AUTO_CORRECTCHUNKS", true);
            long fileSize = getFileSize();
            if (correctChunks) {

                int tmp = Math.min(Math.max(1, (int) (fileSize / Chunk.MIN_CHUNKSIZE)), getChunkNum());
                if (tmp != getChunkNum()) {
                    logger.finer("Corrected Chunknum: " + getChunkNum() + " -->" + tmp);
                    this.setChunkNum(tmp);
                }
            }

            String fileName = downloadLink.getName();

            downloadLink.setStatus(DownloadLink.STATUS_DOWNLOAD_IN_PROGRESS);

            setChunkNum(Math.min(getChunkNum(), plugin.getFreeConnections()));
            // logger.info("Filsize: " + fileSize);
            long parts = fileSize > 0 ? fileSize / getChunkNum() : -1;
            if (parts == -1) {
                logger.warning("Could not get Filesize.... reset chunks to 1");
                setChunkNum(1);
            }
            logger.finer("Start Download in " + getChunkNum() + " chunks. Chunksize: " + parts);
            downloadLink.setDownloadMax((int) fileSize);
            // downloadLink.setChunksProgress(new int[chunkNum]);
            Chunk chunk;

          
            channels = new FileChannel[getChunkNum()];
            partFiles = new File[getChunkNum()];
            for (int i = 0; i < getChunkNum(); i++) {
                partFiles[i] = new File(downloadLink.getFileOutput() + ".part_" + i + "_" + fileSize);
                partFiles[i].delete();
                logger.info("create partfile " + i + " - " + partFiles[i].getAbsolutePath());
                if (i == (getChunkNum() - 1)) {
                    chunk = new Chunk(i * parts, -1, connection);
                }
                else {
                    chunk = new Chunk(i * parts, (i + 1) * parts, connection);
                }

                if (!partFiles[i].exists()) partFiles[i].createNewFile();

                channels[i] = new FileOutputStream(partFiles[i], true).getChannel();
               
                addChunk(chunk);
            }

        }
        catch (Exception e) {
            e.printStackTrace();
            throw new DownloadFailedException("Chunksetup failed: " + e.getLocalizedMessage());
        }

    }

    @Override
    protected void onChunksReady() throws DownloadFailedException {

        if (!handleErrors()) {

            throw new DownloadFailedException("Download failed after chunks were ready");
        }

        FileChannel in;
        FileChannel out;
        try {
            out = new FileOutputStream(downloadLink.getFileOutput(), true).getChannel();

            int coppied = 0;
            for (int i = 0; i < getChunkNum(); i++) {
                channels[i].force(true);
                channels[i].close();
                coppied = 0;
                in = new FileInputStream(partFiles[i].getAbsolutePath()).getChannel();

                while (true){
                    coppied += in.transferTo(coppied, partFiles[i].length(), out);
                    if(coppied==partFiles[i].length())break;
                }
                    ;
                
                logger.info("Coopied " + partFiles[i].getAbsolutePath() + " to " + downloadLink.getFileOutput());
                partFiles[i].delete();
                partFiles[i] = null;
                in.force(true);
                out.force(true);
                in.close();
                

            }
            out.close();
            return;
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new DownloadFailedException("Chunkfile not found after download");
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new DownloadFailedException("IO Exception while Merging files");
        }

    }

}
