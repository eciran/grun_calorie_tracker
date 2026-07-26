package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BodyMeasurementDto;
import com.grun.calorietracker.dto.BodyMeasurementRequestDto;
import com.grun.calorietracker.dto.BodyMeasurementSummaryDto;
import com.grun.calorietracker.entity.BodyMeasurementEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.BodyMeasurementUnitSystem;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.repository.BodyMeasurementRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.BodyMeasurementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BodyMeasurementServiceImplTest {

    @Mock private BodyMeasurementRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;

    private BodyMeasurementServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new BodyMeasurementServiceImpl(repository, userRepository, userService);
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("progress@grun.app");
        user.setHeight(180.0);
        lenient().when(userService.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        lenient().when(repository.save(any())).thenAnswer(invocation -> {
            BodyMeasurementEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) entity.setId(10L);
            return entity;
        });
    }

    @Test
    void create_ConvertsImperialValuesToCanonicalUnits() {
        BodyMeasurementRequestDto request = request();
        request.setUnitSystem(BodyMeasurementUnitSystem.IMPERIAL);
        request.setWeight(176.37);
        request.setWaist(35.43);

        BodyMeasurementDto result = service.create(request, user.getEmail());

        assertEquals(80.0, result.getWeightKg(), 0.02);
        assertEquals(89.99, result.getWaistCm(), 0.02);
        assertEquals(24.69, result.getBmi(), 0.02);
    }

    @Test
    void create_StoresAdvancedAndSideSpecificMeasurements() {
        BodyMeasurementRequestDto request = request();
        request.setShoulder(122.0);
        request.setForearm(31.5);
        request.setCalf(39.0);
        request.setLeftUpperArm(40.0);
        request.setRightUpperArm(40.5);
        request.setLeftThigh(60.0);
        request.setRightThigh(60.8);
        request.setLeftCalf(38.7);
        request.setRightCalf(39.1);

        BodyMeasurementDto result = service.create(request, user.getEmail());

        assertEquals(122.0, result.getShoulderCm());
        assertEquals(31.5, result.getForearmCm());
        assertEquals(39.0, result.getCalfCm());
        assertEquals(40.0, result.getLeftUpperArmCm());
        assertEquals(40.5, result.getRightUpperArmCm());
        assertEquals(60.0, result.getLeftThighCm());
        assertEquals(60.8, result.getRightThighCm());
        assertEquals(38.7, result.getLeftCalfCm());
        assertEquals(39.1, result.getRightCalfCm());
    }

    @Test
    void create_ReusesProviderRecordWithSameExternalId() {
        BodyMeasurementEntity existing = new BodyMeasurementEntity();
        existing.setId(44L);
        existing.setUser(user);
        existing.setCreatedAt(LocalDateTime.now().minusDays(1));
        when(repository.findByUserAndProviderAndExternalId(user, HealthProvider.APPLE_HEALTH, "sample-1"))
                .thenReturn(Optional.of(existing));

        BodyMeasurementRequestDto request = request();
        request.setProvider(HealthProvider.APPLE_HEALTH);
        request.setExternalId(" sample-1 ");
        request.setWeight(81.0);

        BodyMeasurementDto result = service.create(request, user.getEmail());

        assertEquals(44L, result.getId());
        verify(repository).save(existing);
        assertEquals("sample-1", existing.getExternalId());
    }

    @Test
    void create_RejectsProviderRecordWithoutExternalId() {
        BodyMeasurementRequestDto request = request();
        request.setProvider(HealthProvider.HEALTH_CONNECT);

        assertThrows(IllegalArgumentException.class, () -> service.create(request, user.getEmail()));
        verify(repository, never()).save(any());
    }

    @Test
    void create_IgnoresClientExternalIdForManualRecord() {
        BodyMeasurementRequestDto request = request();
        request.setExternalId("client-controlled-id");

        service.create(request, user.getEmail());

        ArgumentCaptor<BodyMeasurementEntity> captor = ArgumentCaptor.forClass(BodyMeasurementEntity.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getExternalId());
        verify(repository, never()).findByUserAndProviderAndExternalId(any(), any(), any());
    }

    @Test
    void summary_ReturnsLatestChangesAndBmi() {
        BodyMeasurementEntity latest = entity(2L, 80.0, 18.0, LocalDateTime.of(2026, 7, 20, 8, 0));
        BodyMeasurementEntity previous = entity(1L, 82.0, 19.5, LocalDateTime.of(2026, 7, 10, 8, 0));
        when(repository.findTop2ByUserAndWeightKgIsNotNullOrderByRecordedAtDescIdDesc(user))
                .thenReturn(List.of(latest, previous));
        when(repository.findTop2ByUserAndBodyFatPercentageIsNotNullOrderByRecordedAtDescIdDesc(user))
                .thenReturn(List.of(latest, previous));
        when(repository.findTopByUserOrderByRecordedAtDescIdDesc(user)).thenReturn(Optional.of(latest));
        when(repository.countByUser(user)).thenReturn(2L);

        BodyMeasurementSummaryDto result = service.summary(user.getEmail());

        assertEquals(-2.0, result.getWeightChangeKg());
        assertEquals(-1.5, result.getBodyFatChangePercentagePoints());
        assertEquals(24.69, result.getCurrentBmi(), 0.01);
        assertEquals(2L, result.getRecordCount());
    }

    @Test
    void list_AllowsExactly366Days() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);

        assertDoesNotThrow(() -> service.list(user.getEmail(), start, start.plusDays(366)));
    }

    @Test
    void list_RejectsRangesLongerThanOneYear() {
        assertThrows(IllegalArgumentException.class, () -> service.list(
                user.getEmail(), LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2026, 2, 1, 0, 0)));
    }

    private BodyMeasurementRequestDto request() {
        BodyMeasurementRequestDto request = new BodyMeasurementRequestDto();
        request.setRecordedAt(LocalDateTime.of(2026, 7, 20, 8, 0));
        request.setUnitSystem(BodyMeasurementUnitSystem.METRIC);
        request.setProvider(HealthProvider.MANUAL);
        request.setWaist(90.0);
        return request;
    }

    private BodyMeasurementEntity entity(Long id, Double weight, Double bodyFat, LocalDateTime recordedAt) {
        BodyMeasurementEntity entity = new BodyMeasurementEntity();
        entity.setId(id);
        entity.setUser(user);
        entity.setWeightKg(weight);
        entity.setBodyFatPercentage(bodyFat);
        entity.setRecordedAt(recordedAt);
        entity.setProvider(HealthProvider.MANUAL);
        return entity;
    }
}
