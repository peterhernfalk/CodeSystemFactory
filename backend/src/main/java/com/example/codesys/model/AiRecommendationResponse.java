package com.example.codesys.model;

import java.util.List;

public record AiRecommendationResponse(
    List<Recommendation> recommendations,
    List<SuggestedTerm> suggestedAdditional
) {
    public record Recommendation(
        String inputTerm,
        String recommendedSnomedId,
        String recommendedTerm,
        String fsn,
        double confidence,
        String reason,
        String definition,
        List<String> relations
    ) {}
    
    public record SuggestedTerm(
        String snomedId,
        String term,
        String fsn,
        String reason,
        String definition,
        List<String> relations
    ) {}
}

