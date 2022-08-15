package Tracker;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Formatter;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import LogWritter.LogWriter;

@Command(name = "tracker", description = "start tracker server", mixinStandardHelpOptions = true)
public class Server implements Runnable {

    private static final LogWriter logWriter = new LogWriter(Server.class);
    private static SSLServerSocket server;
    private static SSLSocket socket;
    private static InetAddress inet;
    @Option(names = {"-p", "--port"}, description = "default port is 5000")
    private static int PORT = 5000;

    private SSLServerSocket createServerSocket() {
        SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket = null;
        try {
            serverSocket = (SSLServerSocket) serverFactory.createServerSocket(PORT);
            String[] CIPHERS = serverSocket.getEnabledCipherSuites();
            System.out.println(Arrays.asList(CIPHERS));
            serverSocket.setEnabledCipherSuites(CIPHERS);
            serverSocket.setEnableSessionCreation(true);
            logWriter.writeLog("tracker-server start listening on port " + PORT, "info");
        } catch (IOException e1) {
            logWriter.writeLog("failed to create server on port" + PORT + "---" + e1.getMessage(), "error");
        }
        return serverSocket;
    }

    public void runServer() {
        try {
            Tracker tracker = new Tracker();
            Formatter fmt = new Formatter();
            System.out.println(fmt.format("%15s %15s %15s\n", "Ip", "Host Name", "Service"));
            while (true) {
                socket = (SSLSocket) server.accept();
                inet = socket.getInetAddress();
                new ClientHandler(socket, tracker);
            }
        } catch (IOException e) {
            logWriter.writeLog("failed to connect with client---" + e.getMessage(), "error");
        }

    }

    public void run() {
        server = createServerSocket();
        runServer();
    }


}
