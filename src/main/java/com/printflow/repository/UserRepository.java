package com.printflow.repository;

import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Osnovne metode
    Optional<User> findByUsername(String username);
    Optional<User> findByIdAndCompany_Id(Long id, Long companyId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);
    List<User> findByActiveTrue();
    List<User> findByCompany_IdAndActiveTrue(Long companyId);
    long countByActiveTrue();
    long countByRoleAndActiveTrue(Role role);
    long countByRoleAndActiveTrueAndCompany_Id(Role role, Long companyId);
    long countByCreatedAtAfter(LocalDateTime date);
    long countByCompany_Id(Long companyId);
    long countByCompany_IdAndActiveTrue(Long companyId);
    
    // Page metode
    Page<User> findByActiveTrue(Pageable pageable);
    Page<User> findByCompany_Id(Long companyId, Pageable pageable);
    Page<User> findByCompany_IdAndActiveTrue(Long companyId, Pageable pageable);
    List<User> findByRoleAndActiveTrue(Role role);
    List<User> findByCompany_IdAndRoleAndActiveTrue(Long companyId, Role role);
    
    // Metode za radnike - ISPRAVLJENE
    @Query("SELECT u FROM User u WHERE u.role IN :workerRoles AND u.active = true")
    List<User> findWorkers(@Param("workerRoles") List<Role> workerRoles);
    
    @Query("SELECT u FROM User u WHERE u.role IN :workerRoles AND u.active = true AND u.available = true")
    List<User> findAvailableWorkers(@Param("workerRoles") List<Role> workerRoles);

    @Query("SELECT u FROM User u WHERE u.company.id = :companyId AND u.role IN :workerRoles AND u.active = true")
    List<User> findWorkersByCompany(@Param("companyId") Long companyId, @Param("workerRoles") List<Role> workerRoles);

    @Query("SELECT u FROM User u WHERE u.company.id = :companyId AND u.role IN :workerRoles AND u.active = true AND u.available = true")
    List<User> findAvailableWorkersByCompany(@Param("companyId") Long companyId, @Param("workerRoles") List<Role> workerRoles);
    
    // Alternativne metode bez @Query za jednostavnost
    List<User> findByRoleInAndActiveTrue(List<Role> roles);
    List<User> findByRoleInAndActiveTrueAndAvailableTrue(List<Role> roles);
    List<User> findByCompany_IdAndRoleInAndActiveTrue(Long companyId, List<Role> roles);
    List<User> findByCompany_IdAndRoleInAndActiveTrueAndAvailableTrue(Long companyId, List<Role> roles);
    List<User> findByCompany_IdAndUsernameInAndActiveTrue(Long companyId, List<String> usernames);
    List<User> findByUsernameInAndActiveTrue(List<String> usernames);
    List<User> findByCompany_IdAndIdIn(Long companyId, List<Long> ids);
    
    // Search metode - SIMPLIFIKOVANE
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> searchUsers(@Param("keyword") String keyword);

    @Query("SELECT u FROM User u WHERE u.company.id = :companyId AND (" +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<User> searchUsersByCompany(@Param("companyId") Long companyId, @Param("keyword") String keyword);

    @Query("SELECT u.company.id FROM User u WHERE u.id = :userId")
    Optional<Long> findCompanyIdByUserId(@Param("userId") Long userId);

    @Query("SELECT u.company.id FROM User u WHERE u.username = :username")
    Optional<Long> findCompanyIdByUsername(@Param("username") String username);
    
    // Metode za sortiranje
    Page<User> findAllByOrderByLastLoginDesc(Pageable pageable);

    List<User> findByCompanyIsNull();
	
}
