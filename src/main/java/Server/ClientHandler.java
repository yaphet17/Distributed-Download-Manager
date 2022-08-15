package Server;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

import javax.net.ssl.SSLSocket;

import LogWritter.LogWriter;
import me.tongfei.progressbar.ProgressBar;

public class ClientHandler implements Runnable {

    private final SSLSocket socket;
    private final Thread t;
    private final LogWriter logWriter = new LogWriter(ClientHandler.class);
    private DataInputStream dis;
    private DataOutputStream dos;
    private long actualSize;


    public ClientHandler(SSLSocket socket) {
        this.socket = socket;
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        t = new Thread(this);
        t.start();

    }

    public String getFileName(String url) {
        if (url.contains("?")) {
            String temp = url.substring(url.lastIndexOf("/") + 1);
            return temp.substring(temp.lastIndexOf("?") + 1);
        }
        return url.substring(url.lastIndexOf("/") + 1);

    }

    public long getPercentage(long i) {
        return (100 - ((actualSize - i) * 100) / actualSize);
    }

    public void run() {
        URL url;
        HttpURLConnection connection;
        InputStream input;//to fetch file from remote server
        RandomAccessFile file;
        File tempFile = null;
        String strUrl;
        String fileName;
        //int index;//index of the chunk to be downloaded
        long start;//byte position to start the download
        long end;//byte position to end the download
        final int MAX_BUFFER_SIZE;
        long size;//size of the file to be downloaded
        long actualDownloaded = 0;
        long downloaded;
        int read;
        boolean isSuccessful;
        ProgressBar pb;
        try {
            strUrl = dis.readUTF();
            start = Long.parseLong(dis.readUTF());
            end = Long.parseLong(dis.readUTF());
            logWriter.writeLog("file information received from client", "info");
            logWriter.writeLog("chunk boundary {" + start + "-" + end + "}", "info");
            fileName = getFileName(strUrl);
            actualSize = end - start;
            MAX_BUFFER_SIZE = 8 * 1024;//8KB
            downloaded = start;
            actualSize = end - start;//get actual file size to be downloaded
            //setting up connection
            url = new URL(strUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setRequestProperty("Range", "bytes" + downloaded + "-");
            if (connection.getResponseCode() / 100 != 2) {
                logWriter.writeLog("remote server returned HTTP response code " + connection.getResponseCode(), "error");
                return;
            }
            connection.connect();
            logWriter.writeLog("connected to remote server", "info");
            input = connection.getInputStream();//assign input stream to fetch data from remote server
            File targetFolder = new File("server-temp-files");
            if (!targetFolder.exists()) {
                if (targetFolder.mkdir()) {
                    logWriter.writeLog("folder doesn't exist---folder " + targetFolder.getName() + " is created", "warn");
                } else {
                    logWriter.writeLog("folder doesn't exist---failed to create " + targetFolder.getName(), "error");
                }
            }
            tempFile = new File(targetFolder.getName() + "/" + fileName);
            file = new RandomAccessFile(tempFile, "rw");
            size = connection.getContentLength();
            //skipping downloaded bytes
            byte[] buffer;
            //command line progress bar to indicate download
            pb = new ProgressBar("Downloading", actualSize);
            //start downloading
            while (downloaded <= end) {
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
            isSuccessful = actualDownloaded == actualSize;
            //if download completed successfully stream the chunk back to client
            if (isSuccessful) {
                //set progress to 100% and change the status if streaming is completed successfully
                pb.setExtraMessage("Completed");
                logWriter.writeLog("download successfully completed", "info");
                //create progressbar to indicate the progress of streaming files to client
                ProgressBar pb2 = new ProgressBar("Streaming", file.length());
                BufferedInputStream tempBos = new BufferedInputStream(Files.newInputStream(tempFile.toPath()));
                buffer = new byte[16 * 1024];
                int r;
                dos.write(1);
                dos.flush();
                long bytes = 0;
                while ((r = tempBos.read(buffer)) != -1) {
                    dos.write(buffer, 0, r);
                    buffer = new byte[16 * 1024];
                    bytes += buffer.length;
                    pb.stepTo(bytes);
                }
                //set progress to 100% and change the status if streaming is completed successfully
                pb2.setExtraMessage("Completed");
                dos.flush();
                tempBos.close();
                logWriter.writeLog("file successfully sent to client " + socket.getInetAddress().getHostAddress(), "info");
            } else {
                logWriter.writeLog("failed to download file", "error");
                dos.write(0);
                dos.flush();
            }

        } catch (ConnectException e) {
            logWriter.writeLog("connection timeout while downloading---" + e.getMessage(), "error");
        } catch (IOException e) {
            try {
                dos.write(0);
                dos.flush();
            } catch (IOException ex) {
                logWriter.writeLog("failed to send error message to client "
                        + socket.getInetAddress().getHostAddress() + "---" + ex.getMessage(), "error");
            }
            logWriter.writeLog(e.getMessage(), "error");
        } finally {
            try {
                assert tempFile != null;
                tempFile.deleteOnExit();
                dis.close();
                dos.close();

            } catch (IOException e) {
                logWriter.writeLog(e.getMessage(), "error");
            }
            logWriter.writeLog("session closed to client " + socket.getInetAddress().getHostAddress(), "info");
        }

    }

}