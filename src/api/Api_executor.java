package api;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

//import net.sf.json.JSONArray;
//import net.sf.json.JSONObject;
public class Api_executor {

    // private String USERNAME = "[your username]";
    // private String PASSWORD = "[your shared secret]";
    private String ENDPOINT = ""; //base url for the ecommerce endpoint
    private Map<String, String> settings = new HashMap<>();
    private static Api_executor instance;
    private final String USER_AGENT = "Mozilla/5.0";
    
    private final String branch_query = "org-hierarchy-master/1/5?_active=true&_q=%s&_reduced=true&_search=true&entries=1&page=1&pageSize=50&refresh=true",
            product_query = "product-master?_active=true&_q=%s&_search=true&page=1&pageSize=50&refresh=true",
            invoice_query = "invoice/adhoc-invoice/",
            creationAttributes_url="product-attributes?_active=true&_q=%s&_reduced=true&_search=true&page=1&pageSize=15&refresh=true&type=3";

    public static Api_executor getInstance(Map<String, String> settings) {
        if (instance == null) {
            instance = new Api_executor(settings);
        }
        return instance;
    }

    private Api_executor(Map<String, String> settings) {
        this.ENDPOINT = settings.get("ecomm_base_url");
        this.settings = settings;
    }

    public String sendRequest(String str_url, String data,String userid) throws Exception {
        return sendPostRequest(str_url, data, "POST",userid);
    }

    public String createInvoice(String jsonStr ,String userid) throws Exception {

        return sendRequest(createUrl(invoice_query), jsonStr ,userid);
    }

    public String sendPostRequest(String str_url, String data, String requestType ,String userid) throws Exception {
        URL url = new URL(str_url);
        //URLConnection connection = url.openConnection();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(requestType);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        
        addHeaderParamsb(connection,userid);

        connection.setDoOutput(true);
        //OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(data);
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

    // HTTP GET request
    private String sendGetRequet(String url) throws Exception {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");

        //add request header
        con.setRequestProperty("User-Agent", USER_AGENT);
        addHeaderParams(con);

        //int responseCode = con.getResponseCode();
        //System.out.println("\nSending 'GET' request to URL : " + url);
        //System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        return response.toString();

    }

    //add header request paramenters
    private void addHeaderParams(HttpURLConnection conn) {
        conn.setRequestProperty("FC_ActiveLanguage", settings.get("ecomm_locale_id"));//localeId
        conn.setRequestProperty("FC_Module", "POS");
        conn.setRequestProperty("FC_Authorization", settings.get("ecomm_user_id"));//userid
        conn.setRequestProperty("FC_Tenant_Entity", settings.get("ecomm_tenant_id"));//tenantId
    }
    
    //add header request paramenters
    private void addHeaderParamsb(HttpURLConnection conn,String useid) {
        conn.setRequestProperty("FC_ActiveLanguage", settings.get("ecomm_locale_id"));//localeId
        conn.setRequestProperty("FC_Module", "POS");
        conn.setRequestProperty("FC_Authorization", useid);//userid
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

    public Map<String, String> fetchRequestParams(String branchName) {
        Map<String, String> params = new HashMap<>();
        //get values from db
        params.put("branchid", getObjectID(branchName, "branch"));
        params.put("salesManId", "");
        params.put("sessionId", settings.get("ecomm_session_id"));
        params.put("shiftId", settings.get("ecomm_shift_id"));
        return params;
    }
    
    public JSONObject getCreationAttributes(String param){
               
        JSONArray jsonData = getJsonData(String.format(creationAttributes_url, encodeParam(param)));
        if(jsonData.length()>0){
            try {
                return jsonData.getJSONObject(0);
            } catch (JSONException ex) {
                iam_services.Iam_services.getInstance().Error_logger(ex,"getCreationAttributes");
            }
        }
            return new JSONObject();
        
    }

    public String getObjectID(String param, String type) {
        //fetch product id give the product code
        String qry_url = "";
        switch (type) {
            case "product":
                qry_url = product_query;
                break;
            case "branch":
                qry_url = branch_query;
                break;
            default:
                //unknown
                iam_services.Iam_services.getInstance().Error_logger(null, "Unsupported type:" + type);
        }
        String id = "";
        JSONArray jsonData = getJsonData(String.format(qry_url, encodeParam(param)));
        if (jsonData.length() > 0) {
            try {
                id = jsonData.getJSONObject(0).getString("id");
            } catch (JSONException ex) {
                iam_services.Iam_services.getInstance().Error_logger(ex, "getJsonData");
            }
        }
        iam_services.Iam_services.getInstance().Error_logger(null, "Get id :" + qry_url + " response->id:" + id);
        return id;
    }

    public JSONArray getJsonData(String queryurl) {
        try {
            String url = createUrl(queryurl);
            String res = sendGetRequet(url);
            iam_services.Iam_services.getInstance().Error_logger(null, res,true);
            return new JSONObject(res).getJSONArray("results");
        } catch (Exception ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "getJsonData");
            return new JSONArray();
        }
    }

    private String createUrl(String qry) {
        String url = this.ENDPOINT;
        if (url.charAt(url.length() - 1) != '/') {
            url += "/";
        }

        if (qry.charAt(0) == '/') {
            qry = qry.substring(1);
        }
        url += qry;
        return url;
    }

    private String encodeParam(String param) {
        try {
            return URLEncoder.encode(param, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "encodeParam");
            return param;
        }
    }
    /*public static void main(String[] args) throws IOException {
        Map map = new HashMap();
        map.put("rs_type", new String[]{"standard"});
        map.put("sp", "");

        String response = OMTR_REST.sendPostRequest("Company.GetReportSuites", JSONObject.fromObject(map).toString());
        JSONObject jsonObj = JSONObject.fromObject(response);
        JSONArray jsonArry = JSONArray.fromObject(jsonObj.get("report_suites"));

        for (int i = 0; i < jsonArry.size(); i++) {
            System.out.println("Report Suite ID: " + JSONObject.fromObject(jsonArry.get(i)).get("rsid"));
            System.out.println("Site Title: " + JSONObject.fromObject(jsonArry.get(i)).get("site_title"));
            System.out.println();
        }
    }*/
}
