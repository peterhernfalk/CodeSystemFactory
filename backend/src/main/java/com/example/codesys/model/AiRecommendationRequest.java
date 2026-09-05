package com.example.codesys.model;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AiRecommendationRequest(
    @NotNull(message = "Unmatched terms list cannot be null")
    List<String> unmatchedTerms,
    
    @NotNull(message = "Matched SNOMED IDs list cannot be null")
    List<String> matchedSnomedIds,
    
    String context,

    RecommendationMode recommendationMode
) {
    public enum RecommendationMode {
        UNMATCHED,
        ADDITIONAL,
        BOTH,
        MODELING
    }
}

