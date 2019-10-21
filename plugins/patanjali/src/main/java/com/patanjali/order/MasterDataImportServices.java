package com.patanjali.order;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
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

public class MasterDataImportServices {
	public static String module  = MasterDataImportServices.class.getName();
	public static String JOURNAL_XLSX_FILE_PATH = System.getProperty("ofbiz.home")+"/plugins/patanjali/dtd/xlsfiles/Transaction.xlsx";
	
	public static Map<String, Object> importProductDetail(DispatchContext dctx, Map<String, ? extends Object> context){
    	Delegator delegator = dctx.getDelegator();
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
            String productId = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(1));
            GenericValue product;
			try {
				product = EntityQuery.use(delegator).from("Product").where("productId", productId.trim()).queryFirst();
			} catch (GenericEntityException e) {
				e.printStackTrace();
				return ServiceUtil.returnError(e.getMessage());
			}
            if(UtilValidate.isNotEmpty(product)) {
            	String comment = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(6));
            	if(UtilValidate.isNotEmpty(comment)) {
            		product.set("comments", comment.trim());
            		try {
            			product.store();
					} catch (GenericEntityException e) {
						e.printStackTrace();
						return ServiceUtil.returnError(e.getMessage());
					}
            	}
            	
            	String facilityId = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(5));
            	if(UtilValidate.isNotEmpty(facilityId)) {
            		switch (facilityId.trim()) {
					case "FG B BLOCK":
						facilityId ="FG-B-BLOCK";
						break;
					
					case "FG A BLOCK":
						facilityId ="FG-A-BLOCK";
						break;
					
					case "GENERAL STORE":
						facilityId ="PATANJALI_WAREHOUSE";
						break;
					
					case "DEFAULT":
						facilityId ="DEFAULT";
						break;
						
					case "COUNTER-GODOWN":
						facilityId ="COUNTER-GODOWN";
						break;
						
					case "RETAIL GODOWN":
						facilityId ="RETAIL-GODOWN";
						break;
						
					case "RM/PM-A BLOCK":
						facilityId ="RM-PM-A-BLOCK";
						break;
						
					case "RM/PM-B BLOCK":
						facilityId ="RM-PM-B-BLOCK";
						break;

					default:
						break;
					}
            		GenericValue productFacility = delegator.makeValue("ProductFacility");
            		productFacility.set("productId", product.get("productId"));
            		productFacility.set("facilityId", facilityId.trim());
            		try {
						productFacility.create();
					} catch (GenericEntityException e) {
						e.printStackTrace();
						return ServiceUtil.returnError(e.getMessage());
					}
            	}
            }
        }
        try {
			workbook.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ServiceUtil.returnSuccess();
	}
	
	public static Map<String, Object> importProductStock(DispatchContext dctx, Map<String, ? extends Object> context){
    	Delegator delegator = dctx.getDelegator();
    	LocalDispatcher dispatcher = dctx.getDispatcher();
    	GenericValue userLogin = (GenericValue) context.get("userLogin");
    	int startRow = (Integer) context.get("startRow");
    	int endRow = (Integer) context.get("endRow");
    	Workbook workbook = null;
		try {
			workbook = WorkbookFactory.create(new File(JOURNAL_XLSX_FILE_PATH));
		} catch (EncryptedDocumentException | IOException e) {
			e.printStackTrace();
		}
        DataFormatter dataFormatter = new DataFormatter();
        Sheet stockSheet = workbook.getSheetAt(7);
        Iterator<Row> iterator = stockSheet.iterator();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            if(row.getRowNum() == endRow)return ServiceUtil.returnSuccess();
            if(row.getRowNum() < startRow)continue;
            boolean beganTransaction = false;
        	try {
                beganTransaction = TransactionUtil.begin(2700);
            } catch (GenericTransactionException e1) {
                Debug.logError(e1, "[Delegator] Could not begin transaction: " + e1.toString(), module);
            }
            String productId = dataFormatter.formatCellValue(stockSheet.getRow(row.getRowNum()).getCell(0));
            GenericValue product;
			try {
				product = EntityQuery.use(delegator).from("Product").where("productId", productId.trim()).queryFirst();
			} catch (GenericEntityException e) {
				e.printStackTrace();
				return ServiceUtil.returnError(e.getMessage());
			}
            if(UtilValidate.isNotEmpty(product)) {
            	String quantity = dataFormatter.formatCellValue(stockSheet.getRow(row.getRowNum()).getCell(3));
            	if(UtilValidate.isNotEmpty(quantity)) {
            		String value = dataFormatter.formatCellValue(stockSheet.getRow(row.getRowNum()).getCell(4));
            		GenericValue productFacility;
					try {
						productFacility = EntityQuery.use(delegator).from("ProductFacility").where("productId", product.get("productId")).queryFirst();
					} catch (GenericEntityException e1) {
						e1.printStackTrace();
						return ServiceUtil.returnError(e1.getMessage());
					}
            		String facilityId = "PATANJALI_WAREHOUSE";
            		if(productFacility != null) {
            			facilityId = productFacility.getString("facilityId");
            		}
            		
                	try {
                		dispatcher.runSync("receiveInventoryProduct", UtilMisc.toMap("userLogin", userLogin, "facilityId", facilityId, "productId", product.get("productId"),
    							"inventoryItemTypeId","NON_SERIAL_INV_ITEM","datetimeReceived",  UtilDateTime.nowTimestamp(),"quantityRejected",BigDecimal.ZERO,
    							"ownerPartyId", "patanjalinepal", "quantityAccepted", new BigDecimal(quantity.replace(",", "")), "unitCost", new BigDecimal(value.replace(",", ""))));
    				} catch (GenericServiceException e) {
    					e.printStackTrace();
    					return ServiceUtil.returnError(e.getMessage());
    				}finally {
    		            try {
    		                // only commit the transaction if we started one... this will throw an exception if it fails
    		                TransactionUtil.commit(beganTransaction);
    		            } catch (GenericEntityException e) {
    		                Debug.logError(e, "Could not commit transaction"+e.getMessage(), module);
    		            }
    		        }
            	}
            }
        }
        try {
			workbook.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ServiceUtil.returnSuccess();
	}
	
	public static Map<String, Object> importCustomer(DispatchContext dctx, Map<String, ? extends Object> context){
    	LocalDispatcher dispatcher = dctx.getDispatcher();
    	Delegator delegator = dctx.getDelegator();
    	int startRow = (Integer) context.get("startRow");
    	int endRow = (Integer) context.get("endRow");
    	GenericValue userLogin = (GenericValue) context.get("userLogin");
    	Workbook workbook = null;
		try {
			workbook = WorkbookFactory.create(new File(JOURNAL_XLSX_FILE_PATH));
		} catch (EncryptedDocumentException | IOException e) {
			e.printStackTrace();
		}
        Sheet sheet = workbook.getSheetAt(1);
        DataFormatter dataFormatter = new DataFormatter();
        Iterator<Row> rowIterator = sheet.iterator();
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if(row.getRowNum() == endRow)return ServiceUtil.returnSuccess();
            if(row.getRowNum() < startRow)continue;
            String partyName = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(0));
            String partyCode = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(1));
            String type = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(2));
            String area = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(4));
            String roleTypeIdTo = "";
            if(UtilValidate.isNotEmpty(type)) {
            	switch (type.trim()) {
				case "SUNDRY DEBTORS":
					type = "SUPPLIER";
					roleTypeIdTo = "BILL_FROM_VENDOR";
					break;
					
				case "SUNDRY CREDITORS":
					type = "CUSTOMER";
					roleTypeIdTo = "BILL_TO_CUSTOMER";

				default:
					break;
				}
            }
            GenericValue existParty  = null;
			try {
				existParty = EntityQuery.use(delegator).from("Party").where("partyId", partyCode).queryOne();
			} catch (GenericEntityException e1) {
				e1.printStackTrace();
			}
            if(existParty != null)  continue;
            GenericValue party = delegator.makeValue("Party");
            party.set("partyId", partyCode);
            party.set("partyTypeId", "PARTY_GROUP");
            party.set("description", area);
            tobestore(delegator, party);
            
            if(type.equalsIgnoreCase("CUSTOMER")) {
	            GenericValue partyRole = delegator.makeValue("PartyRole");
	            partyRole.set("partyId", partyCode);
	            partyRole.set("roleTypeId", "CUSTOMER");
	            tobestore(delegator, partyRole);
	            
	            GenericValue billToCustomerRole = delegator.makeValue("PartyRole");
	            billToCustomerRole.set("partyId", partyCode);
	            billToCustomerRole.set("roleTypeId", "BILL_TO_CUSTOMER");
	            tobestore(delegator, billToCustomerRole);
            }
            
            if(type.equalsIgnoreCase("SUPPLIER")) {
            	GenericValue partyRole = delegator.makeValue("PartyRole");
                partyRole.set("partyId", partyCode);
                partyRole.set("roleTypeId", "SUPPLIER");
                tobestore(delegator, partyRole);
                
                GenericValue billToCustomerRole = delegator.makeValue("PartyRole");
                billToCustomerRole.set("partyId", partyCode);
                billToCustomerRole.set("roleTypeId", "BILL_FROM_VENDOR");
                tobestore(delegator, billToCustomerRole);
            }
            
            if(UtilValidate.isNotEmpty(partyName)) {
            	GenericValue partyGroup = delegator.makeValue("PartyGroup");
                partyGroup.set("partyId", partyCode);
                partyGroup.set("groupName", partyName);
                tobestore(delegator, partyGroup);
            }
            
            String agentName = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(3));
            if(UtilValidate.isNotEmpty(agentName)) {
            	GenericValue partyAgent = delegator.makeValue("Party");
                String agentPartyId = delegator.getNextSeqId("Party");
                partyAgent.set("partyId", agentPartyId);
                partyAgent.set("partyTypeId", "PERSON");
                tobestore(delegator, partyAgent);
                
                GenericValue person = delegator.makeValue("Person");
                person.set("partyId", agentPartyId);
                person.set("firstName", agentName);
                tobestore(delegator, person);
                
                GenericValue agentRole = delegator.makeValue("PartyRole");
                agentRole.set("partyId", agentPartyId);
                agentRole.set("roleTypeId", "AGENT");
                tobestore(delegator, agentRole);
                try {
    				dispatcher.runSync("createPartyRelationship", UtilMisc.toMap("partyIdTo", partyCode,"roleTypeIdTo", roleTypeIdTo,"partyRelationshipTypeId","CUSTOMER_REL",
    						"partyIdFrom", agentPartyId,"roleTypeIdFrom", "AGENT","statusId","PARTYREL_CREATED","fromDate", UtilDateTime.nowTimestamp(),"userLogin", userLogin));
    			} catch (GenericServiceException e) {
    				e.printStackTrace();
    			}
            }
            
            String partyPanNumber = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(5));
            if(UtilValidate.isNotEmpty(partyPanNumber)) {
            	GenericValue partyIdentification = delegator.makeValue("PartyIdentification");
                partyIdentification.set("partyId", partyCode);
                partyIdentification.set("partyIdentificationTypeId", "PAN");
                partyIdentification.set("idValue", partyPanNumber);
                tobestore(delegator, partyIdentification);
            }
            
//            String partyCreditDay = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(11));
//            if(UtilValidate.isNotEmpty(partyCreditDay)) {
//            	GenericValue partyCreditDayIdentification = delegator.makeValue("PartyIdentification");
//                partyCreditDayIdentification.set("partyId", partyCode);
//                partyCreditDayIdentification.set("partyIdentificationTypeId", "CDAY");
//                partyCreditDayIdentification.set("idValue", partyCreditDay);
//                tobestore(delegator, partyCreditDayIdentification);
//            }
            
            String partyContactPerson = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(6));
            if(UtilValidate.isNotEmpty(partyContactPerson)) {
            	GenericValue contactPersonParty = delegator.makeValue("Party");
                String contactPersonPartyId = delegator.getNextSeqId("Party");
                contactPersonParty.set("partyId", contactPersonPartyId);
                contactPersonParty.set("partyTypeId", "PERSON");
                tobestore(delegator, contactPersonParty);
                
                GenericValue contactPerson = delegator.makeValue("Person");
                contactPerson.set("partyId", contactPersonPartyId);
                contactPerson.set("firstName", partyContactPerson.replace("_x000D_", ""));
                tobestore(delegator, contactPerson);
                
                GenericValue contactRole = delegator.makeValue("PartyRole");
                contactRole.set("partyId", contactPersonPartyId);
                contactRole.set("roleTypeId", "CONTACT");
                tobestore(delegator, contactRole);
                
                try {
    				dispatcher.runSync("createPartyRelationship", UtilMisc.toMap("partyIdTo", partyCode,"roleTypeIdTo", roleTypeIdTo,"partyRelationshipTypeId","CUSTOMER_REL",
    						"partyIdFrom", contactPersonPartyId,"roleTypeIdFrom", "CONTACT","statusId","PARTYREL_CREATED","fromDate", UtilDateTime.nowTimestamp(),"userLogin", userLogin));
    			} catch (GenericServiceException e) {
    				e.printStackTrace();
    			}
                String directions = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(7));
                String partyAddress = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(8));
                if(UtilValidate.isNotEmpty(partyAddress)) {
                	String arr[] = partyAddress.split("\\,");
            		String address1 = null;
            		String address2 = null;
            		String city = "";
            		String countryGeoId = "_NA_";
            		String postalCode = "";
                	if(arr.length ==1) {
            			address1 = arr[0];
            		}
            		
            		if(arr.length ==2) {
            			address1 = arr[0];
            			address2 = arr[1];
            		}
            		
            		if(arr.length ==3) {
            			address1 = arr[0];
            			address2 = arr[1];
            			city = arr[2];
            		}
            		
            		if(arr.length ==4) {
            			address1 = arr[0];
            			address2 = arr[1];
            			city = arr[2] + "," +arr[3];
            			countryGeoId = "_NA_";
            		}
            		
            		if(arr.length ==5) {
            			address1 = arr[0]+","+arr[1];
            			address2 = arr[2]+","+arr[3];
            			city = arr[4];
            			countryGeoId = "_NA_";
            		}
            		if(arr.length ==6) {
            			address1 = arr[0]+","+arr[1];
            			address2 = arr[2]+","+arr[3];
            			city = arr[4]+","+arr[5];
            			countryGeoId = "_NA_";
            		}
            		if(arr.length ==7) {
            			address1 = arr[0]+","+arr[1];
            			address2 = arr[2]+","+arr[3]+","+arr[4];
            			city = arr[5]+","+arr[6];
            			countryGeoId = "_NA_";
            		}
            		if(arr.length ==8) {
            			address1 = arr[0]+","+arr[1];
            			address2 = arr[2]+","+arr[3]+","+arr[4];
            			city = arr[5]+","+arr[6]+","+arr[7];
            			countryGeoId = "_NA_";
            		}
            		if(arr.length ==9) {
            			address1 = arr[0]+","+arr[1]+","+arr[2];
            			address2 = arr[3]+","+arr[4]+","+arr[5];
            			city = arr[6]+","+arr[7]+","+arr[8];
            			countryGeoId = "_NA_";
            		}
            		
                	try {
						dispatcher.runSync("createPartyPostalAddress", UtilMisc.toMap("partyId", contactPersonPartyId, "toName",partyContactPerson,
								"address1", address1, "address2", address2, "city", city, "countryGeoId", countryGeoId,"postalCode",postalCode,
								"userLogin", userLogin, "directions",directions));
					} catch (GenericServiceException e) {
						e.printStackTrace();
					}
                }
                String partyPhone = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(9));
                if(UtilValidate.isNotEmpty(partyPhone)) {
                	partyPhone = partyPhone.replace(" / ", "/");
                	String[] arr = partyPhone.split("/");
            		for(int i=0; i <arr.length; i++) {
            			try {
    						dispatcher.runSync("createPartyTelecomNumber", UtilMisc.toMap("partyId", contactPersonPartyId, "contactNumber", arr[i],
    								"userLogin", userLogin));
    					} catch (GenericServiceException e) {
    						e.printStackTrace();
    					}
            		}
                }
                	
                String partyEmail = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(10));
                if(UtilValidate.isNotEmpty(partyEmail)) {
                	try {
						dispatcher.runSync("createPartyEmailAddress", UtilMisc.toMap("emailAddress", partyEmail.toLowerCase(), "partyId", contactPersonPartyId,
								"userLogin", userLogin));
					} catch (GenericServiceException e) {
						e.printStackTrace();
					}
                }
            }else {
            	String directions = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(7));
            	String partyAddress = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(8));
                if(UtilValidate.isNotEmpty(partyAddress)) {
                	String arr[] = partyAddress.split("\\,");
            		String address1 = null;
            		String address2 = null;
            		String city = "";
            		String countryGeoId = "_NA_";
            		String postalCode = "";
                	if(arr.length ==1) {
            			address1 = arr[0];
            		}
            		
            		if(arr.length ==2) {
            			address1 = arr[0];
            			address2 = arr[1];
            		}
            		
            		if(arr.length ==3) {
            			address1 = arr[0];
            			address2 = arr[1];
            			city = arr[2];
            		}
            		
            		if(arr.length ==4) {
            			address1 = arr[0];
            			address2 = arr[1];
            			city = arr[2] + "," +arr[3];
            			countryGeoId = "_NA_";
            		}
            		
            		if(arr.length ==5) {
            			address1 = arr[0]+","+arr[1];
            			address2 = arr[2]+","+arr[3];
            			city = arr[4];
            			countryGeoId = "_NA_";
            		}
            		if(arr.length ==6) {
            			address1 = arr[0]+","+arr[1];
            			address2 = arr[2]+","+arr[3];
            			city = arr[4]+","+arr[5];
            			countryGeoId = "_NA_";
            		}
            		if(arr.length ==7) {
            			address1 = arr[0]+","+arr[1];
            			address2 = arr[2]+","+arr[3]+","+arr[4];
            			city = arr[5]+","+arr[6];
            			countryGeoId = "_NA_";
            		}
            		if(arr.length ==8) {
            			address1 = arr[0]+","+arr[1];
            			address2 = arr[2]+","+arr[3]+","+arr[4];
            			city = arr[5]+","+arr[6]+","+arr[7];
            			countryGeoId = "_NA_";
            		}
            		if(arr.length ==9) {
            			address1 = arr[0]+","+arr[1]+","+arr[2];
            			address2 = arr[3]+","+arr[4]+","+arr[5];
            			city = arr[6]+","+arr[7]+","+arr[8];
            			countryGeoId = "_NA_";
            		}
                	try {
						dispatcher.runSync("createPartyPostalAddress", UtilMisc.toMap("partyId", partyCode, "toName",partyContactPerson,
								"address1", address1, "address2", address2, "city", city, "countryGeoId", countryGeoId,"postalCode",postalCode,
								"userLogin", userLogin, "directions",directions));
					} catch (GenericServiceException e) {
						e.printStackTrace();
					}
                }
                String partyPhone = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(9));
                if(UtilValidate.isNotEmpty(partyPhone)) {
                	partyPhone = partyPhone.replace(" / ", "/");
                	String[] arr = partyPhone.split("/");
            		for(int i=0; i <arr.length; i++) {
            			try {
    						dispatcher.runSync("createPartyTelecomNumber", UtilMisc.toMap("partyId", partyCode, "contactNumber", arr[i],
    								"userLogin", userLogin));
    					} catch (GenericServiceException e) {
    						e.printStackTrace();
    					}
            		}
                }
                	
                String partyEmail = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(10));
                if(UtilValidate.isNotEmpty(partyEmail)) {
                	try {
						dispatcher.runSync("createPartyEmailAddress", UtilMisc.toMap("emailAddress", partyEmail.toLowerCase(), "partyId", partyCode,
								"userLogin", userLogin));
					} catch (GenericServiceException e) {
						e.printStackTrace();
					}
                }
            }
            
//            String partyBankGurantee = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(16));
//            if(UtilValidate.isNotEmpty(partyBankGurantee)) {
//            	GenericValue partyBankGuranteeIdentification = delegator.makeValue("PartyIdentification");
//                partyBankGuranteeIdentification.set("partyId", partyCode);
//                partyBankGuranteeIdentification.set("partyIdentificationTypeId", "BANKGURANTEE");
//                partyBankGuranteeIdentification.set("idValue", partyBankGurantee);
//                tobestore(delegator, partyBankGuranteeIdentification);
//            }
            
//            String partyBank = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(17));
//            if(UtilValidate.isNotEmpty(partyBank)) {
//            	GenericValue partyBankIdentification = delegator.makeValue("PartyIdentification");
//                partyBankIdentification.set("partyId", partyCode);
//                partyBankIdentification.set("partyIdentificationTypeId", "BANK");
//                partyBankIdentification.set("idValue", partyBank);
//                tobestore(delegator, partyBankIdentification);
//            }
            
//            String partyBankGuranteeExpireDate = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(18));
//            if(UtilValidate.isNotEmpty(partyBankGuranteeExpireDate)) {
//            	GenericValue partyBankGuranteeExpireDateIdentification = delegator.makeValue("PartyIdentification");
//                partyBankGuranteeExpireDateIdentification.set("partyId", partyCode);
//                partyBankGuranteeExpireDateIdentification.set("partyIdentificationTypeId", "EXPDATE");
//                partyBankGuranteeExpireDateIdentification.set("idValue", partyBankGuranteeExpireDate);
//                tobestore(delegator, partyBankGuranteeExpireDateIdentification);
//            }
        }
        try {
			workbook.close();
		} catch (IOException e) {
			e.printStackTrace();
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
	
	public static Map<String, Object> importVendor(DispatchContext ctx, Map<String, ? extends Object> context){
		LocalDispatcher dispatcher = ctx.getDispatcher();
		Delegator delegator = ctx.getDelegator();
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
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if(row.getRowNum() <= 6)continue;
            String partyCode = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(0));
            GenericValue existParty  = null;
			try {
				existParty = EntityQuery.use(delegator).from("Party").where("partyId", partyCode).queryOne();
			} catch (GenericEntityException e1) {
				e1.printStackTrace();
			}
            if(existParty != null)  continue;
            GenericValue party = delegator.makeValue("Party");
            party.set("partyId", partyCode);
            party.set("partyTypeId", "PARTY_GROUP");
            tobestore(delegator, party);
            
            GenericValue partyRole = delegator.makeValue("PartyRole");
            partyRole.set("partyId", partyCode);
            partyRole.set("roleTypeId", "SUPPLIER");
            tobestore(delegator, partyRole);
            
            GenericValue billToCustomerRole = delegator.makeValue("PartyRole");
            billToCustomerRole.set("partyId", partyCode);
            billToCustomerRole.set("roleTypeId", "BILL_FROM_VENDOR");
            tobestore(delegator, billToCustomerRole);
            
            String partyName = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(1));
            if(UtilValidate.isNotEmpty(partyName)) {
            	GenericValue partyGroup = delegator.makeValue("PartyGroup");
                partyGroup.set("partyId", partyCode);
                partyGroup.set("groupName", partyName);
                tobestore(delegator, partyGroup);
            }
            
            String partyPanNumber = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(4));
            if(UtilValidate.isNotEmpty(partyPanNumber)) {
            	GenericValue partyIdentification = delegator.makeValue("PartyIdentification");
                partyIdentification.set("partyId", partyCode);
                partyIdentification.set("partyIdentificationTypeId", "PAN");
                partyIdentification.set("idValue", partyPanNumber);
                tobestore(delegator, partyIdentification);
            }
            
            String partyContactPerson = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(6));
            if(UtilValidate.isNotEmpty(partyContactPerson)) {
            	GenericValue contactPersonParty = delegator.makeValue("Party");
                String contactPersonPartyId = delegator.getNextSeqId("Party");
                contactPersonParty.set("partyId", contactPersonPartyId);
                contactPersonParty.set("partyTypeId", "PERSON");
                tobestore(delegator, contactPersonParty);
                
                GenericValue contactPerson = delegator.makeValue("Person");
                contactPerson.set("partyId", contactPersonPartyId);
                contactPerson.set("firstName", partyContactPerson.replace("_x000D_", ""));
                tobestore(delegator, contactPerson);
                
                GenericValue contactRole = delegator.makeValue("PartyRole");
                contactRole.set("partyId", contactPersonPartyId);
                contactRole.set("roleTypeId", "CONTACT");
                tobestore(delegator, contactRole);
                
                try {
    				dispatcher.runSync("createPartyRelationship", UtilMisc.toMap("partyIdTo", partyCode,"roleTypeIdTo", "BILL_FROM_VENDOR","partyRelationshipTypeId","CUSTOMER_REL",
    						"partyIdFrom", contactPersonPartyId,"roleTypeIdFrom", "CONTACT","statusId","PARTYREL_CREATED","fromDate", UtilDateTime.nowTimestamp(),"userLogin", userLogin));
    			} catch (GenericServiceException e) {
    				e.printStackTrace();
    			}
                
                String partyAddress = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(7));
                if(UtilValidate.isNotEmpty(partyAddress)) {
                	String address1 = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(8));
                	if(UtilValidate.isEmpty(address1)) address1="_NA_";
            		String address2 = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(9));
            		if(UtilValidate.isEmpty(address2)) address2="";
            		String postalCode = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(10));
            		if(UtilValidate.isEmpty(postalCode)) postalCode="";
            		String city = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(11));
            		if(UtilValidate.isEmpty(city)) city="";
            		String stateProvinceGeoId = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(12));
            		if(UtilValidate.isEmpty(stateProvinceGeoId)) stateProvinceGeoId =null;
            		String countryGeoId = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(13));
            		if(UtilValidate.isNotEmpty(countryGeoId)) {
            			if(countryGeoId.trim().equalsIgnoreCase("INDIA")) {
            				countryGeoId = "IND";
            			}
            			if(countryGeoId.trim().equalsIgnoreCase("NEPAL")) {
            				countryGeoId = "NPL";
            			}
            		}
                	
                	try {
						dispatcher.runSync("createPartyPostalAddress", UtilMisc.toMap("partyId", contactPersonPartyId, "toName",partyContactPerson,
								"address1", address1, "address2", address2, "city", city, "countryGeoId", countryGeoId,"postalCode",postalCode,
								"userLogin", userLogin, "stateProvinceGeoId", stateProvinceGeoId));
					} catch (GenericServiceException e) {
						e.printStackTrace();
					}
                }
                String partyPhone = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(14));
                if(UtilValidate.isNotEmpty(partyPhone)) {
                	String arr[] = partyPhone.split("\\/");
            		for(int i=0; i<arr.length; i++) {
            			try {
    						dispatcher.runSync("createPartyTelecomNumber", UtilMisc.toMap("partyId", contactPersonPartyId, "contactNumber", arr[i].trim(),
    								"userLogin", userLogin));
    					} catch (GenericServiceException e) {
    						e.printStackTrace();
    					}
            		}
                }
                	
                String partyEmail = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(15));
                if(UtilValidate.isNotEmpty(partyEmail)) {
                	try {
						dispatcher.runSync("createPartyEmailAddress", UtilMisc.toMap("emailAddress", partyEmail.toLowerCase(), "partyId", contactPersonPartyId,
								"userLogin", userLogin));
					} catch (GenericServiceException e) {
						e.printStackTrace();
					}
                }
            }else {
            	String partyAddress = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(7));
                if(UtilValidate.isNotEmpty(partyAddress)) {
                	String address1 = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(8));
                	if(UtilValidate.isEmpty(address1)) address1="_NA_";
            		String address2 = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(9));
            		if(UtilValidate.isEmpty(address2)) address2="";
            		String postalCode = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(10));
            		if(UtilValidate.isEmpty(postalCode)) postalCode="";
            		String city = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(11));
            		if(UtilValidate.isEmpty(city)) city="";
            		String stateProvinceGeoId = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(12));
            		if(UtilValidate.isEmpty(stateProvinceGeoId)) stateProvinceGeoId =null;
            		String countryGeoId = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(13));
            		if(UtilValidate.isNotEmpty(countryGeoId)) {
            			if(countryGeoId.trim().equalsIgnoreCase("INDIA")) {
            				countryGeoId = "IND";
            			}
            			if(countryGeoId.trim().equalsIgnoreCase("NEPAL")) {
            				countryGeoId = "NPL";
            			}
            		}
                	try {
						dispatcher.runSync("createPartyPostalAddress", UtilMisc.toMap("partyId", partyCode, "toName",partyContactPerson,
								"address1", address1, "address2", address2, "city", city, "countryGeoId", countryGeoId,"postalCode",postalCode,
								"userLogin", userLogin,"stateProvinceGeoId",stateProvinceGeoId));
					} catch (GenericServiceException e) {
						e.printStackTrace();
					}
                }
                String partyPhone = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(14));
                if(UtilValidate.isNotEmpty(partyPhone)) {
                	String arr[] = partyPhone.split("\\/");
            		for(int i=0; i<arr.length; i++) {
            			try {
    						dispatcher.runSync("createPartyTelecomNumber", UtilMisc.toMap("partyId", partyCode, "contactNumber", arr[i].trim(),
    								"userLogin", userLogin));
    					} catch (GenericServiceException e) {
    						e.printStackTrace();
    					}
            		}
                }
                	
                String partyEmail = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(15));
                if(UtilValidate.isNotEmpty(partyEmail)) {
                	try {
						dispatcher.runSync("createPartyEmailAddress", UtilMisc.toMap("emailAddress", partyEmail.toLowerCase(), "partyId", partyCode,
								"userLogin", userLogin));
					} catch (GenericServiceException e) {
						e.printStackTrace();
					}
                }
            }
            
            String partyUrl = dataFormatter.formatCellValue(sheet.getRow(row.getRowNum()).getCell(16));
            System.out.print("==partyUrl="+partyUrl);
            System.out.println();
        }
        
        try {
			workbook.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ServiceUtil.returnSuccess();
	}
}
