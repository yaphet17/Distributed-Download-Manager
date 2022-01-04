package Main.Server;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;

class Assembler{

}
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
    public static String getFileName(String url) {
        return url.substring(url.lastIndexOf("/")+1);
    }

    private String getSizeProgress(long downloaded){
        return downloaded/1048576+"MB";
    }
    private int getPercentProgress(long downloaded){
        return (int)((downloaded/actualSize)*100);
    }
    private void showProgress(String sizeProgress,int percentProgress){
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
        long actualDownloaded;
        long downloaded;
        int status=0;//download flag:zero by default means the downloaded didn't finished
        boolean isSuccessful;

        try {
            System.out.println("Url received "+(strUrl=dis.readUTF()));
            start=Long.parseLong(dis.readUTF());
            end=Long.parseLong(dis.readUTF());
            System.out.println("Chunk boundary recieved\nChunk boudary: "+start+"-"+end);
             fileName=getFileName(strUrl);
             actualSize=end-start;
             /*MAX_BUFFER_SIZE=8*1024;//8KB
            downloaded=start;
            actualSize=end-start;//get actual file size to be downloaded

            //setting up connection
            url=new URL(strUrl);
            connection=(HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Range","bytes"+downloaded+"-");
            connection.connect();
            System.out.println("connected to remote server");
            input=connection.getInputStream();//assign input stream to fetch data from remote server
            tempFile=new File(fileName);
            file=new RandomAccessFile(tempFile,"rw");
            size=connection.getContentLength();
            //skipping downloaded bytes
            input.skip(start);
            //start downloading
            while (downloaded<=end) {
                System.out.print("\rStart downloding");
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
                showProgress(getSizeProgress(actualDownloaded),getPercentProgress(actualDownloaded));
            }

            isSuccessful= downloaded == actualSize;
            //if download completed succesfully stream the chunk back to client
            if(isSuccessful){
                byte[] buffer=new byte[16*1024];
                int r;
                while((r=file.read(buffer))!=-1){
                    dos.write(buffer,0,r);
                    buffer=new byte[16*10254];
                }
                dos.flush();
                System.out.println("File completely sent!");
            }
        */
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            try {
               // file.close();
                //tempFile.deleteOnExit();
                dis.close();
                dos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

}
public class Server{

    private static SSLServerSocket server;
    private static SSLSocket socket;
    private static DataOutputStream dos;
    private static final int SERVER_PORT=5001;
    private static final int TRACKER_PORT=5000;
    private static final String TRACKER_IP="192.168.137.49";


    public Server(){

        

    }


    private static SSLServerSocket createServerSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
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

        }
        return socket;
    }
    private static boolean checkConnection(){
        URL url;
        HttpURLConnection connection;
        try {
            url=new URL("https://www.google.com");
            connection=(HttpURLConnection) url.openConnection();
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
        //registering to client server
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
