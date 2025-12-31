# AI Recommendation Logic Flow: "Fotledsfraktur" Example

This document explains step-by-step how the AI recommendation system finds SNOMED CT concepts for unmatched terms, using "Fotledsfraktur" (ankle fracture) as an example.

## Overview

When a term doesn't match directly with SNOMED CT (similarity < 0.75), the system uses a **Hybrid Recommendation System** with multiple strategies to find recommendations.

## Step-by-Step Flow for "Fotledsfraktur"

### Step 1: Initial Term Matching (Fails)

**Input**: "Fotledsfraktur"

**Process**:
1. The term is sent to `/api/terms/match` endpoint
2. `FhirSnomedService.matchTerms()` searches Snowstorm for "Fotledsfraktur"
3. No direct match found (similarity < 0.75 threshold)
4. **Result**: Term is marked as **UNMATCHED**

**Why it fails**:
- "Fotledsfraktur" is Swedish for "ankle fracture"
- Snowstorm may not have this exact Swedish term, or similarity is too low
- The matching threshold is 0.75 (75%), so lower similarities are rejected

---

### Step 2: AI Recommendation Request

**Input**: 
- Unmatched term: "Fotledsfraktur"
- Matched SNOMED IDs: [] (empty, since no terms matched)

**Process**:
1. Frontend calls `/api/ai/recommend` endpoint
2. `HybridRecommendationServiceImpl.recommend()` is invoked
3. System tries **4 strategies in order**:

---

### Strategy 1: Synonym Database Lookup ✅ (SUCCESS)

**Location**: `HybridRecommendationServiceImpl.findViaSynonymDatabase()`

**Step 2.1: Direct Lookup**
```java
Optional<String> snomedId = synonymDatabase.lookup("Fotledsfraktur");
```

**What happens**:
1. Term is normalized: `"fotledsfraktur".toLowerCase().trim()` → `"fotledsfraktur"`
2. Synonym database is checked: `synonymMap.get("fotledsfraktur")`
3. **Found**: `"125605004"` (Fracture of bone)

**Synonym Database Entry** (from `SynonymDatabaseServiceImpl.java:73`):
```java
addMapping("fotledsfraktur", "125605004"); // Fracture of ankle
```

**Step 2.2: Fetch Concept Details**
```java
CandidateRecommendation candidate = fetchConceptDetails("125605004", "Fotledsfraktur");
```

**What happens**:
1. Makes API call to Snowstorm:
   ```
   GET https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts/125605004
   Accept-Language: sv
   ```

2. Snowstorm returns concept details:
   ```json
   {
     "conceptId": "125605004",
     "pt": {
       "term": "Fracture of bone",
       "lang": "en"
     },
     "fsn": {
       "term": "Fracture of bone (disorder)",
       "lang": "en"
     },
     "descriptions": [...]
   }
   ```

3. Extracts:
   - Preferred Term: "Fracture of bone"
   - FSN: "Fracture of bone (disorder)"

**Step 2.3: Calculate Similarity**
```java
String inputLower = "fotledsfraktur".toLowerCase();  // "fotledsfraktur"
String ptLower = "Fracture of bone".toLowerCase();   // "fracture of bone"
double similarity = jw.apply(inputLower, ptLower);   // Jaro-Winkler similarity
```

**Jaro-Winkler Similarity Calculation**:
- Compares "fotledsfraktur" vs "fracture of bone"
- Result: **0.54** (54%)
- This is the confidence score shown in the UI

**Step 2.4: Create Recommendation**
```java
new CandidateRecommendation(
    "125605004",                    // snomedId
    "Fracture of bone",            // preferredTerm
    "Fracture of bone (disorder)", // fsn
    0.54,                          // similarity
    "Synonym database match",      // source
    "Fotledsfraktur"               // inputTerm
)
```

**Step 2.5: Convert to API Response**
```java
new AiRecommendationResponse.Recommendation(
    "Fotledsfraktur",                          // inputTerm
    "125605004",                               // recommendedSnomedId
    "Fracture of bone",                        // recommendedTerm
    "Fracture of bone (disorder)",             // fsn
    0.54,                                      // confidence
    "Synonym database match - similarity: 0.54", // reason
    "SNOMED CT concept",                       // definition
    List.of("is-a: Concept")                   // relations
)
```

**Result**: ✅ Recommendation found via Synonym Database

---

### Strategy 2: Fuzzy Matching (Skipped)

**Why skipped**: 
- Strategy 1 already found a match
- The term is removed from `stillUnmatched` list
- Fuzzy matching only runs for terms not found in Strategy 1

**What it would do** (if Strategy 1 failed):
- Search Snowstorm with lower threshold (0.4-0.6 similarity range)
- Find concepts with partial matches
- Return candidates in the fuzzy range

---

### Strategy 3: Hierarchical Search (Skipped)

**Why skipped**:
- No matched SNOMED IDs available (`matchedSnomedIds` is empty)
- Hierarchical search requires existing matched concepts to find parents/children

**What it would do** (if there were matched terms):
- Find parent concepts (broader terms)
- Find child concepts (narrower terms)
- Suggest related concepts from SNOMED hierarchy

---

### Strategy 4: AI Fallback (Skipped)

**Why skipped**:
- Strategy 1 found a match
- Term is not in `stillUnmatched` list
- AI is only called for terms that failed all other strategies

**What it would do** (if all other strategies failed):
- Call OpenAI/LLM with prompt
- Ask AI to recommend SNOMED concepts
- Parse JSON response with recommendations

---

## Final Result

**Recommendation Returned**:
```json
{
  "inputTerm": "Fotledsfraktur",
  "recommendedSnomedId": "125605004",
  "recommendedTerm": "Fracture of bone",
  "fsn": "Fracture of bone (disorder)",
  "confidence": 0.54,
  "reason": "Synonym database match - similarity: 0.54",
  "definition": "SNOMED CT concept",
  "relations": ["is-a: Concept"]
}
```

---

## Key Components

### 1. Synonym Database (`SynonymDatabaseServiceImpl`)

**Purpose**: Curated mappings of Swedish medical terms to SNOMED CT concept IDs

**How it works**:
- In-memory HashMap: `Map<String, String>` (term → SNOMED ID)
- Normalized keys: All terms converted to lowercase
- Hardcoded mappings: Currently defined in `initializeSynonyms()`

**Example Entry**:
```java
addMapping("fotledsfraktur", "125605004");
```

**Future Enhancement**: Can be loaded from:
- Database
- CSV file
- External API
- Configuration file

### 2. Concept Detail Fetching (`fetchConceptDetails()`)

**Purpose**: Retrieve full concept information from Snowstorm

**Process**:
1. Build Snowstorm API URL: `/snowstorm/snomed-ct/MAIN/concepts/{conceptId}`
2. Set Accept-Language header (Swedish: `sv`)
3. Make HTTP GET request
4. Parse response to extract:
   - Preferred Term (PT)
   - Fully Specified Name (FSN)
   - Descriptions
5. Calculate similarity between input term and concept terms

### 3. Similarity Calculation (Jaro-Winkler)

**Purpose**: Measure how similar the input term is to the concept's preferred term

**Algorithm**: Jaro-Winkler Similarity
- Range: 0.0 (no similarity) to 1.0 (identical)
- Considers character order and common prefixes
- Good for handling typos and variations

**Example**:
- "fotledsfraktur" vs "fracture of bone" = **0.54**
- This becomes the confidence score

### 4. Recommendation Conversion

**Purpose**: Format the internal candidate into API response format

**Process**:
1. Take `CandidateRecommendation` (internal format)
2. Convert to `AiRecommendationResponse.Recommendation` (API format)
3. Add reason string with similarity score
4. Add default definition and relations

---

## Why 54% Confidence?

The confidence of **54%** comes from:

1. **Synonym Database Match**: The term "Fotledsfraktur" is mapped to SNOMED ID "125605004"
2. **Concept Retrieved**: Snowstorm returns "Fracture of bone" as the preferred term
3. **Similarity Calculated**: Jaro-Winkler compares:
   - Input: "fotledsfraktur" (Swedish)
   - Concept PT: "Fracture of bone" (English)
   - Result: 0.54 (54% similarity)

**Why it's not higher**:
- The input is Swedish ("fotledsfraktur")
- The concept term is English ("Fracture of bone")
- Different languages = lower similarity score
- The synonym database provides the connection, but similarity is calculated against English terms

**Note**: The SNOMED ID "125605004" might not be the exact match for "ankle fracture". The comment in the code says:
```java
// Fracture of ankle (approximate - may need correct SNOMED ID)
```

This suggests the mapping might need correction to a more specific ankle fracture concept.

---

## Flow Diagram

```
"Fotledsfraktur" (Input)
    │
    ├─→ Initial Match (FhirSnomedService)
    │   └─→ ❌ No match (similarity < 0.75)
    │
    └─→ AI Recommendation (HybridRecommendationService)
        │
        ├─→ Strategy 1: Synonym Database ✅
        │   ├─→ lookup("fotledsfraktur")
        │   ├─→ Found: "125605004"
        │   ├─→ fetchConceptDetails("125605004")
        │   ├─→ Get: "Fracture of bone"
        │   ├─→ Calculate similarity: 0.54
        │   └─→ ✅ Recommendation created
        │
        ├─→ Strategy 2: Fuzzy Matching (skipped - already found)
        ├─→ Strategy 3: Hierarchical Search (skipped - no matched terms)
        └─→ Strategy 4: AI Fallback (skipped - already found)
        
Result: Recommendation with 54% confidence
```

---

## Configuration

The recommendation system behavior is controlled by:

**`application.yml`**:
```yaml
recommendation:
  use-hybrid: true                    # Enable hybrid approach
  fuzzy-match:
    min-similarity: 0.4              # Minimum similarity for fuzzy matches
    max-similarity: 0.6              # Maximum similarity for fuzzy matches
  hierarchical:
    max-results: 10                  # Max hierarchical suggestions
  ai-fallback: true                  # Use AI if other strategies fail
```

---

## Improving the Recommendation

### Option 1: Fix SNOMED ID Mapping

The current mapping uses "125605004" (Fracture of bone - general), but "Fotledsfraktur" specifically means "ankle fracture". 

**Better mapping**:
```java
// Find the correct SNOMED ID for ankle fracture
// Example: "125605004" might need to be replaced with a more specific concept
addMapping("fotledsfraktur", "<correct-ankle-fracture-snomed-id>");
```

### Option 2: Add Swedish Preferred Terms

If Snowstorm has Swedish descriptions for the concept, the similarity would be higher:
- Swedish input: "fotledsfraktur"
- Swedish PT: "Fotledsfraktur" (if available)
- Similarity: ~1.0 (100%)

### Option 3: Enhance Synonym Database

Add more variations:
```java
addMapping("fotledsfraktur", "<snomed-id>");
addMapping("ankelfraktur", "<snomed-id>");
addMapping("fraktur i fotleden", "<snomed-id>");
addSynonymGroup("fotledsfraktur", "ankelfraktur", "ankle fracture", "fracture of ankle");
```

---

## Summary

**How "Fotledsfraktur" finds SNOMED concept "125605004"**:

1. ✅ **Synonym Database** has hardcoded mapping: `"fotledsfraktur" → "125605004"`
2. ✅ **Concept Details** fetched from Snowstorm: "Fracture of bone"
3. ✅ **Similarity Calculated**: 0.54 (54%) between Swedish input and English concept term
4. ✅ **Recommendation Created** with reason: "Synonym database match - similarity: 0.54"

The system successfully finds the concept through the **Synonym Database strategy**, which is the first and fastest strategy in the hybrid recommendation system.

---

**Last Updated**: 2024
**Application Version**: 0.0.2-SNAPSHOT

