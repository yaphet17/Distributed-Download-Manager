package Client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.ConnectException;

import LogWritter.LogWriter;
import me.tongfei.progressbar.ProgressBar;

public class Downloader implements Runnable{

    private long start;
    private long end;
    private int index;
    private boolean self;
    private Thread t;
    private long actualSize;
    private LogWriter logWritter=new LogWriter(this.getClass());


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

