package com.grun.calorietracker.entity;
import jakarta.persistence.*; import lombok.Data; import java.time.Instant;
@Entity @Table(name="meal_reminder_admin_test_sends") @Data
public class MealReminderAdminTestSendEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private UserEntity user;
 @Column(name="requested_by",nullable=false,length=255) private String requestedBy;
 @Column(name="requested_at",nullable=false) private Instant requestedAt;
}
