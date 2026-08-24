package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "exercise_item_aliases")
@Data
public class ExerciseItemAliasEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_item_id", nullable = false)
    private ExerciseItemEntity exerciseItem;
    @Column(nullable = false, length = 160)
    private String alias;
    @Column(name = "normalized_alias", nullable = false, length = 160)
    private String normalizedAlias;
    @Column(nullable = false, length = 12)
    private String language = "und";
    @Column(name = "alias_type", nullable = false, length = 24)
    private String aliasType = "SYNONYM";
    @Column(nullable = false)
    private Boolean active = true;
}
