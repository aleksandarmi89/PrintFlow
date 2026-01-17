package com.printflow.service;

import com.printflow.dto.UserDTO;
import com.printflow.dto.UserStatisticsDTO;
import com.printflow.dto.UserTaskStats;
import com.printflow.entity.User;
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

    // Lista radničkih rola za konzistentnost
    private static final List<Role> WORKER_ROLES = Arrays.asList(
        Role.WORKER_GENERAL, 
        Role.WORKER_DESIGN, 
        Role.WORKER_PRINT
    );

    public UserService(UserRepository userRepository, 
                       WorkOrderRepository workOrderRepository,
                       TaskRepository taskRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.taskRepository = taskRepository;
        this.passwordEncoder = passwordEncoder;
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
        user.setRole(userDTO.getRole() != null ? Role.valueOf(userDTO.getRole()) : Role.WORKER_GENERAL);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }

    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));

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
            user.setRole(Role.valueOf(userDTO.getRole()));
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
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
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
        return userRepository.findAll().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public Page<UserDTO> getAllUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return users.map(this::convertToDTO);
    }

    public Page<User> findAll(int page, int pageSize) {
        return userRepository.findAll(PageRequest.of(page, pageSize));
    }

    public List<UserDTO> getActiveUsers() {
        return userRepository.findByActiveTrue().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public Page<UserDTO> getActiveUsers(Pageable pageable) {
        Page<User> users = userRepository.findByActiveTrue(pageable);
        return users.map(this::convertToDTO);
    }

    public List<UserDTO> getUsersByRole(Role role) {
        return userRepository.findByRoleAndActiveTrue(role).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    // ISPRAVLJENE METODE ZA RADNIKE
    public List<UserDTO> getWorkers() {
        // Koristimo jednostavniju metodu
        return userRepository.findByRoleInAndActiveTrue(WORKER_ROLES).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    public List<UserDTO> getAvailableWorkers() {
        // Koristimo jednostavniju metodu
        try {
            return userRepository.findByRoleInAndActiveTrueAndAvailableTrue(WORKER_ROLES).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        } catch (Exception e) {
            // Fallback ako metoda ne postoji
            return userRepository.findByRoleInAndActiveTrue(WORKER_ROLES).stream()
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
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void activateUser(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public boolean changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return true;
    }

    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public List<UserDTO> searchUsers(String keyword) {
        try {
            return userRepository.searchUsers(keyword).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        } catch (Exception e) {
            // Alternativna implementacija
            return userRepository.findAll().stream()
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
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActiveTrue();
        long inactiveUsers = totalUsers - activeUsers;
        
        UserStatisticsDTO stats = new UserStatisticsDTO();
        stats.setTotalUsers(totalUsers);
        stats.setActiveUsers(activeUsers);
        stats.setInactiveUsers(inactiveUsers);
        
        // Sinhronizujte ove metode sa onima u UserStatisticsDTO
        try {
            stats.setAdminCount(userRepository.countByRoleAndActiveTrue(Role.ADMIN));
            stats.setManagerCount(userRepository.countByRoleAndActiveTrue(Role.MANAGER));
            stats.setWorkerDesignCount(userRepository.countByRoleAndActiveTrue(Role.WORKER_DESIGN));
            stats.setWorkerPrintCount(userRepository.countByRoleAndActiveTrue(Role.WORKER_PRINT));
            stats.setWorkerGeneralCount(userRepository.countByRoleAndActiveTrue(Role.WORKER_GENERAL));
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
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));

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
                long count = userRepository.countByRoleAndActiveTrue(role);
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