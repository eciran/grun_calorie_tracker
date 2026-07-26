package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.AccountSecurityAuditEventEntity;
import com.grun.calorietracker.enums.AccountSecurityEventType;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.repository.AccountSecurityAuditEventRepository;
import com.grun.calorietracker.service.AccountSecurityAuditService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountSecurityAuditServiceImpl implements AccountSecurityAuditService {
    private final AccountSecurityAuditEventRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, AccountSecurityEventType eventType, AuthProvider provider, String resultCode) {
        if (userId == null) {
            return;
        }
        AccountSecurityAuditEventEntity event = new AccountSecurityAuditEventEntity();
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setProvider(provider);
        event.setResultCode(resultCode);
        event.setCorrelationId(MDC.get("correlationId"));
        event.setCreatedAt(LocalDateTime.now());
        repository.save(event);
    }
}
