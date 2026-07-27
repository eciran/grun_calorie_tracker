package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.UserActivitySource;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "user_daily_activities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_daily_activity",
                columnNames = {"user_id", "activity_date"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDailyActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "first_source", nullable = false, length = 32)
    private UserActivitySource firstSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_source", nullable = false, length = 32)
    private UserActivitySource lastSource;
}
