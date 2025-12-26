# Code System Factory - Architecture Analysis & Recommendations

## Executive Summary

The current codebase provides a basic foundation for building code systems with SNOMED CT integration and AI assistance, but it does not fully meet the stated requirements. The system currently implements a simple two-step workflow (match terms → get AI definitions) but lacks the multi-step process, dual AI agent architecture, and code system creation capabilities required.

## Current State Analysis

### ✅ What Works Well

1. **REST API Foundation**: Basic REST endpoints exist (`/api/terms/match`, `/api/ai/definitions`)
2. **SNOMED Integration**: Local CSV-based matching service with fallback to FHIR server
3. **AI Integration**: Spring AI integration with OpenAI, with mock mode for development
4. **Frontend-Backend Separation**: Clean separation with React frontend and Spring Boot backend
5. **Data Models**: Well-structured request/response models for terms and AI definitions

### ❌ Critical Gaps

1. **Single AI Agent**: Only one AI agent exists; requirement calls for two agents (one for SNOMED matching, one for complementary suggestions)
2. **Incomplete Workflow**: Current flow is only 2 steps; requirement calls for "several steps"
3. **No Code System Creation**: No endpoint or functionality to create/export the final code system
4. **No Persistence**: Repositories are commented out, so no data is saved between sessions
5. **No Session Management**: No way to track a multi-step workflow across requests
6. **No Complementary Code Suggestions**: Missing the second AI agent that suggests codes to complement SNOMED matches
7. **No Review/Edit Capability**: Users cannot review, modify, or finalize selections before creating the code system
8. **No Structured Output**: No format for exporting the final code system (JSON, FHIR CodeSystem, CSV, etc.)

## Recommended Solution Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (React)                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │ Step 1:  │→ │ Step 2:  │→ │ Step 3:  │→ │ Step 4:  │       │
│  │ Input    │  │ SNOMED   │  │ AI       │  │ Review & │       │
│  │ Terms    │  │ Match    │  │ Suggest  │  │ Export   │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │
└─────────────────────────────────────────────────────────────────┘
                              ↕ REST API
┌─────────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                         │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐    │
│  │ SNOMED Service │  │ AI Agent 1:    │  │ AI Agent 2:    │    │
│  │ (Matching)     │  │ Definition &   │  │ Complementary  │    │
│  │                │  │ Relations     │  │ Suggestions    │    │
│  └────────────────┘  └────────────────┘  └────────────────┘    │
│                                                                  │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐    │
│  │ Workflow       │  │ Code System    │  │ Export Service │    │
│  │ Service        │  │ Builder        │  │ (FHIR/JSON)    │    │
│  └────────────────┘  └────────────────┘  └────────────────┘    │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Database (PostgreSQL/H2)                    │   │
│  │  - Workflow Sessions                                     │   │
│  │  - Term Matches                                          │   │
│  │  - AI Definitions                                        │   │
│  │  - AI Suggestions                                        │   │
│  │  - Code Systems                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Detailed Workflow Steps

#### Step 1: Input Terms
- User enters domain terms (one per line)
- Frontend sends terms to backend
- Backend creates a workflow session and stores initial terms

#### Step 2: SNOMED CT Matching
- Backend calls SNOMED Service to match terms
- Results include: matched SNOMED IDs, preferred terms, FSN, similarity scores
- User can review and accept/reject matches
- Backend stores matches in session

#### Step 3: AI Agent 1 - Definitions & Relations
- For accepted SNOMED matches, AI Agent 1 provides:
  - Definitions
  - Relations (is-a, part-of, etc.)
  - Motivations
  - Use cases
- Backend stores AI definitions in session

#### Step 4: AI Agent 2 - Complementary Suggestions
- AI Agent 2 analyzes the matched SNOMED codes and suggests:
  - Additional SNOMED codes that complement the set
  - Missing concepts that should be included
  - Related codes that enhance the code system
- User reviews and accepts/rejects suggestions
- Backend stores suggestions in session

#### Step 5: Review & Finalize
- User reviews complete set:
  - Original terms
  - SNOMED matches
  - AI definitions
  - Complementary suggestions
- User can edit, remove, or add items
- User provides metadata (name, version, description, etc.)

#### Step 6: Code System Creation
- Backend compiles all accepted items into a structured code system
- Code system includes:
  - Metadata (name, version, description, publisher, etc.)
  - All codes with their properties
  - Hierarchical relationships
  - Definitions and use cases
- System is stored in database

#### Step 7: Export
- User can export code system in various formats:
  - FHIR CodeSystem resource
  - JSON
  - CSV
  - Excel

## Recommended Changes

### Backend Changes

#### 1. Enable and Fix Data Persistence
**Priority: HIGH**

- **Uncomment repositories** (`AiDefinitionRepository`, `TermMatchRepository`)
- **Add new repositories**:
  - `WorkflowSessionRepository` - Track multi-step workflows
  - `CodeSystemRepository` - Store created code systems
  - `AiSuggestionRepository` - Store complementary code suggestions
- **Enable JPA configuration** in `application.yml`
- **Add database migrations** (Flyway or Liquibase) for schema management

#### 2. Create Workflow Session Management
**Priority: HIGH**

- **New Model**: `WorkflowSession` entity with:
  - Session ID (UUID)
  - Current step
  - Status (IN_PROGRESS, COMPLETED, ABANDONED)
  - Timestamps
  - User identifier (if multi-user support needed)
- **New Service**: `WorkflowService` to:
  - Create sessions
  - Update session state
  - Retrieve session data
  - Link terms, matches, definitions, and suggestions to sessions
- **New Controller**: `WorkflowController` with endpoints:
  - `POST /api/workflow/sessions` - Create new session
  - `GET /api/workflow/sessions/{sessionId}` - Get session state
  - `PUT /api/workflow/sessions/{sessionId}/step/{stepNumber}` - Update step
  - `DELETE /api/workflow/sessions/{sessionId}` - Abandon session

#### 3. Implement Second AI Agent (Complementary Suggestions)
**Priority: HIGH**

- **New Service**: `ComplementaryAiService` (implements `AiService` interface or new interface)
- **New Model**: `ComplementarySuggestionRequest` and `ComplementarySuggestionResponse`
- **New Controller Endpoint**: `POST /api/ai/complementary-suggestions`
- **Prompt Strategy**: 
  - Analyze the set of matched SNOMED codes
  - Identify gaps and missing concepts
  - Suggest related codes that would enhance the code system
  - Provide reasoning for each suggestion
- **Response Structure**:
  ```json
  {
    "suggestions": [
      {
        "suggestedSnomedId": "123456789",
        "preferredTerm": "Term",
        "reason": "Complements X because...",
        "category": "MISSING_CONCEPT|RELATED_CODE|ENHANCEMENT",
        "confidence": 0.85
      }
    ]
  }
  ```

#### 4. Enhance First AI Agent
**Priority: MEDIUM**

- **Rename/Refactor**: `SpringAiService` → `DefinitionAiService` for clarity
- **Improve Prompts**: Make prompts more specific to Swedish healthcare context
- **Add Validation**: Validate AI responses before returning
- **Add Retry Logic**: Handle API failures gracefully
- **Add Caching**: Cache similar requests to reduce API calls

#### 5. Create Code System Builder Service
**Priority: HIGH**

- **New Service**: `CodeSystemBuilderService`
- **New Model**: `CodeSystem` entity with:
  - ID, name, version, description
  - Publisher, contact information
  - Status (DRAFT, PUBLISHED)
  - Timestamps
- **New Model**: `CodeSystemItem` entity (one-to-many with CodeSystem):
  - Code (SNOMED ID or custom)
  - Display name
  - Definition
  - Relations
  - Source (SNOMED_MATCH, AI_SUGGESTION, MANUAL)
- **New Controller Endpoint**: `POST /api/codesystems` - Create code system
- **Functionality**:
  - Compile all accepted items from workflow session
  - Build hierarchical structure
  - Validate completeness
  - Generate unique identifiers if needed

#### 6. Create Export Service
**Priority: MEDIUM**

- **New Service**: `CodeSystemExportService`
- **New Controller Endpoints**:
  - `GET /api/codesystems/{id}/export?format=fhir` - Export as FHIR CodeSystem
  - `GET /api/codesystems/{id}/export?format=json` - Export as JSON
  - `GET /api/codesystems/{id}/export?format=csv` - Export as CSV
- **Formats to Support**:
  - **FHIR CodeSystem**: Full FHIR R4 CodeSystem resource
  - **JSON**: Custom structured format
  - **CSV**: Simple tabular format
  - **Excel**: Multi-sheet workbook with metadata

#### 7. Add Review/Edit Capabilities
**Priority: MEDIUM**

- **New Controller Endpoints**:
  - `PUT /api/workflow/sessions/{sessionId}/matches/{matchId}/accept` - Accept/reject match
  - `PUT /api/workflow/sessions/{sessionId}/suggestions/{suggestionId}/accept` - Accept/reject suggestion
  - `PUT /api/workflow/sessions/{sessionId}/items/{itemId}` - Edit item
  - `DELETE /api/workflow/sessions/{sessionId}/items/{itemId}` - Remove item
  - `POST /api/workflow/sessions/{sessionId}/items` - Add manual item

#### 8. Improve Error Handling
**Priority: MEDIUM**

- Add global exception handler (`@ControllerAdvice`)
- Return structured error responses
- Add validation for all request models
- Add logging for debugging

#### 9. Add API Documentation
**Priority: LOW**

- Update `openapi.yaml` with all new endpoints
- Ensure SpringDoc OpenAPI generates complete documentation
- Add examples and descriptions

### Frontend Changes

#### 1. Implement Multi-Step Workflow UI
**Priority: HIGH**

- **Replace simple form** with a step-based wizard component
- **Steps**:
  1. Input Terms
  2. Review SNOMED Matches (with accept/reject)
  3. Review AI Definitions
  4. Review Complementary Suggestions (with accept/reject)
  5. Review & Edit Complete Set
  6. Provide Metadata
  7. Export Code System
- **State Management**: 
  - Use React Context or state management library (Redux/Zustand)
  - Persist workflow session ID
  - Handle navigation between steps
- **Progress Indicator**: Show current step and progress

#### 2. Add Session Management
**Priority: HIGH**

- **Create session** when user starts workflow
- **Store session ID** in local storage or state
- **Resume workflow** if user returns (load from backend)
- **Handle session expiration** gracefully

#### 3. Enhance Match Results UI
**Priority: MEDIUM**

- **Add accept/reject buttons** for each match
- **Show similarity scores** visually (progress bars, colors)
- **Allow manual SNOMED ID entry** for unmatched terms
- **Show FSN and preferred terms** clearly
- **Add search/filter** for large result sets

#### 4. Create AI Suggestions UI
**Priority: HIGH**

- **New Component**: `ComplementarySuggestions.tsx`
- **Display suggestions** with:
  - SNOMED ID and preferred term
  - Reasoning for suggestion
  - Confidence score
  - Category badge
- **Add accept/reject actions** for each suggestion
- **Show preview** of how suggestion fits into existing set

#### 5. Create Review & Edit UI
**Priority: HIGH**

- **New Component**: `CodeSystemReview.tsx`
- **Display complete set** in organized view:
  - Group by source (SNOMED Match, AI Suggestion, Manual)
  - Show hierarchical relationships
  - Display definitions and relations
- **Edit capabilities**:
  - Edit definitions
  - Modify relations
  - Remove items
  - Add manual items
- **Validation**: Ensure all required fields are filled

#### 6. Create Metadata Form
**Priority: MEDIUM**

- **New Component**: `CodeSystemMetadata.tsx`
- **Fields**:
  - Name (required)
  - Version (required)
  - Description
  - Publisher
  - Contact information
  - Status
- **Validation**: Client-side validation before submission

#### 7. Create Export UI
**Priority: MEDIUM**

- **New Component**: `CodeSystemExport.tsx`
- **Format selection**: Radio buttons or dropdown
- **Download button**: Trigger download
- **Preview option**: Show preview before download
- **Success/Error feedback**: Toast notifications

#### 8. Improve Overall UX
**Priority: MEDIUM**

- **Loading states**: Show spinners during API calls
- **Error handling**: Display user-friendly error messages
- **Confirmation dialogs**: For destructive actions
- **Responsive design**: Ensure mobile compatibility
- **Accessibility**: Add ARIA labels, keyboard navigation

#### 9. Add TypeScript Types
**Priority: MEDIUM**

- **Replace `any` types** with proper interfaces
- **Create type definitions** for all API responses
- **Add type safety** throughout components

### Data Model Changes

#### New Entities Needed

1. **WorkflowSession**
   ```java
   - id: UUID
   - currentStep: Integer
   - status: Enum (IN_PROGRESS, COMPLETED, ABANDONED)
   - createdAt: OffsetDateTime
   - updatedAt: OffsetDateTime
   - userId: String (optional)
   ```

2. **CodeSystem**
   ```java
   - id: UUID
   - name: String
   - version: String
   - description: String
   - publisher: String
   - contact: String
   - status: Enum (DRAFT, PUBLISHED)
   - workflowSessionId: UUID (FK)
   - createdAt: OffsetDateTime
   ```

3. **CodeSystemItem**
   ```java
   - id: UUID
   - codeSystemId: UUID (FK)
   - code: String (SNOMED ID or custom)
   - display: String
   - definition: String
   - source: Enum (SNOMED_MATCH, AI_SUGGESTION, MANUAL)
   - parentCode: String (nullable, for hierarchy)
   - relationsJson: String
   - createdAt: OffsetDateTime
   ```

4. **ComplementarySuggestion**
   ```java
   - id: UUID
   - workflowSessionId: UUID (FK)
   - suggestedSnomedId: String
   - preferredTerm: String
   - reason: String
   - category: Enum (MISSING_CONCEPT, RELATED_CODE, ENHANCEMENT)
   - confidence: Double
   - accepted: Boolean
   - createdAt: OffsetDateTime
   ```

#### Enhanced Existing Entities

1. **TermMatchEntity**: Add `workflowSessionId` and `accepted` field
2. **AiDefinitionEntity**: Add `workflowSessionId` field

### API Endpoint Summary

#### New Endpoints Needed

**Workflow Management:**
- `POST /api/workflow/sessions` - Create session
- `GET /api/workflow/sessions/{sessionId}` - Get session
- `PUT /api/workflow/sessions/{sessionId}/step/{stepNumber}` - Update step
- `DELETE /api/workflow/sessions/{sessionId}` - Delete session

**SNOMED Matching (Enhanced):**
- `POST /api/terms/match` - (Existing, enhance to accept sessionId)
- `PUT /api/workflow/sessions/{sessionId}/matches/{matchId}/accept` - Accept/reject match

**AI Definitions (Enhanced):**
- `POST /api/ai/definitions` - (Existing, enhance to accept sessionId)

**Complementary Suggestions (New):**
- `POST /api/ai/complementary-suggestions` - Get complementary suggestions
- `PUT /api/workflow/sessions/{sessionId}/suggestions/{suggestionId}/accept` - Accept/reject

**Code System Management:**
- `POST /api/codesystems` - Create code system
- `GET /api/codesystems/{id}` - Get code system
- `GET /api/codesystems` - List code systems
- `GET /api/codesystems/{id}/export?format={format}` - Export

**Review & Edit:**
- `PUT /api/workflow/sessions/{sessionId}/items/{itemId}` - Edit item
- `DELETE /api/workflow/sessions/{sessionId}/items/{itemId}` - Remove item
- `POST /api/workflow/sessions/{sessionId}/items` - Add manual item

## Implementation Priority

### Phase 1: Foundation (Critical Path)
1. Enable data persistence (repositories, JPA)
2. Create workflow session management
3. Implement second AI agent for complementary suggestions
4. Create code system builder service

### Phase 2: Core Functionality
5. Multi-step frontend workflow
6. Review and edit capabilities
7. Code system creation endpoint
8. Basic export (JSON format)

### Phase 3: Enhancement
9. Export service (FHIR, CSV, Excel)
10. Enhanced UI/UX
11. Error handling and validation
12. Documentation

## Technical Considerations

### AI Agent Architecture
- **Agent 1 (Definition)**: Focuses on enriching matched SNOMED codes with definitions, relations, and context
- **Agent 2 (Complementary)**: Analyzes the code set holistically and suggests missing or related codes
- **Prompt Engineering**: Both agents need carefully crafted prompts with:
  - Clear instructions
  - Examples
  - Output format specifications
  - Context about Swedish healthcare terminology

### Performance Considerations
- **Caching**: Cache SNOMED lookups and similar AI requests
- **Async Processing**: Consider async processing for AI calls if they're slow
- **Pagination**: Paginate large result sets
- **Database Indexing**: Index workflowSessionId, codeSystemId for performance

### Security Considerations
- **Input Validation**: Validate all user inputs
- **Rate Limiting**: Limit AI API calls to prevent abuse
- **Session Security**: Secure session IDs, prevent session hijacking
- **API Authentication**: Add authentication if multi-user support is needed

## Conclusion

The current codebase provides a good starting point but requires significant enhancements to meet the requirements. The most critical gaps are:

1. **Missing second AI agent** for complementary suggestions
2. **No workflow/session management** for multi-step process
3. **No code system creation/export** functionality
4. **Disabled data persistence**

Following the recommended architecture and implementation plan will result in a robust system that fully meets the stated requirements while maintaining clean separation of concerns and extensibility for future enhancements.

