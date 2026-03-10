package com.printflow.dto;

public class CompanyBrandingDTO {
    private final Long companyId;
    private final String name;
    private final String logoUrl;
    private final String email;
    private final String phone;
    private final String address;
    private final String website;
    private final String primaryColor;

    public CompanyBrandingDTO(Long companyId, String name, String logoUrl, String email,
                              String phone, String address, String website, String primaryColor) {
        this.companyId = companyId;
        this.name = name;
        this.logoUrl = logoUrl;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.website = website;
        this.primaryColor = primaryColor;
    }

    public Long getCompanyId() { return companyId; }
    public String getName() { return name; }
    public String getLogoUrl() { return logoUrl; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getWebsite() { return website; }
    public String getPrimaryColor() { return primaryColor; }
}
