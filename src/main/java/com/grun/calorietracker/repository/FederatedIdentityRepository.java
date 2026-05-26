package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FederatedIdentityEntity;
import com.grun.calorietracker.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FederatedIdentityRepository extends JpaRepository<FederatedIdentityEntity, Long> {
    Optional<FederatedIdentityEntity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    List<FederatedIdentityEntity> findByUserEmailOrderByCreatedAtAsc(String email);

    Optional<FederatedIdentityEntity> findByUserEmailAndProvider(String email, AuthProvider provider);

    long countByUserEmail(String email);
}
