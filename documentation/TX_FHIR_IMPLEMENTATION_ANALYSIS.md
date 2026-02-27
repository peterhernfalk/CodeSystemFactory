# Implementation Analysis: tx.fhir.org with Swedish + International Edition

This document analyzes how to add **tx.fhir.org** as a new server option in the frontend, with a two-phase matching strategy:
1. **First:** Call Swedish edition for all input terms
2. **Then:** Call International edition for any unmatched terms
3. **Merge:** Combine results from both calls

---

## 1. Current Architecture

### Backend Flow

```
Frontend                    Backend
   |                           |
   |  POST /api/terms/match    |
   |  { terms, server }        |
   |-------------------------->|
   |                           |
   |                    SnomedServiceFactory.getService(server)
   |                           |
   |                    SnomedService.matchTerms(terms)
   |                           |
   |                    [Single API call to chosen server]
   |                           |
   |  { matched, unmatched }   |
   |<--------------------------|
```

### Key Components

| Component | Role |
|-----------|------|
| `TermsController` | Receives request, calls factory, returns response |
| `SnomedServiceFactory` | Returns appropriate `SnomedService` based on `server` string |
| `SnomedService` (interface) | `matchTerms(List<String> terms) → List<TermMatch>` |
| `FhirSnomedService` | Snowstorm implementation |
| `OntoserverSnomedService` | Ontoserver implementation |
| `TermMatch` | Result record: `input`, `matchedSctId`, `preferredTermSv`, `fsnSv`, `similarity`, `status` |

### Current Server Options (Frontend)

```tsx
<select value={selectedServer} onChange={...}>
  <option value="snowstorm">Snowstorm (Default)</option>
  <option value="ontoserver">Ontoserver (FHIR)</option>
</select>
```

---

## 2. Proposed Implementation

### 2.1 New Service: `TxFhirSnomedService`

A new `SnomedService` implementation that:
1. Calls **Swedish edition** first for all terms
2. Identifies unmatched terms (status = "NO_MATCH")
3. Calls **International edition** for unmatched terms only
4. Merges results (Swedish matches + International matches + remaining unmatched)

### 2.2 Architecture Diagram

```
TxFhirSnomedService.matchTerms(["Diabetes", "Fotledsfraktur", "MRI Heart"])
    |
    |  Step 1: Call Swedish Edition
    |  ─────────────────────────────
    |  POST tx.fhir.org/r4/ValueSet/$expand
    |       url=http://snomed.info/sct/45991000052106/version/20231130?fhir_vs
    |       filter=Diabetes, filter=Fotledsfraktur, filter=MRI Heart
    |
    |  Results:
    |    - Diabetes → MATCHED (Swedish has "Diabetes mellitus")
    |    - Fotledsfraktur → MATCHED (Swedish has "Fotledsfraktur")
    |    - MRI Heart → NO_MATCH (Swedish may not have English term)
    |
    |  Step 2: Call International Edition (for unmatched only)
    |  ────────────────────────────────────────────────────────
    |  POST tx.fhir.org/r4/ValueSet/$expand
    |       url=http://snomed.info/sct/900000000000207008?fhir_vs
    |       filter=MRI Heart
    |
    |  Results:
    |    - MRI Heart → MATCHED (International has "Cardiac MRI")
    |
    |  Step 3: Merge Results
    |  ─────────────────────
    |  Return:
    |    - Diabetes → MATCHED (from Swedish)
    |    - Fotledsfraktur → MATCHED (from Swedish)
    |    - MRI Heart → MATCHED (from International)
```

---

## 3. Detailed Implementation Plan

### 3.1 Backend Changes

#### A. New Configuration (`application.yml`)

```yaml
# tx.fhir.org configuration
tx-fhir:
  url: ${TX_FHIR_URL:https://tx.fhir.org/r4}
  swedish-edition: http://snomed.info/sct/45991000052106/version/20231130?fhir_vs
  international-edition: http://snomed.info/sct/900000000000207008?fhir_vs
```

#### B. New Service Class (`TxFhirSnomedService.java`)

```java
@Service
public class TxFhirSnomedService implements SnomedService {
    
    private final String txFhirUrl;
    private final String swedishEditionUrl;
    private final String internationalEditionUrl;
    private final RestTemplate rest;
    private final JaroWinklerSimilarity jw;
    
    // Constructor with @Value injections...
    
    @Override
    public List<TermMatch> matchTerms(List<String> terms) {
        // Step 1: Try Swedish edition for ALL terms
        List<TermMatch> swedishResults = matchWithEdition(terms, swedishEditionUrl, "sv");
        
        // Step 2: Identify unmatched terms
        List<String> unmatchedTerms = swedishResults.stream()
            .filter(m -> "NO_MATCH".equals(m.status()))
            .map(TermMatch::input)
            .toList();
        
        // Step 3: If there are unmatched terms, try International edition
        Map<String, TermMatch> internationalMatches = new HashMap<>();
        if (!unmatchedTerms.isEmpty()) {
            List<TermMatch> intlResults = matchWithEdition(unmatchedTerms, internationalEditionUrl, "en");
            for (TermMatch m : intlResults) {
                if ("MATCHED".equals(m.status())) {
                    internationalMatches.put(m.input(), m);
                }
            }
        }
        
        // Step 4: Merge results (Swedish first, then International for unmatched)
        List<TermMatch> finalResults = new ArrayList<>();
        for (TermMatch swedishMatch : swedishResults) {
            if ("MATCHED".equals(swedishMatch.status())) {
                // Keep Swedish match
                finalResults.add(swedishMatch);
            } else {
                // Check if International matched it
                TermMatch intlMatch = internationalMatches.get(swedishMatch.input());
                if (intlMatch != null) {
                    finalResults.add(intlMatch);
                } else {
                    // Still unmatched
                    finalResults.add(swedishMatch);
                }
            }
        }
        
        return finalResults;
    }
    
    private List<TermMatch> matchWithEdition(List<String> terms, String editionUrl, String language) {
        // Similar to OntoserverSnomedService.searchViaValueSetExpand()
        // but uses editionUrl as the ValueSet URL
        // ...
    }
}
```

#### C. Update `SnomedServiceFactory`

```java
@Component
public class SnomedServiceFactory {
    
    private final FhirSnomedService snowstormService;
    private final OntoserverSnomedService ontoserverService;
    private final TxFhirSnomedService txFhirService;  // NEW
    
    public SnomedServiceFactory(
            FhirSnomedService snowstormService,
            OntoserverSnomedService ontoserverService,
            TxFhirSnomedService txFhirService) {  // NEW
        this.snowstormService = snowstormService;
        this.ontoserverService = ontoserverService;
        this.txFhirService = txFhirService;  // NEW
    }
    
    public SnomedService getService(String server) {
        if (server == null || server.trim().isEmpty()) {
            return snowstormService;
        }
        
        String serverLower = server.toLowerCase().trim();
        switch (serverLower) {
            case "tx-fhir":
            case "tx-fhir-swedish":
            case "txfhir":
                return txFhirService;  // NEW
            case "ontoserver":
            case "ontoserver-fhir":
                return ontoserverService;
            case "snowstorm":
            case "snowstorm-native":
            default:
                return snowstormService;
        }
    }
}
```

### 3.2 Frontend Changes

#### Update Server Dropdown (`App.tsx`)

```tsx
const [selectedServer, setSelectedServer] = useState<'snowstorm' | 'ontoserver' | 'tx-fhir'>('snowstorm')

// In JSX:
<select value={selectedServer} onChange={e => setSelectedServer(e.target.value as any)}>
  <option value="snowstorm">Snowstorm (Default)</option>
  <option value="ontoserver">Ontoserver (FHIR)</option>
  <option value="tx-fhir">tx.fhir.org (Swedish + International)</option>
</select>
```

---

## 4. API Request Flow

When user selects "tx.fhir.org (Swedish + International)" and enters terms:

### Request 1: Swedish Edition (all terms)

```http
GET https://tx.fhir.org/r4/ValueSet/$expand
    ?url=http://snomed.info/sct/45991000052106/version/20231130?fhir_vs
    &filter=Diabetes
    &count=50
Accept: application/fhir+json
Accept-Language: sv
```

(Repeated for each term, or could batch if API supports)

### Request 2: International Edition (unmatched terms only)

```http
GET https://tx.fhir.org/r4/ValueSet/$expand
    ?url=http://snomed.info/sct/900000000000207008?fhir_vs
    &filter=MRI%20Heart
    &count=50
Accept: application/fhir+json
Accept-Language: en
```

(Only for terms that didn't match in Swedish edition)

---

## 5. Consequences and Trade-offs

### 5.1 Advantages

| Advantage | Description |
|-----------|-------------|
| **Better Swedish support** | Swedish terms like "Fotledsfraktur", "Astma" will match directly |
| **Fallback for English** | English terms still work via International edition |
| **Single user choice** | User selects one option, gets best of both worlds |
| **Preserves Swedish context** | Swedish matches show Swedish preferred terms |
| **No duplicate matches** | Each term matched only once (Swedish preferred) |

### 5.2 Disadvantages

| Disadvantage | Description |
|--------------|-------------|
| **More API calls** | Up to 2× the number of calls (Swedish + International) |
| **Increased latency** | Sequential calls (Swedish first, then International) add time |
| **External dependency** | Relies on tx.fhir.org availability and performance |
| **Edition version management** | Need to update Swedish edition URL when new versions released |
| **Potential rate limits** | tx.fhir.org may have rate limits (need to verify) |

### 5.3 Performance Impact

| Scenario | Current (Snowstorm) | New (tx.fhir.org) |
|----------|---------------------|-------------------|
| 5 terms, all English | ~5 API calls | ~5 Swedish + ~5 International = ~10 calls |
| 5 terms, all Swedish | ~5 API calls (may fail) | ~5 Swedish (all match) = ~5 calls |
| 5 terms, mixed | ~5 API calls | ~5 Swedish + ~2 International = ~7 calls |

**Mitigation:** Could parallelize Swedish calls, then parallelize International calls.

### 5.4 Response Data Differences

| Field | Swedish Edition | International Edition |
|-------|-----------------|----------------------|
| `preferredTermSv` | Swedish term (e.g. "Diabetes mellitus") | English term (e.g. "Diabetes mellitus") |
| `fsnSv` | Swedish FSN | English FSN |
| `matchedSctId` | Same SNOMED CT ID | Same SNOMED CT ID |

**Note:** The field names `preferredTermSv` and `fsnSv` suggest Swedish, but for International matches they will contain English terms. Consider:
- Renaming to `preferredTerm` and `fsn` (generic)
- Or adding a `language` field to indicate source

---

## 6. Alternative Approaches

### 6.1 Parallel Calls (Both Editions Simultaneously)

```
┌─────────────────┐     ┌─────────────────┐
│ Swedish Edition │     │ International   │
│ (all terms)     │     │ (all terms)     │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     │
              Merge (prefer Swedish)
```

**Pros:** Faster (parallel)  
**Cons:** More API calls (always 2× terms), wastes International calls for Swedish-matched terms

### 6.2 Single Call with Combined ValueSet

If tx.fhir.org supports it, could create a combined ValueSet that includes both editions. However, this is not standard and likely not supported.

### 6.3 Keep Separate Options

Instead of one "tx.fhir.org" option, offer two:
- "tx.fhir.org (Swedish)"
- "tx.fhir.org (International)"

**Pros:** Simpler implementation, user control  
**Cons:** User must know which to use, no automatic fallback

---

## 7. Recommended Implementation

### Phase 1: Basic Implementation (Sequential)

1. Create `TxFhirSnomedService` with sequential Swedish → International flow
2. Add to `SnomedServiceFactory`
3. Add frontend dropdown option
4. Test with the four terms: MRI Heart, Diabetes, Astma, Fotledsfraktur

### Phase 2: Optimization (Optional)

1. Parallelize API calls within each phase
2. Add caching for repeated terms
3. Add configuration for edition URLs (not hardcoded)
4. Add logging/metrics for performance monitoring

### Phase 3: Enhancements (Optional)

1. Add `source` field to `TermMatch` indicating which edition matched
2. Allow user to configure preferred language order
3. Add health check for tx.fhir.org availability

---

## 8. Files to Create/Modify

### New Files

| File | Description |
|------|-------------|
| `backend/src/main/java/com/example/codesys/service/impl/TxFhirSnomedService.java` | New service implementation |

### Modified Files

| File | Changes |
|------|---------|
| `backend/src/main/resources/application.yml` | Add tx-fhir configuration |
| `backend/src/main/java/com/example/codesys/service/SnomedServiceFactory.java` | Add TxFhirSnomedService injection and case |
| `frontend/src/ui/App.tsx` | Add "tx-fhir" to server dropdown and state type |

---

## 9. Testing Plan

### Test Cases

| Term | Expected Source | Expected Result |
|------|-----------------|-----------------|
| "Diabetes" | Swedish | MATCHED (Diabetes mellitus) |
| "Astma" | Swedish | MATCHED (Astma) |
| "Fotledsfraktur" | Swedish | MATCHED (Fotledsfraktur / ankle fracture) |
| "MRI Heart" | International | MATCHED (Cardiac MRI) |
| "Cardiac MRI" | International | MATCHED (Cardiac MRI) |
| "NonexistentTerm123" | Neither | NO_MATCH |

### Verification

1. Check that Swedish terms match in first call
2. Check that English terms match in second call
3. Check that unmatched terms remain unmatched
4. Check response times are acceptable
5. Check error handling when tx.fhir.org is unavailable

---

## 10. Summary

Adding tx.fhir.org with Swedish + International fallback is **feasible** and provides significant value for Swedish term matching. The main trade-off is **increased API calls and latency**, which can be mitigated with parallelization.

**Recommended approach:**
1. Implement sequential Swedish → International flow first
2. Test thoroughly with mixed Swedish/English terms
3. Optimize with parallelization if needed
4. Consider adding `source` field to track which edition matched each term
