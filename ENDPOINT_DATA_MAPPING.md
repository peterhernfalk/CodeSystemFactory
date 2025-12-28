# Endpoint Data Mapping Documentation

This document describes how all API endpoints map their output fields to their data sources.

## Overview

The application uses multiple data sources:
- **SNOMED CT FHIR API** (Snowstorm): Primary source for SNOMED CT concepts
- **Synonym Database**: In-memory curated Swedish→SNOMED mappings
- **AI Service (OpenAI)**: AI-generated definitions and recommendations
- **User Input**: Metadata and term selections from frontend
- **Local CSV**: Fallback SNOMED data (sct_demo.csv)

---

## Endpoint: `POST /api/terms/match`

**Purpose**: Match input terms with SNOMED CT concepts

### Request
```json
{
  "terms": ["Diabetes", "Heart attack", "Unknown term"]
}
```

### Response Structure
```json
{
  "matched": [
    {
      "inputTerm": "Diabetes",
      "snomedId": "73211009",
      "preferredTerm": "Diabetes mellitus",
      "fsn": "Diabetes mellitus (disorder)",
      "similarity": 1.05,
      "description": null
    }
  ],
  "unmatched": [
    {
      "inputTerm": "Unknown term",
      "reason": "No match found in SNOMED CT (similarity < 0.75)"
    }
  ]
}
```

### Output Field Mapping

#### `matched[]` Array

| Field | Source | Data Flow |
|-------|--------|-----------|
| `inputTerm` | **Request input** | Direct from `request.terms()` → `TermMatch.input()` |
| `snomedId` | **SNOMED CT FHIR API** | `FhirSnomedService.searchByTerm()` → Snowstorm API `/fhir/ValueSet/$expand` → Concept `id` field |
| `preferredTerm` | **SNOMED CT FHIR API** | Snowstorm response → Concept `pt.term` (preferred language) → Fallback to `descriptions[].term` where `type="SYNONYM"` |
| `fsn` | **SNOMED CT FHIR API** | Snowstorm response → Concept `fsn.term` (preferred language) → Fallback to `descriptions[].term` where `type="FSN"` |
| `similarity` | **Calculated** | Jaro-Winkler algorithm comparing input vs PT/FSN/descriptions → Boosted by matching patterns (exact match, starts with, contains) → Capped at 1.0 |
| `description` | **Not populated** | Always `null` (can be added later) |

#### `unmatched[]` Array

| Field | Source | Data Flow |
|-------|--------|-----------|
| `inputTerm` | **Request input** | Direct from `request.terms()` → `TermMatch.input()` |
| `reason` | **Hardcoded** | Static string: `"No match found in SNOMED CT (similarity < 0.75)"` |

### Data Source Details

**Primary Service**: `FhirSnomedService` (or `LocalSnomedService` if FHIR server unavailable)

**SNOMED CT API Call**:
- **Endpoint**: `{snowstormBaseUrl}/fhir/ValueSet/$expand`
- **Method**: POST
- **Body**: FHIR ValueSet with term search parameters
- **Response**: FHIR ValueSet with matching concepts

**Similarity Calculation**:
1. Base: Jaro-Winkler similarity (input vs PT, FSN, all descriptions)
2. Boost: Pattern matching (exact=1.0, starts with=0.95, contains=0.8-0.85)
3. Additional boost: +0.1 for standard medical terms (capped at 1.0)

---

## Endpoint: `POST /api/ai/recommend`

**Purpose**: Get AI recommendations for unmatched terms and complementary codes

### Request
```json
{
  "unmatchedTerms": ["Unknown term"],
  "matchedSnomedIds": ["73211009"],
  "context": "Swedish healthcare terminology"
}
```

### Response Structure
```json
{
  "recommendations": [
    {
      "inputTerm": "Unknown term",
      "recommendedSnomedId": "123456789",
      "recommendedTerm": "Recommended term",
      "fsn": "Fully specified name (disorder)",
      "confidence": 0.85,
      "reason": "Explanation",
      "definition": "Definition text",
      "relations": ["is-a: Disorder"]
    }
  ],
  "suggestedAdditional": [
    {
      "snomedId": "987654321",
      "term": "Additional term",
      "fsn": "Additional FSN",
      "reason": "Why suggested",
      "definition": "Definition",
      "relations": ["is-a: Procedure"]
    }
  ]
}
```

### Output Field Mapping

#### `recommendations[]` Array

| Field | Source | Data Flow |
|-------|--------|-----------|
| `inputTerm` | **Request input** | Direct from `request.unmatchedTerms()` |
| `recommendedSnomedId` | **Multiple sources** | See strategies below |
| `recommendedTerm` | **SNOMED CT API** | `fetchConceptDetails()` → Snowstorm `/concepts/{id}` → `pt.term` |
| `fsn` | **SNOMED CT API** | `fetchConceptDetails()` → Snowstorm `/concepts/{id}` → `fsn.term` |
| `confidence` | **Calculated/AI** | Strategy-dependent (synonym=1.0, fuzzy=0.4-0.6, AI=0.7-0.95) |
| `reason` | **Strategy-dependent** | "Synonym database match", "Fuzzy match", "AI recommendation" |
| `definition` | **AI Service** | OpenAI GPT-4o-mini response (JSON parsed) → `definition` field |
| `relations` | **AI Service** | OpenAI response → `relations` array |

#### `suggestedAdditional[]` Array

| Field | Source | Data Flow |
|-------|--------|-----------|
| `snomedId` | **SNOMED CT API** | Hierarchical search → Snowstorm `/concepts/{id}/children` or `/parents` → Concept `id` |
| `term` | **SNOMED CT API** | `fetchConceptDetails()` → `pt.term` |
| `fsn` | **SNOMED CT API** | `fetchConceptDetails()` → `fsn.term` |
| `reason` | **Calculated** | "Parent concept", "Child concept", "Sibling concept", "AI suggestion" |
| `definition` | **AI Service** | OpenAI response → `definition` field |
| `relations` | **AI Service** | OpenAI response → `relations` array |

### Recommendation Strategies (Hybrid Approach)

**Strategy 1: Synonym Database**
- **Source**: `SynonymDatabaseServiceImpl` (in-memory map)
- **Data**: Curated Swedish→SNOMED mappings (hardcoded)
- **Output**: Direct SNOMED ID lookup → `fetchConceptDetails()` for PT/FSN

**Strategy 2: Fuzzy Matching**
- **Source**: `SnomedService.matchTerms()` → SNOMED CT API
- **Data**: Terms with similarity 0.4-0.6 (below match threshold)
- **Output**: SNOMED concepts from search results

**Strategy 3: Hierarchical Search**
- **Source**: Snowstorm API `/concepts/{id}/children`, `/parents`, `/concepts/{id}/refsets`
- **Data**: Parent/child/sibling concepts of matched SNOMED IDs
- **Output**: Related concepts from SNOMED hierarchy

**Strategy 4: AI Fallback**
- **Source**: OpenAI GPT-4o-mini (via Spring AI)
- **Data**: AI-generated recommendations based on context
- **Output**: JSON response parsed into recommendations
- **Fallback**: Mock data if AI disabled

---

## Endpoint: `POST /api/ai/definitions`

**Purpose**: Get AI definitions, relations, motivations and use cases for terms

### Request
```json
{
  "terms": [
    {"term": "Diabetes", "snomedId": "73211009"}
  ],
  "context": "Swedish healthcare terminology"
}
```

### Response Structure
```json
{
  "results": [
    {
      "term": "Diabetes",
      "snomedId": "73211009",
      "definition": "Clear, concise Swedish definition",
      "relations": ["is-a: Disorder", "part-of: Endocrine system"],
      "motivation": "Why the term is appropriate",
      "useCases": ["Clinical documentation", "Decision support"]
    }
  ]
}
```

### Output Field Mapping

| Field | Source | Data Flow |
|-------|--------|-----------|
| `term` | **Request input** | Direct from `request.terms()[].term()` |
| `snomedId` | **Request input** | Direct from `request.terms()[].snomedId()` |
| `definition` | **AI Service** | OpenAI GPT-4o-mini → JSON response → `definition` field |
| `relations` | **AI Service** | OpenAI response → `relations` array |
| `motivation` | **AI Service** | OpenAI response → `motivation` field |
| `useCases` | **AI Service** | OpenAI response → `useCases` array |

### Data Source Details

**Service**: `SpringAiService`

**AI Prompt**: Custom prompt requesting structured JSON with definitions, relations, motivation, use cases

**Fallback**: If AI disabled (`ai.enabled=false`), returns mock data:
- `definition`: "Mock definition for {term}"
- `relations`: ["is-a: Example relation"]
- `motivation`: "Selected due to lexical similarity and domain relevance."
- `useCases`: ["Clinical documentation", "Decision support"]

---

## Endpoint: `POST /api/codesystems/build`

**Purpose**: Build a code system from matched and recommended terms

### Request
```json
{
  "metadata": {
    "name": "Swedish Cardiology Terms",
    "version": "1.0.0",
    "description": "Description",
    "publisher": "Publisher",
    "contact": "contact@example.com"
  },
  "matchedTerms": [...],
  "recommendedTerms": [...],
  "suggestedTerms": [...]
}
```

### Response Structure
```json
{
  "codeSystem": {
    "name": "Swedish Cardiology Terms",
    "version": "1.0.0",
    "description": "Description",
    "publisher": "Publisher",
    "contact": "contact@example.com",
    "items": [
      {
        "code": "73211009",
        "display": "Diabetes mellitus",
        "definition": null,
        "relations": [],
        "source": "SNOMED_MATCH"
      }
    ]
  }
}
```

### Output Field Mapping

#### `codeSystem` Object

| Field | Source | Data Flow |
|-------|--------|-----------|
| `name` | **Request input** | Direct from `request.metadata().name()` |
| `version` | **Request input** | Direct from `request.metadata().version()` |
| `description` | **Request input** | Direct from `request.metadata().description()` |
| `publisher` | **Request input** | Direct from `request.metadata().publisher()` |
| `contact` | **Request input** | Direct from `request.metadata().contact()` |
| `items[]` | **Combined from multiple sources** | See items mapping below |

#### `items[]` Array - From Matched Terms

| Field | Source | Data Flow |
|-------|--------|-----------|
| `code` | **Request input** | `request.matchedTerms()[].snomedId()` |
| `display` | **Request input** | `request.matchedTerms()[].preferredTerm()` |
| `definition` | **Request input** | `request.matchedTerms()[].description()` (usually null) |
| `relations` | **Not populated** | Always empty `List.of()` |
| `source` | **Hardcoded** | `"SNOMED_MATCH"` |

#### `items[]` Array - From Recommended Terms

| Field | Source | Data Flow |
|-------|--------|-----------|
| `code` | **Request input** | `request.recommendedTerms()[].snomedId()` |
| `display` | **Request input** | `request.recommendedTerms()[].recommendedTerm()` |
| `definition` | **Request input** | `request.recommendedTerms()[].definition()` (from AI) |
| `relations` | **Request input** | `request.recommendedTerms()[].relations()` (from AI) |
| `source` | **Hardcoded** | `"AI_RECOMMENDATION"` |

#### `items[]` Array - From Suggested Terms

| Field | Source | Data Flow |
|-------|--------|-----------|
| `code` | **Request input** | `request.suggestedTerms()[].snomedId()` |
| `display` | **Request input** | `request.suggestedTerms()[].term()` |
| `definition` | **Request input** | `request.suggestedTerms()[].definition()` (from AI) |
| `relations` | **Request input** | `request.suggestedTerms()[].relations()` (from AI) |
| `source` | **Hardcoded** | `"AI_SUGGESTION"` |

### Data Source Details

**Service**: `CodeSystemBuildService`

**Processing**: Combines all three term types into a single code system structure. No external API calls - purely data transformation.

---

## Endpoint: `POST /api/codesystems/export`

**Purpose**: Export code system in various formats (FHIR, CSV, Excel)

### Request
```json
{
  "codeSystem": {
    "name": "Swedish Cardiology Terms",
    "version": "1.0.0",
    "description": "Description",
    "publisher": "Publisher",
    "contact": "contact@example.com",
    "items": [...]
  },
  "format": "FHIR"
}
```

### Response
- **Content-Type**: Varies by format
  - FHIR: `application/json`
  - CSV: `text/csv`
  - Excel: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- **Body**: Binary file content
- **Headers**: `Content-Disposition: attachment; filename="..."`

### Output Field Mapping

#### FHIR Format

| FHIR Field | Source | Data Flow |
|------------|--------|-----------|
| `resourceType` | **Hardcoded** | `"CodeSystem"` |
| `id` | **Request input** | `sanitizeId(request.codeSystem().name())` |
| `url` | **Generated** | `"http://example.org/fhir/CodeSystem/" + sanitizeId(name)` |
| `version` | **Request input** | `request.codeSystem().version()` |
| `name` | **Request input** | `sanitizeName(request.codeSystem().name())` |
| `title` | **Request input** | `request.codeSystem().name()` |
| `status` | **Hardcoded** | `"draft"` |
| `publisher` | **Request input** | `request.codeSystem().publisher()` |
| `contact[].name` | **Hardcoded** | `"Contact"` |
| `contact[].telecom[].value` | **Request input** | `request.codeSystem().contact()` |
| `content` | **Hardcoded** | `"complete"` |
| `concept[].code` | **Request input** | `request.codeSystem().items()[].code()` |
| `concept[].display` | **Request input** | `request.codeSystem().items()[].display()` |
| `concept[].definition` | **Request input** | `request.codeSystem().items()[].definition()` |

#### CSV Format

| Column | Source | Data Flow |
|--------|--------|-----------|
| `Code` | **Request input** | `request.codeSystem().items()[].code()` |
| `Display` | **Request input** | `request.codeSystem().items()[].display()` |
| `Definition` | **Request input** | `request.codeSystem().items()[].definition()` |
| `Source` | **Request input** | `request.codeSystem().items()[].source()` |

#### Excel Format

**Metadata Sheet**:
| Field | Source |
|-------|--------|
| Name | `request.codeSystem().name()` |
| Version | `request.codeSystem().version()` |
| Description | `request.codeSystem().description()` |
| Publisher | `request.codeSystem().publisher()` |
| Contact | `request.codeSystem().contact()` |

**Items Sheet**:
| Column | Source |
|--------|--------|
| Code | `request.codeSystem().items()[].code()` |
| Display | `request.codeSystem().items()[].display()` |
| Definition | `request.codeSystem().items()[].definition()` |
| Relations | `request.codeSystem().items()[].relations()` joined with `"; "` |
| Source | `request.codeSystem().items()[].source()` |

### Data Source Details

**Service**: `CodeSystemExportService`

**Processing**: Pure data transformation - no external API calls. Formats the code system data into the requested export format.

---

## Endpoint: `GET /api/version`

**Purpose**: Get application version information

### Response Structure
```json
{
  "version": "0.0.1-SNAPSHOT",
  "application": "codesys-backend"
}
```

### Output Field Mapping

| Field | Source | Data Flow |
|-------|--------|-----------|
| `version` | **Maven build info** | `pom.xml` → Spring Boot Maven plugin → `build-info.properties` → `info.build.version` property → Fallback to `project.version` |
| `application` | **Spring config** | `application.yml` → `spring.application.name` → Default: `"codesys-backend"` |

---

## Endpoint: `GET /api-docs`

**Purpose**: Get OpenAPI 3.0 specification

### Response
- **Content-Type**: `application/json`
- **Body**: Complete OpenAPI 3.0 JSON specification

### Output Field Mapping

| Field | Source | Data Flow |
|-------|--------|-----------|
| `openapi` | **Hardcoded** | `"3.0.1"` |
| `info.title` | **OpenApiConfig** | `OpenApiConfig.customOpenAPI()` → `"Code System Builder API"` |
| `info.version` | **OpenApiConfig** | `OpenApiConfig.customOpenAPI()` → `"2.0.0"` |
| `info.description` | **OpenApiConfig** | `OpenApiConfig.customOpenAPI()` → Static description |
| `info.contact` | **OpenApiConfig** | `OpenApiConfig.customOpenAPI()` → Static contact info |
| `servers[]` | **OpenApiConfig** | `OpenApiConfig.customOpenAPI()` → `SERVER_URL` env var or `localhost:${port}` |
| `paths` | **SpringDoc** | Auto-generated from `@RestController` and `@RequestMapping` annotations |
| `components.schemas` | **SpringDoc** | Auto-generated from request/response model classes |

### Data Source Details

**Service**: SpringDoc OpenAPI (automatic)

**Generation**: SpringDoc scans all controllers and generates OpenAPI spec from:
- `@RestController` annotations
- `@RequestMapping` annotations
- `@PostMapping`, `@GetMapping` annotations
- Request/Response model classes (records)

---

## Endpoint: `GET /docs`

**Purpose**: Get documentation information

### Response Structure
```json
{
  "message": "API Documentation",
  "swagger-ui": {
    "url": "/swagger-ui.html",
    "description": "Interactive Swagger UI...",
    "alternative-url": "/swagger-ui/index.html"
  },
  "openapi-spec": {
    "url": "/api-docs",
    "description": "OpenAPI 3.0 specification...",
    "format": "application/json"
  },
  "endpoints": {
    "/api/terms/match": "POST - Match terms with SNOMED CT",
    ...
  }
}
```

### Output Field Mapping

| Field | Source | Data Flow |
|-------|--------|-----------|
| `message` | **Hardcoded** | Static string: `"API Documentation"` |
| `swagger-ui.*` | **Hardcoded** | Static map with Swagger UI URLs and descriptions |
| `openapi-spec.*` | **Hardcoded** | Static map with OpenAPI spec URL and description |
| `endpoints.*` | **Hardcoded** | Static map of endpoint descriptions |

---

## Data Source Summary

### External APIs

1. **SNOMED CT FHIR API (Snowstorm)**
   - Base URL: `https://snowstorm-training.snomedtools.org` (configurable)
   - Endpoints used:
     - `POST /fhir/ValueSet/$expand` - Search concepts
     - `GET /concepts/{id}` - Get concept details
     - `GET /concepts/{id}/children` - Get child concepts
     - `GET /concepts/{id}/parents` - Get parent concepts
   - Data retrieved: Concept ID, Preferred Term, FSN, descriptions, relationships

2. **OpenAI API (via Spring AI)**
   - Service: GPT-4o-mini
   - Used for: AI recommendations, definitions, relations, motivations, use cases
   - Fallback: Mock data when `ai.enabled=false`

### Internal Data Sources

1. **Synonym Database** (`SynonymDatabaseServiceImpl`)
   - Type: In-memory HashMap
   - Data: Curated Swedish→SNOMED mappings
   - Location: Hardcoded in `initializeSynonyms()` method

2. **Local CSV** (`LocalSnomedService`)
   - File: `src/main/resources/sct_demo.csv`
   - Format: CSV with columns: id, svPref, svFsn, keywords
   - Used as: Fallback when FHIR server unavailable

3. **Maven Build Info**
   - Source: `pom.xml` → Spring Boot Maven plugin
   - File: `build-info.properties` (generated at build time)
   - Used for: Version information

4. **Spring Configuration**
   - Source: `application.yml`
   - Used for: Application name, server port, AI settings, SNOMED settings

### Calculated Fields

1. **Similarity Score**
   - Algorithm: Jaro-Winkler similarity
   - Input: User term vs SNOMED PT/FSN/descriptions
   - Boosting: Pattern matching (exact, starts with, contains)
   - Range: 0.0 - 1.0 (capped)

2. **Confidence Score**
   - Strategy-dependent:
     - Synonym match: 1.0
     - Fuzzy match: 0.4 - 0.6
     - AI recommendation: 0.7 - 0.95

3. **Reason Text**
   - Strategy-dependent strings:
     - "Synonym database match"
     - "Fuzzy match (similarity 0.4-0.6)"
     - "Parent concept", "Child concept", "Sibling concept"
     - "AI recommendation"

---

## Data Flow Diagrams

### Term Matching Flow
```
User Input → TermsController
    ↓
SnomedService.matchTerms()
    ↓
FhirSnomedService.searchByTerm()
    ↓
Snowstorm FHIR API ($expand)
    ↓
Extract concept data (id, pt, fsn, descriptions)
    ↓
Calculate similarity (Jaro-Winkler + boosting)
    ↓
TermMatch → MatchedTerm/UnmatchedTerm
    ↓
TermMatchResponse
```

### AI Recommendation Flow
```
User Input → AiRecommendationController
    ↓
AiRecommendationService.recommend()
    ↓
HybridRecommendationService.recommend()
    ↓
┌─────────────────────────────────────┐
│ Strategy 1: Synonym Database          │
│   → SynonymDatabaseService          │
│   → fetchConceptDetails()            │
├─────────────────────────────────────┤
│ Strategy 2: Fuzzy Matching            │
│   → SnomedService.matchTerms()       │
│   → Filter similarity 0.4-0.6         │
├─────────────────────────────────────┤
│ Strategy 3: Hierarchical Search       │
│   → Snowstorm API (children/parents) │
│   → fetchConceptDetails()            │
├─────────────────────────────────────┤
│ Strategy 4: AI Fallback               │
│   → OpenAI GPT-4o-mini               │
│   → Parse JSON response              │
└─────────────────────────────────────┘
    ↓
Deduplicate and rank
    ↓
AiRecommendationResponse
```

### Code System Build Flow
```
User Input → CodeSystemStatelessController
    ↓
CodeSystemBuildService.build()
    ↓
Combine:
  - MatchedTerms → CodeSystemItem (source: SNOMED_MATCH)
  - RecommendedTerms → CodeSystemItem (source: AI_RECOMMENDATION)
  - SuggestedTerms → CodeSystemItem (source: AI_SUGGESTION)
    ↓
CodeSystemBuildResponse
```

---

## Notes

1. **Similarity Calculation**: The similarity score can exceed 1.0 during calculation but is capped at 1.0 before being returned (fix applied).

2. **Language Handling**: The system prefers Swedish (`sv`) language but falls back to English (`en`) if `fallback-to-english: true`.

3. **AI Fallback**: When AI is disabled, mock data is returned to maintain API contract.

4. **Data Deduplication**: Recommendations are deduplicated by SNOMED ID before being returned.

5. **Export Formats**: All export formats use the same input data, just formatted differently.

