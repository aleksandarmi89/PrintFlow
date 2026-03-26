package com.printflow.dto;

public class CompanyBrandingDTO {
    private final Long companyId;
    private final String name;
    private final String logoUrl;
    private final String email;
    private final String phone;
    private final String address;
    private final String website;
    private final String legalName;
    private final String taxId;
    private final String registrationNumber;
    private final String billingEmail;
    private final String bankName;
    private final String bankAccount;
    private final String primaryColor;

    public CompanyBrandingDTO(Long companyId, String name, String logoUrl, String email,
                              String phone, String address, String website, String primaryColor) {
        this(companyId, name, logoUrl, email, phone, address, website, null, null, null, null, null, null, primaryColor);
    }

    public CompanyBrandingDTO(Long companyId, String name, String logoUrl, String email,
                              String phone, String address, String website,
                              String legalName, String taxId, String registrationNumber,
                              String billingEmail, String bankName, String bankAccount,
                              String primaryColor) {
        this.companyId = companyId;
        this.name = name;
        this.logoUrl = logoUrl;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.website = website;
        this.legalName = legalName;
        this.taxId = taxId;
        this.registrationNumber = registrationNumber;
        this.billingEmail = billingEmail;
        this.bankName = bankName;
        this.bankAccount = bankAccount;
        this.primaryColor = primaryColor;
    }

    public Long getCompanyId() { return companyId; }
    public String getName() { return name; }
    public String getLogoUrl() { return logoUrl; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getWebsite() { return website; }
    public String getLegalName() { return legalName; }
    public String getTaxId() { return taxId; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getBillingEmail() { return billingEmail; }
    public String getBankName() { return bankName; }
    public String getBankAccount() { return bankAccount; }
    public String getPrimaryColor() { return primaryColor; }
}
