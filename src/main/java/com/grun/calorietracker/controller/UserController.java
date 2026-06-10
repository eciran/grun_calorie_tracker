package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.BodyFatRequestDto;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.service.UserAvatarService;
import com.grun.calorietracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Authenticated user's own profile and body composition operations.")
public class UserController {

    private final UserService userService;
    private final UserAvatarService userAvatarService;

    public UserController(UserService userService, UserAvatarService userAvatarService) {
        this.userService = userService;
        this.userAvatarService = userAvatarService;
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current user profile",
            description = "Returns the profile of the authenticated user identified by the JWT token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<UserProfileDto> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        try {
            UserProfileDto profile = userService.getCurrentUser(userDetails.getUsername());
            return ResponseEntity.ok(profile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload current user avatar",
            description = "Stores a JPEG, PNG, or WebP avatar image for the authenticated user and returns the updated profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avatar uploaded and profile returned."),
            @ApiResponse(responseCode = "400", description = "File is missing, too large, or has an unsupported content type."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<UserProfileDto> uploadAvatar(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("file") MultipartFile file
    ) {
        UserProfileDto updatedProfile = userAvatarService.uploadAvatar(userDetails.getUsername(), file);
        return ResponseEntity.ok(updatedProfile);
    }

    @DeleteMapping("/me/avatar")
    @Operation(
            summary = "Delete current user avatar",
            description = "Deletes the authenticated user's stored avatar and returns the updated profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avatar deleted and profile returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<UserProfileDto> deleteAvatar(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserProfileDto updatedProfile = userAvatarService.deleteAvatar(userDetails.getUsername());
        return ResponseEntity.ok(updatedProfile);
    }

    @GetMapping("/avatars/{filename}")
    @Operation(
            summary = "Get uploaded avatar image",
            description = "Returns a public avatar image by storage filename."
    )
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        Resource resource = userAvatarService.loadAvatar(filename);
        return ResponseEntity.ok()
                .contentType(mediaType(filename))
                .body(resource);
    }

    @PutMapping("/me")
    @Operation(
            summary = "Update current user profile",
            description = "Updates profile information for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<UserProfileDto> updateCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
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
    @Operation(
            summary = "Calculate body fat and BMI",
            description = "Calculates body fat percentage and BMI for the authenticated user, then stores the latest values on the user profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Body composition values calculated and stored."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<BodyFatResultDto> calculateBodyFatOrBmi(
            @RequestBody @Valid BodyFatRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

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

    private MediaType mediaType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

}
