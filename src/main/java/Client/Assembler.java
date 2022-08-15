package Client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import LogWritter.LogWriter;

public class Assembler {
    private static final LogWriter logWriter = new LogWriter(Assembler.class);

    public String getFileName(String url) {
        if (url.contains("?")) {
            String temp = url.substring(url.lastIndexOf("/") + 1);
            return temp.substring(temp.lastIndexOf("?") + 1);
        }
        return url.substring(url.lastIndexOf("/") + 1);
    }

    public void assemble() {
        logWriter.writeLog("assembling files", "info");
        File[] fileList;
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        String fileName = getFileName(Distributer.fileName);
        String targetFolderName = "Downloads";
        File targetFolder = new File(targetFolderName);
        //create download folder if it doesn't exist
        if (!targetFolder.exists()) {
            if (targetFolder.mkdir()) {
                logWriter.writeLog("folder doesn't exist---folder " + targetFolderName + " is created", "warn");
            } else {
                logWriter.writeLog("folder doesn't exist---failed to create " + targetFolderName, "error");
            }

        }
        File targetFile = new File(targetFolder.getName() + "/" + fileName);
        String folderName = "client-temp-files/" + fileName.substring(0, fileName.lastIndexOf("."));
        File folder = new File(folderName);
        try {
            fileList = folder.listFiles();
            FileOutputStream fos = new FileOutputStream(targetFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            Arrays.sort(fileList, (a, b) -> a.getName().compareTo(b.getName()));
            byte[] buffer = new byte[16 * 1024];
            int r;
            for (File f : fileList) {
                fis = new FileInputStream(f);
                bis = new BufferedInputStream(fis);
                while ((r = bis.read(buffer)) != -1) {
                    bos.write(buffer, 0, r);
                    buffer = new byte[16 * 1024];
                }
                fos.flush();
                bos.flush();
                //f.delete();
            }
            fos.close();
            bos.close();
            fis.close();
            bis.close();
        } catch (FileNotFoundException e) {
            logWriter.writeLog("file not found---" + e.getMessage(), "error");
        } catch (IOException e) {
            logWriter.writeLog(e.getMessage(), "error");
        }
        logWriter.writeLog("files successfully assembled", "info");
        //close client thread
        Client.closeClient();
    }
}