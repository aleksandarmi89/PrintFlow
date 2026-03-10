package com.printflow.service;

import com.printflow.dto.UserDTO;
import com.printflow.dto.UserStatisticsDTO;
import com.printflow.dto.UserTaskStats;
import com.printflow.entity.User;
import com.printflow.entity.Company;
import com.printflow.entity.User.Role;
import com.printflow.entity.enums.OrderStatus;
import com.printflow.entity.enums.TaskStatus;
import com.printflow.repository.UserRepository;
import com.printflow.repository.WorkOrderRepository;
import com.printflow.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContextService tenantContextService;
    private final com.printflow.repository.CompanyRepository companyRepository;
    private final PlanLimitService planLimitService;
    private final BillingAccessService billingAccessService;

    // Lista radničkih rola za konzistentnost
    private static final List<Role> WORKER_ROLES = Arrays.asList(
        Role.WORKER_GENERAL, 
        Role.WORKER_DESIGN, 
        Role.WORKER_PRINT
    );

    public UserService(UserRepository userRepository, 
                       WorkOrderRepository workOrderRepository,
                       TaskRepository taskRepository,
                       PasswordEncoder passwordEncoder,
                       TenantContextService tenantContextService,
                       com.printflow.repository.CompanyRepository companyRepository,
                       PlanLimitService planLimitService,
                       BillingAccessService billingAccessService) {
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.taskRepository = taskRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantContextService = tenantContextService;
        this.companyRepository = companyRepository;
        this.planLimitService = planLimitService;
        this.billingAccessService = billingAccessService;
    }

    // ==================== OSNOVNE METODE ====================
    
    public UserDTO createUser(UserDTO userDTO) {
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userDTO.getEmail() != null && !userDTO.getEmail().isEmpty() &&
            userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setFullName(userDTO.getFullName());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        Role requestedRole = userDTO.getRole() != null ? Role.valueOf(userDTO.getRole()) : Role.WORKER_GENERAL;
        if (!tenantContextService.isSuperAdmin() && requestedRole == Role.SUPER_ADMIN) {
            throw new RuntimeException("Not allowed to assign SUPER_ADMIN role");
        }
        user.setRole(requestedRole);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        if (tenantContextService.isSuperAdmin()) {
            if (userDTO.getCompanyId() == null) {
                throw new RuntimeException("Company is required for new user");
            }
            Company company = companyRepository.findById(userDTO.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));
            billingAccessService.assertBillingActiveForPremiumAction(company.getId());
            planLimitService.assertUserLimit(company);
            user.setCompany(company);
        } else {
            Company company = tenantContextService.getCurrentCompany();
            billingAccessService.assertBillingActiveForPremiumAction(company.getId());
            planLimitService.assertUserLimit(company);
            user.setCompany(company);
        }

        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }

    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findByIdAndCompany_Id(id, tenantContextService.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!tenantContextService.isSuperAdmin() &&
            (user.getCompany() == null || !user.getCompany().getId().equals(tenantContextService.requireCompanyId()))) {
            throw new RuntimeException("User not found");
        }

        if (!user.getUsername().equals(userDTO.getUsername()) &&
            userRepository.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        user.setUsername(userDTO.getUsername());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setFullName(userDTO.getFullName());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        
        if (userDTO.getRole() != null) {
            Role requestedRole = Role.valueOf(userDTO.getRole());
            if (!tenantContextService.isSuperAdmin() && requestedRole == Role.SUPER_ADMIN) {
                throw new RuntimeException("Not allowed to assign SUPER_ADMIN role");
            }
            user.setRole(requestedRole);
        }

        if (tenantContextService.isSuperAdmin() && userDTO.getCompanyId() != null) {
            user.setCompany(companyRepository.findById(userDTO.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found")));
        }
        
        user.setActive(userDTO.isActive());
        user.setUpdatedAt(LocalDateTime.now());

        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }

        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findByIdAndCompany_Id(id, tenantContextService.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findByIdAndCompany_Id(id, tenantContextService.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!tenantContextService.isSuperAdmin() &&
            (user.getCompany() == null || !user.getCompany().getId().equals(tenantContextService.requireCompanyId()))) {
            throw new RuntimeException("User not found");
        }
        return convertToDTO(user);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    public List<UserDTO> getAllUsers() {
        if (tenantContextService.isSuperAdmin()) {
            return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        Long companyId = tenantContextService.requireCompanyId();
        return userRepository.findByCompany_Id(companyId, Pageable.unpaged()).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public Page<UserDTO> getAllUsers(Pageable pageable) {
        if (tenantContextService.isSuperAdmin()) {
            Page<User> users = userRepository.findAll(pageable);
            return users.map(this::convertToDTO);
        }
        Long companyId = tenantContextService.requireCompanyId();
        Page<User> users = userRepository.findByCompany_Id(companyId, pageable);
        return users.map(this::convertToDTO);
    }

    public Page<User> findAll(int page, int pageSize) {
        if (tenantContextService.isSuperAdmin()) {
            return userRepository.findAll(PageRequest.of(page, pageSize));
        }
        Long companyId = tenantContextService.requireCompanyId();
        return userRepository.findByCompany_Id(companyId, PageRequest.of(page, pageSize));
    }

    public List<UserDTO> getActiveUsers() {
        if (tenantContextService.isSuperAdmin()) {
            return userRepository.findByActiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        Long companyId = tenantContextService.requireCompanyId();
        return userRepository.findByCompany_IdAndActiveTrue(companyId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public Page<UserDTO> getActiveUsers(Pageable pageable) {
        if (tenantContextService.isSuperAdmin()) {
            Page<User> users = userRepository.findByActiveTrue(pageable);
            return users.map(this::convertToDTO);
        }
        Long companyId = tenantContextService.requireCompanyId();
        Page<User> users = userRepository.findByCompany_IdAndActiveTrue(companyId, pageable);
        return users.map(this::convertToDTO);
    }

    public List<UserDTO> getUsersByRole(Role role) {
        if (tenantContextService.isSuperAdmin()) {
            return userRepository.findByRoleAndActiveTrue(role).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
        Long companyId = tenantContextService.requireCompanyId();
        return userRepository.findByCompany_IdAndRoleAndActiveTrue(companyId, role).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    // ISPRAVLJENE METODE ZA RADNIKE
    public List<UserDTO> getWorkers() {
        // Koristimo jednostavniju metodu
        List<User> workers;
        if (tenantContextService.isSuperAdmin()) {
            workers = userRepository.findByRoleInAndActiveTrue(WORKER_ROLES);
            if (workers.isEmpty()) {
                workers = userRepository.findByActiveTrue().stream()
                    .filter(user -> user.getRole() != Role.SUPER_ADMIN)
                    .collect(Collectors.toList());
            }
        } else {
            Long companyId = tenantContextService.requireCompanyId();
            workers = userRepository.findByCompany_IdAndRoleInAndActiveTrue(companyId, WORKER_ROLES);
            if (workers.isEmpty()) {
                workers = userRepository.findByCompany_IdAndActiveTrue(companyId).stream()
                    .filter(user -> user.getRole() != Role.SUPER_ADMIN)
                    .collect(Collectors.toList());
            }
        }
        return workers.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public List<UserDTO> getAvailableWorkers() {
        // Koristimo jednostavniju metodu
        try {
            if (tenantContextService.isSuperAdmin()) {
                return userRepository.findByRoleInAndActiveTrueAndAvailableTrue(WORKER_ROLES).stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            }
            Long companyId = tenantContextService.requireCompanyId();
            return userRepository.findByCompany_IdAndRoleInAndActiveTrueAndAvailableTrue(companyId, WORKER_ROLES).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        } catch (Exception e) {
            // Fallback ako metoda ne postoji
            if (tenantContextService.isSuperAdmin()) {
                return userRepository.findByRoleInAndActiveTrue(WORKER_ROLES).stream()
                    .filter(User::isAvailable)
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            }
            Long companyId = tenantContextService.requireCompanyId();
            return userRepository.findByCompany_IdAndRoleInAndActiveTrue(companyId, WORKER_ROLES).stream()
                .filter(User::isAvailable)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
    }

    public void updateLastLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    // ==================== POMOĆNE METODE ====================

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public long countAllUsers() {
        return userRepository.count();
    }

    public long countActiveUsers() {
        return userRepository.countByActiveTrue();
    }

    public void deactivateUser(Long id) {
        User user = userRepository.findByIdAndCompany_Id(id, tenantContextService.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!tenantContextService.isSuperAdmin() &&
            (user.getCompany() == null || !user.getCompany().getId().equals(tenantContextService.requireCompanyId()))) {
            throw new RuntimeException("User not found");
        }
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void activateUser(Long id) {
        User user = userRepository.findByIdAndCompany_Id(id, tenantContextService.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!tenantContextService.isSuperAdmin() &&
            (user.getCompany() == null || !user.getCompany().getId().equals(tenantContextService.requireCompanyId()))) {
            throw new RuntimeException("User not found");
        }
        user.setActive(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void updateProfile(Long userId, String firstName, String lastName, String email, String phone,
                              String department, String position, String notes) {
        User user = userRepository.findByIdAndCompany_Id(userId, tenantContextService.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!tenantContextService.isSuperAdmin() &&
            (user.getCompany() == null || !user.getCompany().getId().equals(tenantContextService.requireCompanyId()))) {
            throw new RuntimeException("User not found");
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setDepartment(department);
        user.setPosition(position);
        user.setNotes(notes);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public boolean changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findByIdAndCompany_Id(userId, tenantContextService.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!tenantContextService.isSuperAdmin() &&
            (user.getCompany() == null || !user.getCompany().getId().equals(tenantContextService.requireCompanyId()))) {
            throw new RuntimeException("User not found");
        }
        
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return true;
    }

    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findByIdAndCompany_Id(userId, tenantContextService.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!tenantContextService.isSuperAdmin() &&
            (user.getCompany() == null || !user.getCompany().getId().equals(tenantContextService.requireCompanyId()))) {
            throw new RuntimeException("User not found");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public List<UserDTO> searchUsers(String keyword) {
        try {
            if (tenantContextService.isSuperAdmin()) {
                return userRepository.searchUsers(keyword).stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            }
            Long companyId = tenantContextService.requireCompanyId();
            return userRepository.searchUsersByCompany(companyId, keyword).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        } catch (Exception e) {
            // Alternativna implementacija
            if (tenantContextService.isSuperAdmin()) {
                return userRepository.findAll().stream()
                    .filter(user -> 
                        (user.getUsername() != null && user.getUsername().toLowerCase().contains(keyword.toLowerCase())) ||
                        (user.getFullName() != null && user.getFullName().toLowerCase().contains(keyword.toLowerCase())) ||
                        (user.getEmail() != null && user.getEmail().toLowerCase().contains(keyword.toLowerCase()))
                    )
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            }
            Long companyId = tenantContextService.requireCompanyId();
            return userRepository.findByCompany_Id(companyId, Pageable.unpaged()).stream()
                .filter(user -> 
                    (user.getUsername() != null && user.getUsername().toLowerCase().contains(keyword.toLowerCase())) ||
                    (user.getFullName() != null && user.getFullName().toLowerCase().contains(keyword.toLowerCase())) ||
                    (user.getEmail() != null && user.getEmail().toLowerCase().contains(keyword.toLowerCase()))
                )
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        }
    }

    // ==================== STATISTIKE ====================

    public UserStatisticsDTO getUserStatistics() {
        Long companyId = tenantContextService.isSuperAdmin() ? null : tenantContextService.requireCompanyId();
        long totalUsers = tenantContextService.isSuperAdmin()
            ? userRepository.count()
            : userRepository.findByCompany_Id(companyId, Pageable.unpaged()).getTotalElements();
        long activeUsers = tenantContextService.isSuperAdmin()
            ? userRepository.countByActiveTrue()
            : userRepository.findByCompany_IdAndActiveTrue(companyId, Pageable.unpaged()).getTotalElements();
        long inactiveUsers = totalUsers - activeUsers;
        
        UserStatisticsDTO stats = new UserStatisticsDTO();
        stats.setTotalUsers(totalUsers);
        stats.setActiveUsers(activeUsers);
        stats.setInactiveUsers(inactiveUsers);
        
        // Sinhronizujte ove metode sa onima u UserStatisticsDTO
        try {
            if (tenantContextService.isSuperAdmin()) {
                stats.setAdminCount(userRepository.countByRoleAndActiveTrue(Role.ADMIN));
                stats.setManagerCount(userRepository.countByRoleAndActiveTrue(Role.MANAGER));
                stats.setWorkerDesignCount(userRepository.countByRoleAndActiveTrue(Role.WORKER_DESIGN));
                stats.setWorkerPrintCount(userRepository.countByRoleAndActiveTrue(Role.WORKER_PRINT));
                stats.setWorkerGeneralCount(userRepository.countByRoleAndActiveTrue(Role.WORKER_GENERAL));
            } else {
                stats.setAdminCount(userRepository.countByRoleAndActiveTrueAndCompany_Id(Role.ADMIN, companyId));
                stats.setManagerCount(userRepository.countByRoleAndActiveTrueAndCompany_Id(Role.MANAGER, companyId));
                stats.setWorkerDesignCount(userRepository.countByRoleAndActiveTrueAndCompany_Id(Role.WORKER_DESIGN, companyId));
                stats.setWorkerPrintCount(userRepository.countByRoleAndActiveTrueAndCompany_Id(Role.WORKER_PRINT, companyId));
                stats.setWorkerGeneralCount(userRepository.countByRoleAndActiveTrueAndCompany_Id(Role.WORKER_GENERAL, companyId));
            }
        } catch (Exception e) {
            // Postavite podrazumevane vrednosti
            stats.setAdminCount(0);
            stats.setManagerCount(0);
            stats.setWorkerDesignCount(0);
            stats.setWorkerPrintCount(0);
            stats.setWorkerGeneralCount(0);
        }
        
        return stats;
    }

    public UserTaskStats getUserTaskStats(Long id) {
        User user = userRepository.findByIdAndCompany_Id(id, tenantContextService.requireCompanyId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!tenantContextService.isSuperAdmin() &&
            (user.getCompany() == null || !user.getCompany().getId().equals(tenantContextService.requireCompanyId()))) {
            throw new RuntimeException("User not found");
        }

        UserTaskStats stats = new UserTaskStats();
        stats.setLastLogin(user.getLastLogin());
        
        // Postavite podrazumevane vrednosti
        stats.setAssignedOrders(0);
        stats.setCompletedOrders(0);
        stats.setInProgressOrders(0);
        stats.setAssignedTasks(0);
        stats.setCompletedTasks(0);
        stats.setInProgressTasks(0);
        
        return stats;
    }

    // ==================== KONVERZIJA ====================

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole() != null ? user.getRole().name() : null);
        dto.setActive(user.isActive());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setLastLogin(user.getLastLogin());
        dto.setAvailable(user.isAvailable());
        if (user.getCompany() != null) {
            dto.setCompanyId(user.getCompany().getId());
            dto.setCompanyName(user.getCompany().getName());
        }
        
        if (user.getDepartment() != null) {
            dto.setDepartment(user.getDepartment());
        }
        
        return dto;
    }

    // ==================== DODATNE METODE ====================

    public Map<Role, Long> getUsersCountByRole() {
        Map<Role, Long> roleCounts = new HashMap<>();
        
        for (Role role : Role.values()) {
            try {
                long count = tenantContextService.isSuperAdmin()
                    ? userRepository.countByRoleAndActiveTrue(role)
                    : userRepository.countByRoleAndActiveTrueAndCompany_Id(role, tenantContextService.requireCompanyId());
                roleCounts.put(role, count);
            } catch (Exception e) {
                roleCounts.put(role, 0L);
            }
        }
        
        return roleCounts;
    }

    public List<UserDTO> getRecentlyActiveUsers(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return userRepository.findAllByOrderByLastLoginDesc(pageable).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        } catch (Exception e) {
            return getActiveUsers().stream()
                .limit(limit)
                .collect(Collectors.toList());
        }
    }
}
