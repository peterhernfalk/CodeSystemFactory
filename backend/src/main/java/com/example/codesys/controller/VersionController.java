package com.example.codesys.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for version information.
 * Exposes application version from Maven pom.xml via build-info.properties.
 */
@RestController
@RequestMapping("/api/version")
@CrossOrigin
public class VersionController {

    private final Optional<BuildProperties> buildProperties;
    
    @Value("${spring.application.name:codesys-backend}")
    private String applicationName;

    public VersionController(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    /**
     * Get application version information.
     * 
     * @return Map containing version and application name
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getVersion() {
        Map<String, String> versionInfo = new HashMap<>();
        
        // Get version from BuildProperties (from build-info.properties)
        // Falls back to default if BuildProperties is not available
        String version = buildProperties
            .map(BuildProperties::getVersion)
            .orElse("0.0.1-SNAPSHOT");
        
        versionInfo.put("version", version);
        versionInfo.put("application", applicationName);
        return versionInfo;
    }
}

