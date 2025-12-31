# Stateless Solution - Implementation Guide

## Solution Overview

This document describes a **complete stateless solution** that removes all database dependencies while supporting the full workflow for creating and exporting code systems.

## Complete Workflow

### Step-by-Step Process

1. **User Input**: User enters/edits list of terms in frontend
2. **SNOMED Matching**: Frontend sends terms → Backend matches → Returns matched/unmatched
3. **Edit Unmatched**: User edits unmatched terms in frontend
4. **AI Recommendations**: Frontend sends unmatched terms → AI recommends codes → Returns recommendations
5. **Build Code System**: User combines matched + recommended terms → Frontend builds structure
6. **Add Metadata**: User adds name, version, description, publisher, contact
7. **Export**: User downloads as CSV/Excel or FHIR JSON

## API Endpoints Summary

| Endpoint | Method | Purpose | Input | Output |
|----------|--------|---------|-------|--------|
| `/api/terms/match` | POST | Match terms to SNOMED CT | List of terms | Matched + Unmatched terms |
| `/api/ai/recommend` | POST | Get AI recommendations | Unmatched terms + matched IDs | Recommendations + suggestions |
| `/api/ai/definitions` | POST | Get definitions for terms | Terms with SNOMED IDs | Definitions, relations, use cases |
| `/api/codesystems/build` | POST | Build code system structure | Metadata + matched + recommended | Code system structure |
| `/api/codesystems/export` | POST | Export code system | Code system + format | File (FHIR/CSV/Excel) |

## Detailed API Specifications

### 1. Term Matching Endpoint

**POST** `/api/terms/match`

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
      "description": "A metabolic disorder characterized by hyperglycemia"
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

**Implementation Notes**:
- Backend calls `SnomedService.matchTerms()`
- Separates results into matched (status=MATCHED) and unmatched (status=NO_MATCH)
- No database save - just return results

### 2. AI Recommendation Endpoint

**POST** `/api/ai/recommend`

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

**Implementation Notes**:
- New AI service that analyzes unmatched terms
- Considers context from matched SNOMED IDs
- Suggests both replacements for unmatched terms AND additional complementary codes
- Uses prompt engineering to understand domain context

### 3. Code System Build Endpoint

**POST** `/api/codesystems/build`

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
        "relations": [],
        "source": "SNOMED_MATCH"
      },
      {
        "code": "123456789",
        "display": "Recommended SNOMED term",
        "definition": "Definition",
        "relations": ["is-a: Disorder"],
        "source": "AI_RECOMMENDATION"
      }
    ]
  }
}
```

**Implementation Notes**:
- Combines matched and recommended terms into unified structure
- Assigns source (SNOMED_MATCH or AI_RECOMMENDATION)
- Validates structure
- No database save - just return structure

### 4. Export Endpoint

**POST** `/api/codesystems/export`

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
  "format": "FHIR"
}
```

**Response Formats**:

**FHIR (Content-Type: application/json)**:
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

**CSV (Content-Type: text/csv)**:
```csv
Code,Display,Definition,Source
73211009,Diabetes mellitus,"A metabolic disorder...",SNOMED_MATCH
123456789,Recommended SNOMED term,"Definition...",AI_RECOMMENDATION
```

**Excel (Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)**:
- Sheet 1: Metadata
- Sheet 2: Items

**Implementation Notes**:
- Use Apache POI for Excel generation
- Use Jackson for FHIR JSON
- Use simple CSV writer for CSV
- Return appropriate Content-Type headers
- Set Content-Disposition header for file download

## Frontend State Management

### State Structure
```typescript
interface AppState {
  // Step 1: Input
  terms: string[];
  
  // Step 2: Matching results
  matchedTerms: MatchedTerm[];
  unmatchedTerms: UnmatchedTerm[];
  
  // Step 3: AI recommendations
  aiRecommendations: Recommendation[];
  suggestedAdditional: SuggestedTerm[];
  
  // Step 4: Code system
  codeSystem: CodeSystem | null;
  metadata: CodeSystemMetadata;
}
```

### Component Flow
```
TermInput
  ↓ (on submit)
  → POST /api/terms/match
  ↓
MatchResults (shows matched + unmatched)
  ↓ (user edits unmatched)
  → POST /api/ai/recommend
  ↓
AiRecommendations (shows recommendations)
  ↓ (user selects items)
  → Build code system in frontend
  ↓
CodeSystemBuilder (shows combined items)
  ↓ (user adds metadata)
MetadataForm
  ↓ (user clicks export)
  → POST /api/codesystems/export
  ↓
File download
```

## Implementation Checklist

### Backend Changes

- [ ] Remove all `@Entity` annotations
- [ ] Remove all repository dependencies
- [ ] Remove database save operations from controllers
- [ ] Update `TermMatchResponse` to separate matched/unmatched
- [ ] Create `AiRecommendationService` interface and implementation
- [ ] Create `AiRecommendationController` with `/recommend` endpoint
- [ ] Create `CodeSystemBuildService` for building code systems
- [ ] Create `CodeSystemExportService` for FHIR/CSV/Excel export
- [ ] Update `TermsController` to return enhanced response
- [ ] Remove `CodeSystemController` endpoints (replace with build/export)
- [ ] Add export dependencies (Apache POI for Excel)
- [ ] Update error handling (no database exceptions)

### Frontend Changes

- [ ] Update state management to handle new workflow
- [ ] Create `TermInput` component with edit capability
- [ ] Update `MatchResults` to show matched/unmatched separately
- [ ] Create `UnmatchedEditor` component
- [ ] Create `AiRecommendations` component
- [ ] Create `CodeSystemBuilder` component
- [ ] Create `MetadataForm` component
- [ ] Create `ExportButton` component
- [ ] Update API calls to new endpoints
- [ ] Add localStorage for state persistence (optional)
- [ ] Add file download handling

### Configuration Changes

- [ ] Remove JPA dependencies from `pom.xml`
- [ ] Remove database configuration from `application.yml`
- [ ] Remove H2/PostgreSQL dependencies
- [ ] Add Apache POI dependency for Excel export
- [ ] Update `openapi.yaml` (or use `openapi-stateless.yaml`)

## Export Implementation Details

### FHIR CodeSystem Export

```java
public class FhirCodeSystemExporter {
    public String export(CodeSystem codeSystem) {
        // Build FHIR CodeSystem resource
        // Convert items to FHIR concepts
        // Return JSON string
    }
}
```

### CSV Export

```java
public class CsvExporter {
    public String export(CodeSystem codeSystem) {
        // Build CSV with headers
        // Add metadata row
        // Add items rows
        // Return CSV string
    }
}
```

### Excel Export

```java
public class ExcelExporter {
    public byte[] export(CodeSystem codeSystem) {
        // Create workbook
        // Sheet 1: Metadata
        // Sheet 2: Items
        // Return byte array
    }
}
```

## Benefits

1. **No Database Setup**: Easier deployment
2. **Stateless**: Can scale horizontally
3. **Simple**: Less complexity
4. **Fast Development**: No schema migrations
5. **Portable**: Easy to move between environments

## Limitations

1. **No Persistence**: Code systems not saved
2. **No History**: Cannot retrieve previous systems
3. **No Collaboration**: Cannot share between users
4. **State Loss**: Browser refresh loses state (mitigated with localStorage)

## Testing Strategy

1. **Unit Tests**: Test services without database
2. **Integration Tests**: Test API endpoints
3. **Frontend Tests**: Test workflow components
4. **E2E Tests**: Test complete workflow

## Migration Path

If you want to migrate from database to stateless:

1. Remove entity annotations
2. Remove repository injections
3. Update controllers to remove save operations
4. Update response models
5. Add new endpoints (recommend, build, export)
6. Update frontend to new workflow
7. Remove database dependencies

## Summary

This stateless solution provides:
- ✅ Complete workflow support
- ✅ No database dependencies
- ✅ Multiple export formats
- ✅ Clean API design
- ✅ Scalable architecture
- ✅ Easy deployment

The solution is ready for implementation following the specifications in this guide and the OpenAPI documentation.

