package com.printflow.service;

import com.printflow.dto.ImportResultDTO;
import com.printflow.entity.Client;
import com.printflow.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelImportService {
    
    private final ClientRepository clientRepository;
    
    
    
    public ExcelImportService(ClientRepository clientRepository) {
		
		this.clientRepository = clientRepository;
	}

	public ImportResultDTO importClientsFromExcel(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        
        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            
            List<Client> clientsToImport = new ArrayList<>();
            int totalRecords = 0;
            int importedRecords = 0;
            int skippedRecords = 0;
            
            // Preskoči header red
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }
            
            while (rowIterator.hasNext()) {
                totalRecords++;
                Row row = rowIterator.next();
                
                try {
                    Client client = parseClientFromRow(row);
                    if (isValidClient(client)) {
                        clientsToImport.add(client);
                        importedRecords++;
                    } else {
                        skippedRecords++;
                    }
                } catch (Exception e) {
                    skippedRecords++;
                }
            }
            
            // Snimi sve klijente
            if (!clientsToImport.isEmpty()) {
                clientRepository.saveAll(clientsToImport);
            }
            
            workbook.close();
            
            result.setTotalRecords(totalRecords);
            result.setImportedRecords(importedRecords);
            result.setSkippedRecords(skippedRecords);
            result.setSuccess(true);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Import failed: " + e.getMessage());
        }
        
        return result;
    }
    
    private Client parseClientFromRow(Row row) {
        Client client = new Client();
        
        // Pretpostavljamo sledeći format kolona:
        // 0: Company Name, 1: Contact Person, 2: Phone, 3: Email, 4: Address, 5: City, 6: Country, 7: Tax ID
        
        client.setCompanyName(getCellValue(row.getCell(0)));
        client.setContactPerson(getCellValue(row.getCell(1)));
        client.setPhone(getCellValue(row.getCell(2)));
        client.setEmail(getCellValue(row.getCell(3)));
        client.setAddress(getCellValue(row.getCell(4)));
        client.setCity(getCellValue(row.getCell(5)));
        client.setCountry(getCellValue(row.getCell(6)));
        client.setTaxId(getCellValue(row.getCell(7)));
        client.setActive(true);
        
        return client;
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    private boolean isValidClient(Client client) {
        return client.getCompanyName() != null && !client.getCompanyName().trim().isEmpty() &&
               client.getPhone() != null && !client.getPhone().trim().isEmpty();
    }
}