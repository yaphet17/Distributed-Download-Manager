package Client;

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.LinkedList;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import LogWritter.LogWriter;
import picocli.CommandLine.Command;






@Command(name="client",description = "start downloading file", mixinStandardHelpOptions = true)
public class Client implements Runnable{

    private static SSLSocket socket;
    private static InetAddress inet;
    private static DataInputStream dis;
    private static DataOutputStream dos;
    protected static final HashSet<String> tempIpList=new HashSet<>();
    private static String message;
    private static LinkedList<String> freeServerList=new LinkedList<>();
    private static LinkedList<String> failedDownloadList=new LinkedList<>();
    private static LinkedList<String> successfulDowloadList=new LinkedList<>();
    public static boolean isCompleted=false;
    private final LogWriter logWriter =new LogWriter(this.getClass());

    @Parameters(paramLabel = "tracker-ip",description = "ip address of tracker server")
    private static  String TRACKER_IP=null;

    @Parameters(paramLabel = "url",description = "url of file to be downloaded")
    protected static String strUrl=null;

    @Option(names={"-tp","--trackerport"})
    private static int TRACKER_PORT=5000;

    @Option(names={"-sp","--serverport"})
    protected static int SERVER_PORT=5001;


    public static LinkedList<String> getFreeServerList(){
        return freeServerList;
    }
    public static String getFreeServer(int i){
        return freeServerList.get(i);
    }
    public static void addFreeServer(String ip){
        freeServerList.add(ip);
    }
    public static void removeFreeServer(String ip){
        freeServerList.remove(ip);
    }
    public static LinkedList<String> getFailedDownloadList(){
        return failedDownloadList;
    }
    public static String getFailedDownload(int i){
        return failedDownloadList.get(i);
    }
    public static void addFailedDownload(String chunkInfo){
        failedDownloadList.add(chunkInfo);
    }
    public static void removeFailedDownload(String chunkInfo){
        failedDownloadList.remove(chunkInfo);
    }
    public static LinkedList<String> getSuccessfulDownloadList(){
        return successfulDowloadList;
    }
    public static String getSuccessfulDownload(int i){
        return successfulDowloadList.get(i);
    }
    public static void addSuccessfulDownload(String chunkInfo){
        successfulDowloadList.add(chunkInfo);
    }
    public static void removeSuccessfulDownload(String chunkInfo){
        successfulDowloadList.remove(chunkInfo);
    }
    private SSLSocket createSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = null;
        try {
            socket = (SSLSocket) socketFactory.createSocket(TRACKER_IP, TRACKER_PORT);
            logWriter.writeLog("client socket create with address "+TRACKER_IP+":"+TRACKER_IP,"info");
            socket.setEnabledCipherSuites(CIPHERS);
            socket.setEnableSessionCreation(true);
        } catch (IOException e1) {
            logWriter.writeLog("failed to connect with tracker---"+e1.getMessage(),"error");
            return null;
        }
        return socket;
    }
    private boolean requestIPList(){
        //requesting service from tracker server
        logWriter.writeLog("sending request to tracker for active servers list","info");
        try{
            dos.writeUTF("iplist");
        }catch(IOException e){
            logWriter.writeLog("failed to send request to tracker---"+e.getMessage(),"error");
        }
        //receiving active servers list
        try {
            message=dis.readUTF();
            if(message.equals("null")){
                return false;
            }
            do{
                tempIpList.add(message);
            }while(!(message=dis.readUTF()).equals("fin"));
        } catch (IOException e) {
            logWriter.writeLog("failed to fetch active servers list---"+e.getMessage(),"error");
        }
        return true;
    }

    public static void closeClient(){
        isCompleted=true;
    }
    public void run(){
        Distributer distributer;
        socket=createSocket();
        if(socket==null){
            logWriter.writeLog("can't connect to the tracker continue as standalone download","warn");
            new Distributer(strUrl);
        }else{
            inet=socket.getInetAddress();
            try {
                dis=new DataInputStream(socket.getInputStream());
                dos=new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                logWriter.writeLog("failed to attach stream to socket---"+e.getMessage(),"error");
            }
            //check if there is active servers if not download by it self
            if (!requestIPList()) {
                logWriter.writeLog("no active server available continue as standalone download","warn");
                new Distributer(strUrl);
            }else{
                logWriter.writeLog("active servers available","info");
                new Distributer(strUrl,tempIpList.size());
            }

        }

        while (!isCompleted){

        }
        logWriter.writeLog("session closed","info");
    }

}