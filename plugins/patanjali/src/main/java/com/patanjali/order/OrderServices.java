package com.patanjali.order;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityTypeUtil;
import org.apache.ofbiz.party.party.PartyWorker;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class OrderServices {
	
	public static String module = OrderServices.class.getName();
	public static final String resource = "AccountingUiLabels";
	public static String JOURNAL_XLSX_FILE_PATH = System.getProperty("ofbiz.home")+"/plugins/patanjali/dtd/xlsfiles/Transaction.xlsx";
	public static final String STOCK_XLSX_FILE_PATH = System.getProperty("ofbiz.home")+"/plugins/patanjali/dtd/xlsfiles/Stock.xlsx";
    public static Map<String, Object> readFileAndConvertToMap(){
    	System.out.println("=======SAMPLE_XLSX_FILE_PATH===="+JOURNAL_XLSX_FILE_PATH);
        Workbook workbook = null;
		try {
			workbook = WorkbookFactory.create(new File(JOURNAL_XLSX_FILE_PATH));
		} catch (EncryptedDocumentException | IOException e) {
			e.printStackTrace();
		}
        Sheet sheet = workbook.getSheetAt(6);
        DataFormatter dataFormatter = new DataFormatter();
        Map<String, Object> orderMap = new HashMap<String, Object>();
        List<Map<String, Object>> orderList = new ArrayList<Map<String, Object>>();
        Iterator<Row> rowIterator = sheet.iterator();
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if(row.getRowNum() == 0)continue;
            if(sheet.getRow(row.getRowNum() -1).getCell(1).getStringCellValue() != sheet.getRow(row.getRowNum()).getCell(1).getStringCellValue()) {
            	orderList = new ArrayList<Map<String, Object>>();
            }
            Map<String, Object> orderItems = new HashMap<String, Object>();
            orderItems.put("productId", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(5)));
            orderItems.put("productName", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(4)));
            //orderItems.put("materialCenter", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(6)));
            orderItems.put("quantity", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(9)));
            orderItems.put("unit", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(10)));
            orderItems.put("price", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(10)));
            orderItems.put("amount", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(11)));
            orderItems.put("vat", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(13)));
            orderList.add(orderItems);
        	String poorderId = sheet.getRow(row.getRowNum()).getCell(1).getStringCellValue();
        	Map<String, Object> orderDetailMap = new HashMap<String, Object>();
        	orderDetailMap.put("orderDate", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(0)));
        	orderDetailMap.put("orderId", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(1)));
        	orderDetailMap.put("supplierId", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(3)));
        	orderDetailMap.put("supplierName", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(2)));
        	orderDetailMap.put("orderItems", orderList);
        	orderMap.put(poorderId, orderDetailMap);
        }
        try {
			workbook.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        Debug.logInfo("===========Number Of orders in this file=========="+orderMap.size(), module);
        return orderMap;
    }
    
    @SuppressWarnings("unchecked")
	public static Map<String, Object> importPruchaseOrderFromXLS(DispatchContext ctx, Map<String, ? extends Object> context){
    	Delegator delegator = ctx.getDelegator();
    	LocalDispatcher dispatcher = ctx.getDispatcher();
    	GenericValue userLogin = (GenericValue) context.get("userLogin");
		Map<String, Object> orderMap = readFileAndConvertToMap();
        for (Map.Entry<String,Object> entry : orderMap.entrySet()) {
        	String orderId = entry.getKey();
        	GenericValue orderHeader;
			try {
				orderHeader = EntityQuery.use(delegator).from("OrderHeader").where("orderId", orderId.trim()).queryOne();
			} catch (GenericEntityException e1) {
				e1.printStackTrace();
				return ServiceUtil.returnError(e1.getMessage());
			}
        	if(orderHeader != null) continue;
        	Map<String, Object> orderDetail = (Map<String, Object>) entry.getValue();
        	Map <String, Object> inMap = new HashMap<>();
        	inMap.put("partyId", "patanjalinepal");
        	inMap.put("orderId", orderId);
        	inMap.put("orderTypeId", "PURCHASE_ORDER");
        	inMap.put("currencyUom", "NPR");
        	inMap.put("productStoreId","PATANJALINEPAL_STORE");

        	List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.get("orderItems");
        	
            List <GenericValue> orderAdjustments = new LinkedList<>();
            
        	List <GenericValue> orderItems = new LinkedList<>();
        	int orderItemSeqId = 1;
        	for(Map<String, Object> item : items) {
        		GenericValue orderItem = delegator.makeValue("OrderItem", UtilMisc.toMap("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId), "orderItemTypeId", "PRODUCT_ORDER_ITEM", "productId", item.get("productId"), "quantity", new BigDecimal(((String)item.get("quantity")).replace(",", "")), "isPromo", "N"));
                orderItem.set("unitPrice", new BigDecimal(((String)item.get("price")).replace(",", "")));
                orderItem.set("unitListPrice", BigDecimal.ZERO);
                orderItem.set("isModifiedPrice", "N");
                orderItem.set("statusId", "ITEM_CREATED");
                orderItem.set("itemDescription", item.get("productName"));
                orderItems.add(orderItem);
                if(UtilValidate.isNotEmpty(item.get("vat"))) {
                	GenericValue orderAdjustment = delegator.makeValue("OrderAdjustment");
                    orderAdjustment.set("orderAdjustmentTypeId", "SALES_TAX");
                    orderAdjustment.set("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId));
                    orderAdjustment.set("shipGroupSeqId", "00001");
                    orderAdjustment.set("amount", new BigDecimal(((String)item.get("vat")).replace(",", "")));
                    orderAdjustment.set("comments", "13% VAT");
                    orderAdjustments.add(orderAdjustment);
                }
                orderItemSeqId ++;
        	}
        	
        	inMap.put("orderItems", orderItems);
        	
        	GenericValue partyContactMech = PartyWorker.findPartyLatestContactMech((String)orderDetail.get("supplierId"), "POSTAL_ADDRESS", delegator);
        	if(partyContactMech != null) {
        		GenericValue orderContactMech = delegator.makeValue("OrderContactMech", UtilMisc.toMap("contactMechPurposeTypeId", "SHIPPING_LOCATION", "contactMechId", partyContactMech.get("contactMechId")));
                List <GenericValue> orderContactMechs = new LinkedList<>();
                orderContactMechs.add(orderContactMech);
                inMap.put("orderContactMechs", orderContactMechs);
                
//                GenericValue orderItemContactMech = delegator.makeValue("OrderItemContactMech", UtilMisc.toMap("contactMechPurposeTypeId", "SHIPPING_LOCATION", "contactMechId", partyContactMech.get("contactMechId"), "orderItemSeqId", "00001"));
//                List <GenericValue> orderItemContactMechs = new LinkedList<>();
//                orderItemContactMechs.add(orderItemContactMech);
//                inMap.put("orderItemContactMechs", orderItemContactMechs);
        	}

            GenericValue orderItemShipGroup = delegator.makeValue("OrderItemShipGroup", UtilMisc.toMap("carrierPartyId", "patanjalinepal", "contactMechId", "9000", "isGift", "N", "maySplit", "N", "shipGroupSeqId", "00001", "shipmentMethodTypeId", "LOCAL_DELIVERY"));
            orderItemShipGroup.set("carrierRoleTypeId","CARRIER");
            List <GenericValue> orderItemShipGroupInfo = new LinkedList<>();
            orderItemShipGroupInfo.add(orderItemShipGroup);
            inMap.put("orderItemShipGroupInfo", orderItemShipGroupInfo);

            List <GenericValue> orderTerms = new LinkedList<>();
            inMap.put("orderTerms", orderTerms);

            inMap.put("orderAdjustments", orderAdjustments);

            inMap.put("billToCustomerPartyId", "patanjalinepal");
            inMap.put("billFromVendorPartyId", orderDetail.get("supplierId"));
            inMap.put("shipFromVendorPartyId", "patanjalinepal");
            inMap.put("supplierAgentPartyId", orderDetail.get("supplierId"));
            inMap.put("userLogin", userLogin);
            Map<String, Object> resp;
    		try {
    			resp = dispatcher.runSync("storeOrder", inMap);
    		} catch (GenericServiceException e) {
    			e.printStackTrace();
    			return ServiceUtil.returnError(e.getMessage());
    		}
            if (ServiceUtil.isError(resp)) {
                Debug.logError(ServiceUtil.getErrorMessage(resp), module);
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(resp));
            }
            
//            try {
//                OrderChangeHelper.orderStatusChanges(dispatcher, userLogin, orderId, "ORDER_APPROVED", null, "ITEM_APPROVED", null);
//            } catch (GenericServiceException e) {
//                Debug.logError(e, "Service invocation error, status changes were not updated for order #" + orderId, module);
//                return ServiceUtil.returnError(e.getMessage());
//            }
        }
    	return ServiceUtil.returnSuccess();
    }
    
    @SuppressWarnings("unchecked")
	public static Map<String, Object> importSalesOrderFromXLS(DispatchContext ctx, Map<String, ? extends Object> context){
    	Delegator delegator = ctx.getDelegator();
    	LocalDispatcher dispatcher = ctx.getDispatcher();
    	GenericValue userLogin = (GenericValue) context.get("userLogin");
//    	int startRow = (Integer) context.get("startRow");
//    	int endRow = (Integer) context.get("endRow");
		Map<String, Object> orderMap = readSalesFileAndConvertToMap();
        for (Map.Entry<String,Object> entry : orderMap.entrySet()) {
        	String orderId = entry.getKey();
        	GenericValue orderHeader;
			try {
				orderHeader = EntityQuery.use(delegator).from("OrderHeader").where("orderId", orderId.trim()).queryOne();
			} catch (GenericEntityException e1) {
				e1.printStackTrace();
				return ServiceUtil.returnError(e1.getMessage());
			}
        	if(orderHeader != null) continue;
        	Map<String, Object> orderDetail = (Map<String, Object>) entry.getValue();
        	Map <String, Object> inMap = new HashMap<>();
        	inMap.put("partyId", "patanjalinepal");
        	inMap.put("orderId", orderId);
        	inMap.put("orderTypeId", "SALES_ORDER");
        	inMap.put("currencyUom", "NPR");
        	inMap.put("productStoreId","PATANJALINEPAL_STORE");

        	List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.get("orderItems");
        	
            List <GenericValue> orderAdjustments = new LinkedList<>();
            
        	List <GenericValue> orderItems = new LinkedList<>();
        	int orderItemSeqId = 1;
        	for(Map<String, Object> item : items) {
        		GenericValue orderItem = delegator.makeValue("OrderItem", UtilMisc.toMap("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId), "orderItemTypeId", "PRODUCT_ORDER_ITEM", "productId", item.get("productId"), "quantity", new BigDecimal(((String)item.get("quantity")).replace(",", "")), "isPromo", "N"));
                orderItem.set("unitPrice", new BigDecimal(((String)item.get("price")).replace(",", "")));
                orderItem.set("unitListPrice", BigDecimal.ZERO);
                orderItem.set("isModifiedPrice", "N");
                orderItem.set("statusId", "ITEM_CREATED");
                orderItem.set("itemDescription", item.get("productName"));
                orderItems.add(orderItem);
                if(UtilValidate.isNotEmpty(item.get("vat"))) {
                	GenericValue orderAdjustment = delegator.makeValue("OrderAdjustment");
                    orderAdjustment.set("orderAdjustmentTypeId", "SALES_TAX");
                    orderAdjustment.set("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId));
                    orderAdjustment.set("shipGroupSeqId", "00001");
                    orderAdjustment.set("amount", new BigDecimal(((String)item.get("vat")).replace(",", "")));
                    orderAdjustment.set("comments", "13% VAT");
                    orderAdjustments.add(orderAdjustment);
                }
                
                if(UtilValidate.isNotEmpty(item.get("exciseDutyOnFreeQuantity"))) {
                	GenericValue orderAdjustment = delegator.makeValue("OrderAdjustment");
                    orderAdjustment.set("orderAdjustmentTypeId", "EXCISE_ON_FREE_Q");
                    orderAdjustment.set("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId));
                    orderAdjustment.set("shipGroupSeqId", "00001");
                    orderAdjustment.set("amount", new BigDecimal(((String)item.get("exciseDutyOnFreeQuantity")).replace(",", "")));
                    orderAdjustment.set("comments", "EXCISE DUTY ON FREE(Q)");
                    orderAdjustments.add(orderAdjustment);
                }
                
                if(UtilValidate.isNotEmpty(item.get("exciseDutyOnFreeVat"))) {
                	GenericValue orderAdjustment = delegator.makeValue("OrderAdjustment");
                    orderAdjustment.set("orderAdjustmentTypeId", "EXCISE_ON_FREE_V");
                    orderAdjustment.set("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId));
                    orderAdjustment.set("shipGroupSeqId", "00001");
                    orderAdjustment.set("amount", new BigDecimal(((String)item.get("exciseDutyOnFreeVat")).replace(",", "")));
                    orderAdjustment.set("comments", "EXCISE DUTY ON FREE(V)");
                    orderAdjustments.add(orderAdjustment);
                }
                
                if(UtilValidate.isNotEmpty(item.get("exciseDutyQuantity"))) {
                	GenericValue orderAdjustment = delegator.makeValue("OrderAdjustment");
                    orderAdjustment.set("orderAdjustmentTypeId", "EXCISE_DUTY_Q");
                    orderAdjustment.set("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId));
                    orderAdjustment.set("shipGroupSeqId", "00001");
                    orderAdjustment.set("amount", new BigDecimal(((String)item.get("exciseDutyQuantity")).replace(",", "")));
                    orderAdjustment.set("comments", "Excise Duty (Q)");
                    orderAdjustments.add(orderAdjustment);
                }
                
                if(UtilValidate.isNotEmpty(item.get("exciseDutyQuantityOnFree"))) {
                	GenericValue orderAdjustment = delegator.makeValue("OrderAdjustment");
                    orderAdjustment.set("orderAdjustmentTypeId", "EXCISE_DUTY_Q_FREE");
                    orderAdjustment.set("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId));
                    orderAdjustment.set("shipGroupSeqId", "00001");
                    orderAdjustment.set("amount", new BigDecimal(((String)item.get("exciseDutyQuantityOnFree")).replace(",", "")));
                    orderAdjustment.set("comments", "EXCISE DUTY(Q) ON FREE");
                    orderAdjustments.add(orderAdjustment);
                }
                
                if(UtilValidate.isNotEmpty(item.get("exciseDutyVat"))) {
                	GenericValue orderAdjustment = delegator.makeValue("OrderAdjustment");
                    orderAdjustment.set("orderAdjustmentTypeId", "EXCISE_DUTY_V");
                    orderAdjustment.set("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId));
                    orderAdjustment.set("shipGroupSeqId", "00001");
                    orderAdjustment.set("amount", new BigDecimal(((String)item.get("exciseDutyVat")).replace(",", "")));
                    orderAdjustment.set("comments", "EXCISE DUTY (V)");
                    orderAdjustments.add(orderAdjustment);
                }
                
                if(UtilValidate.isNotEmpty(item.get("exciseDutyVatOnFree"))) {
                	GenericValue orderAdjustment = delegator.makeValue("OrderAdjustment");
                    orderAdjustment.set("orderAdjustmentTypeId", "EXCISE_DUTY_V_FREE");
                    orderAdjustment.set("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId));
                    orderAdjustment.set("shipGroupSeqId", "00001");
                    orderAdjustment.set("amount", new BigDecimal(((String)item.get("exciseDutyVatOnFree")).replace(",", "")));
                    orderAdjustment.set("comments", "EXCISE DUTY (V) ON FREE");
                    orderAdjustments.add(orderAdjustment);
                }
                
                if(UtilValidate.isNotEmpty(item.get("vatOnFree"))) {
                	GenericValue orderAdjustment = delegator.makeValue("OrderAdjustment");
                    orderAdjustment.set("orderAdjustmentTypeId", "VAT_ON_FREE");
                    orderAdjustment.set("orderItemSeqId", "0000"+String.valueOf(orderItemSeqId));
                    orderAdjustment.set("shipGroupSeqId", "00001");
                    orderAdjustment.set("amount", new BigDecimal(((String)item.get("vatOnFree")).replace(",", "")));
                    orderAdjustment.set("comments", "VAT ON FREE");
                    orderAdjustments.add(orderAdjustment);
                }
                
                orderItemSeqId ++;
        	}
        	
        	
        	
        	inMap.put("orderItems", orderItems);
        	
        	GenericValue partyContactMech = PartyWorker.findPartyLatestContactMech((String)orderDetail.get("supplierId"), "POSTAL_ADDRESS", delegator);
        	if(partyContactMech != null) {
        		GenericValue orderContactMech = delegator.makeValue("OrderContactMech", UtilMisc.toMap("contactMechPurposeTypeId", "SHIPPING_LOCATION", "contactMechId", partyContactMech.get("contactMechId")));
                List <GenericValue> orderContactMechs = new LinkedList<>();
                orderContactMechs.add(orderContactMech);
                inMap.put("orderContactMechs", orderContactMechs);
                
//                GenericValue orderItemContactMech = delegator.makeValue("OrderItemContactMech", UtilMisc.toMap("contactMechPurposeTypeId", "SHIPPING_LOCATION", "contactMechId", partyContactMech.get("contactMechId"), "orderItemSeqId", "00001"));
//                List <GenericValue> orderItemContactMechs = new LinkedList<>();
//                orderItemContactMechs.add(orderItemContactMech);
//                inMap.put("orderItemContactMechs", orderItemContactMechs);
        	}

            GenericValue orderItemShipGroup = delegator.makeValue("OrderItemShipGroup", UtilMisc.toMap("carrierPartyId", "patanjalinepal", "contactMechId", "9000", "isGift", "N", "maySplit", "N", "shipGroupSeqId", "00001", "shipmentMethodTypeId", "LOCAL_DELIVERY"));
            orderItemShipGroup.set("carrierRoleTypeId","CARRIER");
            List <GenericValue> orderItemShipGroupInfo = new LinkedList<>();
            orderItemShipGroupInfo.add(orderItemShipGroup);
            inMap.put("orderItemShipGroupInfo", orderItemShipGroupInfo);

            List <GenericValue> orderTerms = new LinkedList<>();
            inMap.put("orderTerms", orderTerms);

            inMap.put("orderAdjustments", orderAdjustments);

            inMap.put("billToCustomerPartyId", orderDetail.get("customerId"));
            inMap.put("shipToCustomerPartyId", orderDetail.get("customerId"));
            inMap.put("billFromVendorPartyId", "patanjalinepal");
            inMap.put("placingCustomerPartyId", orderDetail.get("customerId"));
            inMap.put("userLogin", userLogin);
            Map<String, Object> resp;
    		try {
    			resp = dispatcher.runSync("storeOrder", inMap);
    		} catch (GenericServiceException e) {
    			e.printStackTrace();
    			return ServiceUtil.returnError(e.getMessage());
    		}
            if (ServiceUtil.isError(resp)) {
                Debug.logError(ServiceUtil.getErrorMessage(resp), module);
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(resp));
            }
            
//            try {
//                OrderChangeHelper.orderStatusChanges(dispatcher, userLogin, orderId, "ORDER_APPROVED", null, "ITEM_APPROVED", null);
//            } catch (GenericServiceException e) {
//                Debug.logError(e, "Service invocation error, status changes were not updated for order #" + orderId, module);
//                return ServiceUtil.returnError(e.getMessage());
//            }
        }
    	
    	return ServiceUtil.returnSuccess();
    }
    
    public static Map<String, Object> readSalesFileAndConvertToMap(){
    	System.out.println("=======SAMPLE_XLSX_FILE_PATH===="+JOURNAL_XLSX_FILE_PATH);
        Workbook workbook = null;
		try {
			workbook = WorkbookFactory.create(new File(JOURNAL_XLSX_FILE_PATH));
		} catch (EncryptedDocumentException | IOException e) {
			e.printStackTrace();
		}
        Sheet sheet = workbook.getSheetAt(2);
        DataFormatter dataFormatter = new DataFormatter();
        Map<String, Object> orderMap = new HashMap<String, Object>();
        List<Map<String, Object>> orderList = new ArrayList<Map<String, Object>>();
        Iterator<Row> rowIterator = sheet.iterator();
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            //if(row.getRowNum() < startRow)continue;
            if(row.getRowNum() == 0)continue;
            if(sheet.getRow(row.getRowNum() -1).getCell(1).getStringCellValue() != sheet.getRow(row.getRowNum()).getCell(1).getStringCellValue()) {
            	orderList = new ArrayList<Map<String, Object>>();
            }
            
            //if(row.getRowNum() == endRow)return ServiceUtil.returnSuccess();
            
            Map<String, Object> orderItems = new HashMap<String, Object>();
            orderItems.put("productId", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(5)));
            orderItems.put("productName", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(4)));
            //orderItems.put("materialCenter", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(6)));
            orderItems.put("quantity", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(9)));
            orderItems.put("freeQuantityIncluded", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(10)));
            orderItems.put("unit", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(11)));
            orderItems.put("price", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(11)));
            orderItems.put("amount", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(11)));
            orderItems.put("discount", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(13)));
            orderItems.put("exciseDutyOnFreeQuantity", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(14)));
            orderItems.put("exciseDutyOnFreeVat", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(15)));
            orderItems.put("exciseDutyQuantity", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(16)));
            orderItems.put("exciseDutyQuantityOnFree", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(17)));
            orderItems.put("exciseDutyVat", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(18)));
            orderItems.put("exciseDutyVatOnFree", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(19)));
            orderItems.put("vat", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(20)));
            orderItems.put("vatOnFree", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(21)));
            orderList.add(orderItems);
        	String poorderId = sheet.getRow(row.getRowNum()).getCell(1).getStringCellValue();
        	Map<String, Object> orderDetailMap = new HashMap<String, Object>();
        	orderDetailMap.put("orderDate", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(0)));
        	orderDetailMap.put("orderId", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(1)));
        	orderDetailMap.put("customerId", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(3)));
        	orderDetailMap.put("customerName", dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(2)));
        	orderDetailMap.put("orderItems", orderList);
        	orderMap.put(poorderId, orderDetailMap);
        }
        try {
			workbook.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        Debug.logInfo("===========Number Of orders in this file=========="+orderMap.size(), module);
        return orderMap;
    }
 
    
    static String ch1 = "";
    private static String pw(int n,String ch, int start)
	  {
    	//23.45
    	//23 ruppe 1
    	String ch11="";
    	//23 ruppe and
	    String  one[]={" "," One"," Two"," Three"," Four"," Five"," Six"," Seven"," Eight"," Nine"," Ten"," Eleven"," Twelve"," Thirteen"," Fourteen","Fifteen"," Sixteen"," Seventeen"," Eighteen"," Nineteen"};
	 
	    String ten[]={" "," "," Twenty"," Thirty"," Forty"," Fifty"," Sixty"," Seventy"," Eighty"," Ninety"};
	    if(n > 19) {
	    	System.out.print(ten[n/10]+" "+one[n%10]);
	    	
	    	if(start ==1){
	    		ch1 = ch1+ten[n/10]+" "+one[n%10];
	    	}else{
	    		ch1 = ch1+ten[n/10]+" "+one[n%10];
	    	}
	    	System.out.print("--------19---------ch11--------"+ch11);
    	} else { 
    		System.out.print(one[n]);
    		if(start ==1){
	    		ch1 = ch1+one[n];
	    	}else{
	    		ch1 = ch1+one[n];
	    	}
		}
	    if(n > 0){
	    	System.out.print(ch);
	    	if(start ==1){
	    		ch1 = ch1+ch;
	    	}else{
	    		ch11 = ch1+ch;
	    		ch1 = "";
	    	}
	    }
	    System.out.print("-----------------ch11--------"+ch11);
	    return ch11;
	  }

	public static String convertNumberToWords(BigDecimal number) {
		String word = "";
		int n = 0;
		int s = 0;
		String[] values = String.valueOf(number).split("\\.");
		if (values.length > 1) {
			n = Integer.valueOf(values[0]);
			if ( !values[1].startsWith("0") && !values[1].startsWith("00") ) {
				s = Integer.valueOf(values[1]);
			}
		} else {
			n = Integer.valueOf(values[0]);
		}
		if (n <= 0) {
			System.out.println("Enter numbers greater than 0");
		} else {
			word = pw((n / 1000000000), " Hundred",1);
			word = pw((n / 10000000) % 100, " Crore",1);
			word =  pw(((n / 100000) % 100), " Lakh", 1);
			word =  pw(((n / 1000) % 100), " Thousand",1);
			word =  pw(((n / 100) % 10), " Hundred",1);
			if (values.length > 1 && s > 0) {
				word =  pw((n % 100), " Rupees and",1);
			} else {
				word =  pw((n % 100), " Rupees",0);
			}
			if (values.length > 1 && s > 0) {
				word =  pw((s % 100), " Paise",0);
			}
		}
		return word;
	}
	
    public static Map<String, Object> importInvoice(DispatchContext dctx, Map<String, Object> context) {
        Locale locale = (Locale) context.get("locale");
       Delegator delegator = dctx.getDelegator();
       LocalDispatcher dispatcher = dctx.getDispatcher();
       GenericValue userLogin = (GenericValue) context.get("userLogin");
       ByteBuffer fileBytes = (ByteBuffer) context.get("uploadedFile");
       
       if (fileBytes == null) {
           return ServiceUtil.returnError(UtilProperties.getMessage(resource, "AccountingUploadedFileDataNotFound", locale));
       }
       
       Debug.logInfo(":::Import Ptanjali invoice:::", module);
       String organizationPartyId = (String) context.get("organizationPartyId");
       String encoding = System.getProperty("file.encoding");
       String csvString = Charset.forName(encoding).decode(fileBytes).toString();
       final BufferedReader csvReader = new BufferedReader(new StringReader(csvString));
       CSVFormat fmt = CSVFormat.DEFAULT.withHeader();
       List<String> errMsgs = new LinkedList<>();
       List<String> newErrMsgs;
       String lastInvoiceId = null;
       String currentInvoiceId = null;
       String newInvoiceId = null;
       int invoicesCreated = 0;
       String partyIdFrom = "patanjali";
       List<String> createdInvoiceIds = new LinkedList<>();
       try {
           for (final CSVRecord rec : fmt.parse(csvReader)) {
               currentInvoiceId =  rec.get("invoiceId");
				/*
				 * if(!rec.get("invoiceTypeId").equals("SALES_INVOICE")) { partyIdFrom =
				 * rec.get("partyId"); }
				 */

               if (lastInvoiceId == null || !currentInvoiceId.equals(lastInvoiceId)) {
                   newInvoiceId = null;
                   Map<String, Object> invoice = UtilMisc.toMap(
                           "invoiceTypeId", rec.get("invoiceTypeId"),
                           "partyIdFrom", partyIdFrom,
                           "partyId", rec.get("partyId"),
                           "invoiceDate", rec.get("invoiceDate"),
                           "currencyUomId", "INR",
                           "description", rec.get("partyDetails"),
                           "referenceNumber","orginal Invoice Amount: " +  rec.get("amount") ,
                           "userLogin", userLogin
                           );

                  // invoice validation
                   newErrMsgs = new LinkedList<>();
                   try {
                       /*if (UtilValidate.isEmpty(invoice.get("partyIdFrom"))) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory Party Id From and Party Id From Trans missing for invoice: " + currentInvoiceId);
                       } else if (EntityQuery.use(delegator).from("Party").where("partyId", invoice.get("partyIdFrom")).queryOne() == null) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": partyIdFrom: " + invoice.get("partyIdFrom") + " not found for invoice: " + currentInvoiceId);
                       }*/
                       if (UtilValidate.isEmpty(invoice.get("partyId"))) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory Party Id and Party Id Trans missing for invoice: " + currentInvoiceId);
                       } else if (EntityQuery.use(delegator).from("Party").where("partyId", invoice.get("partyId")).queryOne() == null) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": partyId: " + invoice.get("partyId") + " not found for invoice: " + currentInvoiceId);
                       }
                       if (UtilValidate.isEmpty(invoice.get("invoiceTypeId"))) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory Invoice Type missing for invoice: " + currentInvoiceId);
                       } else if (EntityQuery.use(delegator).from("InvoiceType").where("invoiceTypeId", invoice.get("invoiceTypeId")).queryOne() == null) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": InvoiceItem type id: " + invoice.get("invoiceTypeId") + " not found for invoice: " + currentInvoiceId);
                       }

                       Boolean isPurchaseInvoice = EntityTypeUtil.hasParentType(delegator, "InvoiceType", "invoiceTypeId", (String) invoice.get("invoiceTypeId"), "parentTypeId", "PURCHASE_INVOICE");
                       Boolean isSalesInvoice = EntityTypeUtil.hasParentType(delegator, "InvoiceType", "invoiceTypeId", (String) invoice.get("invoiceTypeId"), "parentTypeId", "SALES_INVOICE");
                       if (isPurchaseInvoice && !invoice.get("partyId").equals(organizationPartyId)) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": A purchase type invoice should have the partyId 'To' being the organizationPartyId(=" + organizationPartyId + ")! however is " + invoice.get("partyId") +"! invoice: " + currentInvoiceId);
                       }
                       if (isSalesInvoice && !invoice.get("partyIdFrom").equals(organizationPartyId)) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": A sales type invoice should have the partyId 'from' being the organizationPartyId(=" + organizationPartyId + ")! however is " + invoice.get("partyIdFrom") +"! invoice: " + currentInvoiceId);
                       }


                   } catch (GenericEntityException e) {
                       Debug.logError("Valication checking problem against database. due to " + e.getMessage(), module);
                   }

                   if (newErrMsgs.size() > 0) {
                       errMsgs.addAll(newErrMsgs);
                   } else {
                       Map<String, Object> invoiceResult = null;
                       try {
                           invoiceResult = dispatcher.runSync("createInvoice", invoice);
                           if (ServiceUtil.isError(invoiceResult)) {
                              return ServiceUtil.returnError(ServiceUtil.getErrorMessage(invoiceResult));
                           }
                       } catch (GenericServiceException e) {
                           csvReader.close();
                           Debug.logError(e, module);
                           return ServiceUtil.returnError(e.getMessage());
                       }
                       newInvoiceId = (String) invoiceResult.get("invoiceId");
                       createdInvoiceIds.add(newInvoiceId);
                       invoicesCreated++;
                   }
                   lastInvoiceId = currentInvoiceId;
               }


               if (newInvoiceId != null) {
            	   //DecimalFormat df = new DecimalFormat("0000");
                   Map<String, Object> invoiceItem = UtilMisc.toMap(
                           "invoiceId", newInvoiceId,
                           "invoiceItemSeqId",rec.get("invoiceItemSeqId"),
                           "invoiceItemTypeId", "INV_PROD_ITEM",
                           "productId", rec.get("productId"),
                           "description", rec.get("itemDescription"),
                           "amount", rec.get("amount"),
                           "quantity", rec.get("quantity"),
                           "userLogin", userLogin
                           );
                   Debug.logInfo(":::::::##########:::::productId::"+ rec.get("productId"), module);
                   // invoice item validation
                   newErrMsgs = new LinkedList<>();
                   try {
                       if (UtilValidate.isEmpty(invoiceItem.get("invoiceItemSeqId"))) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory item sequence Id missing for invoice: " + currentInvoiceId);
                       }
                       if (UtilValidate.isEmpty(invoiceItem.get("invoiceItemTypeId"))) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory invoice item type missing for invoice: " + currentInvoiceId);
                       } else if (EntityQuery.use(delegator).from("InvoiceItemType").where("invoiceItemTypeId", invoiceItem.get("invoiceItemTypeId")).queryOne() == null) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": InvoiceItem Item type id: " + invoiceItem.get("invoiceItemTypeId") + " not found for invoice: " + currentInvoiceId + " Item seqId:" + invoiceItem.get("invoiceItemSeqId"));
                       }
                       if (UtilValidate.isEmpty(invoiceItem.get("productId")) && UtilValidate.isEmpty(invoiceItem.get("description"))) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": no Product Id given, no description given");
                       }
                       if (UtilValidate.isNotEmpty(invoiceItem.get("productId")) && EntityQuery.use(delegator).from("Product").where("productId", invoiceItem.get("productId")).queryOne() == null) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Product Id: " + invoiceItem.get("productId") + " not found for invoice: " + currentInvoiceId + " Item seqId:" + invoiceItem.get("invoiceItemSeqId"));
                       }
                       if (UtilValidate.isEmpty(invoiceItem.get("amount")) && UtilValidate.isEmpty(invoiceItem.get("quantity"))) {
                           newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Either or both quantity and amount is required for invoice: " + currentInvoiceId + " Item seqId:" + invoiceItem.get("invoiceItemSeqId"));
                       }
                   } catch (GenericEntityException e) {
                       Debug.logError("Validation checking problem against database. due to " + e.getMessage(), module);
                   }

                   if (newErrMsgs.size() > 0) {
                       errMsgs.addAll(newErrMsgs);
                   } else {
                       try {
                           Map<String, Object> result = dispatcher.runSync("createInvoiceItem", invoiceItem);
                           Debug.logInfo(":::::::##########:::::invoiceItem:::"+invoiceItem.get("productId"), module);
                           if (ServiceUtil.isError(result)) {
                              return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                           }
                       } catch (GenericServiceException e) {
                           csvReader.close();
                           Debug.logError(e, module);
                           return ServiceUtil.returnError(e.getMessage());
                       }
                   }
               }
           }

       } catch (IOException e) {
           Debug.logError(e, module);
           return ServiceUtil.returnError(e.getMessage());
       }

       if (errMsgs.size() > 0) {
           return ServiceUtil.returnError(errMsgs);
       }

       Map<String, Object> result = ServiceUtil.returnSuccess(UtilProperties.getMessage(resource, "AccountingNewInvoicesCreated", UtilMisc.toMap("invoicesCreated", invoicesCreated), locale));
       result.put("organizationPartyId", organizationPartyId);
       Debug.logInfo(":::Invoice created ::"+invoicesCreated, module);
       Debug.logInfo(":::Invoices created ::"+createdInvoiceIds, module);
       for(String invoiceId :createdInvoiceIds) {
    	   try {
               Map<String, Object> addTaxResult = dispatcher.runSync("addtax", UtilMisc.toMap("invoiceId", invoiceId,"userLogin", userLogin));
               if (ServiceUtil.isError(addTaxResult)) {
                  return ServiceUtil.returnError(ServiceUtil.getErrorMessage(addTaxResult));
               }
           } catch (GenericServiceException e) {
               Debug.logError(e, module);
               return ServiceUtil.returnError(e.getMessage());
           }
    	   Debug.logInfo(":::Tax added for invoice ::"+invoiceId, module);
       }
       return result;
   }	
	
    
    public static Map<String, Object> importPaymentVouchers(DispatchContext dctx, Map<String, Object> context) {
        Locale locale = (Locale) context.get("locale");
       Delegator delegator = dctx.getDelegator();
       LocalDispatcher dispatcher = dctx.getDispatcher();
       GenericValue userLogin = (GenericValue) context.get("userLogin");
       ByteBuffer fileBytes = (ByteBuffer) context.get("uploadedFile");
       
       if (fileBytes == null) {
           return ServiceUtil.returnError(UtilProperties.getMessage(resource, "AccountingUploadedFileDataNotFound", locale));
       }
       
       Debug.logInfo(":::importPaymentVouchers:::", module);
       String organizationPartyId = (String) context.get("organizationPartyId");
       String encoding = System.getProperty("file.encoding");
       String csvString = Charset.forName(encoding).decode(fileBytes).toString();
       final BufferedReader csvReader = new BufferedReader(new StringReader(csvString));
       CSVFormat fmt = CSVFormat.DEFAULT.withHeader();
       List<String> errMsgs = new LinkedList<>();
       List<String> newErrMsgs;
       String lastTransactionDate = null;
       String transactionDate = null;
       int transCreated = 0;
       try {
           for (final CSVRecord rec : fmt.parse(csvReader)) {
        	   transactionDate =  rec.get("transactionDate");
               if (transactionDate != null && !UtilValidate.isEmpty(transactionDate.trim())) {
                   Map<String, Object> acctingTransAndEntriesMap = UtilMisc.toMap(
                           "acctgTransTypeId", rec.get("transactionType"),
                           "debitGlAccountId", rec.get("debitGlAccountId"),
                           "creditGlAccountId", rec.get("creditGlAccountId"),
                           "transactionDate", rec.get("transactionDate"),
                           "voucherRef", rec.get("Vch/Bill No"),
                           "currencyUomId", "INR",
                           "organizationPartyId", "patanjali",
                           "glFiscalTypeId", "ACTUAL",
                           "isPosted", "Y",
                           "amount", rec.get("amount"),
                           "userLogin", userLogin
                           );

                   newErrMsgs = new LinkedList<>();
                   Debug.logInfo("##################acctingTransAndEntriesMap::"+acctingTransAndEntriesMap, module);
                   Debug.logInfo(":::transactionDate:::"+transactionDate+":::debitGlAccountId:::"+rec.get("debitGlAccountId")+":::creditGlAccountId:::"+rec.get("creditGlAccountId")+":::amount:::"+rec.get("amount"), module);
                   try {
                	   
	                   if (UtilValidate.isEmpty(acctingTransAndEntriesMap.get("debitGlAccountId"))) {
	                       newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory debitGlAccountId missing for transaction: " + transactionDate);
	                   }else if (EntityQuery.use(delegator).from("GlAccount").where("glAccountId", acctingTransAndEntriesMap.get("debitGlAccountId")).queryOne() == null) {
	                	   newErrMsgs.add("Line number " + rec.getRecordNumber() + ":debit GlAccount id: " + acctingTransAndEntriesMap.get("debitGlAccountId") + " not found for glAccount: ");
	                   }
	                   if (UtilValidate.isEmpty(acctingTransAndEntriesMap.get("creditGlAccountId"))) {
	                       newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory creditGlAccountId missing for transaction: " + transactionDate);
	                   } else if (EntityQuery.use(delegator).from("GlAccount").where("glAccountId", acctingTransAndEntriesMap.get("creditGlAccountId")).queryOne() == null) {
	                	   newErrMsgs.add("Line number " + rec.getRecordNumber() + ":credit GlAccount id: " + acctingTransAndEntriesMap.get("creditGlAccountId") + " not found for glAccount: ");
	                   }
	                   if (UtilValidate.isEmpty(acctingTransAndEntriesMap.get("amount")) || acctingTransAndEntriesMap.get("amount").toString().contains("-")) {
	                       newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory amount missing for transaction: " + transactionDate);
	                   }
	               } catch (GenericEntityException e) {
	                   Debug.logError("Validation checking problem against database. due to " + e.getMessage(), module);
	               }
                   if (newErrMsgs.size() > 0) {
                       errMsgs.addAll(newErrMsgs);
                   } else {
                       Map<String, Object> transactionResult = null;
                       try {
                    	   transactionResult = dispatcher.runSync("quickCreateAcctgTransAndEntries", acctingTransAndEntriesMap);
                    	                      	   
                           if (ServiceUtil.isError(transactionResult)) {
                              return ServiceUtil.returnError(ServiceUtil.getErrorMessage(transactionResult));
                           }
                           
                           String acctgTransId = (String) transactionResult.get("acctgTransId");
                           Map<String, Object>  postTransaction= dispatcher.runSync("postAcctgTrans", UtilMisc.toMap("acctgTransId",acctgTransId, "userLogin", userLogin));
                           
                       } catch (GenericServiceException e) {
                           csvReader.close();
                           Debug.logError(e, module);
                           return ServiceUtil.returnError(e.getMessage());
                       }
                       transCreated++;
                   }
                   lastTransactionDate = transactionDate;
               }
           }
           Debug.logInfo(":::Transactions created ::"+transCreated, module); 
       } catch (IOException e) {
           Debug.logError(e, module);
           return ServiceUtil.returnError(e.getMessage());
       }

       if (errMsgs.size() > 0) {
           return ServiceUtil.returnError(errMsgs);
       }

       Map<String, Object> result = ServiceUtil.returnSuccess();
       result.put("organizationPartyId", organizationPartyId);
       return result;
   }    
    
    public static Map<String, Object> ImportSupplierProducts(DispatchContext dctx, Map<String, Object> context) {
        Locale locale = (Locale) context.get("locale");
       Delegator delegator = dctx.getDelegator();
       LocalDispatcher dispatcher = dctx.getDispatcher();
       GenericValue userLogin = (GenericValue) context.get("userLogin");
       ByteBuffer fileBytes = (ByteBuffer) context.get("uploadedFile");
       
       if (fileBytes == null) {
           return ServiceUtil.returnError(UtilProperties.getMessage(resource, "AccountingUploadedFileDataNotFound", locale));
       }
       
       Debug.logInfo(":::ImportSupplierProducts:::", module);
       String encoding = System.getProperty("file.encoding");
       String csvString = Charset.forName(encoding).decode(fileBytes).toString();
       final BufferedReader csvReader = new BufferedReader(new StringReader(csvString));
       CSVFormat fmt = CSVFormat.DEFAULT.withHeader();
       List<String> errMsgs = new LinkedList<>();
       List<String> newErrMsgs;
       String productId = null;
       int transCreated = 0;
       try {
           for (final CSVRecord rec : fmt.parse(csvReader)) {
        	   productId =  rec.get("OfbizProductCode");
               if (productId != null && !UtilValidate.isEmpty(productId.trim())) {
                   Map<String, Object> supplierProductMap = UtilMisc.toMap(
                           "productId", productId,
                           "partyId", rec.get("OfbizPartyCode"),
                           "availableFromDate", UtilValidate.isNotEmpty(rec.get("FromDate"))?rec.get("FromDate"):UtilDateTime.nowTimestamp(),
                           "minimumOrderQuantity", UtilValidate.isNotEmpty(rec.get("minimumOrderQuantity"))?rec.get("minimumOrderQuantity"):BigDecimal.ZERO,
                           "currencyUomId", "INR",
                           "supplierProductId", UtilValidate.isNotEmpty(rec.get("supplierProductId"))?rec.get("supplierProductId"):productId,
                           "lastPrice", UtilValidate.isNotEmpty(rec.get("unitPrice"))?rec.get("unitPrice"):BigDecimal.ONE,
                           "userLogin", userLogin
                           );

                   newErrMsgs = new LinkedList<>();
                   Debug.logInfo("##################supplierProductMap::"+supplierProductMap, module);
                   try {
                	   
	                   if (UtilValidate.isEmpty(supplierProductMap.get("productId"))) {
	                       newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory productId missing for entry");
	                   }else if (EntityQuery.use(delegator).from("Product").where("productId", supplierProductMap.get("productId")).queryOne() == null) {
	                	   newErrMsgs.add("Line number " + rec.getRecordNumber() + ":product id: " + supplierProductMap.get("productId") + " not found for prodcut: ");
	                   }
	                   if (UtilValidate.isEmpty(supplierProductMap.get("partyId"))) {
	                       newErrMsgs.add("Line number " + rec.getRecordNumber() + ": Mandatory supplier Id missing for entry");
	                   } else if (EntityQuery.use(delegator).from("Party").where("partyId", supplierProductMap.get("partyId")).queryOne() == null) {
	                	   newErrMsgs.add("Line number " + rec.getRecordNumber() + ":supplier id: " + supplierProductMap.get("partyId") + " not found for supplier: ");
	                   }
	               } catch (GenericEntityException e) {
	                   Debug.logError("Validation checking problem against database. due to " + e.getMessage(), module);
	               }
                   if (newErrMsgs.size() > 0) {
                       errMsgs.addAll(newErrMsgs);
                   } else {
                       Map<String, Object> transactionResult = null;
                       try {
                    	   transactionResult = dispatcher.runSync("createSupplierProduct", supplierProductMap);
                    	                      	   
                           if (ServiceUtil.isError(transactionResult)) {
                              return ServiceUtil.returnError(ServiceUtil.getErrorMessage(transactionResult));
                           }
                           
                       } catch (GenericServiceException e) {
                           csvReader.close();
                           Debug.logError(e, module);
                           return ServiceUtil.returnError(e.getMessage());
                       }
                       transCreated++;
                   }
               }
           }
           Debug.logInfo(":::entery created ::"+transCreated, module); 
       } catch (IOException e) {
           Debug.logError(e, module);
           return ServiceUtil.returnError(e.getMessage());
       }

       if (errMsgs.size() > 0) {
           return ServiceUtil.returnError(errMsgs);
       }

       return ServiceUtil.returnSuccess();
   }   
}
