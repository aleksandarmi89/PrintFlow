package com.printflow.repository;

import com.printflow.entity.WhitelistedIp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhitelistedIpRepository extends JpaRepository<WhitelistedIp, Long> {
    Optional<WhitelistedIp> findByIp(String ip);
    boolean existsByIpAndActiveTrue(String ip);
    List<WhitelistedIp> findByActiveTrueOrderByCreatedAtDesc();
}
