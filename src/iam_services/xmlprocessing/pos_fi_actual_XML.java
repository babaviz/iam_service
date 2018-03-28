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
public class pos_fi_actual_XML {

    private String parentTable = "CNB_IAM_IN_Record",
            subRecordsTable = "CNB_IAM_IN_ItemDetails",
            link_key = "SNo";
    
    private ArrayList<String> exemptions=new ArrayList<>(Arrays.asList("id", "DATE_STAMP"));

    public void generateXML() {
        try {
            List<Map<String, String>> dbResMap = XmlDB_funcs.getInstance().QueryDB(parentTable, null);
            if (dbResMap.size() > 550) {
                CreateXMLElements.getInstance().batches(dbResMap, 550).forEach(list -> {
                    genarate_XMLDOC(list);
                });
            } else {
                genarate_XMLDOC(dbResMap);
            }
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
            Element mainRootElement = doc.createElementNS("http://POS_FI_Upload.com", "ns1:MT_POS_FIUpload");
            doc.appendChild(mainRootElement);
            //prepare records
            if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty records", true);
                return;
            }
            dbResMap.forEach((row) -> {
                Node record = CreateXMLElements.getInstance().createRecordFields(doc, row, "Record",exemptions);
                addSubrecords(doc, record, row.get(link_key));
                mainRootElement.appendChild(record);
            });
            // output DOM XML to file
            String filename = "inbound_generated" + System.getProperty("file.separator") + "POS_FI_Actual_" + dateFormated + ".xml";
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            DOMSource source = new DOMSource(doc);
            StreamResult output = new StreamResult(new File(filename));
            transformer.transform(source, output);

            System.out.println("\nXML DOM Created Successfully..");
            iam_services.Iam_services.getInstance().upload_inboundXMLFiles(filename, "POS_FI_Actual", docNum);
        } catch (Exception e) {
            iam_services.Iam_services.getInstance().Error_logger(e, "buildDoc");
        }
    }

    private void addSubrecords(Document doc, Node record, String key) {
        try {
            Map<String, String> where = new HashMap<>();
            where.put(link_key, key);
            
            List<String> ex=new ArrayList<>();
            ex.addAll(exemptions);ex.add(link_key);
            
            List<Map<String, String>> dbResMap = XmlDB_funcs.getInstance().QueryDB(subRecordsTable, where);
            if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records", true);
            }
            dbResMap.forEach((row) -> {
                Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "ItemDetails",ex);
                record.appendChild(sub);
            });

        } catch (Exception e) {
            iam_services.Iam_services.getInstance().Error_logger(e, "addSubrecords");
        }
    }

    /*public static void main(String[] args) {
        new pos_fi_actual_XML().generateXML();
    }*/
}
