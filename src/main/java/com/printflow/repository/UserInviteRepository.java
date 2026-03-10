package com.printflow.repository;

import com.printflow.entity.UserInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserInviteRepository extends JpaRepository<UserInvite, Long> {
    Optional<UserInvite> findByToken(String token);
}
