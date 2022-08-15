package Server;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import LogWritter.LogWriter;

@Command(name = "server", description = "start download server", mixinStandardHelpOptions = true)
public class Server implements Runnable {

    @Parameters(paramLabel = "tracker-ip", description = "ip address of tracker server")
    private static String TRACKER_IP = null;
    @Option(names = {"-tp", "--trackerport"}, description = "default port is 5000")
    private static int TRACKER_PORT = 5000;
    @Option(names = {"-sp", "--serverport"}, description = "default port is 5001")
    protected static int SERVER_PORT = 5001;
    private static SSLServerSocket server;
    private static SSLSocket socket;
    private static DataOutputStream dos;
    private final LogWriter logWriter = new LogWriter(Server.class);

    private SSLServerSocket createServerSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket = null;
        try {
            serverSocket = (SSLServerSocket) serverFactory.createServerSocket(SERVER_PORT);
            serverSocket.setEnabledCipherSuites(CIPHERS);
            serverSocket.setEnableSessionCreation(true);
            logWriter.writeLog("server start listening on port " + SERVER_PORT, "info");
        } catch (IOException e1) {
            logWriter.writeLog("failed to create server on port" + SERVER_PORT + "---" + e1.getMessage(), "error");
        }
        return serverSocket;
    }

    private SSLSocket createSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = null;
        try {
            socket = (SSLSocket) socketFactory.createSocket(TRACKER_IP, TRACKER_PORT);
            socket.setEnabledCipherSuites(CIPHERS);
            socket.setEnableSessionCreation(true);
            logWriter.writeLog("client socket created with address " + TRACKER_IP + ":" + TRACKER_IP, "info");
        } catch (IOException e1) {
            logWriter.writeLog("failed to connect with tracker---" + e1.getMessage(), "error");
        }
        return socket;
    }

    private boolean checkConnection() {
        URL url;
        HttpURLConnection connection;
        try {
            url = new URL("https://www.google.com");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.connect();
        } catch (Exception e) {
            logWriter.writeLog("connection is not available---" + e.getMessage(), "warn");
            return false;
        }
        connection.disconnect();
        return true;
    }

    public void run() {
        //creating a server
        server = createServerSocket();
        //registering to tracker server
        socket = createSocket();
        //attach inputstream to socket
        try {
            dos = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            logWriter.writeLog("failed to attach stream to socket---" + e.getMessage(), "error");
        }
        //inform the tracker server is ready to accept download request
        if (!checkConnection()) {
            try {
                dos.writeUTF("noconnection");
            } catch (IOException e) {
                logWriter.writeLog("failed to attach stream to socket---" + e.getMessage(), "error");
            }
            return;
        }
        try {
            dos.writeUTF("server");
        } catch (IOException e) {
            logWriter.writeLog("failed to register to tracker---" + e.getMessage(), "error");
        }
        InetAddress inet = socket.getInetAddress();
        //accept client request
        SSLSocket socket;
        while (true) {
            try {
                socket = (SSLSocket) server.accept();
                logWriter.writeLog("client " + inet.getHostAddress() + " connected", "info");
                new ClientHandler(socket);
            } catch (IOException e) {
                logWriter.writeLog("failed to accept client request---" + e.getMessage(), "error");
            }

        }
    }
}
