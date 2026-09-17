package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class AiCompletionNotificationQueryTest {
    @Autowired TestEntityManager em;
    @Autowired AiRequestHistoryRepository repository;

    @Test
    void onlyNewConfirmedCoachingIsEligibleAndRepeatedScanSkipsNotifiedResults() {
        UserEntity user = em.persist(new UserEntity());
        var daily = history(user, AiRequestType.AI_DAILY_INSIGHT, AiRequestStatus.CONFIRMED, true);
        var weekly = history(user, AiRequestType.AI_WEEKLY_INSIGHT, AiRequestStatus.CONFIRMED, true);
        var draft = history(user, AiRequestType.PHOTO_MEAL_LOG, AiRequestStatus.DRAFT_CREATED, false);
        var failed = history(user, AiRequestType.AI_DAILY_INSIGHT, AiRequestStatus.FAILED, true);
        history(user, AiRequestType.AI_DAILY_INSIGHT, AiRequestStatus.CONFIRMED, false);
        history(user, AiRequestType.AI_WEEKLY_INSIGHT, AiRequestStatus.CONFIRMED, false);
        history(user, AiRequestType.PHOTO_MEAL_LOG, AiRequestStatus.CONFIRMED, true);
        history(user, AiRequestType.AI_DAILY_INSIGHT, AiRequestStatus.PROCESSING, true);
        em.flush();
        var pending = repository.findPendingCompletionNotifications(
                List.of(AiRequestStatus.DRAFT_CREATED, AiRequestStatus.FAILED), PageRequest.of(0, 50));
        assertThat(pending).extracting(AiRequestHistoryEntity::getId)
                .containsExactlyInAnyOrder(daily.getId(), weekly.getId(), draft.getId(), failed.getId());
        pending.forEach(item -> item.setCompletionNotifiedAt(LocalDateTime.now()));
        em.flush();
        assertThat(repository.findPendingCompletionNotifications(
                List.of(AiRequestStatus.DRAFT_CREATED, AiRequestStatus.FAILED), PageRequest.of(0, 50))).isEmpty();
    }

    private AiRequestHistoryEntity history(UserEntity user, AiRequestType type, AiRequestStatus status, boolean eligible) {
        var history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(type);
        history.setStatus(status);
        history.setProvider(AiProvider.OPENAI);
        history.setModel("test");
        history.setPromptVersion("test");
        history.setCreatedAt(LocalDateTime.now());
        history.setCoachingCompletionNotificationEligible(eligible);
        return em.persist(history);
    }
}
