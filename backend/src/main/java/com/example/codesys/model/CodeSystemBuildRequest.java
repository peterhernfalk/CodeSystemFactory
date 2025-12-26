package com.example.codesys.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CodeSystemBuildRequest(
    @NotNull(message = "Metadata is required")
    @Valid
    CodeSystemMetadata metadata,
    
    List<MatchedTermForBuild> matchedTerms,
    
    List<RecommendedTermForBuild> recommendedTerms,
    
    List<SuggestedTermForBuild> suggestedTerms
) {
    public record CodeSystemMetadata(
        String name,
        String version,
        String description,
        String publisher,
        String contact
    ) {}
    
    public record MatchedTermForBuild(
        String inputTerm,
        String snomedId,
        String preferredTerm,
        String fsn,
        String description
    ) {}
    
    public record RecommendedTermForBuild(
        String inputTerm,
        String snomedId,
        String recommendedTerm,
        String fsn,
        String definition,
        List<String> relations
    ) {}
    
    public record SuggestedTermForBuild(
        String snomedId,
        String term,
        String fsn,
        String reason,
        String definition,
        List<String> relations
    ) {}
}

