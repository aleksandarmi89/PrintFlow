package com.printflow.entity;

import jakarta.persistence.*;
import lombok.Data;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
@Data
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "company_name", nullable = false)
    private String companyName;
    
    @Column(name = "contact_person")
    private String contactPerson;
    
    @Column(nullable = false)
    private String phone;
    
    @Column(unique = true)
    private String email;
    
    private String address;
    
    private String city;
    
    private String country = "Serbia";
    
    @Column(name = "tax_id")
    private String taxId;
    
    @Column(name = "company_id")
    private String companyId;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(nullable = false)
    private boolean active = true;
    
    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
    
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrder> orders = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }
    

	public Client() {
		
	}


	public Client(Long id, String companyName, String contactPerson, String phone, String email, String address,
			String city, String country, String taxId, String companyId, String notes, boolean active,
			LocalDateTime createdAt, List<WorkOrder> orders) {
		
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
		this.createdAt = createdAt;
		this.orders = orders;
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

	public java.time.LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(java.time.LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public List<WorkOrder> getOrders() {
		return orders;
	}

	public void setOrders(List<WorkOrder> orders) {
		this.orders = orders;
	}
    
	
}