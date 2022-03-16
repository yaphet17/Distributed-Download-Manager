package Server;

import me.tongfei.progressbar.ProgressBar;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;

class ClientHandler implements Runnable{

    private final SSLSocket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private  long actualSize;
    private final Thread t;



    public ClientHandler(SSLSocket socket){
        this.socket=socket;
        try {
            dis=new DataInputStream(socket.getInputStream());
            dos=new DataOutputStream(socket.getOutputStream());

        } catch (IOException e) {
            e.printStackTrace();
        }
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
    public long getPercentage(long i){
        return (100-((actualSize-i)*100)/actualSize);
    }
    public void run(){

        URL url;
        HttpURLConnection connection;
        InputStream input;//to fetch file from remote server
        RandomAccessFile file=null;
        File tempFile=null;
        String strUrl;
        String fileName;
        //int index;//index of the chunk to be downloaded
        long start;//byte position to start the download
        long end;//byte position to end the download
        final int MAX_BUFFER_SIZE;
        long size;//size of the file to be downloaded
        long actualDownloaded=0;
        long downloaded=0;
        int read;
        boolean isSuccessful=false;
        ProgressBar pb = null;
        try {
            System.out.println("Url received "+(strUrl=dis.readUTF()));
            start=Long.parseLong(dis.readUTF());
            end=Long.parseLong(dis.readUTF());
            System.out.println("Chunk boundary recieved\nChunk boudary: "+start+"-"+end);
             fileName=getFileName(strUrl);
             actualSize=end-start;
             MAX_BUFFER_SIZE=8*1024;//8KB
            downloaded=start;
            actualSize=end-start;//get actual file size to be downloaded
            //setting up connection
            url=new URL(strUrl);
            connection=(HttpURLConnection) url.openConnection();


            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setRequestProperty("Range","bytes"+downloaded+"-");
            if(connection.getResponseCode()/100!=2) {
                System.out.println("Remote server returned HTTP response code: "+connection.getResponseCode());
                return;
            }
            connection.connect();
            System.out.println("connected to remote server");
            input=connection.getInputStream();//assign input stream to fetch data from remote server
            System.out.println("Input stream attached");
            tempFile=new File("../../../server-temp-files/"+fileName);
            System.out.println("mark");
            file=new RandomAccessFile(tempFile,"rw");
            size=connection.getContentLength();
            System.out.println("mark-2:"+start);
            //skipping downloaded bytes
            System.out.println(input.skip(downloaded));

            System.out.println("mark-3");
            System.out.print("\rStart downloading");
            byte[] buffer;
            //command line progress bar to indicate download
            pb=new ProgressBar("Downloading", actualSize);
            //start downloading
            while (downloaded<=end) {
                if (end - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    int diff = (int) (end - downloaded);
                    buffer = new byte[diff];
                }
                read = input.read(buffer);
                if (read == -1) {
                    break;
                }
                //write bytes to the file
                file.write(buffer, 0, read);
                downloaded += read;
                actualDownloaded = downloaded - start;
                //update progress bar
                pb.stepBy(getPercentage(actualDownloaded));
            }
            System.out.println("downloaded completed downloaded="+downloaded+" actual="+actualSize+" file size"+file.length());
            isSuccessful= actualDownloaded == actualSize;
            //if download completed successfully stream the chunk back to client
            if(isSuccessful){
                //set progress to 100% and change the status if streaming is completed successfully
                pb.stepTo(100);
                pb.setExtraMessage("Completed");
                //create new progressbar to indicate the progress of streaming files to client
                ProgressBar pb2=new ProgressBar("Streaming",100);
                System.out.println("file successfully downloaded");
                BufferedInputStream tempBos=new BufferedInputStream(new FileInputStream(tempFile));
                buffer=new byte[16*1024];
                int r=0;
                dos.write(1);
                dos.flush();
                while((r=tempBos.read(buffer))!=-1){
                    System.out.println("sending...");
                    dos.write(buffer,0,r);
                    buffer=new byte[16*1024];
                    pb.stepBy(getPercentage(buffer.length));
                }
                //set progress to 100% and change the status if streaming is completed successfully
                pb2.stepTo(100);
                pb2.setExtraMessage("Completed");
                dos.flush();
                tempBos.close();
                System.out.println("File completely sent! r "+r);
            }else{
                System.out.println("download is not completed successfully");
                dos.write(0);
                dos.flush();
            }

        } catch (IOException e) {
            try {
                dos.write(0);
                dos.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        }finally{
            try {
                tempFile.deleteOnExit();
                dis.close();
                dos.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("session closed");
        }

    }

}
public class Server{

    private static SSLServerSocket server;
    private static SSLSocket socket;
    private static DataOutputStream dos;
    private static final int SERVER_PORT=5001;
    private static final int TRACKER_PORT=5000;
    private static final String TRACKER_IP="localhost";


    public Server(){

        

    }


    private static SSLServerSocket createServerSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        //"SSL_DH_anon_WITH_RC4_128_MD5"
        //java.security.Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket = null;
        try {
            System.out.println("creating a server socket....");
            serverSocket = (SSLServerSocket) serverFactory.createServerSocket(SERVER_PORT);
            System.out.println("server socket created");
            serverSocket.setEnabledCipherSuites(CIPHERS);
            serverSocket.setEnableSessionCreation(true);
        } catch (IOException e1) {
            e1.printStackTrace();

        }
        return serverSocket;
    }
    private static SSLSocket createSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        //"SSL_DH_anon_WITH_RC4_128_MD5"
        //java.security.Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = null;
        try {
            System.out.println("creating socket....");
            socket = (SSLSocket) socketFactory.createSocket(TRACKER_IP, TRACKER_PORT);
            System.out.println("socket created");
            socket.setEnabledCipherSuites(CIPHERS);
            socket.setEnableSessionCreation(true);
        } catch (IOException e1) {
            e1.printStackTrace();
            System.out.println("can't connect to tracker:"+e1.getMessage());

        }
        return socket;
    }
    private static boolean checkConnection(){
        URL url;
        HttpURLConnection connection;
        try {
            url=new URL("https://www.google.com");
            connection=(HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.connect();
        } catch (Exception e) {
            System.out.println("connection is not available");
            return false;
        }
        System.out.println("connection available");
        connection.disconnect();
        return true;
    }
    public static  void main(String[] args) {
        //creating a server
        server=createServerSocket();
        //registering to tracker server
        socket=createSocket();
        //attach inputstream to socket
		try {
            dos=new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //inform the tracker server is ready to accept download request
        if(!checkConnection()){
            try {
                dos.writeUTF("noconnection");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        try {
            dos.writeUTF("server");
            System.out.println("registered to tracker");
        } catch (IOException e) {
            System.out.println("Error to write to tracker");
            e.printStackTrace();
        }
        InetAddress inet=socket.getInetAddress();
        //server start listening
        System.out.println("Server start listening on port:"+SERVER_PORT);
        SSLSocket socket;
        while(true){
            try {
                socket=(SSLSocket)server.accept();
                System.out.println("client "+inet.getHostAddress()+" connected");
                new ClientHandler(socket);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
