package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.email = :email")
    Optional<UserEntity> findByEmailForUpdate(@Param("email") String email);

    long countByRole(UserRole role);

    List<UserEntity> findByRole(UserRole role);
}
