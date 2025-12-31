# Confidence Value Calculation

This document explains how confidence values are calculated for recommendations in the hybrid recommendation system.

## Overview

**Confidence = Similarity Score**

The confidence value is directly derived from the similarity score calculated using the **Jaro-Winkler similarity algorithm**. The confidence represents how similar the input term is to the recommended SNOMED CT term.

**Range**: 0.0 (no similarity) to 1.0 (exact match)

---

## Strategy-Specific Confidence Calculation

### Strategy 1: Synonym Database Lookup

**File**: `HybridRecommendationServiceImpl.java` → `fetchConceptDetails()`

**Calculation**:
```java
// Line 298-302
double similarity = 1.0;  // Default if no input term
if (inputTerm != null) {
    String inputLower = inputTerm.toLowerCase();
    String ptLower = preferredTerm.toLowerCase();
    similarity = jw.apply(inputLower, ptLower);  // Jaro-Winkler similarity
}
```

**Process**:
1. Lookup SNOMED ID from synonym database
2. Fetch concept details from Snowstorm API
3. Extract Preferred Term (PT) from concept
4. Calculate Jaro-Winkler similarity between:
   - Input term (lowercase): `"sockersjuka"`
   - Preferred Term (lowercase): `"diabetes mellitus"` (from SNOMED)
5. Use similarity as confidence

**Example**:
- Input: `"sockersjuka"`
- SNOMED PT: `"Diabetes mellitus"`
- Jaro-Winkler(`"sockersjuka"`, `"diabetes mellitus"`) ≈ 0.45
- **Confidence**: 0.45

**Note**: Even though the term is in the synonym database (curated mapping), the confidence is still calculated based on string similarity, not set to 1.0 automatically.

---

### Strategy 2: Enhanced Fuzzy Matching

**File**: `HybridRecommendationServiceImpl.java` → `findViaFuzzyMatching()`

**Calculation**:
```java
// Line 177-192
List<TermMatch> matches = snomedService.matchTerms(List.of(term));
TermMatch match = matches.get(0);
// Use the similarity score directly from the match
CandidateRecommendation candidate = new CandidateRecommendation(
    match.matchedSctId(),
    match.preferredTermSv(),
    match.fsnSv(),
    match.similarity(),  // <-- Confidence = similarity from match
    "Fuzzy match",
    term
);
```

**Process**:
1. Call `snomedService.matchTerms()` which searches Snowstorm API
2. `FhirSnomedService` calculates similarity using Jaro-Winkler:
   ```java
   // Line 509-536 in FhirSnomedService.java
   String inputLower = inputTerm.toLowerCase();
   double similarityPt = jw.apply(inputLower, preferredTerm.toLowerCase());
   double similarityFsn = jw.apply(inputLower, fsn.toLowerCase());
   
   // Check all descriptions (including synonyms)
   double maxDescriptionSimilarity = Math.max(similarityPt, similarityFsn);
   for (Map<?, ?> desc : descriptionsToUse) {
       double descSimilarity = jw.apply(inputLower, descTerm.toLowerCase());
       // Boost for exact matches
       if (descLower.equals(inputLower)) {
           descSimilarity = 1.0;
       } else if (descLower.contains(inputLower) || inputLower.contains(descLower)) {
           descSimilarity = Math.max(descSimilarity, 0.8);
       }
       maxDescriptionSimilarity = Math.max(maxDescriptionSimilarity, descSimilarity);
   }
   ```
3. Returns `TermMatch` with similarity score
4. Confidence = similarity score (range: 0.4-0.6 for fuzzy matches, or < 0.4 for low similarity matches)

**Example**:
- Input: `"diabet"` (partial term)
- SNOMED PT: `"Diabetes mellitus"`
- Jaro-Winkler(`"diabet"`, `"diabetes mellitus"`) ≈ 0.55
- **Confidence**: 0.55

---

### Strategy 3: Hierarchical Search

**File**: `HybridRecommendationServiceImpl.java` → `findViaHierarchicalSearch()`

**Note**: This strategy generates **"suggested additional"** terms, not recommendations. These terms don't have confidence values because they're not matched to input terms - they're related concepts from the SNOMED hierarchy.

**No confidence calculation** - these are suggestions based on relationships, not similarity.

---

### Strategy 4: AI Agent Fallback

**File**: `HybridRecommendationServiceImpl.java` → `findViaAiFallback()`

**Calculation**:
```java
// Line 449-467
String content = this.chatClient.prompt(prompt).call().content();
// Parse JSON response from AI
AiRecommendationResponse response = mapper.readValue(content, AiRecommendationResponse.class);
// Confidence comes from AI response JSON
```

**Process**:
1. Build prompt asking AI to recommend SNOMED codes
2. AI returns JSON with confidence values
3. Confidence is **AI-generated** (typically 0.7-0.9)
4. If AI fails, uses mock response with confidence 0.75

**AI Prompt Example**:
```
Return JSON with this structure:
{
  "recommendations": [{
    "confidence": 0.85,  // <-- AI generates this
    ...
  }]
}
```

**Mock Response** (if AI disabled/fails):
```java
// Line 520-525
recommendations.add(new AiRecommendationResponse.Recommendation(
    term,
    "MOCK_" + term.hashCode(),
    "Mock recommended term for " + term,
    "Mock term (disorder)",
    0.75,  // <-- Fixed confidence for mock
    ...
));
```

**Example**:
- Input: `"Chronic fatigue syndrome"`
- AI analyzes and suggests: `"Chronic fatigue syndrome" (161891005)`
- **Confidence**: 0.85 (AI-generated)

---

## Jaro-Winkler Similarity Algorithm

**Library**: Apache Commons Text `JaroWinklerSimilarity`

**What it measures**:
- String similarity based on:
  - Common characters
  - Character order
  - Prefix matching (Winkler modification)

**Formula**:
```
Jaro-Winkler(s1, s2) = Jaro(s1, s2) + (0.1 * p * (1 - Jaro(s1, s2)))
```
Where:
- `Jaro(s1, s2)` = Jaro similarity (0.0 to 1.0)
- `p` = Length of common prefix (max 4 characters)

**Examples**:
- `jw.apply("astma", "astma")` = **1.0** (exact match)
- `jw.apply("astma", "asthma")` ≈ **0.97** (very similar)
- `jw.apply("diabet", "diabetes mellitus")` ≈ **0.55** (partial match)
- `jw.apply("sockersjuka", "diabetes mellitus")` ≈ **0.45** (different terms, some similarity)
- `jw.apply("abc", "xyz")` ≈ **0.0** (no similarity)

---

## Confidence Value Mapping

| Strategy | Confidence Source | Range | Typical Values |
|----------|------------------|-------|----------------|
| **Synonym Database** | Jaro-Winkler(input, PT) | 0.0-1.0 | 0.4-1.0 |
| **Fuzzy Matching** | Jaro-Winkler(input, PT/FSN/descriptions) | 0.0-1.0 | 0.4-0.6 (fuzzy), < 0.4 (low) |
| **Hierarchical Search** | N/A (no confidence) | - | - |
| **AI Fallback** | AI-generated | 0.0-1.0 | 0.7-0.9 |
| **Mock Response** | Fixed value | 0.75 | 0.75 |

---

## Code Flow for Confidence

### Synonym Database Path
```
findViaSynonymDatabase()
  → synonymDatabase.lookup(term) → SNOMED ID
  → fetchConceptDetails(snomedId, term)
    → Extract PT from Snowstorm API
    → jw.apply(inputTerm.toLowerCase(), preferredTerm.toLowerCase())
    → similarity (0.0-1.0) = confidence
```

### Fuzzy Matching Path
```
findViaFuzzyMatching()
  → snomedService.matchTerms([term])
    → FhirSnomedService.searchByTerm(term)
      → Search Snowstorm API
      → extractConceptFromSnowstormResponse()
        → Calculate similarity using Jaro-Winkler
        → Return TermMatch with similarity
  → match.similarity() = confidence
```

### AI Fallback Path
```
findViaAiFallback()
  → Build prompt
  → Call OpenAI API
  → Parse JSON response
  → response.recommendations[].confidence (AI-generated)
```

---

## Final Confidence Assignment

**File**: `HybridRecommendationServiceImpl.java` → `convertToRecommendations()`

```java
// Line 400-405
result.add(new AiRecommendationResponse.Recommendation(
    candidate.inputTerm(),
    candidate.snomedId(),
    candidate.preferredTerm(),
    candidate.fsn(),
    candidate.similarity(),  // <-- Confidence = similarity from candidate
    reasonPrefix + " - similarity: " + String.format("%.2f", candidate.similarity()),
    ...
));
```

**Key Point**: The `similarity` field from `CandidateRecommendation` becomes the `confidence` field in the final response.

---

## Deduplication and Ranking

**File**: `HybridRecommendationServiceImpl.java` → `deduplicateAndRank()`

```java
// Line 417-432
// If same SNOMED ID found by multiple strategies, keep highest confidence
if (!bestBySnomedId.containsKey(key) || 
    rec.confidence() > bestBySnomedId.get(key).confidence()) {
    bestBySnomedId.put(key, rec);
}

// Sort by confidence (highest first)
return bestBySnomedId.values().stream()
    .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
    .collect(Collectors.toList());
```

**Process**:
1. If multiple strategies find the same SNOMED ID, keep the one with **highest confidence**
2. Sort all recommendations by confidence (descending)
3. Return ranked list

---

## Examples

### Example 1: Synonym Database
- **Input**: `"sockersjuka"`
- **SNOMED PT**: `"Diabetes mellitus"`
- **Calculation**: `jw.apply("sockersjuka", "diabetes mellitus")`
- **Result**: ≈ 0.45
- **Confidence**: **0.45**

### Example 2: Fuzzy Matching
- **Input**: `"diabet"`
- **SNOMED PT**: `"Diabetes mellitus"`
- **Calculation**: `jw.apply("diabet", "diabetes mellitus")`
- **Result**: ≈ 0.55
- **Confidence**: **0.55**

### Example 3: Exact Match
- **Input**: `"astma"`
- **SNOMED PT**: `"Astma"` (Swedish)
- **Calculation**: `jw.apply("astma", "astma")`
- **Result**: 1.0
- **Confidence**: **1.0**

### Example 4: AI Fallback
- **Input**: `"Chronic fatigue syndrome"`
- **AI Analysis**: Suggests `"Chronic fatigue syndrome" (161891005)`
- **AI Confidence**: 0.85 (AI-generated)
- **Confidence**: **0.85**

---

## Summary

1. **Confidence = Similarity Score** (calculated using Jaro-Winkler algorithm)
2. **Synonym Database**: Calculates similarity between input term and SNOMED Preferred Term
3. **Fuzzy Matching**: Uses similarity from SNOMED matching service (which checks PT, FSN, and all descriptions)
4. **AI Fallback**: Confidence is AI-generated (typically 0.7-0.9)
5. **Mock Response**: Fixed at 0.75
6. **Final Step**: Deduplicate by keeping highest confidence, then rank by confidence (highest first)

**Key Algorithm**: Jaro-Winkler Similarity (Apache Commons Text library)


