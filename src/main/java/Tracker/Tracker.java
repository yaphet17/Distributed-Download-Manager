package Tracker;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import LogWritter.LogWriter;

public class Tracker {
    private static final LogWriter logWriter = new LogWriter(Tracker.class);
    public static Set<String> clientList = clientList = new HashSet<>();;
    synchronized public void addToList(String ip) {
        clientList.add(ip);
    }

    synchronized public void removeFromList(String ip) {
        clientList.remove(ip);
    }

    synchronized public void getIpList(DataOutputStream dos) {
        try {
            if (clientList.size() == 0) {
                dos.writeUTF("null");//notify requesting client there is no active server
                return;
            }
            for (String ip : clientList) {
                dos.writeUTF(ip);
            }
        } catch (IOException e) {
            logWriter.writeLog("error to send active server list to client---" + e.getMessage(), "error");
        }
    }

}

