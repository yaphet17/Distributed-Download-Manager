package Tracker;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Formatter;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import LogWritter.LogWriter;

@Command(name = "tracker", description = "start tracker server", mixinStandardHelpOptions = true)
public class Server implements Runnable {

    private static SSLServerSocket server;
    private static SSLSocket socket;
    private static InetAddress inet;
    @Option(names = {"-p", "--port"}, description = "default port is 5000")
    private static int PORT = 5000;
    private final LogWriter logWritter = new LogWriter(this.getClass());

    private SSLServerSocket createServerSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket = null;
        try {
            serverSocket = (SSLServerSocket) serverFactory.createServerSocket(PORT);
            serverSocket.setEnabledCipherSuites(CIPHERS);
            serverSocket.setEnableSessionCreation(true);
            logWritter.writeLog("tracker-server start listening on port " + PORT, "info");
        } catch (IOException e1) {
            logWritter.writeLog("failed to create server on port" + PORT + "---" + e1.getMessage(), "error");
        }
        return serverSocket;
    }

    //Run server and handle client requests
    public void runServer() {
        try {
            //server start listening
            Tracker tracker = new Tracker();
            //prepare table to track client request
            Formatter fmt = new Formatter();
            System.out.println(fmt.format("%15s %15s %15s\n", "Ip", "Host Name", "Service"));
            while (true) {
                socket = (SSLSocket) server.accept();
                inet = socket.getInetAddress();
                new ClientHandler(socket, tracker);
            }
        } catch (IOException e) {
            logWritter.writeLog("failed to connect with client---" + e.getMessage(), "error");
        }

    }

    public void run() {
        //creating server
        server = createServerSocket();
        runServer();
    }


}
