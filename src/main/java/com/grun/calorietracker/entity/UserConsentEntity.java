package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.LegalConsentStatus;
import com.grun.calorietracker.enums.LegalConsentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_consents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 80)
    private LegalConsentType consentType;

    @Column(nullable = false, length = 80)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LegalConsentStatus status;

    @Column(length = 80)
    private String source;

    @Column(name = "ip_address", length = 128)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
