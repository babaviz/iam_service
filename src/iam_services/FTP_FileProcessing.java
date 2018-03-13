/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

    public FTP_FileProcessing(Map<String, String> settings) {
        this.settings = settings;
        Check_new_files();
    }

    public void Check_new_files() {
        String func="Check_new_files";
        String server = settings.get("ftp_server_url");
        int port = 21;
        try {
             port=Integer.parseInt(settings.get("ftp_server_port"));
        } catch (Exception e) {
            Iam_services.getInstance().Error_logger(e, func);
        }
        String user = settings.get("ftp_server_user");
        String pass = settings.get("ftp_server_password");        
        Iam_services.getInstance().Error_logger(null, "Connectiong to ftp server at "+server+":"+port+", with @username:"+user+" &  @password:"+pass,true);
        FTPClient ftpClient = new FTPClient();

        try {

            ftpClient.connect(server, port);
            showServerReply(ftpClient);

            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
               Iam_services.getInstance().Error_logger(null, "Connecting to ftp server failed",true);
                return;
            }

            boolean success = ftpClient.login(user, pass);
            showServerReply(ftpClient);

            if (!success) {
                Iam_services.getInstance().Error_logger(null, "Could not login to the server",true);
                return;
            }

            Iam_services.getInstance().Error_logger(null, "Now connected to ftp server successfully",true);
            //dir
            String dir=settings.get("remote_folder");
            if(!dir.isEmpty() && !dir.contains("/")){
                dir="/"+dir;
            }
            // Lists files and directories
            FTPFile[] files1 = ftpClient.listFiles(dir);
            get_down_LoadFiles(files1,ftpClient);
            //check and process downloaded files
            if(files1.length>0){
                Iam_services.getInstance().check_files(true);
            }
        } catch (IOException ex) {
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
    }
    
    private  void get_down_LoadFiles(FTPFile[] files,FTPClient fTPClient) {
        for (FTPFile file : files) {
            String details = file.getName();           
            if (file.isDirectory()) {
                details = "[" + details + "]";
            }else /*if(truefile.contains(".xml"))*/{
                download_file(fTPClient, details);
            }
        }
    }
 
    private void download_file(FTPClient ftpClient,String file){
        String func="download_file";
        String hmdir=settings.get("working_dir");
        if(hmdir.trim().isEmpty()){
            hmdir="workspace";
        }
        File downloadFolder=new File(hmdir);
        if(!downloadFolder.exists()){
            downloadFolder.mkdirs();
        }
        
        try{
        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(new File(downloadFolder,file).getPath()));
        boolean success = ftpClient.retrieveFile(file, outputStream);
        outputStream.close();

        if (success) {
            Iam_services.getInstance().Error_logger(null,file+" has been downloaded successfully.",true);
            boolean rename = ftpClient.rename(file, "processed/"+file);
            
            if(!rename){
                Iam_services.getInstance().Error_logger(null,file+"-> File could not be relocated on server",true);
            }
        }else{
            Iam_services.getInstance().Error_logger(null," Error downloading file->"+file,true);
        }
        }catch(Exception ex){
            Iam_services.getInstance().Error_logger(ex, func);
        }
    }
 
    private static void showServerReply(FTPClient ftpClient) {
        String[] replies = ftpClient.getReplyStrings();
        if (replies != null && replies.length > 0) {
            for (String aReply : replies) {
                Iam_services.getInstance().Error_logger(null,"SERVER reply: " + aReply,true);
            }
        }
    }

}
