package com.example.codesys.controller;

import com.example.codesys.model.AiRecommendationResponse;
import com.example.codesys.service.AiRecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiRecommendationController.class)
class AiRecommendationControllerValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiRecommendationService recommendationService;

    @Test
    void recommend_acceptsEmptyUnmatchedTerms_whenMatchedIdsAreProvided() throws Exception {
        when(recommendationService.recommend(any()))
                .thenReturn(new AiRecommendationResponse(List.of(), List.of()));

        String payload = """
                {
                  "unmatchedTerms": [],
                  "matchedSnomedIds": ["123456"],
                  "context": "Swedish healthcare terminology"
                }
                """;

        mockMvc.perform(post("/api/ai/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isArray())
                .andExpect(jsonPath("$.suggestedAdditional").isArray());

        verify(recommendationService).recommend(any());
    }
}
