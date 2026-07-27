package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminUserActivityFilter;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.RefreshTokenService;
import com.grun.calorietracker.service.UserService;
import com.grun.calorietracker.service.support.UserAgeSupport;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

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
    private final RefreshTokenService refreshTokenService;
    private final UserTimeZoneSupport userTimeZoneSupport;
    private final UserAgeSupport userAgeSupport;
    private final int maxFailedLoginAttempts;
    private final int loginLockMinutes;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil,
                           RefreshTokenService refreshTokenService,
                           UserTimeZoneSupport userTimeZoneSupport,
                           UserAgeSupport userAgeSupport,
                           @Value("${grun.security.login.max-failed-attempts:" + DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS + "}") int maxFailedLoginAttempts,
                           @Value("${grun.security.login.lock-minutes:" + DEFAULT_LOGIN_LOCK_MINUTES + "}") int loginLockMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.userTimeZoneSupport = userTimeZoneSupport;
        this.userAgeSupport = userAgeSupport;
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
    public AdminUserPageDto listUsersForAdmin(String search,
                                               UserRole role,
                                               Boolean accountEnabled,
                                               Boolean accountLocked,
                                               SubscriptionPlan plan,
                                               MarketRegion region,
                                               PreferredLanguage language,
                                               Boolean emailVerified,
                                               AdminUserActivityFilter activity,
                                               int page,
                                               int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_ADMIN_USER_PAGE_SIZE : Math.min(size, MAX_ADMIN_USER_PAGE_SIZE);
        Specification<UserEntity> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (search != null && !search.isBlank()) {
            String normalizedSearch = search.trim().toLowerCase();
            specification = specification.and((root, query, criteriaBuilder) -> {
                var emailMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), "%" + normalizedSearch + "%");
                var nameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + normalizedSearch + "%");
                if (normalizedSearch.chars().allMatch(Character::isDigit)) {
                    return criteriaBuilder.or(emailMatch, nameMatch, criteriaBuilder.equal(root.get("id"), Long.valueOf(normalizedSearch)));
                }
                return criteriaBuilder.or(emailMatch, nameMatch);
            });
        }
        if (role != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("role"), role));
        }
        if (accountEnabled != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("accountEnabled"), accountEnabled));
        }
        if (accountLocked != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("accountLocked"), accountLocked));
        }
        if (region != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("marketRegion"), region));
        }
        if (language != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("preferredLanguage"), language));
        }
        if (emailVerified != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("emailVerified"), emailVerified));
        }
        if (activity != null) {
            Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
            specification = specification.and((root, query, criteriaBuilder) -> switch (activity) {
                case ACTIVE_30_DAYS -> criteriaBuilder.greaterThanOrEqualTo(root.get("lastActiveAt"), cutoff);
                case INACTIVE_30_DAYS -> criteriaBuilder.and(
                        criteriaBuilder.isNotNull(root.get("lastActiveAt")),
                        criteriaBuilder.lessThan(root.get("lastActiveAt"), cutoff)
                );
                case NEVER_ACTIVE -> criteriaBuilder.isNull(root.get("lastActiveAt"));
            });
        }
        if (plan != null) {
            specification = specification.and((root, query, criteriaBuilder) -> {
                var subquery = query.subquery(Long.class);
                var subscription = subquery.from(SubscriptionEntity.class);
                subquery.select(subscription.get("user").get("id"));
                subquery.where(
                        criteriaBuilder.equal(subscription.get("user").get("id"), root.get("id")),
                        criteriaBuilder.equal(subscription.get("planType"), plan)
                );
                return criteriaBuilder.exists(subquery);
            });
        }

        Page<UserEntity> users = userRepository.findAll(
                specification,
                PageRequest.of(safePage, safeSize, Sort.by("id").descending())
        );
        AdminUserPageDto dto = new AdminUserPageDto();
        dto.setContent(users.getContent().stream().map(this::mapToAdminUserDto).toList());
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
    public Optional<AdminUserDto> getById(Long id) {
        return userRepository.findById(id).map(this::mapToAdminUserDto);
    }

    @Override
    public AdminUserDto updateUserStatus(Long userId, AdminUserStatusUpdateRequestDto request, String adminEmail) {
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
        if (Boolean.FALSE.equals(request.getAccountEnabled()) || Boolean.TRUE.equals(request.getAccountLocked())) {
            refreshTokenService.revokeAllForUser(user);
        }
        return mapToAdminUserDto(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public MyProfileDto getMyProfile(String email) {
        return mapToMyProfileDto(requireUser(email));
    }

    @Override
    public MyProfileDto updateMyProfile(MyProfileUpdateRequestDto request, String email) {
        UserEntity user = requireUser(email);
        if (request.getName() != null) {
            user.setName(request.getName().trim());
        }
        return mapToMyProfileDto(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileBodyDto getProfileBody(String email) {
        return mapToProfileBodyDto(requireUser(email));
    }

    @Override
    public MyProfileDto updateProfileBody(ProfileBodyUpdateRequestDto request, String email) {
        UserEntity user = requireUser(email);
        boolean recalculate = false;
        if (request.getBirthDate() != null) {
            Integer resolvedAge = userAgeSupport.resolveAge(
                    request.getBirthDate(),
                    null,
                    userTimeZoneSupport.zoneId(user)
            );
            recalculate |= valueChanged(user.getBirthDate(), request.getBirthDate())
                    || valueChanged(user.getAge(), resolvedAge);
            user.setBirthDate(request.getBirthDate());
            user.setAge(resolvedAge);
        } else if (request.getAge() != null && user.getBirthDate() == null) {
            recalculate |= valueChanged(user.getAge(), request.getAge());
            user.setAge(request.getAge());
        }
        if (request.getGender() != null) {
            String normalizedGender = request.getGender().toUpperCase();
            recalculate |= valueChanged(user.getGender(), normalizedGender);
            user.setGender(normalizedGender);
        }
        if (request.getHeight() != null) {
            recalculate |= valueChanged(user.getHeight(), request.getHeight());
            user.setHeight(request.getHeight());
        }
        if (request.getWeight() != null) {
            recalculate |= valueChanged(user.getWeight(), request.getWeight());
            user.setWeight(request.getWeight());
        }
        if (request.getBodyFat() != null) {
            recalculate |= valueChanged(user.getBodyFatPercentage(), request.getBodyFat());
            user.setBodyFatPercentage(request.getBodyFat());
        }
        MyProfileDto response = mapToMyProfileDto(userRepository.save(user));
        response.setGoalRecalculationRecommended(recalculate);
        if (recalculate) {
            response.setGoalRecalculationReason("Profile metrics that affect calorie calculation changed.");
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfilePreferencesDto getProfilePreferences(String email) {
        return mapToProfilePreferencesDto(requireUser(email));
    }

    @Override
    public MyProfileDto updateProfilePreferences(ProfilePreferencesUpdateRequestDto request, String email) {
        UserEntity user = requireUser(email);
        if (request.getCountryCode() != null) {
            user.setCountryCode(request.getCountryCode());
        }
        if (request.getMarketRegion() != null) {
            user.setMarketRegion(request.getMarketRegion());
        }
        if (request.getPreferredLanguage() != null) {
            user.setPreferredLanguage(request.getPreferredLanguage());
        }
        if (request.getTimeZone() != null) {
            user.setTimeZone(userTimeZoneSupport.normalizeOrDefault(request.getTimeZone()));
        }
        if (request.getUnitPreference() != null) {
            user.setUnitPreference(request.getUnitPreference());
        }
        return mapToMyProfileDto(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileSecurityDto getProfileSecurity(String email) {
        UserEntity user = requireUser(email);
        return ProfileSecurityDto.builder()
                .emailVerified(user.getEmailVerified())
                .passwordSet(user.getPasswordSet())
                .build();
    }
    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUser(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        // Bu metot zaten doÄŸru ÅŸekilde DTO dÃ¶ndÃ¼rÃ¼yor, olduÄŸu gibi bÄ±rakÄ±ldÄ±.
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setAge(resolvedAge(user));
        dto.setBirthDate(user.getBirthDate());
        dto.setGender(user.getGender());
        dto.setHeight(user.getHeight());
        dto.setWeight(user.getWeight());
        dto.setRole(user.getRole());
        dto.setEmailVerified(user.getEmailVerified());
        dto.setPasswordSet(user.getPasswordSet());
        dto.setAccountEnabled(user.getAccountEnabled());
        dto.setAccountLocked(user.getAccountLocked());
        dto.setMarketRegion(user.getMarketRegion());
        dto.setCountryCode(user.getCountryCode());
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
            if (updatedUserDto.getBirthDate() != null) {
                Integer resolvedAge = userAgeSupport.resolveAge(
                        updatedUserDto.getBirthDate(),
                        null,
                        userTimeZoneSupport.zoneId(existingUser)
                );
                goalRecalculationRecommended |= valueChanged(existingUser.getBirthDate(), updatedUserDto.getBirthDate())
                        || valueChanged(existingUser.getAge(), resolvedAge);
                existingUser.setBirthDate(updatedUserDto.getBirthDate());
                existingUser.setAge(resolvedAge);
            } else if (updatedUserDto.getAge() != null && existingUser.getBirthDate() == null) {
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
            if (updatedUserDto.getCountryCode() != null) existingUser.setCountryCode(updatedUserDto.getCountryCode());
            if (updatedUserDto.getPreferredLanguage() != null) existingUser.setPreferredLanguage(updatedUserDto.getPreferredLanguage());
            if (updatedUserDto.getTimeZone() != null) existingUser.setTimeZone(userTimeZoneSupport.normalizeOrDefault(updatedUserDto.getTimeZone()));
            if (updatedUserDto.getUnitPreference() != null) existingUser.setUnitPreference(updatedUserDto.getUnitPreference());

            // DTO'dan gelen verilerle entity'yi gÃ¼ncelledikten sonra kaydet
            UserEntity updatedUser = userRepository.save(existingUser);
            // Kaydedilen entity'yi DTO'ya dÃ¶nÃ¼ÅŸtÃ¼rerek dÃ¶ndÃ¼r
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
        if (request.getStepRemindersEnabled() != null) {
            user.setStepRemindersEnabled(request.getStepRemindersEnabled());
        }
        if (request.getFastingRemindersEnabled() != null) {
            user.setFastingRemindersEnabled(request.getFastingRemindersEnabled());
        }
        if (request.getRecipeSuggestionsEnabled() != null) {
            user.setRecipeSuggestionsEnabled(request.getRecipeSuggestionsEnabled());
        }
        if (request.getAiInsightsEnabled() != null) {
            user.setAiInsightsEnabled(request.getAiInsightsEnabled());
        }
        if (request.getWeeklyReportsEnabled() != null) {
            user.setWeeklyReportsEnabled(request.getWeeklyReportsEnabled());
        }
        if (request.getMarketingNotificationsEnabled() != null) {
            user.setMarketingNotificationsEnabled(request.getMarketingNotificationsEnabled());
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

    private UserEntity requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    private Integer resolvedAge(UserEntity user) {
        return userAgeSupport.resolveAge(user, userTimeZoneSupport.zoneId(user));
    }
    private MyProfileDto mapToMyProfileDto(UserEntity user) {
        return MyProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .body(mapToProfileBodyDto(user))
                .preferences(mapToProfilePreferencesDto(user))
                .security(ProfileSecurityDto.builder()
                        .emailVerified(user.getEmailVerified())
                        .passwordSet(user.getPasswordSet())
                        .build())
                .build();
    }

    private ProfileBodyDto mapToProfileBodyDto(UserEntity user) {
        return ProfileBodyDto.builder()
                .age(resolvedAge(user))
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .height(user.getHeight())
                .weight(user.getWeight())
                .bmi(user.getBmi())
                .bodyFat(user.getBodyFatPercentage())
                .build();
    }

    private ProfilePreferencesDto mapToProfilePreferencesDto(UserEntity user) {
        return ProfilePreferencesDto.builder()
                .marketRegion(user.getMarketRegion())
                .countryCode(user.getCountryCode())
                .preferredLanguage(user.getPreferredLanguage())
                .timeZone(userTimeZoneSupport.normalizeOrDefault(user.getTimeZone()))
                .unitPreference(user.getUnitPreference())
                .build();
    }

    private AdminUserDto mapToAdminUserDto(UserEntity user) {
        return AdminUserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .emailVerified(user.getEmailVerified())
                .passwordSet(user.getPasswordSet())
                .accountEnabled(user.getAccountEnabled())
                .accountLocked(user.getAccountLocked())
                .marketRegion(user.getMarketRegion())
                .countryCode(user.getCountryCode())
                .preferredLanguage(user.getPreferredLanguage())
                .timeZone(userTimeZoneSupport.normalizeOrDefault(user.getTimeZone()))
                .createdAt(user.getCreatedAt())
                .emailVerifiedAt(user.getEmailVerifiedAt())
                .lastLoginAt(user.getLastLoginAt())
                .lastActiveAt(user.getLastActiveAt())
                .build();
    }
    private UserProfileDto mapToUserProfileDto(UserEntity user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setAge(resolvedAge(user));
        dto.setBirthDate(user.getBirthDate());
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
        dto.setCountryCode(user.getCountryCode());
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
                user.getHydrationRemindersEnabled(),
                user.getStepRemindersEnabled(),
                user.getFastingRemindersEnabled(),
                user.getRecipeSuggestionsEnabled(),
                user.getAiInsightsEnabled(),
                user.getWeeklyReportsEnabled(),
                user.getMarketingNotificationsEnabled()
        );
    }
}
