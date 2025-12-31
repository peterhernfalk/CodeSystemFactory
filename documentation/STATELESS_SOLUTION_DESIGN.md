# Stateless Code System Factory - Solution Design

## Overview

This document describes a **stateless solution** that removes database dependencies and implements a workflow where the frontend maintains state and the backend processes requests on-demand.

## Architecture Principles

1. **Stateless Backend**: No database storage - all state maintained in frontend
2. **Request-Response Pattern**: Each API call is independent
3. **Frontend State Management**: React state holds all workflow data
4. **On-Demand Processing**: Backend processes and returns results immediately
5. **Export on Creation**: Code systems are generated and exported, not stored

## Workflow Steps

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (State Management)              │
├─────────────────────────────────────────────────────────────┤
│ Step 1: User enters/edits terms                            │
│ Step 2: POST /api/terms/match → Get matched/unmatched      │
│ Step 3: User edits unmatched terms                         │
│ Step 4: POST /api/ai/recommend → Get AI recommendations    │
│ Step 5: User builds code system (combines matched + AI)     │
│ Step 6: User adds metadata                                  │
│ Step 7: POST /api/codesystems/export → Download file       │
└─────────────────────────────────────────────────────────────┘
```

## API Endpoints

### 1. Term Matching

**Endpoint**: `POST /api/terms/match`

**Request**:
```json
{
  "terms": ["Diabetes", "Heart attack", "Unknown term"]
}
```

**Response**:
```json
{
  "matched": [
    {
      "inputTerm": "Diabetes",
      "snomedId": "73211009",
      "preferredTerm": "Diabetes mellitus",
      "fsn": "Diabetes mellitus (disorder)",
      "similarity": 0.95,
      "description": "A metabolic disorder..."
    },
    {
      "inputTerm": "Heart attack",
      "snomedId": "22298006",
      "preferredTerm": "Myocardial infarction",
      "fsn": "Myocardial infarction (disorder)",
      "similarity": 0.88,
      "description": "Necrosis of heart muscle..."
    }
  ],
  "unmatched": [
    {
      "inputTerm": "Unknown term",
      "reason": "No match found in SNOMED CT"
    }
  ]
}
```

### 2. AI Recommendations

**Endpoint**: `POST /api/ai/recommend`

**Request**:
```json
{
  "unmatchedTerms": ["Unknown term", "Another term"],
  "matchedSnomedIds": ["73211009", "22298006"],
  "context": "Swedish cardiology terminology"
}
```

**Response**:
```json
{
  "recommendations": [
    {
      "inputTerm": "Unknown term",
      "recommendedSnomedId": "123456789",
      "recommendedTerm": "Recommended SNOMED term",
      "fsn": "Recommended term (disorder)",
      "confidence": 0.85,
      "reason": "Closely related to matched terms and fits context",
      "definition": "Definition of recommended term",
      "relations": ["is-a: Disorder", "associated-with: Cardiovascular"]
    }
  ],
  "suggestedAdditional": [
    {
      "snomedId": "987654321",
      "term": "Additional related concept",
      "fsn": "Additional concept (disorder)",
      "reason": "Complements existing matched terms",
      "definition": "Definition",
      "relations": ["is-a: Disorder"]
    }
  ]
}
```

### 3. Build Code System

**Endpoint**: `POST /api/codesystems/build`

**Request**:
```json
{
  "metadata": {
    "name": "Swedish Cardiology Terms",
    "version": "1.0.0",
    "description": "Code system for Swedish cardiology",
    "publisher": "Swedish Health Authority",
    "contact": "contact@example.se"
  },
  "matchedTerms": [
    {
      "inputTerm": "Diabetes",
      "snomedId": "73211009",
      "preferredTerm": "Diabetes mellitus",
      "fsn": "Diabetes mellitus (disorder)",
      "description": "A metabolic disorder..."
    }
  ],
  "recommendedTerms": [
    {
      "inputTerm": "Unknown term",
      "snomedId": "123456789",
      "recommendedTerm": "Recommended SNOMED term",
      "fsn": "Recommended term (disorder)",
      "definition": "Definition",
      "relations": ["is-a: Disorder"]
    }
  ]
}
```

**Response**:
```json
{
  "codeSystem": {
    "name": "Swedish Cardiology Terms",
    "version": "1.0.0",
    "description": "Code system for Swedish cardiology",
    "publisher": "Swedish Health Authority",
    "contact": "contact@example.se",
    "items": [
      {
        "code": "73211009",
        "display": "Diabetes mellitus",
        "definition": "A metabolic disorder...",
        "source": "SNOMED_MATCH"
      },
      {
        "code": "123456789",
        "display": "Recommended SNOMED term",
        "definition": "Definition",
        "source": "AI_RECOMMENDATION"
      }
    ]
  }
}
```

### 4. Export Code System

**Endpoint**: `POST /api/codesystems/export`

**Request**:
```json
{
  "codeSystem": {
    "name": "Swedish Cardiology Terms",
    "version": "1.0.0",
    "description": "Code system for Swedish cardiology",
    "publisher": "Swedish Health Authority",
    "contact": "contact@example.se",
    "items": [...]
  },
  "format": "FHIR" | "CSV" | "EXCEL"
}
```

**Response**:
- **FHIR**: Returns FHIR CodeSystem resource as JSON
- **CSV**: Returns CSV file (Content-Type: text/csv)
- **EXCEL**: Returns Excel file (Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)

## Data Models

### TermMatchResponse (Enhanced)
```java
public record TermMatchResponse(
    List<MatchedTerm> matched,
    List<UnmatchedTerm> unmatched
) {
    public record MatchedTerm(
        String inputTerm,
        String snomedId,
        String preferredTerm,
        String fsn,
        double similarity,
        String description
    ) {}
    
    public record UnmatchedTerm(
        String inputTerm,
        String reason
    ) {}
}
```

### AiRecommendationRequest
```java
public record AiRecommendationRequest(
    List<String> unmatchedTerms,
    List<String> matchedSnomedIds,
    String context
) {}
```

### AiRecommendationResponse
```java
public record AiRecommendationResponse(
    List<Recommendation> recommendations,
    List<SuggestedTerm> suggestedAdditional
) {
    public record Recommendation(
        String inputTerm,
        String recommendedSnomedId,
        String recommendedTerm,
        String fsn,
        double confidence,
        String reason,
        String definition,
        List<String> relations
    ) {}
    
    public record SuggestedTerm(
        String snomedId,
        String term,
        String fsn,
        String reason,
        String definition,
        List<String> relations
    ) {}
}
```

### CodeSystemBuildRequest
```java
public record CodeSystemBuildRequest(
    CodeSystemMetadata metadata,
    List<MatchedTerm> matchedTerms,
    List<RecommendedTerm> recommendedTerms
) {
    public record CodeSystemMetadata(
        String name,
        String version,
        String description,
        String publisher,
        String contact
    ) {}
    
    public record MatchedTerm(
        String inputTerm,
        String snomedId,
        String preferredTerm,
        String fsn,
        String description
    ) {}
    
    public record RecommendedTerm(
        String inputTerm,
        String snomedId,
        String recommendedTerm,
        String fsn,
        String definition,
        List<String> relations
    ) {}
}
```

### CodeSystemBuildResponse
```java
public record CodeSystemBuildResponse(
    CodeSystem codeSystem
) {
    public record CodeSystem(
        String name,
        String version,
        String description,
        String publisher,
        String contact,
        List<CodeSystemItem> items
    ) {}
    
    public record CodeSystemItem(
        String code,
        String display,
        String definition,
        List<String> relations,
        String source
    ) {}
}
```

### CodeSystemExportRequest
```java
public record CodeSystemExportRequest(
    CodeSystem codeSystem,
    ExportFormat format
) {
    public enum ExportFormat {
        FHIR, CSV, EXCEL
    }
}
```

## Implementation Changes

### Backend Changes

1. **Remove Database Dependencies**:
   - Remove `@Entity` annotations from models
   - Remove repository injections from controllers
   - Remove database save operations
   - Keep only request/response processing

2. **Update Controllers**:
   - `TermsController`: Remove repository save, return enhanced response
   - `AiController`: Split into `/definitions` (existing) and `/recommend` (new)
   - `CodeSystemController`: Replace with build/export endpoints

3. **Create New Services**:
   - `AiRecommendationService`: AI agent for recommending codes
   - `CodeSystemExportService`: Export to FHIR, CSV, Excel

4. **Update Response Models**:
   - Enhance `TermMatchResponse` to separate matched/unmatched
   - Create new recommendation models
   - Create export models

### Frontend Changes

1. **State Management**:
   ```typescript
   interface AppState {
     terms: string[];
     matchedTerms: MatchedTerm[];
     unmatchedTerms: UnmatchedTerm[];
     aiRecommendations: Recommendation[];
     codeSystem: CodeSystem | null;
     metadata: CodeSystemMetadata;
   }
   ```

2. **Workflow Components**:
   - `TermInput`: Enter/edit terms
   - `MatchResults`: Display matched/unmatched
   - `UnmatchedEditor`: Edit unmatched terms
   - `AiRecommendations`: Display AI recommendations
   - `CodeSystemBuilder`: Combine matched + recommended
   - `MetadataForm`: Add metadata
   - `ExportButton`: Download code system

3. **API Integration**:
   - Call endpoints in sequence
   - Maintain state between calls
   - Handle errors gracefully

## Export Formats

### FHIR CodeSystem Format
```json
{
  "resourceType": "CodeSystem",
  "id": "swedish-cardiology-terms",
  "url": "http://example.org/fhir/CodeSystem/swedish-cardiology-terms",
  "version": "1.0.0",
  "name": "SwedishCardiologyTerms",
  "title": "Swedish Cardiology Terms",
  "status": "draft",
  "publisher": "Swedish Health Authority",
  "contact": [{
    "name": "Contact",
    "telecom": [{"system": "email", "value": "contact@example.se"}]
  }],
  "content": "complete",
  "concept": [
    {
      "code": "73211009",
      "display": "Diabetes mellitus",
      "definition": "A metabolic disorder..."
    }
  ]
}
```

### CSV Format
```csv
Code,Display,Definition,Source
73211009,Diabetes mellitus,"A metabolic disorder...",SNOMED_MATCH
123456789,Recommended term,"Definition...",AI_RECOMMENDATION
```

### Excel Format
- Sheet 1: Metadata (Name, Version, Description, Publisher, Contact)
- Sheet 2: Items (Code, Display, Definition, Relations, Source)

## Updated Swagger Documentation

See updated `openapi.yaml` with:
- Enhanced term matching endpoint
- AI recommendation endpoint
- Code system build endpoint
- Export endpoints (FHIR, CSV, Excel)
- All new request/response models

## Benefits of Stateless Approach

1. **Simplicity**: No database setup or migrations
2. **Scalability**: Stateless servers can scale horizontally
3. **Flexibility**: Frontend controls workflow
4. **Portability**: Easy to deploy (no DB dependencies)
5. **Development Speed**: Faster iteration without DB schema changes

## Limitations

1. **No Persistence**: Code systems are not saved
2. **No History**: Cannot retrieve previous code systems
3. **No Collaboration**: Cannot share code systems between users
4. **Session Loss**: Refresh loses all state (can be mitigated with localStorage)

## Frontend State Persistence (Optional)

To handle browser refresh, use `localStorage`:

```typescript
// Save state
localStorage.setItem('codeSystemState', JSON.stringify(state));

// Restore state
const saved = localStorage.getItem('codeSystemState');
if (saved) {
  setState(JSON.parse(saved));
}
```

## Summary

This stateless solution:
- ✅ Removes all database dependencies
- ✅ Implements complete workflow via API
- ✅ Supports export in multiple formats
- ✅ Maintains frontend state
- ✅ Provides clean separation of concerns
- ✅ Enables easy deployment and scaling

