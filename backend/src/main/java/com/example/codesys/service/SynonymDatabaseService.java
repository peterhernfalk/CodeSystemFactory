package com.example.codesys.service;

import java.util.Optional;

/**
 * Service for managing Swedish→SNOMED CT synonym mappings.
 * Provides curated mappings for common Swedish medical terms.
 */
public interface SynonymDatabaseService {
    /**
     * Look up a SNOMED CT concept ID for a Swedish term.
     * @param swedishTerm The Swedish term to look up
     * @return Optional SNOMED CT concept ID if found
     */
    Optional<String> lookup(String swedishTerm);
    
    /**
     * Get all known synonyms for a term (for fuzzy matching).
     * @param term The term to find synonyms for
     * @return List of alternative terms to try
     */
    java.util.List<String> getSynonyms(String term);
}

