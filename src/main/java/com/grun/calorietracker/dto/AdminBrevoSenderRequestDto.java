package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminBrevoSenderRequestDto {
    @NotBlank(message = "Sender name is required.")
    private String name;

    @NotBlank(message = "Sender email is required.")
    @Email(message = "Sender email must be valid.")
    private String email;
}
