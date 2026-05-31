package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.UserConsentDto;
import com.grun.calorietracker.dto.UserConsentRequestDto;
import com.grun.calorietracker.entity.UserConsentEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.UserConsentRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.LegalConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LegalConsentServiceImpl implements LegalConsentService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;

    @Override
    @Transactional
    public UserConsentDto recordConsent(String userEmail, UserConsentRequestDto request, String ipAddress, String userAgent) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        UserConsentEntity entity = new UserConsentEntity();
        entity.setUser(user);
        entity.setConsentType(request.getConsentType());
        entity.setVersion(request.getVersion().trim());
        entity.setStatus(request.getStatus());
        entity.setSource(request.getSource() == null || request.getSource().isBlank() ? "UNKNOWN" : request.getSource().trim());
        entity.setIpAddress(limit(ipAddress, 128));
        entity.setUserAgent(limit(userAgent, 512));
        entity.setCreatedAt(LocalDateTime.now());
        return toDto(userConsentRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserConsentDto> listMyConsents(String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return userConsentRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toDto)
                .toList();
    }

    private UserConsentDto toDto(UserConsentEntity entity) {
        return new UserConsentDto(
                entity.getId(),
                entity.getConsentType(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getSource(),
                entity.getCreatedAt()
        );
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
