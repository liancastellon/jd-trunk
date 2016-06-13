package org.jdownloader.phantomjs;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import org.appwork.exceptions.WTFException;
import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.net.protocol.http.HTTPConstants.ResponseCode;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.Application;
import org.appwork.utils.IO;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.Base64;
import org.appwork.utils.encoding.URLEncode;
import org.appwork.utils.images.IconIO;
import org.appwork.utils.logging2.ConsoleLogImpl;
import org.appwork.utils.logging2.LogInterface;
import org.appwork.utils.net.HTTPHeader;
import org.appwork.utils.net.httpconnection.HTTPConnection.RequestMethod;
import org.appwork.utils.net.httpconnection.HTTPProxy;
import org.appwork.utils.net.httpserver.HttpServer;
import org.appwork.utils.net.httpserver.handler.HttpRequestHandler;
import org.appwork.utils.net.httpserver.requests.GetRequest;
import org.appwork.utils.net.httpserver.requests.PostRequest;
import org.appwork.utils.net.httpserver.responses.HttpResponse;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.processes.ProcessBuilderFactory;
import org.appwork.utils.reflection.Clazz;
import org.jdownloader.controlling.UniqueAlltimeID;
import org.jdownloader.webcache.CachedHeader;
import org.jdownloader.webcache.CachedRequest;
import org.jdownloader.webcache.WebCache;

import jd.http.Browser;
import jd.http.ProxySelectorInterface;
import jd.http.QueryInfo;
import jd.http.Request;
import jd.plugins.components.UserAgents;

public class PhantomJS implements HttpRequestHandler {
    private static final boolean DEBUGGER = false;
    private LogInterface         logger;
    private File                 exe;
    private String               accessToken;
    private WebCache             webCache;

    public boolean isAvailable() {
        final File bins = getBinaryPath();
        return bins != null && bins.exists() && bins.canExecute();
    }

    public PhantomJS() {
        logger = new ConsoleLogImpl();
    }

    public LogInterface getLogger() {
        return logger;
    }

    public void setLogger(LogInterface logger) {
        this.logger = logger;
    }

    protected WebCache initWebCache() {
        return new WebCache();
    }

    protected void initBinaries() throws PhantomJSBinariesMissingException {
        final File exe = getBinaryPath();
        if (!exe.exists()) {
            throw new PhantomJSBinariesMissingException(exe.getAbsolutePath());
        }
        this.exe = exe;
    }

    public File getBinaryPath() {
        final File exe;
        switch (CrossSystem.getOS().getFamily()) {
        case WINDOWS:
            exe = Application.getResource("tools/Windows/phantomjs/phantomjs.exe");
            break;
        case MAC:
            exe = Application.getResource("tools/mac/phantomjs/phantomjs");
            break;
        default:
            exe = Application.getResource("tools/" + CrossSystem.getOS().getFamily().name().toLowerCase(Locale.ENGLISH) + "/phantomjs/" + CrossSystem.getARCHFamily().name() + "_" + (CrossSystem.is64BitOperatingSystem() ? "x64" : "") + "_phantomjs");
            break;
        }
        return exe;
    }

    private Browser    br;
    private HttpServer server;

    public Browser getBr() {
        return br;
    }

    public void setBr(Browser httpClient) {
        this.br = httpClient;
    }

    public void initPipe() throws IOException {
        server = new HttpServer(0);
        server.setLocalhostOnly(true);
        server.start();

        server.registerRequestHandler(this);

    }

    @Override
    public boolean onPostRequest(PostRequest request, HttpResponse response) {
        boolean requestOkay = false;

        try {
            String id = request.getParameterbyKey("id");
            if (Long.parseLong(id) != this.id) {
                return false;
            }
            String path = request.getRequestedPath();
            if ("/webproxy".equalsIgnoreCase(path)) {
                String url = request.getParameterbyKey("url");
                String requestID = request.getParameterbyKey("rid");

                onWebProxy(request, response, url, requestID, false);

            } else {
                System.out.println("UNKNOWN");
            }
            return true;
        } catch (Exception e) {
            logger.log(e);
        } finally {

        }
        return false;
    }

    protected void onWebProxy(org.appwork.utils.net.httpserver.requests.HttpRequest request, HttpResponse response, String url, String requestID, boolean b) throws IOException {
        try {

            logger.info((b ? "GET" : "POST") + " " + url);
            Request newRequest = null;
            if (url.startsWith("data:")) {
                BufferedImage image = IconIO.toBufferedImage(IconIO.getImageFromDataUrl(url));
                ByteArrayOutputStream bao = new ByteArrayOutputStream();
                ImageIO.write(image, "png", bao);
                // BasicWindow.showImage(image, "Dataurl");
                response.getResponseHeaders().add(new HTTPHeader("Content-Length", bao.size() + ""));
                response.setResponseCode(ResponseCode.SUCCESS_OK);
                response.getOutputStream(true).write(bao.toByteArray());
                return;

            }
            if (b) {
                CachedRequest cache = webCache.get(url);
                if (cache != null) {
                    // logger.info(newRequest + "");
                    for (CachedHeader s : cache.getHeaders()) {
                        for (String v : s.getValues()) {

                            if (StringUtils.equalsIgnoreCase(s.getKey(), "Content-Encoding")) {
                                continue;
                            }
                            if (StringUtils.equalsIgnoreCase(s.getKey(), "Transfer-Encoding")) {
                                continue;
                            }
                            if (StringUtils.equalsIgnoreCase(s.getKey(), "Content-Length")) {
                                continue;
                            }
                            response.getResponseHeaders().add(new HTTPHeader(s.getKey(), v));
                        }
                    }

                    response.getResponseHeaders().remove("Content-Encoding");
                    response.getResponseHeaders().remove("Transfer-Encoding");

                    response.getResponseHeaders().add(new HTTPHeader("Content-Length", cache._getBytes().length + ""));
                    response.setResponseCode(ResponseCode.get(cache.getResponseCode()));
                    logger.info("-->Cached");
                    response.getOutputStream(true).write(cache._getBytes());
                    return;
                }
                newRequest = new jd.http.requests.GetRequest(url);

            } else {
                newRequest = new jd.http.requests.PostRequest(url);
                byte[] bytes = IO.readStream(-1, ((PostRequest) request).getInputStream());
                ((jd.http.requests.PostRequest) newRequest).setPostBytes(bytes);
            }

            for (HTTPHeader header : request.getRequestHeaders()) {
                if (StringUtils.equalsIgnoreCase(header.getKey(), "Host")) {
                    continue;
                }
                if (StringUtils.equalsIgnoreCase(header.getKey(), "Connection")) {
                    continue;
                }

                newRequest.getHeaders().put(header.getKey(), header.getValue());

            }
            br.setDebug(false);
            br.setVerbose(false);
            synchronized (br) {

                boolean keepBytes = br.isKeepResponseContentBytes();
                try {
                    br.setKeepResponseContentBytes(true);
                    br.getPage(newRequest);

                } finally {
                    br.setKeepResponseContentBytes(keepBytes);
                }
            }
            // logger.info(newRequest + "");
            for (Entry<String, List<String>> s : newRequest.getResponseHeaders().entrySet()) {
                for (String v : s.getValue()) {

                    if (StringUtils.equalsIgnoreCase(s.getKey(), "Content-Encoding")) {
                        continue;
                    }
                    if (StringUtils.equalsIgnoreCase(s.getKey(), "Transfer-Encoding")) {
                        continue;
                    }
                    if (StringUtils.equalsIgnoreCase(s.getKey(), "Content-Length")) {
                        continue;
                    }
                    response.getResponseHeaders().add(new HTTPHeader(s.getKey(), v));
                }
            }

            // logger.info(newRequest.getHttpConnection() + "");
            byte[] data = newRequest.getResponseBytes();
            if (data == null) {
                throw new WTFException();
            }
            response.getResponseHeaders().remove("Content-Encoding");
            response.getResponseHeaders().remove("Transfer-Encoding");
            byte[] bytes = newRequest.getResponseBytes();
            if (newRequest instanceof jd.http.requests.GetRequest) {
                cacheRequest(url, newRequest, bytes);
            }
            bytes = onRequestDone(url, b, newRequest, bytes);

            response.getResponseHeaders().add(new HTTPHeader("Content-Length", bytes.length + ""));
            response.setResponseCode(ResponseCode.get(newRequest.getHttpConnection().getResponseCode()));
            response.getOutputStream(true).write(bytes);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected void cacheRequest(String url, Request newRequest, byte[] bytes) {

        ArrayList<CachedHeader> headers = new ArrayList<CachedHeader>();
        for (Entry<String, List<String>> s : newRequest.getResponseHeaders().entrySet()) {
            if (StringUtils.equalsIgnoreCase(s.getKey(), "Content-Encoding")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(s.getKey(), "Transfer-Encoding")) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(s.getKey(), "Content-Length")) {
                continue;
            }

            headers.add(new CachedHeader(s.getKey(), s.getValue()));

        }

        webCache.put(new CachedRequest(RequestMethod.GET, url, url, newRequest.getHttpConnection().getResponseCode(), newRequest.getHttpConnection().getResponseMessage(), bytes, headers));
    }

    @Override
    public boolean onGetRequest(GetRequest request, HttpResponse response) {
        boolean requestOkay = false;

        try {
            String id = request.getParameterbyKey("id");
            try {
                if (Long.parseLong(id) != this.id) {
                    return false;
                }
            } catch (Throwable e) {
                // e.printStackTrace();
                return false;
            }
            String path = request.getRequestedPath();
            if ("/ping".equalsIgnoreCase(path)) {
                response.setResponseCode(ResponseCode.SUCCESS_OK);
                response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_RESPONSE_CONTENT_TYPE, "text/json; charset=utf-8"));
                response.getOutputStream(true).write("ok".getBytes("UTF-8"));
            } else if ("/exit".equalsIgnoreCase(path)) {

                kill();
                response.setResponseCode(ResponseCode.SUCCESS_OK);
                response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_RESPONSE_CONTENT_TYPE, "text/json; charset=utf-8"));
                response.getOutputStream(true).write("{}".getBytes("UTF-8"));
            } else if ("/webproxy".equalsIgnoreCase(path)) {
                String url = request.getParameterbyKey("url");
                String requestID = request.getParameterbyKey("rid");

                onWebProxy(request, response, url, requestID, true);

            } else {
                System.out.println("UNKNOWN");
            }
            return true;
        } catch (Exception e) {
            logger.log(e);
        } finally {

        }
        return false;
    }

    protected byte[] onRequestDone(String url, boolean getMethod, Request newRequest, byte[] bytes) throws Exception {
        return bytes;
    }

    private boolean webSecurity = false;

    public boolean isWebSecurity() {
        return webSecurity;
    }

    public void setWebSecurity(boolean webSecurity) {
        this.webSecurity = webSecurity;
    }

    public boolean isIgnoreSslErrors() {
        return ignoreSslErrors;
    }

    public void setIgnoreSslErrors(boolean ignoreSslErrors) {
        this.ignoreSslErrors = ignoreSslErrors;
    }

    private boolean               ignoreSslErrors      = false;
    private Thread                phantomProcessThread;
    protected long                id;

    private HashMap<Long, String> results;
    private int                   phantomJSPort;
    private File                  scriptFile;
    private boolean               processServerRunning = false;
    private Thread                psjPinger;

    private class LoggerStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {

            byte[] stringBytes = new byte[len];
            System.arraycopy(b, off, stringBytes, 0, len);
            logger.info(new String(stringBytes, "UTF-8"));
        }
    }

    private String replace(String js) {

        js = js.replace("null/* %%%localPort%%% */", server.getPort() + "");
        js = js.replace("null/* %%%localID%%% */", id + "");
        js = js.replace("null/* %%%debugger%%% */", DEBUGGER + "");
        js = js.replace("null/* %%%phantomPort%%% */", phantomJSPort + "");
        js = js.replace("null/* %%%accessToken%%% */", "\"" + accessToken + "\"");
        return js;
    }

    protected InetAddress getLocalHost() {
        InetAddress localhost = null;
        try {
            localhost = InetAddress.getByName("127.0.0.1");
        } catch (final UnknownHostException e1) {
        }
        if (localhost != null) {
            return localhost;
        }
        try {
            localhost = InetAddress.getByName(null);
        } catch (final UnknownHostException e1) {
        }
        return localhost;
    }

    public void init() throws IOException, InterruptedException, PhantomJSBinariesMissingException {

        accessToken = new BigInteger(130, new SecureRandom()).toString(32);

        ipcBrowser = new Browser();
        ipcBrowser.setVerbose(false);
        ipcBrowser.setDebug(false);
        webCache = initWebCache();
        ipcBrowser.setProxySelector(new ProxySelectorInterface() {
            private ArrayList<HTTPProxy> lst = new ArrayList<HTTPProxy>();

            {
                lst.add(HTTPProxy.NONE);
            }

            @Override
            public boolean updateProxy(Request request, int retryCounter) {
                return false;
            }

            @Override
            public boolean reportConnectException(Request request, int retryCounter, IOException e) {
                return false;
            }

            @Override
            public List<HTTPProxy> getProxiesByURL(URL uri) {

                return lst;
            }
        });
        initBinaries();

        results = new HashMap<Long, String>();
        id = new UniqueAlltimeID().getID();

        initPipe();
        final OutputStream stream = new LoggerStream();
        // find free port for phantomjs

        final SocketAddress socketAddress = new InetSocketAddress(this.getLocalHost(), 0);
        ServerSocket controlSocket = new ServerSocket();
        controlSocket.setReuseAddress(true);
        controlSocket.bind(socketAddress);
        phantomJSPort = controlSocket.getLocalPort();
        controlSocket.close();
        scriptFile = Application.getTempResource("phantom_" + id + ".js");
        IO.writeStringToFile(scriptFile, replace(IO.readURLToString(PhantomJS.class.getResource("phantom.js"))));

        List<String> lst = createCmd();
        final ProcessBuilder pb = ProcessBuilderFactory.create(lst);
        pb.directory(exe.getParentFile());
        final OutputStream stdStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
            }

            String sb = "";

            @Override
            public void write(byte[] b, int off, int len) throws IOException {

                byte[] stringBytes = new byte[len];
                System.arraycopy(b, off, stringBytes, 0, len);
                String str = new String(stringBytes, "UTF-8");
                sb += str;

                // logger.info(str);
                String[][] resultMatches = new Regex(sb, ">>>RESULT\\:(\\-?\\d+)\\:([^\r\n]*?)<<<\\s*[\r\n]{1,2}").getMatches();
                if (resultMatches != null && resultMatches.length > 0) {
                    synchronized (results) {
                        for (String[] result : resultMatches) {
                            long jobID = Long.parseLong(result[0]);
                            if (jobID <= 0) {
                                onPhantomJSEvent(jobID, result[1]);
                            } else {
                                results.put(jobID, result[1]);
                            }

                        }
                        results.notifyAll();

                    }
                }
                String[] logs = new Regex(sb, ">>>LOG\\:([^\r\n]*?)<<<\\s*[\r\n]{1,2}").getColumn(0);
                if (logs != null && logs.length > 0) {
                    synchronized (results) {
                        for (String result : logs) {
                            Object logged = JSonStorage.restoreFromString(result, TypeRef.OBJECT);
                            if (Clazz.isPrimitiveWrapper(logged.getClass()) || logged instanceof String) {
                                logger.info("PJS: " + logged);
                            } else {
                                logger.info("PJS: " + JSonStorage.serializeToJson(logged));
                            }
                        }

                    }
                }
                int lastIndex = sb.lastIndexOf("<<<");
                if (lastIndex > 0) {
                    sb = sb.substring(lastIndex + 3);
                }
            }
        };

        phantomProcessThread = new Thread("Phantom.JS") {
            public void run() {
                try {

                    ProcessBuilderFactory.runCommand(pb, stream, stdStream);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    onDisposed();
                    phantomProcessThread = null;
                }

            };
        };
        psjPinger = new Thread("Phantom.JS Ping") {
            public void run() {
                try {
                    while (phantomProcessThread != null) {

                        Thread.sleep(5000);

                        String result = ipcBrowser.postPage("http://127.0.0.1:" + phantomJSPort + "/ping", new QueryInfo().addAndReplace("accessToken", URLEncode.encodeRFC2396(accessToken)));
                        if (!"OK".equals(result)) {

                            throw new IOException("IPC JD->PJS Failed: " + result);

                        }
                    }
                } catch (Throwable e) {
                    logger.log(e);
                    kill();
                }

            };
        };
        psjPinger.start();
        phantomProcessThread.start();
        while (phantomProcessThread != null && !processServerRunning) {
            synchronized (results) {
                results.wait(1000);

            }
        }
        eval("page.settings.userAgent=\"" + UserAgents.stringUserAgent() + "\";");
        if (DEBUGGER) {
            Thread.sleep(2000);
            CrossSystem.openURL("http://127.0.0.1:9999/webkit/inspector/inspector.html?page=1");
        }

    }

    protected void onDisposed() {
    }

    protected void onPhantomJSEvent(long jobID, String string) {
        if (jobID == -1) {
            // SERVER Status;
            logger.info("Server Status: " + string);
            processServerRunning = JSonStorage.restoreFromString(string, TypeRef.BOOLEAN) == Boolean.TRUE;
            if (!processServerRunning) {
                logger.info("Could not start Phantom Server");
                kill();
            } else {

            }
        }
    }

    protected List<String> createCmd() throws IOException {
        ArrayList<String> lst = new ArrayList<String>();

        lst.add(exe.getAbsolutePath());
        // lst.add("--debug=true");
        lst.add("--output-encoding=utf8");
        // lst.add("--web-security=" + isWebSecurity());
        lst.add("--ignore-ssl-errors=" + isIgnoreSslErrors());
        if (DEBUGGER) {
            lst.add("--remote-debugger-port=9999");
            lst.add("--remote-debugger-autorun=true");
        }

        lst.add(scriptFile.getAbsolutePath());
        return lst;
    }

    public void loadPage(String url) throws InterruptedException, IOException {
        long jobID = new UniqueAlltimeID().getID();

        String result = execute(jobID, "loadPage(" + jobID + ",'" + url + "');");
        if (!"success".equals(JSonStorage.restoreFromString(result, TypeRef.STRING))) {
            throw new IOException("Could not load page");
        }

    }

    private String waitForJob(long jobID) throws InterruptedException {
        long started = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - started > 60000) {
                throw new InterruptedException("Timeout");
            }
            if (phantomProcessThread == null || !phantomProcessThread.isAlive()) {
                throw new InterruptedException("Process died");
            }
            synchronized (results) {
                if (!results.containsKey(jobID)) {
                    results.wait(1000);
                } else {
                    return results.remove(jobID);
                }
            }

        }
    }

    public void kill() {

        if (phantomProcessThread != null) {
            phantomProcessThread.interrupt();
            phantomProcessThread = null;
        }
        if (psjPinger != null) {
            psjPinger.interrupt();
            psjPinger = null;
        }
        if (server != null) {
            server.stop();
        }
    }

    public Image getScreenShot() throws InterruptedException, IOException {
        long jobID = new UniqueAlltimeID().getID();

        String result = ipcBrowser.postPage("http://127.0.0.1:" + phantomJSPort + "/screenshot", new QueryInfo().addAndReplace("jobID", jobID + "").addAndReplace("accessToken", URLEncode.encodeRFC2396(accessToken)));
        if ("OK".equals(result)) {
            String base64JSon = waitForJob(jobID);
            String base64 = JSonStorage.restoreFromString(base64JSon, TypeRef.STRING);
            return ImageIO.read(new ByteArrayInputStream(Base64.decode(base64)));
        } else {
            throw new IOException("IPC JD->PJS Failed: " + result);
        }

    }

    public void waitUntilDOM(String conditionJS) throws InterruptedException, IOException {
        long started = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - started > 30000) {
                throw new InterruptedException("Timeout");
            }
            long jobID = new UniqueAlltimeID().getID();
            String js = "var evalResult=page.evaluate(function(){try{ret= " + conditionJS + ";return ret; } catch (err){console.log(err);return false;}}); endJob(" + jobID + ",evalResult);";
            String result = execute(jobID, "(function(){" + js + "})()");
            if (JSonStorage.restoreFromString(result, TypeRef.BOOLEAN) == Boolean.TRUE) {
                return;
            }

        }
    }

    private Browser ipcBrowser;

    public void eval(String domjs) throws InterruptedException, IOException {
        long jobID = new UniqueAlltimeID().getID();
        String result = execute(jobID, domjs + "; endJob(" + jobID + ",null);");

    }

    public String execute(long jobID, String js) throws IOException, InterruptedException {

        String result = ipcBrowser.postPage("http://127.0.0.1:" + phantomJSPort + "/exec", new QueryInfo().addAndReplace("js", URLEncode.encodeRFC2396(js)).addAndReplace("accessToken", URLEncode.encodeRFC2396(accessToken)));
        if ("OK".equals(result)) {
            return waitForJob(jobID);
        } else {
            throw new IOException("IPC JD->PJS Failed: " + result);
        }

    }

    public Object get(String domjs) throws InterruptedException, IOException {
        long jobID = new UniqueAlltimeID().getID();
        String s = execute(jobID, " endJob(" + jobID + "," + domjs + ");");
        return JSonStorage.restoreFromString(s, new TypeRef<Object>() {
        });

    }

    public Object evalInPageContext(String domjs) throws InterruptedException, IOException {

        long jobID = new UniqueAlltimeID().getID();
        String s = execute(jobID, "(function(){var result= page.evaluate(function(){ return " + domjs + ";}); endJob(" + jobID + ",result);})()");

        return JSonStorage.restoreFromString(s, new TypeRef<Object>() {
        });
    }

    public void setVariable(String key, String string) throws IOException, InterruptedException {
        long jobID = new UniqueAlltimeID().getID();
        execute(jobID, " _global['" + key + "']=\"" + string + "\"; endJob(" + jobID + ",null);");
    }

    public void setVariable(String key, int integer) throws IOException, InterruptedException {
        long jobID = new UniqueAlltimeID().getID();
        execute(jobID, " _global['" + key + "']=" + integer + "; endJob(" + jobID + ",null);");
    }

    public void setVariable(String key, boolean b) throws IOException, InterruptedException {
        long jobID = new UniqueAlltimeID().getID();
        execute(jobID, " _global['" + key + "']=" + b + "; endJob(" + jobID + ",null);");
    }

    public void switchFrameToMain() throws IOException, InterruptedException {
        long jobID = new UniqueAlltimeID().getID();
        execute(jobID, "page.switchToMainFrame(); endJob(" + jobID + ",null);");

    }

    public void switchFrameToChild(int index) throws IOException, InterruptedException {
        long jobID = new UniqueAlltimeID().getID();
        execute(jobID, "page.switchToChildFrame(" + index + "); endJob(" + jobID + ",null);");

    }

    public String getFrameHtml() throws IOException, InterruptedException {
        long jobID = new UniqueAlltimeID().getID();
        return JSonStorage.restoreFromString(execute(jobID, "endJob(" + jobID + ",page.frameContent);"), TypeRef.STRING);
    }

    public String execute(String string) throws IOException, InterruptedException {
        long jobID = new UniqueAlltimeID().getID();
        return execute(jobID, string + "; endJob(" + jobID + ",null);");

    }

    public boolean isRunning() {
        return server.isRunning() && processServerRunning && phantomProcessThread != null && phantomProcessThread.isAlive();
    }

}
