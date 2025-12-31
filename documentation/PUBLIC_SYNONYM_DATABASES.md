# Publicly Available Synonym Databases with APIs

This document lists publicly available synonym databases with APIs that could replace the hardcoded in-memory synonym database solution.

## Overview

There are several publicly available medical terminology APIs that provide synonym/description lookup capabilities. However, **Swedish language support** is limited in most public APIs.

---

## 1. SNOMED CT via Snowstorm (Already in Use) ⭐ RECOMMENDED

**What it is**: SNOMED CT itself contains synonyms and descriptions in multiple languages, accessible through Snowstorm API.

**API**: Snowstorm REST API (already integrated in your application)

**Swedish Support**: ✅ Yes (if Swedish language module is loaded)

**How to Use**:
```java
// Get all descriptions (including synonyms) for a concept
GET /snowstorm/snomed-ct/MAIN/concepts/{conceptId}

// Response includes:
{
  "conceptId": "125605004",
  "descriptions": [
    {
      "term": "Fracture of bone",
      "type": "SYNONYM",
      "lang": "en",
      "active": true
    },
    {
      "term": "Fotledsfraktur",
      "type": "SYNONYM", 
      "lang": "sv",
      "active": true
    }
  ]
}
```

**Advantages**:
- ✅ Already integrated in your application
- ✅ Official SNOMED CT data
- ✅ Supports multiple languages (if language modules loaded)
- ✅ Free (if using public Snowstorm instance)
- ✅ Comprehensive synonym coverage

**Disadvantages**:
- ⚠️ Requires Snowstorm server with Swedish language module
- ⚠️ API calls needed (not instant lookup)
- ⚠️ May not have all Swedish synonyms

**Implementation**: You're already using this! The `FhirSnomedService` extracts descriptions which include synonyms.

**Recommendation**: **Enhance current Snowstorm usage** to extract and cache synonyms from descriptions.

---

## 2. UMLS (Unified Medical Language System) Metathesaurus

**What it is**: Large biomedical terminology database maintained by NLM (US National Library of Medicine).

**API**: UMLS REST API (requires registration)

**Swedish Support**: ⚠️ Limited (primarily English)

**Access**:
- **Registration**: Required (free for research/educational use)
- **API Base URL**: `https://uts-ws.nlm.nih.gov/rest`
- **Documentation**: https://documentation.uts.nlm.nih.gov/

**How to Use**:
```java
// Search for concept
GET https://uts-ws.nlm.nih.gov/rest/search/current?string={term}&apiKey={apiKey}

// Get synonyms for a concept
GET https://uts-ws.nlm.nih.gov/rest/content/current/CUI/{cui}/atoms?apiKey={apiKey}
```

**Advantages**:
- ✅ Comprehensive medical terminology
- ✅ Multiple vocabularies (SNOMED, ICD, MeSH, etc.)
- ✅ Synonym support
- ✅ Free for research/educational use

**Disadvantages**:
- ⚠️ Requires API key registration
- ⚠️ Limited Swedish language support
- ⚠️ Rate limits on free tier
- ⚠️ More complex integration

**Swedish Support**: Very limited - primarily English terms.

---

## 3. FHIR Terminology Service APIs

**What it is**: FHIR-compliant terminology services that provide concept lookup and synonyms.

**Examples**:
- **Ontoserver** (Australia): https://ontoserver.csiro.au/
- **HAPI FHIR Terminology Server**: Various public instances
- **Snowstorm FHIR API** (already using): `/fhir/CodeSystem/...`

**Swedish Support**: ⚠️ Depends on server configuration

**How to Use**:
```java
// FHIR CodeSystem lookup
GET /fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code={code}

// FHIR ValueSet expansion (includes synonyms)
GET /fhir/ValueSet/$expand?url={valueset-url}
```

**Advantages**:
- ✅ Standard FHIR interface
- ✅ Some public instances available
- ✅ Synonym support via descriptions

**Disadvantages**:
- ⚠️ Limited public instances
- ⚠️ Swedish support varies
- ⚠️ May require authentication

---

## 4. Medical Subject Headings (MeSH) API

**What it is**: NLM's controlled vocabulary thesaurus for indexing biomedical literature.

**API**: MeSH REST API

**Swedish Support**: ❌ No (English only)

**Access**:
- **Base URL**: `https://id.nlm.nih.gov/mesh/`
- **Documentation**: https://id.nlm.nih.gov/mesh/docs/api.html

**How to Use**:
```java
// Search MeSH terms
GET https://id.nlm.nih.gov/mesh/lookup/descriptor?label={term}&match={matchType}

// Get synonyms (entry terms)
GET https://id.nlm.nih.gov/mesh/lookup/descriptor/{descriptorId}
```

**Advantages**:
- ✅ Free and public
- ✅ Good synonym coverage
- ✅ Well-documented API

**Disadvantages**:
- ❌ No Swedish support
- ⚠️ Not SNOMED CT (different terminology system)
- ⚠️ May need mapping to SNOMED CT

---

## 5. Wikidata Medical Concepts

**What it is**: Wikipedia's structured data project includes medical concepts with multilingual labels.

**API**: Wikidata SPARQL Query Service

**Swedish Support**: ✅ Yes (multilingual)

**Access**:
- **SPARQL Endpoint**: `https://query.wikidata.org/sparql`
- **Documentation**: https://www.wikidata.org/wiki/Wikidata:Main_Page

**How to Use**:
```sparql
# SPARQL query example
SELECT ?item ?itemLabel ?synonym WHERE {
  ?item wdt:P31 wd:Q12136 .  # Instance of disease
  ?item skos:altLabel ?synonym .
  FILTER(LANG(?synonym) = "sv")  # Swedish
  FILTER(CONTAINS(LCASE(?synonym), "fotledsfraktur"))
  SERVICE wikibase:label { bd:serviceParam wikibase:language "sv,en" }
}
```

**Advantages**:
- ✅ Free and public
- ✅ Multilingual (including Swedish)
- ✅ Good coverage of common medical terms
- ✅ Can link to SNOMED CT via properties

**Disadvantages**:
- ⚠️ Requires SPARQL knowledge
- ⚠️ Not specifically medical terminology focused
- ⚠️ May not have all SNOMED CT concepts
- ⚠️ Quality varies (crowd-sourced)

---

## 6. Socialstyrelsen (Swedish National Board of Health and Welfare)

**What it is**: Swedish healthcare terminology resources (may have APIs).

**Swedish Support**: ✅ Yes (Swedish official)

**Access**:
- **Website**: https://www.socialstyrelsen.se/
- **ICD-10-SE**: Swedish version of ICD-10
- **API**: May have terminology services (check their website)

**Advantages**:
- ✅ Official Swedish healthcare terminology
- ✅ Native Swedish support
- ✅ May have SNOMED CT mappings

**Disadvantages**:
- ⚠️ API availability unclear
- ⚠️ May require registration/agreement
- ⚠️ May not be publicly accessible

**Note**: Check Socialstyrelsen's website for available APIs or terminology services.

---

## 7. Custom Solutions Using SNOMED CT Descriptions

**Best Approach**: Extract synonyms directly from SNOMED CT via Snowstorm

**Implementation Strategy**:

1. **Cache SNOMED CT Descriptions**:
   ```java
   // When fetching a concept, extract all synonyms
   GET /snowstorm/snomed-ct/MAIN/concepts/{conceptId}
   
   // Extract all descriptions with type="SYNONYM" and lang="sv"
   // Cache in memory or database
   ```

2. **Build Synonym Index**:
   ```java
   // Create reverse index: Swedish term -> SNOMED ID
   Map<String, String> swedishToSnomed = new HashMap<>();
   
   // For each concept with Swedish descriptions:
   for (Description desc : concept.getDescriptions()) {
       if (desc.getLang().equals("sv") && desc.getType().equals("SYNONYM")) {
           swedishToSnomed.put(desc.getTerm().toLowerCase(), conceptId);
       }
   }
   ```

3. **Pre-load on Startup**:
   - Load common concepts
   - Extract Swedish synonyms
   - Build in-memory index
   - Update periodically

**Advantages**:
- ✅ Uses official SNOMED CT data
- ✅ Native Swedish support (if language module loaded)
- ✅ No external dependencies
- ✅ Can be cached for performance

**Disadvantages**:
- ⚠️ Requires initial load time
- ⚠️ Needs Snowstorm with Swedish module
- ⚠️ Storage requirements for large index

---

## Recommendations

### Option 1: Enhance Current Snowstorm Usage (⭐ BEST)

**Action**: Extract and cache synonyms from SNOMED CT descriptions via Snowstorm API.

**Implementation**:
1. When fetching concepts, extract all Swedish synonyms from descriptions
2. Build in-memory index: `Map<String, String>` (Swedish term → SNOMED ID)
3. Cache for fast lookup
4. Pre-load common concepts on startup

**Code Example**:
```java
@Service
public class SnowstormSynonymService implements SynonymDatabaseService {
    
    private final Map<String, String> synonymCache = new HashMap<>();
    private final SnomedService snomedService;
    
    public void buildSynonymIndex() {
        // Load common concepts
        List<String> commonConcepts = Arrays.asList("73211009", "22298006", ...);
        
        for (String conceptId : commonConcepts) {
            // Fetch concept with all descriptions
            Concept concept = fetchConceptWithDescriptions(conceptId);
            
            // Extract Swedish synonyms
            for (Description desc : concept.getDescriptions()) {
                if ("sv".equals(desc.getLang()) && "SYNONYM".equals(desc.getType())) {
                    synonymCache.put(desc.getTerm().toLowerCase(), conceptId);
                }
            }
        }
    }
}
```

### Option 2: Use Wikidata (For Common Terms)

**Action**: Query Wikidata for Swedish medical term synonyms.

**Use Case**: Supplement SNOMED CT with common Swedish medical terms.

**Implementation**: SPARQL queries to Wikidata endpoint.

### Option 3: Hybrid Approach

**Action**: Combine multiple sources:
1. **Primary**: SNOMED CT descriptions (via Snowstorm) - official, comprehensive
2. **Secondary**: Wikidata - for common terms, multilingual
3. **Fallback**: Hardcoded mappings - for edge cases

---

## Implementation Guide: Enhancing Snowstorm Synonym Extraction

### Step 1: Create Enhanced Synonym Service

```java
@Service
public class SnowstormSynonymServiceImpl implements SynonymDatabaseService {
    
    private final RestTemplate restTemplate;
    private final String snowstormBaseUrl;
    private final Map<String, String> synonymCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> synonymGroups = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        // Pre-load common concepts
        buildSynonymIndex();
    }
    
    private void buildSynonymIndex() {
        // List of common SNOMED CT concepts to pre-load
        List<String> commonConcepts = loadCommonConcepts();
        
        for (String conceptId : commonConcepts) {
            extractSynonymsFromConcept(conceptId);
        }
    }
    
    private void extractSynonymsFromConcept(String conceptId) {
        try {
            // Fetch concept with all descriptions
            String url = snowstormBaseUrl + "/snowstorm/snomed-ct/MAIN/concepts/" + conceptId;
            HttpHeaders headers = new HttpHeaders();
            headers.setAcceptLanguage(Locale.LanguageRange.parse("sv,en"));
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> concept = response.getBody();
                List<Map<String, Object>> descriptions = (List<Map<String, Object>>) 
                    concept.get("descriptions");
                
                Set<String> synonyms = new HashSet<>();
                
                for (Map<String, Object> desc : descriptions) {
                    String lang = (String) desc.get("lang");
                    String type = (String) desc.get("type");
                    String term = (String) desc.get("term");
                    Boolean active = (Boolean) desc.get("active");
                    
                    if (Boolean.TRUE.equals(active) && "SYNONYM".equals(type)) {
                        if ("sv".equals(lang)) {
                            // Swedish synonym
                            String normalized = term.toLowerCase().trim();
                            synonymCache.put(normalized, conceptId);
                            synonyms.add(term);
                        }
                    }
                }
                
                // Create synonym group
                if (!synonyms.isEmpty()) {
                    for (String synonym : synonyms) {
                        synonymGroups.put(synonym.toLowerCase(), synonyms);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading synonyms for concept " + conceptId + ": " + e.getMessage());
        }
    }
    
    @Override
    public Optional<String> lookup(String swedishTerm) {
        String normalized = swedishTerm.toLowerCase().trim();
        
        // Check cache first
        String cached = synonymCache.get(normalized);
        if (cached != null) {
            return Optional.of(cached);
        }
        
        // If not in cache, try to find via Snowstorm search
        // (lazy loading)
        return findViaSnowstormSearch(normalized);
    }
    
    private Optional<String> findViaSnowstormSearch(String term) {
        // Search Snowstorm for the term
        // If found, extract concept ID and cache it
        // Return the concept ID
        // (Implementation similar to FhirSnomedService)
        return Optional.empty();
    }
}
```

### Step 2: Configuration

Add to `application.yml`:
```yaml
synonym:
  cache:
    enabled: true
    preload-common-concepts: true
    common-concepts:
      - "73211009"  # Diabetes
      - "22298006"  # Myocardial infarction
      - "125605004" # Fracture of bone
      # Add more as needed
```

---

## Summary

### Best Options for Swedish Medical Synonyms:

1. **⭐ SNOMED CT via Snowstorm** (Recommended)
   - Extract synonyms from SNOMED CT descriptions
   - Official, comprehensive, supports Swedish
   - Already integrated in your application

2. **Wikidata** (Supplement)
   - Good for common terms
   - Multilingual support
   - Free and public

3. **UMLS** (Limited Swedish)
   - Comprehensive but primarily English
   - Requires registration

### Recommendation:

**Enhance your current Snowstorm integration** to extract and cache Swedish synonyms from SNOMED CT descriptions. This provides:
- ✅ Official SNOMED CT data
- ✅ Native Swedish support
- ✅ No external dependencies
- ✅ Better than hardcoded mappings

The hardcoded solution can serve as a **fallback** for terms not yet loaded from Snowstorm.

---

**Last Updated**: 2024
**Application Version**: 0.0.2-SNAPSHOT

