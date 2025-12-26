package com.example.codesys.service.impl;

import com.example.codesys.service.SynonymDatabaseService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * In-memory synonym database for Swedish medical terms.
 * Can be extended to load from file or database.
 */
@Service
public class SynonymDatabaseServiceImpl implements SynonymDatabaseService {
    
    // Curated Swedish→SNOMED mappings
    // Format: Swedish term -> SNOMED concept ID
    private final Map<String, String> synonymMap;
    
    // Reverse mapping: SNOMED ID -> Swedish terms
    private final Map<String, Set<String>> reverseMap;
    
    // Synonym groups (terms that are synonyms of each other)
    private final Map<String, Set<String>> synonymGroups;

    public SynonymDatabaseServiceImpl() {
        this.synonymMap = new HashMap<>();
        this.reverseMap = new HashMap<>();
        this.synonymGroups = new HashMap<>();
        
        initializeSynonyms();
    }
    
    private void initializeSynonyms() {
        // Common Swedish medical term mappings
        // These can be loaded from a file or database in production
        
        // Diabetes
        addMapping("sockersjuka", "73211009"); // Diabetes mellitus
        addMapping("diabetes", "73211009");
        addSynonymGroup("sockersjuka", "diabetes", "diabetes mellitus");
        
        // Heart attack
        addMapping("hjärtattack", "22298006"); // Myocardial infarction
        addMapping("hjärtinfarkt", "22298006");
        addSynonymGroup("hjärtattack", "hjärtinfarkt", "myocardial infarction", "heart attack");
        
        // Cardiac MRI
        addMapping("hjärt-mri", "433390000"); // Cardiac MRI (approximate)
        addMapping("hjärtmri", "433390000");
        addMapping("kardiell mri", "433390000");
        addSynonymGroup("hjärt-mri", "hjärtmri", "kardiell mri", "cardiac mri", "mri heart");
        
        // Hypertension
        addMapping("högt blodtryck", "38341003"); // Hypertensive disorder
        addMapping("hypertoni", "38341003");
        addSynonymGroup("högt blodtryck", "hypertoni", "hypertension");
        
        // Pneumonia
        addMapping("lunginflammation", "233604007"); // Pneumonia
        addMapping("pneumoni", "233604007");
        addSynonymGroup("lunginflammation", "pneumoni", "pneumonia");
        
        // Stroke
        addMapping("stroke", "230690007"); // Cerebrovascular accident
        addMapping("slaganfall", "230690007");
        addSynonymGroup("stroke", "slaganfall", "cerebrovascular accident");
        
        // Asthma
        addMapping("astma", "195967001"); // Asthma
        addSynonymGroup("astma", "asthma");
        
        // Ankle fracture
        addMapping("fotledsfraktur", "125605004"); // Fracture of ankle (approximate - may need correct SNOMED ID)
        addMapping("ankelfraktur", "125605004");
        addSynonymGroup("fotledsfraktur", "ankelfraktur", "ankle fracture", "fracture of ankle");
        
        // Add more mappings as needed...
    }
    
    private void addMapping(String swedishTerm, String snomedId) {
        String normalized = normalizeTerm(swedishTerm);
        synonymMap.put(normalized, snomedId);
        
        reverseMap.computeIfAbsent(snomedId, k -> new HashSet<>()).add(normalized);
    }
    
    private void addSynonymGroup(String... terms) {
        Set<String> group = new HashSet<>(Arrays.asList(terms));
        for (String term : terms) {
            String normalized = normalizeTerm(term);
            synonymGroups.put(normalized, group);
        }
    }
    
    private String normalizeTerm(String term) {
        return term.toLowerCase().trim();
    }
    
    @Override
    public Optional<String> lookup(String swedishTerm) {
        String normalized = normalizeTerm(swedishTerm);
        return Optional.ofNullable(synonymMap.get(normalized));
    }
    
    @Override
    public List<String> getSynonyms(String term) {
        String normalized = normalizeTerm(term);
        Set<String> synonyms = synonymGroups.get(normalized);
        if (synonyms != null) {
            return new ArrayList<>(synonyms);
        }
        return Collections.emptyList();
    }
    
    /**
     * Get all Swedish terms mapped to a SNOMED concept.
     */
    public Set<String> getTermsForConcept(String snomedId) {
        return reverseMap.getOrDefault(snomedId, Collections.emptySet());
    }
}

