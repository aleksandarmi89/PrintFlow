package com.printflow.dto;

import lombok.Data;

@Data
public class PaginationDTO {
    private int currentPage;
    private int pageSize;
    private int total;
    private int start;
    private int end;
    
    public int getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(int currentPage) {
		this.currentPage = currentPage;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public int getEnd() {
		return end;
	}

	public void setEnd(int end) {
		this.end = end;
	}
	public PaginationDTO() {}
	public PaginationDTO(int currentPage, int pageSize, int total, int start, int end) {
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.total = total;
        this.start = start;
        this.end = end;
    }
}