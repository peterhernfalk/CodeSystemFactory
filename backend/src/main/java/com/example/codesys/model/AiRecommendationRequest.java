package com.example.codesys.model;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AiRecommendationRequest(
    @NotEmpty(message = "Unmatched terms list cannot be empty")
    List<String> unmatchedTerms,
    
    @NotEmpty(message = "Matched SNOMED IDs list cannot be empty")
    List<String> matchedSnomedIds,
    
    String context
) {}

