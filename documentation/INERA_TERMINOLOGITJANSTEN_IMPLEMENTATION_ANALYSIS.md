# Implementation Analysis: Inera Terminologitjänsten as Frontend Option

This document analyses the changes required to add **Inera Terminologitjänsten** (Swedish SNOMED CT FHIR API) as a selectable terminology server in the Code System Factory app.

## 1. Current Architecture

- **SnomedService** interface: `List<TermMatch> matchTerms(List<String> terms)`
- **SnomedServiceFactory** selects implementation based on `request.server()` (e.g. `"snowstorm"`, `"ontoserver"`).
- **TermsController** calls `serviceFactory.getService(request.server()).matchTerms(request.terms())`.
- **Frontend** sends `server: selectedServer` in the POST body to `/api/terms/match` and shows a dropdown with Snowstorm and Ontoserver.

Inera’s API is FHIR R4 and supports **ValueSet $expand** with a text filter, same pattern as Ontoserver. The Swedish edition is identified by the ValueSet URL:

`http://snomed.info/sct/45991000052106/version/20251130?fhir_vs`

Base URL for the FHIR API: `https://terminologitjansten.inera.se/fhir`

---

## 2. Required Changes (Summary)

| Layer        | Change |
|-------------|--------|
| Backend     | New `IneraSnomedService` implementing `SnomedService`. |
| Backend     | New `inera.*` configuration in `application.yml`. |
| Backend     | Register Inera in `SnomedServiceFactory` and handle `"inera"` (and optional aliases). |
| Frontend    | Add `"inera"` to server type and dropdown. |
| API contract| No change (`server` is already a free-form string). |

---

## 3. Backend Changes

### 3.1 New service: `IneraSnomedService`

- **Location:** `backend/src/main/java/com/example/codesys/service/impl/IneraSnomedService.java`
- **Implements:** `SnomedService`
- **Behaviour:** Same as Ontoserver for term matching:
  - For each term, call **ValueSet $expand** on Inera:
    - Base: configurable (e.g. `https://terminologitjansten.inera.se/fhir`)
    - Path: `/ValueSet/$expand`
    - Query: `url=<valueset-url>&filter=<term>&count=50`
  - Parse `expansion.contains[]` for `code` and `display`.
  - Use **Jaro-Winkler** similarity and return best match if similarity ≥ 0.75; otherwise `NO_MATCH`.
- **Config:** Inject at least:
  - `inera.url` – FHIR base (e.g. `https://terminologitjansten.inera.se/fhir`)
  - `inera.valueset-url` – Swedish edition ValueSet (e.g. `http://snomed.info/sct/45991000052106/version/20251130?fhir_vs`)
- **Optional:** Support `snomed.language` / `snomed.fallback-to-english` and/or alternative search terms (e.g. "Astma" → "Asthma") to improve matches; can mirror OntoserverSnomedService.
- **Edge case:** If `inera.url` is empty, return `NO_MATCH` for all terms (same pattern as Ontoserver).

Implementation can follow `OntoserverSnomedService` closely: same REST call shape, same response parsing, same similarity and threshold logic. Only base URL and ValueSet URL differ.

### 3.2 Configuration: `application.yml`

Add a dedicated block, e.g.:

```yaml
# Inera Terminologitjänsten (Swedish SNOMED CT FHIR API)
inera:
  url: ${INERA_URL:https://terminologitjansten.inera.se/fhir}
  valueset-url: ${INERA_VALUESET_URL:http://snomed.info/sct/45991000052106/version/20251130?fhir_vs}
```

- `inera.url`: base URL of the FHIR API (no trailing slash in code).
- `inera.valueset-url`: full ValueSet URL for the Swedish edition; can be overridden for another version (e.g. when Inera publishes a new release).

No other existing properties need to change.

### 3.3 Factory: `SnomedServiceFactory`

- **Constructor:** Add `IneraSnomedService ineraService` and assign to a field.
- **getService(String server):** For `server` (trimmed, lowercased):
  - `"inera"` (and optionally `"terminologitjansten"` or `"inera-se"`) → return `ineraService`.
  - Keep existing `"snowstorm"` / `"ontoserver"` behaviour.
- No interface or API contract change.

---

## 4. Frontend Changes

- **State type:** Extend server type from `'snowstorm' | 'ontoserver'` to `'snowstorm' | 'ontoserver' | 'inera'` everywhere it is used (e.g. `useState`, `onChange`).
- **Dropdown:** Add one option, e.g.:
  - `value="inera"`, label e.g. `"Inera Terminologitjänsten (Swedish)"`.
- **Request payload:** Already sends `server: selectedServer`; no change needed.

No new pages or routes; only the existing “Match Terms” flow and server dropdown.

---

## 5. API Contract and Compatibility

- **POST /api/terms/match** already accepts any string in `server`. Adding `"inera"` is backward compatible.
- **Response shape** (matched/unmatched) is unchanged; Inera returns the same `TermMatch` structure (code, display, similarity, status).

---

## 6. Optional Enhancements

- **Alternative search terms:** As in Ontoserver, add a small map or method (e.g. "Astma" → "Asthma") and try both the original and the alternative term if the first call returns no or weak matches.
- **Versioning:** If Inera exposes multiple versions via different ValueSet URLs, keep `inera.valueset-url` configurable so new versions can be used without code changes.
- **Logging:** Log which server was used and, in debug, the request URL (without leaking sensitive data), to simplify support.

---

## 7. Testing Checklist

- Start backend with default config; confirm Inera URL is loaded and no startup errors.
- From frontend, select **Inera Terminologitjänsten** and run **Match Terms** with:
  - Swedish terms: e.g. "Diabetes", "Astma", "Fotledsfraktur".
  - English terms if fallback or synonyms are implemented.
- Verify response: matched terms have `snomedId`, `preferredTerm`, `fsn`, `similarity`; unmatched have reason.
- Verify **Snowstorm** and **Ontoserver** still work and that switching server gives correct results for the same terms where applicable.
- Optional: set `INERA_URL` / `INERA_VALUESET_URL` to invalid or empty and confirm graceful behaviour (e.g. all terms unmatched, no uncaught exception).

---

## 8. File List (Summary)

| Action   | File |
|----------|------|
| Add      | `backend/.../service/impl/IneraSnomedService.java` |
| Edit     | `backend/src/main/resources/application.yml` (add `inera`) |
| Edit     | `backend/.../service/SnomedServiceFactory.java` (wire Inera, handle `"inera"`) |
| Edit     | `frontend/src/ui/App.tsx` (server type + dropdown option) |

No other files need to be changed for a minimal, correct integration of Inera Terminologitjänsten as a selectable frontend option.
