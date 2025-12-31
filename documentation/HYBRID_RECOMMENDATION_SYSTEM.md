# Hybrid Recommendation System

## Overview

The hybrid recommendation system combines multiple strategies to recommend SNOMED CT codes for unmatched terms. This approach provides fast, accurate recommendations by leveraging different techniques based on the context and characteristics of the input terms.

## Architecture

The system uses a **multi-strategy approach** that tries different methods in order of speed and accuracy:

1. **Synonym Database Lookup** (fastest, most accurate for known terms)
2. **Enhanced Fuzzy Matching** (fast, good for similar terms)
3. **Hierarchical Search** (context-aware, finds related concepts)
4. **AI Agent** (fallback for complex cases)

## How It Works

### Strategy 1: Synonym Database Lookup

**Purpose**: Fast lookup for common Swedish medical terms with known SNOMED CT mappings.

**Process**:
1. Direct lookup: Check if the unmatched term exists in the synonym database
2. Synonym expansion: If not found, try known synonyms of the term
3. If found, fetch full concept details from Snowstorm API
4. Return recommendation with high confidence (1.0)

**Example**:
- Input: "sockersjuka" (Swedish for diabetes)
- Database lookup: Finds mapping to SNOMED ID "73211009"
- Result: Recommendation with "Diabetes mellitus" as preferred term

**Configuration**: Managed in `SynonymDatabaseServiceImpl.java` - can be extended with more mappings.

### Strategy 2: Enhanced Fuzzy Matching

**Purpose**: Find candidates that are similar but below the strict matching threshold (0.6).

**Process**:
1. Use existing SNOMED matching service to search for the term
2. Accept candidates with similarity between 0.4-0.6 (configurable)
3. These are terms that are similar but not exact matches
4. Return as recommendations with their similarity score as confidence

**Example**:
- Input: "diabet" (partial/incomplete term)
- Fuzzy match finds: "Diabetes mellitus" with similarity 0.55
- Result: Recommendation with confidence 0.55

**Configuration**:
```yaml
recommendation:
  fuzzy-match:
    min-similarity: 0.4  # Minimum similarity to consider
    max-similarity: 0.6  # Maximum similarity (above this is a direct match)
```

### Strategy 3: Hierarchical Search

**Purpose**: Find related concepts by exploring the SNOMED CT hierarchy.

**Process**:
1. For each matched SNOMED concept, query its parent concepts (broader terms)
2. Query its child concepts (narrower terms)
3. Fetch full details for each related concept
4. Return as "suggested additional" terms that complement the code system

**Example**:
- Matched term: "Diabetes mellitus" (73211009)
- Hierarchical search finds:
  - Parent: "Disorder of glucose metabolism" (broader concept)
  - Child: "Type 1 diabetes mellitus" (narrower concept)
- Result: Suggestions for related concepts that enhance the code system

**Configuration**:
```yaml
recommendation:
  hierarchical:
    max-results: 10  # Maximum number of parent/child concepts to suggest
```

**API Endpoints Used**:
- `/snowstorm/snomed-ct/{branch}/concepts/{conceptId}/parents`
- `/snowstorm/snomed-ct/{branch}/concepts/{conceptId}/children`

### Strategy 4: AI Agent (Fallback)

**Purpose**: Handle complex cases where other strategies fail.

**Process**:
1. Identify terms that weren't matched by previous strategies
2. Send to AI agent with context (matched SNOMED IDs, domain context)
3. AI analyzes the term and suggests appropriate SNOMED codes
4. Return recommendations with AI-generated explanations

**Example**:
- Unmatched term: "Chronic fatigue syndrome"
- AI analyzes context and suggests: "Chronic fatigue syndrome" (161891005)
- Result: Recommendation with AI-generated reason and definition

**Configuration**:
```yaml
recommendation:
  ai-fallback: true  # Enable AI as fallback
ai:
  enabled: true      # Enable AI service (requires OPENAI_API_KEY)
```

## Recommendation Ranking

All recommendations are:
1. **Deduplicated**: If multiple strategies find the same SNOMED ID, keep the one with highest confidence
2. **Ranked**: Sorted by confidence score (highest first)
3. **Annotated**: Each recommendation includes:
   - Source strategy (synonym database, fuzzy match, hierarchical, AI)
   - Similarity/confidence score
   - Reason for recommendation

## Context Awareness

The system uses context to improve recommendations:

1. **Matched SNOMED IDs**: Used for hierarchical search to find related concepts
2. **Domain Context**: Provided by user (e.g., "Swedish healthcare terminology")
3. **Language**: Respects configured language (Swedish by default)

## Configuration

All configuration is in `application.yml`:

```yaml
recommendation:
  use-hybrid: true                    # Enable hybrid approach
  fuzzy-match:
    min-similarity: 0.4              # Minimum similarity for fuzzy matches
    max-similarity: 0.6              # Maximum similarity (above = direct match)
  hierarchical:
    max-results: 10                  # Max parent/child concepts to suggest
  ai-fallback: true                  # Use AI for unmatched terms

ai:
  enabled: false                     # Enable AI service (requires API key)

snomed:
  language: sv                       # Preferred language for descriptions
  fallback-to-english: true          # Fallback to English if Swedish not available
```

## Testing

### Test Cases

#### 1. Synonym Database Test

**Test**: Verify Swedish terms are matched via synonym database

```bash
# Request
POST /api/ai/recommend
{
  "unmatchedTerms": ["sockersjuka", "hjärtattack"],
  "matchedSnomedIds": [],
  "context": "Swedish healthcare"
}

# Expected: Recommendations with high confidence (1.0) from synonym database
```

#### 2. Fuzzy Matching Test

**Test**: Verify partial/incomplete terms are matched via fuzzy matching

```bash
# Request
POST /api/ai/recommend
{
  "unmatchedTerms": ["diabet", "hjärt"],
  "matchedSnomedIds": [],
  "context": "Swedish healthcare"
}

# Expected: Recommendations with confidence 0.4-0.6 from fuzzy matching
```

#### 3. Hierarchical Search Test

**Test**: Verify related concepts are suggested from matched terms

```bash
# Request
POST /api/ai/recommend
{
  "unmatchedTerms": [],
  "matchedSnomedIds": ["73211009"],  # Diabetes mellitus
  "context": "Swedish healthcare"
}

# Expected: Suggested additional terms (parent/child concepts) in suggestedAdditional array
```

#### 4. AI Fallback Test

**Test**: Verify complex terms use AI as fallback

```bash
# Request
POST /api/ai/recommend
{
  "unmatchedTerms": ["Chronic fatigue syndrome"],
  "matchedSnomedIds": [],
  "context": "Swedish healthcare"
}

# Expected: AI-generated recommendations (if AI enabled)
```

#### 5. Combined Strategy Test

**Test**: Verify multiple strategies work together

```bash
# Request
POST /api/ai/recommend
{
  "unmatchedTerms": ["sockersjuka", "unknown-term"],
  "matchedSnomedIds": ["73211009"],
  "context": "Swedish healthcare"
}

# Expected:
# - "sockersjuka" matched via synonym database
# - Hierarchical suggestions from "73211009"
# - "unknown-term" handled by AI (if enabled) or returned as unmatched
```

### Manual Testing Steps

1. **Start the backend**:
   ```bash
   cd backend
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

2. **Test with curl**:
   ```bash
   curl -X POST http://localhost:8080/api/ai/recommend \
     -H "Content-Type: application/json" \
     -d '{
       "unmatchedTerms": ["sockersjuka"],
       "matchedSnomedIds": [],
       "context": "Swedish healthcare"
     }'
   ```

3. **Check logs** for debug output showing which strategies were used

4. **Verify response** contains:
   - `recommendations`: Array of recommended codes for unmatched terms
   - `suggestedAdditional`: Array of additional related concepts

### Unit Testing

Create test cases in `HybridRecommendationServiceImplTest.java`:

```java
@Test
void testSynonymDatabaseLookup() {
    // Test that "sockersjuka" returns "73211009"
}

@Test
void testFuzzyMatching() {
    // Test that partial terms get fuzzy matches
}

@Test
void testHierarchicalSearch() {
    // Test that matched terms generate hierarchical suggestions
}

@Test
void testDeduplication() {
    // Test that duplicate SNOMED IDs are deduplicated
}
```

### Integration Testing

Test the full flow:

1. Match terms → get matched/unmatched
2. Request recommendations for unmatched terms
3. Verify recommendations are appropriate
4. Build code system with recommendations
5. Export code system

## Performance Considerations

- **Synonym Database**: O(1) lookup - very fast
- **Fuzzy Matching**: Uses existing SNOMED service - moderate speed
- **Hierarchical Search**: Multiple API calls - slower, limited by `max-results`
- **AI Agent**: Network call to OpenAI - slowest, only used as fallback

**Optimization Tips**:
- Increase synonym database coverage to reduce AI calls
- Cache hierarchical search results
- Limit `max-results` for hierarchical search
- Use AI only when other strategies fail

## Extending the System

### Adding More Synonyms

Edit `SynonymDatabaseServiceImpl.java`:

```java
private void initializeSynonyms() {
    // Add new mappings
    addMapping("swedish-term", "SNOMED-ID");
    addSynonymGroup("term1", "term2", "term3");
}
```

### Adjusting Similarity Thresholds

Edit `application.yml`:

```yaml
recommendation:
  fuzzy-match:
    min-similarity: 0.3  # Lower = more candidates
    max-similarity: 0.7  # Higher = stricter matching
```

### Disabling Strategies

```yaml
recommendation:
  use-hybrid: true
  ai-fallback: false  # Disable AI fallback
```

## Troubleshooting

### No Recommendations Returned

1. Check if synonym database has the term
2. Verify fuzzy matching thresholds are appropriate
3. Check if AI is enabled and API key is set
4. Review logs for error messages

### Poor Quality Recommendations

1. Increase synonym database coverage
2. Adjust fuzzy matching thresholds
3. Review hierarchical search results
4. Improve AI prompts (if using AI)

### Performance Issues

1. Reduce `max-results` for hierarchical search
2. Disable AI fallback if not needed
3. Increase synonym database to reduce API calls
4. Add caching for hierarchical searches

