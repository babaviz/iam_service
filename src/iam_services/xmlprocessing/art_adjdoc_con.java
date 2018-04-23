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
public class art_adjdoc_con {

    private String parentTable = "CNB_IAM_IN_E1WPG01",
            subRecordsTable = "CNB_IAM_IN_E1WPG02",
            link_key = "BONNUMMER",
            link_key2="BRNCH_ID";
    
    private ArrayList<String> exemptions=new ArrayList<>(Arrays.asList("id", "DATE_STAMP","BRNCH_ID","READ_FLG"));
    private Map<String,String> attributes=new HashMap<>();

    public art_adjdoc_con() {
        attributes.clear();
        attributes.put("SEGMENT", "1");
    }
    
    
    
    public void generateXML() {
         try {
        String tb=parentTable;
                String query="SELECT DISTINCT CONVERT(DATE,BELEGDATUM) AS day FROM "+tb+ " WHERE READ_FLG=0";
                //select distict date
                List<String> dateList = XmlDB_funcs.getInstance().getDistict_byDate("day",query);
                
                //select distict invoice number for each date
                String column="BRNCH_ID";
                List<List<Map<String, String>>> accu_data =new ArrayList<>();
                for(String date:dateList){                    
                    query="SELECT DISTINCT "+column+"  FROM "+tb+ " WHERE READ_FLG=0 AND CONVERT(DATE,BELEGDATUM)=CONVERT(DATE,'"+date+"') ";
                    List<String> distinct_inv_bra_by_day = XmlDB_funcs.getInstance().getDistict_byDate(column,query);   
                    for(String col:distinct_inv_bra_by_day){
                        Map<String,String> in_where=new HashMap<>();
                        in_where.put(column, col);
                        in_where.put("no","CONVERT(DATE,BELEGDATUM)=CONVERT(DATE,'"+date+"')");
                        accu_data.add(XmlDB_funcs.getInstance().QueryDB(parentTable, in_where));
                    }
                }
                //now generate xml
                //dbResMap = XmlDB_funcs.getInstance().QueryDB(parentTable+(walkin?walkin_surffix:""), null);
                accu_data.forEach(list->{genarate_XMLDOC(list);});
        
       
           /* List<Map<String, String>> dbResMap = XmlDB_funcs.getInstance().QueryDB(parentTable, null);
            if (dbResMap.size() > 550) {
                CreateXMLElements.getInstance().batches(dbResMap, 550).forEach(list -> {
                    genarate_XMLDOC(list);
                });
            } else {
                genarate_XMLDOC(dbResMap);
            }*/
        } catch (Exception ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "generateXML");
        }
    }

    public void genarate_XMLDOC(List<Map<String, String>> dbResMap) {
        new File("inbound_generated").mkdir();
        DocumentBuilderFactory icFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder icBuilder;
        Date date = new Date();
        String dateFormated = new SimpleDateFormat("yyyyMMdd_HHmmssS").format(date);
        String docNum = new Date().getTime() + "";
        try {
            icBuilder = icFactory.newDocumentBuilder();
            Document doc = icBuilder.newDocument();
            Element mainRootElement = doc.createElement("WPUWBW01");//wpuwbw01
            doc.appendChild(mainRootElement);
            Element IDOC = doc.createElement("IDOC");
            IDOC.setAttribute("BEGIN", "1");
            mainRootElement.appendChild(IDOC);
            //addHeaderRow(doc, IDOC, dateFormated, docNum);
            //prepare records
            if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty records", true);
                return;
            }
            
            
            String brand_id="not found";
                        
            try{
                brand_id=XmlDB_funcs.getInstance().getSAPBranchMapping(dbResMap.get(0).get("BRNCH_ID"));
            }catch(Exception ex){
                   iam_services.Iam_services.getInstance().Error_logger(ex, "genarate_XMLDOC");
            }
            
            addHeaderRow(doc, IDOC, dateFormated,docNum,brand_id);
            
            dbResMap.forEach((row) -> {
                Node record = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPG01",exemptions,attributes);
                addSubrecords(doc, record, row.get(link_key),row.get(link_key2));
                IDOC.appendChild(record);
            });
            // output DOM XML to file
            String filename = "inbound_generated" + System.getProperty("file.separator") + "ART_ADJDOC_CON_" + dateFormated + ".xml";
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            DOMSource source = new DOMSource(doc);
            StreamResult output = new StreamResult(new File(filename));
            transformer.transform(source, output);
            System.out.println("\nXML DOM Created Successfully..");

            iam_services.Iam_services.getInstance().upload_inboundXMLFiles(filename, "ART_ADJDOC_CON", docNum);

        } catch (Exception e) {
            iam_services.Iam_services.getInstance().Error_logger(e, "buildDoc");
        }
    }

    private void addSubrecords(Document doc, Node record, String key,String key2) {
        try {
            Map<String, String> where = new HashMap<>();
            where.put(link_key, key);
            where.put(link_key2, key2);
            
            List<String> ex=new ArrayList<>();
            ex.addAll(exemptions);
            ex.add(link_key);
            
            List<Map<String, String>> dbResMap = XmlDB_funcs.getInstance().QueryDB(subRecordsTable, where);
            if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records", true);
            }
            dbResMap.forEach((row) -> {
                Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPG02",ex,attributes);
                record.appendChild(sub);
            });

        } catch (Exception e) {
            iam_services.Iam_services.getInstance().Error_logger(e, "addSubrecords");
        }
    }

    private void addHeaderRow(Document doc, Element IDOC, String dateFormated, String docNum,String bran_id) {
        Map<String, String> header = new HashMap<>();
        header.put("MANDT", 100 + "");
        header.put("DOCREL", "700");
        header.put("STATUS", "30");
        header.put("DIRECT", 2 + "");
        header.put("MESTYP", "WPUWBW");
        header.put("STDMES", "WPUWBW");
        header.put("SNDPOR", "WPUx");
        header.put("SNDPRT", "KU");
        header.put("SNDPRN", bran_id);
        header.put("RCVPOR", "SAPECP");
        header.put("RCVPRT", "KU");
        header.put("RCVPRN", "2010");
        header.put("TABNAM", "EDI_DC40");
        header.put("IDOCTYP", "WPUWBW01");
        header.put("CREDAT", dateFormated.split("_")[0]);
        header.put("CRETIM", dateFormated.split("_")[1]);
        header.put("DOCNUM", docNum);
        Node record = CreateXMLElements.getInstance().createRecordFields(doc, header, "EDI_DC40",new ArrayList<>(),attributes);
        IDOC.appendChild(record);
    }

    /*public static void main(String[] args) {
        new art_adjdoc_con().generateXML();
    }*/
}
