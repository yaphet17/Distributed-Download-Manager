package Tracker;
import LogWritter.LogWritter;
import org.jline.utils.Log;
import picocli.CommandLine;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetAddress;
import java.util.Formatter;
import java.util.HashSet;

import static picocli.CommandLine.*;


class Tracker {
    public static HashSet<String> clientList;
    private final LogWritter logWritter=new LogWritter(this.getClass());

    public  Tracker(){
        clientList=new HashSet<>();
    }
    synchronized public void addToList(String ip){
        clientList.add(ip);
    }
    synchronized  public void removeFromList(String ip){
        clientList.remove(ip);
    }
    synchronized public void getIpList(DataOutputStream dos){
        try{
            if(clientList.size()==0){
                dos.writeUTF("null");//notify requesting client there is no active server
                return;
            }
            for(String ip:clientList){
                dos.writeUTF(ip);
            }
        }catch(IOException e){
            logWritter.writeLog("error to send active server list to client---"+e.getMessage(),"error");
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
    private final LogWritter logWritter=new LogWritter(this.getClass());

    public ClientHandler(SSLSocket socket,Tracker tracker){
        this.socket=socket;
        this.tracker=tracker;
        inet=socket.getInetAddress();
        ip=inet.getHostAddress();
        try {
            dis=new DataInputStream(socket.getInputStream());
            dos=new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            logWritter.writeLog("failed to attach stream to socket---"+e.getMessage(),"error");
        }
        t=new Thread(this);
        t.start();
    }
    public void run(){
        Formatter formatter = new Formatter();
        try {
            //receive client request to give service
            message=dis.readUTF();
            switch (message) {
                case "server":
                    tracker.addToList(ip);//if message=server register as active server
                    formatter.format("%14s %14s %17s\n", ip, inet.getHostName(), "register service");
                    break;
                case "rm" :
                    tracker.removeFromList(ip);//if message=rm remove server from active servers list
                    formatter.format("%14s %14s %17s\n", ip, inet.getHostName(), "remove service");
                    break;
                case "iplist" :
                    tracker.getIpList(dos);//if message=iplist send active servers ip list to invoking machine
                    formatter.format("%14s %14s %17s\n", ip, inet.getHostName(), "request service");
                    break;
            }
            System.out.println(formatter);
            dos.flush();
        } catch (IOException e) {
           logWritter.writeLog("failed to receive message from client---"+e.getMessage(),"error");
        }finally {
            try {
                dis.close();;
                dos.close();
            } catch (IOException e) {
                logWritter.writeLog(e.getMessage(),"eror");
            }
        }
    }
}
@Command(name="tracker",description = "start tracker server", mixinStandardHelpOptions = true)
public class Server implements Runnable{

    private static SSLServerSocket server;
    private static SSLSocket socket;
    private static InetAddress inet;
    private final LogWritter logWritter=new LogWritter(this.getClass());

    @Option(names={"-p","--port"},description="default port is 5000")
    private static int PORT=5000;

    private SSLServerSocket createServerSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket = null;
        try {
            serverSocket = (SSLServerSocket) serverFactory.createServerSocket(PORT);
            serverSocket.setEnabledCipherSuites(CIPHERS);
            serverSocket.setEnableSessionCreation(true);
            logWritter.writeLog("tracker-server start listening on port "+PORT,"info");
        } catch (IOException e1) {
            logWritter.writeLog("failed to create server on port"+PORT+"---"+e1.getMessage(),"error");
        }
        return serverSocket;
    }
    //Run server and handle client requests
    public void runServer(){
        try {
            //server start listening
            Tracker tracker=new Tracker();
            //prepare table to track client request
            Formatter fmt = new Formatter();
            System.out.println(fmt.format("%15s %15s %15s\n", "Ip", "Host Name", "Service"));
            while(true){
                socket=(SSLSocket) server.accept();
                inet=socket.getInetAddress();
                new ClientHandler(socket,tracker);
            }
        } catch (IOException e) {
           logWritter.writeLog("failed to connect with client---"+e.getMessage(),"error");
        }

    }
    public void run(){
        //creating server
        server=createServerSocket();
        runServer();
    }


}
