package com.printflow.repository;

import com.printflow.entity.BannedIp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BannedIpRepository extends JpaRepository<BannedIp, Long> {
    Optional<BannedIp> findByIp(String ip);
    boolean existsByIpAndActiveTrue(String ip);
    List<BannedIp> findByActiveTrueOrderByCreatedAtDesc();
}
