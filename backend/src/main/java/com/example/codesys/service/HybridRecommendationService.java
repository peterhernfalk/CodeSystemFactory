package com.example.codesys.service;

import com.example.codesys.model.AiRecommendationRequest;
import com.example.codesys.model.AiRecommendationResponse;

/**
 * Hybrid recommendation service that combines multiple strategies:
 * 1. Enhanced fuzzy matching (candidates with 0.4-0.6 similarity)
 * 2. Hierarchical search (parent/child/sibling concepts)
 * 3. Synonym database (curated Swedish→SNOMED mappings)
 * 4. AI agent (fallback for complex cases)
 */
public interface HybridRecommendationService {
    AiRecommendationResponse recommend(AiRecommendationRequest request);
}

