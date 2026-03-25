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
    private final int hierarchyHopDepth;
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
            @Value("${recommendation.hierarchical.hop-depth:2}") int hierarchyHopDepth,
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
        this.hierarchyHopDepth = Math.max(1, hierarchyHopDepth);
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
            // Widen the candidate pool with a bounded multi-hop hierarchy traversal.
            // This makes "additional suggestions" less brittle across different term sets.
            List<AiRecommendationResponse.SuggestedTerm> hierarchicalSuggestions =
                    findViaWidenedHierarchicalSearch(request.matchedSnomedIds(), hierarchyHopDepth);
            allSuggested.addAll(hierarchicalSuggestions);
        }

        // Deterministic candidate pool for "additional suggestions".
        // This pool is grounded in your SNOMED hierarchy lookup (not in the AI output).
        List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool = new ArrayList<>(allSuggested);
        
        // Strategy 4: AI Agent (fallback for unmatched terms)
        List<String> stillUnmatched = request.unmatchedTerms().stream()
                .filter(term -> !synonymCandidates.containsKey(term.toLowerCase()) 
                             && !fuzzyCandidates.containsKey(term.toLowerCase()))
                .collect(Collectors.toList());
        
        System.out.println("DEBUG: Hybrid recommendation - stillUnmatched: " + stillUnmatched);
        System.out.println("DEBUG: Hybrid recommendation - useAiFallback: " + useAiFallback + ", aiEnabled: " + aiEnabled);
        
        if (!stillUnmatched.isEmpty() && useAiFallback && aiEnabled) {
            System.out.println("DEBUG: Calling AI fallback for terms: " + stillUnmatched);
            AiRecommendationResponse aiResponse = findViaAiFallback(
                    stillUnmatched,
                    request.matchedSnomedIds(),
                    request.context(),
                    additionalCandidatePool
            );
            allRecommendations.addAll(aiResponse.recommendations());
            // Prefer AI-ranked additions when present; otherwise keep deterministic hierarchy suggestions.
            if (aiResponse.suggestedAdditional() != null && !aiResponse.suggestedAdditional().isEmpty()) {
                allSuggested = new ArrayList<>(aiResponse.suggestedAdditional());
            }
        } else if (!stillUnmatched.isEmpty()) {
            // If AI is disabled, still provide mock recommendations so user sees something
            System.out.println("DEBUG: AI disabled, creating mock recommendations for: " + stillUnmatched);
            AiRecommendationResponse mockResponse = createGroundedFallbackResponse(stillUnmatched, additionalCandidatePool);
            allRecommendations.addAll(mockResponse.recommendations());
            // Do not add mock suggestedAdditional: keep deterministic hierarchy pool only.
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
    
    // Strategy 3: Hierarchical search is now handled by findViaWidenedHierarchicalSearch(...)

    /**
     * Widened hierarchical search by bounded multi-hop traversal.
     *
     * Hop depth = 1 behaves like the old strategy (parents + children of matched concepts).
     * Hop depth > 1 additionally includes grandparents/grandchildren, which also covers many sibling-like
     * concepts (children of parents) without needing explicit sibling traversal.
     */
    private List<AiRecommendationResponse.SuggestedTerm> findViaWidenedHierarchicalSearch(
            List<String> matchedSnomedIds,
            int hopDepth) {

        if (hopDepth < 1) {
            return List.of();
        }

        Set<String> matchedSet = new HashSet<>(matchedSnomedIds);

        // Avoid suggesting the same SNOMED id multiple times.
        Set<String> suggestedVisited = new HashSet<>();
        List<AiRecommendationResponse.SuggestedTerm> suggestions = new ArrayList<>();

        record Node(String conceptId, int depth) {}

        // Avoid infinite loops when traversing the graph.
        Set<String> expandedVisited = new HashSet<>(matchedSnomedIds);
        Deque<Node> queue = new ArrayDeque<>();
        for (String matchedId : matchedSnomedIds) {
            queue.add(new Node(matchedId, 0));
        }

        while (!queue.isEmpty() && suggestions.size() < maxHierarchicalResults) {
            Node node = queue.removeFirst();

            if (node.depth() >= hopDepth) {
                continue;
            }

            // Parents (broader concepts)
            for (String parentId : getParentConcepts(node.conceptId())) {
                if (suggestions.size() >= maxHierarchicalResults) break;
                if (parentId == null || parentId.isBlank()) continue;
                if (matchedSet.contains(parentId)) continue;
                if (suggestedVisited.contains(parentId)) continue;

                CandidateRecommendation candidate = fetchConceptDetails(parentId, null);
                if (candidate == null) continue;

                suggestions.add(new AiRecommendationResponse.SuggestedTerm(
                        candidate.snomedId(),
                        candidate.preferredTerm(),
                        candidate.fsn(),
                        "Ancestor (hierarchy depth " + (node.depth() + 1) + ") of matched concepts",
                        "Hierarchy-derived related concept",
                        List.of("hierarchy:broader")
                ));
                suggestedVisited.add(parentId);

                // Expand one more hop beyond the suggested node.
                if (node.depth() + 1 < hopDepth && expandedVisited.add(parentId)) {
                    queue.addLast(new Node(parentId, node.depth() + 1));
                }
            }

            // Children (narrower concepts)
            for (String childId : getChildConcepts(node.conceptId())) {
                if (suggestions.size() >= maxHierarchicalResults) break;
                if (childId == null || childId.isBlank()) continue;
                if (matchedSet.contains(childId)) continue;
                if (suggestedVisited.contains(childId)) continue;

                CandidateRecommendation candidate = fetchConceptDetails(childId, null);
                if (candidate == null) continue;

                suggestions.add(new AiRecommendationResponse.SuggestedTerm(
                        candidate.snomedId(),
                        candidate.preferredTerm(),
                        candidate.fsn(),
                        "Descendant (hierarchy depth " + (node.depth() + 1) + ") of matched concepts",
                        "Hierarchy-derived related concept",
                        List.of("hierarchy:narrower")
                ));
                suggestedVisited.add(childId);

                if (node.depth() + 1 < hopDepth && expandedVisited.add(childId)) {
                    queue.addLast(new Node(childId, node.depth() + 1));
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
        // Preserve input order for consistent ranking in the UI.
        // If the same SNOMED id appears multiple times, keep the last occurrence.
        Map<String, AiRecommendationResponse.SuggestedTerm> unique = new java.util.LinkedHashMap<>();
        for (AiRecommendationResponse.SuggestedTerm term : suggested) {
            if (term == null || term.snomedId() == null) continue;
            if (unique.containsKey(term.snomedId())) {
                unique.remove(term.snomedId());
            }
            unique.put(term.snomedId(), term);
        }
        return new ArrayList<>(unique.values());
    }
    
    /**
     * Strategy 4: AI Agent Fallback
     */
    private AiRecommendationResponse findViaAiFallback(
            List<String> unmatchedTerms,
            List<String> matchedSnomedIds,
            String context,
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool) {
        
        try {
            String prompt = buildAiPrompt(unmatchedTerms, matchedSnomedIds, context, additionalCandidatePool);
            String content = this.chatClient.prompt(prompt).call().content();
            
            try {
                com.fasterxml.jackson.databind.json.JsonMapper mapper = 
                        com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
                AiRecommendationResponse parsed = mapper.readValue(content, AiRecommendationResponse.class);
                return filterAiResponseToGroundedPool(parsed, unmatchedTerms, additionalCandidatePool);
            } catch (Exception e) {
                System.err.println("Error parsing AI response: " + e.getMessage());
                return createGroundedFallbackResponse(unmatchedTerms, additionalCandidatePool);
            }
        } catch (Exception e) {
            System.err.println("Error calling AI service: " + e.getMessage());
            return createGroundedFallbackResponse(unmatchedTerms, additionalCandidatePool);
        }
    }
    
    // (old 3-arg prompt removed; we now use the grounded 4-arg prompt only)

    private String escapeForPrompt(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    /**
     * Grounded (candidate-pool-only) AI prompt.
     * The AI must only output SNOMED ids that appear in additionalCandidatePool.
     */
    private String buildAiPrompt(
            List<String> unmatchedTerms,
            List<String> matchedSnomedIds,
            String context,
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool) {

        String unmatchedList = unmatchedTerms.stream()
                .map(t -> "\"" + escapeForPrompt(t) + "\"")
                .collect(Collectors.joining(", "));

        String matchedList = matchedSnomedIds.stream()
                .map(id -> "\"" + escapeForPrompt(id) + "\"")
                .collect(Collectors.joining(", "));

        String ctx = context != null ? context : "Swedish healthcare terminology";

        int maxCandidatesInPrompt = 30;
        List<AiRecommendationResponse.SuggestedTerm> pool = additionalCandidatePool == null
                ? List.of()
                : additionalCandidatePool.stream().limit(maxCandidatesInPrompt).toList();

        String candidatePoolJson = pool.stream()
                .map(c -> "{"
                        + "\"snomedId\":\"" + escapeForPrompt(c.snomedId()) + "\","
                        + "\"term\":\"" + escapeForPrompt(c.term()) + "\","
                        + "\"fsn\":\"" + escapeForPrompt(c.fsn()) + "\""
                        + "}")
                .collect(Collectors.joining(", "));

        return ""
                + "You are a clinical terminology assistant helping build a code system.\n"
                + "You must use a *grounded* candidate pool.\n\n"
                + "Given unmatched terms and existing matched SNOMED CT codes, do:\n"
                + "1) For each unmatched term, select the best recommendedSnomedId from the candidate pool.\n"
                + "2) Select additional complementary SNOMED codes from the same candidate pool.\n\n"
                + "IMPORTANT HARD CONSTRAINTS:\n"
                + "- 'recommendations[].recommendedSnomedId' MUST be one of the provided candidate pool snomedId values.\n"
                + "- 'suggestedAdditional[].snomedId' MUST be one of the provided candidate pool snomedId values.\n"
                + "- Do NOT invent any SNOMED id outside the candidate pool.\n"
                + "- Return JSON only.\n\n"
                + "Candidate pool (grounded): [" + candidatePoolJson + "]\n\n"
                + "Return JSON with this structure:\n"
                + "{\n"
                + "  \"recommendations\": [\n"
                + "    {\n"
                + "      \"inputTerm\": \"<unmatched term>\",\n"
                + "      \"recommendedSnomedId\": \"<SNOMED ID>\",\n"
                + "      \"recommendedTerm\": \"<term name>\",\n"
                + "      \"fsn\": \"<fully specified name>\",\n"
                + "      \"confidence\": 0.0,\n"
                + "      \"reason\": \"<explanation>\",\n"
                + "      \"definition\": \"<definition>\",\n"
                + "      \"relations\": [\"<relation>\", ...]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"suggestedAdditional\": [\n"
                + "    {\n"
                + "      \"snomedId\": \"<SNOMED ID>\",\n"
                + "      \"term\": \"<term name>\",\n"
                + "      \"fsn\": \"<fully specified name>\",\n"
                + "      \"reason\": \"<why suggested>\",\n"
                + "      \"definition\": \"<definition>\",\n"
                + "      \"relations\": [\"<relation>\", ...]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "Only output valid JSON.\n"
                + "Context: " + ctx + "\n"
                + "Unmatched terms: [" + unmatchedList + "]\n"
                + "Matched SNOMED IDs: [" + matchedList + "]\n";
    }

    private AiRecommendationResponse createGroundedFallbackResponse(
            List<String> unmatchedTerms,
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool) {

        if (unmatchedTerms == null || unmatchedTerms.isEmpty()) {
            return new AiRecommendationResponse(List.of(), List.of());
        }
        if (additionalCandidatePool == null || additionalCandidatePool.isEmpty()) {
            // Nothing grounded we can select from
            return new AiRecommendationResponse(List.of(), List.of());
        }

        AiRecommendationResponse.SuggestedTerm fallback = additionalCandidatePool.get(0);
        List<AiRecommendationResponse.Recommendation> recommendations = new ArrayList<>();

        for (String term : unmatchedTerms) {
            recommendations.add(new AiRecommendationResponse.Recommendation(
                    term,
                    fallback.snomedId(),
                    fallback.term(),
                    fallback.fsn(),
                    0.1,
                    "Fallback to grounded candidate pool",
                    fallback.definition(),
                    fallback.relations()
            ));
        }

        // Grounded behavior: do not override deterministic additions with mock entries.
        return new AiRecommendationResponse(recommendations, List.of());
    }

    private AiRecommendationResponse filterAiResponseToGroundedPool(
            AiRecommendationResponse parsed,
            List<String> unmatchedTerms,
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool) {

        if (parsed == null) {
            return createGroundedFallbackResponse(unmatchedTerms, additionalCandidatePool);
        }

        Set<String> allowedAdditionalIds = additionalCandidatePool == null
                ? Set.of()
                : additionalCandidatePool.stream()
                        .filter(c -> c != null && c.snomedId() != null)
                        .map(AiRecommendationResponse.SuggestedTerm::snomedId)
                        .collect(Collectors.toSet());

        Map<String, AiRecommendationResponse.SuggestedTerm> candidateById = additionalCandidatePool == null
                ? Map.of()
                : additionalCandidatePool.stream()
                        .filter(c -> c != null && c.snomedId() != null)
                        .collect(Collectors.toMap(AiRecommendationResponse.SuggestedTerm::snomedId, c -> c, (a, b) -> a));

        // Hard filter suggestedAdditional to the grounded pool; also normalize term/fsn fields from the pool.
        List<AiRecommendationResponse.SuggestedTerm> filteredSuggested = parsed.suggestedAdditional() == null
                ? List.of()
                : parsed.suggestedAdditional().stream()
                        .filter(s -> s != null && s.snomedId() != null && allowedAdditionalIds.contains(s.snomedId()))
                        .map(s -> {
                            AiRecommendationResponse.SuggestedTerm c = candidateById.get(s.snomedId());
                            if (c == null) return null;
                            String reason = s.reason() != null ? s.reason() : c.reason();
                            return new AiRecommendationResponse.SuggestedTerm(
                                    c.snomedId(),
                                    c.term(),
                                    c.fsn(),
                                    reason,
                                    c.definition(),
                                    c.relations()
                            );
                        })
                        .filter(Objects::nonNull)
                        .toList();

        // Hard filter recommendations to the same grounded pool to eliminate invented ids.
        List<AiRecommendationResponse.Recommendation> filteredRecs = parsed.recommendations() == null
                ? List.of()
                : parsed.recommendations().stream()
                        .filter(r -> r != null && r.recommendedSnomedId() != null && allowedAdditionalIds.contains(r.recommendedSnomedId()))
                        .map(r -> {
                            AiRecommendationResponse.SuggestedTerm c = candidateById.get(r.recommendedSnomedId());
                            if (c == null) return null;
                            String reason = r.reason() != null ? r.reason() : "Grounded candidate match";
                            return new AiRecommendationResponse.Recommendation(
                                    r.inputTerm(),
                                    c.snomedId(),
                                    c.term(),
                                    c.fsn(),
                                    r.confidence(),
                                    reason,
                                    c.definition(),
                                    c.relations()
                            );
                        })
                        .filter(Objects::nonNull)
                        .toList();

        if (filteredRecs.isEmpty() && unmatchedTerms != null && !unmatchedTerms.isEmpty()) {
            return createGroundedFallbackResponse(unmatchedTerms, additionalCandidatePool);
        }

        return new AiRecommendationResponse(filteredRecs, filteredSuggested);
    }
    
    // (old mocked AI response removed; we now use grounded fallback only)
    
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

