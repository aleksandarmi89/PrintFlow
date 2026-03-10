package com.printflow.dto;

import com.printflow.entity.enums.Language;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class UserDTO {
    private Long id;
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;
    
    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must be less than 50 characters")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must be less than 50 characters")
    private String lastName;
    
    private String fullName;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email must be less than 100 characters")
    private String email;
    
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
    
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String confirmPassword;
    
    private Language language;
    private String languagePreference = "sr";
    
    @Size(max = 20, message = "Phone must be less than 20 characters")
    private String phone;
    
    private String role;
    private boolean active = true;
    private boolean available = true;
    private String department;
    private String position;
    private Long companyId;
    private String companyName;
    
    @Size(max = 1000, message = "Notes must be less than 1000 characters")
    private String notes;
    
    // Vremenske oznake
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLogin;
    
    // Statistika
    private Integer assignedOrdersCount;
    private Integer completedOrdersCount;
    private Integer assignedTasksCount;
    private Integer completedTasksCount;
    
    // Konstruktori
    public UserDTO() {}
    
    public UserDTO(Long id, String username, String firstName, String lastName, String fullName, 
                   String email, String password, String phone, String role, boolean active, 
                   Language language, String department) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.role = role;
        this.active = active;
        this.language = language;
        this.department = department;
    }
    
    // Kompletan konstruktor
    public UserDTO(Long id, String username, String firstName, String lastName, String fullName,
                   String email, String password, String phone, String role, boolean active,
                   Language language, String department, String position, String notes,
                   LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime lastLogin,
                   boolean available) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.role = role;
        this.active = active;
        this.language = language;
        this.department = department;
        this.position = position;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLogin = lastLogin;
        this.available = available;
    }
    
    // ==================== GETTERS I SETTERS ====================
    
    public Long getId() { 
        return id; 
    }
    
    public void setId(Long id) { 
        this.id = id; 
    }
    
    public String getUsername() { 
        return username; 
    }
    
    public void setUsername(String username) { 
        this.username = username; 
    }
    
    public String getFirstName() { 
        return firstName; 
    }
    
    public void setFirstName(String firstName) { 
        this.firstName = firstName;
        updateFullName();
    }
    
    public String getLastName() { 
        return lastName; 
    }
    
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
    
    public void setFullName(String fullName) { 
        this.fullName = fullName; 
    }
    
    public String getEmail() { 
        return email; 
    }
    
    public void setEmail(String email) { 
        this.email = email; 
    }
    
    public String getPassword() { 
        return password; 
    }
    
    public void setPassword(String password) { 
        this.password = password; 
    }
    
    public String getConfirmPassword() {
        return confirmPassword;
    }
    
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
    
    public Language getLanguage() {
        return language;
    }
    
    public void setLanguage(Language language) {
        this.language = language;
    }
    
    public String getLanguagePreference() {
        return languagePreference;
    }
    
    public void setLanguagePreference(String languagePreference) {
        this.languagePreference = languagePreference;
    }
    
    public String getPhone() { 
        return phone; 
    }
    
    public void setPhone(String phone) { 
        this.phone = phone; 
    }
    
    public String getRole() { 
        return role; 
    }
    
    public void setRole(String role) { 
        this.role = role; 
    }
    
    public boolean isActive() { 
        return active; 
    }
    
    public void setActive(boolean active) { 
        this.active = active; 
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    public void setAvailable(boolean available) {
        this.available = available;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public String getPosition() {
        return position;
    }
    
    public void setPosition(String position) {
        this.position = position;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public Integer getAssignedOrdersCount() {
        return assignedOrdersCount;
    }
    
    public void setAssignedOrdersCount(Integer assignedOrdersCount) {
        this.assignedOrdersCount = assignedOrdersCount;
    }
    
    public Integer getCompletedOrdersCount() {
        return completedOrdersCount;
    }
    
    public void setCompletedOrdersCount(Integer completedOrdersCount) {
        this.completedOrdersCount = completedOrdersCount;
    }
    
    public Integer getAssignedTasksCount() {
        return assignedTasksCount;
    }
    
    public void setAssignedTasksCount(Integer assignedTasksCount) {
        this.assignedTasksCount = assignedTasksCount;
    }
    
    public Integer getCompletedTasksCount() {
        return completedTasksCount;
    }
    
    public void setCompletedTasksCount(Integer completedTasksCount) {
        this.completedTasksCount = completedTasksCount;
    }
    
    // ==================== HELPER METODE ====================
    
    private void updateFullName() {
        if (firstName != null && lastName != null) {
            this.fullName = firstName + " " + lastName;
        } else if (firstName != null) {
            this.fullName = firstName;
        } else if (lastName != null) {
            this.fullName = lastName;
        }
    }
    
    // Validacija lozinke
    public boolean isPasswordValid() {
        if (password == null || password.length() < 6) {
            return false;
        }
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            return false;
        }
        return true;
    }
    
    // Provera da li je admin
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
    
    // Provera da li je manager
    public boolean isManager() {
        return "MANAGER".equalsIgnoreCase(role);
    }
    
    // Provera da li je radnik
    public boolean isWorker() {
        return role != null && (role.contains("WORKER") || role.contains("DESIGN") || role.contains("PRINT"));
    }
    
    // Provera da li je dostupan
    public boolean isAvailableForWork() {
        return active && available;
    }
    
    // Formatirani datum kreiranja
    public String getFormattedCreatedAt() {
        if (createdAt == null) return "N/A";
        return createdAt.toString();
    }
    
    // Formatirani datum poslednje prijave
    public String getFormattedLastLogin() {
        if (lastLogin == null) return "Nikad";
        return lastLogin.toString();
    }
    
    // Provera za kreiranje novog korisnika
    public boolean isValidForCreation() {
        return username != null && !username.trim().isEmpty() &&
               firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() &&
               password != null && password.length() >= 6;
    }
    
    // Provera za ažuriranje
    public boolean isValidForUpdate() {
        return id != null && 
               username != null && !username.trim().isEmpty() &&
               firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty() &&
               email != null && !email.trim().isEmpty();
    }
    
    // Računanje ukupne produktivnosti
    public Integer getTotalCompletedWork() {
        int total = 0;
        if (completedOrdersCount != null) total += completedOrdersCount;
        if (completedTasksCount != null) total += completedTasksCount;
        return total;
    }
    
    // Provera da li je prijavljen danas
    public boolean isLoggedInToday() {
        if (lastLogin == null) return false;
        LocalDateTime today = LocalDateTime.now();
        return lastLogin.toLocalDate().equals(today.toLocalDate());
    }
    
    @Override
    public String toString() {
        return "UserDTO{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", active=" + active +
                '}';
    }
}
