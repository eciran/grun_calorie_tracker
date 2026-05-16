package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminDashboardSummaryDto;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;

    @Override
    public AdminDashboardSummaryDto getSummary() {
        long verifiedProducts = foodItemRepository.countByVerificationStatus(VerificationStatus.VERIFIED);
        long rawImportedProducts = foodItemRepository.countByVerificationStatus(VerificationStatus.RAW_IMPORTED);
        long needsReviewProducts = foodItemRepository.countByVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        long rejectedProducts = foodItemRepository.countByVerificationStatus(VerificationStatus.REJECTED);
        long reviewQueueProducts = foodItemRepository.countReviewQueueProducts(
                List.of(VerificationStatus.RAW_IMPORTED, VerificationStatus.NEEDS_REVIEW),
                ImageStatus.NEEDS_REVIEW
        );

        return new AdminDashboardSummaryDto(
                userRepository.count(),
                userRepository.countByRole(UserRole.STANDARD),
                userRepository.countByRole(UserRole.PRO),
                userRepository.countByRole(UserRole.ADMIN),
                foodItemRepository.count(),
                verifiedProducts,
                rawImportedProducts,
                needsReviewProducts,
                rejectedProducts,
                reviewQueueProducts
        );
    }
}
