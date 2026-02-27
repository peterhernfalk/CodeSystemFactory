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
 * SNOMED CT service implementation for Inera Terminologitjänsten (Swedish FHIR terminology server).
 * Uses FHIR ValueSet $expand operation with the Swedish edition ValueSet.
 */
@Service
public class IneraSnomedService implements SnomedService {

    private final String ineraUrl;
    private final String valuesetUrl;
    private final RestTemplate rest;
    private final JaroWinklerSimilarity jw = new JaroWinklerSimilarity();

    public IneraSnomedService(
            @Value("${inera.url:https://terminologitjansten.inera.se/fhir}") String ineraUrl,
            @Value("${inera.valueset-url:http://snomed.info/sct/45991000052106/version/20251130?fhir_vs}") String valuesetUrl) {

        this.ineraUrl = ineraUrl == null || ineraUrl.isBlank()
            ? ""
            : ineraUrl.endsWith("/") ? ineraUrl.substring(0, ineraUrl.length() - 1) : ineraUrl;
        this.valuesetUrl = valuesetUrl == null || valuesetUrl.isBlank()
            ? "http://snomed.info/sct/45991000052106/version/20251130?fhir_vs"
            : valuesetUrl;
        this.rest = new RestTemplate();

        System.out.println("Inera Terminologitjänsten Configuration loaded:");
        System.out.println("  URL: " + this.ineraUrl);
        System.out.println("  ValueSet: " + this.valuesetUrl);
    }

    @Override
    public List<TermMatch> matchTerms(List<String> terms) {
        if (ineraUrl.isEmpty()) {
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
            String altTerm = getAlternativeSearchTerm(searchTerm);
            if (altTerm != null && !termsToTry.contains(altTerm)) {
                termsToTry.add(altTerm);
            }

            List<Map<String, Object>> concepts = null;
            for (String tryTerm : termsToTry) {
                concepts = searchViaValueSetExpand(tryTerm);
                if (concepts != null && !concepts.isEmpty()) {
                    break;
                }
            }

            if (concepts != null && !concepts.isEmpty()) {
                TermMatch bestMatch = findBestMatch(concepts, term);
                if (bestMatch != null && bestMatch.similarity() >= 0.75) {
                    return bestMatch;
                }
            }

            return new TermMatch(term, null, null, null, 0.0, "NO_MATCH");

        } catch (Exception e) {
            System.err.println("Error searching Inera for term '" + term + "': " + e.getMessage());
            return new TermMatch(term, null, null, null, 0.0, "NO_MATCH");
        }
    }

    private String getAlternativeSearchTerm(String term) {
        String lower = term.toLowerCase().trim();
        if (lower.equals("astma")) return "Asthma";
        if (lower.contains("fotledsfraktur") || lower.contains("ankelfraktur")) return "ankle fracture";
        if (lower.contains("mri") && lower.contains("heart")) return "heart mri";
        if (lower.contains("hjärt") && lower.contains("mri")) return "heart mri";
        return null;
    }

    private List<Map<String, Object>> searchViaValueSetExpand(String term) {
        try {
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(ineraUrl)
                .path("/ValueSet/$expand")
                .queryParam("url", valuesetUrl)
                .queryParam("filter", term)
                .queryParam("count", "50");

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.parseMediaType("application/fhir+json")));
            HttpEntity<Void> req = new HttpEntity<>(headers);

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
                        System.out.println("DEBUG: Inera found " + concepts.size() + " concepts for '" + term + "'");
                        return concepts;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error calling Inera ValueSet $expand: " + e.getMessage());
        }
        return null;
    }

    private TermMatch findBestMatch(List<Map<String, Object>> concepts, String inputTerm) {
        TermMatch bestMatch = null;
        double bestScore = 0.0;
        String inputLower = inputTerm.toLowerCase().trim();

        for (Map<String, Object> concept : concepts) {
            String code = (String) concept.get("code");
            String display = (String) concept.get("display");
            if (code == null || display == null) continue;

            String displayLower = display.toLowerCase();
            double similarity = jw.apply(inputLower, displayLower);
            if (displayLower.equals(inputLower)) {
                similarity = 1.0;
            } else if (displayLower.startsWith(inputLower + " ") || displayLower.startsWith(inputLower + "(")) {
                similarity = Math.max(similarity, 0.95);
            } else if (displayLower.startsWith(inputLower) || inputLower.startsWith(displayLower)) {
                similarity = Math.min(similarity + 0.1, 1.0);
            }
            if (inputLower.equals("astma") && displayLower.contains("asthma")) {
                similarity = Math.max(similarity, 0.95);
            }
            if ((inputLower.contains("fotledsfraktur") || inputLower.contains("ankelfraktur"))
                && displayLower.contains("fracture") && displayLower.contains("ankle")) {
                similarity = Math.max(similarity, 0.90);
            }

            if (similarity > bestScore) {
                bestScore = similarity;
                bestMatch = new TermMatch(inputTerm, code, display, display, similarity, "MATCHED");
            }
        }
        return bestMatch;
    }
}
