package com.printflow.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserStatisticsDTO {
    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    
    // Broj korisnika po rolama
    private long adminCount;
    private long managerCount;
    private long workerDesignCount;
    private long workerPrintCount;
    private long workerGeneralCount;
    
    // Statistika po vremenu
    private long newUsersThisMonth;
    private long newUsersThisWeek;
    private long newUsersToday;
    
    // Vremenski podaci
    private LocalDateTime lastUserCreatedAt;
    private LocalDateTime lastLoginTime;
    
    // Aktivnost
    private long usersLoggedInToday;
    private long usersLoggedInThisWeek;
    
    // Proseci
    private double averageUsersPerDay;
    private double averageUsersPerWeek;
    
    // Default constructor
    public UserStatisticsDTO() {}
    
    // Constructor with basic parameters
    public UserStatisticsDTO(long totalUsers, long activeUsers, long inactiveUsers) {
        this.totalUsers = totalUsers;
        this.activeUsers = activeUsers;
        this.inactiveUsers = inactiveUsers;
    }
    
    // Constructor with all parameters
    public UserStatisticsDTO(long totalUsers, long activeUsers, long inactiveUsers,
                            long adminCount, long managerCount, long workerDesignCount,
                            long workerPrintCount, long workerGeneralCount, 
                            long newUsersThisMonth) {
        this.totalUsers = totalUsers;
        this.activeUsers = activeUsers;
        this.inactiveUsers = inactiveUsers;
        this.adminCount = adminCount;
        this.managerCount = managerCount;
        this.workerDesignCount = workerDesignCount;
        this.workerPrintCount = workerPrintCount;
        this.workerGeneralCount = workerGeneralCount;
        this.newUsersThisMonth = newUsersThisMonth;
    }

    // Getters and setters (Lombok @Data bi trebao generisati ove, ali dodajemo za sigurnost)
    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(long activeUsers) {
        this.activeUsers = activeUsers;
    }

    public long getInactiveUsers() {
        return inactiveUsers;
    }

    public void setInactiveUsers(long inactiveUsers) {
        this.inactiveUsers = inactiveUsers;
    }

    public long getAdminCount() {
        return adminCount;
    }

    public void setAdminCount(long adminCount) {
        this.adminCount = adminCount;
    }

    public long getManagerCount() {
        return managerCount;
    }

    public void setManagerCount(long managerCount) {
        this.managerCount = managerCount;
    }

    public long getWorkerDesignCount() {
        return workerDesignCount;
    }

    public void setWorkerDesignCount(long workerDesignCount) {
        this.workerDesignCount = workerDesignCount;
    }

    public long getWorkerPrintCount() {
        return workerPrintCount;
    }

    public void setWorkerPrintCount(long workerPrintCount) {
        this.workerPrintCount = workerPrintCount;
    }

    public long getWorkerGeneralCount() {
        return workerGeneralCount;
    }

    public void setWorkerGeneralCount(long workerGeneralCount) {
        this.workerGeneralCount = workerGeneralCount;
    }

    public long getNewUsersThisMonth() {
        return newUsersThisMonth;
    }

    public void setNewUsersThisMonth(long newUsersThisMonth) {
        this.newUsersThisMonth = newUsersThisMonth;
    }
    
    // Helper metode za stare gettere/settere (kompatibilnost)
    public long getAdminUsers() {
        return adminCount;
    }

    public void setAdminUsers(long adminUsers) {
        this.adminCount = adminUsers;
    }

    public long getWorkerUsers() {
        return workerDesignCount + workerPrintCount + workerGeneralCount;
    }

    public void setWorkerUsers(long workerUsers) {
        // Ova metoda ne radi ništa jer workerUsers se računa
        // Možete je ostaviti praznu ili postaviti proporcionalno
    }
    
    // Dodatne getters i setters za nova polja
    public long getNewUsersThisWeek() {
        return newUsersThisWeek;
    }

    public void setNewUsersThisWeek(long newUsersThisWeek) {
        this.newUsersThisWeek = newUsersThisWeek;
    }

    public long getNewUsersToday() {
        return newUsersToday;
    }

    public void setNewUsersToday(long newUsersToday) {
        this.newUsersToday = newUsersToday;
    }

    public LocalDateTime getLastUserCreatedAt() {
        return lastUserCreatedAt;
    }

    public void setLastUserCreatedAt(LocalDateTime lastUserCreatedAt) {
        this.lastUserCreatedAt = lastUserCreatedAt;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public long getUsersLoggedInToday() {
        return usersLoggedInToday;
    }

    public void setUsersLoggedInToday(long usersLoggedInToday) {
        this.usersLoggedInToday = usersLoggedInToday;
    }

    public long getUsersLoggedInThisWeek() {
        return usersLoggedInThisWeek;
    }

    public void setUsersLoggedInThisWeek(long usersLoggedInThisWeek) {
        this.usersLoggedInThisWeek = usersLoggedInThisWeek;
    }

    public double getAverageUsersPerDay() {
        return averageUsersPerDay;
    }

    public void setAverageUsersPerDay(double averageUsersPerDay) {
        this.averageUsersPerDay = averageUsersPerDay;
    }

    public double getAverageUsersPerWeek() {
        return averageUsersPerWeek;
    }

    public void setAverageUsersPerWeek(double averageUsersPerWeek) {
        this.averageUsersPerWeek = averageUsersPerWeek;
    }
    
    // Metode za izračunavanje
    public double getActivePercentage() {
        if (totalUsers == 0) return 0.0;
        return (double) activeUsers / totalUsers * 100;
    }
    
    public double getAdminPercentage() {
        if (totalUsers == 0) return 0.0;
        return (double) adminCount / totalUsers * 100;
    }
    
    public double getWorkerPercentage() {
        if (totalUsers == 0) return 0.0;
        return (double) getWorkerUsers() / totalUsers * 100;
    }
    
    public long getTotalWorkers() {
        return workerDesignCount + workerPrintCount + workerGeneralCount;
    }
    
    public String getActivePercentageFormatted() {
        return String.format("%.1f%%", getActivePercentage());
    }
    
    public String getAdminPercentageFormatted() {
        return String.format("%.1f%%", getAdminPercentage());
    }
    
    public String getWorkerPercentageFormatted() {
        return String.format("%.1f%%", getWorkerPercentage());
    }
    
    // Metoda za dobijanje statistike po rolama kao mapa
    public java.util.Map<String, Long> getRoleStatistics() {
        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("ADMIN", adminCount);
        stats.put("MANAGER", managerCount);
        stats.put("WORKER_DESIGN", workerDesignCount);
        stats.put("WORKER_PRINT", workerPrintCount);
        stats.put("WORKER_GENERAL", workerGeneralCount);
        return stats;
    }
    
    // Metoda za dobijanje statistike po statusu
    public java.util.Map<String, Long> getStatusStatistics() {
        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("ACTIVE", activeUsers);
        stats.put("INACTIVE", inactiveUsers);
        return stats;
    }
    
    @Override
    public String toString() {
        return "UserStatisticsDTO{" +
                "totalUsers=" + totalUsers +
                ", activeUsers=" + activeUsers +
                ", inactiveUsers=" + inactiveUsers +
                ", adminCount=" + adminCount +
                ", managerCount=" + managerCount +
                ", workerDesignCount=" + workerDesignCount +
                ", workerPrintCount=" + workerPrintCount +
                ", workerGeneralCount=" + workerGeneralCount +
                ", newUsersThisMonth=" + newUsersThisMonth +
                '}';
    }
}