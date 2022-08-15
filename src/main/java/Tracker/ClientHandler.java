package Tracker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Formatter;

import javax.net.ssl.SSLSocket;

import LogWritter.LogWriter;

public class ClientHandler implements Runnable{

    private final SSLSocket socket;
    private final Tracker tracker;
    private DataInputStream dis;
    private DataOutputStream dos;
    private final Thread t;
    private String message;
    private final String ip;
    private final InetAddress inet;
    private final LogWriter logWritter=new LogWriter(this.getClass());

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
                dis.close();
                dos.close();
            } catch (IOException e) {
                logWritter.writeLog(e.getMessage(),"eror");
            }
        }
    }
}
