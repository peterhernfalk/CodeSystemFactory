package com.example.codesys.service.impl;

import com.example.codesys.model.AiRecommendationResponse;
import com.example.codesys.model.AiRecommendationRequest;
import com.example.codesys.service.SnomedService;
import com.example.codesys.service.SynonymDatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldInvokeAiFallback_modeling_whenSourceTermsExistEvenIfPoolEmpty() {
        HybridRecommendationServiceImpl service = createService(true, true);
        boolean shouldInvoke = service.shouldInvokeAiFallback(
                AiRecommendationRequest.RecommendationMode.MODELING,
                List.of("Heart attack", "Fotledsfraktur"),
                List.of("22298006"),
                List.of()
        );
        assertTrue(shouldInvoke);
    }

    @Test
    void shouldInvokeAiFallback_modeling_whenOnlyMatchedIdsExist() {
        HybridRecommendationServiceImpl service = createService(true, true);
        boolean shouldInvoke = service.shouldInvokeAiFallback(
                AiRecommendationRequest.RecommendationMode.MODELING,
                List.of(),
                List.of("22298006"),
                List.of()
        );
        assertTrue(shouldInvoke);
    }

    @Test
    void parseModelingAiResponse_toleratesMissingOptionalFields() throws Exception {
        HybridRecommendationServiceImpl service = createService(true, true);
        String json = """
                {
                  "existingSnomedAdditions": [
                    {"snomedId":"123","pt":"Example","fsn":"Example (disorder)","whyAdd":"coverage","inputTerm":"exempel"}
                  ],
                  "candidateNewTerms": [
                    {
                      "proposedPt":"New local term",
                      "proposedFsn":"New local term (disorder)",
                      "semanticTag":"disorder",
                      "gapType":"lexical-gap",
                      "gapJustification":"Missing synonym coverage",
                      "inputTerm":"ny term",
                      "decision":"new"
                    }
                  ],
                  "modelingReviewChecklist": ["Review parents"]
                }
                """;

        AiRecommendationResponse parsed = service.parseModelingAiResponse(json);
        assertEquals(1, parsed.existingSnomedAdditions().size());
        assertEquals("123", parsed.existingSnomedAdditions().get(0).snomedId());
        assertEquals("exempel", parsed.existingSnomedAdditions().get(0).inputTerm());
        assertEquals(1, parsed.candidateNewTerms().size());
        assertEquals("New local term", parsed.candidateNewTerms().get(0).proposedPt());
        assertEquals("ny term", parsed.candidateNewTerms().get(0).inputTerm());
        assertEquals("new", parsed.candidateNewTerms().get(0).decision());
        assertFalse(parsed.candidateNewTerms().get(0).postcoordinationCandidate());
        assertEquals(1, parsed.modelingReviewChecklist().size());
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
    void extractJsonFromModelContent_stripsJsonFence() {
        String raw = "```json\n{\"recommendations\":[],\"suggestedAdditional\":[]}\n```";
        assertEquals("{\"recommendations\":[],\"suggestedAdditional\":[]}",
                HybridRecommendationServiceImpl.extractJsonFromModelContent(raw));
    }

    @Test
    void extractJsonFromModelContent_leavesRawJson() {
        String raw = "  {\"a\":1} ";
        assertEquals("{\"a\":1}", HybridRecommendationServiceImpl.extractJsonFromModelContent(raw));
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
                "https://r4.ontoserver.csiro.au/fhir",
                "MAIN",
                "sv",
                0.4,
                0.6,
                10,
                2,
                useAiFallback,
                aiEnabled,
                false
        );
    }
}
