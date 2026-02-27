# API Requests Sent When Matching Terms to SNOMED CT

This document describes the **exact API requests** (HTTP method, URL, headers, and when they are used) that the backend sends when the user clicks **"Match Terms"** and terms are matched against SNOMED CT sources. The backend uses one of two implementations depending on the user’s server choice: **Snowstorm** (default) or **Ontoserver**.

---

## 1. Overview

- **Endpoint used by frontend:** `POST /api/terms/match` with body `{ "terms": ["term1", "term2", ...], "server": "snowstorm" | "ontoserver" }`.
- **Backend:** For each term, the chosen `SnomedService` (Snowstorm or Ontoserver) performs one or more **outbound** HTTP requests to the terminology server. No follow-up request by concept ID is made; matching uses only the search/expand response.

---

## 2. Snowstorm (Default Server)

**Base URL:** From `fhir.server.url` (e.g. `https://snowstorm-training.snomedtools.org/fhir`). The code uses the **base without** `/fhir`, e.g. `https://snowstorm-training.snomedtools.org`.

**API type:** Snowstorm **native** REST (not FHIR).

---

### 2.1 Primary search (per term)

Sent for each term until a response with at least one concept is returned. The backend tries combinations of **language** (e.g. `sv` then `en`) and **search string** (original term, then lowercase if different).

**Request:**

| Property   | Value |
|-----------|--------|
| **Method** | `GET` |
| **URL**    | `{snowstormBaseUrl}/snowstorm/snomed-ct/{branch}/concepts?term={term}&limit=50&activeFilter=true` |
| **Optional query** | `&languageRefset={languageRefset}` if `snomed.language-refset` is set and the current try is in the preferred language |

**Path/query parameters:**

| Parameter       | Source | Example |
|----------------|--------|---------|
| `snowstormBaseUrl` | `fhir.server.url` with `/fhir` stripped | `https://snowstorm-training.snomedtools.org` |
| `branch`       | `snomed.branch` | `MAIN` |
| `term`         | User term or lowercase variant | `Diabetes`, `MRI Heart`, `cardiac mri` (if rewritten) |
| `limit`        | Fixed | `50` |
| `activeFilter`  | Fixed | `true` |
| `languageRefset`| `snomed.language-refset` (optional) | (e.g. Swedish refset ID if configured) |

**Headers:**

| Header | Value |
|--------|--------|
| `Accept` | `application/json` |
| `Accept-Language` | Current try language: `sv` or `en` (from `snomed.language` and fallback) |

**Example:**

```http
GET https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=Diabetes&limit=50&activeFilter=true
Accept: application/json
Accept-Language: sv
```

(Further tries for the same term would use e.g. `Accept-Language: en` or `term=diabetes`.)

**Response used:** JSON with `items`: array of concepts. Each item has e.g. `conceptId`, `fsn`, `pt`, `descriptions`. The backend picks the best match from this list (no extra request per concept).

---

### 2.2 Alternative-term search (Snowstorm only)

If the **primary** search returns **zero** concepts and the backend has an alternative phrase for the user input (e.g. "MRI Heart" → "cardiac mri"), it sends **one more** GET with that alternative term.

**Request:**

| Property | Value |
|----------|--------|
| **Method** | `GET` |
| **URL**    | `{snowstormBaseUrl}/snowstorm/snomed-ct/{branch}/concepts?term={altTerm}&limit=50&activeFilter=true` |
| **Optional query** | `&languageRefset={languageRefset}` if set |

**Headers:** Same as primary: `Accept: application/json`, `Accept-Language: {language}`.

**Example:**

```http
GET https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=cardiac%20mri&limit=50&activeFilter=true
Accept: application/json
Accept-Language: sv
```

Alternative terms come from `FhirSnomedService.getAlternativeSearchTerm()` (e.g. "MRI Heart"/"heart mri" → "cardiac mri", "heart attack" → "myocardial infarction", Swedish "hjärtmri" → "cardiac mri").

---

### 2.3 Word-by-word search (Snowstorm only)

If the primary (and optional alternative-term) search still returns **zero** concepts and the user term contains **multiple words**, the backend does one GET **per word** (words with length &gt; 2).

**Request (per word):**

| Property | Value |
|----------|--------|
| **Method** | `GET` |
| **URL**    | `{snowstormBaseUrl}/snowstorm/snomed-ct/{branch}/concepts?term={word}&limit=50&activeFilter=true` |
| **Optional query** | `&languageRefset={languageRefset}` if set |

**Headers:** Same as primary.

**Example** (term `"MRI Heart"` split into words; "mri" and "heart" each &gt; 2):

```http
GET https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=mri&limit=50&activeFilter=true
Accept: application/json
Accept-Language: sv
```

```http
GET https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=heart&limit=50&activeFilter=true
Accept: application/json
Accept-Language: sv
```

Results from all word requests are merged; the best match is chosen from the combined list.

---

### 2.4 Order of Snowstorm requests (per term)

1. For each language (e.g. `sv`, then `en`):
   - For each search string (original term, then lowercase if different):
     - **1 GET** → primary search.
     - If `items` is non-empty → **stop**, use this response.
2. If all primary GETs returned empty and there is an **alternative term**:
   - **1 GET** → alternative-term search.
3. If still empty and term has **multiple words**:
   - **1 GET per word** (length &gt; 2) → word-by-word search; merge results.

So for one user term you get **at least 1** and potentially **several** GETs to Snowstorm (no POST, no request body).

---

## 3. Ontoserver (User-Selectable Server)

**Base URL:** From `ontoserver.url` (e.g. `https://r4.ontoserver.csiro.au/fhir`).

**API type:** FHIR R4 **ValueSet $expand**.

---

### 3.1 ValueSet $expand (per term)

The backend tries (language × term) until the expand response contains at least one concept. For each try it sends a single GET.

**Request:**

| Property | Value |
|----------|--------|
| **Method** | `GET` |
| **URL**    | `{ontoserverUrl}/ValueSet/$expand?url={SNOMED_VALUESET_URL}&filter={term}&count=50` |

**Query parameters:**

| Parameter | Value |
|-----------|--------|
| `url`     | `http://snomed.info/sct/900000000000207008?fhir_vs` (SNOMED CT ValueSet for all active concepts) |
| `filter`  | User term or lowercase variant (URL-encoded) |
| `count`   | `50` |

**Headers:**

| Header | Value |
|--------|--------|
| `Accept` | `application/fhir+json` |
| `Accept-Language` | Current try language: e.g. `sv` or `en` |

**Example:**

```http
GET https://r4.ontoserver.csiro.au/fhir/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=50
Accept: application/fhir+json
Accept-Language: sv
```

**Response used:** FHIR ValueSet with `expansion.contains`: array of `{ "code": "...", "display": "..." }`. The backend selects the best match by similarity on `display`; no second request is made.

---

### 3.2 Order of Ontoserver requests (per term)

- For each language (e.g. `sv`, then `en`):
  - For each search string (original term, then lowercase if different):
    - **1 GET** → ValueSet `$expand` with that `filter` and `Accept-Language`.
    - If `expansion.contains` is non-empty → **stop**, use this response.

There is **no** alternative-term or word-by-word step for Ontoserver. So for one user term you get **1 to a few** GETs (one per try until success).

---

## 4. Summary Table

| Source     | Request type        | Method | Path/operation        | Main query/body          | When sent |
|-----------|----------------------|--------|------------------------|--------------------------|-----------|
| Snowstorm | Concept search       | GET    | `/snowstorm/snomed-ct/{branch}/concepts` | `term`, `limit=50`, `activeFilter=true`, optional `languageRefset` | Primary and (if empty) alternative term, then (if still empty) per-word |
| Ontoserver| ValueSet expand      | GET    | `/ValueSet/$expand`    | `url=...fhir_vs`, `filter`, `count=50` | One per (language, term) until concepts found |

---

## 5. Configuration that affects the requests

| Config key              | Used by    | Effect on API request |
|-------------------------|------------|------------------------|
| `fhir.server.url`       | Snowstorm  | Base URL (without `/fhir`) for all Snowstorm GETs |
| `ontoserver.url`        | Ontoserver| Base URL for ValueSet `$expand` |
| `snomed.branch`         | Snowstorm  | Path segment, e.g. `MAIN` |
| `snomed.language`       | Both       | First `Accept-Language` tried (e.g. `sv`) |
| `snomed.fallback-to-english` | Both  | If true, also try `Accept-Language: en` |
| `snomed.language-refset`| Snowstorm  | Optional `languageRefset` query param |

---

## 6. References in code

- **Snowstorm:** `FhirSnomedService` – `searchByTerm()`, URL built with `UriComponentsBuilder.fromHttpUrl(snowstormBaseUrl).path("/snowstorm/snomed-ct").pathSegment(branch, "concepts").queryParam("term", ...)`.
- **Ontoserver:** `OntoserverSnomedService` – `searchViaValueSetExpand()`, URL built with `.path("/ValueSet/$expand").queryParam("url", SNOMED_VALUESET_URL).queryParam("filter", term).queryParam("count", "50")`.
