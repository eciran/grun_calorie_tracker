package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.AccountLinkPurpose;
import com.grun.calorietracker.enums.AccountReauthenticationMethod;
import com.grun.calorietracker.enums.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_link_authorizations")
@Data
public class AccountLinkAuthorizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountLinkPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_provider", nullable = false, length = 32)
    private AuthProvider targetProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "reauthentication_method", nullable = false, length = 32)
    private AccountReauthenticationMethod reauthenticationMethod;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
