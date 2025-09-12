package com.example.codesys.controller;

import com.example.codesys.model.TermMatchEntity;
import com.example.codesys.model.TermMatchResponse;
import com.example.codesys.model.TermRequest;
import com.example.codesys.model.TermMatch;
import com.example.codesys.repository.TermMatchRepository;
import com.example.codesys.service.SnomedService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/terms")
@CrossOrigin
public class TermsController {
    private final SnomedService snomedService;
    private final TermMatchRepository repo;

    public TermsController(SnomedService snomedService, TermMatchRepository repo){
        this.snomedService = snomedService;
        this.repo = repo;
    }

    @PostMapping(value="/match", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public TermMatchResponse match(@Valid @RequestBody TermRequest request){
        List<TermMatch> matches = snomedService.matchTerms(request.terms());
        List<TermMatchEntity> entities = matches.stream()
                .map(m -> new TermMatchEntity(m.input(), m.matchedSctId(), m.preferredTermSv(), m.fsnSv(), m.similarity(), m.status()))
                .collect(Collectors.toList());
        repo.saveAll(entities);
        return new TermMatchResponse(matches);
    }
}
