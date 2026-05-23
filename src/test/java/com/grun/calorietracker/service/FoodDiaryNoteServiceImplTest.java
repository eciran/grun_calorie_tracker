package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodDiaryNoteDto;
import com.grun.calorietracker.dto.FoodDiaryNoteRequestDto;
import com.grun.calorietracker.entity.FoodDiaryNoteEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodDiaryNoteRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.FoodDiaryNoteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodDiaryNoteServiceImplTest {

    @Mock
    private FoodDiaryNoteRepository foodDiaryNoteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FoodDiaryNoteServiceImpl service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
    }

    @Test
    void upsertNote_createsNewDailyNote() {
        FoodDiaryNoteRequestDto request = new FoodDiaryNoteRequestDto();
        request.setNote("  More protein tomorrow  ");
        LocalDate diaryDate = LocalDate.of(2026, 5, 23);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(foodDiaryNoteRepository.findByUserAndDiaryDate(user, diaryDate)).thenReturn(Optional.empty());
        when(foodDiaryNoteRepository.save(any(FoodDiaryNoteEntity.class))).thenAnswer(invocation -> {
            FoodDiaryNoteEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        FoodDiaryNoteDto result = service.upsertNote("user@example.com", diaryDate, request);

        assertEquals(10L, result.getId());
        assertEquals(diaryDate, result.getDiaryDate());
        assertEquals("More protein tomorrow", result.getNote());
    }

    @Test
    void getNote_whenMissing_throwsNotFound() {
        LocalDate diaryDate = LocalDate.of(2026, 5, 23);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(foodDiaryNoteRepository.findByUserAndDiaryDate(user, diaryDate)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getNote("user@example.com", diaryDate));
    }

    @Test
    void deleteNote_deletesOwnedDailyNote() {
        LocalDate diaryDate = LocalDate.of(2026, 5, 23);
        FoodDiaryNoteEntity note = new FoodDiaryNoteEntity();
        note.setId(10L);
        note.setUser(user);
        note.setDiaryDate(diaryDate);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(foodDiaryNoteRepository.findByUserAndDiaryDate(user, diaryDate)).thenReturn(Optional.of(note));

        service.deleteNote("user@example.com", diaryDate);

        verify(foodDiaryNoteRepository).delete(note);
    }

    @Test
    void upsertNote_whenUserMissing_throwsInvalidCredentials() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.upsertNote("missing@example.com", LocalDate.now(), new FoodDiaryNoteRequestDto()));
    }
}
