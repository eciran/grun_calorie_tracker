package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.UserConsentRequestDto;
import com.grun.calorietracker.entity.UserConsentEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.LegalConsentStatus;
import com.grun.calorietracker.enums.LegalConsentType;
import com.grun.calorietracker.repository.UserConsentRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.LegalConsentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalConsentServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserConsentRepository userConsentRepository;

    private LegalConsentServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LegalConsentServiceImpl(userRepository, userConsentRepository);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@grun.app");
    }

    @Test
    void recordConsent_storesImmutableConsentDecision() {
        UserConsentRequestDto request = new UserConsentRequestDto(
                LegalConsentType.PRIVACY_POLICY,
                " privacy-2026-05 ",
                LegalConsentStatus.ACCEPTED,
                "MOBILE_ONBOARDING"
        );
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(userConsentRepository.save(org.mockito.ArgumentMatchers.any(UserConsentEntity.class)))
                .thenAnswer(invocation -> {
                    UserConsentEntity entity = invocation.getArgument(0);
                    entity.setId(10L);
                    return entity;
                });

        var dto = service.recordConsent("user@grun.app", request, "10.0.0.1", "JUnit");

        assertEquals(10L, dto.getId());
        assertEquals("privacy-2026-05", dto.getVersion());
        ArgumentCaptor<UserConsentEntity> captor = ArgumentCaptor.forClass(UserConsentEntity.class);
        verify(userConsentRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals("10.0.0.1", captor.getValue().getIpAddress());
    }

    @Test
    void listMyConsents_returnsNewestFirstHistory() {
        UserConsentEntity entity = new UserConsentEntity();
        entity.setId(11L);
        entity.setUser(user);
        entity.setConsentType(LegalConsentType.TERMS_OF_SERVICE);
        entity.setVersion("terms-2026-05");
        entity.setStatus(LegalConsentStatus.ACCEPTED);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(userConsentRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(entity));

        var result = service.listMyConsents("user@grun.app");

        assertEquals(1, result.size());
        assertEquals(LegalConsentType.TERMS_OF_SERVICE, result.get(0).getConsentType());
    }
}
