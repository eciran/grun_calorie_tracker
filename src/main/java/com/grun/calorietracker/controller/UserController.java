package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.service.UserService;
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
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            UserProfileDto profile = userService.getCurrentUser(userDetails.getUsername());
            return ResponseEntity.ok(profile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserProfileDto updatedUserDto
    ) {
        String email = userDetails.getUsername();
        try {
            UserProfileDto updatedProfile = userService.updateCurrentUser(updatedUserDto, email);
            return ResponseEntity.ok(updatedProfile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }

    @PostMapping("/calculate-body-fat-or-bmi")
    public ResponseEntity<?> calculateBodyFatOrBmi(
            @RequestBody BodyFatResultDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Optional<UserEntity> userOpt = userService.findByEmail(userDetails.getUsername());
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            BodyFatResultDto calculatedBodyFat = userService.calculateBodyFatAndBmi(request, user);
            user.setBodyFatPercentage(calculatedBodyFat.getBodyFat());
            user.setBmi(calculatedBodyFat.getBmi());
            UserProfileDto updatedUserDto = new UserProfileDto();
            updatedUserDto.setBodyFat(calculatedBodyFat.getBodyFat());
            updatedUserDto.setBmi(calculatedBodyFat.getBmi());
            userService.updateCurrentUser(updatedUserDto, userDetails.getUsername());
            return ResponseEntity.ok(calculatedBodyFat);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }
}