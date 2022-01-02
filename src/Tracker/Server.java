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
        System.out.println("Active server added");
    }
    synchronized  public void removeFromList(String ip){
        clientList.remove(ip);
        System.out.println("Server removed");
    }
    synchronized public void getIpList(DataOutputStream dos){
        try{
            if(clientList.size()==0){
                dos.writeUTF("null");//notify requesting client there no is active server
                System.out.println("no active serevr");
                return;
            }
            System.out.println("sending active server ip list");
            for(String ip:clientList){
                dos.writeUTF(ip);
                System.out.println("Server: "+ip);
            }
            dos.writeUTF("fin");
            System.out.println("Ip list sent");
        }catch(IOException e){
            e.printStackTrace();
        }

    }

}

class ClientHandler implements Runnable{

    private final SSLSocket socket;
    private final Tracker tracker;
    private DataInputStream dis;
    private DataOutputStream dos;
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
            dis=new DataInputStream(socket.getInputStream());
            dos=new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        t=new Thread(this);
        t.start();
    }
    public void run(){
        System.out.println("client handler");
        try {
            //receive client request to give service
            message=dis.readUTF();
            System.out.println("request recieved");
            switch (message) {
                case "server" -> tracker.addToList(ip);//if message=server register as active server
                case "rm" -> tracker.removeFromList(ip);//if message=rm remove server from active servers list
                case "iplist" -> tracker.getIpList(dos);//if message=iplist send active servers ip list to invoking machine
            }
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {

            try {
                dis.close();;
                dos.close();
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
                System.out.println("client connected");
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
