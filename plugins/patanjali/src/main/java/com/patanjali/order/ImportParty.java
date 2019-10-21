package com.patanjali.order;

import java.io.File;
import java.io.IOException;
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

public class ImportParty {
	public static String module  = ImportParty.class.getName();
	public static String JOURNAL_XLSX_FILE_PATH = System.getProperty("ofbiz.home")+"/plugins/patanjali/dtd/xlsfiles/party.xlsx";
	public static Map<String, Object> importParty(DispatchContext dctx, Map<String, ? extends Object> context){
    	LocalDispatcher dispatcher = dctx.getDispatcher();
    	Delegator delegator = dctx.getDelegator();
    	GenericValue userLogin = (GenericValue) context.get("userLogin");
    	Workbook workbook = null;
		try {
			workbook = WorkbookFactory.create(new File(JOURNAL_XLSX_FILE_PATH));
		} catch (EncryptedDocumentException | IOException e) {
			e.printStackTrace();
		}
        Sheet sheet = workbook.getSheetAt(0);           
        DataFormatter dataFormatter = new DataFormatter();
        Iterator<Row> rowIterator = sheet.iterator();
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if(row.getRowNum() <= 0)continue;
            String partyCode = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(1));
                      
            GenericValue party = delegator.makeValue("Party");
            party.set("partyId", partyCode);
            party.set("partyTypeId", "PARTY_GROUP");
            tobestore(delegator, party);
            
            GenericValue partyRole = delegator.makeValue("PartyRole");
            partyRole.set("partyId", partyCode);
            partyRole.set("roleTypeId", "CUSTOMER");
            tobestore(delegator, partyRole);
            
        
            String partyName = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(0));
            System.out.println("====================PartyId========="+partyCode);
            System.out.println("====================PartyName====="+partyName);

            if(UtilValidate.isNotEmpty(partyName)) {
            	GenericValue partyGroup = delegator.makeValue("PartyGroup");
                partyGroup.set("partyId", partyCode);
                partyGroup.set("groupName", partyName);
                tobestore(delegator, partyGroup);
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
            
        

