# Server Selection Design: Snowstorm vs Ontoserver

This document describes the design for adding a user interface choice to select between Snowstorm and Ontoserver for SNOMED CT term matching.

## Overview

Allow users to choose between:
- **Snowstorm** (default) - Uses native API: `/snowstorm/snomed-ct/{branch}/concepts`
- **Ontoserver** - Uses FHIR API: `/fhir/ValueSet/$expand`

## Architecture Design

### Option 1: Strategy Pattern (Recommended) ⭐

Create separate service implementations and select based on user choice.

**Advantages**:
- Clean separation of concerns
- Easy to add more servers in future
- Each service optimized for its API
- Maintainable and testable

**Implementation**:
1. Create `OntoserverSnomedService` implementing `SnomedService`
2. Create `SnomedServiceFactory` to select service based on parameter
3. Update `TermRequest` to include `server` field
4. Frontend adds dropdown to select server

### Option 2: Unified Service with API Detection

Single service that detects server type and uses appropriate API.

**Advantages**:
- Single service class
- Less code duplication

**Disadvantages**:
- More complex logic in one class
- Harder to maintain
- Less flexible

**Recommendation**: Use Option 1 (Strategy Pattern)

---

## Implementation Plan

### Step 1: Backend Changes

#### 1.1 Update TermRequest Model

Add optional `server` field:

```java
public record TermRequest(
    @NotEmpty List<String> terms,
    String server  // "snowstorm" or "ontoserver" (optional, defaults to "snowstorm")
) {}
```

#### 1.2 Create Ontoserver Service

Create `OntoserverSnomedService` implementing `SnomedService`:

```java
@Service
public class OntoserverSnomedService implements SnomedService {
    // Implement FHIR ValueSet $expand approach
    // Parse FHIR ValueSet expansion response
}
```

#### 1.3 Create Service Factory

Create factory to select appropriate service:

```java
@Component
public class SnomedServiceFactory {
    private final FhirSnomedService snowstormService;
    private final OntoserverSnomedService ontoserverService;
    
    public SnomedService getService(String server) {
        if ("ontoserver".equalsIgnoreCase(server)) {
            return ontoserverService;
        }
        return snowstormService; // default
    }
}
```

#### 1.4 Update TermsController

Inject factory and use selected service:

```java
@RestController
@RequestMapping("/api/terms")
public class TermsController {
    private final SnomedServiceFactory serviceFactory;
    
    @PostMapping("/match")
    public TermMatchResponse match(@Valid @RequestBody TermRequest request) {
        String server = request.server() != null ? request.server() : "snowstorm";
        SnomedService service = serviceFactory.getService(server);
        List<TermMatch> matches = service.matchTerms(request.terms());
        // ... rest of logic
    }
}
```

### Step 2: Frontend Changes

#### 2.1 Add Server Selection State

```typescript
const [selectedServer, setSelectedServer] = useState<'snowstorm' | 'ontoserver'>('snowstorm')
```

#### 2.2 Add UI Dropdown

Add dropdown before "Match Terms" button:

```tsx
<div style={{marginBottom: 16}}>
  <label style={{display: 'block', marginBottom: 8, fontWeight: 'bold'}}>
    SNOMED CT Server:
  </label>
  <select 
    value={selectedServer} 
    onChange={e => setSelectedServer(e.target.value as 'snowstorm' | 'ontoserver')}
    style={{padding: '8px 12px', fontSize: '1em', borderRadius: 4, border: '1px solid #ddd'}}
  >
    <option value="snowstorm">Snowstorm (Default)</option>
    <option value="ontoserver">Ontoserver (FHIR)</option>
  </select>
</div>
```

#### 2.3 Update API Call

Include server selection in request:

```typescript
const callMatch = async () => {
  const terms = termsText.split(/\n+/).map(t => t.trim()).filter(Boolean)
  const res = await fetch(`${API_BASE_URL}/terms/match`, {
    method: 'POST', 
    headers: {'Content-Type':'application/json'},
    body: JSON.stringify({ 
      terms,
      server: selectedServer  // Add server selection
    })
  })
  // ... rest of logic
}
```

---

## Detailed Implementation

### Backend: Ontoserver Service

**File**: `backend/src/main/java/com/example/codesys/service/impl/OntoserverSnomedService.java`

**Key Features**:
- Uses FHIR ValueSet $expand: `/fhir/ValueSet/$expand?url=...&filter=...`
- Parses FHIR ValueSet expansion response
- Extracts concepts from `expansion.contains[]`
- Calculates similarity using Jaro-Winkler
- Returns `TermMatch` objects (same interface as Snowstorm)

### Backend: Service Factory

**File**: `backend/src/main/java/com/example/codesys/service/SnomedServiceFactory.java`

**Purpose**: Select appropriate service based on user choice.

### Frontend: Server Selection Component

**Location**: `frontend/src/ui/App.tsx`

**UI Placement**: Above the "Match Terms" button, in the terms input section.

---

## Configuration

### Default Server

- **Default**: Snowstorm (backward compatible)
- **If not specified**: Uses Snowstorm
- **User can override**: Via UI dropdown

### Server URLs

**Snowstorm** (from `application.yml`):
```yaml
fhir:
  server:
    url: ${FHIR_SERVER_URL:https://snowstorm-training.snomedtools.org/fhir}
```

**Ontoserver** (hardcoded or configurable):
```yaml
ontoserver:
  url: https://r4.ontoserver.csiro.au/fhir
```

---

## User Experience

### UI Flow

1. User sees dropdown: "SNOMED CT Server: [Snowstorm ▼]"
2. User can select: Snowstorm or Ontoserver
3. User enters terms
4. User clicks "Match Terms"
5. Backend uses selected server for matching
6. Results displayed (same format regardless of server)

### Visual Design

```
┌─────────────────────────────────────────┐
│  Enter Terms:                           │
│  ┌───────────────────────────────────┐ │
│  │ MRI Heart                          │ │
│  │ Diabetes                           │ │
│  │ Heart attack                       │ │
│  └───────────────────────────────────┘ │
│                                         │
│  SNOMED CT Server: [Snowstorm ▼]       │
│                                         │
│  [Match Terms]                          │
└─────────────────────────────────────────┘
```

---

## Benefits

1. **Flexibility**: Users can choose best server for their needs
2. **Fallback**: If one server is down, try the other
3. **Comparison**: Users can compare results from different servers
4. **Future-proof**: Easy to add more servers (e.g., local Snowstorm)

---

## Testing

### Test Cases

1. **Default (Snowstorm)**:
   - Don't specify server → Should use Snowstorm
   - Select "Snowstorm" → Should use Snowstorm

2. **Ontoserver**:
   - Select "Ontoserver" → Should use Ontoserver FHIR API
   - Verify FHIR ValueSet $expand is called
   - Verify results parsed correctly

3. **Error Handling**:
   - If selected server fails → Show error message
   - Allow user to switch and retry

---

## Implementation Files

### New Files
- `backend/src/main/java/com/example/codesys/service/impl/OntoserverSnomedService.java`
- `backend/src/main/java/com/example/codesys/service/SnomedServiceFactory.java`

### Modified Files
- `backend/src/main/java/com/example/codesys/model/TermRequest.java`
- `backend/src/main/java/com/example/codesys/controller/TermsController.java`
- `frontend/src/ui/App.tsx`
- `backend/src/main/resources/application.yml` (optional: add Ontoserver URL)

---

## Next Steps

1. Implement `OntoserverSnomedService`
2. Create `SnomedServiceFactory`
3. Update `TermRequest` model
4. Update `TermsController`
5. Add UI dropdown in frontend
6. Update API call to include server selection
7. Test both servers
8. Update documentation

---

**Last Updated**: 2024
**Application Version**: 0.0.2-SNAPSHOT

