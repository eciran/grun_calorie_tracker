package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.BodyFatRequestDto;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserProfileDto> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            UserProfileDto profile = userService.getCurrentUser(userDetails.getUsername());
            return ResponseEntity.ok(profile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
}
    @PutMapping("/me")
    public ResponseEntity<UserProfileDto> updateCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid UserProfileDto updatedUserDto
    ) {
        String email = userDetails.getUsername();
        try {
            UserProfileDto updatedProfile = userService.updateCurrentUser(updatedUserDto, email);
            return ResponseEntity.ok(updatedProfile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/calculate-body-fat-or-bmi")
    public ResponseEntity<BodyFatResultDto> calculateBodyFatOrBmi(
            @RequestBody @Valid BodyFatRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {

        return userService.findByEmail(userDetails.getUsername())
                .map(user -> {
                    BodyFatResultDto result = userService.calculateBodyFatAndBmi(request, user);

                    user.setBodyFatPercentage(result.getBodyFat());
                    user.setBmi(result.getBmi());
                    UserProfileDto updatedUserDto = UserProfileDto.builder()
                            .bmi(result.getBmi())
                            .bodyFat(result.getBodyFat())
                            .build();

                    userService.updateCurrentUser(updatedUserDto, user.getEmail());

                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

}