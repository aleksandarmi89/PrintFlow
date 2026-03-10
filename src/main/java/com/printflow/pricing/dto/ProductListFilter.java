package com.printflow.pricing.dto;

import com.printflow.entity.enums.ProductSource;

public class ProductListFilter {
    private String q;
    private Boolean active;
    private ProductSource source;
    private int page;
    private int size;
    private String sortBy;
    private String sortDir;

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public ProductSource getSource() {
        return source;
    }

    public void setSource(ProductSource source) {
        this.source = source;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }
}
