package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminUserStatusUpdateRequestDto;
import com.grun.calorietracker.dto.AdminUserPageDto;
import com.grun.calorietracker.dto.BodyFatRequestDto;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.NotificationPreferenceDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.UserService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private static final int DEFAULT_ADMIN_USER_PAGE_SIZE = 50;
    private static final int MAX_ADMIN_USER_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int DEFAULT_LOGIN_LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserTimeZoneSupport userTimeZoneSupport;
    private final int maxFailedLoginAttempts;
    private final int loginLockMinutes;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil,
                           UserTimeZoneSupport userTimeZoneSupport,
                           @Value("${grun.security.login.max-failed-attempts:" + DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS + "}") int maxFailedLoginAttempts,
                           @Value("${grun.security.login.lock-minutes:" + DEFAULT_LOGIN_LOCK_MINUTES + "}") int loginLockMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userTimeZoneSupport = userTimeZoneSupport;
        this.maxFailedLoginAttempts = Math.max(1, maxFailedLoginAttempts);
        this.loginLockMinutes = Math.max(1, loginLockMinutes);
    }

    @Override
    @Transactional
    public String loginUser(String email, String password) {
        Optional<UserEntity> userOpt = userRepository.findByEmailForUpdate(email);
        if (userOpt.isEmpty()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        UserEntity user = userOpt.get();
        if (isTemporarilyLoginLocked(user)) {
            throw new BadCredentialsException("Account is temporarily locked. Try again later.");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (AuthenticationException e) {
            registerFailedLogin(user);
            throw new BadCredentialsException("Invalid credentials");
        }
        resetFailedLoginProtection(user);
        return jwtUtil.generateToken(email);
    }

    @Override
    public UserProfileDto registerUser(UserEntity user) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setPasswordSet(true);
        user.setRole(UserRole.STANDARD);
        user.setTimeZone(userTimeZoneSupport.normalizeOrDefault(user.getTimeZone()));
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        user.setLastFailedLoginAt(null);
        UserEntity savedUser = userRepository.save(user);
        return mapToUserProfileDto(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserProfileDto> getAllUsers() {
        return userRepository.findAll(PageRequest.of(0, MAX_ADMIN_USER_PAGE_SIZE, Sort.by("id").descending()))
                .getContent()
                .stream()
                .map(this::mapToUserProfileDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserPageDto listUsersForAdmin(UserRole role, Boolean accountEnabled, Boolean accountLocked, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_ADMIN_USER_PAGE_SIZE : Math.min(size, MAX_ADMIN_USER_PAGE_SIZE);
        Specification<UserEntity> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (role != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("role"), role));
        }
        if (accountEnabled != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("accountEnabled"), accountEnabled));
        }
        if (accountLocked != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("accountLocked"), accountLocked));
        }

        Page<UserEntity> users = userRepository.findAll(
                specification,
                PageRequest.of(safePage, safeSize, Sort.by("id").descending())
        );
        AdminUserPageDto dto = new AdminUserPageDto();
        dto.setContent(users.getContent().stream().map(this::mapToUserProfileDto).toList());
        dto.setPage(users.getNumber());
        dto.setSize(users.getSize());
        dto.setTotalElements(users.getTotalElements());
        dto.setTotalPages(users.getTotalPages());
        dto.setFirst(users.isFirst());
        dto.setLast(users.isLast());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfileDto> getById(Long id) {
        return userRepository.findById(id).map(this::mapToUserProfileDto);
    }

    @Override
    public UserProfileDto updateUserStatus(Long userId, AdminUserStatusUpdateRequestDto request, String adminEmail) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        boolean selfTarget = user.getEmail() != null && user.getEmail().equalsIgnoreCase(adminEmail);
        boolean wouldDisableSelf = Boolean.FALSE.equals(request.getAccountEnabled());
        boolean wouldLockSelf = Boolean.TRUE.equals(request.getAccountLocked());
        if (selfTarget && (wouldDisableSelf || wouldLockSelf)) {
            throw new IllegalArgumentException("Admin cannot disable or lock their own account.");
        }

        user.setAccountEnabled(request.getAccountEnabled());
        user.setAccountLocked(request.getAccountLocked());
        return mapToUserProfileDto(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUser(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        // Bu metot zaten doğru şekilde DTO döndürüyor, olduğu gibi bırakıldı.
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setAge(user.getAge());
        dto.setGender(user.getGender());
        dto.setHeight(user.getHeight());
        dto.setWeight(user.getWeight());
        dto.setRole(user.getRole());
        dto.setEmailVerified(user.getEmailVerified());
        dto.setPasswordSet(user.getPasswordSet());
        dto.setAccountEnabled(user.getAccountEnabled());
        dto.setAccountLocked(user.getAccountLocked());
        dto.setMarketRegion(user.getMarketRegion());
        dto.setPreferredLanguage(user.getPreferredLanguage());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setTimeZone(userTimeZoneSupport.normalizeOrDefault(user.getTimeZone()));
        dto.setUnitPreference(user.getUnitPreference());
        if (user.getHeight() != null && user.getWeight() != null && user.getHeight() > 0) {
            double heightM = user.getHeight() / 100.0;
            double bmi = user.getWeight() / (heightM * heightM);
            dto.setBmi(Math.round(bmi * 100) / 100.0);
            double bodyFat = 0.0;
            if ("MALE".equalsIgnoreCase(user.getGender())) {
                bodyFat = 1.20 * bmi + 0.23 * (user.getAge() != null ? user.getAge() : 25) - 16.2;
            } else {
                bodyFat = 1.20 * bmi + 0.23 * (user.getAge() != null ? user.getAge() : 25) - 5.4;
            }
            dto.setBodyFat(Math.round(bodyFat * 100) / 100.0);
        }
        return dto;
    }

    @Override
    public UserProfileDto updateCurrentUser(UserProfileDto updatedUserDto, String email) {
        return userRepository.findByEmail(email).map(existingUser -> {
            boolean goalRecalculationRecommended = false;
            if (updatedUserDto.getName() != null) existingUser.setName(updatedUserDto.getName());
            if (updatedUserDto.getGender() != null) {
                goalRecalculationRecommended |= valueChanged(existingUser.getGender(), updatedUserDto.getGender());
                existingUser.setGender(updatedUserDto.getGender());
            }
            if (updatedUserDto.getAge() != null) {
                goalRecalculationRecommended |= valueChanged(existingUser.getAge(), updatedUserDto.getAge());
                existingUser.setAge(updatedUserDto.getAge());
            }
            if (updatedUserDto.getHeight() != null) {
                goalRecalculationRecommended |= valueChanged(existingUser.getHeight(), updatedUserDto.getHeight());
                existingUser.setHeight(updatedUserDto.getHeight());
            }
            if (updatedUserDto.getWeight() != null) {
                goalRecalculationRecommended |= valueChanged(existingUser.getWeight(), updatedUserDto.getWeight());
                existingUser.setWeight(updatedUserDto.getWeight());
            }
            if (updatedUserDto.getBodyFat() != null) {
                goalRecalculationRecommended |= valueChanged(existingUser.getBodyFatPercentage(), updatedUserDto.getBodyFat());
                existingUser.setBodyFatPercentage(updatedUserDto.getBodyFat());
            }
            if (updatedUserDto.getBmi() != null) existingUser.setBmi(updatedUserDto.getBmi());
            if (updatedUserDto.getMarketRegion() != null) existingUser.setMarketRegion(updatedUserDto.getMarketRegion());
            if (updatedUserDto.getPreferredLanguage() != null) existingUser.setPreferredLanguage(updatedUserDto.getPreferredLanguage());
            if (updatedUserDto.getTimeZone() != null) existingUser.setTimeZone(userTimeZoneSupport.normalizeOrDefault(updatedUserDto.getTimeZone()));
            if (updatedUserDto.getUnitPreference() != null) existingUser.setUnitPreference(updatedUserDto.getUnitPreference());

            // DTO'dan gelen verilerle entity'yi güncelledikten sonra kaydet
            UserEntity updatedUser = userRepository.save(existingUser);
            // Kaydedilen entity'yi DTO'ya dönüştürerek döndür
            UserProfileDto response = mapToUserProfileDto(updatedUser);
            response.setGoalRecalculationRecommended(goalRecalculationRecommended);
            if (goalRecalculationRecommended) {
                response.setGoalRecalculationReason("Profile metrics that affect calorie calculation changed.");
            }
            return response;
        }).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceDto getNotificationPreferences(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return toNotificationPreferenceDto(user);
    }

    @Override
    public NotificationPreferenceDto updateNotificationPreferences(String email, NotificationPreferenceDto request) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        if (request == null) {
            return toNotificationPreferenceDto(user);
        }
        if (request.getPushNotificationsEnabled() != null) {
            user.setPushNotificationsEnabled(request.getPushNotificationsEnabled());
        }
        if (request.getMealRemindersEnabled() != null) {
            user.setMealRemindersEnabled(request.getMealRemindersEnabled());
        }
        if (request.getHydrationRemindersEnabled() != null) {
            user.setHydrationRemindersEnabled(request.getHydrationRemindersEnabled());
        }
        return toNotificationPreferenceDto(userRepository.save(user));
    }

    private boolean valueChanged(Object currentValue, Object newValue) {
        return currentValue == null ? newValue != null : !currentValue.equals(newValue);
    }

    private boolean isTemporarilyLoginLocked(UserEntity user) {
        LocalDateTime lockedUntil = user.getLoginLockedUntil();
        if (lockedUntil == null) {
            return false;
        }
        if (lockedUntil.isAfter(LocalDateTime.now())) {
            return true;
        }
        user.setLoginLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        return false;
    }

    private void registerFailedLogin(UserEntity user) {
        int attempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
        attempts++;
        user.setFailedLoginAttempts(attempts);
        user.setLastFailedLoginAt(LocalDateTime.now());
        if (attempts >= maxFailedLoginAttempts) {
            user.setLoginLockedUntil(LocalDateTime.now().plusMinutes(loginLockMinutes));
        }
        userRepository.save(user);
    }

    private void resetFailedLoginProtection(UserEntity user) {
        if ((user.getFailedLoginAttempts() == null || user.getFailedLoginAttempts() == 0)
                && user.getLoginLockedUntil() == null
                && user.getLastFailedLoginAt() == null) {
            return;
        }
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        user.setLastFailedLoginAt(null);
        userRepository.save(user);
    }

    @Override
    public BodyFatResultDto calculateBodyFatAndBmi(BodyFatRequestDto req, UserEntity user) {
        BodyFatResultDto result = new BodyFatResultDto();
        Double bmi = null;
        Double bodyFat = null;

        if (user.getHeight() != null && user.getWeight() != null) {
            double heightM = user.getHeight() / 100.0;
            bmi = user.getWeight() / (heightM * heightM);
            result.setBmi(Math.round(bmi * 100.0) / 100.0);
        }
        if ("MALE".equalsIgnoreCase(user.getGender())
                && req.getWaistCircumference() != null
                && req.getNeckCircumference() != null
                && user.getHeight() != null) {
            bodyFat = 495.0 / (1.0324 - 0.19077 * Math.log10(req.getWaistCircumference() - req.getNeckCircumference())
                    + 0.15456 * Math.log10(user.getHeight())) - 450.0;
            result.setBodyFat(Math.round(bodyFat * 100.0) / 100.0);
            return result;
        }
        if ("FEMALE".equalsIgnoreCase(user.getGender())
                && req.getWaistCircumference() != null
                && req.getNeckCircumference() != null
                && req.getHipCircumference() != null
                && user.getHeight() != null) {
            bodyFat = 495.0 / (1.29579 - 0.35004 * Math.log10(req.getWaistCircumference() + req.getHipCircumference() - req.getNeckCircumference())
                    + 0.22100 * Math.log10(user.getHeight())) - 450.0;
            result.setBodyFat(Math.round(bodyFat * 100.0) / 100.0);
            return result;
        }
        if (bmi != null && user.getAge() != null && user.getGender() != null) {
            bodyFat = "MALE".equalsIgnoreCase(user.getGender())
                    ? 1.20 * bmi + 0.23 * user.getAge() - 16.2
                    : 1.20 * bmi + 0.23 * user.getAge() - 5.4;
            result.setBodyFat(Math.round(bodyFat * 100.0) / 100.0);
        }
        return result;
    }

    private UserProfileDto mapToUserProfileDto(UserEntity user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setAge(user.getAge());
        dto.setGender(user.getGender());
        dto.setHeight(user.getHeight());
        dto.setWeight(user.getWeight());
        dto.setBodyFat(user.getBodyFatPercentage());
        dto.setBmi(user.getBmi());
        dto.setRole(user.getRole());
        dto.setEmailVerified(user.getEmailVerified());
        dto.setPasswordSet(user.getPasswordSet());
        dto.setAccountEnabled(user.getAccountEnabled());
        dto.setAccountLocked(user.getAccountLocked());
        dto.setMarketRegion(user.getMarketRegion());
        dto.setPreferredLanguage(user.getPreferredLanguage());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setTimeZone(userTimeZoneSupport.normalizeOrDefault(user.getTimeZone()));
        dto.setUnitPreference(user.getUnitPreference());
        return dto;
    }

    private NotificationPreferenceDto toNotificationPreferenceDto(UserEntity user) {
        return new NotificationPreferenceDto(
                user.getPushNotificationsEnabled(),
                user.getMealRemindersEnabled(),
                user.getHydrationRemindersEnabled()
        );
    }
}
