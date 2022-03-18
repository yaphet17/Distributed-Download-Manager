package Client;

import LogWritter.LogWritter;
import me.tongfei.progressbar.ProgressBar;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;

import static picocli.CommandLine.*;

class Downloader implements Runnable{

    private long start;
    private long end;
    private int index;
    private boolean self;
    private Thread t;
    private long actualSize;
    private LogWritter logWritter=new LogWritter(this.getClass());


    public Downloader(long start,long end,int index,boolean self) {
        this.start=start;
        this.end=end;
        this.index=index;
        this.self=self;
        t=new Thread(this);
        t.start();
    }
    public String getFileName(String url) {
        if(url.contains("?")){
            String temp=url.substring(url.lastIndexOf("/")+1);
            return temp.substring(temp.lastIndexOf("?")+1);
        }
        return url.substring(url.lastIndexOf("/")+1);
    }
    public String getFolderName(String fileName) {
        return fileName.substring(0,fileName.lastIndexOf("."));
    }
    public long getPercentage(long i){
        return (i*100)/actualSize;
    }
    public void run() {
        logWritter.writeLog("download started","info");
        RandomAccessFile file=null;
        File tempFile;
        InputStream input=null;
        int MAX_BUFFER_SIZE=8*1024;//8KB
        long downloaded=start;
        long actualDownloaded=0;
        boolean isSuccessful=false;
        actualSize=end-start;
        ProgressBar pb = null;
        try {
            String fileName=getFileName(Distributer.fileName);
            String folderName="client-temp-files";
            //all client files goes here
            File mainFolder=new File(folderName);
            //create folder if it doesn't exist
            if(!mainFolder.exists()){
                if(mainFolder.mkdir()){
                    logWritter.writeLog("folder doesn't exist---folder "+folderName+" is created","warn");
                }else{
                    logWritter.writeLog("folder doesn't exist---failed to create "+folderName,"error");
                }
            }
            //files of specific download goes here
            File targetFolder=new File(mainFolder.getName()+"/"+getFolderName(fileName));
            //create folder if it doesn't exist
            if(!targetFolder.exists()){
                if(targetFolder.mkdir()){
                    logWritter.writeLog("folder doesn't exist---folder "+targetFolder.getName()+" is created","warn");
                }else{
                    logWritter.writeLog("folder doesn't exist---failed to create "+targetFolder.getName(),"error");
                }
            }
            //if the download is standalone download omit index from file name or append if it is distributed
            if(self){
                tempFile=new File(mainFolder.getName()+"/"+targetFolder.getName()+"/"+fileName);
            }else {
                tempFile=new File(mainFolder.getName()+"/"+targetFolder.getName()+"/"+index+"-"+fileName);
            }
            file=new RandomAccessFile(tempFile,"rw");
            input = Distributer.connection.getInputStream();
            //skipping some bytes
            input.skip(downloaded);
            byte[] buffer;
            int read;
            //command line progress bar to indicate download
            pb=new ProgressBar("Downloading", actualSize);
            while (downloaded<end) {
                if (end - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    int diff=(int)(end-downloaded);
                    buffer = new byte[diff];
                }
                read = input.read(buffer);
                if (read == -1) {
                    break;
                }
                //write bytes to the file
                file.write(buffer, 0, read);
                downloaded += read;
                actualDownloaded=downloaded-start;
                pb.stepTo(actualDownloaded);
            }
            isSuccessful= actualDownloaded == actualSize;
            //if download completed successfully stream the chunk back to client
            if(isSuccessful) {
                pb.setExtraMessage("Completed");
                logWritter.writeLog("download successfully completed","info");
            }
        } catch (ConnectException e) {
            logWritter.writeLog("connection timeout while downloading---"+e.getMessage(),"error");
        } catch (FileNotFoundException e) {
          logWritter.writeLog("file not found","error");
        } catch (IOException e) {
           logWritter.writeLog(e.getMessage(),"error");
        } finally {
            if(file!=null) {
                try {
                    file.close();
                } catch (IOException e) {
                    logWritter.writeLog(e.getMessage(),"error");
                }
            }
            if(input!=null) {
                try {
                    input.close();
                } catch (IOException e) {
                    logWritter.writeLog(e.getMessage(),"error");
                }
            }
        }
        Client.addSuccessfulDownload(start+"-"+end);
        //if all downloads are completed start assembling and disconnect from remote server
        if(Client.getSuccessfulDownloadList().size()==Distributer.noServer+1&&!self){
            new Assembler().assemble();
            Distributer.connection.disconnect();
        }else{
            Client.addFreeServer("localhost");
        }
        //close client thread if the download is standalone and the completed
        if(self&&isSuccessful){
            Client.closeClient();
        }
    }


}
class Assembler{
    private final LogWritter logWritter=new LogWritter(this.getClass());

    public String getFileName(String url) {
        if(url.contains("?")){
            String temp=url.substring(url.lastIndexOf("/")+1);
            return temp.substring(temp.lastIndexOf("?")+1);
        }
        return url.substring(url.lastIndexOf("/")+1);
    }
    public void assemble() {
        logWritter.writeLog("assembling files","info");
        File[] fileList;
        FileInputStream fis=null;
        BufferedInputStream bis=null;
        String fileName=getFileName(Distributer.fileName);
        String targetFolderName="Downloads";
        File targetFolder=new File(targetFolderName);
        //create download folder if it doesn't exist
        if(!targetFolder.exists()){
            if(targetFolder.mkdir()){
                logWritter.writeLog("folder doesn't exist---folder "+targetFolderName+" is created","warn");
            }else{
                logWritter.writeLog("folder doesn't exist---failed to create "+targetFolderName,"error");
            }

        }
        File targetFile=new File(targetFolder.getName()+"/"+fileName);
        String folderName="client-temp-files/"+fileName.substring(0,fileName.lastIndexOf("."));
        File folder=new File(folderName);
        try {
            fileList=folder.listFiles();
            FileOutputStream fos=new FileOutputStream(targetFile);
            BufferedOutputStream bos=new BufferedOutputStream(fos);
            Arrays.sort(fileList,(a,b)->a.getName().compareTo(b.getName()));
            byte[] buffer=new byte[16*1024];
            int r;
            for(File f:fileList){
                fis=new FileInputStream(f);
                bis=new BufferedInputStream(fis);
                while((r=bis.read(buffer))!=-1){
                    bos.write(buffer,0,r);
                    buffer=new byte[16*1024];
                }
                fos.flush();
                bos.flush();
                //f.delete();
            }
            fos.close();
            bos.close();
            fis.close();
            bis.close();
        } catch (FileNotFoundException e) {
                logWritter.writeLog("file not found---"+e.getMessage(),"error");
        } catch (IOException e) {
            logWritter.writeLog(e.getMessage(),"error");
        }
        logWritter.writeLog("files successfully assembled","info");
        //close client thread
        Client.closeClient();
    }
}

class ServerHandler implements Runnable{
    private final SSLSocket socket;
    private final InetAddress inet;
    private final String ip;
    private final long start;
    private final long end;
    private DataInputStream dis;
    private DataOutputStream dos;
    private final int index;
    private final long size;
    private final Thread t;
    private final LogWritter logWritter=new LogWritter(this.getClass());

    public ServerHandler(String ip,long start,long end,long size,int index){
        this.ip=ip;
        socket=createSocket(ip);
        inet=socket.getInetAddress();
        this.start=start;
        this.end=end;
        dis=null;
        dos=null;
        try {
            dis=new DataInputStream(socket.getInputStream());
            dos=new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            logWritter.writeLog("failed to attach stream to socket---"+e.getMessage(),"error");
        }
        this.size=size;
        this.index=index;
        t=new Thread(this);
        t.start();

    }
    private SSLSocket createSocket(String ip) {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = null;
        try {
            socket = (SSLSocket) socketFactory.createSocket(ip,Client.SERVER_PORT);
            socket.setEnabledCipherSuites(CIPHERS);
            socket.setEnableSessionCreation(true);
            logWritter.writeLog("client socket created with address "+ip+":"+Client.SERVER_PORT,"info");
        } catch (IOException e1) {
            logWritter.writeLog("failed to create server with address"+ip+":"+Client.SERVER_PORT+"---"+e1.getMessage(),"error");
        }
        return socket;
    }
    public String getFileName(String url,int index) {
        if(url.contains("?")){
            String temp=url.substring(url.lastIndexOf("/")+1);
            return index+"-"+temp.substring(temp.lastIndexOf("?")+1);
        }
        return index+"-"+url.substring(url.lastIndexOf("/")+1);

    }
    public static String getFolderName(String fileName) {
        return fileName.substring(0,fileName.lastIndexOf("."));
    }
    private void successfulDownload(){
        Client.addSuccessfulDownload(start+"-"+end);
        //if all downloads are completed start assembling
        if(Client.getSuccessfulDownloadList().size()==Distributer.noServer+1){
            logWritter.writeLog("file successfully receivedfrom "+ip,"info");
            new Assembler().assemble();
            return;
        }
        if(!Client.getFailedDownloadList().isEmpty()){//if download is successful and there is any failed download redistribute
            logWritter.writeLog("redistributing chunk","warn");
            Distributer.redistribute();
        }

    }
    private void failedDownload(){
        logWritter.writeLog("download failed for server "+ip,"warn");
        Client.addFailedDownload(start+"-"+end+"-"+index);
        //if download failed and there is any free server redistribute
        if(!Client.getFreeServerList().isEmpty()){
            logWritter.writeLog("redistributing chunk","warn");
            Distributer.redistribute();
        }
    }
    public void run(){
        RandomAccessFile file=null;
        byte[] buffer=new byte[16*1024];
        int b;
        try {
            //sending download information to servers
            dos.writeUTF(Client.strUrl);
            dos.writeUTF(String.valueOf(start));
            dos.writeUTF(String.valueOf(end));
            logWritter.writeLog("file information sent to "+inet.getHostAddress(),"");
            String fileName=getFileName(Client.strUrl,index);
            String folderName="client-temp-files/"+getFolderName(fileName.substring(2));
            File folder=new File(folderName);
            if(!folder.exists()){
               if(folder.mkdir()){
                   logWritter.writeLog("folder doesn't exist---folder "+folderName+" is created","warn");
               }else{
                   logWritter.writeLog("folder doesn't exist---failed to create folder "+folderName,"error");
               }
            }
            file=new RandomAccessFile(folderName+"/"+fileName,"rw");
            //start receiving chunks from servers
            int status;
            status=dis.read();
            //create new progressbar to indicate the progress of streaming files to client
            ProgressBar pb=new ProgressBar("Receiving",file.length());
            long bytes=0;
            if(status==1) {
                while ((b = dis.read(buffer)) != -1) {
                    file.write(buffer, 0, b);
                    buffer = new byte[16 * 1024];
                    bytes+=buffer.length;
                    pb.stepTo(bytes);
                }
                pb.setExtraMessage("Completed");
                successfulDownload();
            }else{
                failedDownload();
            }
        } catch (IOException e) {
           failedDownload();
        }finally {
            try {
                dos.flush();
                dis.close();
                dos.close();
            } catch (IOException e) {
                logWritter.writeLog(e.getMessage(),"error");
            }
            logWritter.writeLog("session closed for server "+ip,"info");
        }


    }
}

class Distributer{

    protected static final Map<String,String> serverChunkMap=new LinkedHashMap<>();
    private static  URL url=null;
    protected static HttpURLConnection connection=null;
    protected static String strUrl;
    public static String fileName;
    private static long downloadSize;
    protected static int noServer;
    private Thread t;
    private final LogWritter logWritter=new LogWritter(this.getClass());
    private int retryCount=0;
    public Distributer(String strUrl){
        this.strUrl=strUrl;
        if(initialize()){
            new Downloader(0,downloadSize,1,true);
        }
    }
    public Distributer(String strUrl,int noServer){
        this.strUrl=strUrl;
        this.noServer=noServer;
        if(initialize()){
          distribute();
        }

    }

    private boolean initialize(){
        try {
            url=new URL(strUrl);
            connection=(HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setRequestProperty("Range", "bytes"+0+"-");
            downloadSize=connection.getContentLengthLong();
            fileName=url.getFile();
            if(connection.getResponseCode()/100!=2) {
                logWritter.writeLog("remote server returned HTTP response code "+connection.getResponseCode(),"error");
                return false;
            }
            connection.connect();
        } catch (IOException e) {
            logWritter.writeLog("connection timeout","warn");
            if(retryCount<=10){
                retryCount++;
                initialize();
            }
        }
        return true;
    }

    public void distribute(){
        long chunkSize=downloadSize/(noServer+1);
        int remainder=(int)(downloadSize-((noServer+1)*chunkSize));
        long start=0;
        long end=chunkSize+remainder;
        int index=1;
        new Downloader(start,end,index++,false);
        logWritter.writeLog("chunk boundary for self {"+start+"-"+end+"}","info");
        for(String ip:Client.tempIpList){
            start=end;
            end+=chunkSize;
            logWritter.writeLog("chunk boundary for "+ip+" {"+start+"-"+end+"}","info");
            new ServerHandler(ip,start,end,downloadSize,index);
            serverChunkMap.put(ip,start+"-"+end);
            index++;
        }
    }
    public static void redistribute(){
        //number of failed download
        int n= Math.min(Client.getFailedDownloadList().size(), Client.getFreeServerList().size());//get minimum size
        long start,end;
        int index;
        String ip;
        String[] chunkInfo;
        for(int i=0;i<n;i++){
            chunkInfo=Client.getFailedDownload(i).split("-");
            start=Long.parseLong(chunkInfo[0]);
            end=Long.parseLong(chunkInfo[1]);
            index=Integer.parseInt(chunkInfo[2]);
            ip=Client.getFreeServer(i);
            if(ip.equals("localhost")||Client.getFreeServer(i).equals("127.0.0.1")){
                new Downloader(start,end,index,false);
            }else{
                new ServerHandler(ip,start,end,downloadSize,index);
            }
            Client.removeFreeServer(ip);//remove server from free server list once download is assigned
            Client.removeFailedDownload(Client.getFailedDownload(i));//remove chunk info from failed download list once server is assigned

        }

    }

}
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
    private final LogWritter logWritter=new LogWritter(this.getClass());

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
            logWritter.writeLog("client socket create with address "+TRACKER_IP+":"+TRACKER_IP,"info");
            socket.setEnabledCipherSuites(CIPHERS);
            socket.setEnableSessionCreation(true);
        } catch (IOException e1) {
            logWritter.writeLog("failed to connect with tracker---"+e1.getMessage(),"error");
            return null;
        }
        return socket;
    }
    private boolean requestIPList(){
        //requesting service from tracker server
        logWritter.writeLog("sending request to tracker for active servers list","info");
        try{
            dos.writeUTF("iplist");
        }catch(IOException e){
           logWritter.writeLog("failed to send request to tracker---"+e.getMessage(),"error");
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
            logWritter.writeLog("failed to fetch active servers list---"+e.getMessage(),"error");
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
            logWritter.writeLog("can't connect to the tracker continue as standalone download","warn");
            new Distributer(strUrl);
        }else{
            inet=socket.getInetAddress();
            try {
                dis=new DataInputStream(socket.getInputStream());
                dos=new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                logWritter.writeLog("failed to attach stream to socket---"+e.getMessage(),"error");
            }
            //check if there is active servers if not download by it self
            if (!requestIPList()) {
                logWritter.writeLog("no active server available continue as standalone download","warn");
                new Distributer(strUrl);
            }else{
                logWritter.writeLog("active servers available","info");
                new Distributer(strUrl,tempIpList.size());
            }

        }

        while (!isCompleted){

        }
        logWritter.writeLog("session closed","info");
    }

}
