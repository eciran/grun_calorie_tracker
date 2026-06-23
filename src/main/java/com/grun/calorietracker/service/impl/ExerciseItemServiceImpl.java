// All comments are in English as requested in the project rules.
package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseItemPageDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import com.grun.calorietracker.exception.DuplicateExerciseItemException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.mapper.ExerciseItemMapper;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.service.ExerciseItemService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseItemServiceImpl implements ExerciseItemService {

    private final ExerciseItemRepository exerciseItemRepository;
    private final ExerciseItemMapper exerciseItemMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ExerciseItemDto> getAllItems() {
        return exerciseItemRepository.findAll()
                .stream()
                .map(exerciseItemMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ExerciseItemPageDto searchItems(String query,
                                           String primaryMuscleGroup,
                                           String equipment,
                                           ExerciseDifficulty difficulty,
                                           Boolean active,
                                           int page,
                                           int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                Math.min(size, 100),
                Sort.by(Sort.Order.asc("name"))
        );

        Page<ExerciseItemEntity> resultPage = exerciseItemRepository.findAll(
                buildSearchSpecification(query, primaryMuscleGroup, equipment, difficulty, active),
                pageRequest
        );

        ExerciseItemPageDto response = new ExerciseItemPageDto();
        response.setContent(resultPage.getContent().stream()
                .map(exerciseItemMapper::toDto)
                .collect(Collectors.toList()));
        response.setPage(resultPage.getNumber());
        response.setSize(resultPage.getSize());
        response.setTotalElements(resultPage.getTotalElements());
        response.setTotalPages(resultPage.getTotalPages());
        response.setFirst(resultPage.isFirst());
        response.setLast(resultPage.isLast());
        return response;
    }

    @Override
    public ExerciseItemDto addItem(ExerciseItemDto dto) {
        ensureMetCodeIsAvailable(dto.getMetCode(), null);
        ExerciseItemEntity entity = exerciseItemMapper.toEntity(dto);
        entity.setMetCode(normalizeMetCode(entity.getMetCode()));
        applyCatalogDefaults(entity);
        ExerciseItemEntity saved = exerciseItemRepository.save(entity);
        return exerciseItemMapper.toDto(saved);
    }

    @Override
    public ExerciseItemDto updateItem(Long id, ExerciseItemDto dto) {
        ExerciseItemEntity existing = exerciseItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise item not found with id: " + id));
        ensureMetCodeIsAvailable(dto.getMetCode(), id);

        existing.setName(dto.getName());
        existing.setMetCode(normalizeMetCode(dto.getMetCode()));
        existing.setCaloriesPerMinute(dto.getCaloriesPerMinute());
        existing.setDescription(dto.getDescription());
        existing.setIconUrl(dto.getIconUrl());
        existing.setPrimaryMuscleGroup(dto.getPrimaryMuscleGroup());
        existing.setSecondaryMuscleGroups(dto.getSecondaryMuscleGroups());
        existing.setEquipment(dto.getEquipment());
        existing.setDifficulty(dto.getDifficulty());
        existing.setInstructions(dto.getInstructions());
        existing.setSafetyNotes(dto.getSafetyNotes());
        existing.setThumbnailUrl(dto.getThumbnailUrl());
        existing.setVideoUrl(dto.getVideoUrl());
        existing.setAnimationUrl(dto.getAnimationUrl());
        existing.setDefaultMeasurementType(dto.getDefaultMeasurementType());
        existing.setAllowedMeasurementTypes(com.grun.calorietracker.mapper.ExerciseItemMapper.toAllowedMeasurementTypesCsv(dto.getAllowedMeasurementTypes()));
        existing.setAiEligible(dto.getAiEligible());
        existing.setActive(dto.getActive());
        applyCatalogDefaults(existing);

        ExerciseItemEntity updated = exerciseItemRepository.save(existing);
        return exerciseItemMapper.toDto(updated);
    }

    @Override
    public void deleteItem(Long id) {
        ExerciseItemEntity existing = exerciseItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise item not found with id: " + id));
        exerciseItemRepository.delete(existing);
    }

    private void applyCatalogDefaults(ExerciseItemEntity entity) {
        if (entity.getAiEligible() == null) {
            entity.setAiEligible(true);
        }
        if (entity.getActive() == null) {
            entity.setActive(true);
        }
    }

    private void ensureMetCodeIsAvailable(String metCode, Long currentItemId) {
        String normalizedMetCode = normalizeMetCode(metCode);
        exerciseItemRepository.findByMetCode(normalizedMetCode)
                .filter(existing -> currentItemId == null || !existing.getId().equals(currentItemId))
                .ifPresent(existing -> {
                    throw new DuplicateExerciseItemException("Exercise item metCode already exists: " + normalizedMetCode);
                });
    }

    private String normalizeMetCode(String metCode) {
        return metCode == null ? null : metCode.trim().toUpperCase();
    }

    private Specification<ExerciseItemEntity> buildSearchSpecification(String query,
                                                                       String primaryMuscleGroup,
                                                                       String equipment,
                                                                       ExerciseDifficulty difficulty,
                                                                       Boolean active) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("metCode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)
                ));
            }

            if (primaryMuscleGroup != null && !primaryMuscleGroup.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("primaryMuscleGroup")),
                        primaryMuscleGroup.trim().toLowerCase()
                ));
            }

            if (equipment != null && !equipment.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("equipment")),
                        equipment.trim().toLowerCase()
                ));
            }

            if (difficulty != null) {
                predicates.add(criteriaBuilder.equal(root.get("difficulty"), difficulty));
            }

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
