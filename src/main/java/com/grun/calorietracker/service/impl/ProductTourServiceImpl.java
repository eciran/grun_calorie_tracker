package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ProductTourDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserProductTourEntity;
import com.grun.calorietracker.enums.ProductTourStatus;
import com.grun.calorietracker.repository.UserProductTourRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.ProductTourService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductTourServiceImpl implements ProductTourService {

    public static final String DASHBOARD_TOUR_KEY = "dashboard";
    public static final String DASHBOARD_TOUR_VERSION = "dashboard-tour-v1";
    private static final Set<ProductTourStatus> FINAL_STATUSES = Set.of(
            ProductTourStatus.COMPLETED,
            ProductTourStatus.SKIPPED,
            ProductTourStatus.DISMISSED);

    private final UserRepository userRepository;
    private final UserProductTourRepository productTourRepository;

    @Override
    @Transactional(readOnly = true)
    public ProductTourDto get(String email, String tourKey, String version) {
        validateContract(tourKey, version);
        UserEntity user = requireUser(email);
        return productTourRepository.findByUserAndTourKeyAndTourVersion(user, tourKey, version)
                .map(this::toDto)
                .orElseGet(() -> new ProductTourDto(
                        tourKey, version, ProductTourStatus.NOT_STARTED, null));
    }

    @Override
    @Transactional
    public ProductTourDto recordDecision(
            String email, String tourKey, String version, ProductTourStatus status) {
        validateContract(tourKey, version);
        if (status == null || !FINAL_STATUSES.contains(status)) {
            throw new IllegalArgumentException("A final product tour status is required.");
        }
        UserEntity user = requireUser(email);
        LocalDateTime now = LocalDateTime.now();
        UserProductTourEntity entity = productTourRepository
                .findByUserAndTourKeyAndTourVersion(user, tourKey, version)
                .orElseGet(() -> {
                    UserProductTourEntity created = new UserProductTourEntity();
                    created.setUser(user);
                    created.setTourKey(tourKey);
                    created.setTourVersion(version);
                    return created;
                });
        entity.setStatus(status);
        entity.setCompletedAt(now);
        entity.setUpdatedAt(now);
        return toDto(productTourRepository.save(entity));
    }

    private void validateContract(String tourKey, String version) {
        if (!DASHBOARD_TOUR_KEY.equals(tourKey)
                || !DASHBOARD_TOUR_VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported product tour contract.");
        }
    }

    private UserEntity requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    private ProductTourDto toDto(UserProductTourEntity entity) {
        return new ProductTourDto(
                entity.getTourKey(),
                entity.getTourVersion(),
                entity.getStatus(),
                entity.getCompletedAt());
    }
}
