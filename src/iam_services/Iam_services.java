/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services;

import iam_services.xmlprocessing.StatusLogger;
import iam_services.xmlprocessing.art_adjdoc_con;
import iam_services.xmlprocessing.inv_detail_credit;
import iam_services.xmlprocessing.pos_fi_actual_XML;
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
    private static Iam_services instance;
    public boolean lock = false;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here

        instance = new Iam_services();
        instance.doWork();
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
        int wait=10;
        while (true) {
            try {
                if (!lock) {
                    check_files();
                    check_and_process_inboundDATA();
                }
                try{wait=Integer.parseInt(settings.get("duration").trim());}catch(Exception ex){Error_logger(ex, "doWork");}
                Thread.sleep(1000 * 60 * wait);
            } catch (InterruptedException e) {
                Error_logger(e, "doWork");
                Error_logger(null, "Sytem Interrupted", true);
            }
            Error_logger(null, "Sytem system alive", true);
        }
    }

    public Connection Connect() {
        if(conn !=null)return conn;
        
        String[] conStrings = getSettings();
        conn = MSQLConnection(conStrings[0]);
        if (conn == null) {//if not successful, try second
            Error_logger(new Exception("Failed to connect with connection string: " + conStrings[0]), "Connect");
            //try seond alternative
            conn = MSQLConnection(conStrings[1]);
        }

        if (conn == null) {
            //couldn't connect
            Error_logger(null, "Failed to connect with connection string: " + conStrings[1], true);
            Error_logger(null, "System could not connect to the server using provided settings", true);
        } else {
            Error_logger(null, "Connection succeeded, hurray!", true);
        }
        return conn;
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

    public void Error_logger(Exception e, String methodName) {
        Error_logger(e, methodName, false);
    }

    public void Error_logger(Exception e, String methodName, boolean info) {
        // logger.setUseParentHandlers(false);//no console logging
        try {
            // This block configure the logger with handler and formatter
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            // the following statement is used to log any messages
            if (!info) {
                logger.log(Level.SEVERE, e, () -> methodName);
            } else {
                logger.info(methodName);
            }

        } catch (SecurityException ex) {
            ex.printStackTrace();
        }

    }

    //mssql connection
    private Connection MSQLConnection(String connectionUrl) {
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

    public boolean dump_xmlFILE_toDB(String name, String xml) {
        try {
            CallableStatement cstm = getStatement(name);
            if (xml.isEmpty()) {
                Error_logger(new Exception("Empty xml file content:" + name), "dump_xmlFILE_toDB");
                return true;
            }

            if (cstm == null) {
                Error_logger(new Exception("System couldn't determine category of xml file:" + name), "dump_xmlFILE_toDB");
                return false;
            }

            cstm.setNString(1, xml);
            cstm.setString(2, name);
            //System.err.println("sending...");
                    cstm.execute();
        } catch (SQLException ex) {
            Error_logger(ex, "dump_xmlFILE_toDB");
            return false;
        }
        return true;
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
            Error_logger(ex, "readLocalFile");
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
        check_files(false);
    }

    public void check_files(boolean fromRemote) {
        getSettings();//read settings

        switch (settings.get("access_type").toLowerCase()) {
            case "remote":
                processRemoteFolder();
                return;
            case "ftp":
                if (!fromRemote) {
                    new FTP_FileProcessing(settings);
                    return;
                }
                break;
            case "local":
                break;
            default:
                Error_logger(new Exception("Unsupported settings type:" + settings.get("access_type")), "check_files");
                return;
        }

        //else process files on a local directory
        String get_folderpath = settings.get(FOLDER);
        if (fromRemote) {
            get_folderpath = settings.get("working_dir");
        }

        //check if its provided
        if (get_folderpath.trim().isEmpty()) {
            get_folderpath = "workspace";
        }

        File folder = new File(get_folderpath);
        if (!folder.exists()) {
            Error_logger(null, "File folder not found:" + folder.getPath(), true);
            return;
        }

        //filter xml files
        //System.err.println(folder.listFiles(new XMLFileFilter()).length + " new Files found");
        int new_files = folder.listFiles(new XMLFileFilter()).length;
        Error_logger(null, new_files + " new Files found", true);

        if (new_files > 0) {
            Connect();
        } else {
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
            Error_logger(null, "Processing file->" + (count++), true);
            try {
                if(dump_xmlFILE_toDB(fileEntry.getName(), readLocalFile(fileEntry.getPath())))
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
            return file.getName().toLowerCase().endsWith("xml");
        }
    }

    public static Iam_services getInstance() {
        if (instance == null) {
            instance = new Iam_services();
        }
        return instance;
    }
    
    public void check_and_process_inboundDATA(){
         new art_adjdoc_con().generateXML();
         new inv_detail_credit().generateXML();
         new inv_detail_credit(true).generateXML();
         new pos_fi_actual_XML().generateXML();
    }

    public void upload_inboundXMLFiles(String fileName, String type,String docNum) {
        String func="process_inbound";
        StatusLogger dbLogger = null;
        int id=0;
        try {
            new File("inbound_generated", "processed").mkdirs();
            dbLogger=StatusLogger.getInstance();
            id=dbLogger.Log_start(new File(fileName), type);
            switch (settings.get("access_type").toLowerCase()) {
                case "remote":
                    processRemoteFolder();
                    break;
                case "ftp":
                    //wait
                    break;
                case "local": 
                    File localInbound=new File(settings.get(FOLDER), "inbound");
                    localInbound.mkdirs();
                    Files.copy(new File(fileName).toPath(), new File(localInbound, new File(fileName).getName()).toPath());                    
                
                break;
                default:
                    Error_logger(new Exception("Unsupported settings type:" + settings.get("access_type")), "check_files");
                    return;
            }
            Files.move(new File(fileName).toPath(),
                    new File(new File("inbound_generated", "processed"), new File(fileName).getName()).toPath()
            );                    
        } catch (Exception ex) {
            Error_logger(ex, func);
            dbLogger.Log_error(id);
            return;
        }
        dbLogger.Log_success(id, docNum);         
    }
}
