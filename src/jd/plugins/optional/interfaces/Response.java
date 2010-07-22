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

package jd.plugins.optional.interfaces;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;

import jd.nutils.encoding.Encoding;

public class Response {

    public final static String OK = "200 OK";

    public final static String ERROR = "404 ERROR";

    public File fileServe = null;

    private StringBuilder data = new StringBuilder();

    public StringBuilder getData() {
        return data;
    }

    private HashMap<String, String> headers = new HashMap<String, String>();

    private String returnStatus = Response.OK;

    private String returnType = "text/html";

    private long filestart = 0;

    private long fileend = -1;

    private long filesize = 0;

    private boolean range = false;

    public Response() {
    }

    public void setFileServe(String path, long start, long end, long filesize, boolean range) {
        this.fileServe = new File(path);
        this.filestart = start;
        if (end == -1) {
            this.fileend = filesize;
        } else {
            this.fileend = end;
        }
        this.filesize = filesize;
        this.range = range;
        this.returnType = "application/octet-stream";
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setReturnStatus(String returnStatus) {
        this.returnStatus = returnStatus;
    }

    public void addContent(Object content) {
        data.append(content.toString());
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public void writeToStream(OutputStream out) throws IOException {
        StringBuilder help = new StringBuilder();
        help.append("HTTP/1.1 ").append(returnStatus).append("\r\n");
        help.append("Connection: close\r\n");
        help.append("Server: jDownloader HTTP Server\r\n");
        help.append("Content-Type: ").append(returnType).append("\r\n");
        try {
            help.append("Content-Length: ");
            if (fileServe != null) {
                if (!this.range) {
                    help.append(this.filesize);
                    help.append("\r\n");
                } else {
                    if (this.fileend == -1) {
                        this.fileend = this.filesize;
                    }
                    help.append(Math.max(0, fileend - filestart));
                    help.append("\r\n");
                    help.append("Content-Range: bytes " + filestart + "-" + fileend + "/" + filesize);
                    help.append("\r\n");
                }
                help.append("Content-Disposition: attachment;filename*=UTF-8''" + Encoding.urlEncode(fileServe.getName()));
                help.append("\r\n");
                help.append("Accept-Ranges: bytes");
                help.append("\r\n");
            } else {
                help.append(data.toString().getBytes("UTF-8").length);
            }
            help.append("\r\n");
        } catch (Exception e) {
        }
        for (String key : headers.keySet()) {
            help.append(key).append(": ").append(headers.get(key)).append("\r\n");
        }
        help.append("\r\n");
        help.append("\r\n");
        out.write(help.toString().getBytes("UTF-8"));

        if (fileServe != null) {
            RandomAccessFile raf = null;
            long served = 0;
            try {
                raf = new RandomAccessFile(fileServe, "r");
                raf.seek(filestart);
                long curpos = filestart;
                int toread = 1024;
                int read = 0;
                byte[] buffer = new byte[1024];
                if (fileend - curpos < 1024) {
                    toread = (int) (fileend - curpos);
                }
                while ((read = raf.read(buffer, 0, toread)) != -1) {
                    curpos += read;
                    served += read;
                    out.write(buffer);
                    if ((fileend - curpos) < 1024) {
                        toread = (int) (fileend - curpos);
                    }
                    if (toread == 0 || fileend == curpos) {
                        break;
                    }
                }
                raf.close();
            } finally {
                try {
                    raf.close();
                } catch (Exception e) {
                }
            }
        } else {
            out.write(data.toString().getBytes("UTF-8"));
        }
    }

}
