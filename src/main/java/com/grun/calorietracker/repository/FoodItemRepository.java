package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends JpaRepository<FoodItemEntity, Long>, JpaSpecificationExecutor<FoodItemEntity> {
    Optional<FoodItemEntity> findByBarcode(String barcode);
    List<FoodItemEntity> findByVerificationStatus(VerificationStatus verificationStatus);
    List<FoodItemEntity> findByVerificationStatus(VerificationStatus verificationStatus, Sort sort);
    List<FoodItemEntity> findByImageStatus(ImageStatus imageStatus);
    List<FoodItemEntity> findByImageStatus(ImageStatus imageStatus, Sort sort);
    List<FoodItemEntity> findByVerificationStatusAndImageStatus(VerificationStatus verificationStatus, ImageStatus imageStatus);
    List<FoodItemEntity> findByVerificationStatusAndImageStatus(VerificationStatus verificationStatus, ImageStatus imageStatus, Sort sort);

    List<FoodItemEntity> findAll(Specification<FoodItemEntity> spec, Sort sort);
}
