package Tracker;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetAddress;
import java.util.HashSet;


class Tracker {
    public static HashSet<String> clientList;

    public  Tracker(){
        clientList=new HashSet<>();
    }
    synchronized public void addToList(String ip){
        clientList.add(ip);
    }
    synchronized  public void removeFromList(String ip){
        clientList.remove(ip);
    }
    synchronized public void getIpList(PrintWriter writer){
        if(clientList.size()==0){
            writer.write("null");//notify requesting client there no is active server
            return;
        }
        for(String ip:clientList){
            writer.write(ip);
        }
        writer.write("fin");
    }

}

class ClientHandler implements Runnable{

    private final SSLSocket socket;
    private final Tracker tracker;
    private BufferedReader reader;
    private PrintWriter writer;
    private final Thread t;
    private String message;
    private final String ip;
    private final InetAddress inet;

    public ClientHandler(SSLSocket socket,Tracker tracker){
        this.socket=socket;
        this.tracker=tracker;
        inet=socket.getInetAddress();
        ip=inet.getHostAddress();
        try {
            reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer=new PrintWriter(socket.getOutputStream(),true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        t=new Thread(this);
        t.start();
    }
    public void run(){
        try {
            //receive client request to give service
            message=reader.readLine();
            switch (message) {
                case "server" -> tracker.addToList(ip);//if message=server register as active server
                case "rm" -> tracker.removeFromList(ip);//if message=rm remove server from active servers list
                case "iplist" -> tracker.getIpList(writer);//if message=iplist send active servers ip list to invoking machine
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            writer.close();
            try {
                reader.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
public class Server{

    private static SSLServerSocket server;
    private static SSLSocket socket;

    private static final int PORT=5000;


    private static SSLServerSocket createServerSocket(int port) {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        //java.security.Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket = null;
        try {
            System.out.println("creating a server socket....");
            serverSocket = (SSLServerSocket) serverFactory.createServerSocket(port);
            System.out.println("server socket created");
            serverSocket.setEnabledCipherSuites(CIPHERS);
            serverSocket.setEnableSessionCreation(true);
        } catch (IOException e1) {
            e1.printStackTrace();

        }
        System.out.println("waiting for client....");
        return serverSocket;
    }
    //Run server and handle client requests
    public static void runServer(){
        try {
            System.out.println("Server start listening on port:"+PORT);
            //server start listening
            Tracker tracker=new Tracker();
            while(true){
                socket=(SSLSocket) server.accept();
                new ClientHandler(socket,tracker);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public static void main(String[] args){
        //creating server
        server=createServerSocket(PORT);
        runServer();
    }


}
