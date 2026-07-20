package com.grun.calorietracker.dto;

public interface AiUsageMetadataCarrier {
    Integer getPromptTokens();
    void setPromptTokens(Integer promptTokens);

    Integer getCompletionTokens();
    void setCompletionTokens(Integer completionTokens);

    Integer getTotalTokens();
    void setTotalTokens(Integer totalTokens);

    Double getEstimatedCost();
    void setEstimatedCost(Double estimatedCost);

    String getCostCurrency();
    void setCostCurrency(String costCurrency);
}
