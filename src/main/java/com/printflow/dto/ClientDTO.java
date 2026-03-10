package com.printflow.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
public class ClientDTO {
    private Long id;
    private String companyName;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String city;
    private String country;
    private String taxId;
    private String companyId;
    private String notes;
    private boolean active;
    
    // Statistika
    private long totalOrders;
    private long completedOrders;
    private long pendingOrders;
	public ClientDTO() {
		
	}
	
	public ClientDTO(Long id, String companyName, String contactPerson, String phone, String email, String address,
			String city, String country, String taxId, String companyId, String notes, boolean active, long totalOrders,
			long completedOrders, long pendingOrders) {
		
		this.id = id;
		this.companyName = companyName;
		this.contactPerson = contactPerson;
		this.phone = phone;
		this.email = email;
		this.address = address;
		this.city = city;
		this.country = country;
		this.taxId = taxId;
		this.companyId = companyId;
		this.notes = notes;
		this.active = active;
		this.totalOrders = totalOrders;
		this.completedOrders = completedOrders;
		this.pendingOrders = pendingOrders;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getCompanyName() {
		return companyName;
	}
	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}
	public String getContactPerson() {
		return contactPerson;
	}
	public void setContactPerson(String contactPerson) {
		this.contactPerson = contactPerson;
	}
	public String getPhone() {
		return phone;
	}
	public void setPhone(String phone) {
		this.phone = phone;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getAddress() {
		return address;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public String getCity() {
		return city;
	}
	public void setCity(String city) {
		this.city = city;
	}
	public String getCountry() {
		return country;
	}
	public void setCountry(String country) {
		this.country = country;
	}
	public String getTaxId() {
		return taxId;
	}
	public void setTaxId(String taxId) {
		this.taxId = taxId;
	}
	public String getCompanyId() {
		return companyId;
	}
	public void setCompanyId(String companyId) {
		this.companyId = companyId;
	}
	public String getNotes() {
		return notes;
	}
	public void setNotes(String notes) {
		this.notes = notes;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	public long getTotalOrders() {
		return totalOrders;
	}
	public void setTotalOrders(long totalOrders) {
		this.totalOrders = totalOrders;
	}
	public long getCompletedOrders() {
		return completedOrders;
	}
	public void setCompletedOrders(long completedOrders) {
		this.completedOrders = completedOrders;
	}
	public long getPendingOrders() {
		return pendingOrders;
	}
	public void setPendingOrders(long pendingOrders) {
		this.pendingOrders = pendingOrders;
	}
	
	
    
}
