package com.example.codesys.service.impl;

import com.example.codesys.model.AiRecommendationRequest;
import com.example.codesys.model.AiRecommendationResponse;
import com.example.codesys.model.TermMatch;
import com.example.codesys.service.*;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid recommendation service that combines multiple strategies:
 * 1. Enhanced fuzzy matching (candidates with 0.4-0.6 similarity)
 * 2. Hierarchical search (parent/child/sibling concepts)
 * 3. Synonym database (curated Swedish→SNOMED mappings)
 * 4. AI agent (fallback for complex cases)
 */
@Service
public class HybridRecommendationServiceImpl implements HybridRecommendationService {
    
    private final SnomedService snomedService;
    private final SynonymDatabaseService synonymDatabase;
    private final ChatClient chatClient;
    private final RestTemplate rest;
    private final JaroWinklerSimilarity jw = new JaroWinklerSimilarity();
    
    private final String snowstormBaseUrl;
    private final String branch;
    private final String language;
    
    // Configuration
    private final double fuzzyMatchMinSimilarity;
    private final double fuzzyMatchMaxSimilarity;
    private final int maxHierarchicalResults;
    private final boolean useAiFallback;
    private final boolean aiEnabled;
    
    public HybridRecommendationServiceImpl(
            SnomedService snomedService,
            SynonymDatabaseService synonymDatabase,
            ChatClient.Builder chatClientBuilder,
            @Value("${fhir.server.url:}") String fhirServerUrl,
            @Value("${snomed.branch:MAIN}") String branch,
            @Value("${snomed.language:en}") String language,
            @Value("${recommendation.fuzzy-match.min-similarity:0.4}") double fuzzyMatchMinSimilarity,
            @Value("${recommendation.fuzzy-match.max-similarity:0.6}") double fuzzyMatchMaxSimilarity,
            @Value("${recommendation.hierarchical.max-results:10}") int maxHierarchicalResults,
            @Value("${recommendation.ai-fallback:true}") boolean useAiFallback,
            @Value("${ai.enabled:true}") boolean aiEnabled) {
        
        this.snomedService = snomedService;
        this.synonymDatabase = synonymDatabase;
        this.chatClient = chatClientBuilder.build();
        this.branch = branch;
        this.language = language;
        this.fuzzyMatchMinSimilarity = fuzzyMatchMinSimilarity;
        this.fuzzyMatchMaxSimilarity = fuzzyMatchMaxSimilarity;
        this.maxHierarchicalResults = maxHierarchicalResults;
        this.useAiFallback = useAiFallback;
        this.aiEnabled = aiEnabled;
        
        // Extract base URL
        String baseUrl = fhirServerUrl == null ? "" : fhirServerUrl.trim();
        if (baseUrl.isEmpty()) {
            this.snowstormBaseUrl = "https://snowstorm-training.snomedtools.org";
        } else if (baseUrl.contains("/fhir")) {
            this.snowstormBaseUrl = baseUrl.replace("/fhir", "").replaceAll("/$", "");
        } else {
            this.snowstormBaseUrl = baseUrl.replaceAll("/$", "");
        }
        
        this.rest = new RestTemplate();
    }
    
    @Override
    public AiRecommendationResponse recommend(AiRecommendationRequest request) {
        List<AiRecommendationResponse.Recommendation> allRecommendations = new ArrayList<>();
        List<AiRecommendationResponse.SuggestedTerm> allSuggested = new ArrayList<>();
        
        // Strategy 1: Synonym Database Lookup
        Map<String, CandidateRecommendation> synonymCandidates = findViaSynonymDatabase(request.unmatchedTerms());
        allRecommendations.addAll(convertToRecommendations(synonymCandidates, "Synonym database match"));
        
        // Strategy 2: Enhanced Fuzzy Matching (candidates with 0.4-0.6 similarity)
        Map<String, CandidateRecommendation> fuzzyCandidates = findViaFuzzyMatching(request.unmatchedTerms());
        allRecommendations.addAll(convertToRecommendations(fuzzyCandidates, "Fuzzy match (similarity 0.4-0.6)"));
        
        // Strategy 3: Hierarchical Search (parent/child/sibling of matched concepts)
        if (!request.matchedSnomedIds().isEmpty()) {
            List<AiRecommendationResponse.SuggestedTerm> hierarchicalSuggestions = 
                    findViaHierarchicalSearch(request.matchedSnomedIds(), request.unmatchedTerms());
            allSuggested.addAll(hierarchicalSuggestions);
        }
        
        // Strategy 4: AI Agent (fallback for unmatched terms)
        List<String> stillUnmatched = request.unmatchedTerms().stream()
                .filter(term -> !synonymCandidates.containsKey(term.toLowerCase()) 
                             && !fuzzyCandidates.containsKey(term.toLowerCase()))
                .collect(Collectors.toList());
        
        System.out.println("DEBUG: Hybrid recommendation - stillUnmatched: " + stillUnmatched);
        System.out.println("DEBUG: Hybrid recommendation - useAiFallback: " + useAiFallback + ", aiEnabled: " + aiEnabled);
        
        if (!stillUnmatched.isEmpty() && useAiFallback && aiEnabled) {
            System.out.println("DEBUG: Calling AI fallback for terms: " + stillUnmatched);
            AiRecommendationResponse aiResponse = findViaAiFallback(stillUnmatched, request.matchedSnomedIds(), request.context());
            allRecommendations.addAll(aiResponse.recommendations());
            allSuggested.addAll(aiResponse.suggestedAdditional());
        } else if (!stillUnmatched.isEmpty()) {
            // If AI is disabled, still provide mock recommendations so user sees something
            System.out.println("DEBUG: AI disabled, creating mock recommendations for: " + stillUnmatched);
            AiRecommendationResponse mockResponse = createMockAiResponse(stillUnmatched, request.matchedSnomedIds());
            allRecommendations.addAll(mockResponse.recommendations());
            allSuggested.addAll(mockResponse.suggestedAdditional());
        }
        
        // Deduplicate and rank recommendations
        List<AiRecommendationResponse.Recommendation> deduplicatedRecs = deduplicateAndRank(allRecommendations);
        List<AiRecommendationResponse.SuggestedTerm> deduplicatedSuggested = deduplicateSuggested(allSuggested);
        
        return new AiRecommendationResponse(deduplicatedRecs, deduplicatedSuggested);
    }
    
    /**
     * Strategy 1: Synonym Database Lookup
     */
    private Map<String, CandidateRecommendation> findViaSynonymDatabase(List<String> unmatchedTerms) {
        Map<String, CandidateRecommendation> candidates = new HashMap<>();
        
        for (String term : unmatchedTerms) {
            // Direct lookup
            Optional<String> snomedId = synonymDatabase.lookup(term);
            if (snomedId.isPresent()) {
                CandidateRecommendation candidate = fetchConceptDetails(snomedId.get(), term);
                if (candidate != null) {
                    candidates.put(term.toLowerCase(), new CandidateRecommendation(
                            candidate.snomedId(), candidate.preferredTerm(), candidate.fsn(),
                            candidate.similarity(), candidate.source(), term));
                    continue;
                }
            }
            
            // Try synonyms
            List<String> synonyms = synonymDatabase.getSynonyms(term);
            for (String synonym : synonyms) {
                snomedId = synonymDatabase.lookup(synonym);
                if (snomedId.isPresent()) {
                    CandidateRecommendation candidate = fetchConceptDetails(snomedId.get(), term);
                    if (candidate != null) {
                        candidates.put(term.toLowerCase(), new CandidateRecommendation(
                                candidate.snomedId(), candidate.preferredTerm(), candidate.fsn(),
                                candidate.similarity(), candidate.source(), term));
                        break;
                    }
                }
            }
        }
        
        return candidates;
    }
    
    /**
     * Strategy 2: Enhanced Fuzzy Matching (find candidates with similarity 0.4-0.6)
     */
    private Map<String, CandidateRecommendation> findViaFuzzyMatching(List<String> unmatchedTerms) {
        Map<String, CandidateRecommendation> candidates = new HashMap<>();
        
        for (String term : unmatchedTerms) {
            // Search with lower threshold to get candidates
            List<TermMatch> matches = snomedService.matchTerms(List.of(term));
            if (!matches.isEmpty()) {
                TermMatch match = matches.get(0);
                System.out.println("DEBUG: Fuzzy matching for '" + term + "': similarity=" + match.similarity() + 
                        ", matchedSctId=" + match.matchedSctId() + ", status=" + match.status());
                
                // Accept if similarity is in the fuzzy range (0.4-0.6)
                // Also accept if we found a match but similarity is below threshold (might be a valid match)
                if (match.matchedSctId() != null && 
                        match.similarity() >= fuzzyMatchMinSimilarity 
                        && match.similarity() < fuzzyMatchMaxSimilarity) {
                    CandidateRecommendation candidate = new CandidateRecommendation(
                            match.matchedSctId(),
                            match.preferredTermSv() != null ? match.preferredTermSv() : "Unknown",
                            match.fsnSv() != null ? match.fsnSv() : "Unknown",
                            match.similarity(),
                            "Fuzzy match",
                            term
                    );
                    candidates.put(term.toLowerCase(), candidate);
                    System.out.println("DEBUG: Added fuzzy match candidate for '" + term + "'");
                } else if (match.matchedSctId() != null && match.similarity() > 0.0 && match.similarity() < fuzzyMatchMinSimilarity) {
                    // Very low similarity but still found something - include it with lower confidence
                    System.out.println("DEBUG: Found very low similarity match for '" + term + "' (similarity=" + match.similarity() + "), including as candidate");
                    CandidateRecommendation candidate = new CandidateRecommendation(
                            match.matchedSctId(),
                            match.preferredTermSv() != null ? match.preferredTermSv() : "Unknown",
                            match.fsnSv() != null ? match.fsnSv() : "Unknown",
                            match.similarity(),
                            "Low similarity match",
                            term
                    );
                    candidates.put(term.toLowerCase(), candidate);
                }
            } else {
                System.out.println("DEBUG: No matches found for '" + term + "' in fuzzy matching");
            }
        }
        
        return candidates;
    }
    
    /**
     * Strategy 3: Hierarchical Search - find parent/child/sibling concepts
     */
    private List<AiRecommendationResponse.SuggestedTerm> findViaHierarchicalSearch(
            List<String> matchedSnomedIds, List<String> unmatchedTerms) {
        
        List<AiRecommendationResponse.SuggestedTerm> suggestions = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        for (String matchedId : matchedSnomedIds) {
            // Get parent concepts
            List<String> parents = getParentConcepts(matchedId);
            for (String parentId : parents) {
                if (!visited.contains(parentId) && suggestions.size() < maxHierarchicalResults) {
                    CandidateRecommendation candidate = fetchConceptDetails(parentId, null);
                    if (candidate != null) {
                        suggestions.add(new AiRecommendationResponse.SuggestedTerm(
                                candidate.snomedId(),
                                candidate.preferredTerm(),
                                candidate.fsn(),
                                "Parent concept of matched term " + matchedId,
                                "Related concept from SNOMED hierarchy",
                                List.of("is-a: " + matchedId)
                        ));
                        visited.add(parentId);
                    }
                }
            }
            
            // Get child concepts
            List<String> children = getChildConcepts(matchedId);
            for (String childId : children) {
                if (!visited.contains(childId) && suggestions.size() < maxHierarchicalResults) {
                    CandidateRecommendation candidate = fetchConceptDetails(childId, null);
                    if (candidate != null) {
                        suggestions.add(new AiRecommendationResponse.SuggestedTerm(
                                candidate.snomedId(),
                                candidate.preferredTerm(),
                                candidate.fsn(),
                                "Child concept of matched term " + matchedId,
                                "Related concept from SNOMED hierarchy",
                                List.of("is-a: " + matchedId)
                        ));
                        visited.add(childId);
                    }
                }
            }
        }
        
        return suggestions;
    }
    
    /**
     * Fetch full concept details from Snowstorm
     */
    private CandidateRecommendation fetchConceptDetails(String conceptId, String inputTerm) {
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(snowstormBaseUrl)
                    .path("/snowstorm/snomed-ct")
                    .pathSegment(branch, "concepts", conceptId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (language != null) {
                headers.setAcceptLanguage(java.util.Locale.LanguageRange.parse(language));
            }
            HttpEntity<Void> req = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    urlBuilder.toUriString(), HttpMethod.GET, req,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Map<String, Object> concept = resp.getBody();
                
                String preferredTerm = extractPreferredTerm(concept);
                String fsn = extractFSN(concept);
                
                // Calculate similarity if input term provided
                double similarity = 1.0;
                if (inputTerm != null) {
                    String inputLower = inputTerm.toLowerCase();
                    String ptLower = preferredTerm.toLowerCase();
                    similarity = jw.apply(inputLower, ptLower);
                }
                
                return new CandidateRecommendation(conceptId, preferredTerm, fsn, similarity, "Concept lookup", inputTerm);
            }
        } catch (Exception e) {
            System.err.println("Error fetching concept " + conceptId + ": " + e.getMessage());
        }
        
        return null;
    }
    
    private String extractPreferredTerm(Map<String, Object> concept) {
        Object ptObj = concept.get("pt");
        if (ptObj instanceof Map<?, ?>) {
            String term = (String) ((Map<?, ?>) ptObj).get("term");
            if (term != null) return term;
        }
        
        // Fallback to FSN
        Object fsnObj = concept.get("fsn");
        if (fsnObj instanceof Map<?, ?>) {
            String term = (String) ((Map<?, ?>) fsnObj).get("term");
            if (term != null) return term;
        }
        
        return "Unknown term";
    }
    
    private String extractFSN(Map<String, Object> concept) {
        Object fsnObj = concept.get("fsn");
        if (fsnObj instanceof Map<?, ?>) {
            String term = (String) ((Map<?, ?>) fsnObj).get("term");
            if (term != null) return term;
        }
        return "Unknown FSN";
    }
    
    /**
     * Get parent concepts (broader concepts)
     */
    private List<String> getParentConcepts(String conceptId) {
        return getRelatedConcepts(conceptId, "parents", 5);
    }
    
    /**
     * Get child concepts (narrower concepts)
     */
    private List<String> getChildConcepts(String conceptId) {
        return getRelatedConcepts(conceptId, "children", 5);
    }
    
    private List<String> getRelatedConcepts(String conceptId, String relationType, int limit) {
        List<String> concepts = new ArrayList<>();
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(snowstormBaseUrl)
                    .path("/snowstorm/snomed-ct")
                    .pathSegment(branch, "concepts", conceptId, relationType)
                    .queryParam("limit", limit)
                    .queryParam("activeFilter", "true");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    urlBuilder.toUriString(), HttpMethod.GET, req,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object items = resp.getBody().get("items");
                if (items instanceof List) {
                    for (Object item : (List<?>) items) {
                        if (item instanceof Map<?, ?>) {
                            String id = (String) ((Map<?, ?>) item).get("conceptId");
                            if (id != null) {
                                concepts.add(id);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching " + relationType + " for " + conceptId + ": " + e.getMessage());
        }
        
        return concepts;
    }
    
    /**
     * Convert candidate recommendations to API response format
     */
    private List<AiRecommendationResponse.Recommendation> convertToRecommendations(
            Map<String, CandidateRecommendation> candidates, String reasonPrefix) {
        
        List<AiRecommendationResponse.Recommendation> result = new ArrayList<>();
        for (Map.Entry<String, CandidateRecommendation> entry : candidates.entrySet()) {
            CandidateRecommendation candidate = entry.getValue();
            result.add(new AiRecommendationResponse.Recommendation(
                    candidate.inputTerm() != null ? candidate.inputTerm() : entry.getKey(),
                    candidate.snomedId(),
                    candidate.preferredTerm(),
                    candidate.fsn(),
                    candidate.similarity(),
                    reasonPrefix + " - similarity: " + String.format("%.2f", candidate.similarity()),
                    "SNOMED CT concept",
                    List.of("is-a: Concept")
            ));
        }
        return result;
    }
    
    /**
     * Deduplicate and rank recommendations by confidence
     */
    private List<AiRecommendationResponse.Recommendation> deduplicateAndRank(
            List<AiRecommendationResponse.Recommendation> recommendations) {
        
        Map<String, AiRecommendationResponse.Recommendation> bestBySnomedId = new HashMap<>();
        
        for (AiRecommendationResponse.Recommendation rec : recommendations) {
            String key = rec.recommendedSnomedId();
            if (!bestBySnomedId.containsKey(key) || 
                    rec.confidence() > bestBySnomedId.get(key).confidence()) {
                bestBySnomedId.put(key, rec);
            }
        }
        
        return bestBySnomedId.values().stream()
                .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
                .collect(Collectors.toList());
    }
    
    private List<AiRecommendationResponse.SuggestedTerm> deduplicateSuggested(
            List<AiRecommendationResponse.SuggestedTerm> suggested) {
        
        Map<String, AiRecommendationResponse.SuggestedTerm> unique = new HashMap<>();
        for (AiRecommendationResponse.SuggestedTerm term : suggested) {
            unique.putIfAbsent(term.snomedId(), term);
        }
        
        return new ArrayList<>(unique.values());
    }
    
    /**
     * Strategy 4: AI Agent Fallback
     */
    private AiRecommendationResponse findViaAiFallback(
            List<String> unmatchedTerms, List<String> matchedSnomedIds, String context) {
        
        try {
            String prompt = buildAiPrompt(unmatchedTerms, matchedSnomedIds, context);
            String content = this.chatClient.prompt(prompt).call().content();
            
            try {
                com.fasterxml.jackson.databind.json.JsonMapper mapper = 
                        com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
                return mapper.readValue(content, AiRecommendationResponse.class);
            } catch (Exception e) {
                System.err.println("Error parsing AI response: " + e.getMessage());
                return createMockAiResponse(unmatchedTerms, matchedSnomedIds);
            }
        } catch (Exception e) {
            System.err.println("Error calling AI service: " + e.getMessage());
            return createMockAiResponse(unmatchedTerms, matchedSnomedIds);
        }
    }
    
    private String buildAiPrompt(List<String> unmatchedTerms, List<String> matchedSnomedIds, String context) {
        String unmatchedList = unmatchedTerms.stream()
                .map(t -> "\"" + t + "\"")
                .collect(Collectors.joining(", "));
        
        String matchedList = matchedSnomedIds.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(", "));
        
        String ctx = context != null ? context : "Swedish healthcare terminology";
        
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
            + "Context: " + ctx + "\n"
            + "Unmatched terms: [" + unmatchedList + "]\n"
            + "Matched SNOMED IDs: [" + matchedList + "]\n";
    }
    
    private AiRecommendationResponse createMockAiResponse(List<String> unmatchedTerms, List<String> matchedSnomedIds) {
        List<AiRecommendationResponse.Recommendation> recommendations = new ArrayList<>();
        for (String term : unmatchedTerms) {
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
        if (!matchedSnomedIds.isEmpty()) {
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
    
    /**
     * Internal data structure for candidate recommendations
     */
    private record CandidateRecommendation(
            String snomedId,
            String preferredTerm,
            String fsn,
            double similarity,
            String source,
            String inputTerm
    ) {}
}

