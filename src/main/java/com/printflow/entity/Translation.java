package com.printflow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "translations")
@Data
public class Translation {
    @Id
    @Column(name = "message_key", nullable = false, unique = true)
    private String messageKey;
    
    @Column(columnDefinition = "TEXT")
    private String sr;
    
    @Column(columnDefinition = "TEXT")
    private String en;
    
    @Column(columnDefinition = "TEXT")
    private String fr;
    
    @Column(columnDefinition = "TEXT")
    private String ru;
    
    @Column(columnDefinition = "TEXT")
    private String tr;
    
    @Column(name = "category")
    private String category; // UI, EMAIL, ERROR, etc.
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "last_updated")
    private java.time.LocalDateTime lastUpdated = java.time.LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = java.time.LocalDateTime.now();
    }
    

	public Translation() {
		
	}


	public Translation(String messageKey, String sr, String en, String fr, String ru, String tr, String category,
			String description, LocalDateTime lastUpdated) {
		
		this.messageKey = messageKey;
		this.sr = sr;
		this.en = en;
		this.fr = fr;
		this.ru = ru;
		this.tr = tr;
		this.category = category;
		this.description = description;
		this.lastUpdated = lastUpdated;
	}


	public String getMessageKey() {
		return messageKey;
	}

	public void setMessageKey(String messageKey) {
		this.messageKey = messageKey;
	}

	public String getSr() {
		return sr;
	}

	public void setSr(String sr) {
		this.sr = sr;
	}

	public String getEn() {
		return en;
	}

	public void setEn(String en) {
		this.en = en;
	}

	public String getFr() {
		return fr;
	}

	public void setFr(String fr) {
		this.fr = fr;
	}

	public String getRu() {
		return ru;
	}

	public void setRu(String ru) {
		this.ru = ru;
	}

	public String getTr() {
		return tr;
	}

	public void setTr(String tr) {
		this.tr = tr;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public java.time.LocalDateTime getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(java.time.LocalDateTime lastUpdated) {
		this.lastUpdated = lastUpdated;
	}
    
}
