
package com.fincore.api.repository;

import com.fincore.api.entity.UserAccount;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    // Lock the account row during customer-profile creation.
    // Must be called inside an active database transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);
}
