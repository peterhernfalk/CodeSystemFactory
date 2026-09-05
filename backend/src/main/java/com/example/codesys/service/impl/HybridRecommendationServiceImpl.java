package com.example.codesys.service.impl;

import com.example.codesys.model.AiRecommendationRequest;
import com.example.codesys.model.AiRecommendationResponse;
import com.example.codesys.model.TermMatch;
import com.example.codesys.service.*;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final String fhirBaseUrl;
    private final String branch;
    private final String language;
    
    // Configuration
    private final double fuzzyMatchMinSimilarity;
    private final double fuzzyMatchMaxSimilarity;
    private final int maxHierarchicalResults;
    private final int hierarchyHopDepth;
    private final boolean useAiFallback;
    private final boolean aiEnabled;
    /** When true, ADDITIONAL mode does not back-fill from the deterministic pool if the model returns nothing. */
    private final boolean aiOnlyAdditional;
    
    public HybridRecommendationServiceImpl(
            @Qualifier("ontoserverSnomedService") SnomedService snomedService,
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
            @Value("${ai.enabled:true}") boolean aiEnabled,
            @Value("${recommendation.ai-only-additional:false}") boolean aiOnlyAdditional) {
        
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
        this.aiOnlyAdditional = aiOnlyAdditional;
        
        // Extract base URL — default to Ontoserver when unset (Snowstorm training is often unreachable).
        String baseUrl = fhirServerUrl == null ? "" : fhirServerUrl.trim();
        if (baseUrl.isEmpty()) {
            this.snowstormBaseUrl = "https://r4.ontoserver.csiro.au";
            this.fhirBaseUrl = "https://r4.ontoserver.csiro.au/fhir";
        } else if (baseUrl.contains("/fhir")) {
            this.snowstormBaseUrl = baseUrl.replace("/fhir", "").replaceAll("/$", "");
            this.fhirBaseUrl = baseUrl.replaceAll("/$", "");
        } else {
            this.snowstormBaseUrl = baseUrl.replaceAll("/$", "");
            this.fhirBaseUrl = baseUrl.replaceAll("/$", "") + "/fhir";
        }
        
        this.rest = new RestTemplate();
    }
    
    @Override
    public AiRecommendationResponse recommend(AiRecommendationRequest request) {
        AiRecommendationRequest.RecommendationMode mode = normalizeMode(request.recommendationMode());
        boolean modelingMode = mode == AiRecommendationRequest.RecommendationMode.MODELING;
        boolean runUnmatchedRecommendations =
                mode != AiRecommendationRequest.RecommendationMode.ADDITIONAL
                && mode != AiRecommendationRequest.RecommendationMode.MODELING;
        // Always surface additional SNOMED suggestions alongside unmatched resolution. The UI “unmatched”
        // action still sends UNMATCHED mode; previously this flag skipped hierarchy + post-fill, so users
        // only saw extras when the model happened to populate suggestedAdditional.
        boolean runAdditionalSuggestions = true;

        List<AiRecommendationResponse.Recommendation> allRecommendations = new ArrayList<>();
        List<AiRecommendationResponse.SuggestedTerm> allSuggested = new ArrayList<>();

        // MODELING uses unmatchedTerms as the full source-term list for the modeling prompt.
        List<String> modelingSourceTerms = modelingMode
                ? (request.unmatchedTerms() == null ? List.of() : request.unmatchedTerms().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList())
                : List.of();
        
        // Strategy 1: Synonym Database Lookup
        Map<String, CandidateRecommendation> synonymCandidates = runUnmatchedRecommendations
                ? findViaSynonymDatabase(request.unmatchedTerms())
                : Map.of();
        allRecommendations.addAll(convertToRecommendations(synonymCandidates, "Synonym database match"));
        
        // Strategy 2: Enhanced Fuzzy Matching (candidates with 0.4-0.6 similarity)
        Map<String, CandidateRecommendation> fuzzyCandidates = runUnmatchedRecommendations
                ? findViaFuzzyMatching(request.unmatchedTerms())
                : Map.of();
        allRecommendations.addAll(convertToRecommendations(fuzzyCandidates, "Fuzzy match (similarity 0.4-0.6)"));
        
        // Strategy 3: Hierarchical Search (parent/child/sibling of matched concepts)
        List<AiRecommendationResponse.SuggestedTerm> hierarchicalSuggestions = List.of();
        if (!request.matchedSnomedIds().isEmpty()) {
            hierarchicalSuggestions =
                    findViaWidenedHierarchicalSearch(request.matchedSnomedIds(), hierarchyHopDepth);
            if (runAdditionalSuggestions) {
                allSuggested.addAll(hierarchicalSuggestions);
            }
        }

        // Strategy 3b: FHIR search for unmatched terms that fuzzy/synonym didn't resolve.
        // Seeds the candidate pool so the AI has something to ground against.
        Map<String, CandidateRecommendation> fhirSearchCandidates = new HashMap<>();
        Map<String, List<CandidateRecommendation>> fhirSearchAllResults = new HashMap<>();
        if (runUnmatchedRecommendations) {
            for (String term : request.unmatchedTerms()) {
                if (synonymCandidates.containsKey(term.toLowerCase()) || fuzzyCandidates.containsKey(term.toLowerCase())) {
                    continue;
                }
                List<CandidateRecommendation> fhirResults = searchViaFhirExpand(term, 5);
                fhirSearchAllResults.put(term.toLowerCase(), fhirResults);
                if (!fhirResults.isEmpty()) {
                    fhirSearchCandidates.put(term.toLowerCase(), fhirResults.get(0));
                    System.out.println("DEBUG: FHIR search found " + fhirResults.size() + " results for '" + term
                            + "', best: " + fhirResults.get(0).preferredTerm() + " (" + fhirResults.get(0).snomedId() + ")");
                }
            }
        }

        // Grounded candidate pool for the AI step (required for UNMATCHED mode — previously empty).
        // Includes: hierarchy neighbors, already-matched concepts, synonym/fuzzy/FHIR candidates.
        List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool =
                buildGroundedAiCandidatePool(
                        request.matchedSnomedIds(),
                        hierarchicalSuggestions,
                        synonymCandidates,
                        fuzzyCandidates);

        // Add all FHIR search results (cached) to the candidate pool
        for (List<CandidateRecommendation> results : fhirSearchAllResults.values()) {
            for (CandidateRecommendation c : results) {
                additionalCandidatePool.add(candidateToSuggestedTerm(c, "FHIR terminology search", List.of("pool:fhir-search")));
            }
        }
        additionalCandidatePool = deduplicateSuggested(additionalCandidatePool);

        // Also add direct FHIR recommendations for terms that were resolved
        allRecommendations.addAll(convertToRecommendations(fhirSearchCandidates, "FHIR terminology search"));
        
        // Strategy 4: AI Agent (fallback for unmatched terms + ranking of additional suggestions)
        List<String> stillUnmatched = request.unmatchedTerms().stream()
                .filter(term -> !synonymCandidates.containsKey(term.toLowerCase()) 
                             && !fuzzyCandidates.containsKey(term.toLowerCase())
                             && !fhirSearchCandidates.containsKey(term.toLowerCase()))
                .collect(Collectors.toList());
        if (!runUnmatchedRecommendations) {
            // ADDITIONAL clears unmatched resolution terms; MODELING keeps source terms for the prompt.
            stillUnmatched = modelingMode ? modelingSourceTerms : List.of();
        }
        
        System.out.println("DEBUG: Hybrid recommendation - mode=" + mode
                + ", stillUnmatched: " + stillUnmatched
                + ", modelingSourceTerms: " + modelingSourceTerms);
        System.out.println("DEBUG: Hybrid recommendation - useAiFallback: " + useAiFallback + ", aiEnabled: " + aiEnabled);
        
        if (shouldInvokeAiFallback(mode, stillUnmatched, request.matchedSnomedIds(), additionalCandidatePool)) {
            System.out.println("DEBUG: Calling AI fallback. stillUnmatched=" + stillUnmatched
                    + ", candidatePoolSize=" + additionalCandidatePool.size());
            List<String> aiUnmatchedTerms = modelingMode ? modelingSourceTerms : stillUnmatched;
            AiRecommendationResponse aiResponse = findViaAiFallback(
                    aiUnmatchedTerms,
                    request.matchedSnomedIds(),
                    request.context(),
                    additionalCandidatePool,
                    mode
            );
            if (modelingMode) {
                return normalizeModelingResponse(aiResponse);
            }
            allRecommendations.addAll(aiResponse.recommendations());
            // Prefer AI-ranked additions when present; otherwise keep deterministic hierarchy suggestions.
            if (aiResponse.suggestedAdditional() != null && !aiResponse.suggestedAdditional().isEmpty()) {
                allSuggested = new ArrayList<>(aiResponse.suggestedAdditional());
            } else if (mode == AiRecommendationRequest.RecommendationMode.ADDITIONAL && aiOnlyAdditional) {
                allSuggested = new ArrayList<>();
            }
        } else if (modelingMode) {
            if (!useAiFallback || !aiEnabled) {
                throw new IllegalStateException(
                        "Modeling suggestions require AI to be enabled (ai.enabled=true and recommendation.ai-fallback=true).");
            }
            throw new IllegalStateException(
                    "Modeling suggestions require at least one source term or matched SNOMED ID.");
        } else if (!stillUnmatched.isEmpty()) {
            // If AI is disabled, still provide mock recommendations so user sees something
            System.out.println("DEBUG: AI disabled, creating mock recommendations for: " + stillUnmatched);
            AiRecommendationResponse mockResponse = createGroundedFallbackResponse(
                    stillUnmatched, request.matchedSnomedIds(), additionalCandidatePool, false);
            allRecommendations.addAll(mockResponse.recommendations());
            // Do not add mock suggestedAdditional: keep deterministic hierarchy pool only.
        }
        
        // If hierarchy + AI produced no additional rows, show deterministic pool entries (excluding matched ids).
        boolean skipDeterministicPostFill = mode == AiRecommendationRequest.RecommendationMode.ADDITIONAL && aiOnlyAdditional;
        if (runAdditionalSuggestions && allSuggested.isEmpty() && !additionalCandidatePool.isEmpty() && !skipDeterministicPostFill) {
            allSuggested = new ArrayList<>(suggestedAdditionalExcludingMatched(
                    request.matchedSnomedIds(), additionalCandidatePool, maxHierarchicalResults));
        }

        // Deduplicate and rank recommendations
        List<AiRecommendationResponse.Recommendation> deduplicatedRecs = deduplicateAndRank(allRecommendations);
        List<AiRecommendationResponse.SuggestedTerm> deduplicatedSuggested = deduplicateSuggested(allSuggested);
        
        return new AiRecommendationResponse(deduplicatedRecs, deduplicatedSuggested);
    }

    private AiRecommendationResponse normalizeModelingResponse(AiRecommendationResponse aiResponse) {
        if (aiResponse == null) {
            return new AiRecommendationResponse(List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return new AiRecommendationResponse(
                List.of(),
                List.of(),
                aiResponse.existingSnomedAdditions() == null ? List.of() : aiResponse.existingSnomedAdditions(),
                aiResponse.candidateNewTerms() == null ? List.of() : aiResponse.candidateNewTerms(),
                aiResponse.modelingReviewChecklist() == null ? List.of() : aiResponse.modelingReviewChecklist()
        );
    }

    /**
     * Additional SNOMED codes for the code system: prefer concepts not already selected as matches.
     */
    private List<AiRecommendationResponse.SuggestedTerm> suggestedAdditionalExcludingMatched(
            List<String> matchedSnomedIds,
            List<AiRecommendationResponse.SuggestedTerm> pool,
            int limit) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        Set<String> matched = matchedSnomedIds == null
                ? Set.of()
                : matchedSnomedIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return pool.stream()
                .filter(s -> s != null && s.snomedId() != null && !matched.contains(s.snomedId()))
                .limit(Math.max(1, limit))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    AiRecommendationRequest.RecommendationMode normalizeMode(AiRecommendationRequest.RecommendationMode mode) {
        return mode == null ? AiRecommendationRequest.RecommendationMode.BOTH : mode;
    }

    /**
     * SNOMED ids the AI may output — must be non-empty for grounded UNMATCHED recommendations to work.
     */
    private List<AiRecommendationResponse.SuggestedTerm> buildGroundedAiCandidatePool(
            List<String> matchedSnomedIds,
            List<AiRecommendationResponse.SuggestedTerm> hierarchicalSuggestions,
            Map<String, CandidateRecommendation> synonymCandidates,
            Map<String, CandidateRecommendation> fuzzyCandidates) {

        List<AiRecommendationResponse.SuggestedTerm> pool = new ArrayList<>();
        if (hierarchicalSuggestions != null) {
            pool.addAll(hierarchicalSuggestions);
        }
        if (matchedSnomedIds != null) {
            for (String id : matchedSnomedIds) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                CandidateRecommendation det = fetchConceptDetails(id, null);
                if (det != null) {
                    pool.add(candidateToSuggestedTerm(
                            det,
                            "Already matched concept in the code system",
                            List.of("matched:input")));
                }
            }
        }
        for (CandidateRecommendation c : synonymCandidates.values()) {
            pool.add(candidateToSuggestedTerm(
                    c,
                    "Synonym / dictionary candidate",
                    List.of("pool:synonym")));
        }
        for (CandidateRecommendation c : fuzzyCandidates.values()) {
            pool.add(candidateToSuggestedTerm(
                    c,
                    "Fuzzy match candidate",
                    List.of("pool:fuzzy")));
        }
        return deduplicateSuggested(pool);
    }

    private AiRecommendationResponse.SuggestedTerm candidateToSuggestedTerm(
            CandidateRecommendation c,
            String reason,
            List<String> relations) {
        return new AiRecommendationResponse.SuggestedTerm(
                c.snomedId(),
                c.preferredTerm(),
                c.fsn(),
                reason,
                "SNOMED CT concept (grounded candidate pool)",
                relations);
    }

    /**
     * Option A behavior:
     * - invoke AI for unresolved unmatched terms
     * - also invoke AI for additional-suggestion ranking when matched-term candidate pool exists
     * - for MODELING, invoke AI whenever there are source terms and/or matched IDs
     */
    boolean shouldInvokeAiFallback(
            List<String> stillUnmatched,
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool) {
        return shouldInvokeAiFallback(
                AiRecommendationRequest.RecommendationMode.BOTH,
                stillUnmatched,
                List.of(),
                additionalCandidatePool);
    }

    boolean shouldInvokeAiFallback(
            AiRecommendationRequest.RecommendationMode mode,
            List<String> stillUnmatched,
            List<String> matchedSnomedIds,
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool) {
        if (!useAiFallback || !aiEnabled) {
            return false;
        }
        if (mode == AiRecommendationRequest.RecommendationMode.MODELING) {
            boolean hasSourceTerms = stillUnmatched != null && !stillUnmatched.isEmpty();
            boolean hasMatchedIds = matchedSnomedIds != null && matchedSnomedIds.stream()
                    .anyMatch(id -> id != null && !id.isBlank());
            return hasSourceTerms || hasMatchedIds;
        }
        boolean hasUnmatched = stillUnmatched != null && !stillUnmatched.isEmpty();
        boolean hasAdditionalPool = additionalCandidatePool != null && !additionalCandidatePool.isEmpty();
        return hasUnmatched || hasAdditionalPool;
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
     * Fetch full concept details — tries Snowstorm first, falls back to FHIR CodeSystem/$lookup.
     */
    private CandidateRecommendation fetchConceptDetails(String conceptId, String inputTerm) {
        CandidateRecommendation result = fetchConceptDetailsSnowstorm(conceptId, inputTerm);
        if (result != null) return result;
        return fetchConceptDetailsFhir(conceptId, inputTerm);
    }

    private CandidateRecommendation fetchConceptDetailsSnowstorm(String conceptId, String inputTerm) {
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
                double similarity = 1.0;
                if (inputTerm != null) {
                    similarity = jw.apply(inputTerm.toLowerCase(), preferredTerm.toLowerCase());
                }
                return new CandidateRecommendation(conceptId, preferredTerm, fsn, similarity, "Concept lookup", inputTerm);
            }
        } catch (Exception e) {
            // Snowstorm not available — will try FHIR
        }
        return null;
    }

    private CandidateRecommendation fetchConceptDetailsFhir(String conceptId, String inputTerm) {
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(fhirBaseUrl)
                    .path("/CodeSystem/$lookup")
                    .queryParam("system", "http://snomed.info/sct")
                    .queryParam("code", conceptId)
                    .queryParam("property", "display");

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.valueOf("application/fhir+json"), MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    urlBuilder.toUriString(), HttpMethod.GET, req,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                String display = extractFhirParameterString(resp.getBody(), "display");
                if (display == null || display.isBlank()) display = "Unknown term";
                double similarity = 1.0;
                if (inputTerm != null) {
                    similarity = jw.apply(inputTerm.toLowerCase(), display.toLowerCase());
                }
                return new CandidateRecommendation(conceptId, display, display, similarity, "FHIR lookup", inputTerm);
            }
        } catch (Exception e) {
            System.err.println("Error fetching concept " + conceptId + " via FHIR: " + e.getMessage());
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
        List<String> concepts = getRelatedConceptsSnowstorm(conceptId, relationType, limit);
        if (!concepts.isEmpty()) return concepts;
        return getRelatedConceptsFhir(conceptId, relationType.equals("parents") ? "parent" : "child");
    }

    private List<String> getRelatedConceptsSnowstorm(String conceptId, String relationType, int limit) {
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
            // Snowstorm not available — will try FHIR
        }
        return concepts;
    }

    /**
     * FHIR-based hierarchy traversal using CodeSystem/$lookup with parent/child properties.
     */
    private List<String> getRelatedConceptsFhir(String conceptId, String property) {
        List<String> concepts = new ArrayList<>();
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(fhirBaseUrl)
                    .path("/CodeSystem/$lookup")
                    .queryParam("system", "http://snomed.info/sct")
                    .queryParam("code", conceptId)
                    .queryParam("property", property);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.valueOf("application/fhir+json"), MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    urlBuilder.toUriString(), HttpMethod.GET, req,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                concepts.addAll(extractFhirPropertyCodes(resp.getBody(), property));
            }
        } catch (Exception e) {
            System.err.println("Error fetching " + property + " for " + conceptId + " via FHIR: " + e.getMessage());
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
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool,
            AiRecommendationRequest.RecommendationMode mode) {
        
        boolean suppressPool = suppressDeterministicAdditionalPool(mode);
        boolean modelingMode = mode == AiRecommendationRequest.RecommendationMode.MODELING;
        try {
            String prompt = buildAiPrompt(unmatchedTerms, matchedSnomedIds, context, additionalCandidatePool, mode);
            String content = this.chatClient.prompt(prompt).call().content();
            String jsonPayload = extractJsonFromModelContent(content);
            
            try {
                if (modelingMode) {
                    return parseModelingAiResponse(jsonPayload);
                }
                com.fasterxml.jackson.databind.json.JsonMapper mapper = 
                        com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
                AiRecommendationResponse parsed = mapper.readValue(jsonPayload, AiRecommendationResponse.class);
                return filterAiResponseToGroundedPool(
                        parsed, unmatchedTerms, matchedSnomedIds, additionalCandidatePool, suppressPool);
            } catch (Exception e) {
                System.err.println("Error parsing AI response: " + e.getMessage());
                if (modelingMode) {
                    throw new IllegalStateException(
                            "AI returned modeling JSON that could not be parsed: " + e.getMessage(), e);
                }
                return createGroundedFallbackResponse(unmatchedTerms, matchedSnomedIds, additionalCandidatePool, suppressPool);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error calling AI service: " + e.getMessage());
            if (modelingMode) {
                throw new IllegalStateException(
                        "Error calling AI service for modeling suggestions: " + e.getMessage(), e);
            }
            return createGroundedFallbackResponse(unmatchedTerms, matchedSnomedIds, additionalCandidatePool, suppressPool);
        }
    }

    /**
     * Tolerant MODELING JSON parser: missing lists/booleans get defaults so partial model output still works.
     */
    AiRecommendationResponse parseModelingAiResponse(String jsonPayload) throws Exception {
        com.fasterxml.jackson.databind.json.JsonMapper mapper =
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonPayload);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Expected a JSON object for modeling response");
        }

        List<AiRecommendationResponse.ExistingSnomedAddition> existing = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode existingNode = root.get("existingSnomedAdditions");
        if (existingNode != null && existingNode.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode n : existingNode) {
                if (n == null || !n.isObject()) continue;
                existing.add(new AiRecommendationResponse.ExistingSnomedAddition(
                        textOrEmpty(n, "snomedId"),
                        textOrEmpty(n, "pt"),
                        textOrEmpty(n, "fsn"),
                        textOrEmpty(n, "whyAdd"),
                        stringListOrEmpty(n.get("relationsToExisting")),
                        doubleOrDefault(n.get("confidence"), 0.0),
                        textOrEmpty(n, "inputTerm")
                ));
            }
        }

        List<AiRecommendationResponse.CandidateNewTerm> candidates = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode candidatesNode = root.get("candidateNewTerms");
        if (candidatesNode != null && candidatesNode.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode n : candidatesNode) {
                if (n == null || !n.isObject()) continue;
                List<AiRecommendationResponse.ParentSuggestion> parents = new ArrayList<>();
                com.fasterxml.jackson.databind.JsonNode parentsNode = n.get("proximalPrimitiveParentSuggestions");
                if (parentsNode != null && parentsNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode p : parentsNode) {
                        if (p == null || !p.isObject()) continue;
                        parents.add(new AiRecommendationResponse.ParentSuggestion(
                                textOrEmpty(p, "snomedId"),
                                textOrEmpty(p, "term")));
                    }
                }
                List<AiRecommendationResponse.DefiningAttribute> attrs = new ArrayList<>();
                com.fasterxml.jackson.databind.JsonNode attrsNode = n.get("definingAttributes");
                if (attrsNode != null && attrsNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode a : attrsNode) {
                        if (a == null || !a.isObject()) continue;
                        attrs.add(new AiRecommendationResponse.DefiningAttribute(
                                textOrEmpty(a, "attribute"),
                                textOrEmpty(a, "value"),
                                textOrEmpty(a, "valueSnomedId")));
                    }
                }
                candidates.add(new AiRecommendationResponse.CandidateNewTerm(
                        textOrEmpty(n, "proposedPt"),
                        textOrEmpty(n, "proposedFsn"),
                        textOrEmpty(n, "semanticTag"),
                        textOrEmpty(n, "gapType"),
                        textOrEmpty(n, "gapJustification"),
                        parents,
                        attrs,
                        booleanOrDefault(n.get("postcoordinationCandidate"), false),
                        stringListOrEmpty(n.get("exampleExpressions")),
                        stringListOrEmpty(n.get("synonymsSv")),
                        stringListOrEmpty(n.get("synonymsEn")),
                        textOrEmpty(n, "usageExample"),
                        textOrEmpty(n, "uncertaintyNotes"),
                        doubleOrDefault(n.get("confidence"), 0.0),
                        textOrEmpty(n, "inputTerm"),
                        textOrEmpty(n, "decision")
                ));
            }
        }

        List<String> checklist = stringListOrEmpty(root.get("modelingReviewChecklist"));
        return normalizeModelingResponse(new AiRecommendationResponse(
                List.of(), List.of(), existing, candidates, checklist));
    }

    private static String textOrEmpty(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node == null) return "";
        com.fasterxml.jackson.databind.JsonNode v = node.get(field);
        if (v == null || v.isNull()) return "";
        return v.asText("");
    }

    private static List<String> stringListOrEmpty(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode item : node) {
            if (item == null || item.isNull()) continue;
            String s = item.asText(null);
            if (s != null && !s.isBlank()) out.add(s);
        }
        return out;
    }

    private static double doubleOrDefault(com.fasterxml.jackson.databind.JsonNode node, double defaultValue) {
        if (node == null || node.isNull() || !node.isNumber()) return defaultValue;
        return node.asDouble(defaultValue);
    }

    private static boolean booleanOrDefault(com.fasterxml.jackson.databind.JsonNode node, boolean defaultValue) {
        if (node == null || node.isNull()) return defaultValue;
        if (node.isBoolean()) return node.asBoolean(defaultValue);
        if (node.isTextual()) {
            String t = node.asText("").trim().toLowerCase();
            if ("true".equals(t) || "yes".equals(t) || "1".equals(t)) return true;
            if ("false".equals(t) || "no".equals(t) || "0".equals(t)) return false;
        }
        return defaultValue;
    }

    private boolean suppressDeterministicAdditionalPool(AiRecommendationRequest.RecommendationMode mode) {
        return mode == AiRecommendationRequest.RecommendationMode.ADDITIONAL && aiOnlyAdditional;
    }

    /**
     * Strips markdown code fences (e.g. triple-backtick json blocks) so Jackson can parse the payload.
     */
    public static String extractJsonFromModelContent(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (!s.startsWith("```")) {
            return s;
        }
        int firstNl = s.indexOf('\n');
        if (firstNl > 0) {
            s = s.substring(firstNl + 1);
        } else {
            s = s.substring(3).trim();
            if (s.startsWith("json")) {
                int afterLang = s.indexOf('\n');
                if (afterLang >= 0) {
                    s = s.substring(afterLang + 1);
                }
            }
        }
        int fenceEnd = s.lastIndexOf("```");
        if (fenceEnd >= 0) {
            s = s.substring(0, fenceEnd);
        }
        return s.trim();
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
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool,
            AiRecommendationRequest.RecommendationMode mode) {

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

        String commonConstraints = ""
                + "IMPORTANT HARD CONSTRAINTS:\n"
                + "- 'recommendations[].recommendedSnomedId' MUST be one of the provided candidate pool snomedId values.\n"
                + "- 'suggestedAdditional[].snomedId' MUST be one of the provided candidate pool snomedId values.\n"
                + "- Do NOT invent any SNOMED id outside the candidate pool.\n"
                + "- Do NOT wrap the JSON in markdown or code fences — output raw JSON only.\n\n";

        String jsonShape = ""
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
                + "}\n";

        if (mode == AiRecommendationRequest.RecommendationMode.ADDITIONAL) {
            return ""
                    + "You are a clinical terminology assistant helping build a code system.\n"
                    + "MODE: ADDITIONAL SUGGESTIONS ONLY — there are no unmatched input terms to resolve.\n"
                    + "Your primary deliverable is suggestedAdditional: pick complementary SNOMED CT concepts from the candidate pool.\n"
                    + "- You MUST set \"suggestedAdditional\" to a non-empty array whenever the candidate pool is non-empty.\n"
                    + "- Prefer concepts that are clinically related but not already in the matched list.\n"
                    + "- \"recommendations\" MUST be an empty array [] (no unmatched terms to map).\n"
                    + commonConstraints
                    + "Candidate pool (grounded): [" + candidatePoolJson + "]\n\n"
                    + jsonShape
                    + "Only output valid JSON.\n"
                    + "Context: " + ctx + "\n"
                    + "Matched SNOMED IDs (exclude these from suggestedAdditional): [" + matchedList + "]\n";
        }

        if (mode == AiRecommendationRequest.RecommendationMode.UNMATCHED) {
            return ""
                    + "You are a clinical terminology assistant helping build a code system.\n"
                    + "MODE: UNMATCHED TERMS — map each unmatched term to the best candidate from the pool.\n"
                    + "- Focus on filling \"recommendations\" with one entry per unmatched term.\n"
                    + "- \"suggestedAdditional\" is optional; may be [] if not needed.\n"
                    + commonConstraints
                    + "Candidate pool (grounded): [" + candidatePoolJson + "]\n\n"
                    + jsonShape
                    + "Only output valid JSON.\n"
                    + "Context: " + ctx + "\n"
                    + "Unmatched terms: [" + unmatchedList + "]\n"
                    + "Matched SNOMED IDs: [" + matchedList + "]\n";
        }

        if (mode == AiRecommendationRequest.RecommendationMode.MODELING) {
            return ""
                    + "You are a SNOMED CT clinical terminology modeling assistant.\n"
                    + "Follow SNOMED CT Editorial Guide authoring principles (proximal primitive modeling).\n\n"
                    + "OBJECTIVE:\n"
                    + "For EVERY source clinical term, recommend how it should be represented using SNOMED CT Editorial Guide practice:\n"
                    + "1) Prefer an EXISTING international concept when it adequately covers the meaning;\n"
                    + "2) Else prefer POSTCOORDINATION with approved attributes when expressible;\n"
                    + "3) Else propose a NEW local/extension concept with a stated logical definition.\n\n"
                    + "EDITORIAL GUIDE RULES (MUST FOLLOW):\n"
                    + "- Use zero-based proximal primitive modeling: state proximal PRIMITIVE parent(s) and ALL defining attribute-value pairs needed for meaning (even if also present on a supertype).\n"
                    + "- Do NOT rely on inheritance/refinement of a sufficiently defined parent; the classifier infers proximal defined parents.\n"
                    + "- FSN MUST use pattern: \"preferred term (semantic tag)\".\n"
                    + "- Choose an appropriate semantic tag (disorder, finding, procedure, observable entity, situation, etc.).\n"
                    + "- Prefer postcoordination over inventing a new concept when EG-compatible attributes can express the meaning.\n"
                    + "- Do NOT invent SNOMED IDs. If unknown, use an empty string and name the concept in text.\n"
                    + "- Group defining attributes when clinically required (describe grouping in exampleExpressions when helpful).\n\n"
                    + "PER-TERM CONTRACT (HARD):\n"
                    + "- Every source clinical term MUST appear as inputTerm on exactly one covering outcome:\n"
                    + "  (A) existingSnomedAdditions[] with inputTerm set, OR\n"
                    + "  (B) candidateNewTerms[] with inputTerm set and decision \"postcoordination\" or \"new\".\n"
                    + "- If an existing concept covers the term, use (A) and do NOT also invent a new candidate for that same inputTerm.\n"
                    + "- candidateNewTerms.decision must be one of: \"existing\", \"postcoordination\", \"new\".\n"
                    + "  Use \"existing\" only when mirroring coverage already listed in existingSnomedAdditions for the same inputTerm is unavoidable; prefer (A) alone.\n"
                    + "- Exclude duplicates of already matched concepts unless adding a distinct related concept with clear whyAdd.\n"
                    + "- Use confidence as a number between 0.0 and 1.0.\n"
                    + "- Return RAW JSON only. No markdown, no code fences, no commentary.\n\n"
                    + "MODEL INPUT:\n"
                    + "- Context: " + ctx + "\n"
                    + "- Source clinical terms (cover EACH): [" + unmatchedList + "]\n"
                    + "- Existing matched SNOMED IDs: [" + matchedList + "]\n"
                    + "- Candidate pool (grounded context only; prefer these IDs when recommending existing concepts): [" + candidatePoolJson + "]\n\n"
                    + "Return JSON with EXACTLY this top-level structure:\n"
                    + "{\n"
                    + "  \"existingSnomedAdditions\": [\n"
                    + "    {\n"
                    + "      \"inputTerm\": \"<source clinical term covered>\",\n"
                    + "      \"snomedId\": \"<id or empty>\",\n"
                    + "      \"pt\": \"<preferred term>\",\n"
                    + "      \"fsn\": \"<fully specified name>\",\n"
                    + "      \"whyAdd\": \"<coverage rationale vs Editorial Guide>\",\n"
                    + "      \"relationsToExisting\": [\"<relation>\", \"...\"],\n"
                    + "      \"confidence\": 0.0\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"candidateNewTerms\": [\n"
                    + "    {\n"
                    + "      \"inputTerm\": \"<source clinical term>\",\n"
                    + "      \"decision\": \"postcoordination|new\",\n"
                    + "      \"proposedPt\": \"<term>\",\n"
                    + "      \"proposedFsn\": \"<term (semantic tag)>\",\n"
                    + "      \"semanticTag\": \"<disorder|finding|procedure|observable entity|situation|event|regime/therapy|body structure|substance|organism|physical object|specimen|qualifier value>\",\n"
                    + "      \"gapType\": \"<lexical-gap|granularity-gap|context-gap|workflow-gap|local-policy-gap>\",\n"
                    + "      \"gapJustification\": \"<why existing international concept / postcoordination is insufficient or how postcoordination applies>\",\n"
                    + "      \"proximalPrimitiveParentSuggestions\": [{\"snomedId\":\"<id or empty>\",\"term\":\"<parent term>\"}],\n"
                    + "      \"definingAttributes\": [{\"attribute\":\"<attribute>\",\"value\":\"<value>\",\"valueSnomedId\":\"<id or empty>\"}],\n"
                    + "      \"postcoordinationCandidate\": false,\n"
                    + "      \"exampleExpressions\": [\"<optional SNOMED expression>\"],\n"
                    + "      \"synonymsSv\": [\"<sv synonym>\"],\n"
                    + "      \"synonymsEn\": [\"<en synonym>\"],\n"
                    + "      \"usageExample\": \"<clinical usage example>\",\n"
                    + "      \"uncertaintyNotes\": \"<empty or uncertainty notes>\",\n"
                    + "      \"confidence\": 0.0\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"modelingReviewChecklist\": [\"<Editorial Guide author check 1>\", \"<check 2>\"]\n"
                    + "}\n"
                    + "Include a modelingReviewChecklist with concrete author checks (PP parent, attribute completeness, FSN/tag, duplicates).\n"
                    + "Cover every source clinical term exactly once via (A) or (B).\n";
        }

        return ""
                + "You are a clinical terminology assistant helping build a code system.\n"
                + "You must use a *grounded* candidate pool.\n\n"
                + "Given unmatched terms and existing matched SNOMED CT codes, do:\n"
                + "1) For each unmatched term, select the best recommendedSnomedId from the candidate pool.\n"
                + "2) Select additional complementary SNOMED codes from the same candidate pool.\n\n"
                + commonConstraints
                + "Candidate pool (grounded): [" + candidatePoolJson + "]\n\n"
                + jsonShape
                + "Only output valid JSON.\n"
                + "Context: " + ctx + "\n"
                + "Unmatched terms: [" + unmatchedList + "]\n"
                + "Matched SNOMED IDs: [" + matchedList + "]\n";
    }

    private AiRecommendationResponse createGroundedFallbackResponse(
            List<String> unmatchedTerms,
            List<String> matchedSnomedIds,
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool,
            boolean suppressDeterministicAdditionalPool) {

        if (additionalCandidatePool == null || additionalCandidatePool.isEmpty()) {
            return new AiRecommendationResponse(List.of(), List.of());
        }
        // Additional-suggestions-only (e.g. ADDITIONAL mode): no unmatched rows — return pool minus matches.
        if (unmatchedTerms == null || unmatchedTerms.isEmpty()) {
            if (suppressDeterministicAdditionalPool) {
                return new AiRecommendationResponse(List.of(), List.of());
            }
            return new AiRecommendationResponse(
                    List.of(),
                    suggestedAdditionalExcludingMatched(matchedSnomedIds, additionalCandidatePool, maxHierarchicalResults));
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
            List<String> matchedSnomedIds,
            List<AiRecommendationResponse.SuggestedTerm> additionalCandidatePool,
            boolean suppressDeterministicAdditionalPool) {

        if (parsed == null) {
            return createGroundedFallbackResponse(unmatchedTerms, matchedSnomedIds, additionalCandidatePool, suppressDeterministicAdditionalPool);
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
            return createGroundedFallbackResponse(unmatchedTerms, matchedSnomedIds, additionalCandidatePool, false);
        }

        // Model often omits suggestedAdditional when it focuses on recommendations (UNMATCHED/BOTH).
        // Still return grounded pool neighbors excluding already-matched ids unless ai-only ADDITIONAL is enabled.
        List<AiRecommendationResponse.SuggestedTerm> finalSuggested = filteredSuggested;
        if (finalSuggested.isEmpty() && !suppressDeterministicAdditionalPool) {
            finalSuggested = suggestedAdditionalExcludingMatched(
                    matchedSnomedIds, additionalCandidatePool, maxHierarchicalResults);
        }

        return new AiRecommendationResponse(filteredRecs, finalSuggested);
    }
    
    // --- FHIR helper methods ---

    private String extractFhirParameterString(Map<String, Object> body, String paramName) {
        Object params = body.get("parameter");
        if (!(params instanceof List)) return null;
        for (Object p : (List<?>) params) {
            if (p instanceof Map<?, ?> pm) {
                if (paramName.equals(pm.get("name"))) {
                    Object v = pm.get("valueString");
                    if (v != null) return v.toString();
                }
            }
        }
        return null;
    }

    private List<String> extractFhirPropertyCodes(Map<String, Object> body, String propertyCode) {
        List<String> codes = new ArrayList<>();
        Object params = body.get("parameter");
        if (!(params instanceof List)) return codes;
        for (Object p : (List<?>) params) {
            if (!(p instanceof Map<?, ?> pm)) continue;
            if (!"property".equals(pm.get("name"))) continue;
            Object parts = pm.get("part");
            if (!(parts instanceof List)) continue;
            String foundCode = null;
            String foundValue = null;
            for (Object part : (List<?>) parts) {
                if (!(part instanceof Map<?, ?> partMap)) continue;
                String name = (String) partMap.get("name");
                if ("code".equals(name) && propertyCode.equals(partMap.get("valueCode"))) {
                    foundCode = propertyCode;
                }
                if ("value".equals(name)) {
                    Object vc = partMap.get("valueCode");
                    if (vc != null) foundValue = vc.toString();
                }
            }
            if (foundCode != null && foundValue != null) {
                codes.add(foundValue);
            }
        }
        return codes;
    }

    /**
     * Search SNOMED CT via FHIR ValueSet/$expand — works on any FHIR terminology server.
     * Used to seed the candidate pool when Snowstorm-based fuzzy matching returns nothing.
     */
    private List<CandidateRecommendation> searchViaFhirExpand(String term, int count) {
        List<CandidateRecommendation> results = new ArrayList<>();
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(fhirBaseUrl)
                    .path("/ValueSet/$expand")
                    .queryParam("url", "http://snomed.info/sct?fhir_vs")
                    .queryParam("filter", term)
                    .queryParam("count", count);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.valueOf("application/fhir+json"), MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    urlBuilder.toUriString(), HttpMethod.GET, req,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Map<String, Object> body = resp.getBody();
                Object expansion = body.get("expansion");
                if (expansion instanceof Map<?, ?> expMap) {
                    Object contains = expMap.get("contains");
                    if (contains instanceof List<?> containsList) {
                        for (Object item : containsList) {
                            if (!(item instanceof Map<?, ?> entry)) continue;
                            Boolean inactive = (Boolean) entry.get("inactive");
                            if (Boolean.TRUE.equals(inactive)) continue;
                            String code = (String) entry.get("code");
                            String display = (String) entry.get("display");
                            if (code != null && display != null) {
                                double sim = jw.apply(term.toLowerCase(), display.toLowerCase());
                                results.add(new CandidateRecommendation(code, display, display, sim, "FHIR search", term));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error searching FHIR ValueSet/$expand for '" + term + "': " + e.getMessage());
        }
        return results;
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

