package com.example.codesys.controller;

import com.example.codesys.model.AiDefinitionEntity;
import com.example.codesys.model.AiDefinitionRequest;
import com.example.codesys.model.AiDefinitionResponse;
import com.example.codesys.repository.AiDefinitionRepository;
import com.example.codesys.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin
public class AiController {
    private final AiService aiService;
    private final AiDefinitionRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiController(AiService aiService, AiDefinitionRepository repo){ this.aiService=aiService; this.repo = repo; }

    @PostMapping(value="/definitions", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public AiDefinitionResponse define(@Valid @RequestBody AiDefinitionRequest request){
        AiDefinitionResponse response = aiService.defineTerms(request);
        try {
            List<AiDefinitionEntity> entities = response.results().stream().map(r -> {
                String relationsJson = safeWrite(r.relations());
                String useCasesJson = safeWrite(r.useCases());
                return new AiDefinitionEntity(r.term(), r.snomedId(), r.definition(), relationsJson, r.motivation(), useCasesJson);
            }).toList();
            repo.saveAll(entities);
        } catch (Exception e){
        }
        return response;
    }

    private String safeWrite(Object o){
        try { return mapper.writeValueAsString(o); }
        catch (Exception e){ return "[]"; }
    }
}
