package Client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import LogWritter.LogWriter;

public class Distributer {

    protected static final Map<String, String> serverChunkMap = new LinkedHashMap<>();
    public static String fileName;
    protected static HttpURLConnection connection = null;
    protected static String strUrl;
    protected static int noServer;
    private static URL url = null;
    private static long downloadSize;
    private final LogWriter logWriter = new LogWriter(Distributer.class);
    private Thread t;
    private int retryCount = 0;

    public Distributer(String strUrl) {
        Distributer.strUrl = strUrl;
        if (initialize()) {
            new Downloader(0, downloadSize, 1, true);
        }
    }

    public Distributer(String strUrl, int noServer) {
        Distributer.strUrl = strUrl;
        Distributer.noServer = noServer;
        if (initialize()) {
            distribute();
        }

    }

    public static void redistribute() {
        //number of failed download
        int n = Math.min(Client.getFailedDownloadList().size(), Client.getFreeServerList().size());//get minimum size
        long start, end;
        int index;
        String ip;
        String[] chunkInfo;
        for (int i = 0; i < n; i++) {
            chunkInfo = Client.getFailedDownload(i).split("-");
            start = Long.parseLong(chunkInfo[0]);
            end = Long.parseLong(chunkInfo[1]);
            index = Integer.parseInt(chunkInfo[2]);
            ip = Client.getFreeServer(i);
            if (ip.equals("localhost") || Client.getFreeServer(i).equals("127.0.0.1")) {
                new Downloader(start, end, index, false);
            } else {
                new ServerHandler(ip, start, end, downloadSize, index);
            }
            Client.removeFreeServer(ip);//remove server from free server list once download is assigned
            Client.removeFailedDownload(Client.getFailedDownload(i));//remove chunk info from failed download list once server is assigned

        }

    }

    private boolean initialize() {
        try {
            url = new URL(strUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setRequestProperty("Range", "bytes" + 0 + "-");
            downloadSize = connection.getContentLengthLong();
            fileName = url.getFile();
            if (connection.getResponseCode() / 100 != 2) {
                logWriter.writeLog("remote server returned HTTP response code " + connection.getResponseCode(), "error");
                return false;
            }
            connection.connect();
        } catch (IOException e) {
            logWriter.writeLog("connection timeout", "warn");
            if (retryCount <= 10) {
                retryCount++;
                initialize();
            }
        }
        return true;
    }

    public void distribute() {
        long chunkSize = downloadSize / (noServer + 1);
        int remainder = (int) (downloadSize - ((noServer + 1) * chunkSize));
        long start = 0;
        long end = chunkSize + remainder;
        int index = 1;
        new Downloader(start, end, index++, false);
        logWriter.writeLog("chunk boundary for self {" + start + "-" + end + "}", "info");
        for (String ip : Client.tempIpList) {
            start = end;
            end += chunkSize;
            logWriter.writeLog("chunk boundary for " + ip + " {" + start + "-" + end + "}", "info");
            new ServerHandler(ip, start, end, downloadSize, index);
            serverChunkMap.put(ip, start + "-" + end);
            index++;
        }
    }

}
