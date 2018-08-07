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

    private PreparedStatement buildQuery(String table, Map<String, String> where) throws SQLException {
        PreparedStatement pstm;
        String query = "";
        if (where == null) {
            //query="SELECT * FROM "+table+" WHERE CONVERT(DATE, [DATE_STAMP]) = CONVERT(DATE, CURRENT_TIMESTAMP) ORDER BY id ASC;";
            query = "UPDATE " + table + " SET READ_FLG = 1 OUTPUT inserted.* WHERE  (READ_FLG = 0 OR READ_FLG IS NULL) AND CONVERT(DATE, [DATE_STAMP]) = CONVERT(DATE, CURRENT_TIMESTAMP) ;";
            pstm = conn.prepareStatement(query);
        } else {
            StringBuilder sql = new StringBuilder("SELECT * FROM " + table + " WHERE ");
            List<String> wherecols = new ArrayList<>();
            where.entrySet().forEach(set -> {
                if(!set.getKey().equals("no")){
                    sql.append(" " + set.getKey() + "=? AND");
                    wherecols.add(set.getKey());
                }else{//Its ready, just a string
                    sql.append(" " + set.getValue() + " AND");
                }
            });
            query = sql.toString();
            query = query.substring(0, query.length() - 3);
            pstm = conn.prepareStatement(query);//remove the last &

            int i = 1;
            for (String col_h : wherecols) {
                pstm.setString(i++, where.get(col_h));
            }
        }

        iam_services.Iam_services.getInstance().Error_logger(null, "Query->" + query, true);
        return pstm;
    }

    public List<Map<String, String>> QueryDB(String table, Map<String, String> where) {
        try {
            PreparedStatement pstm = buildQuery(table, where);
            return getDBResMap(pstm);
        } catch (SQLException ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "QueryDB");
        }
        return new ArrayList<>();
    }

    public static Map<String, String> brachidMap;

    public String getSAPBranchMapping(String branch_id) throws Exception {
        branch_id="9521600";
        if (brachidMap == null) {//initialize the map
            brachidMap = new HashMap<>();
        }
        //check if we already have that id, to avoid network delay connecting everytime
        if (brachidMap.get(branch_id) != null) {
            return brachidMap.get(branch_id);
        }

        //not available on our map yet, so, we fetch it
        return fetch_branch_id_mapping(branch_id);
    }

    public String fetch_branch_id_mapping(String branch_id) {
      //hard code branch code
        branch_id= "9521600";
        String sap_branch_mapping = "";
        try {
            PreparedStatement pstm = conn.prepareStatement("SELECT TOP 1 SAP_ENT_ID FROM CNB_IAM_ENTITY_MAP WHERE ENT_ID=?");
            pstm.setString(1, branch_id);
            ResultSet rs = pstm.executeQuery();
            while (rs.next()) {
                sap_branch_mapping = rs.getString("SAP_ENT_ID");
            }
        } catch (Exception ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "fetch_branch_id_mapping");
        }

       // iam_services.Iam_services.getInstance().Error_logger(null, "Branch mapping res=" + sap_branch_mapping  , true);
          iam_services.Iam_services.getInstance().Error_logger(null, "Branch mapping res=" + sap_branch_mapping + " nexx_branch _id " + sap_branch_mapping + "" , true);
       if(sap_branch_mapping==""){
            return "-";
        }
        return sap_branch_mapping;
    }

    public List<String> getDistict_byDate(String column, String query) {
        List<String> res = new ArrayList<>();
        try {
            PreparedStatement pstm = conn.prepareStatement(query);
            ResultSet rs = pstm.executeQuery();
            while (rs.next()) {
                res.add(rs.getString(column));
            }
        } catch (Exception ex) {
            iam_services.Iam_services.getInstance().Error_logger(ex, "getDistict");
        }

        return res;
    }

    /*public static void main(String[] args) {
        try {
            System.out.println(getInstance().getDBResMap("CNB_IAM_E1WXX01"));
        } catch (Exception ex) {
            Logger.getLogger(XmlDB_funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
    }*/
}
