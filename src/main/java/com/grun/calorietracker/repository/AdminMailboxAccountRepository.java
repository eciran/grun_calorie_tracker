package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdminMailboxAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AdminMailboxAccountRepository extends JpaRepository<AdminMailboxAccountEntity,Long> {
    Optional<AdminMailboxAccountEntity> findByEmailAddressIgnoreCase(String emailAddress);
    List<AdminMailboxAccountEntity> findAllByOrderByEmailAddressAsc();
}
