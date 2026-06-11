package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventDetailDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventPageDto;
import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.service.RevenueCatWebhookService;
import com.grun.calorietracker.service.SubscriptionProviderEventAdminService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionProviderEventAdminServiceImpl implements SubscriptionProviderEventAdminService {

    private final SubscriptionProviderEventRepository eventRepository;
    private final RevenueCatWebhookService revenueCatWebhookService;

    @Override
    @Transactional(readOnly = true)
    public SubscriptionProviderEventPageDto getEvents(SubscriptionProviderEventStatus status, String eventType, String productId, Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id"))
        );
        Page<SubscriptionProviderEventEntity> events = eventRepository.findAll(buildSpecification(status, eventType, productId, userId), pageable);

        SubscriptionProviderEventPageDto dto = new SubscriptionProviderEventPageDto();
        dto.setContent(events.getContent().stream().map(this::toDto).toList());
        dto.setPage(events.getNumber());
        dto.setSize(events.getSize());
        dto.setTotalElements(events.getTotalElements());
        dto.setTotalPages(events.getTotalPages());
        dto.setFirst(events.isFirst());
        dto.setLast(events.isLast());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionProviderEventPageDto getUserHistory(Long userId, int page, int size) {
        return getEvents(null, null, null, userId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionProviderEventDetailDto getEvent(Long id) {
        return toDetailDto(eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription provider event not found")));
    }

    @Override
    public RevenueCatWebhookResponseDto retryEvent(Long id) {
        return revenueCatWebhookService.retryStoredEvent(id);
    }

    private Specification<SubscriptionProviderEventEntity> buildSpecification(SubscriptionProviderEventStatus status, String eventType, String productId, Long userId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("eventType")), eventType.trim().toUpperCase()));
            }
            if (productId != null && !productId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("productId"), productId.trim()));
            }
            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SubscriptionProviderEventDto toDto(SubscriptionProviderEventEntity event) {
        SubscriptionProviderEventDto dto = new SubscriptionProviderEventDto();
        dto.setId(event.getId());
        dto.setProvider(event.getProvider());
        dto.setProviderEventId(event.getProviderEventId());
        dto.setProviderAppUserId(event.getProviderAppUserId());
        dto.setEventType(event.getEventType());
        dto.setProductId(event.getProductId());
        dto.setEntitlementIds(event.getEntitlementIds());
        dto.setTransactionId(event.getTransactionId());
        dto.setOriginalTransactionId(event.getOriginalTransactionId());
        if (event.getUser() != null) {
            dto.setUserId(event.getUser().getId());
            dto.setUserEmail(event.getUser().getEmail());
        }
        dto.setStatus(event.getStatus());
        dto.setProcessingError(event.getProcessingError());
        dto.setReceivedAt(event.getReceivedAt());
        dto.setProcessedAt(event.getProcessedAt());
        return dto;
    }

    private SubscriptionProviderEventDetailDto toDetailDto(SubscriptionProviderEventEntity event) {
        SubscriptionProviderEventDetailDto dto = new SubscriptionProviderEventDetailDto();
        SubscriptionProviderEventDto base = toDto(event);
        dto.setId(base.getId());
        dto.setProvider(base.getProvider());
        dto.setProviderEventId(base.getProviderEventId());
        dto.setProviderAppUserId(base.getProviderAppUserId());
        dto.setEventType(base.getEventType());
        dto.setProductId(base.getProductId());
        dto.setEntitlementIds(base.getEntitlementIds());
        dto.setTransactionId(base.getTransactionId());
        dto.setOriginalTransactionId(base.getOriginalTransactionId());
        dto.setUserId(base.getUserId());
        dto.setUserEmail(base.getUserEmail());
        dto.setStatus(base.getStatus());
        dto.setProcessingError(base.getProcessingError());
        dto.setReceivedAt(base.getReceivedAt());
        dto.setProcessedAt(base.getProcessedAt());
        dto.setRawPayload(event.getRawPayload());
        return dto;
    }
}
