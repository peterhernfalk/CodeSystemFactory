package com.example.codesys.service.impl;

import com.example.codesys.model.TermMatch;
import com.example.codesys.service.SnomedService;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SNOMED CT service implementation for Ontoserver (FHIR R4 terminology server).
 * Uses FHIR ValueSet $expand operation for concept search.
 */
@Service
public class OntoserverSnomedService implements SnomedService {

    private final String ontoserverUrl;
    private final RestTemplate rest;
    private final JaroWinklerSimilarity jw = new JaroWinklerSimilarity();
    private final String language;
    private final boolean fallbackToEnglish;
    
    // SNOMED CT ValueSet URL for all active concepts
    private static final String SNOMED_VALUESET_URL = "http://snomed.info/sct/900000000000207008?fhir_vs";

    public OntoserverSnomedService(
            @Value("${ontoserver.url:https://r4.ontoserver.csiro.au/fhir}") String ontoserverUrl,
            @Value("${snomed.language:en}") String language,
            @Value("${snomed.fallback-to-english:true}") boolean fallbackToEnglish) {
        
        this.ontoserverUrl = ontoserverUrl.endsWith("/") 
            ? ontoserverUrl.substring(0, ontoserverUrl.length() - 1) 
            : ontoserverUrl;
        this.rest = new RestTemplate();
        this.language = language;
        this.fallbackToEnglish = fallbackToEnglish;
        
        System.out.println("Ontoserver Configuration loaded:");
        System.out.println("  URL: " + this.ontoserverUrl);
        System.out.println("  Language: " + this.language);
        System.out.println("  Fallback to English: " + this.fallbackToEnglish);
    }

    @Override
    public List<TermMatch> matchTerms(List<String> terms) {
        if (ontoserverUrl.isEmpty()) {
            return terms.stream()
                .map(t -> new TermMatch(t, null, null, null, 0.0, "NO_MATCH"))
                .collect(Collectors.toList());
        }
        
        List<TermMatch> results = new ArrayList<>();
        for (String term : terms) {
            results.add(searchByTerm(term));
        }
        return results;
    }

    private TermMatch searchByTerm(String term) {
        try {
            String searchTerm = term.trim();
            List<String> termsToTry = new ArrayList<>();
            termsToTry.add(searchTerm);
            if (!searchTerm.equals(searchTerm.toLowerCase())) {
                termsToTry.add(searchTerm.toLowerCase());
            }
            
            // Try preferred language first, then English if enabled
            List<String> languagesToTry = new ArrayList<>();
            languagesToTry.add(language);
            if (fallbackToEnglish && !"en".equals(language)) {
                languagesToTry.add("en");
            }
            
            List<Map<String, Object>> concepts = null;
            String matchedLanguage = null;
            
            for (String tryLanguage : languagesToTry) {
                for (String tryTerm : termsToTry) {
                    concepts = searchViaValueSetExpand(tryTerm, tryLanguage);
                    if (concepts != null && !concepts.isEmpty()) {
                        matchedLanguage = tryLanguage;
                        break;
                    }
                }
                if (concepts != null && !concepts.isEmpty()) {
                    break;
                }
            }
            
            if (concepts != null && !concepts.isEmpty()) {
                // Find best match by similarity
                TermMatch bestMatch = findBestMatch(concepts, term, matchedLanguage);
                if (bestMatch != null && bestMatch.similarity() >= 0.75) {
                    return bestMatch;
                }
            }
            
            // No match found
            return new TermMatch(term, null, null, null, 0.0, "NO_MATCH");
            
        } catch (Exception e) {
            System.err.println("Error searching Ontoserver for term '" + term + "': " + e.getMessage());
            return new TermMatch(term, null, null, null, 0.0, "NO_MATCH");
        }
    }

    /**
     * Search concepts using FHIR ValueSet $expand operation
     */
    private List<Map<String, Object>> searchViaValueSetExpand(String term, String language) {
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(ontoserverUrl)
                .path("/ValueSet/$expand")
                .queryParam("url", SNOMED_VALUESET_URL)
                .queryParam("filter", term)
                .queryParam("count", "50");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.parseMediaType("application/fhir+json")));
            headers.setAcceptLanguage(java.util.Locale.LanguageRange.parse(language));
            HttpEntity<Void> req = new HttpEntity<>(headers);
            
            System.out.println("DEBUG: Ontoserver search URL: " + urlBuilder.toUriString() + " (language: " + language + ")");
            
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                urlBuilder.toUriString(),
                HttpMethod.GET,
                req,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Map<String, Object> valueSet = resp.getBody();
                Object expansion = valueSet.get("expansion");
                
                if (expansion instanceof Map) {
                    Object contains = ((Map<?, ?>) expansion).get("contains");
                    if (contains instanceof List) {
                        List<Map<String, Object>> concepts = new ArrayList<>();
                        for (Object item : (List<?>) contains) {
                            if (item instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> concept = (Map<String, Object>) item;
                                concepts.add(concept);
                            }
                        }
                        System.out.println("DEBUG: Ontoserver found " + concepts.size() + " concepts for '" + term + "'");
                        return concepts;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error calling Ontoserver ValueSet $expand: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Find best matching concept from FHIR ValueSet expansion results
     */
    private TermMatch findBestMatch(List<Map<String, Object>> concepts, String inputTerm, String preferredLanguage) {
        TermMatch bestMatch = null;
        double bestScore = 0.0;
        String inputLower = inputTerm.toLowerCase().trim();
        
        for (Map<String, Object> concept : concepts) {
            String code = (String) concept.get("code");
            String display = (String) concept.get("display");
            
            if (code == null || display == null) {
                continue;
            }
            
            // Calculate similarity
            String displayLower = display.toLowerCase();
            double similarity = jw.apply(inputLower, displayLower);
            
            // Boost score for exact or close matches
            if (displayLower.equals(inputLower)) {
                similarity = 1.0;
            } else if (displayLower.startsWith(inputLower) || inputLower.startsWith(displayLower)) {
                similarity = Math.min(similarity + 0.1, 1.0);
            }
            
            if (similarity > bestScore) {
                bestScore = similarity;
                
                // For Ontoserver, we only get code and display
                // FSN might not be available in the expansion response
                // We could make a separate $lookup call to get full details if needed
                String fsn = display; // Use display as FSN fallback
                
                bestMatch = new TermMatch(
                    inputTerm,
                    code,
                    display,  // Preferred term
                    fsn,      // FSN (same as display if not available)
                    similarity,
                    "MATCHED"
                );
            }
        }
        
        return bestMatch;
    }
}

