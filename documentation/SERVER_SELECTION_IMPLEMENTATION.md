# Server Selection Implementation Summary

This document summarizes the implementation of server selection (Snowstorm vs Ontoserver) in the Code System Factory application.

## ✅ Implementation Complete

The feature has been successfully implemented and allows users to choose between Snowstorm and Ontoserver for SNOMED CT term matching.

## What Was Implemented

### Backend Changes

#### 1. New Service: `OntoserverSnomedService`
**File**: `backend/src/main/java/com/example/codesys/service/impl/OntoserverSnomedService.java`

- Implements `SnomedService` interface
- Uses FHIR ValueSet $expand operation: `/fhir/ValueSet/$expand?url=...&filter=...`
- Parses FHIR ValueSet expansion response
- Calculates similarity using Jaro-Winkler algorithm
- Returns `TermMatch` objects (same interface as Snowstorm)

**Key Features**:
- Supports Swedish language with English fallback
- Handles multiple search strategies (original term, lowercase, language fallback)
- Extracts concepts from `expansion.contains[]` in FHIR response
- Calculates similarity scores

#### 2. Service Factory: `SnomedServiceFactory`
**File**: `backend/src/main/java/com/example/codesys/service/SnomedServiceFactory.java`

- Selects appropriate service based on user choice
- Defaults to Snowstorm if not specified
- Supports: "snowstorm", "ontoserver", or null/empty

#### 3. Updated Model: `TermRequest`
**File**: `backend/src/main/java/com/example/codesys/model/TermRequest.java`

- Added optional `server` field
- Backward compatible (defaults to Snowstorm if not provided)

#### 4. Updated Controller: `TermsController`
**File**: `backend/src/main/java/com/example/codesys/controller/TermsController.java`

- Uses `SnomedServiceFactory` to get appropriate service
- Routes to selected server based on request parameter

#### 5. Configuration: `application.yml`
**File**: `backend/src/main/resources/application.yml`

- Added `ontoserver.url` configuration
- Default: `https://r4.ontoserver.csiro.au/fhir`
- Configurable via `ONTOSERVER_URL` environment variable

### Frontend Changes

#### 1. Server Selection State
**File**: `frontend/src/ui/App.tsx`

- Added `selectedServer` state: `'snowstorm' | 'ontoserver'`
- Default: `'snowstorm'`

#### 2. UI Dropdown
**File**: `frontend/src/ui/App.tsx`

- Added dropdown selector above "Match Terms" button
- Options: "Snowstorm (Default)" and "Ontoserver (FHIR)"
- Styled to match existing UI

#### 3. API Request Update
**File**: `frontend/src/ui/App.tsx`

- Updated `callMatch()` to include `server` in request body
- Sends server selection to backend

## User Interface

### Visual Layout

```
┌─────────────────────────────────────────┐
│  Enter terms (one per line):             │
│  ┌───────────────────────────────────┐ │
│  │ MRI Heart                          │ │
│  │ Diabetes                           │ │
│  │ Heart attack                       │ │
│  └───────────────────────────────────┘ │
│                                         │
│  SNOMED CT Server: [Snowstorm ▼]       │
│  [Match Terms]                          │
└─────────────────────────────────────────┘
```

### Dropdown Options

1. **Snowstorm (Default)**
   - Uses native API: `/snowstorm/snomed-ct/MAIN/concepts`
   - Current default behavior
   - Fast and comprehensive

2. **Ontoserver (FHIR)**
   - Uses FHIR API: `/fhir/ValueSet/$expand`
   - Standard FHIR R4 terminology server
   - Alternative server option

## How It Works

### Request Flow

1. **User selects server** in dropdown (default: Snowstorm)
2. **User enters terms** and clicks "Match Terms"
3. **Frontend sends request**:
   ```json
   {
     "terms": ["Diabetes", "Heart attack"],
     "server": "snowstorm"  // or "ontoserver"
   }
   ```
4. **Backend receives request**:
   - `TermsController` extracts `server` parameter
   - `SnomedServiceFactory` selects appropriate service
   - Service performs matching using selected server's API
5. **Results returned** in same format regardless of server

### Service Selection Logic

```java
// In SnomedServiceFactory
public SnomedService getService(String server) {
    if (server == null || server.trim().isEmpty()) {
        return snowstormService; // Default
    }
    
    switch (server.toLowerCase()) {
        case "ontoserver":
            return ontoserverService;
        case "snowstorm":
        default:
            return snowstormService;
    }
}
```

## API Differences

### Snowstorm (Native API)

**Request**:
```
GET /snowstorm/snomed-ct/MAIN/concepts?term=Diabetes&limit=50&activeFilter=true
Accept: application/json
Accept-Language: sv
```

**Response**: Custom JSON with `items[]`

### Ontoserver (FHIR API)

**Request**:
```
GET /fhir/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=50
Accept: application/fhir+json
Accept-Language: sv
```

**Response**: FHIR ValueSet with `expansion.contains[]`

## Configuration

### Backend Configuration

```yaml
# Snowstorm (existing)
fhir:
  server:
    url: ${FHIR_SERVER_URL:https://snowstorm-training.snomedtools.org/fhir}

# Ontoserver (new)
ontoserver:
  url: ${ONTOSERVER_URL:https://r4.ontoserver.csiro.au/fhir}
```

### Environment Variables

- `FHIR_SERVER_URL` - Snowstorm server URL (default: `https://snowstorm-training.snomedtools.org/fhir`)
- `ONTOSERVER_URL` - Ontoserver URL (default: `https://r4.ontoserver.csiro.au/fhir`)

## Testing

### Test Snowstorm (Default)

1. Select "Snowstorm (Default)" in dropdown
2. Enter terms: "Diabetes", "Heart attack"
3. Click "Match Terms"
4. Verify results from Snowstorm

### Test Ontoserver

1. Select "Ontoserver (FHIR)" in dropdown
2. Enter terms: "Diabetes", "Heart attack"
3. Click "Match Terms"
4. Verify results from Ontoserver

### Verify Server Used

Check backend console logs:
- Snowstorm: `DEBUG: Searching with URL: .../snowstorm/snomed-ct/...`
- Ontoserver: `DEBUG: Ontoserver search URL: .../ValueSet/$expand...`

## Benefits

1. **Flexibility**: Users can choose the best server for their needs
2. **Fallback**: If one server is unavailable, users can switch
3. **Comparison**: Users can compare results from different servers
4. **Future-proof**: Easy to add more servers (e.g., local Snowstorm instance)
5. **Backward Compatible**: Defaults to Snowstorm if not specified

## Limitations & Future Enhancements

### Current Limitations

1. **FSN Not Available**: Ontoserver ValueSet expansion may not include FSN (Fully Specified Name)
   - **Workaround**: Uses display name as FSN fallback
   - **Future**: Could make separate `$lookup` call to get full concept details

2. **Description Not Available**: Ontoserver expansion doesn't include descriptions
   - **Future**: Could fetch descriptions separately if needed

3. **Language Support**: Both servers support Swedish, but results may vary
   - **Note**: Ontoserver may have different Swedish coverage than Snowstorm

### Future Enhancements

1. **Add More Servers**: Easy to extend with more terminology servers
2. **Server Health Check**: Show server status in UI
3. **Auto-fallback**: Automatically try alternative server if primary fails
4. **Result Comparison**: Side-by-side comparison of results from both servers
5. **Full Concept Details**: For Ontoserver, fetch full details via `$lookup` if needed

## Files Changed

### New Files
- ✅ `backend/src/main/java/com/example/codesys/service/impl/OntoserverSnomedService.java`
- ✅ `backend/src/main/java/com/example/codesys/service/SnomedServiceFactory.java`
- ✅ `documentation/SERVER_SELECTION_DESIGN.md`
- ✅ `documentation/SERVER_SELECTION_IMPLEMENTATION.md`

### Modified Files
- ✅ `backend/src/main/java/com/example/codesys/model/TermRequest.java`
- ✅ `backend/src/main/java/com/example/codesys/controller/TermsController.java`
- ✅ `backend/src/main/resources/application.yml`
- ✅ `frontend/src/ui/App.tsx`

## Summary

✅ **Implementation Complete**

Users can now:
1. Select between Snowstorm and Ontoserver via dropdown
2. Get matching results from selected server
3. Use same UI and workflow regardless of server choice
4. Switch servers easily for comparison or fallback

The implementation uses the **Strategy Pattern** for clean separation of concerns and easy extensibility.

---

**Last Updated**: 2024
**Application Version**: 0.0.2-SNAPSHOT

