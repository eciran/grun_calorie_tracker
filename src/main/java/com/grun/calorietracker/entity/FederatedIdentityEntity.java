package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "federated_identities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_federated_identity_provider_subject",
                columnNames = {"provider", "provider_subject"}
        )
)
@Data
public class FederatedIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "provider_email")
    private String providerEmail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
