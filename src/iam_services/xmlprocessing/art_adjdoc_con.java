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
public class art_adjdoc_con {

    private String parentTable = "CNB_IAM_IN_E1WPG01",
            subRecordsTable = "CNB_IAM_IN_E1WPG02",
            link_key = "BONNUMMER";

    public void generateXML() {
        new File("inbound_generated").mkdir();
        DocumentBuilderFactory icFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder icBuilder;        
        Date date = new Date();
        String dateFormated=new SimpleDateFormat("yyyyMMdd_HHmmss").format(date);
        
        try {
            icBuilder = icFactory.newDocumentBuilder();
            Document doc = icBuilder.newDocument();
            Element mainRootElement = doc.createElement("WPUUMS01");
            doc.appendChild(mainRootElement);
            Element IDOC = doc.createElement("IDOC");
            mainRootElement.appendChild(IDOC);
            addHeaderRow(doc, IDOC, dateFormated);
            //prepare records
            List<Map<String, String>> dbResMap = XmlDB_funcs.getInstance().QueryDB(parentTable, null);
            if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty records", true);
                return;
            }
            dbResMap.forEach((row) -> {
                Node record = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPG01");
                addSubrecords(doc, record, row.get(link_key));
                IDOC.appendChild(record);
            });
            // output DOM XML to file
            String filename = "inbound_generated"+System.getProperty("file.separator")+"ART_ADJDOC_CON_" + dateFormated + ".xml";
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            DOMSource source = new DOMSource(doc);
            StreamResult output = new StreamResult(new File(filename));
            transformer.transform(source, output);
            System.out.println("\nXML DOM Created Successfully..");
            
            iam_services.Iam_services.getInstance().upload_inboundXMLFiles(filename,"ART_ADJDOC_CON");
            
        } catch (Exception e) {
            iam_services.Iam_services.getInstance().Error_logger(e, "buildDoc");
        }
    }

    private void addSubrecords(Document doc, Node record, String key) {
        try {
            Map<String, String> where = new HashMap<>();
            where.put(link_key, key);
            List<Map<String, String>> dbResMap = XmlDB_funcs.getInstance().QueryDB(subRecordsTable, where);
            if (dbResMap.isEmpty()) {
                iam_services.Iam_services.getInstance().Error_logger(null, "Empty sub-records", true);
            }
            dbResMap.forEach((row) -> {
                Node sub = CreateXMLElements.getInstance().createRecordFields(doc, row, "E1WPG02");
                record.appendChild(sub);
            });

        } catch (Exception e) {
             iam_services.Iam_services.getInstance().Error_logger(e, "addSubrecords");
        }
    }
    
    private void addHeaderRow(Document doc,Element IDOC,String dateFormated){
        Map<String,String> header=new HashMap<>();          
        header.put("MANDT", 100+"");
        header.put("DOCREL", "700");
        header.put("STATUS", "30");
        header.put("DIRECT", 2+"");
        header.put("MESTYP", "WPUWBW");
        header.put("STDMES", "WPUWBW");
        header.put("SNDPOR", "WPUx");
        header.put("SNDPRT", "KU");
        header.put("SNDPRN", "0000002040");
        header.put("RCVPOR", "SAPECP");
        header.put("RCVPRT", "KU");
        header.put("RCVPRN", "2010");
        header.put("TABNAM", "EDI_DC40");
        header.put("IDOCTYP", "WPUWBW01");
        header.put("CREDAT", dateFormated.split("_")[0]);
        header.put("CRETIM", dateFormated.split("_")[1]);
        header.put("DOCNUM", new Date().getTime()+"");
        Node record = CreateXMLElements.getInstance().createRecordFields(doc, header, "EDI_DC40");
        IDOC.appendChild(record);
    }

    public static void main(String[] args) {
        new art_adjdoc_con().generateXML();
    }
}
