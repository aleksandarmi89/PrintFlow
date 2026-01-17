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
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByActiveTrue();
    long countByActiveTrue();
    long countByRoleAndActiveTrue(Role role);
    long countByCreatedAtAfter(LocalDateTime date);
    
    // Page metode
    Page<User> findByActiveTrue(Pageable pageable);
    List<User> findByRoleAndActiveTrue(Role role);
    
    // Metode za radnike - ISPRAVLJENE
    @Query("SELECT u FROM User u WHERE u.role IN :workerRoles AND u.active = true")
    List<User> findWorkers(@Param("workerRoles") List<Role> workerRoles);
    
    @Query("SELECT u FROM User u WHERE u.role IN :workerRoles AND u.active = true AND u.available = true")
    List<User> findAvailableWorkers(@Param("workerRoles") List<Role> workerRoles);
    
    // Alternativne metode bez @Query za jednostavnost
    List<User> findByRoleInAndActiveTrue(List<Role> roles);
    List<User> findByRoleInAndActiveTrueAndAvailableTrue(List<Role> roles);
    
    // Search metode - SIMPLIFIKOVANE
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> searchUsers(@Param("keyword") String keyword);
    
    // Metode za sortiranje
    Page<User> findAllByOrderByLastLoginDesc(Pageable pageable);
	
}