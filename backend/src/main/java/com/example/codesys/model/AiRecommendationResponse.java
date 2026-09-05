package com.example.codesys.model;

import java.util.List;

public record AiRecommendationResponse(
    List<Recommendation> recommendations,
    List<SuggestedTerm> suggestedAdditional,
    List<ExistingSnomedAddition> existingSnomedAdditions,
    List<CandidateNewTerm> candidateNewTerms,
    List<String> modelingReviewChecklist
) {
    public AiRecommendationResponse(List<Recommendation> recommendations, List<SuggestedTerm> suggestedAdditional) {
        this(recommendations, suggestedAdditional, List.of(), List.of(), List.of());
    }

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

    public record ExistingSnomedAddition(
        String snomedId,
        String pt,
        String fsn,
        String whyAdd,
        List<String> relationsToExisting,
        double confidence,
        /** Source unmatched / clinical term this existing concept covers (MODELING). */
        String inputTerm
    ) {
        public ExistingSnomedAddition(
                String snomedId,
                String pt,
                String fsn,
                String whyAdd,
                List<String> relationsToExisting,
                double confidence) {
            this(snomedId, pt, fsn, whyAdd, relationsToExisting, confidence, "");
        }
    }

    public record CandidateNewTerm(
        String proposedPt,
        String proposedFsn,
        String semanticTag,
        String gapType,
        String gapJustification,
        List<ParentSuggestion> proximalPrimitiveParentSuggestions,
        List<DefiningAttribute> definingAttributes,
        boolean postcoordinationCandidate,
        List<String> exampleExpressions,
        List<String> synonymsSv,
        List<String> synonymsEn,
        String usageExample,
        String uncertaintyNotes,
        double confidence,
        /** Source unmatched / clinical term this candidate models (MODELING). */
        String inputTerm,
        /** Triage: existing | postcoordination | new */
        String decision
    ) {
        public CandidateNewTerm(
                String proposedPt,
                String proposedFsn,
                String semanticTag,
                String gapType,
                String gapJustification,
                List<ParentSuggestion> proximalPrimitiveParentSuggestions,
                List<DefiningAttribute> definingAttributes,
                boolean postcoordinationCandidate,
                List<String> exampleExpressions,
                List<String> synonymsSv,
                List<String> synonymsEn,
                String usageExample,
                String uncertaintyNotes,
                double confidence) {
            this(proposedPt, proposedFsn, semanticTag, gapType, gapJustification,
                    proximalPrimitiveParentSuggestions, definingAttributes, postcoordinationCandidate,
                    exampleExpressions, synonymsSv, synonymsEn, usageExample, uncertaintyNotes,
                    confidence, "", "");
        }
    }

    public record ParentSuggestion(
        String snomedId,
        String term
    ) {}

    public record DefiningAttribute(
        String attribute,
        String value,
        String valueSnomedId
    ) {}
}

