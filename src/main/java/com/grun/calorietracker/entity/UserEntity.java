package com.grun.calorietracker.entity;


import com.grun.calorietracker.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String email;

    private String password;

    private LocalDate birthdate;

    private String gender;

    private Double height;

    private Double weight;

    @Enumerated(EnumType.STRING)
    private Role role;

}