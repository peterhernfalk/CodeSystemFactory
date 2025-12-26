package com.example.codesys.controller;

import com.example.codesys.model.AiRecommendationRequest;
import com.example.codesys.model.AiRecommendationResponse;
import com.example.codesys.service.AiRecommendationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin
public class AiRecommendationController {
    private final AiRecommendationService recommendationService;

    public AiRecommendationController(AiRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping(value="/recommend", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public AiRecommendationResponse recommend(@Valid @RequestBody AiRecommendationRequest request) {
        return recommendationService.recommend(request);
    }
}

