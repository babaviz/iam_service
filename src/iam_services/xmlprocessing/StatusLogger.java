/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services.xmlprocessing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author user
 */
public class StatusLogger {

    private static StatusLogger instance;
    private Connection conn;

    private StatusLogger() throws Exception {
        conn = iam_services.Iam_services.getInstance().Connect();
        if (conn == null) {
            throw new Exception("Null connection");
        }
    }

    public static StatusLogger getInstance() throws Exception {
        if (instance == null) {
            instance = new StatusLogger();
        }
        return instance;
    }

    public int Log_start(File xmlFile, String type) {
        int id = 0;
        CallableStatement cstm = null;
        /*
        @Doc_Uniq_Id	=NULL,--SERIAL
        @XML =@DATA,
        @ProcedureName ='sp_Read_Article_XMlFile',
        @File_Name	=@FILENAME,
        @Integration	=NULL,
        @Type_Integration=@TYPE,
        @Doc_Header	='Article',
        @Log_ID = @Log_ID OUTPUT;
         */
        String xml = getContent(xmlFile.getPath());
        try {
            cstm = conn.prepareCall("{call sp_log_files(?,?,?,?,?,?,?)}");
            cstm.setString(1, null);
            cstm.setNString(2, xml);
            cstm.setString(3, "JAVA");
            cstm.setString(4, xmlFile.getName());
            cstm.setString(5, null);
            cstm.setInt(6, 1);
            cstm.setString(7, type);
            //System.err.println("sending...");
            cstm.execute();
            cstm.registerOutParameter("Log_id", java.sql.Types.INTEGER);
            id = cstm.getInt("Log_id");
            System.err.println("Insert_id=>" + id);
        } catch (Exception ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "Log_start");
        }
        return id;
    }

    public void Log_error(int id) {
        //errors logged on local logfile
        /*
         EXEC sp_CallProcedureLog 
        @ObjectID       = @@PROCID,	
        @Log_ID=@Log_ID,
        @AdditionalInfo = @msg;
         */
        try {
            CallableStatement cstm = null;
            cstm = conn.prepareCall("{call sp_CallProcedureLog(?,?,?)}");
            cstm.setInt(1, 0);
            cstm.setInt(2, id);
            cstm.setString(3, "File upload finished with errors, refer to Logs on the iam_services directory");
            cstm.execute();
        } catch (Exception ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "Log_error");
        }
    }

    public void Log_success(int id, String docNum) {
        try {
            PreparedStatement pstm = conn.prepareStatement(
                    "UPDATE CNB_IAM_Log_HD SET Doc_Uniq_Id=?, Status=1 WHERE Log_ID=?"
            );
            pstm.setString(1, docNum);
            pstm.setInt(2, id);
        } catch (Exception ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "Log_success");
        }
    }

    private String getContent(String path) {
        try {
            String string = new String(Files.readAllBytes(Paths.get(path)));
            return string;
        } catch (IOException ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "getContent");
        }
        return "";
    }
}
