/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services.xmlprocessing;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author user
 */
public class inv_detail_credit {
        private String 
                parentTable =            "CNB_IAM_IN_E1WPU01",
                subRecordsTable1 = "CNB_IAM_IN_E1WPU02",
                subRecordsTable2 = "CNB_IAM_IN_E1WPU05",
                subRecordsTable3 = "CNB_IAM_IN_E1WPU04",
                subRecordsTable4 = "CNB_IAM_IN_E1WXX01",
                
                link_key1 = "PACKAGE_ID",
                link_key2="ARTNR";
        
    private boolean walkin;

    public inv_detail_credit(boolean summery) {
        this.walkin = summery;
    }

    public inv_detail_credit() {
        walkin=false;
    }

    public void generateXML() {
        new File("inbound_generated").mkdir();
        DocumentBuilderFactory icFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder icBuilder;        
        Date date = new Date();
        String dateFormated=new SimpleDateFormat("yyyyMMdd_HHmmss").format(date);
        String docNum=new Date().getTime()+"";
        
        try {
            icBuilder = icFactory.newDocumentBuilder();
            Document doc = icBuilder.newDocument();
            Element mainRootElement = doc.createElement("WPUUMS01");
            doc.appendChild(mainRootElement);
            Element IDOC = doc.createElement("IDOC");
            mainRootElement.appendChild(IDOC);
            addHeaderRow(doc, IDOC, dateFormated,docNum);
            //prepare records
            List<Map<String, String>> dbResMap = XmlDB_funcs.getInstance().QueryDB(parentTable, null);
            if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty records", true);
                return;
            }
            dbResMap.forEach((row) -> {
                Node record = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPU01");
                addSubrecords(doc, record, row.get(link_key1));
                IDOC.appendChild(record);
            });
            // output DOM XML to file
            String filename = "inbound_generated"+System.getProperty("file.separator")+(walkin?"Inv-summary-CASH":"Inv-detail-Credit") + dateFormated + ".xml";
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            DOMSource source = new DOMSource(doc);
            StreamResult output = new StreamResult(new File(filename));
            transformer.transform(source, output);

            System.out.println("\nXML DOM Created Successfully..");
            iam_services.Iam_services.getInstance().upload_inboundXMLFiles(filename,walkin?"Inv-summary-CASH":"Inv-detail-Credit",docNum);
             
        } catch (Exception e) {
            iam_services.Iam_services.getInstance().Error_logger(e, "buildDoc");
        }
    }

    private void addSubrecords(Document doc, Node record, String key) {
        try {
            Map<String, String> where = new HashMap<>();
            where.put(link_key1, key);
            List<Map<String, String>> dbResMap = XmlDB_funcs.getInstance().QueryDB(subRecordsTable1, where);
            if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records", true);
                return;
            }
            dbResMap.forEach((row) -> {
                 Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPU02");
                 addSubOfSubrecords(doc, sub, row.get(link_key2));
                 record.appendChild(sub);
            });

        } catch (Exception e) {
             iam_services.Iam_services.getInstance().Error_logger(e, "addSubrecords");
        }
    }
    
     private void addSubOfSubrecords(Document doc, Node record, String key) {
        try {
            Map<String, String> where = new HashMap<>();
            where.put(link_key2, key);
            List<Map<String, String>> dbResMap1 = XmlDB_funcs.getInstance().QueryDB(subRecordsTable2, where);
            List<Map<String, String>> dbResMap2 = XmlDB_funcs.getInstance().QueryDB(subRecordsTable3, where);           
            if (dbResMap1.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records=>1", true);
            }
            if (dbResMap2.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records=>3", true);
            }
            
            dbResMap1.forEach((row) -> {
                Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPU05");
                record.appendChild(sub);
            });
            
            dbResMap2.forEach((row) -> {
                Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPU04");
                record.appendChild(sub);
            });
            
            if(walkin){
                List<Map<String, String>> dbResMap3 = XmlDB_funcs.getInstance().QueryDB(subRecordsTable4, where);

               if (dbResMap3.isEmpty()) {
                   iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records=>3", true);
               }

               dbResMap3.forEach((row) -> {
                   Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WXX01");
                   record.appendChild(sub);
               });
            }
        } catch (Exception e) {
             iam_services.Iam_services.getInstance().Error_logger(e, "addSubOfSubrecords");
        }
    }
    
    private void addHeaderRow(Document doc,Element IDOC,String dateFormated, String docNum){
        Map<String,String> header=new HashMap<>();       
        header.put("MANDT", 100+"");
        header.put("DOCREL", "700");
        header.put("STATUS", "30");
        header.put("DIRECT", 2+"");
        header.put("MESTYP", "WPUUMS01");
        header.put("MESTYP", "WPUUMS");
        header.put("SNDPOR", "WPUx");
        header.put("SNDPRT", "KU");
        header.put("SNDPRN", "2010");
        header.put("RCVPOR", "SAPECP");
        header.put("RCVPRT", "KU");
        header.put("RCVPRN", "SAP");
        header.put("TABNAM", "EDI_DC40");
        header.put("IDOCTYP", "WPUUMS01");
        header.put("CREDAT", dateFormated.split("_")[0]);
        header.put("CRETIM", dateFormated.split("_")[1]);
        header.put("SERIAL", dateFormated.replace("_", ""));
        header.put("DOCNUM", docNum);
        Node record = CreateXMLElements.getInstance().createRecordFields(doc, header, "EDI_DC40");
        IDOC.appendChild(record);
    }

   /* public static void main(String[] args) {
        new inv_detail_credit().generateXML();
    }*/
}
