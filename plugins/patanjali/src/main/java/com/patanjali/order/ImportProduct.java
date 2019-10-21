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

public class ImportProduct {
	public static String module  = ImportProduct.class.getName();
	public static String JOURNAL_XLSX_FILE_PATH = System.getProperty("ofbiz.home")+"/plugins/patanjali/dtd/xlsfiles/ProductNew.xlsx";
	public static Map<String, Object> importProducts(DispatchContext dctx, Map<String, ? extends Object> context){
    	LocalDispatcher dispatcher = dctx.getDispatcher();
    //	Delegator delegator = dctx.getDelegator();
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
            if(row.getRowNum() <= 1)continue;
         //   String code = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(0));
         //   String name = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(1));
         //   String productGroup = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(2));
         //   String uom = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(4));
         //   if(uom.equalsIgnoreCase("PCS")) uom ="UQC_pcs";
            String code = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(6));
            String name = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(0));
            String productGroup = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(1));
            String uom = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(3));
         
            System.out.println("=============Code==============="+code);
            System.out.println("=============Name==============="+name);
            System.out.println("=============ProductGroup==============="+productGroup);
            System.out.println("=============UOM==============="+uom);
            try {
				dispatcher.runSync("createProduct", UtilMisc.toMap("productId",code,"internalName",name,"productTypeId",productGroup,
						"quantityUomId",uom,"userLogin",userLogin));
			} catch (GenericServiceException e) {
				e.printStackTrace();
			}
            
           
        }
		return ServiceUtil.returnSuccess();
	}
}


