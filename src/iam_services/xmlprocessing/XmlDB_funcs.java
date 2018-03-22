/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iam_services.xmlprocessing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author user
 */
public class XmlDB_funcs {

    private Connection conn;
    private static XmlDB_funcs instance;

    public XmlDB_funcs() throws Exception {
        if (conn == null) {
            conn = iam_services.Iam_services.getInstance().Connect();
            if (conn == null) {
                throw new Exception("Could not establish connection");
            }
        }
    }

    public static XmlDB_funcs getInstance() throws Exception {
        if (instance == null) {
            instance = new XmlDB_funcs();
        }
        return instance;
    }

    private List<Map<String, String>> getDBResMap(PreparedStatement pstm) {
        try {
            //Statement stmt = conn.createStatement();
            ResultSet rs = pstm.executeQuery();
            ResultSetMetaData rsmd = rs.getMetaData();
            List<String> columns = new ArrayList<>(rsmd.getColumnCount());
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                columns.add(rsmd.getColumnName(i));
            }
            
            List<Map<String, String>> data = new ArrayList<>();
            while (rs.next()) {
                Map<String, String> row = new HashMap<>(columns.size());
                for (String col : columns) {
                    row.put(col, rs.getString(col));
                }
                data.add(row);
            }
            rs.close();
            pstm.close();
            return data;
        } catch (SQLException ex) {
            Logger.getLogger(XmlDB_funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new ArrayList<>();//empty
    }
    
    private PreparedStatement buildQuery(String table, Map<String,String> where) throws SQLException{
        PreparedStatement pstm;
        String query="";
        if(where==null){
            query="SELECT * FROM "+table+" WHERE CONVERT(DATE, [DATE_STAMP]) = CONVERT(DATE, CURRENT_TIMESTAMP);";
            pstm=conn.prepareCall(query);
        }else{
            StringBuilder sql=new StringBuilder("SELECT * FROM "+table+" WHERE ");
            where.entrySet().forEach(set->{
                sql.append(" "+set.getKey()+"=? &");
            });
            query=sql.toString().replaceFirst(".$","");
            pstm=conn.prepareCall(query);//remove the last &
            
            int i=1;
            for (Map.Entry<String, String> entry : where.entrySet()) {
                pstm.setString(i++, entry.getValue());
            }
        }
        
        iam_services.Iam_services.getInstance().Error_logger(null, "Query->"+query, true);
        return pstm;
    }
    
    public  List<Map<String, String>> QueryDB(String table, Map<String,String> where){
        try {
            PreparedStatement pstm = buildQuery(table, where);
            return getDBResMap(pstm);
        } catch (SQLException ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "QueryDB");
        }
        return new ArrayList<>();
    }
    
    /*public static void main(String[] args) {
        try {
            System.out.println(getInstance().getDBResMap("CNB_IAM_E1WXX01"));
        } catch (Exception ex) {
            Logger.getLogger(XmlDB_funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
    }*/
}
