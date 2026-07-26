package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.BodyFatRequestDto;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.MyProfileDto;
import com.grun.calorietracker.dto.MyProfileUpdateRequestDto;
import com.grun.calorietracker.dto.ProfileBodyDto;
import com.grun.calorietracker.dto.ProfileBodyUpdateRequestDto;
import com.grun.calorietracker.dto.ProfilePreferencesDto;
import com.grun.calorietracker.dto.ProfilePreferencesUpdateRequestDto;
import com.grun.calorietracker.dto.ProfileSecurityDto;
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
    @Operation(summary = "Get my profile", description = "Returns the authenticated user's profile without admin-only account fields.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<MyProfileDto> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(userService.getMyProfile(userDetails.getUsername()));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping("/me")
    @Operation(summary = "Update my basic profile", description = "Updates only safe basic profile fields. Body metrics and preferences use dedicated endpoints.")
    public ResponseEntity<MyProfileDto> updateCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid MyProfileUpdateRequestDto request) {
        return ResponseEntity.ok(userService.updateMyProfile(request, userDetails.getUsername()));
    }

    @GetMapping("/me/body")
    @Operation(summary = "Get my body metrics")
    public ProfileBodyDto getProfileBody(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return userService.getProfileBody(userDetails.getUsername());
    }

    @PatchMapping("/me/body")
    @Operation(summary = "Update my body metrics", description = "Partially updates body metrics and reports whether calorie goals should be recalculated.")
    public MyProfileDto updateProfileBody(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid ProfileBodyUpdateRequestDto request) {
        return userService.updateProfileBody(request, userDetails.getUsername());
    }

    @GetMapping("/me/preferences")
    @Operation(summary = "Get my regional and display preferences")
    public ProfilePreferencesDto getProfilePreferences(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return userService.getProfilePreferences(userDetails.getUsername());
    }

    @PatchMapping("/me/preferences")
    @Operation(summary = "Update my regional and display preferences")
    public MyProfileDto updateProfilePreferences(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid ProfilePreferencesUpdateRequestDto request) {
        return userService.updateProfilePreferences(request, userDetails.getUsername());
    }

    @GetMapping("/me/security")
    @Operation(summary = "Get my security state", description = "Returns user-visible verification and password state without admin enforcement fields.")
    public ProfileSecurityDto getProfileSecurity(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return userService.getProfileSecurity(userDetails.getUsername());
    }

    @Deprecated(forRemoval = false)
    @GetMapping("/me/legacy")
    @Operation(summary = "Get legacy profile contract", deprecated = true,
            description = "Temporary compatibility endpoint. Clients must migrate to the split /me contracts.")
    public UserProfileDto getLegacyProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload current user avatar")
    public ResponseEntity<MyProfileDto> uploadAvatar(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("file") MultipartFile file) {
        userAvatarService.uploadAvatar(userDetails.getUsername(), file);
        return ResponseEntity.ok(userService.getMyProfile(userDetails.getUsername()));
    }

    @DeleteMapping("/me/avatar")
    @Operation(summary = "Delete current user avatar")
    public ResponseEntity<MyProfileDto> deleteAvatar(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        userAvatarService.deleteAvatar(userDetails.getUsername());
        return ResponseEntity.ok(userService.getMyProfile(userDetails.getUsername()));
    }

    @GetMapping("/avatars/{filename}")
    @Operation(summary = "Get uploaded avatar image")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        Resource resource = userAvatarService.loadAvatar(filename);
        return ResponseEntity.ok().contentType(mediaType(filename)).body(resource);
    }

    @PostMapping("/calculate-body-fat-or-bmi")
    @Operation(summary = "Calculate body fat and BMI")
    public ResponseEntity<BodyFatResultDto> calculateBodyFatOrBmi(
            @RequestBody @Valid BodyFatRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return userService.findByEmail(userDetails.getUsername())
                .map(user -> {
                    BodyFatResultDto result = userService.calculateBodyFatAndBmi(request, user);
                    UserProfileDto update = UserProfileDto.builder()
                            .bmi(result.getBmi())
                            .bodyFat(result.getBodyFat())
                            .build();
                    userService.updateCurrentUser(update, user.getEmail());
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
