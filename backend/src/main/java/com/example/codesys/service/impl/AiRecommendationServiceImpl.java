package com.example.codesys.service.impl;

import com.example.codesys.model.AiRecommendationRequest;
import com.example.codesys.model.AiRecommendationResponse;
import com.example.codesys.service.AiRecommendationService;
import com.example.codesys.service.HybridRecommendationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiRecommendationServiceImpl implements AiRecommendationService {

    private final HybridRecommendationService hybridService;
    private final ChatClient chatClient;
    private final boolean enabled;
    private final boolean useHybrid;

    public AiRecommendationServiceImpl(
            HybridRecommendationService hybridService,
            @Value("${ai.enabled:true}") boolean enabled,
            @Value("${recommendation.use-hybrid:true}") boolean useHybrid,
            ChatClient.Builder chatClientBuilder) {
        this.hybridService = hybridService;
        this.enabled = enabled;
        this.useHybrid = useHybrid;
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public AiRecommendationResponse recommend(AiRecommendationRequest request) {
        // Use hybrid approach by default, fallback to pure AI if disabled
        if (useHybrid) {
            return hybridService.recommend(request);
        }
        
        // Pure AI approach (original implementation)
        if (!enabled) {
            return createMockResponse(request);
        }

        String prompt = buildRecommendationPrompt(request);
        String content = this.chatClient.prompt(prompt).call().content();
        
        try {
            return parseJsonResponse(content, request);
        } catch (Exception e) {
            return createMockResponse(request);
        }
    }

    private String buildRecommendationPrompt(AiRecommendationRequest req) {
        String unmatchedList = req.unmatchedTerms().stream()
                .map(t -> "\"" + t + "\"")
                .collect(Collectors.joining(", "));
        
        String matchedList = req.matchedSnomedIds().stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(", "));
        
        String context = req.context() != null ? req.context() : "Swedish healthcare terminology";
        
        return ""
            + "You are a clinical terminology assistant helping build a code system.\n"
            + "Given unmatched terms and existing matched SNOMED CT codes, recommend:\n"
            + "1. SNOMED codes for unmatched terms\n"
            + "2. Additional complementary SNOMED codes that enhance the code system\n\n"
            + "Return JSON with this structure:\n"
            + "{\n"
            + "  \"recommendations\": [\n"
            + "    {\n"
            + "      \"inputTerm\": \"<unmatched term>\",\n"
            + "      \"recommendedSnomedId\": \"<SNOMED ID>\",\n"
            + "      \"recommendedTerm\": \"<term name>\",\n"
            + "      \"fsn\": \"<fully specified name>\",\n"
            + "      \"confidence\": 0.85,\n"
            + "      \"reason\": \"<explanation>\",\n"
            + "      \"definition\": \"<definition>\",\n"
            + "      \"relations\": [\"is-a: Disorder\", ...]\n"
            + "    }\n"
            + "  ],\n"
            + "  \"suggestedAdditional\": [\n"
            + "    {\n"
            + "      \"snomedId\": \"<SNOMED ID>\",\n"
            + "      \"term\": \"<term name>\",\n"
            + "      \"fsn\": \"<fully specified name>\",\n"
            + "      \"reason\": \"<why suggested>\",\n"
            + "      \"definition\": \"<definition>\",\n"
            + "      \"relations\": [\"is-a: Disorder\", ...]\n"
            + "    }\n"
            + "  ]\n"
            + "}\n"
            + "Only output valid JSON.\n"
            + "Context: " + context + "\n"
            + "Unmatched terms: [" + unmatchedList + "]\n"
            + "Matched SNOMED IDs: [" + matchedList + "]\n";
    }

    private AiRecommendationResponse parseJsonResponse(String content, AiRecommendationRequest request) {
        try {
            com.fasterxml.jackson.databind.json.JsonMapper mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
            return mapper.readValue(content, AiRecommendationResponse.class);
        } catch (Exception e) {
            return createMockResponse(request);
        }
    }

    private AiRecommendationResponse createMockResponse(AiRecommendationRequest request) {
        List<AiRecommendationResponse.Recommendation> recommendations = new ArrayList<>();
        for (String term : request.unmatchedTerms()) {
            recommendations.add(new AiRecommendationResponse.Recommendation(
                term,
                "MOCK_" + term.hashCode(),
                "Mock recommended term for " + term,
                "Mock term (disorder)",
                0.75,
                "Mock recommendation based on context",
                "Mock definition",
                List.of("is-a: Disorder")
            ));
        }
        
        List<AiRecommendationResponse.SuggestedTerm> suggested = new ArrayList<>();
        if (!request.matchedSnomedIds().isEmpty()) {
            suggested.add(new AiRecommendationResponse.SuggestedTerm(
                "MOCK_COMPLEMENTARY",
                "Complementary concept",
                "Complementary concept (disorder)",
                "Complements matched terms",
                "Mock definition",
                List.of("is-a: Disorder")
            ));
        }
        
        return new AiRecommendationResponse(recommendations, suggested);
    }
}

