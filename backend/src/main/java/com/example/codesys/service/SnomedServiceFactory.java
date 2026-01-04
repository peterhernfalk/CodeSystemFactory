package com.example.codesys.service;

import com.example.codesys.service.impl.FhirSnomedService;
import com.example.codesys.service.impl.OntoserverSnomedService;
import org.springframework.stereotype.Component;

/**
 * Factory for selecting the appropriate SNOMED CT service based on user choice.
 * Supports multiple terminology servers (Snowstorm, Ontoserver, etc.)
 */
@Component
public class SnomedServiceFactory {
    
    private final FhirSnomedService snowstormService;
    private final OntoserverSnomedService ontoserverService;
    
    public SnomedServiceFactory(
            FhirSnomedService snowstormService,
            OntoserverSnomedService ontoserverService) {
        this.snowstormService = snowstormService;
        this.ontoserverService = ontoserverService;
    }
    
    /**
     * Get the appropriate SNOMED service based on server selection.
     * 
     * @param server Server identifier: "snowstorm", "ontoserver", or null/empty for default
     * @return SnomedService implementation
     */
    public SnomedService getService(String server) {
        if (server == null || server.trim().isEmpty()) {
            return snowstormService; // Default to Snowstorm
        }
        
        String serverLower = server.toLowerCase().trim();
        switch (serverLower) {
            case "ontoserver":
            case "ontoserver-fhir":
                return ontoserverService;
            case "snowstorm":
            case "snowstorm-native":
            default:
                return snowstormService;
        }
    }
}

