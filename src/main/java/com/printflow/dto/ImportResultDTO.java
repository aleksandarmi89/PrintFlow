package com.printflow.dto;

import lombok.Data;

@Data
public class ImportResultDTO {
    private int totalRecords;
    private int importedRecords;
    private int skippedRecords;
    private String errorMessage;
    private boolean success;
    
    
    public ImportResultDTO(int totalRecords, int importedRecords, int skippedRecords, String errorMessage,
			boolean success) {
	
		this.totalRecords = totalRecords;
		this.importedRecords = importedRecords;
		this.skippedRecords = skippedRecords;
		this.errorMessage = errorMessage;
		this.success = success;
	}

	public ImportResultDTO() {
		
	}

	public int getTotalRecords() {
		return totalRecords;
	}

	public void setTotalRecords(int totalRecords) {
		this.totalRecords = totalRecords;
	}

	public int getImportedRecords() {
		return importedRecords;
	}

	public void setImportedRecords(int importedRecords) {
		this.importedRecords = importedRecords;
	}

	public int getSkippedRecords() {
		return skippedRecords;
	}

	public void setSkippedRecords(int skippedRecords) {
		this.skippedRecords = skippedRecords;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public ImportResultDTO(int total, int imported, int skipped) {
        this.totalRecords = total;
        this.importedRecords = imported;
        this.skippedRecords = skipped;
        this.success = true;
    }
}
