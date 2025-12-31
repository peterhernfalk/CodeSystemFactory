# How Unmatched Terms Get Matched in the Recommendation Step

This document explains why terms that don't match SNOMED CT in the initial matching step can still be matched to SNOMED concepts in the AI recommendation step.

---

## Overview: Two-Stage Matching Process

The system uses a **two-stage matching approach**:

1. **Stage 1: Initial Term Matching** (`FhirSnomedService.matchTerms()`)
   - **Strict threshold**: Requires similarity ≥ 0.6 OR input contained in term
   - **Purpose**: High-confidence matches only
   - **Result**: Terms with similarity < 0.6 are marked as "NO_MATCH"

2. **Stage 2: Recommendation Step** (`HybridRecommendationService.recommend()`)
   - **Lower threshold**: Accepts similarity 0.4-0.6 (configurable)
   - **Multiple strategies**: Uses 4 different approaches
   - **Purpose**: Find matches for unmatched terms with lower confidence
   - **Result**: Recommendations with confidence scores

---

## Why Terms Don't Match in Stage 1

### Threshold Logic

**File**: `FhirSnomedService.java` → `searchByTerm()` (Line 365-368)

```java
// Accept if similarity >= 0.6 OR if input is contained in term
if (bestScore >= 0.6 || ptLower.contains(inputLower) || inputLower.contains(ptLower) 
        || fsnLower.contains(inputLower) || inputLower.contains(fsnLower)) {
    return new TermMatch(..., "MATCHED");
} else {
    return new TermMatch(..., "NO_MATCH");  // <-- Rejected
}
```

**Rejection Criteria:**
- Similarity < 0.6 **AND**
- Input term is NOT contained in SNOMED term **AND**
- SNOMED term is NOT contained in input term

**Example:**
- Input: `"diabet"` (partial term)
- SNOMED: `"Diabetes mellitus"`
- Jaro-Winkler similarity: ~0.55
- **Result**: NO_MATCH (similarity 0.55 < 0.6, but "diabet" is contained in "diabetes mellitus" - wait, this should match!)

Actually, let me check the logic more carefully. The condition is:
- `bestScore >= 0.6` OR
- `ptLower.contains(inputLower)` OR
- `inputLower.contains(ptLower)` OR
- `fsnLower.contains(inputLower)` OR
- `inputLower.contains(fsnLower)`

So if "diabet" is contained in "diabetes mellitus", it should match. But if the similarity is very low (e.g., 0.3) and there's no substring match, it won't match.

**Real Example of Unmatched Term:**
- Input: `"kronisk trötthet"` (Swedish for "chronic fatigue")
- SNOMED: `"Chronic fatigue syndrome"` (English)
- Jaro-Winkler similarity: ~0.45 (low due to language difference)
- Substring check: "kronisk" not in "Chronic fatigue", "Chronic" not in "kronisk trötthet"
- **Result**: NO_MATCH (similarity 0.45 < 0.6, no substring match)

---

## How Terms Get Matched in Stage 2 (Recommendation Step)

The recommendation step uses **4 strategies** in sequence:

### Strategy 1: Synonym Database Lookup

**Data Source**: In-memory curated database (`SynonymDatabaseServiceImpl`)

**File**: `HybridRecommendationServiceImpl.java` → `findViaSynonymDatabase()`

**Logic:**
1. Direct lookup: Check if term exists in synonym map
2. Synonym lookup: If not found, check synonym groups
3. Fetch concept details from Snowstorm API
4. Calculate similarity (for confidence score)

**Code:**
```java
// Direct lookup
Optional<String> snomedId = synonymDatabase.lookup(term);
if (snomedId.isPresent()) {
    CandidateRecommendation candidate = fetchConceptDetails(snomedId.get(), term);
    // Returns recommendation with confidence = similarity
}

// Try synonyms
List<String> synonyms = synonymDatabase.getSynonyms(term);
for (String synonym : synonyms) {
    snomedId = synonymDatabase.lookup(synonym);
    // ...
}
```

**Example:**
- Input: `"sockersjuka"` (Swedish for "diabetes")
- Synonym database: `"sockersjuka" → "73211009"` (Diabetes mellitus)
- **Result**: Recommendation with SNOMED ID `73211009`
- **Confidence**: Jaro-Winkler similarity between "sockersjuka" and "Diabetes mellitus" (~0.45)

**Key Point**: This strategy **bypasses the similarity threshold** - it uses a curated mapping, then calculates similarity only for the confidence score.

---

### Strategy 2: Enhanced Fuzzy Matching

**Data Source**: Snowstorm API (same as Stage 1)

**File**: `HybridRecommendationServiceImpl.java` → `findViaFuzzyMatching()`

**Logic:**
1. Call `snomedService.matchTerms()` (same service as Stage 1)
2. Accept matches with similarity **0.4-0.6** (configurable)
3. Also accept matches with similarity < 0.4 if found (very low confidence)

**Code:**
```java
List<TermMatch> matches = snomedService.matchTerms(List.of(term));
TermMatch match = matches.get(0);

// Accept if similarity is in the fuzzy range (0.4-0.6)
if (match.matchedSctId() != null && 
        match.similarity() >= fuzzyMatchMinSimilarity  // 0.4
        && match.similarity() < fuzzyMatchMaxSimilarity) {  // 0.6
    // Create recommendation
}

// Also accept very low similarity matches
else if (match.matchedSctId() != null && match.similarity() > 0.0 && match.similarity() < fuzzyMatchMinSimilarity) {
    // Include with lower confidence
}
```

**Configuration** (`application.yml`):
```yaml
recommendation:
  fuzzy-match:
    min-similarity: 0.4  # Lower than Stage 1 threshold (0.6)
    max-similarity: 0.6
```

**Example:**
- Input: `"kronisk trötthet"` (Swedish)
- Stage 1: Similarity 0.45 → NO_MATCH (0.45 < 0.6)
- Stage 2 Strategy 2: Similarity 0.45 → **ACCEPTED** (0.4 ≤ 0.45 < 0.6)
- **Result**: Recommendation with confidence 0.45

**Key Difference from Stage 1:**
- **Stage 1 threshold**: 0.6
- **Strategy 2 threshold**: 0.4-0.6 (lower, more permissive)

---

### Strategy 3: Hierarchical Search

**Data Source**: Snowstorm API (parent/child relationships)

**File**: `HybridRecommendationServiceImpl.java` → `findViaHierarchicalSearch()`

**Logic:**
1. For each matched SNOMED ID from Stage 1:
   - Get parent concepts (broader terms)
   - Get child concepts (narrower terms)
2. Fetch concept details for each parent/child
3. Return as "suggested additional" terms (not matched to input)

**Code:**
```java
// Get parent concepts
List<String> parents = getParentConcepts(matchedId);
for (String parentId : parents) {
    CandidateRecommendation candidate = fetchConceptDetails(parentId, null);
    // Add as suggested term
}

// Get child concepts
List<String> children = getChildConcepts(matchedId);
// ...
```

**Example:**
- Stage 1 matched: `"Diabetes mellitus" (73211009)`
- Hierarchical search finds:
  - Parent: `"Disorder of glucose metabolism" (73211009)`
  - Child: `"Type 1 diabetes mellitus" (46635009)`
- **Result**: Suggested additional terms (not matched to input)

**Key Point**: This strategy doesn't match unmatched terms - it suggests related concepts based on matched terms.

---

### Strategy 4: AI Agent Fallback

**Data Source**: OpenAI API (GPT-4o-mini)

**File**: `HybridRecommendationServiceImpl.java` → `findViaAiFallback()`

**Logic:**
1. Build prompt with unmatched terms and context
2. Call OpenAI API
3. Parse JSON response with SNOMED recommendations
4. Return recommendations with AI-generated confidence

**Code:**
```java
String prompt = buildAiPrompt(unmatchedTerms, matchedSnomedIds, context);
String content = this.chatClient.prompt(prompt).call().content();
AiRecommendationResponse response = mapper.readValue(content, AiRecommendationResponse.class);
```

**Prompt Structure:**
```
You are a clinical terminology assistant helping build a code system.
Given unmatched terms and existing matched SNOMED CT codes, recommend:
1. SNOMED codes for unmatched terms
2. Additional complementary SNOMED codes that enhance the code system

Return JSON with this structure:
{
  "recommendations": [{
    "inputTerm": "<unmatched term>",
    "recommendedSnomedId": "<SNOMED ID>",
    "recommendedTerm": "<term name>",
    "confidence": 0.85,  // AI-generated
    ...
  }]
}
```

**Example:**
- Input: `"kronisk trötthet"` (Swedish for "chronic fatigue")
- AI analyzes and suggests: `"Chronic fatigue syndrome" (161891005)`
- **Result**: Recommendation with confidence 0.85 (AI-generated)

**Key Point**: AI uses its knowledge of medical terminology to map terms, regardless of similarity scores.

---

## Complete Flow Example

### Example: Term "kronisk trötthet" (Chronic Fatigue)

**Stage 1: Initial Matching**
```
Input: "kronisk trötthet"
→ FhirSnomedService.searchByTerm()
→ Searches Snowstorm API
→ Finds: "Chronic fatigue syndrome" (161891005)
→ Similarity: 0.45 (low due to language difference)
→ Threshold check: 0.45 < 0.6 → NO_MATCH
→ Result: Unmatched term
```

**Stage 2: Recommendation Step**

**Strategy 1: Synonym Database**
```
Input: "kronisk trötthet"
→ synonymDatabase.lookup("kronisk trötthet")
→ Not found in database
→ Result: No match
```

**Strategy 2: Fuzzy Matching**
```
Input: "kronisk trötthet"
→ snomedService.matchTerms(["kronisk trötthet"])
→ Same search as Stage 1
→ Finds: "Chronic fatigue syndrome" (161891005)
→ Similarity: 0.45
→ Threshold check: 0.4 ≤ 0.45 < 0.6 → ACCEPTED
→ Result: Recommendation with confidence 0.45
```

**Strategy 3: Hierarchical Search**
```
(Only runs if there are matched terms from Stage 1)
→ Not applicable if no matched terms
```

**Strategy 4: AI Fallback**
```
Input: "kronisk trötthet"
→ AI analyzes term
→ Suggests: "Chronic fatigue syndrome" (161891005)
→ Confidence: 0.85 (AI-generated)
→ Result: Recommendation with confidence 0.85
```

**Final Result:**
- Two recommendations for "kronisk trötthet":
  1. From Strategy 2: Confidence 0.45
  2. From Strategy 4: Confidence 0.85
- System keeps highest confidence: **0.85** (from AI)

---

## Data Sources Summary

| Strategy | Data Source | API/Service | Threshold |
|---------|-------------|-------------|-----------|
| **Stage 1** | Snowstorm API | `FhirSnomedService` | ≥ 0.6 |
| **Strategy 1** | In-memory database | `SynonymDatabaseService` | None (curated) |
| **Strategy 2** | Snowstorm API | `FhirSnomedService` (same as Stage 1) | 0.4-0.6 |
| **Strategy 3** | Snowstorm API | Parent/child endpoints | N/A (suggestions) |
| **Strategy 4** | OpenAI API | `ChatClient` | AI-generated |

---

## Key Differences: Stage 1 vs Stage 2

### Threshold Comparison

| Stage | Threshold | Purpose |
|-------|-----------|---------|
| **Stage 1** | ≥ 0.6 | High-confidence matches only |
| **Stage 2 Strategy 2** | 0.4-0.6 | Lower-confidence matches (recommendations) |

### Why Different Thresholds?

1. **Stage 1 (Strict)**: 
   - User expects high-quality matches
   - False positives are costly
   - Threshold: 0.6 ensures good matches

2. **Stage 2 (Permissive)**:
   - User is looking for recommendations
   - Lower confidence is acceptable
   - User can review and accept/reject
   - Threshold: 0.4-0.6 allows more candidates

### Additional Strategies in Stage 2

Stage 2 has **3 additional strategies** that Stage 1 doesn't use:

1. **Synonym Database**: Curated mappings (bypasses similarity)
2. **Hierarchical Search**: Related concepts (not similarity-based)
3. **AI Fallback**: Knowledge-based matching (not similarity-based)

---

## Configuration

**File**: `application.yml`

```yaml
recommendation:
  use-hybrid: true
  fuzzy-match:
    min-similarity: 0.4  # Lower threshold for recommendations
    max-similarity: 0.6
  hierarchical:
    max-results: 10
  ai-fallback: true  # Enable AI fallback
```

**Adjusting Thresholds:**
- **Lower `min-similarity`**: More recommendations (lower quality)
- **Higher `min-similarity`**: Fewer recommendations (higher quality)
- **Disable `ai-fallback`**: No AI recommendations (only strategies 1-3)

---

## Summary

**Why unmatched terms can match in recommendations:**

1. **Lower threshold**: Strategy 2 accepts similarity 0.4-0.6 (vs 0.6 in Stage 1)
2. **Synonym database**: Strategy 1 uses curated mappings (bypasses similarity)
3. **AI fallback**: Strategy 4 uses knowledge-based matching (not similarity-based)
4. **Same data source**: Strategy 2 uses the same Snowstorm API, just with lower threshold

**Key Insight**: The recommendation step is **more permissive** than the initial matching step, allowing lower-confidence matches to be presented as recommendations for user review.

