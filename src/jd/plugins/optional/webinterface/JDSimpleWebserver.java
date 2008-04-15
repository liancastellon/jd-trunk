package jd.plugins.optional.webinterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.logging.Logger;

import jd.config.SubConfiguration;
import jd.utils.JDUtilities;

public class JDSimpleWebserver extends Thread {

    private ServerSocket Server_Socket;

    private boolean Server_Running = true;

    private Logger logger = JDUtilities.getLogger();

    public static int CURRENT_CLIENT_COUNTER = 0;

    private static int max_clientCounter = 0;

    private static String AuthUser = "";
    private static boolean NeedAuth = false;

    public JDSimpleWebserver() {

        SubConfiguration subConfig = JDUtilities.getSubConfig("WEBINTERFACE");
        max_clientCounter = subConfig.getIntegerProperty(JDWebinterface.PROPERTY_CONNECTIONS, 10);
        AuthUser = "Basic " + JDUtilities.Base64Encode(subConfig.getStringProperty(JDWebinterface.PROPERTY_USER, "JD") + ":" + subConfig.getStringProperty(JDWebinterface.PROPERTY_PASS, "JD"));
        NeedAuth = subConfig.getBooleanProperty(JDWebinterface.PROPERTY_LOGIN, true);
        try {
            Server_Socket = new ServerSocket(subConfig.getIntegerProperty(JDWebinterface.PROPERTY_PORT, 1024));
            logger.info("Webinterface: Server started");
            start();
        } catch (IOException e) {
            logger.info("WebInterface: Server failed to start!");
        }
    }

    public void run() {
        while (Server_Running) {
            try {
                while (getCurrentClientCounter() >= max_clientCounter) {
                    try {
                        logger.info("warte");
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                    ;
                }
                ;
                Socket Client_Socket = Server_Socket.accept();
                logger.info("WebInterface: Client[" + getCurrentClientCounter() + "/" + max_clientCounter + "] connecting from " + Client_Socket.getInetAddress());

                Thread client_thread = new Thread(new JDRequestHandler(Client_Socket));
                client_thread.start();
                /*
                 * JDSimpleStatusPage requestThread = new
                 * JDSimpleStatusPage(Client_Socket); requestThread.start();
                 */

            } catch (IOException e) {
                logger.info("WebInterface: Client-Connection failed");
            }
        }
    }

    private class JDRequestHandler implements Runnable {

        private Socket Current_Socket;

        private Logger logger = JDUtilities.getLogger();

        public JDRequestHandler(Socket Client_Socket) {
            this.Current_Socket = Client_Socket;
        }

        public void run() {
            addToCurrentClientCounter(1);
            run0();
            addToCurrentClientCounter(-1);

        }

        public void run0() {
            try {
                InputStream requestInputStream = Current_Socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(requestInputStream));
                String line = null;
                HashMap<String, String> headers = new HashMap<String, String>();
                while ((line = reader.readLine()) != null && line.trim().length() > 0) {
                    // GET
                    // /cgi-bin/uploadjs.cgi?uploadid=130439095897968957&r=31
                    // HTTP/1.1
                    String key = null;
                    String value = null;
                    if (line.indexOf(": ") > 0) {
                        key = line.substring(0, line.indexOf(": ")).toLowerCase();
                        value = line.substring(line.indexOf(": ") + 2);
                    } else {
                        key = null;
                        value = line;
                    }
                    headers.put(key, value);

                    // if (line.startsWith("GET") || line.startsWith("POST")) {
                    // StringTokenizer tokenizer = new StringTokenizer(line, "
                    // ");
                    // String requestType = tokenizer.nextToken();
                    // String url = tokenizer.nextToken();
                    //
                    // Map<String, String> parameters = new HashMap<String,
                    // String>();
                    //
                    // int indexOfQuestionMark = url.indexOf("?");
                    // if (indexOfQuestionMark >= 0) {
                    // // there are URL parameters
                    // String parametersToParse =
                    // url.substring(indexOfQuestionMark + 1);
                    // url = url.substring(0, indexOfQuestionMark);
                    // StringTokenizer parameterTokenizer = new
                    // StringTokenizer(parametersToParse, "&");
                    // while (parameterTokenizer.hasMoreTokens()) {
                    // String[] keyAndValue =
                    // parameterTokenizer.nextToken().split("=");
                    // String key = URLDecoder.decode(keyAndValue[0], "utf-8");
                    // String value = URLDecoder.decode(keyAndValue[1],
                    // "utf-8");
                    // parameters.put(key, value);
                    // }
                    // }

                    // logger.info(headers+"");
                }
                ;
                JDSimpleWebserverResponseCreator response = new JDSimpleWebserverResponseCreator();
                JDSimpleWebserverRequestHandler request = new JDSimpleWebserverRequestHandler(headers, response);
                OutputStream outputStream = Current_Socket.getOutputStream();
                if (NeedAuth == true) {/* need authorization */
                    if (headers.containsKey("authorization")) {
                        if (JDSimpleWebserver.AuthUser.equals(headers.get("authorization"))) { /*
                                                                                                 * send
                                                                                                 * authorization
                                                                                                 * granted
                                                                                                 */
                            logger.info("pass stimmt");
                            request.handle();

                        } else { /* send authorization failed */
                            response.setAuth_failed();
                        }
                    } else { /* send autorization needed */
                        response.setAuth_needed();
                    }
                } else { /* no autorization needed */
                    request.handle();
                }
                ;

                response.writeToStream(outputStream);
                outputStream.close();
                Current_Socket.close();

            } catch (SocketException e) {
                logger.info("WebInterface: Socket error");
            } catch (IOException e) {
                logger.info("WebInterface: I/O Error");
            }
        }

    }

    /**
     * greift Threadsafe auf den clientcounter zu
     * 
     * @return
     */
    public synchronized int getCurrentClientCounter() {
        return CURRENT_CLIENT_COUNTER;
    }

    /**
     * Fügt einen Wert zum aktuellen Clientzähler hinzu
     * 
     * @param i
     * @return
     */
    public synchronized int addToCurrentClientCounter(int i) {
        CURRENT_CLIENT_COUNTER += i;
        return CURRENT_CLIENT_COUNTER;
    }

    /**
     * setzt den aktuellen Client Zähler.
     * 
     * @param current_clientCounter
     */
    public synchronized void setCurrentClientCounter(int cc) {
        CURRENT_CLIENT_COUNTER = cc;
    }

}
