package com.example.codesys.service.impl;

import com.example.codesys.model.AiRecommendationResponse;
import com.example.codesys.model.AiRecommendationRequest;
import com.example.codesys.service.SnomedService;
import com.example.codesys.service.SynonymDatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRecommendationServiceImplOptionATest {

    @Test
    void normalizeMode_defaultsToBothWhenNull() {
        HybridRecommendationServiceImpl service = createService(true, true);
        assertTrue(service.normalizeMode(null) == AiRecommendationRequest.RecommendationMode.BOTH);
    }

    @Test
    void normalizeMode_keepsProvidedMode() {
        HybridRecommendationServiceImpl service = createService(true, true);
        assertTrue(service.normalizeMode(AiRecommendationRequest.RecommendationMode.UNMATCHED)
                == AiRecommendationRequest.RecommendationMode.UNMATCHED);
    }

    @Test
    void shouldInvokeAiFallback_whenUnmatchedIsEmptyButAdditionalPoolExists() {
        HybridRecommendationServiceImpl service = createService(true, true);
        List<AiRecommendationResponse.SuggestedTerm> additionalPool = List.of(
                new AiRecommendationResponse.SuggestedTerm(
                        "123456", "Example term", "Example FSN", "reason", "definition", List.of()
                )
        );

        boolean shouldInvoke = service.shouldInvokeAiFallback(List.of(), additionalPool);

        assertTrue(shouldInvoke);
    }

    @Test
    void shouldInvokeAiFallback_whenFallbackDisabled_returnsFalse() {
        HybridRecommendationServiceImpl service = createService(false, true);
        List<AiRecommendationResponse.SuggestedTerm> additionalPool = List.of(
                new AiRecommendationResponse.SuggestedTerm(
                        "123456", "Example term", "Example FSN", "reason", "definition", List.of()
                )
        );

        boolean shouldInvoke = service.shouldInvokeAiFallback(List.of(), additionalPool);

        assertFalse(shouldInvoke);
    }

    @Test
    void shouldInvokeAiFallback_whenAiDisabled_returnsFalse() {
        HybridRecommendationServiceImpl service = createService(true, false);
        List<AiRecommendationResponse.SuggestedTerm> additionalPool = List.of(
                new AiRecommendationResponse.SuggestedTerm(
                        "123456", "Example term", "Example FSN", "reason", "definition", List.of()
                )
        );

        boolean shouldInvoke = service.shouldInvokeAiFallback(List.of(), additionalPool);

        assertFalse(shouldInvoke);
    }

    private HybridRecommendationServiceImpl createService(boolean useAiFallback, boolean aiEnabled) {
        SnomedService snomedService = mock(SnomedService.class);
        SynonymDatabaseService synonymDatabase = mock(SynonymDatabaseService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        return new HybridRecommendationServiceImpl(
                snomedService,
                synonymDatabase,
                builder,
                "https://snowstorm-training.snomedtools.org/fhir",
                "MAIN",
                "sv",
                0.4,
                0.6,
                10,
                2,
                useAiFallback,
                aiEnabled
        );
    }
}
