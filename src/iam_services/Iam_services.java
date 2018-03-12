/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 *
 * @author user
 */
public class Iam_services {

    final String DB = "DB";
    final String SERVER = "SERVER_INSTANCE";
    final String PORT = "port";
    final String USER = "user";
    final String PASSWORD = "password";
    final String CONNECTION_STRING = "jdbc_connection_string";
    final String DATABASE_DRIVER_MSSSQL = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    final String FOLDER = "destination_folder";
    Connection conn = null;
    Map<String, String> settings = new HashMap<>();

    Logger logger = Logger.getLogger(getClass().getSimpleName());
    FileHandler fh;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        
        new Iam_services().doWork();
    }

    public Iam_services() {
        try {
            //initialize logger
            String dateString = new SimpleDateFormat("yyyy_MM_dd").format(new Date());
            fh = new FileHandler("iam_service_err_" + dateString + ".log", true);
            logger.addHandler(fh);
        } catch (IOException | SecurityException ex) {
            Logger.getLogger(Iam_services.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void doWork() {
        while (true) {
            try {
                check_files();
                Thread.sleep(1000*60);                
            } catch (InterruptedException e) {
                Error_logger(e,"doWork");
                Error_logger(new Exception("Sytem Interrupted"),"doWork");
            }
            Error_logger(new Exception("Sytem system alive"),"doWork");
        }
    }

    private void Connect() {
        String[] conStrings = getSettings();
        conn = MSQLConnection(conStrings[0]);
        if (conn == null) {//if not successful, try second
            Error_logger(new Exception("Failed to connect with connection string: " + conStrings[0]), "Connect");
            //try seond alternative
            conn = MSQLConnection(conStrings[1]);
        }

        if (conn == null) {
            //couldn't connect
            Error_logger(new Exception("Failed to connect with connection string: " + conStrings[1]), "Connect");
            Error_logger(new Exception("System could not connect to the server using provided settings"), "Connect");
        } else {
            Error_logger(new Exception("Connection succeeded, hurray!"),"Connect");
        }
    }

    private String[] getSettings() {
        try {
            File file = new File("iam_service_settings.xml");
            FileInputStream fileInput = new FileInputStream(file);
            Properties properties = new Properties();
            properties.loadFromXML(fileInput);
            fileInput.close();

            Enumeration enuKeys = properties.keys();
            while (enuKeys.hasMoreElements()) {
                String key = (String) enuKeys.nextElement();
                String value = properties.getProperty(key);
                settings.put(key, value);
            }
            String whole = settings.get(CONNECTION_STRING);
            String parts = "jdbc:sqlserver://" + settings.get(SERVER) + (settings.get(PORT).isEmpty() ? "" : ":" + settings.get(PORT))
                    + " ;databaseName=" + settings.get(DB) + ";user=" + settings.get(USER) + ";password=" + settings.get(PASSWORD);
            String[] conn_Strings = {whole, parts};

            return conn_Strings;
            // throw new Exception("test error");
        } catch (Exception e) {
            Error_logger(e, "getConnetionString");
        }
        return null;
    }

    private void Error_logger(Exception e, String methodName) {
        // logger.setUseParentHandlers(false);//no console logging
        try {
            // This block configure the logger with handler and formatter
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            // the following statement is used to log any messages
            logger.log(Level.SEVERE, e, () -> methodName);

        } catch (SecurityException ex) {
            ex.printStackTrace();
        }

    }

    //mssql connection
    public Connection MSQLConnection(String connectionUrl) {
        try {
            // Establish the connection.  
            Class.forName(DATABASE_DRIVER_MSSSQL);
            conn = DriverManager.getConnection(connectionUrl);

        } // Handle any errors that may have occurred.  
        catch (Exception e) {
            Error_logger(e, "MSQLConnection");
        }
        return conn;
    }

    public void dump_xmlFILE_toDB(String name, String xml) {
        try {
            CallableStatement cstm = getStatement(name);
            if (xml.isEmpty()) {
                Error_logger(new Exception("Empty xml file content:" + name), "dump_xmlFILE_toDB");
                return;
            }

            if (cstm == null) {
                Error_logger(new Exception("System couldn't determine category of xml file:" + name), "dump_xmlFILE_toDB");
                return;
            }

            cstm.setNString(1, xml);
            cstm.setString(2, name);
            //System.err.println("sending...");
            new Thread(() -> {
                try {
                    cstm.execute();
                } catch (SQLException ex) {
                    Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
                }
            }).start();
        } catch (SQLException ex) {
            Error_logger(ex, "dump_xmlFILE_toDB");
        }
    }

    public CallableStatement getStatement(String fileName) throws SQLException {
        CallableStatement cstm = null;
        if (fileName.toLowerCase().contains("price_ean")) {
            cstm = conn.prepareCall("{call sp_Read_PriceMasterEan_XMLFILE(?,?)}");
        } else if (fileName.toLowerCase().contains("price_artuom")) {
            cstm = conn.prepareCall("{call sp_Read_PriceMasterArticleUom_XMLFILE(?,?)}");
        } else if (fileName.toLowerCase().contains("pi_artmas")) {
            cstm = conn.prepareCall("{call sp_Read_PI_ARTIMAS_XMlFile(?,?)}");
        } else if (fileName.toLowerCase().contains("merc")) {
            cstm = conn.prepareCall("{call sp_Read_MERC_XMlFile(?,?)}");
        } else if (fileName.toLowerCase().contains("ean_")) {
            cstm = conn.prepareCall("{call sp_Read_EAN_XMlFile(?,?)}");
        } else if (fileName.toLowerCase().contains("customer")) {
            cstm = conn.prepareCall("{call sp_Read_customerMaster_XMLFILE(?,?)}");
        } else if (fileName.toLowerCase().contains("brand")) {
            cstm = conn.prepareCall("{call sp_Read_BrandMaster_XMLFILE(?,?)}");
        } else if (fileName.toLowerCase().contains("artmas")) {
            cstm = conn.prepareCall("{call sp_Read_Article_XMlFile(?,?)}");
        }

        return cstm;
    }

    public String readLocalFile(String path) {
        //System.err.println("Reading->" + path);
        try {
            String string = new String(Files.readAllBytes(Paths.get(path)));

            //System.err.println("Done reading");
            return string;
        } catch (IOException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public String readRemoteFile(URL url) {
        try {
            URLConnection xyzcon = url.openConnection();
            StringBuilder builder;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(xyzcon.getInputStream()))) {
                String inputLine;
                builder = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    builder.append(inputLine);
                }
            }
            return builder.toString();
        } catch (IOException ex) {
            Logger.getLogger(Iam_services.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public void check_files() {        
         getSettings();//read settings
         
        if (settings.get("access_type").equalsIgnoreCase("remote")) {
            //process files from a remote server
            processRemoteFolder();
        }
        //else process files on a local directory
        File folder = new File(settings.get(FOLDER));
        if (!folder.exists()) {
            Error_logger(new IOException("File folder not found:" + folder.getPath()), "check_files");
            return;
        }

        //filter xml files
        //System.err.println(folder.listFiles(new XMLFileFilter()).length + " new Files found");
        int new_files=folder.listFiles(new XMLFileFilter()).length;
        Error_logger(new Exception(new_files + " new Files found"), DB);
        
        if(new_files>0){
            Connect();
        }else{
            return;
        }
        
        File processed_dir = new File(folder, "proccessed");
        if (!processed_dir.exists()) {
            processed_dir.mkdirs();
        }

        int count = 1;
        for (final File fileEntry : folder.listFiles(new XMLFileFilter())) {
            if (fileEntry.isDirectory()) {
                continue;
            }
            Error_logger(new Exception("Processing file->" + (count++)),"check_files");
            try {
                dump_xmlFILE_toDB(fileEntry.getName(), readLocalFile(fileEntry.getPath()));
                Files.move(Paths.get(fileEntry.getPath()), Paths.get(new File(processed_dir, fileEntry.getName()).getPath()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                Error_logger(ex, "check_files");
            }
        }
    }

    public void processRemoteFolder() {
        Error_logger(new UnsupportedOperationException("remote operation not supported"), "processRemoteFolder");
    }

    /**
     * A class that implements the Java FileFilter interface.
     */
    public class XMLFileFilter implements FileFilter {

        @Override
        public boolean accept(File file) {
            if (file.getName().toLowerCase().endsWith("xml")) {
                return true;
            }
            return false;
        }
    }
}
