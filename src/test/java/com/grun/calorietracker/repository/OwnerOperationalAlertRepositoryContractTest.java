package com.grun.calorietracker.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OwnerOperationalAlertRepositoryContractTest {

    @Test
    void dueDeliveryRowsUseDatabaseWriteLock() throws Exception {
        var method = OwnerOperationalAlertRepository.class.getMethod(
                "findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc",
                Collection.class, Instant.class, org.springframework.data.domain.Pageable.class);
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock, "Delivery selection must lock rows before sending email.");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }
}
