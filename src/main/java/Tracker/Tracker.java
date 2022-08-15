package Tracker;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;

import LogWritter.LogWriter;

public class Tracker {
    public static HashSet<String> clientList;
    private final LogWriter logWritter=new LogWriter(this.getClass());

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

