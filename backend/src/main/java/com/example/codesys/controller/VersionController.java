package com.example.codesys.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for version information.
 * Exposes application version from Maven pom.xml.
 */
@RestController
@RequestMapping("/api/version")
@CrossOrigin
public class VersionController {

    // Version is injected by Maven at build time via spring-boot-maven-plugin
    // The buildInfo generates build-info.properties with info.build.version
    // Falls back to project.version from pom.xml, or default if not available
    @Value("${info.build.version:${project.version:0.0.1-SNAPSHOT}}")
    private String version;

    @Value("${spring.application.name:codesys-backend}")
    private String applicationName;

    /**
     * Get application version information.
     * 
     * @return Map containing version and application name
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getVersion() {
        Map<String, String> versionInfo = new HashMap<>();
        versionInfo.put("version", version);
        versionInfo.put("application", applicationName);
        return versionInfo;
    }
}

