package Server;

import Client.Client;
import LogWritter.LogWritter;
import me.tongfei.progressbar.ProgressBar;
import picocli.CommandLine;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;

import static picocli.CommandLine.*;

class ClientHandler implements Runnable{

    private final SSLSocket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private  long actualSize;
    private final Thread t;
    private final LogWritter logWritter=new LogWritter(this.getClass());



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
            strUrl=dis.readUTF();
            start=Long.parseLong(dis.readUTF());
            end=Long.parseLong(dis.readUTF());
            logWritter.writeLog("file information received from client","info");
            logWritter.writeLog("chunk boundary {"+start+"-"+end+"}","info");
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
                logWritter.writeLog("remote server returned HTTP response code "+connection.getResponseCode(),"error");
                return;
            }
            connection.connect();
            logWritter.writeLog("connected to remote server","info");
            input=connection.getInputStream();//assign input stream to fetch data from remote server
            File targetFolder=new File("server-temp-files");
            if(!targetFolder.exists()){
                if(targetFolder.mkdir()){
                    logWritter.writeLog("folder doesn't exist---folder "+targetFolder.getName()+" is created","warn");
                }else{
                    logWritter.writeLog("folder doesn't exist---failed to create "+targetFolder.getName(),"error");
                }
            }
            tempFile=new File(targetFolder.getName()+"/"+fileName);
            file=new RandomAccessFile(tempFile,"rw");
            size=connection.getContentLength();
            //skipping downloaded bytes
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
                pb.stepTo(actualDownloaded);
            }
            isSuccessful= actualDownloaded == actualSize;
            //if download completed successfully stream the chunk back to client
            if(isSuccessful){
                //set progress to 100% and change the status if streaming is completed successfully
                pb.setExtraMessage("Completed");
                logWritter.writeLog("download successfully completed","info");
                //create progressbar to indicate the progress of streaming files to client
                ProgressBar pb2=new ProgressBar("Streaming",file.length());
                BufferedInputStream tempBos=new BufferedInputStream(new FileInputStream(tempFile));
                buffer=new byte[16*1024];
                int r=0;
                dos.write(1);
                dos.flush();
                long bytes=0;
                while((r=tempBos.read(buffer))!=-1){
                    dos.write(buffer,0,r);
                    buffer=new byte[16*1024];
                    bytes+=buffer.length;
                    pb.stepTo(bytes);
                }
                //set progress to 100% and change the status if streaming is completed successfully
                pb2.setExtraMessage("Completed");
                dos.flush();
                tempBos.close();
                logWritter.writeLog("file successfully sent to client "+socket.getInetAddress().getHostAddress(),"info");
            }else{
                logWritter.writeLog("failed to download file","error");
                dos.write(0);
                dos.flush();
            }

        } catch (ConnectException e) {
            logWritter.writeLog("connection timeout while downloading---"+e.getMessage(),"error");
         } catch (IOException e) {
            try {
                dos.write(0);
                dos.flush();
            } catch (IOException ex) {
                logWritter.writeLog("failed to send error message to client "
                        +socket.getInetAddress().getHostAddress()+"---"+ex.getMessage(),"error");
            }
          logWritter.writeLog(e.getMessage(),"error");
        }finally{
            try {
                tempFile.deleteOnExit();
                dis.close();
                dos.close();

            } catch (IOException e) {
                logWritter.writeLog(e.getMessage(),"error");
            }
           logWritter.writeLog("session closed to client "+socket.getInetAddress().getHostAddress(),"info");
        }

    }

}
@Command(name="server",description = "start download server", mixinStandardHelpOptions = true)
public class Server implements Runnable{

    private static SSLServerSocket server;
    private static SSLSocket socket;
    private static DataOutputStream dos;
    private static final int SERVER_PORT=5001;
    private static final int TRACKER_PORT=5000;
    private static final String TRACKER_IP="localhost";
    private final LogWritter logWritter=new LogWritter(this.getClass());




    private SSLServerSocket createServerSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket = null;
        try {
            serverSocket = (SSLServerSocket) serverFactory.createServerSocket(SERVER_PORT);
            serverSocket.setEnabledCipherSuites(CIPHERS);
            serverSocket.setEnableSessionCreation(true);
            logWritter.writeLog("server start listening on port "+SERVER_PORT,"info");
        } catch (IOException e1) {
          logWritter.writeLog("failed to create server on port"+SERVER_PORT+"---"+e1.getMessage(),"error");
        }
        return serverSocket;
    }
    private  SSLSocket createSocket() {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = null;
        try {
            socket = (SSLSocket) socketFactory.createSocket(TRACKER_IP, TRACKER_PORT);
            socket.setEnabledCipherSuites(CIPHERS);
            socket.setEnableSessionCreation(true);
            logWritter.writeLog("client socket created with address "+TRACKER_IP+":"+TRACKER_IP,"info");
        } catch (IOException e1) {
            logWritter.writeLog("failed to connect with tracker---"+e1.getMessage(),"error");
        }
        return socket;
    }
    private boolean checkConnection(){
        URL url;
        HttpURLConnection connection;
        try {
            url=new URL("https://www.google.com");
            connection=(HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.connect();
        } catch (Exception e) {
            logWritter.writeLog("connection is not available---"+e.getMessage(),"warn");
            return false;
        }
        connection.disconnect();
        return true;
    }
    public void run() {
        //creating a server
        server=createServerSocket();
        //registering to tracker server
        socket=createSocket();
        //attach inputstream to socket
		try {
            dos=new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            logWritter.writeLog("failed to attach stream to socket---"+e.getMessage(),"error");
        }
        //inform the tracker server is ready to accept download request
        if(!checkConnection()){
            try {
                dos.writeUTF("noconnection");
            } catch (IOException e) {
                logWritter.writeLog("failed to attach stream to socket---"+e.getMessage(),"error");
            }
            return;
        }
        try {
            dos.writeUTF("server");
        } catch (IOException e) {
            logWritter.writeLog("failed to register to tracker---"+e.getMessage(),"error");
        }
        InetAddress inet=socket.getInetAddress();
        //accepting client request
        SSLSocket socket;
        while(true){
            try {
                socket=(SSLSocket)server.accept();
                logWritter.writeLog("client "+inet.getHostAddress()+" connected","info");
                new ClientHandler(socket);
            } catch (IOException e) {
                logWritter.writeLog("failed to accept client request---"+e.getMessage(),"error");
            }

        }
    }
}
