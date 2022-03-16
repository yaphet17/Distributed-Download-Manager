package Client;

import me.tongfei.progressbar.ProgressBar;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

class Downloader implements Runnable{

    private long start;
    private long end;
    private int index;
    private boolean self;
    private Thread t;
    private long actualSize;


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
        return (100-((actualSize-i)*100)/actualSize);
    }
    public void run() {
        System.out.println("download started");
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
            String folderName="../../../client-temp-files/"+getFolderName(fileName);
            File folder=new File(folderName);
            if(!folder.exists()){
                folder.mkdir();
                System.out.println("folder "+folderName+" created");
            }
            tempFile=new File(folderName+"/"+index+"-"+fileName);
            file=new RandomAccessFile(tempFile,"rw");
            input = Distributer.connection.getInputStream();
            //skipping some bytes
            input.skip(downloaded);
            System.out.println("Start:"+start+" End:"+end+" downloded:"+downloaded);
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
                pb.stepBy(getPercentage(actualDownloaded));
            }
            isSuccessful= actualDownloaded == actualSize;
            //if download completed succesfully stream the chunk back to client
            if(isSuccessful) {
                pb.stepTo(100);
                pb.setExtraMessage("Completed");
            }
        } catch (ConnectException e) {
            System.out.println("connection timeout");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(file!=null) {
                try {
                    file.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(input!=null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
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
        System.out.println("download completed");
    }


}
class Assembler{

    public String getFileName(String url) {
        if(url.contains("?")){
            String temp=url.substring(url.lastIndexOf("/")+1);
            return temp.substring(temp.lastIndexOf("?")+1);
        }
        return url.substring(url.lastIndexOf("/")+1);
    }
    public void assemble() {
        File[] fileList;
        FileInputStream fis=null;
        BufferedInputStream bis=null;
        String fileName=getFileName(Distributer.fileName);
        System.out.println("assembling..");
        String targetFolderName="../../../Downloads";
        File targetFolder=new File(targetFolderName);
        //create download folder if it doesn't exist
        if(!targetFolder.exists()){
            targetFolder.mkdir();
            System.out.println("folder "+targetFolder.getName()+" created");
        }
        File targetFile=new File(targetFolder.getName()+"/"+fileName);
        String folderName="../../../client-temp-files/"+fileName.substring(0,fileName.lastIndexOf("."));
        File folder=new File(folderName);
        try {
            fileList=folder.listFiles();
            FileOutputStream fos=new FileOutputStream(targetFile);
            BufferedOutputStream bos=new BufferedOutputStream(fos);
            Arrays.sort(fileList,(a,b)->a.getName().compareTo(b.getName()));
            byte[] buffer=new byte[16*1024];
            int r;

            for(File f:fileList){
                System.out.println("assemble file "+f.getName());
                fis=new FileInputStream(f);
                bis=new BufferedInputStream(fis);
                while((r=bis.read(buffer))!=-1){
                    bos.write(buffer,0,r);
                    buffer=new byte[16*1024];
                }
                fos.flush();
                bos.flush();
            }
            fos.close();
            bos.close();
            fis.close();
            bis.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("successfully assembled");
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
            e.printStackTrace();
        }
        this.size=size;
        this.index=index;
        t=new Thread(this);
        t.start();

    }
    private SSLSocket createSocket(String ip) {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        //java.security.Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = null;
        try {
            System.out.println("creating a client socket....");
            socket = (SSLSocket) socketFactory.createSocket(ip,Client.SERVER_PORT);
            System.out.println("client socket created");
            socket.setEnabledCipherSuites(CIPHERS);
            socket.setEnableSessionCreation(true);
        } catch (IOException e1) {
            e1.printStackTrace();
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
            new Assembler().assemble();
            return;
        }
        if(!Client.getFailedDownloadList().isEmpty()){//if download is successful and there is any failed download redistribute
            Distributer.redistribute();
        }
        System.out.println("File completely received!");
    }
    private void failedDownload(){
        Client.addFailedDownload(start+"-"+end+"-"+index);
        //if download failed and there is any free server redistribute
        if(!Client.getFreeServerList().isEmpty()){
            Distributer.redistribute();
        }
    }
    public void run(){

        RandomAccessFile file=null;
        byte[] buffer=new byte[16*1024];
        int b;
        try {
            //sending download information to servers
            System.out.println("sending file information to servers");
            dos.writeUTF(Client.strUrl);
            System.out.println("Url sent");
            dos.writeUTF(String.valueOf(start));
            dos.writeUTF(String.valueOf(end));
            System.out.println("Chunk boundary sent to: "+inet.getHostAddress());
            String fileName=getFileName(Client.strUrl,index);
            String folderName="../../../client-temp-files/"+getFolderName(fileName.substring(2));
            File folder=new File(folderName);
            if(!folder.exists()){
                folder.mkdir();
                System.out.println("folder "+folderName+" created");
            }
            file=new RandomAccessFile(folderName+"/"+fileName,"rw");
            //start receiving chunks from servers
            System.out.println("receiving chunk from "+ip);
            int status;
            status=dis.read();
            if(status==1) {
                while ((b = dis.read(buffer)) != -1) {
                    System.out.print("\rrecieving...");
                    file.write(buffer, 0, b);
                    buffer = new byte[16 * 1024];
                }
                successfulDownload();
            }else{
                failedDownload();
            }

        } catch (IOException e) {
           failedDownload();
            e.printStackTrace();
        }finally {
            try {
                dos.flush();
                //file.close();
                dis.close();
                dos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("session closed");
        }


    }
}

class Distributer{

    protected static final Map<String,String> serverChunkMap=new LinkedHashMap<>();
    private static  URL url=null;
    protected static HttpURLConnection connection=null;
    protected static String strUrl;
    //https://file-examples-com.github.io/uploads/2017/04/file_example_MP4_1280_10MG.mp4
    public static String fileName;
    private static long downloadSize;
    protected static int noServer;

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
                System.out.println("Remote server returned HTTP response code: "+connection.getResponseCode());
                return false;
            }
            connection.connect();
        } catch (IOException e) {
            System.out.println("connection time out");
            initialize();
        }
        return true;
    }

    public void distribute(){
        long chunkSize=downloadSize/(noServer+1);
        int remainder=(int)(downloadSize-((noServer+1)*chunkSize));
        long start=0;
        System.out.println(chunkSize);
        long end=chunkSize+remainder;
        int index=1;
        new Downloader(start,end,index++,false);
        System.out.println("Chunk boundary for:self "+start+"-"+end);
        for(String ip:Client.tempIpList){
            start=end;
            end+=chunkSize;
            System.out.println("Chunk boundary for server: "+ip+" "+start+"-"+end);
            new ServerHandler(ip,start,end,downloadSize,index);
            serverChunkMap.put(ip,start+"-"+end);
            index++;
        }
    }
    public static void redistribute(){
        System.out.println("redistributing chunk...");
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
                System.out.println("Chunk redistributed to self");
                new Downloader(start,end,index,false);
            }else{
                System.out.println("chunk redistributed to ip="+ip);
                new ServerHandler(ip,start,end,downloadSize,index);
            }
            Client.removeFreeServer(ip);//remove server from free server list once download is assigned
            Client.removeFailedDownload(Client.getFailedDownload(i));//remove chunk info from failed download list once server is assigned

        }

    }

}
public class Client {

    private static SSLSocket socket;
    private static InetAddress inet;
    private static DataInputStream dis;
    private static DataOutputStream dos;
    private static final String TRACKER_IP="localhost";
    private static final int TRACKER_PORT=5000;
    protected static final int SERVER_PORT=5001;
    protected static final HashSet<String> tempIpList=new HashSet<>();
    private static String message;
    protected static String strUrl="http://ipv4.download.thinkbroadband.com/20MB.zip";
    private static LinkedList<String> freeServerList=new LinkedList<>();
    private static LinkedList<String> failedDownloadList=new LinkedList<>();
    private static LinkedList<String> successfulDowloadList=new LinkedList<>();


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
    private static SSLSocket createSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        //"SSL_DH_anon_WITH_RC4_128_MD5"
        //java.security.Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = null;
        try {
            System.out.println("creating a client socket....");
            socket = (SSLSocket) socketFactory.createSocket(TRACKER_IP, TRACKER_PORT);
            System.out.println("client socket created");
            socket.setEnabledCipherSuites(CIPHERS);
            socket.setEnableSessionCreation(true);
        } catch (IOException e1) {
            System.out.println("can't connet to tracker:"+e1.getMessage());
            e1.printStackTrace();
        }
        return socket;
    }
    private static boolean requestIPList(){
        //requesting service from tracker server
        System.out.println("sending request");
        //writer.write("iplist");
        try{
            dos.writeUTF("iplist");
            System.out.println("request sent: iplist");
        }catch(IOException e){
            e.printStackTrace();
        }
        System.out.println("request sent");
        //receiving active servers list
        System.out.println("start recieving ip list");
        try {
            message=dis.readUTF();
            if(message.equals("null")){
                System.out.println("no server available");
                return false;
            }
            do{
                tempIpList.add(message);
                System.out.println(message);
                System.out.println("active servers available");
            }while(!(message=dis.readUTF()).equals("fin"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static void main(String[] args) {
        Distributer distributer;
        socket=createSocket();
        inet=socket.getInetAddress();
        try {
            dis=new DataInputStream(socket.getInputStream());
            dos=new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //check if there is active servers if not download by it self
        System.out.println("requesting active servers");
        if (!requestIPList()) {
            System.out.println("Downloading by it self");
            new Distributer(strUrl);
        }else{
            new Distributer(strUrl,tempIpList.size());

        }
    }

}
