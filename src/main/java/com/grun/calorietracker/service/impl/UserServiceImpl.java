package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.BodyFatRequestDto;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public String loginUser(String email, String password) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid credentials");
        }
        Optional<UserEntity> userOpt = findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new BadCredentialsException("Invalid credentials");
        }
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
        UserEntity savedUser = userRepository.save(user);
        return mapToUserProfileDto(savedUser);
    }

    @Override
    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public List<UserProfileDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToUserProfileDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<UserProfileDto> getById(Long id) {
        return userRepository.findById(id).map(this::mapToUserProfileDto);
    }

    @Override
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
        dto.setMarketRegion(user.getMarketRegion());
        dto.setPreferredLanguage(user.getPreferredLanguage());
        dto.setAvatarUrl(user.getAvatarUrl());
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

    private boolean valueChanged(Object currentValue, Object newValue) {
        return currentValue == null ? newValue != null : !currentValue.equals(newValue);
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
        dto.setMarketRegion(user.getMarketRegion());
        dto.setPreferredLanguage(user.getPreferredLanguage());
        dto.setAvatarUrl(user.getAvatarUrl());
        return dto;
    }
}
