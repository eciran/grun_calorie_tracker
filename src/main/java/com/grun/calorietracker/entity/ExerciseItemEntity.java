package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "exercise_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String metCode;
    private Double caloriesPerMinute;
    private String description;
    private String iconUrl;
}
