package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.PushTokenDto;
import com.grun.calorietracker.dto.PushTokenRegisterRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.PushDeliveryLogRepository;
import com.grun.calorietracker.repository.UserPushTokenRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.PushTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PushTokenServiceImpl implements PushTokenService {

    private final UserRepository userRepository;
    private final UserPushTokenRepository userPushTokenRepository;
    private final PushDeliveryLogRepository pushDeliveryLogRepository;

    @Override
    @Transactional
    public PushTokenDto register(String email, PushTokenRegisterRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Push token request is required.");
        }
        UserEntity user = getUser(email);
        String tokenHash = hash(request.getToken().trim());
        UserPushTokenEntity entity = userPushTokenRepository.findByTokenHash(tokenHash)
                .orElseGet(UserPushTokenEntity::new);
        entity.setUser(user);
        entity.setProvider(request.getProvider());
        entity.setPlatform(request.getPlatform());
        entity.setDeviceId(trimToNull(request.getDeviceId()));
        entity.setTokenHash(tokenHash);
        entity.setTokenValue(request.getToken().trim());
        entity.setEnabled(true);
        entity.setRevokedAt(null);
        entity.setLastSeenAt(LocalDateTime.now());
        return toDto(userPushTokenRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushTokenDto> list(String email) {
        UserEntity user = getUser(email);
        return userPushTokenRepository.findByUserOrderByLastSeenAtDesc(user)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public PushTokenDto revoke(String email, Long tokenId) {
        UserEntity user = getUser(email);
        UserPushTokenEntity entity = userPushTokenRepository.findByIdAndUser(tokenId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Push token not found"));
        entity.setEnabled(false);
        entity.setRevokedAt(LocalDateTime.now());
        pushDeliveryLogRepository.deleteByPushToken(entity);
        return toDto(userPushTokenRepository.save(entity));
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private PushTokenDto toDto(UserPushTokenEntity entity) {
        PushTokenDto dto = new PushTokenDto();
        dto.setId(entity.getId());
        dto.setProvider(entity.getProvider());
        dto.setPlatform(entity.getPlatform());
        dto.setDeviceId(entity.getDeviceId());
        dto.setTokenPreview(preview(entity.getTokenValue()));
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setLastSeenAt(entity.getLastSeenAt());
        dto.setRevokedAt(entity.getRevokedAt());
        return dto;
    }

    private String preview(String token) {
        if (token == null || token.length() <= 10) {
            return "****";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
