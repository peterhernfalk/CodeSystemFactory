package com.example.codesys.service;

import com.example.codesys.model.AiRecommendationRequest;
import com.example.codesys.model.AiRecommendationResponse;

public interface AiRecommendationService {
    AiRecommendationResponse recommend(AiRecommendationRequest request);
}

