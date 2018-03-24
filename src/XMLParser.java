/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author user
 */
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLParser {
    private String pre_Table="CNB_IAM_IN_";
    private String key="SNo";
    private String key2="ARTNR";
    private String key_value;
    
    private Map<String,String> keys=new HashMap<>();
    
    public static void main(String[] args) {
        XMLParser parser=new XMLParser();
        try {
            File file = new File("C:\\Users\\user\\Documents\\DENNIS\\SAP_Inbound\\Z_all\\POS_FI_Actual_1030_20170111-061432-520_VI.xml");
            DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = dBuilder.parse(file);
            //System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
            if (doc.hasChildNodes()) {
                parser.printNote(doc.getChildNodes(),doc.getNodeName());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void printNote(NodeList nodeList,String parent) {
        Map<String,String> data=new HashMap<>();
        
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node tempNode = nodeList.item(count);
            // make sure it's element node. 
            //System.out.println("\n");
                if (tempNode.hasChildNodes() && tempNode.getChildNodes().getLength()>1) {
                    // loop again if has child nodes
                    //System.err.println(tempNode.getNodeName());
                    printNote(tempNode.getChildNodes(),tempNode.getNodeName());
                }else{
                    data.put(
                            tempNode.getNodeName(), 
                            tempNode.getTextContent());
                    if(tempNode.getNodeName().equalsIgnoreCase(key)){
                        keys.put(key, tempNode.getTextContent());
                    }else if( tempNode.getNodeName().equalsIgnoreCase(key2)){
                        keys.put(key2, tempNode.getTextContent());
                    }
                }

        }
        
        
        //check if parent has the key
            
        System.out.println("\n\n"+parent+"\n-----------------------"+data);
        dumpToDB(data, parent);
    }
    
    private void dumpToDB(Map<String,String> data,String parent){
        //prepare query
        
        String query="INSERT INTO "+pre_Table+parent+" ";
        String cols="(",vals=" VALUES(";
        int colcount=0;
        List<String> col_List=new ArrayList<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            if(!key.toLowerCase().contains("#text")){
                cols+=key+",";
                vals+="?,";
                colcount++;
                col_List.add(key);
            }
        }
        if(colcount==0){
            iam_services.Iam_services.getInstance().Error_logger(null, "Empty data for "+parent, true);
            return;
        }
          
        //set key_link
        if(keys.get(key) !=null && data.get(key)==null /*&& parent.equals("E1WPU02")*/){
            data.put(key, keys.get(key));
            cols+=key+",";
            vals+="?,";
            colcount++;
            col_List.add(key);
            
        }/*else if(keys.get(key2) !=null && data.get(key2)==null && !parent.equalsIgnoreCase("E1WPU01")) {
            data.put(key2, keys.get(key2));
            cols+=key2+",";
            vals+="?,";
            colcount++;
            col_List.add(key2);
        }*/
        Connection conn=iam_services.Iam_services.getInstance().Connect();
        //process data
        cols=cols.replaceFirst(".$",")");
        vals=vals.replaceFirst(".$",")");
        try{
        PreparedStatement pstm=conn.prepareStatement(query+cols+vals);
        
         iam_services.Iam_services.getInstance().Error_logger(null, "cols:"+cols+" \nvals:"+vals, true);
         
         int count=1;
         for (String colName:col_List) {
            String key =colName;
            String val=data.get(colName);
            if(!key.toLowerCase().contains("#text")){
                //System.err.println("data: "+count+"#"+entry);
                pstm.setString(count++, (val==null || val.isEmpty())?" ":val);
            }
        }
         
        //System.err.println("cols:"+colcount+" vals:"+count);
            
         pstm.execute();
         if(data.get(key) !=null)
            key_value=data.get(key);
         
        }catch(Exception ex){ex.printStackTrace();}
    }
}
