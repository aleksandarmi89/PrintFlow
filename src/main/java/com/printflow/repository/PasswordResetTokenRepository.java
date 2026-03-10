package com.printflow.repository;

import com.printflow.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    @Query("SELECT t.company.id FROM PasswordResetToken t WHERE t.token = :token")
    Optional<Long> findCompanyIdByToken(@Param("token") String token);

    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
