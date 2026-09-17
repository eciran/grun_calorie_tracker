package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "ai_credit_debits")
@Data
public class AiCreditDebitEntity {
    @Id private Long requestId;
    private Long userId;
    private Integer planAmount;
    private Integer addonAmount;
    private String allocationKey;
    private LocalDate planStart;
    private LocalDate planEnd;
    private LocalDate addonExpires;
    private Boolean refunded = false;
    private Integer refundedAmount = 0;
}
