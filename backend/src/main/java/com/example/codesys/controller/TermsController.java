package com.example.codesys.controller;

import com.example.codesys.model.*;
import com.example.codesys.service.SnomedService;
import com.example.codesys.service.SnomedServiceFactory;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/terms")
@CrossOrigin
public class TermsController {
    private final SnomedServiceFactory serviceFactory;

    public TermsController(SnomedServiceFactory serviceFactory){
        this.serviceFactory = serviceFactory;
    }

    @PostMapping(value="/match", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public TermMatchResponse match(@Valid @RequestBody TermRequest request){
        // Get appropriate service based on user selection (defaults to Snowstorm)
        SnomedService snomedService = serviceFactory.getService(request.server());
        List<TermMatch> matches = snomedService.matchTerms(request.terms());
        
        // Separate matched and unmatched terms
        List<MatchedTerm> matched = matches.stream()
                .filter(m -> "MATCHED".equals(m.status()))
                .map(m -> new MatchedTerm(
                    m.input(),
                    m.matchedSctId(),
                    m.preferredTermSv(),
                    m.fsnSv(),
                    m.similarity(),
                    null // Description can be added later if needed
                ))
                .collect(Collectors.toList());
        
        List<UnmatchedTerm> unmatched = matches.stream()
                .filter(m -> "NO_MATCH".equals(m.status()))
                .map(m -> new UnmatchedTerm(
                    m.input(),
                    "No match found in SNOMED CT (similarity < 0.75)"
                ))
                .collect(Collectors.toList());
        
        return new TermMatchResponse(matched, unmatched);
    }
}
