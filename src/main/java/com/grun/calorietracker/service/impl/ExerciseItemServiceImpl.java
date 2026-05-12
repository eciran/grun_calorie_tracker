// All comments are in English as requested in the project rules.
package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.mapper.ExerciseItemMapper;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.service.ExerciseItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseItemServiceImpl implements ExerciseItemService {

    private final ExerciseItemRepository exerciseItemRepository;
    private final ExerciseItemMapper exerciseItemMapper;

    @Override
    public List<ExerciseItemDto> getAllItems() {
        return exerciseItemRepository.findAll()
                .stream()
                .map(exerciseItemMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public ExerciseItemDto addItem(ExerciseItemDto dto) {
        ExerciseItemEntity entity = exerciseItemMapper.toEntity(dto);
        applyCatalogDefaults(entity);
        ExerciseItemEntity saved = exerciseItemRepository.save(entity);
        return exerciseItemMapper.toDto(saved);
    }

    @Override
    public ExerciseItemDto updateItem(Long id, ExerciseItemDto dto) {
        ExerciseItemEntity existing = exerciseItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise item not found with id: " + id));

        existing.setName(dto.getName());
        existing.setMetCode(dto.getMetCode());
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
}
