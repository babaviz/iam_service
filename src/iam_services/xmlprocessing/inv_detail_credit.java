/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services.xmlprocessing;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
                subRecordsTable5 = "CNB_IAM_IN_E1WPU03",
                
                link_key1 = "PACKAGE_ID",
                link_key2="ARTNR",
                link_key3="Dnote_Detail_ID";
       
        
    private boolean walkin;
    private String walkin_surffix="_summary";
    private ArrayList<String> exemptions=new ArrayList<>(Arrays.asList("id", "DATE_STAMP","READ_FLG","BRNCH_ID","Dnote_Detail_ID"));
    private Map<String,String> attributes=new HashMap<>();
    
    public inv_detail_credit(boolean summary) {
        this.walkin = summary;
        attributes.clear();
        attributes.put("SEGMENT", "");
    }

    public inv_detail_credit() {
        walkin=false;
        attributes.clear();
        attributes.put("SEGMENT", "");
    }

    public void generateXML() {
            try {
                ///List<Map<String, String>> dbResMap=new ArrayList<>();
                
                String tb=parentTable+(walkin?walkin_surffix:"");
                String query="SELECT DISTINCT CONVERT(DATE,BELEGDATUM) AS day FROM "+tb+ " WHERE READ_FLG=0";
                //select distict date
                List<String> dateList = XmlDB_funcs.getInstance().getDistict_byDate("day",query);
                
                //select distict invoice number for each date
                String column=walkin?"BRNCH_ID":"PACKAGE_ID";
                List<List<Map<String, String>>> accu_data =new ArrayList<>();
                for(String date:dateList){                    
                    query="SELECT DISTINCT "+column+"  FROM "+tb+ " WHERE READ_FLG=0 AND CONVERT(DATE,BELEGDATUM)=CONVERT(DATE,'"+date+"') ";
                    List<String> distinct_inv_bra_by_day = XmlDB_funcs.getInstance().getDistict_byDate(column,query);   
                    for(String col:distinct_inv_bra_by_day){
                        Map<String,String> in_where=new HashMap<>();
                        in_where.put(column, col);
                        in_where.put("no","CONVERT(DATE,BELEGDATUM)=CONVERT(DATE,'"+date+"')");
                        accu_data.add(XmlDB_funcs.getInstance().QueryDB(parentTable+(walkin?walkin_surffix:""), in_where));
                    }
                }
                //now generate xml
                //dbResMap = XmlDB_funcs.getInstance().QueryDB(parentTable+(walkin?walkin_surffix:""), null);
                accu_data.forEach(list->{genarate_XMLDOC(list);});
               /* if(dbResMap.size()>(walkin?600:1)){
                   CreateXMLElements.getInstance().batches(dbResMap,(walkin?600:1)).forEach(list->{genarate_XMLDOC(list);});
                }else{
                    genarate_XMLDOC(dbResMap);
                }*/
            } catch (Exception ex) {
                iam_services.Iam_services.getInstance().Error_logger(ex, "generateXML");
            }
    }    
    
    private void genarate_XMLDOC(List<Map<String, String>> dbResMap){
         new File("inbound_generated").mkdir();
        DocumentBuilderFactory icFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder icBuilder;        
        Date date = new Date();
        String dateFormated=new SimpleDateFormat("yyyyMMdd_HHmmssS").format(date);
        String docNum=new Date().getTime()+"";
        
        try {
             if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty records", true);
                return;
            }
             
            icBuilder = icFactory.newDocumentBuilder();
            Document doc = icBuilder.newDocument();
            Element mainRootElement = doc.createElement("WPUUMS01");
            doc.appendChild(mainRootElement);
            Element IDOC = doc.createElement("IDOC");
            IDOC.setAttribute("BEGIN", "0");
            mainRootElement.appendChild(IDOC);
            //prepare records
            
           
            String brand_id="not found";
                        
            try{
                brand_id=XmlDB_funcs.getInstance().getSAPBranchMapping(dbResMap.get(0).get("BRNCH_ID"));
            }catch(Exception ex){
                   iam_services.Iam_services.getInstance().Error_logger(ex, "genarate_XMLDOC");
            }
            
            addHeaderRow(doc, IDOC, dateFormated,docNum,brand_id);
            
            dbResMap.forEach((row) -> {
                Node record = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPU01",exemptions,attributes);
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
            iam_services.Iam_services.getInstance().Error_logger(e, "genarate_XMLDOC");
        }
    }

    private void addSubrecords(Document doc, Node record, String key) {
        try {
            Map<String, String> where = new HashMap<>();
            where.put(link_key1, key);
            List<String> ex=new ArrayList<>();
            ex.addAll(exemptions);ex.add(link_key1);
            
            List<Map<String, String>> dbResMap = XmlDB_funcs.getInstance().QueryDB(subRecordsTable1+(walkin?walkin_surffix:""), where);
            if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records", true);
                return;
            }
            dbResMap.forEach((row) -> {
                 Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPU02",ex,attributes);                 
                 addSubOfSubrecords(doc, sub, row.get(link_key1),row.get(link_key2), walkin?"":row.get(link_key3));
                 record.appendChild(sub);
            });

        } catch (Exception e) {
             iam_services.Iam_services.getInstance().Error_logger(e, "addSubrecords");
        }
    }
    
     private void addSubOfSubrecords(Document doc, Node record, String key1,String key2,String key3) {
        try {
            Map<String, String> where = new HashMap<>();
            where.put(link_key2, key2);
            where.put(link_key1, key1);
            if(!walkin){//if invoice details
                where.put(link_key3, key3);
            }
            
            ArrayList<String> ex=new ArrayList<>();
            ex.addAll(exemptions);ex.add(link_key1);
            ex.add(link_key2);
            
            //record 1
           List<Map<String, String>> dbResMap4 = XmlDB_funcs.getInstance().QueryDB(subRecordsTable5+(walkin?walkin_surffix:""), where);

           if (dbResMap4.isEmpty()) {
               iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records=>4: where:"+where.toString(), true);
           }

           dbResMap4.forEach((row) -> {
               Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPU03",ex,attributes);
               record.appendChild(sub);
           });
            dbResMap4.clear();//release memory
            
             //record 2
            List<Map<String, String>> dbResMap1 = XmlDB_funcs.getInstance().QueryDB(subRecordsTable2+(walkin?walkin_surffix:""), where);
           
            if (dbResMap1.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records=>1", true);
            }
            
             dbResMap1.forEach((row) -> {
                Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPU05",ex,attributes);
                record.appendChild(sub);
            });
            dbResMap1.clear();//release memory
            
             //record 2
            List<Map<String, String>> dbResMap2 = XmlDB_funcs.getInstance().QueryDB(subRecordsTable3+(walkin?walkin_surffix:""), where);           
            if (dbResMap2.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records=>3", true);
            }           
            
            dbResMap2.forEach((row) -> {
                Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPU04",ex,attributes);
                record.appendChild(sub);
            });
            dbResMap2.clear();//release memory
             
             //record 4
            List<Map<String, String>> dbResMap3 = XmlDB_funcs.getInstance().QueryDB(subRecordsTable4+(walkin?walkin_surffix:""), where);

           if (dbResMap3.isEmpty()) {
               iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records=>3", true);
           }

           dbResMap3.forEach((row) -> {
               Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WXX01",ex,attributes);
               record.appendChild(sub);
           });
           dbResMap3.clear();//release memory
              
        } catch (Exception e) {
             iam_services.Iam_services.getInstance().Error_logger(e, "addSubOfSubrecords");
        }
    }
    
    private void addHeaderRow(Document doc,Element IDOC,String dateFormated, String docNum,String branch_id){
        Map<String,String> header=new HashMap<>();       
        header.put("MANDT", 100+"");
        header.put("DOCREL", "700");
        header.put("STATUS", "30");
        header.put("DIRECT", 2+"");
        header.put("MESTYP", "WPUUMS01");
        header.put("MESTYP", "WPUUMS");
        header.put("SNDPOR", "WPUx");
        header.put("SNDPRT", "KU");
        header.put("SNDPRN", branch_id);
        header.put("RCVPOR", "SAPECP");
        header.put("RCVPRT", "KU");
        header.put("RCVPRN", "SAP");
        header.put("TABNAM", "EDI_DC40");
        header.put("IDOCTYP", "WPUUMS01");
        header.put("CREDAT", dateFormated.split("_")[0]);
        header.put("CRETIM", dateFormated.split("_")[1]);
        header.put("SERIAL", dateFormated.replace("_", ""));
        header.put("DOCNUM", docNum);
        Node record = CreateXMLElements.getInstance().createRecordFields(doc, header, "EDI_DC40",new ArrayList<>(),attributes);
        IDOC.appendChild(record);
    }
    
    

   /* public static void main(String[] args) {
        new inv_detail_credit().generateXML();
    }*/
}
