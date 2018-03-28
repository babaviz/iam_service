/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services.xmlprocessing;

/**
 *
 * @author user
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
 * @author Crunchify.com
 */
public class CreateXMLElements {
    
    private static CreateXMLElements instance;
    
    public static CreateXMLElements getInstance(){
        if(instance==null)
            instance=new CreateXMLElements();
        
        return instance;
    }

    public void buildDoc(String[] args) {
        DocumentBuilderFactory icFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder icBuilder;
        try {
            icBuilder = icFactory.newDocumentBuilder();
            Document doc = icBuilder.newDocument();
            Element mainRootElement = doc.createElementNS("https://crunchify.com/CrunchifyCreateXMLDOM", "Companies");
            doc.appendChild(mainRootElement);

            // append child elements to root element
            Map<String,String> data=new HashMap<>();
            data.put("name", "john");
            data.put("age", "20");
            mainRootElement.appendChild(createRecordFields(doc, data,"company"));

            // output DOM XML to console 
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult console = new StreamResult(System.out);
            transformer.transform(source, console);

            System.out.println("\nXML DOM Created Successfully..");

        } catch (Exception e) {
            iam_services.Iam_services.getInstance().Error_logger(e, "buildDoc");
        }
    }

     public Node createRecordFields(Document doc, Map<String,String> fields,String RecordName) {
         return createRecordFields(doc, fields, RecordName, new ArrayList<>());
     }
    
    public Node createRecordFields(Document doc, Map<String,String> fields,String RecordName,List<String> exemption) {
        Element record = doc.createElement(RecordName);
        fields.entrySet().forEach((entry) -> {
            if(!exemption.contains(entry.getKey()))
                record.appendChild(creatElement(doc,entry.getKey(), entry.getValue()));
        });
        return record;
    }

    // utility method to create text node
    private Node creatElement(Document doc,String name, String value) {
        Element node = doc.createElement(name);
        if(value==null || value.isEmpty())value=" ";
        node.appendChild(doc.createTextNode(value));
        return node;
    }
    
    
    public  <T> Stream<List<T>> batches(List<T> source, int length) {
        if (length <= 0)
            throw new IllegalArgumentException("length = " + length);
        int size = source.size();
        if (size <= 0)
            return Stream.empty();
        int fullChunks = (size - 1) / length;
        return IntStream.range(0, fullChunks + 1).mapToObj(
            n -> source.subList(n * length, n == fullChunks ? size : (n + 1) * length));
    }
    
}
