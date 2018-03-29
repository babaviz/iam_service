/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

/**
 *
 * @author user
 */
public class FTP_FileProcessing {

    private Map<String, String> settings;
    FTPClient ftpClient = new FTPClient();
    private static FTP_FileProcessing instance;
    
    public FTP_FileProcessing(Map<String, String> settings) {
        this.settings = settings;
        Check_new_files();
    }
    
    public static FTP_FileProcessing getinstance(Map<String, String> settings){
        if(instance==null){
            instance=new FTP_FileProcessing(settings);
        }
        return instance;
    }

    public void connect() throws Exception {
        String func = "connect";
        String server = settings.get("ftp_server_url");
        int port = 21;
        try {
            port = Integer.parseInt(settings.get("ftp_server_port"));
        } catch (NumberFormatException e) {
            Iam_services.getInstance().Error_logger(e, func);
        }
        String user = settings.get("ftp_server_user");
        String pass = settings.get("ftp_server_password");
        Iam_services.getInstance().Error_logger(null, "Connectiong to ftp server at " + server + ":" + port + ", with @username:" + user + " &  @password:" + pass, true);

        ftpClient.setControlEncoding("UTF-8");
        ftpClient.setAutodetectUTF8(true);

        ftpClient.connect(server, port);
        showServerReply(ftpClient);

        int replyCode = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            Iam_services.getInstance().Error_logger(null, "Connecting to ftp server failed", true);
            return;
        }

        boolean success = ftpClient.login(user, pass);
        showServerReply(ftpClient);
        if (!success) {
            Iam_services.getInstance().Error_logger(null, "Could not login to the server", true);
            return;
        }

        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
        //ftpClient.enterLocalPassiveMode();

        Iam_services.getInstance().Error_logger(null, "Now connected to ftp server successfully", true);
    }

    public void Check_new_files() {
        Iam_services.getInstance().lock = true;
        String func = "Check_new_files";
        try {
            //dir
            connect();
            String dir = settings.get("remote_outbound_folder");
            /* if (!dir.isEmpty() && !dir.contains("/")) {
                dir = "/" + dir;
            }*/
            //check if processed dir exist then create it
            /*String proc = dir.trim().isEmpty() ? "" : dir + "/";
            if (!checkDirectoryExists(ftpClient, proc + settings.get("proccessed_folder_name"))) {
                boolean makeDirectory = ftpClient.makeDirectory(proc + settings.get("proccessed_folder_name"));
                if (makeDirectory) {
                    Iam_services.getInstance().Error_logger(null, "Proccessed dir created successfully", true);
                } else {
                    Iam_services.getInstance().Error_logger(new Exception("proccessed directory could not be created on the server"), func);
                }
            }*/

            //test
            List<String[]> newXMFileList = new ArrayList<>();
            listDirectory(ftpClient, dir, "", 0, newXMFileList);
            // Lists files and directories
            //Iam_services.getInstance().Error_logger(null, "Dircetory:" + dir, true);
            //FTPFile[] files1 = ftpClient.listFiles(dir);
            Iam_services.getInstance().Error_logger(null, newXMFileList.size() + "  Files found", true);
            //download files for processing
            for (String[] file : newXMFileList) {
                download_file(ftpClient, file[1], file[0]);
            }

            //get_down_LoadFiles(files1, ftpClient, dir.trim().isEmpty() ? "" : dir + "/");*/
            //check and process downloaded files
            if (newXMFileList.size() > 0) {
                Iam_services.getInstance().check_files(true);
            }
        } catch (Exception ex) {
            Iam_services.getInstance().Error_logger(ex, func);
        } finally {
            // logs out and disconnects from server
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ex) {
                Iam_services.getInstance().Error_logger(ex, func);
            }
        }
        Iam_services.getInstance().lock = false;
    }

    private void get_down_LoadFiles(FTPFile[] files, FTPClient fTPClient, String dir) {

        for (FTPFile file : files) {
            String details = file.getName();
            if (file.isDirectory()) {
                details = "[" + details + "]";
                Iam_services.getInstance().Error_logger(null, details, true);
            } else /*if(truefile.contains(".xml"))*/ {
                download_file(fTPClient, details, dir);
            }
        }
    }

    private void download_file(FTPClient ftpClient, String file, String dir) {
        String func = "download_file";
        String hmdir = settings.get("working_dir");
        if (hmdir.trim().isEmpty()) {
            hmdir = "workspace";
        }
        File downloadFolder = new File(hmdir);
        if (!downloadFolder.exists()) {
            downloadFolder.mkdirs();
        }

        if (!dir.endsWith("/")) {
            dir += "/";
        }

        try {
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(new File(downloadFolder, file).getPath()));
            boolean success = ftpClient.retrieveFile(dir + file, outputStream);
            outputStream.close();

            if (success) {
                Iam_services.getInstance().Error_logger(null, file + " has been downloaded successfully.", true);

                String proc = dir;
                if (!checkDirectoryExists(ftpClient, proc + settings.get("proccessed_folder_name"))) {
                    boolean makeDirectory = ftpClient.makeDirectory(proc + settings.get("proccessed_folder_name"));
                    if (makeDirectory) {
                        Iam_services.getInstance().Error_logger(null, "Proccessed dir created successfully", true);
                    } else {
                        Iam_services.getInstance().Error_logger(new Exception("proccessed directory could not be created on the server"), func);
                    }
                }

                boolean rename = ftpClient.rename(dir + file, dir + settings.get("proccessed_folder_name") + "/" + file);

                if (!rename) {
                    Iam_services.getInstance().Error_logger(null, file + "-> File could not be relocated on server", true);
                }
            } else {
                Iam_services.getInstance().Error_logger(null, " Error downloading file->" + file, true);
            }
        } catch (Exception ex) {
            Iam_services.getInstance().Error_logger(ex, func);
        }
    }

    private static void showServerReply(FTPClient ftpClient) {
        String[] replies = ftpClient.getReplyStrings();
        if (replies != null && replies.length > 0) {
            for (String aReply : replies) {
                Iam_services.getInstance().Error_logger(null, "SERVER reply: " + aReply, true);
            }
        }
    }

    boolean checkDirectoryExists(FTPClient ftpClient, String dirPath) {
        try {
            ftpClient.changeWorkingDirectory(dirPath);
            int replyCode = ftpClient.getReplyCode();
            if (replyCode == 550) {
                return false;
            }
            return true;
        } catch (IOException ex) {
            Iam_services.getInstance().Error_logger(ex, "checkDirectoryExists");
            return false;
        }
    }

    private void listDirectory(FTPClient ftpClient, String parentDir, String currentDir, int level, List<String[]> newFilList) throws IOException {
        String dirToList = parentDir;
        if (!currentDir.equals("")) {
            dirToList += "/" + currentDir;
        }

        // Iam_services.getInstance().Error_logger(null, dirToList, true);
        FTPFile[] subFiles = ftpClient.listFiles(dirToList);
        if (subFiles == null) {
            // Iam_services.getInstance().Error_logger(null, dirToList+"->Returned null", true);
        } else if (subFiles.length == 0) {
            //Iam_services.getInstance().Error_logger(null, dirToList+"->list files empty", true);
        } else {
            /* if (subFiles != null && subFiles.length > 0) {*/
            for (FTPFile aFile : subFiles) {
                String currentFileName = aFile.getName();
                //Iam_services.getInstance().Error_logger(null, "Early:"+currentDir, true);
                if (currentFileName.equals(".") || currentFileName.equals("..")
                        || new ArrayList<>(Arrays.asList(settings.get("ignore_folders").split(","))).contains(currentFileName)) {

                    // skip parent directory and directory itself
                    continue;
                }
                String tab = "";
                /*for (int i = 0; i < level; i++) {
                    //System.out.print("\t");
                    //tab += "\t";
                }*/
                if (aFile.isDirectory()) {
                    // System.out.println("[" + currentFileName + "]");
                    //Iam_services.getInstance().Error_logger(null, tab + "[" + currentFileName + "]", true);
                    listDirectory(ftpClient, dirToList, currentFileName, level + 1, newFilList);
                } else {
                    //System.out.println(currentFileName);
                    //Iam_services.getInstance().Error_logger(null, tab + currentFileName, true);
                    newFilList.add(new String[]{dirToList, currentFileName});//add to queue
                }
            }
        }
    }

    public void uploadFile(String XMLFilePath) throws Exception {
        if (!ftpClient.isConnected()) {
            connect();
        }
        InputStream inputStream = new FileInputStream(XMLFilePath);
        boolean done = ftpClient.storeFile(settings.get("remote_inbound_folder") + "/" + (new File(XMLFilePath).getName()), inputStream);
        inputStream.close();
        if (done) {
            Iam_services.getInstance().Error_logger(null, XMLFilePath + "->Upload success", true);
            
             Files.move(new File(XMLFilePath).toPath(),
                            new File(new File("inbound_generated", "processed"), new File(XMLFilePath).getName()).toPath()
                    );
            
        } else {
            Iam_services.getInstance().Error_logger(new Exception(XMLFilePath + "->Upload error, System received failure responce"), "uploadFile", true);
        }
    }

}
