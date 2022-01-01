package Main.Client;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
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
    private PrintWriter writer;
    private final int index;
    private final long size;

    public ServerHandler(String ip,long start,long end,long size,int index){
        this.ip=ip;
        socket=createSocket(ip);
        this.start=start;
        this.end=end;
        dis=null;
        writer=null;
        try {
            dis=new DataInputStream(socket.getInputStream());
            writer=new PrintWriter(socket.getOutputStream());
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
        System.out.println("waiting for client....");
        return socket;
    }
    public static String getFileName(String url,int index) {
        return index+"-"+url.substring(url.lastIndexOf("/")+1);
    }
    public void run(){
        //sending download information to servers
        System.out.println("sending file information to servers");
        writer.write(Client.strUrl);
        //writer.write(String.valueOf(size));
        writer.write(String.valueOf(start));
        writer.write(String.valueOf(end));
        writer.flush();

        RandomAccessFile file=null;
        byte[] buffer=new byte[8*1024];
        int b;
        try {
           file=new RandomAccessFile(getFileName(Client.strUrl,index),"rw");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //start receiving chunks from servers
        System.out.println("recieving chunk from "+ip);
        try{
            while((b=dis.read(buffer))!=-1) {
                //bop.write(buffer,0,b);
                file.write(buffer, 0, b);
                buffer=new byte[8*1024];
            }
            System.out.println("File completely received!");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            dis.close();
            writer.close();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

class Distributer{

    protected static final Map<String,String> serverChunkMap=new LinkedHashMap<>();
    private final int PORT=5001;
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

    private final SSLSocket socket;
    private final InetAddress inet;
    private static BufferedReader reader;
    private static PrintWriter writer;
    private final String TRACKER_IP="";
    private final int TRACKER_PORT=5000;
    protected static final int SERVER_PORT=5001;
    protected static final HashSet<String> tempIpList=new HashSet<>();
    private static  URL url=null;
    private static HttpURLConnection connection=null;
    private static String message;
    protected static String strUrl;
    protected static String fileName;
    protected static long downloadSize;


    public Client(){
        //connecting to tracker server
        socket=createSocket();
        inet=socket.getInetAddress();
        reader=null;
        writer=null;
        try {
            reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer=new PrintWriter(socket.getOutputStream(),true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private SSLSocket createSocket() {
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
        System.out.println("waiting for client....");
        return socket;
    }
    private static boolean requestIPList(){
        //requesting service from tracker server
        writer.write("iplist");
        //receiving active servers list
        try {
            if(!message.equals("null")){
                while(!(message=reader.readLine()).equals("fin")){
                    tempIpList.add(message);
                }
                return true;
            }else{
                System.out.println("there is no active server proceed to download by it self");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    public static void main(String[] args) {
        Distributer distributer=new Distributer();
        strUrl="";
        try {
           url=new URL(strUrl);
           connection=(HttpURLConnection) url.openConnection();
           downloadSize=connection.getContentLengthLong();
           fileName=url.getFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //check if there is active servers if not download by it self

        if (requestIPList()) {
            System.out.println("Downloading by it self");
            download(0,downloadSize);
        }else{
            distributer.distribute();
        }
    }
    private static void download(long start,long end) {

        System.out.println("download started");
        RandomAccessFile file=null;
        File tempFile;
        InputStream input=null;
        long size;
        long actualSize;
        int MAX_BUFFER_SIZE=8*1024;//8KB
        long downloaded=start;
        long actualDownloaded=downloaded-start;

        try {
            connection=(HttpURLConnection)url.openConnection();
            connection.setRequestProperty("Range", "bytes"+downloaded+"-");
            connection.connect();
            size=connection.getContentLength();
            actualSize=end-start;
            tempFile=new File(fileName);
            file=new RandomAccessFile(tempFile,"rw");
            input = connection.getInputStream();

            input.skip(downloaded);

            System.out.println("Start:"+start+" End:"+end+" downloded:"+downloaded);

            while ( downloaded<=end) {
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
        System.out.println("Thread completed");
        connection.disconnect();
    }

}
