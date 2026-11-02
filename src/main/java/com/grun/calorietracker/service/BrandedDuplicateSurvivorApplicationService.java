package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseDto;
import com.grun.calorietracker.dto.BrandedDuplicateSearchCollapseRequestDto;
import com.grun.calorietracker.dto.BrandedDuplicateSurvivorApplyRequestDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateDecisionRequestDto;
import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandedDuplicateSurvivorApplicationService {

    private final BrandedProductDuplicateDecisionService decisionService;
    private final BrandedDuplicateSearchCollapseService collapseService;

    @Transactional
    @CacheEvict(cacheNames = "foodProductSearch", allEntries = true,
            cacheResolver = "foodProductCacheResolver")
    public BrandedDuplicateSearchCollapseDto selectAndApply(
            BrandedDuplicateSurvivorApplyRequestDto request,
            String actor
    ) {
        String reason = "Admin selected product #" + request.survivorProductId()
                + " as survivor and applied it to search.";
        decisionService.decide(new BrandedProductDuplicateDecisionRequestDto(
                request.brandKey(), request.nameKey(), BrandedDuplicateDecision.SURVIVOR_SELECTED,
                request.survivorProductId(), reason, request.candidateFingerprint()
        ), actor);
        return collapseService.apply(new BrandedDuplicateSearchCollapseRequestDto(
                request.brandKey(), request.nameKey(), request.candidateFingerprint(), reason
        ), actor);
    }
}
