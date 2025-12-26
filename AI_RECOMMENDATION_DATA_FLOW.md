# AI Recommendation Data Flow

This document explains the complete flow of AI recommendation data from the frontend to the backend and back, including all the logic involved.

## Overview

The AI recommendation system uses a **hybrid approach** that combines multiple strategies:
1. Synonym Database Lookup
2. Enhanced Fuzzy Matching
3. Hierarchical Search
4. AI Agent (fallback)

## Complete Data Flow

### 1. Frontend: User Action

**File**: `frontend/src/ui/App.tsx`

When the user clicks "Get AI Recommendations" button:

```typescript
const callAiRecommend = async () => {
  // Extract unmatched terms and matched SNOMED IDs
  const unmatchedTermList = unmatchedTerms.map(u => u.inputTerm)
  const matchedSnomedIds = matchedTerms.map(m => m.snomedId)
  
  // POST request to backend
  const res = await fetch('/api/ai/recommend', {
    method: 'POST', 
    headers: {'Content-Type':'application/json'},
    body: JSON.stringify({ 
      unmatchedTerms: unmatchedTermList,
      matchedSnomedIds: matchedSnomedIds,
      context: 'Swedish healthcare terminology'
    })
  })
  
  // Parse response
  const data = await res.json()
  setRecommendations(data.recommendations || [])
  setSuggestedAdditional(data.suggestedAdditional || [])
}
```

**Request Payload**:
```json
{
  "unmatchedTerms": ["sockersjuka", "fotledsfraktur"],
  "matchedSnomedIds": ["73211009", "22298006"],
  "context": "Swedish healthcare terminology"
}
```

---

### 2. Backend: Controller Layer

**File**: `backend/src/main/java/com/example/codesys/controller/AiRecommendationController.java`

```java
@RestController
@RequestMapping("/api/ai")
public class AiRecommendationController {
    private final AiRecommendationService recommendationService;

    @PostMapping("/recommend")
    public AiRecommendationResponse recommend(@Valid @RequestBody AiRecommendationRequest request) {
        return recommendationService.recommend(request);
    }
}
```

**What happens**:
- Receives HTTP POST request at `/api/ai/recommend`
- Validates request body using `@Valid` annotation
- Delegates to `AiRecommendationService`

---

### 3. Backend: Service Layer (Entry Point)

**File**: `backend/src/main/java/com/example/codesys/service/impl/AiRecommendationServiceImpl.java`

```java
@Service
public class AiRecommendationServiceImpl implements AiRecommendationService {
    private final HybridRecommendationService hybridService;
    private final boolean useHybrid;

    @Override
    public AiRecommendationResponse recommend(AiRecommendationRequest request) {
        // Check configuration: use hybrid approach?
        if (useHybrid) {
            // Delegate to hybrid service (default path)
            return hybridService.recommend(request);
        }
        
        // Fallback: Pure AI approach (if hybrid disabled)
        // ... (original AI implementation)
    }
}
```

**Configuration Check**:
- Reads `recommendation.use-hybrid` from `application.yml` (default: `true`)
- If `true`: delegates to `HybridRecommendationService`
- If `false`: uses pure AI approach (legacy)

---

### 4. Backend: Hybrid Recommendation Service (Main Logic)

**File**: `backend/src/main/java/com/example/codesys/service/impl/HybridRecommendationServiceImpl.java`

This is where the **main logic** happens. The service combines multiple strategies:

#### 4.1 Strategy Execution Order

```java
@Override
public AiRecommendationResponse recommend(AiRecommendationRequest request) {
    List<AiRecommendationResponse.Recommendation> allRecommendations = new ArrayList<>();
    List<AiRecommendationResponse.SuggestedTerm> allSuggested = new ArrayList<>();
    
    // Strategy 1: Synonym Database Lookup
    Map<String, CandidateRecommendation> synonymCandidates = 
        findViaSynonymDatabase(request.unmatchedTerms());
    allRecommendations.addAll(convertToRecommendations(synonymCandidates, "Synonym database match"));
    
    // Strategy 2: Enhanced Fuzzy Matching
    Map<String, CandidateRecommendation> fuzzyCandidates = 
        findViaFuzzyMatching(request.unmatchedTerms());
    allRecommendations.addAll(convertToRecommendations(fuzzyCandidates, "Fuzzy match (similarity 0.4-0.6)"));
    
    // Strategy 3: Hierarchical Search
    if (!request.matchedSnomedIds().isEmpty()) {
        List<AiRecommendationResponse.SuggestedTerm> hierarchicalSuggestions = 
            findViaHierarchicalSearch(request.matchedSnomedIds(), request.unmatchedTerms());
        allSuggested.addAll(hierarchicalSuggestions);
    }
    
    // Strategy 4: AI Agent (fallback)
    List<String> stillUnmatched = request.unmatchedTerms().stream()
        .filter(term -> !synonymCandidates.containsKey(term.toLowerCase()) 
                     && !fuzzyCandidates.containsKey(term.toLowerCase()))
        .collect(Collectors.toList());
    
    if (!stillUnmatched.isEmpty() && useAiFallback && aiEnabled) {
        AiRecommendationResponse aiResponse = 
            findViaAiFallback(stillUnmatched, request.matchedSnomedIds(), request.context());
        allRecommendations.addAll(aiResponse.recommendations());
        allSuggested.addAll(aiResponse.suggestedAdditional());
    } else if (!stillUnmatched.isEmpty()) {
        // Mock recommendations if AI disabled
        AiRecommendationResponse mockResponse = 
            createMockAiResponse(stillUnmatched, request.matchedSnomedIds());
        allRecommendations.addAll(mockResponse.recommendations());
    }
    
    // Deduplicate and rank
    return new AiRecommendationResponse(
        deduplicateAndRank(allRecommendations),
        deduplicateSuggested(allSuggested)
    );
}
```

---

### 5. Strategy Details

#### Strategy 1: Synonym Database Lookup

**File**: `backend/src/main/java/com/example/codesys/service/impl/SynonymDatabaseServiceImpl.java`

**Logic**:
```java
private Map<String, CandidateRecommendation> findViaSynonymDatabase(List<String> unmatchedTerms) {
    for (String term : unmatchedTerms) {
        // Direct lookup in in-memory map
        Optional<String> snomedId = synonymDatabase.lookup(term);
        if (snomedId.isPresent()) {
            // Fetch full concept details from Snowstorm API
            CandidateRecommendation candidate = fetchConceptDetails(snomedId.get(), term);
            // Add to candidates
        }
        
        // Try synonyms if direct lookup fails
        List<String> synonyms = synonymDatabase.getSynonyms(term);
        // ... try each synonym
    }
}
```

**Data Source**:
- In-memory `HashMap` with Swedish→SNOMED mappings
- Pre-populated with common terms (e.g., "sockersjuka" → "73211009")
- Can be extended by adding more mappings in `initializeSynonyms()`

**Example**:
- Input: "sockersjuka"
- Database lookup: Finds "73211009" (Diabetes mellitus)
- Fetches concept details from Snowstorm API
- Returns recommendation with confidence 1.0

---

#### Strategy 2: Enhanced Fuzzy Matching

**Logic**:
```java
private Map<String, CandidateRecommendation> findViaFuzzyMatching(List<String> unmatchedTerms) {
    for (String term : unmatchedTerms) {
        // Use existing SNOMED matching service
        List<TermMatch> matches = snomedService.matchTerms(List.of(term));
        if (!matches.isEmpty()) {
            TermMatch match = matches.get(0);
            // Accept if similarity is in fuzzy range (0.4-0.6)
            if (match.similarity() >= 0.4 && match.similarity() < 0.6) {
                // Create candidate recommendation
            }
        }
    }
}
```

**Data Source**:
- Calls `SnomedService.matchTerms()` which queries Snowstorm API
- Uses Jaro-Winkler similarity algorithm
- Only accepts matches with similarity 0.4-0.6 (configurable)

**Configuration**:
```yaml
recommendation:
  fuzzy-match:
    min-similarity: 0.4
    max-similarity: 0.6
```

---

#### Strategy 3: Hierarchical Search

**Logic**:
```java
private List<AiRecommendationResponse.SuggestedTerm> findViaHierarchicalSearch(
        List<String> matchedSnomedIds, List<String> unmatchedTerms) {
    
    for (String matchedId : matchedSnomedIds) {
        // Get parent concepts (broader terms)
        List<String> parents = getParentConcepts(matchedId);
        // Get child concepts (narrower terms)
        List<String> children = getChildConcepts(matchedId);
        
        // Fetch details for each related concept
        for (String conceptId : parents + children) {
            CandidateRecommendation candidate = fetchConceptDetails(conceptId, null);
            // Add as suggested term
        }
    }
}
```

**Data Source**:
- Snowstorm API endpoints:
  - `/snowstorm/snomed-ct/{branch}/concepts/{conceptId}/parents`
  - `/snowstorm/snomed-ct/{branch}/concepts/{conceptId}/children`
- Explores SNOMED CT hierarchy to find related concepts

**Example**:
- Matched: "Diabetes mellitus" (73211009)
- Finds parent: "Disorder of glucose metabolism"
- Finds child: "Type 1 diabetes mellitus"
- Returns as "suggested additional" terms

---

#### Strategy 4: AI Agent Fallback

**Logic**:
```java
private AiRecommendationResponse findViaAiFallback(
        List<String> unmatchedTerms, List<String> matchedSnomedIds, String context) {
    
    // Build prompt for AI
    String prompt = buildAiPrompt(unmatchedTerms, matchedSnomedIds, context);
    
    // Call OpenAI API via Spring AI
    String content = this.chatClient.prompt(prompt).call().content();
    
    // Parse JSON response
    return mapper.readValue(content, AiRecommendationResponse.class);
}
```

**Data Source**:
- OpenAI API (via Spring AI `ChatClient`)
- Model: `gpt-4o-mini` (configurable)

**Prompt Structure**:
```
You are a clinical terminology assistant helping build a code system.
Given unmatched terms and existing matched SNOMED CT codes, recommend:
1. SNOMED codes for unmatched terms
2. Additional complementary SNOMED codes that enhance the code system

Return JSON with this structure:
{
  "recommendations": [...],
  "suggestedAdditional": [...]
}

Context: Swedish healthcare terminology
Unmatched terms: ["term1", "term2"]
Matched SNOMED IDs: ["73211009", "22298006"]
```

**Configuration**:
```yaml
ai:
  enabled: true  # Must be true for AI to work
  openai:
    api-key: ${OPENAI_API_KEY}  # Required
    chat:
      options:
        model: gpt-4o-mini

recommendation:
  ai-fallback: true  # Enable AI as fallback
```

---

### 6. Data Transformation

#### Fetching Concept Details

**Method**: `fetchConceptDetails(String conceptId, String inputTerm)`

**Logic**:
```java
// Call Snowstorm API to get full concept details
GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}

// Extract:
- Preferred Term (PT)
- Fully Specified Name (FSN)
- Calculate similarity if inputTerm provided
```

**Data Source**: Snowstorm API

---

#### Deduplication and Ranking

**Method**: `deduplicateAndRank()`

**Logic**:
```java
// Group by SNOMED ID, keep highest confidence
Map<String, Recommendation> bestBySnomedId = new HashMap<>();
for (Recommendation rec : recommendations) {
    if (!bestBySnomedId.containsKey(rec.snomedId) || 
        rec.confidence() > bestBySnomedId.get(rec.snomedId).confidence()) {
        bestBySnomedId.put(rec.snomedId, rec);
    }
}

// Sort by confidence (highest first)
return bestBySnomedId.values().stream()
    .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
    .collect(Collectors.toList());
```

---

### 7. Response Format

**Response Structure**:
```json
{
  "recommendations": [
    {
      "inputTerm": "sockersjuka",
      "recommendedSnomedId": "73211009",
      "recommendedTerm": "Diabetes mellitus",
      "fsn": "Diabetes mellitus (disorder)",
      "confidence": 1.0,
      "reason": "Synonym database match - similarity: 1.00",
      "definition": "SNOMED CT concept",
      "relations": ["is-a: Concept"]
    }
  ],
  "suggestedAdditional": [
    {
      "snomedId": "46635009",
      "term": "Type 1 diabetes mellitus",
      "fsn": "Type 1 diabetes mellitus (disorder)",
      "reason": "Child concept of matched term 73211009",
      "definition": "Related concept from SNOMED hierarchy",
      "relations": ["is-a: 73211009"]
    }
  ]
}
```

---

### 8. Frontend: Display

**File**: `frontend/src/ui/AiRecommendations.tsx`

**Display Logic**:
```typescript
// Recommendations section
{recommendations.map((r, i) => (
  <div>
    <strong>Input:</strong> {r.inputTerm}
    <strong>Recommended:</strong> {r.recommendedTerm} ({r.recommendedSnomedId})
    <strong>FSN:</strong> {r.fsn}
    <strong>Confidence:</strong> {(r.confidence * 100).toFixed(0)}%
    <strong>Reason:</strong> {r.reason}
    <strong>Definition:</strong> {r.definition}
    <strong>Relations:</strong> {r.relations.join(', ')}
  </div>
))}

// Suggested additional section
{suggestedAdditional.map((s, i) => (
  <div onClick={() => toggleSuggested(i)}>
    <strong>Term:</strong> {s.term} ({s.snomedId})
    <strong>FSN:</strong> {s.fsn}
    <strong>Reason:</strong> {s.reason}
  </div>
))}
```

---

## Configuration Summary

All configuration is in `backend/src/main/resources/application.yml`:

```yaml
# AI Service
ai:
  enabled: false  # Set to true to enable AI fallback
  openai:
    api-key: ${OPENAI_API_KEY}
    chat:
      options:
        model: gpt-4o-mini

# Recommendation System
recommendation:
  use-hybrid: true                    # Use hybrid approach
  fuzzy-match:
    min-similarity: 0.4              # Minimum similarity for fuzzy matches
    max-similarity: 0.6              # Maximum similarity (above = direct match)
  hierarchical:
    max-results: 10                  # Max parent/child concepts to suggest
  ai-fallback: true                  # Use AI for unmatched terms

# SNOMED Configuration
snomed:
  branch: MAIN
  language: sv                       # Preferred language
  fallback-to-english: true
```

---

## Data Sources Summary

| Strategy | Data Source | Speed | Accuracy |
|----------|------------|-------|----------|
| Synonym Database | In-memory HashMap | Very Fast | High (exact matches) |
| Fuzzy Matching | Snowstorm API | Fast | Medium (similarity-based) |
| Hierarchical Search | Snowstorm API | Medium | High (semantic relationships) |
| AI Agent | OpenAI API | Slow | Variable (depends on prompt) |

---

## Key Files

### Frontend
- `frontend/src/ui/App.tsx` - Main app, calls API
- `frontend/src/ui/AiRecommendations.tsx` - Displays recommendations

### Backend
- `backend/src/main/java/com/example/codesys/controller/AiRecommendationController.java` - REST endpoint
- `backend/src/main/java/com/example/codesys/service/AiRecommendationService.java` - Service interface
- `backend/src/main/java/com/example/codesys/service/impl/AiRecommendationServiceImpl.java` - Service entry point
- `backend/src/main/java/com/example/codesys/service/impl/HybridRecommendationServiceImpl.java` - **Main logic**
- `backend/src/main/java/com/example/codesys/service/impl/SynonymDatabaseServiceImpl.java` - Synonym database
- `backend/src/main/java/com/example/codesys/model/AiRecommendationRequest.java` - Request model
- `backend/src/main/java/com/example/codesys/model/AiRecommendationResponse.java` - Response model

---

## Debugging

To see what's happening, check backend logs for:
- `DEBUG: Hybrid recommendation - stillUnmatched: [...]`
- `DEBUG: Fuzzy matching for 'term': similarity=X`
- `DEBUG: Calling AI fallback for terms: [...]`

These debug messages show which strategies are being used and why.

