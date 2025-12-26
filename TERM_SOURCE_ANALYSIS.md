# Term Source Analysis

This document shows exactly where the recommendation information comes from for each specific term.

## Terms Analyzed

1. **Astma**
2. **Sockersjuka**
3. **Fotledsfraktur**

---

## Analysis Results

### All Three Terms Use: **Strategy 1 - Synonym Database Lookup**

All three terms are found in the synonym database, so they follow the same path:

**Source**: `SynonymDatabaseServiceImpl.java` (in-memory HashMap)

---

## Detailed Flow for Each Term

### 1. Astma (Asthma)

**Step 1: Synonym Database Lookup**
- **File**: `backend/src/main/java/com/example/codesys/service/impl/SynonymDatabaseServiceImpl.java`
- **Line**: 69
- **Code**: `addMapping("astma", "195967001");`
- **Result**: Finds SNOMED ID `195967001`

**Step 2: Fetch Concept Details from Snowstorm**
- **Method**: `fetchConceptDetails("195967001", "astma")`
- **API Call**: `GET https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts/195967001`
- **Headers**: `Accept-Language: sv` (Swedish)
- **Response**: Full concept details including:
  - Preferred Term (PT) in Swedish
  - Fully Specified Name (FSN) in Swedish
  - All descriptions

**Step 3: Create Recommendation**
- **Source**: Synonym database match
- **Confidence**: 1.0 (exact match)
- **Reason**: "Synonym database match - similarity: 1.00"

**Data Sources**:
1. **SNOMED ID**: Synonym database (in-memory HashMap)
2. **Term details** (PT, FSN, descriptions): Snowstorm API
3. **Language**: Swedish (from `application.yml`: `snomed.language: sv`)

---

### 2. Sockersjuka (Diabetes)

**Step 1: Synonym Database Lookup**
- **File**: `backend/src/main/java/com/example/codesys/service/impl/SynonymDatabaseServiceImpl.java`
- **Line**: 38
- **Code**: `addMapping("sockersjuka", "73211009");`
- **Result**: Finds SNOMED ID `73211009`

**Step 2: Fetch Concept Details from Snowstorm**
- **Method**: `fetchConceptDetails("73211009", "sockersjuka")`
- **API Call**: `GET https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts/73211009`
- **Headers**: `Accept-Language: sv` (Swedish)
- **Response**: Full concept details for "Diabetes mellitus"

**Step 3: Create Recommendation**
- **Source**: Synonym database match
- **Confidence**: 1.0 (exact match)
- **Reason**: "Synonym database match - similarity: 1.00"

**Data Sources**:
1. **SNOMED ID**: Synonym database (in-memory HashMap)
2. **Term details** (PT, FSN, descriptions): Snowstorm API
3. **Language**: Swedish (from `application.yml`: `snomed.language: sv`)

---

### 3. Fotledsfraktur (Ankle Fracture)

**Step 1: Synonym Database Lookup**
- **File**: `backend/src/main/java/com/example/codesys/service/impl/SynonymDatabaseServiceImpl.java`
- **Line**: 73
- **Code**: `addMapping("fotledsfraktur", "125605004");`
- **Result**: Finds SNOMED ID `125605004`

**Step 2: Fetch Concept Details from Snowstorm**
- **Method**: `fetchConceptDetails("125605004", "fotledsfraktur")`
- **API Call**: `GET https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts/125605004`
- **Headers**: `Accept-Language: sv` (Swedish)
- **Response**: Full concept details for "Fracture of ankle"

**Step 3: Create Recommendation**
- **Source**: Synonym database match
- **Confidence**: 1.0 (exact match)
- **Reason**: "Synonym database match - similarity: 1.00"

**Data Sources**:
1. **SNOMED ID**: Synonym database (in-memory HashMap)
2. **Term details** (PT, FSN, descriptions): Snowstorm API
3. **Language**: Swedish (from `application.yml`: `snomed.language: sv`)

---

## Code Flow Diagram

```
User Request: ["astma", "sockersjuka", "fotledsfraktur"]
    ↓
HybridRecommendationServiceImpl.recommend()
    ↓
Strategy 1: findViaSynonymDatabase()
    ↓
For each term:
    ├─ SynonymDatabaseServiceImpl.lookup("astma")
    │   └─ Returns: Optional.of("195967001")
    │
    ├─ SynonymDatabaseServiceImpl.lookup("sockersjuka")
    │   └─ Returns: Optional.of("73211009")
    │
    └─ SynonymDatabaseServiceImpl.lookup("fotledsfraktur")
        └─ Returns: Optional.of("125605004")
    ↓
For each SNOMED ID found:
    ├─ fetchConceptDetails("195967001", "astma")
    │   └─ API: GET /snowstorm/snomed-ct/MAIN/concepts/195967001
    │       └─ Returns: {pt: {...}, fsn: {...}, descriptions: [...]}
    │
    ├─ fetchConceptDetails("73211009", "sockersjuka")
    │   └─ API: GET /snowstorm/snomed-ct/MAIN/concepts/73211009
    │       └─ Returns: {pt: {...}, fsn: {...}, descriptions: [...]}
    │
    └─ fetchConceptDetails("125605004", "fotledsfraktur")
        └─ API: GET /snowstorm/snomed-ct/MAIN/concepts/125605004
            └─ Returns: {pt: {...}, fsn: {...}, descriptions: [...]}
    ↓
Create CandidateRecommendation objects
    ↓
Convert to AiRecommendationResponse.Recommendation
    ↓
Return to frontend
```

---

## Data Source Summary

| Component | Source | Location |
|-----------|--------|----------|
| **SNOMED ID** | In-memory HashMap | `SynonymDatabaseServiceImpl.synonymMap` |
| **Preferred Term (PT)** | Snowstorm API | `/snowstorm/snomed-ct/MAIN/concepts/{id}` |
| **Fully Specified Name (FSN)** | Snowstorm API | `/snowstorm/snomed-ct/MAIN/concepts/{id}` |
| **Descriptions** | Snowstorm API | `/snowstorm/snomed-ct/MAIN/concepts/{id}` |
| **Language** | Configuration | `application.yml: snomed.language: sv` |

---

## Why These Terms Don't Use Other Strategies

### Strategy 2 (Fuzzy Matching) - NOT USED
- **Reason**: Terms are found in synonym database first
- **Logic**: If Strategy 1 succeeds, the term is removed from the unmatched list before Strategy 2 runs

### Strategy 3 (Hierarchical Search) - NOT USED FOR RECOMMENDATIONS
- **Reason**: This strategy only generates "suggested additional" terms, not recommendations for unmatched terms
- **Note**: It would run if there are matched SNOMED IDs, but it doesn't handle these specific unmatched terms

### Strategy 4 (AI Fallback) - NOT USED
- **Reason**: Terms are found in synonym database, so they're filtered out before AI fallback
- **Logic**: Only terms NOT found by Strategy 1 or 2 go to AI fallback

---

## Key Code Locations

### Synonym Database Mappings
**File**: `backend/src/main/java/com/example/codesys/service/impl/SynonymDatabaseServiceImpl.java`

```java
// Line 69
addMapping("astma", "195967001"); // Asthma

// Line 38
addMapping("sockersjuka", "73211009"); // Diabetes mellitus

// Line 73
addMapping("fotledsfraktur", "125605004"); // Fracture of ankle
```

### Lookup Logic
**File**: `backend/src/main/java/com/example/codesys/service/impl/HybridRecommendationServiceImpl.java`

```java
// Line 139
Optional<String> snomedId = synonymDatabase.lookup(term);
if (snomedId.isPresent()) {
    // Line 141
    CandidateRecommendation candidate = fetchConceptDetails(snomedId.get(), term);
    // ...
}
```

### Fetch Concept Details
**File**: `backend/src/main/java/com/example/codesys/service/impl/HybridRecommendationServiceImpl.java`

```java
// Line 274-289
private CandidateRecommendation fetchConceptDetails(String conceptId, String inputTerm) {
    // API call to Snowstorm
    GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}
    // Extract PT, FSN from response
}
```

---

## Summary

**All three terms get their information from:**

1. **SNOMED ID**: Synonym database (in-memory HashMap in `SynonymDatabaseServiceImpl`)
2. **Term details** (PT, FSN, descriptions): Snowstorm API via `fetchConceptDetails()`
3. **Language preference**: Configuration (`application.yml`: `snomed.language: sv`)

**Strategy used**: Strategy 1 - Synonym Database Lookup (fastest, most accurate)

**Confidence**: 1.0 (exact matches from curated database)

