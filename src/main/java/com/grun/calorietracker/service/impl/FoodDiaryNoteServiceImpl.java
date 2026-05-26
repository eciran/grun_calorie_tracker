package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodDiaryNoteDto;
import com.grun.calorietracker.dto.FoodDiaryNoteRequestDto;
import com.grun.calorietracker.entity.FoodDiaryNoteEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodDiaryNoteRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FoodDiaryNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FoodDiaryNoteServiceImpl implements FoodDiaryNoteService {

    private final FoodDiaryNoteRepository foodDiaryNoteRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public FoodDiaryNoteDto upsertNote(String email, LocalDate diaryDate, FoodDiaryNoteRequestDto request) {
        UserEntity user = getUser(email);
        LocalDateTime now = LocalDateTime.now();
        FoodDiaryNoteEntity note = foodDiaryNoteRepository.findByUserAndDiaryDate(user, diaryDate)
                .orElseGet(() -> newNote(user, diaryDate, now));
        note.setNote(request.getNote().trim());
        note.setUpdatedAt(now);
        return toDto(foodDiaryNoteRepository.save(note));
    }

    @Override
    public FoodDiaryNoteDto getNote(String email, LocalDate diaryDate) {
        UserEntity user = getUser(email);
        return foodDiaryNoteRepository.findByUserAndDiaryDate(user, diaryDate)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Food diary note not found"));
    }

    @Override
    @Transactional
    public void deleteNote(String email, LocalDate diaryDate) {
        UserEntity user = getUser(email);
        FoodDiaryNoteEntity note = foodDiaryNoteRepository.findByUserAndDiaryDate(user, diaryDate)
                .orElseThrow(() -> new ResourceNotFoundException("Food diary note not found"));
        foodDiaryNoteRepository.delete(note);
    }

    private FoodDiaryNoteEntity newNote(UserEntity user, LocalDate diaryDate, LocalDateTime now) {
        FoodDiaryNoteEntity note = new FoodDiaryNoteEntity();
        note.setUser(user);
        note.setDiaryDate(diaryDate);
        note.setCreatedAt(now);
        return note;
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private FoodDiaryNoteDto toDto(FoodDiaryNoteEntity entity) {
        FoodDiaryNoteDto dto = new FoodDiaryNoteDto();
        dto.setId(entity.getId());
        dto.setDiaryDate(entity.getDiaryDate());
        dto.setNote(entity.getNote());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
