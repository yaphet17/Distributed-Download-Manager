package Main.Client;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

class ServerHandler implements Runnable{
    private final SSLSocket socket;
    private final String ip;
    private final long start;
    private final long end;
    private DataInputStream dis;
    private DataOutputStream dos;
    private final int index;
    private final long size;

    public ServerHandler(String ip,long start,long end,long size,int index){
        this.ip=ip;
        socket=createSocket(ip);
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
    public static String getFileName(String url,int index) {
        return index+"-"+url.substring(url.lastIndexOf("/")+1);
    }
    public void run(){
        RandomAccessFile file=null;
        byte[] buffer=new byte[8*1024];
        int b;
        try {
            //sending download information to servers
            System.out.println("sending file information to servers");
            dos.writeUTF(Client.strUrl);
            dos.writeUTF(String.valueOf(start));
            dos.writeUTF(String.valueOf(end));
            file=new RandomAccessFile(getFileName(Client.strUrl,index),"rw");
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //start receiving chunks from servers
        System.out.println("recieving chunk from "+ip);
        try{
            while((b=dis.read(buffer))!=-1) {
                file.write(buffer, 0, b);
                buffer=new byte[8*1024];
            }
            System.out.println("File completely received!");
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                file.close();
                dis.close();
                dos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }
}

class Distributer{

    protected static final Map<String,String> serverChunkMap=new LinkedHashMap<>();
    public Distributer(){
    }


    public void distribute(){
        int noServer=Client.tempIpList.size();
        long fileSize=Client.downloadSize;
        long chunkSize=fileSize/noServer;
        int remainder=(int)(fileSize-(noServer*chunkSize));
        long start;
        long end=chunkSize;
        int index=2;
        //Client.download(start,end+remainder);
        start=end+remainder;
        end=start+chunkSize;
        for(String ip:Client.tempIpList){
            new ServerHandler(ip,start,end,fileSize,index);
            serverChunkMap.put(ip,start+"-"+end);
            start=end;
            end+=chunkSize;
            index++;
        }
    }


}
public class Client {

    private static SSLSocket socket;
    private static InetAddress inet;
    private static DataInputStream dis;
    private static DataOutputStream dos;
    private static final String TRACKER_IP="192.168.0.140";
    private static final int TRACKER_PORT=5000;
    protected static final int SERVER_PORT=5001;
    protected static final HashSet<String> tempIpList=new HashSet<>();
    private static  URL url=null;
    private static HttpURLConnection connection=null;
    private static String message;
    protected static String strUrl="https://file-examples-com.github.io/uploads/2017/04/file_example_MP4_1280_10MG.mp4";
    protected static String fileName;
    protected static long size;
    protected static long downloadSize;


    public Client(){
        //connecting to tracker server

    }

    private static SSLSocket createSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
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
        }catch(IOException e){
            e.printStackTrace();
        }
        System.out.println("request sent");
        //receiving active servers list
        try {

            while(!(message=dis.readUTF()).equals("fin")){
                System.out.println("start recieving ip list");
                if(!message.equals("null")){
                    System.out.println("no server available");
                    break;
                }
                tempIpList.add(message);
                System.out.println(message);
            }
            System.out.println("active servers available");
            return true;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static String getSizeProgress(long downloaded){
        return downloaded/1048576+"MB";
    }
    private static int getPercentProgress(long downloaded,long size){
        return (int)((downloaded/size)*100);
    }
    private static void showProgress(String sizeProgress,int percentProgress){
        for(int i=percentProgress;i<=100;i++){
            System.out.print("\rDownloading|");
            for(int j=0;j<=i;j++){
                System.out.print("#");
            }
            for(int k=i;k<100;k++){
                System.out.print(" ");
            }
            System.out.print("|"+i+"% "+sizeProgress+"MB");

        }
    }
    private static void download() {

        System.out.println("download started");
        RandomAccessFile file=null;
        File tempFile;
        InputStream input=null;
        int MAX_BUFFER_SIZE=8*1024;//8KB
        long downloaded=0;

        try {
            connection=(HttpURLConnection)url.openConnection();
            connection.setRequestProperty("Range", "bytes"+downloaded+"-");
            connection.connect();
            size=connection.getContentLength();
            tempFile=new File(fileName);
            file=new RandomAccessFile(tempFile,"rw");
            input = connection.getInputStream();
            input.skip(downloaded);
            while ( downloaded!=size) {
                byte[] buffer;

                if (size - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    int diff=(int)(size-downloaded);
                    buffer = new byte[diff];
                }
                int read = input.read(buffer);
                if (read == -1) {
                    break;
                }
                //write bytes to the file
                file.write(buffer, 0, read);
                downloaded += read;
                showProgress(getSizeProgress(downloaded),getPercentProgress(downloaded,size));

            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
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
        System.out.print("\rdownloaded completed");
        connection.disconnect();
    }
    private static void download(long start,long end) {

        System.out.println("download started");
        RandomAccessFile file=null;
        File tempFile;
        InputStream input=null;
        long actualSize;
        int MAX_BUFFER_SIZE=8*1024;//8KB
        long downloaded=start;
        long actualDownloaded;

        try {
            connection=(HttpURLConnection)url.openConnection();
            connection.setRequestProperty("Range", "bytes"+downloaded+"-");
            connection.connect();
            actualSize=end-start;
            tempFile=new File(fileName);
            file=new RandomAccessFile(tempFile,"rw");
            input = connection.getInputStream();

            input.skip(downloaded);

            System.out.println("Start:"+start+" End:"+end+" downloded:"+downloaded);

            while (downloaded<=end) {
                byte[] buffer;

                if (size - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    int diff=(int)(size-downloaded);
                    buffer = new byte[diff];
                }
                int read = input.read(buffer);
                if (read == -1) {
                    break;
                }
                //write bytes to the file
                file.write(buffer, 0, read);
                downloaded += read;
                actualDownloaded=downloaded-start;
                showProgress(getSizeProgress(actualDownloaded),getPercentProgress(actualDownloaded,actualSize));

            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
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
        System.out.println("\rdownloaded completed");
        connection.disconnect();
    }
    public static void main(String[] args) {
        Distributer distributer=new Distributer();
        socket=createSocket();
        inet=socket.getInetAddress();
        try {
            dis=new DataInputStream(socket.getInputStream());
            dos=new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            url=new URL(strUrl);
            connection=(HttpURLConnection) url.openConnection();
            downloadSize=connection.getContentLengthLong();
            fileName=url.getFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //check if there is active servers if not download by it self
        System.out.println("requesting active servers");
        if (requestIPList()) {
            System.out.println("Downloading by it self");
            download(0,downloadSize);
        }else{
            System.out.println("distributing");
            distributer.distribute();
        }
    }

}
