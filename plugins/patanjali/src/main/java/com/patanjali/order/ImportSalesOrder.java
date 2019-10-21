package com.patanjali.order;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
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

public class ImportSalesOrder {
	public static String module  = ImportSalesOrder.class.getName();
	public static String JOURNAL_XLSX_FILE_PATH = System.getProperty("ofbiz.home")+"/plugins/patanjali/dtd/xlsfiles/Transaction2.xlsx";
	public static Map<String, Object> importSalesOrder(DispatchContext dctx, Map<String, ? extends Object> context){
    	LocalDispatcher dispatcher = dctx.getDispatcher();
    	Delegator delegator = dctx.getDelegator();
    	GenericValue userLogin = (GenericValue) context.get("userLogin");
    	Workbook workbook = null;
		try {
			workbook = WorkbookFactory.create(new File(JOURNAL_XLSX_FILE_PATH));
		} catch (EncryptedDocumentException | IOException e) {
			e.printStackTrace();
		}
        Sheet sheet = workbook.getSheetAt(2);
        DataFormatter dataFormatter = new DataFormatter();
        Iterator<Row> rowIterator = sheet.iterator();
        int orderItemSeqId = 1;
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if(row.getRowNum() <= 1)continue;
            
      /* OrderHeader   
            date --->  orderDate
            InvoiceNo ---> orderId
            salesChannelEnumId ---> WEB_SALES_CHANNEL
            currencyUom ----> NPL
            orderTypeId -----> SALES_ORDER
            productStoreId	-----> PATANJALINEPAL_STORE
            
       */     
            String date = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(0));
            String invoiceNo = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(1));  
            System.out.println("=========Date=========="+date);
            System.out.println("============Invoice Number=========="+invoiceNo);
            GenericValue OrderHeader = delegator.makeValue("OrderHeader");
            OrderHeader.set("orderDate", UtilDateTime.toTimestamp(date + " 00:00:00"));
            OrderHeader.set("orderId", invoiceNo);
            OrderHeader.set("salesChannelEnumId", "WEB_SALES_CHANNEL");
            OrderHeader.set("currencyUom", "NPR");
            OrderHeader.set("orderTypeId", "SALES_ORDER");
            OrderHeader.set("productStoreId", "PATANJALINEPAL_STORE");
            tobestore(delegator, OrderHeader);
      /*
       OrderItem
productId -----> Code
itemDescription	-----> Product
Quantity ------> qty
unitPrice -----> rate
      
       */
            
            String Code = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(5));
            String Product = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(4)); 
            String qty = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(9));
            String rate = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(11));      
            GenericValue OrderItem = delegator.makeValue("OrderItem");
            OrderItem.set("orderId", invoiceNo);
            OrderItem.set("orderItemSeqId", "00000"+String.valueOf(orderItemSeqId));
            OrderItem.set("productId", Code);
            OrderItem.set("itemDescription", Product);
            OrderItem.set("quantity", new BigDecimal(qty.replace(",", "")));
            OrderItem.set("unitPrice",new BigDecimal(rate.replace(",", "")));
            tobestore(delegator, OrderItem);
            orderItemSeqId++;
     /*
      OrderRole
orderId ----> orderId
partyId ---> party
roleTypeId ----> BILL_TO_CUSTOMER

orderId ----> orderId
partyId ---> party
roleTypeId ----> CUSTOMER

orderId ----> orderId
partyId ---> patanjalinepal
roleTypeId ----> SHIP_FROM_VENDOR

orderId ----> orderId
partyId ---> patanjalinepal
roleTypeId ----> BILL_TO_CUSTOMER	

      */
            
            for(int i=0;i<4;i++) {
            	for(int j=i;j<2;j++) {
            		if(j==0) {
            			
            			String party = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(3));     
                        GenericValue OrderRole = delegator.makeValue("OrderRole");
                        OrderRole.set("orderId", invoiceNo);
                        OrderRole.set("partyId", party);
                        OrderRole.set("roleTypeId", "BILL_TO_CUSTOMER");
                        tobestore(delegator, OrderRole);
            			
            		}
                     if(j==1) {
                    	 String party = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(3));     
                         GenericValue OrderRole = delegator.makeValue("OrderRole");
                         OrderRole.set("orderId", invoiceNo);
                         OrderRole.set("partyId", party);
                         OrderRole.set("roleTypeId", "CUSTOMER");
                         tobestore(delegator, OrderRole);
             			
            		}
            		
            		
            	}
                for(int j=i;j>1;j++) {
                     if(j==2) {
            			
                         GenericValue OrderRole = delegator.makeValue("OrderRole");
                         OrderRole.set("orderId", invoiceNo);
                         OrderRole.set("partyId", "PatanjaliNepal");
                         OrderRole.set("roleTypeId", "SHIP_FROM_VENDOR");
                         tobestore(delegator, OrderRole);
            		}
                     if(j==3) {
                    	    
                         GenericValue OrderRole = delegator.makeValue("OrderRole");
                         OrderRole.set("orderId", invoiceNo);
                         OrderRole.set("partyId", "PatanjaliNepal");
                         OrderRole.set("roleTypeId", "BILL_TO_CUSTOMER");
                         tobestore(delegator, OrderRole);
            			
            		}           		           		
            	}          	
            }          
          
     /*
      OrderAdjustment

orderAdjustmentTypeId ----->  SALES_TAX
comments --> 13% VAT
amount ----> VAT

orderAdjustmentTypeId ----->  SALES_TAX
comments --> Excise Duty
amount ----> Excise Duty(V)
      */
            for(int i=0;i<1;i++) {
            	for(int j=i;j<2;j++) {
            		if(j==0) {
                        String vat = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(20));      

                        GenericValue OrderAdjustment = delegator.makeValue("OrderAdjustment");
                        OrderAdjustment.set("orderAdjustmentTypeId", "SALES_TAX");
                        OrderAdjustment.set("comments", "13% VAT");
                        OrderAdjustment.set("amount", new BigDecimal(vat.replace(",", "")));
                        tobestore(delegator, OrderAdjustment);
            			
            		}
                     if(j==1) {
                         String duty = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(18));      

                         GenericValue OrderAdjustment = delegator.makeValue("OrderAdjustment");
                         OrderAdjustment.set("orderAdjustmentTypeId", "SALES_TAX");
                         OrderAdjustment.set("comments", "Excise Duty");
                         OrderAdjustment.set("amount", new BigDecimal(duty.replace(",", "")));
                         tobestore(delegator, OrderAdjustment);
            			
            		}
            		
            		
            	}
                	
            }          
        }
    	return ServiceUtil.returnSuccess();
	}
	
	private static void tobestore(Delegator delegator,  GenericValue value) {
		try {
			delegator.create(value);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
	}
	
	
}
