package com.example.codesys.controller;

import com.example.codesys.model.AiDefinitionRequest;
import com.example.codesys.model.AiDefinitionResponse;
import com.example.codesys.service.AiService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService){
        this.aiService = aiService;
    }

    @PostMapping(value="/definitions", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public AiDefinitionResponse define(@Valid @RequestBody AiDefinitionRequest request){
        return aiService.defineTerms(request);
    }
}
