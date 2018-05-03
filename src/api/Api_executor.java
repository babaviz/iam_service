package api;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

//import net.sf.json.JSONArray;
//import net.sf.json.JSONObject;

public class Api_executor {

   // private String USERNAME = "[your username]";
   // private String PASSWORD = "[your shared secret]";
    private String ENDPOINT = ""; //base url for the ecommerce endpoint
    private Map<String, String> settings = new HashMap<>();
    private static Api_executor instance;
    
    public static Api_executor getInstance(Map<String, String> settings){
        if(instance==null){
            instance=new Api_executor(settings);
        }        
        return instance;
    }
    
    private Api_executor(Map<String, String> settings) {
        this.ENDPOINT=settings.get("ecomm_endpoint");
        this.settings=settings;
    }

    public String callMethod(String method, String data) throws IOException {
        URL url = new URL(ENDPOINT + (method.isEmpty()?"":"/" + method));
        //URLConnection connection = url.openConnection();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        
        addHeaderParams(connection);

        connection.setDoOutput(true);
        OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
        wr.write(data);
        wr.flush();

        InputStream in = connection.getInputStream();
        BufferedReader res = new BufferedReader(new InputStreamReader(in, "UTF-8"));

        StringBuffer sBuffer = new StringBuffer();
        String inputLine;
        while ((inputLine = res.readLine()) != null) {
            sBuffer.append(inputLine);
        }

        res.close();

        return sBuffer.toString();
    }
    
    //add header request paramenters
    private void addHeaderParams(HttpURLConnection conn){
        conn.setRequestProperty("FC_ActiveLanguage", settings.get("ecomm_locale_id"));//localeId
        conn.setRequestProperty("FC_Module", "POS");
        conn.setRequestProperty("FC_Authorization", settings.get("ecomm_authorization"));//userid
        conn.setRequestProperty("FC_Tenant_Entity", settings.get("ecomm_tenant_id"));//tenantId
    }

    private String getHeader() throws UnsupportedEncodingException {
        byte[] nonceB = generateNonce();
        String nonce = base64Encode(nonceB);
        String created = generateTimestamp();
        //String password64 = getBase64Digest(nonceB, created.getBytes("UTF-8"), PASSWORD.getBytes("UTF-8"));
        StringBuffer header = new StringBuffer("UsernameToken Username=\"");
        //header.append(USERNAME);
        header.append("\", ");
        header.append("PasswordDigest=\"");
        //header.append(password64.trim());
        header.append("\", ");
        header.append("Nonce=\"");
        header.append(nonce.trim());
        header.append("\", ");
        header.append("Created=\"");
        header.append(created);
        header.append("\"");
        return header.toString();
    }

    private byte[] generateNonce() {
        String nonce = Long.toString(new Date().getTime());
        return nonce.getBytes();
    }

    private String generateTimestamp() {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        return dateFormatter.format(new Date());
    }

    private synchronized String getBase64Digest(byte[] nonce, byte[] created, byte[] password) {
        try {
            MessageDigest messageDigester = MessageDigest.getInstance("SHA-1");
            // SHA-1 ( nonce + created + password )
            messageDigester.reset();
            messageDigester.update(nonce);
            messageDigester.update(created);
            messageDigester.update(password);
            return base64Encode(messageDigester.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String base64Encode(byte[] bytes) {
        return Base64Coder.encodeLines(bytes);
    }
    
    public Map<String,Integer> fetchRequestParams(){
        Map<String,Integer> params=new HashMap<>();
        //get values from db
        params.put("branchid", 2573104);
        params.put("salesManId", 2572800);
        params.put("sessionId", 3735805);
        params.put("shiftId", 3724305);       
        return params;
    }
    
    public String getProductID(String itemcode){
        //fetch product id give the product code
        
        return "";
    }

    /*public static void main(String[] args) throws IOException {
        Map map = new HashMap();
        map.put("rs_type", new String[]{"standard"});
        map.put("sp", "");

        String response = OMTR_REST.callMethod("Company.GetReportSuites", JSONObject.fromObject(map).toString());
        JSONObject jsonObj = JSONObject.fromObject(response);
        JSONArray jsonArry = JSONArray.fromObject(jsonObj.get("report_suites"));

        for (int i = 0; i < jsonArry.size(); i++) {
            System.out.println("Report Suite ID: " + JSONObject.fromObject(jsonArry.get(i)).get("rsid"));
            System.out.println("Site Title: " + JSONObject.fromObject(jsonArry.get(i)).get("site_title"));
            System.out.println();
        }
    }*/
}
