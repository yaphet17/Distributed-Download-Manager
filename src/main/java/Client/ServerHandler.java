package Client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import LogWritter.LogWriter;
import me.tongfei.progressbar.ProgressBar;

public class ServerHandler implements Runnable {
    private final SSLSocket socket;
    private final InetAddress inet;
    private final String ip;
    private final long start;
    private final long end;
    private final int index;
    private final long size;
    private final Thread t;
    private final LogWriter logWriter = new LogWriter(this.getClass());
    private DataInputStream dis;
    private DataOutputStream dos;

    public ServerHandler(String ip, long start, long end, long size, int index) {
        this.ip = ip;
        socket = createSocket(ip);
        inet = socket.getInetAddress();
        this.start = start;
        this.end = end;
        dis = null;
        dos = null;
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            logWriter.writeLog("failed to attach stream to socket---" + e.getMessage(), "error");
        }
        this.size = size;
        this.index = index;
        t = new Thread(this);
        t.start();

    }

    public static String getFolderName(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    private SSLSocket createSocket(String ip) {
        String[] CIPHERS = {"SSL_DH_anon_WITH_RC4_128_MD5"};
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = null;
        try {
            socket = (SSLSocket) socketFactory.createSocket(ip, Client.SERVER_PORT);
            socket.setEnabledCipherSuites(CIPHERS);
            socket.setEnableSessionCreation(true);
            logWriter.writeLog("client socket created with address " + ip + ":" + Client.SERVER_PORT, "info");
        } catch (IOException e1) {
            logWriter.writeLog("failed to create server with address" + ip + ":" + Client.SERVER_PORT + "---" + e1.getMessage(), "error");
        }
        return socket;
    }

    public String getFileName(String url, int index) {
        if (url.contains("?")) {
            String temp = url.substring(url.lastIndexOf("/") + 1);
            return index + "-" + temp.substring(temp.lastIndexOf("?") + 1);
        }
        return index + "-" + url.substring(url.lastIndexOf("/") + 1);

    }

    private void successfulDownload() {
        Client.addSuccessfulDownload(start + "-" + end);
        //if all downloads are completed start assembling
        if (Client.getSuccessfulDownloadList().size() == Distributer.noServer + 1) {
            logWriter.writeLog("file successfully receivedfrom " + ip, "info");
            new Assembler().assemble();
            return;
        }
        if (!Client.getFailedDownloadList().isEmpty()) {//if download is successful and there is any failed download redistribute
            logWriter.writeLog("redistributing chunk", "warn");
            Distributer.redistribute();
        }

    }

    private void failedDownload() {
        logWriter.writeLog("download failed for server " + ip, "warn");
        Client.addFailedDownload(start + "-" + end + "-" + index);
        //if download failed and there is any free server redistribute
        if (!Client.getFreeServerList().isEmpty()) {
            logWriter.writeLog("redistributing chunk", "warn");
            Distributer.redistribute();
        }
    }

    public void run() {
        RandomAccessFile file = null;
        byte[] buffer = new byte[16 * 1024];
        int b;
        try {
            //sending download information to servers
            dos.writeUTF(Client.strUrl);
            dos.writeUTF(String.valueOf(start));
            dos.writeUTF(String.valueOf(end));
            logWriter.writeLog("file information sent to " + inet.getHostAddress(), "");
            String fileName = getFileName(Client.strUrl, index);
            String folderName = "client-temp-files/" + getFolderName(fileName.substring(2));
            File folder = new File(folderName);
            if (!folder.exists()) {
                if (folder.mkdir()) {
                    logWriter.writeLog("folder doesn't exist---folder " + folderName + " is created", "warn");
                } else {
                    logWriter.writeLog("folder doesn't exist---failed to create folder " + folderName, "error");
                }
            }
            file = new RandomAccessFile(folderName + "/" + fileName, "rw");
            //start receiving chunks from servers
            int status;
            status = dis.read();
            //create new progressbar to indicate the progress of streaming files to client
            ProgressBar pb = new ProgressBar("Receiving", file.length());
            long bytes = 0;
            if (status == 1) {
                while ((b = dis.read(buffer)) != -1) {
                    file.write(buffer, 0, b);
                    buffer = new byte[16 * 1024];
                    bytes += buffer.length;
                    pb.stepTo(bytes);
                }
                pb.setExtraMessage("Completed");
                successfulDownload();
            } else {
                failedDownload();
            }
        } catch (IOException e) {
            failedDownload();
        } finally {
            try {
                dos.flush();
                dis.close();
                dos.close();
            } catch (IOException e) {
                logWriter.writeLog(e.getMessage(), "error");
            }
            logWriter.writeLog("session closed for server " + ip, "info");
        }


    }
}