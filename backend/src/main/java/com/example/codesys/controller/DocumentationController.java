package com.example.codesys.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for API documentation endpoints.
 * Provides easy access to Swagger UI and OpenAPI documentation.
 */
@RestController
@RequestMapping("/docs")
@CrossOrigin
public class DocumentationController {

    /**
     * Redirect endpoint that provides information about available documentation.
     * 
     * @return Map with documentation URLs and descriptions
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDocumentationInfo() {
        Map<String, Object> docs = new HashMap<>();
        docs.put("message", "API Documentation");
        docs.put("swagger-ui", Map.of(
            "url", "/swagger-ui.html",
            "description", "Interactive Swagger UI for exploring and testing all API endpoints",
            "alternative-url", "/swagger-ui/index.html"
        ));
        docs.put("openapi-spec", Map.of(
            "url", "/api-docs",
            "description", "OpenAPI 3.0 specification in JSON format",
            "format", "application/json"
        ));
        docs.put("endpoints", Map.of(
            "/api/terms/match", "POST - Match terms with SNOMED CT",
            "/api/ai/recommend", "POST - Get AI recommendations for unmatched terms",
            "/api/ai/definitions", "POST - Get AI definitions for terms",
            "/api/codesystems/build", "POST - Build a code system",
            "/api/codesystems/export", "POST - Export code system in various formats"
        ));
        
        return ResponseEntity.ok(docs);
    }

    /**
     * Simple redirect endpoint to Swagger UI.
     * Returns a redirect response that browsers will follow.
     */
    @GetMapping("/swagger")
    public ResponseEntity<Map<String, String>> redirectToSwagger() {
        Map<String, String> response = new HashMap<>();
        response.put("redirect", "/swagger-ui.html");
        response.put("message", "Navigate to /swagger-ui.html for Swagger UI");
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
            .header("Location", "/swagger-ui.html")
            .body(response);
    }
}

