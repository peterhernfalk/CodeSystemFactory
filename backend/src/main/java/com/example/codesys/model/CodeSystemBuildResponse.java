package com.example.codesys.model;

import java.util.List;

public record CodeSystemBuildResponse(
    CodeSystem codeSystem
) {
    public record CodeSystem(
        String name,
        String version,
        String description,
        String publisher,
        String contact,
        List<CodeSystemItem> items
    ) {}
    
    public record CodeSystemItem(
        String code,
        String display,
        String definition,
        List<String> relations,
        String source
    ) {}
}

