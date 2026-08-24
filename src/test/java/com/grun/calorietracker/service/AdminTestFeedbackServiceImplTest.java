package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminTestFeedbackUpdateRequestDto;
import com.grun.calorietracker.entity.TestFeedbackSubmissionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.TestFeedbackSubmissionRepository;
import com.grun.calorietracker.repository.TestFeedbackScreenshotEventRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AdminTestFeedbackServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminTestFeedbackServiceImplTest {
    @Mock TestFeedbackSubmissionRepository repository;
    @Mock UserRepository userRepository;
    @Mock AdminAuditService auditService;

    @Test
    void updateTracksReviewerAndWritesAuditRecord() {
        UserEntity tester = user(3L, "tester@example.com");
        UserEntity admin = user(9L, "admin@example.com");
        TestFeedbackSubmissionEntity feedback = new TestFeedbackSubmissionEntity();
        feedback.setId(11L);
        feedback.setUser(tester);
        feedback.setFeedbackType(TestFeedbackType.PROBLEM);
        feedback.setStatus(TestFeedbackStatus.NEW);
        feedback.setPlatform(TestFeedbackPlatform.IOS);
        feedback.setRoute("/progress");
        when(repository.findById(11L)).thenReturn(Optional.of(feedback));
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(repository.save(feedback)).thenReturn(feedback);

        AdminTestFeedbackServiceImpl service = new AdminTestFeedbackServiceImpl(repository, userRepository, auditService,
                mock(TestFeedbackScreenshotEventRepository.class));
        var result = service.update(11L,
                new AdminTestFeedbackUpdateRequestDto(TestFeedbackStatus.REVIEWING, "Reproducing"),
                admin.getEmail(), "cid-11");

        assertThat(result.status()).isEqualTo(TestFeedbackStatus.REVIEWING);
        assertThat(result.adminNote()).isEqualTo("Reproducing");
        assertThat(feedback.getReviewedBy()).isSameAs(admin);
        verify(auditService).record(eq(admin.getEmail()), any(), any(), eq("test-feedback:11"),
                any(), any(), eq("cid-11"));
    }

    private UserEntity user(Long id, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        return user;
    }
}
