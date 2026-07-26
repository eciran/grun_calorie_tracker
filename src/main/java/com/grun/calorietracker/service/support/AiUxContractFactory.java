package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.AiUxContractDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.enums.AiClientLifecycleStatus;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.PreferredLanguage;

public final class AiUxContractFactory {

    private AiUxContractFactory() {
    }

    public static AiUxContractDto success(
            AiRequestStatus status,
            boolean confirmationRequired,
            int creditCost,
            SubscriptionDto quota,
            PreferredLanguage outputLanguage
    ) {
        AiUxContractDto contract = base(status, outputLanguage);
        contract.setReviewRequired(confirmationRequired);
        contract.setConfirmationRequired(confirmationRequired);
        contract.setRetryable(false);
        contract.setCreditCost(creditCost);
        contract.setCreditCharged(true);
        if (quota != null) {
            contract.setAiBaseRemainingThisPeriod(quota.getAiBaseRemainingThisPeriod());
            contract.setAiAddonRemainingThisPeriod(quota.getAiAddonRemainingThisPeriod());
            contract.setAiRemainingThisPeriod(quota.getAiRemainingThisPeriod());
        }
        return contract;
    }

    public static AiUxContractDto failure(
            boolean retryable,
            int creditCost,
            boolean creditCharged,
            PreferredLanguage outputLanguage
    ) {
        AiUxContractDto contract = base(AiRequestStatus.FAILED, outputLanguage);
        contract.setReviewRequired(false);
        contract.setConfirmationRequired(false);
        contract.setRetryable(retryable);
        contract.setCreditCost(creditCost);
        contract.setCreditCharged(creditCharged);
        return contract;
    }

    public static AiUxContractDto history(
            AiRequestStatus status,
            boolean confirmationRequired,
            int creditCost,
            boolean creditCharged,
            PreferredLanguage outputLanguage
    ) {
        AiUxContractDto contract = base(status, outputLanguage);
        contract.setReviewRequired(confirmationRequired && status == AiRequestStatus.DRAFT_CREATED);
        contract.setConfirmationRequired(confirmationRequired && status == AiRequestStatus.DRAFT_CREATED);
        contract.setRetryable(status == AiRequestStatus.FAILED);
        contract.setCreditCost(creditCost);
        contract.setCreditCharged(creditCharged);
        return contract;
    }

    public static AiClientLifecycleStatus lifecycle(AiRequestStatus status) {
        if (status == null) {
            return AiClientLifecycleStatus.PROCESSING;
        }
        return switch (status) {
            case PROCESSING -> AiClientLifecycleStatus.PROCESSING;
            case DRAFT_CREATED, CONFIRMED -> AiClientLifecycleStatus.COMPLETED;
            case REJECTED -> AiClientLifecycleStatus.REJECTED;
            case FAILED -> AiClientLifecycleStatus.FAILED;
        };
    }

    private static AiUxContractDto base(AiRequestStatus status, PreferredLanguage outputLanguage) {
        AiUxContractDto contract = new AiUxContractDto();
        contract.setLifecycleStatus(lifecycle(status));
        contract.setOutputLanguage(outputLanguage == null ? PreferredLanguage.EN : outputLanguage);
        return contract;
    }
}