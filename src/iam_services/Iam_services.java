/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services;

import api.Api_executor;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

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
    final String IN_FOLDER = "inbound_xml_generateg_folder";
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
            //create log folder
            new File("logs").mkdir();
            
            String dateString = new SimpleDateFormat("yyyy_MM_dd").format(new Date());
            fh = new FileHandler("logs"+System.getProperty("file.separator")+"iam_service_err_" + dateString + "_%g.log", 5 * 1024 * 1024, 20, true);//new FileHandler("iam_service_err_" + dateString + ".log", true);
            logger.addHandler(fh);
        } catch (IOException | SecurityException ex) {
            Logger.getLogger(Iam_services.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void doWork() {
        int wait = 10;
        while (true) {
            try {
                if (!lock) {
                    logServiceStart();//show service started
                    check_files();
                    check_and_process_inboundDATA();
                    logServiceEnd();
                }
                try {
                    wait = Integer.parseInt(settings.get("duration").trim());
                } catch (Exception ex) {
                    Error_logger(ex, "doWork");
                }
                Thread.sleep(1000 * 60 * wait);
            } catch (InterruptedException e) {
                Error_logger(e, "doWork");
                Error_logger(null, "Sytem Interrupted", true);
            }
            Error_logger(null, "Sytem system alive", true);
        }
    }

    public Connection Connect() {
        if (conn != null) {
            return conn;
        }

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

    public boolean dump_xmlFILE_toDB(File xmlFile, String xml) {
        try {
            CallableStatement cstm = getStatement(xmlFile.getName());
            if (xml.isEmpty()) {
                Error_logger(new Exception("Empty xml file content:" + xmlFile), "dump_xmlFILE_toDB");
                return true;
            }

            if (cstm == null) {
                Error_logger(new Exception("System couldn't determine category of xml file:" + xmlFile), "dump_xmlFILE_toDB");
                return false;
            }

            cstm.setNString(1, xml);
            cstm.setString(2, xmlFile.getName());
            cstm.execute();

            //if its ecommerce, send json as well
            if (xmlFile.getName().toLowerCase().contains("order")) {
                return XML_to_JSON(xml,xmlFile);
            }

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
        } else if (fileName.toLowerCase().contains("order")) {
            cstm = conn.prepareCall("{call sp_Read_Ecomm_Order_XMLFILE(?,?)}");
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
            Error_logger(null, "Processing file->" + (count++) + "[" + fileEntry.getPath() + "]", true);
            try {
                if (dump_xmlFILE_toDB(fileEntry, readLocalFile(fileEntry.getPath()))) {
                    Files.move(Paths.get(fileEntry.getPath()), Paths.get(new File(processed_dir, fileEntry.getName()).getPath()), StandardCopyOption.REPLACE_EXISTING);
                }
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

    public void check_and_process_inboundDATA() {
        new art_adjdoc_con().generateXML();
        new inv_detail_credit().generateXML();
        new inv_detail_credit(true).generateXML();
        new pos_fi_actual_XML().generateXML();
    }

    public void upload_inboundXMLFiles(String fileName, String type, String docNum) {
        String func = "process_inbound";
        StatusLogger dbLogger = null;
        int id = 0;
        try {
            new File("inbound_generated", "processed").mkdirs();
            dbLogger = StatusLogger.getInstance();
            id = dbLogger.Log_start(new File(fileName), type);
            switch (settings.get("access_type").toLowerCase()) {
                case "remote":
                    processRemoteFolder();
                    break;
                case "ftp":
                    FTP_FileProcessing instance1 = FTP_FileProcessing.getinstance(settings);
                    instance1.uploadFile(new File(fileName).getPath());
                    break;
                case "local":
                    File localInbound = new File(settings.get(IN_FOLDER), "inbound");
                    localInbound.mkdirs();
                    Files.copy(new File(fileName).toPath(), new File(localInbound, new File(fileName).getName()).toPath());

                    //move generated files
                    Files.move(new File(fileName).toPath(),
                            new File(new File("inbound_generated", "processed"), new File(fileName).getName()).toPath()
                    );
                    break;
                default:
                    Error_logger(new Exception("Unsupported settings type:" + settings.get("access_type")), "check_files");
                    return;
            }

        } catch (Exception ex) {
            Error_logger(ex, func);
            dbLogger.Log_error(id);
            return;
        }
        dbLogger.Log_success(id, docNum);
    }

    public boolean XML_to_JSON(String xml,File file) {
        int  id =0; 
        StatusLogger dbLogger = null;
        try {
             dbLogger= StatusLogger.getInstance();
            id=dbLogger.Log_start(file, "Ecomm_Order");
            
            JSONObject xmlJSONObj = XML.toJSONObject(xml);
            //String json_indented = xmlJSONObj.toString();
            //System.out.println(jsonPrettyPrintString);
           // Error_logger(null, json_indented, true);
            return mapJSONData(xmlJSONObj);
            // String response = Api_executor.getInstance(settings).sendPostRequest("", json_indented);
           // Error_logger(null, response, true);
        } catch (Exception je) {
            Error_logger(je, "XML_to_JSON");
            if(dbLogger !=null){
                dbLogger.Log_error(id);
            }
            return false;
        }

    }

    private boolean mapJSONData(JSONObject convertedXML) {
        String func="mapJSONData";
        try {
            Object obj = convertedXML.getJSONObject("ns1:MT_ECOMM_INV").get("Record");
            // `instanceof` tells us whether the object can be cast to a specific type
            if (obj instanceof JSONArray) {
                // it's an array
                JSONArray recordsArray = (JSONArray) obj;
                // do all kinds of JSONArray'ish things with urlArray
                 Error_logger(null, recordsArray.length()+"->Records found!", true);
                 for (int i = 0; i < recordsArray.length(); i++) {
                     createJSON((JSONObject) recordsArray.get(i));
                }
            } else {
                // if you know it's either an array or an object, then it's an object
                JSONObject record = (JSONObject) obj;
                // do objecty stuff with urlObject
                 Error_logger(null,"Only one record found!", true);
                 createJSON(record);
            }
        } catch (Exception ex) {
            Error_logger(ex, func);
            return false;
        }
        
        return true;
    }

    private void createJSON(JSONObject jsonRecord) throws Exception{
        JSONObject parent=new JSONObject();
        parent.put("currency", new JSONObject("{\"id\":1}"));//get currency
        
        Map<String,String> requestParams=Api_executor.getInstance(settings).fetchRequestParams(jsonRecord.getString("StoreName"));
        
        //create customer
        JSONObject cuJSONObject=new JSONObject();
        cuJSONObject.put("contacts", new JSONArray());
        cuJSONObject.put("GroupId", 0);
        cuJSONObject.put("active", true);
        cuJSONObject.put("authStatus", false);
        cuJSONObject.put("awaitingLevel", new JSONArray());
        cuJSONObject.put("id", jsonRecord.get("ClientID"));
        cuJSONObject.put("loyaltyCustomer", false);
        cuJSONObject.put("creditCustomer", true);
        cuJSONObject.put("customerType", 0);
        parent.put("customer", cuJSONObject);
        
        //branch 
        JSONObject brJSONObject=new JSONObject();
        brJSONObject.put("id", requestParams.get("branchid"));//get brach id
        brJSONObject.put("hierarchyLevelId", 0);
        parent.put("branch", brJSONObject);        
        
        parent.put("structure", new JSONObject("{\"id\":1}"));
        parent.put("dNoteRemarks", "");
        
        parent.put("dNoteDate", new SimpleDateFormat("yyyyMMdd").parse(jsonRecord.getString("OrderDate")).getTime());
        parent.put("dNoteItemCount", jsonRecord.get("TotalLineNo")); //item count
        parent.put("salesManId", requestParams.get("salesManId"));//get salesManID
        parent.put("dNoteExchangeRate", 1);//fetch this from db
        parent.put("dNoteNetAmount", jsonRecord.get("TotalAmount"));
        parent.put("dNoteTaxAmount", jsonRecord.get("VATAmount"));
        parent.put("dNoteTotalAmount", jsonRecord.get("TotalAmount"));
        parent.put("dNoteSysDate", new Date().getTime());
        parent.put("terminalId", settings.get("ecomm_terminal_id"));//provided on setting xml
        parent.put("userId", settings.get("ecomm_user_id"));//provided on setting xml
        parent.put("customerType", 0);
        parent.put("generatedInvoice", true);
        
        //custCreditDetails
       JSONObject custCreditDetails= new JSONObject();
       custCreditDetails.put("id", "3");// not provided
       custCreditDetails.put("openingBalance", 0);// not provided
       custCreditDetails.put("closingBalance", 0);//not provided
       parent.put("custCreditDetails", custCreditDetails);
       parent.put("sessionId", requestParams.get("sessionId"));//fetch session id from api
       parent.put("shiftId", requestParams.get("shiftId"));//fetch shiftid from api
       
       //orderDetails
       JSONArray orderJSON=new JSONArray();
       Object itemDetails=jsonRecord.get("ItemDetail");
       
       JSONArray itemDetailsArray=new JSONArray();
        if (itemDetails instanceof JSONArray) {
            itemDetailsArray=(JSONArray)itemDetails;
        }else{
            itemDetailsArray.put(itemDetails);
        }
       
        for (int i = 0; i < itemDetailsArray.length(); i++) {
            JSONObject itemJSON=(JSONObject) itemDetailsArray.get(i);
            JSONObject mappedItem=new JSONObject();
            JSONObject productJSON=new JSONObject();
            //PRODUCT details
            productJSON.put("id", Api_executor.getInstance(settings).getObjectID(itemJSON.getString("ItemCode"),"product"));//fetch product id from db using ItemCode, not provided
            productJSON.put("workFlowId", 0);
            productJSON.put("code",  itemJSON.get("ItemCode"));
            productJSON.put("description", "");//not provided
            productJSON.put("shortDescription", "");//not provided
            productJSON.put("labelDescription", "");
            productJSON.put("multiSellQuantity", 0);
            productJSON.put("active", true);
            productJSON.put("authStatus",false);
            productJSON.put("effectiveFrom", parent.get("dNoteDate"));
            productJSON.put("isScanned", true);
            productJSON.put("soh", 0);
            productJSON.put("prodSellPrice", 0);
            productJSON.put("prodCostPrice", 0);
            mappedItem.put("product", productJSON);
            
            //end of product detail            
            mappedItem.put("isScanned", true);
            mappedItem.put("packaging", new JSONObject("{\"id\":1,\"scanCodes\":[]}"));
            mappedItem.put("packageQuantity", 1);
            mappedItem.put("quantity", itemJSON.get("Qty"));
            mappedItem.put("packagePrice", itemJSON.get("Price"));
            mappedItem.put("itemRemarks", "");
            mappedItem.put("stockOnHand",0);
            mappedItem.put("discount", 0);
            mappedItem.put("receivedQuantity", 0);
            mappedItem.put("returnQuantity", 0);
            mappedItem.put("transferQuantity", 0);
            mappedItem.put("receiptQuantity", 0);
            mappedItem.put("poOrderedQuantity", 0);
            mappedItem.put("receiptQuantity", 0);
            mappedItem.put("poOrderedQuantity", 0);
            mappedItem.put("grnProdQuantity", 0);
            mappedItem.put("adjustmentType", 0);
            mappedItem.put("taxId", 3707800);//fetch tax id from db, but how?
            mappedItem.put("returnToOriginBranch", false);
            mappedItem.put("status", 0);
            mappedItem.put("costPrice", 0);
            mappedItem.put("taxBreakDown", new JSONObject("{\"taxList\":[],\"taxAmount\":0}"));//tax amount unknown   
            orderJSON.put(mappedItem);
        }
        
        parent.put("orderDetails", orderJSON);
        
        Error_logger(null, parent.toString(),true);
        String createInvoice = Api_executor.getInstance(settings).createInvoice(parent.toString());
        Error_logger(null, createInvoice, true);
        
        JSONObject res=new JSONObject(createInvoice);
        if(res.get("id").toString().equals("0")){
            throw new Exception(res.getString("error"));
        }
    }
    
    private void logServiceStart(){
        Error_logger(null, "\n"
                + "============================\n"
                + "Service Started\n"
                + "============================\n\n", true);
    }
    
    private void logServiceEnd(){
        Error_logger(null, "\n"
                + "***********************************************\n"
                + "#######Service Ended#############\n"
                + "***********************************************\n\n\n\n\n\n", true);
    }
}
