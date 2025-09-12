package com.example.codesys.service.impl;

import com.example.codesys.model.TermMatch;
import com.example.codesys.service.SnomedService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FhirSnomedService implements SnomedService {

    private final String fhirServerUrl;
    private final RestTemplate rest;

    public FhirSnomedService(@Value("${fhir.server.url:}") String fhirServerUrl) {
        this.fhirServerUrl = fhirServerUrl == null ? "" : fhirServerUrl.trim();
        this.rest = new RestTemplate();
    }

    @Override
    public List<TermMatch> matchTerms(List<String> terms) {
        if (fhirServerUrl.isEmpty()) {
            return terms.stream().map(t -> new TermMatch(t, null, null, null, 0.0, "NO_MATCH")).collect(Collectors.toList());
        }
        List<TermMatch> results = new ArrayList<>();
        for (String t : terms) {
            results.add(lookupByName(t));
        }
        return results;
    }

    private TermMatch lookupByName(String term) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(fhirServerUrl)
                    .path("/CodeSystem")
                    .queryParam("title", term)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.GET, req, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object entriesObj = resp.getBody().get("entry");
                if (entriesObj instanceof List && !((List) entriesObj).isEmpty()) {
                    Object first = ((List) entriesObj).get(0);
                    if (first instanceof Map bundleEntry) {
                        Object resource = bundleEntry.get("resource");
                        if (resource instanceof Map resourceMap) {
                            String id = (String) resourceMap.get("id");
                            String title = (String) resourceMap.get("title");
                            String version = (String) resourceMap.get("version");
                            String prefer = title != null ? title : term;
                            String fsn = prefer + (version != null ? " (" + version + ")" : "");
                            return new TermMatch(term, id, prefer, fsn, 0.90, "MATCHED");
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // log/ignore
        }
        return new TermMatch(term, null, null, null, 0.0, "NO_MATCH");
    }
}
