package com.example.codesys.controller;

import com.example.codesys.model.CodeSystemBuildRequest;
import com.example.codesys.model.CodeSystemBuildResponse;
import com.example.codesys.model.CodeSystemExportRequest;
import com.example.codesys.service.CodeSystemBuildService;
import com.example.codesys.service.CodeSystemExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/codesystems")
@CrossOrigin
public class CodeSystemStatelessController {
    
    private final CodeSystemBuildService buildService;
    private final CodeSystemExportService exportService;
    
    public CodeSystemStatelessController(
            CodeSystemBuildService buildService,
            CodeSystemExportService exportService) {
        this.buildService = buildService;
        this.exportService = exportService;
    }
    
    @PostMapping(
        value = "/build",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CodeSystemBuildResponse build(@Valid @RequestBody CodeSystemBuildRequest request) {
        return buildService.build(request);
    }
    
    @PostMapping(value = "/export")
    public ResponseEntity<?> export(@Valid @RequestBody CodeSystemExportRequest request) {
        CodeSystemExportService.ExportResult result = exportService.export(request);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.contentType()));
        headers.setContentDispositionFormData("attachment", result.filename());
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(result.content());
    }
}

