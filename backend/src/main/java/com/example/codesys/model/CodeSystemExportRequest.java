package com.example.codesys.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

public record CodeSystemExportRequest(
    @NotNull(message = "Code system is required")
    @Valid
    CodeSystemExportRequest.CodeSystem codeSystem,
    
    @NotNull(message = "Format is required")
    ExportFormat format
) {
    public record CodeSystem(
        String name,
        String version,
        String description,
        String publisher,
        String contact,
        java.util.List<CodeSystemItem> items
    ) {}
    
    public record CodeSystemItem(
        String code,
        String display,
        String definition,
        java.util.List<String> relations,
        String source
    ) {}
    
    public enum ExportFormat {
        FHIR, CSV, EXCEL
    }
}

