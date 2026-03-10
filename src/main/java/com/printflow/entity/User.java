package com.printflow.entity;

import com.printflow.entity.enums.Language;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "users")
@Filter(name = "tenantFilter", condition = "tenant_id = :companyId")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    @Enumerated(EnumType.STRING)
    private Role role;
    
    @Column(name = "language_preference", length = 5)
    private String languagePreference = "sr";
    
    private boolean active = true;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 2)
    private Language language = Language.SR;
    
    private String email;
    private String phone;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @Column(name = "full_name")
    private String fullName;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    @Column(name = "available")
    private Boolean available = true;
    
    @Column(name = "department")
    private String department;
    
    @Column(name = "position")
    private String position;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;
    
    @Column(name = "notes", length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Company company;
    
    // Konstruktori
    public User() {}
    
    public User(Long id, String username, String password, Role role, String languagePreference, boolean active,
			Language language, String email, String phone, String firstName, String lastName, String fullName,
			LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime lastLogin, Boolean available,
			String department, String position, String notes) {
	
		this.id = id;
		this.username = username;
		this.password = password;
		this.role = role;
		this.languagePreference = languagePreference;
		this.active = active;
		this.language = language;
		this.email = email;
		this.phone = phone;
		this.firstName = firstName;
		this.lastName = lastName;
		this.fullName = fullName;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.lastLogin = lastLogin;
		this.available = available;
		this.department = department;
		this.position = position;
		this.notes = notes;
	}

	// Getters i Setters (isti kao prethodni)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    
    public String getLanguagePreference() { return languagePreference; }
    public void setLanguagePreference(String languagePreference) { this.languagePreference = languagePreference; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    public Language getLanguage() { return language; }
    public void setLanguage(Language language) { this.language = language; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { 
        this.firstName = firstName;
        updateFullName();
    }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { 
        this.lastName = lastName;
        updateFullName();
    }
    
    public String getFullName() { 
        if (fullName == null || fullName.trim().isEmpty()) {
            updateFullName();
        }
        return fullName; 
    }
    
    
    public Boolean getAvailable() {
        return available;
    }

	public void setFullName(String fullName) { this.fullName = fullName; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
    
    public boolean isAvailable() { return available != null && available; }
    public void setAvailable(boolean available) { this.available = available; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    
    private void updateFullName() {
        if (firstName != null && lastName != null) {
            this.fullName = firstName + " " + lastName;
        } else if (firstName != null) {
            this.fullName = firstName;
        } else if (lastName != null) {
            this.fullName = lastName;
        }
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updateFullName();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        updateFullName();
    }
    
    public enum Role {
        SUPER_ADMIN,
        ADMIN,
        MANAGER,
        WORKER_DESIGN,    // Promenjeno iz WORKER_DESIGNER za konzistentnost
        WORKER_PRINT,
        WORKER_GENERAL
    }
}
