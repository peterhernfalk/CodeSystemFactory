# Data Sources for SNOMED CT Term Matching (English & Swedish)

This document describes **existing data sources** that can be used in the first step of the application—when the user enters terms and presses **"Match Terms"**—to find SNOMED CT concepts. Terms may be in **English** or **Swedish**.

---

## 1. Summary Table

| Data source | Type | English | Swedish | Used in "Match Terms"? | API / access |
|-------------|------|---------|---------|------------------------|---------------|
| **Snowstorm** (snomedtools.org) | Remote API | Yes | Yes (with refset/fallback) | Yes (default) | Native REST: `/snowstorm/snomed-ct/{branch}/concepts?term=...` |
| **Ontoserver** (CSIRO) | Remote API | Yes | Limited | Yes (user option) | FHIR: `ValueSet/$expand?url=...&filter=...` |
| **In-app synonym rewrites** | Built-in logic | Yes | Yes | Yes (inside Snowstorm flow) | Hardcoded phrases → alternative search terms |
| **In-app synonym database** | In-memory map | Yes | Yes | No (recommendation step only) | `SynonymDatabaseServiceImpl` lookup |
| **sct_demo.csv** | Local file | Yes (keywords) | Yes (preferred/FSN) | No (service exists, not wired) | `LocalSnomedService` – CSV with `enKeywords` |
| **tx.fhir.org** | Remote API | Yes | Yes (Sweden edition) | No | FHIR: `ValueSet/$expand`, multiple SNOMED editions |

---

## 2. Data Sources Currently Used in "Match Terms"

### 2.1 Snowstorm (default server)

- **URL (current):** `https://snowstorm-training.snomedtools.org`  
  (FHIR base: `https://snowstorm-training.snomedtools.org/fhir`)
- **Alternative public base:** `https://snowstorm.snomedtools.org` (demo, non-production).
- **API used:** Snowstorm **native** REST, not FHIR, for term search:
  - `GET /snowstorm/snomed-ct/{branch}/concepts?term={term}&limit=50&activeFilter=true`
  - Optional: `languageRefset` for Swedish (if configured on the server).
  - `Accept-Language` header: e.g. `sv` then `en` (fallback).
- **Languages:** English (default) and Swedish when the server has Swedish content and/or refset; fallback to English is configured in the app (`snomed.fallback-to-english: true`).
- **Behaviour:** Returns concept list; the app picks the best match using **Jaro–Winkler similarity** (and optional boosts). Before calling the API, the app can **rewrite** some phrases via `getAlternativeSearchTerm()` (e.g. "MRI Heart" → "cardiac mri", Swedish "hjärtmri" → "cardiac mri").

**Test terms (Snowstorm native API):**

| User term | Result (observed) |
|-----------|--------------------|
| **MRI Heart** | Concepts returned; best match typically **Cardiac MRI** (e.g. 241620005 – Magnetic resonance imaging of heart (procedure)). Rewrite "MRI Heart" → "cardiac mri" improves search. |
| **Diabetes** | Many concepts (726+); app selects best by similarity (e.g. **Diabetes mellitus** 73211009 when similarity is highest). |
| **Astma** | Can match **Asthma** (195967001) if the server indexes Swedish terms or after English fallback (e.g. "asthma"). |
| **Fotledsfraktur** | May not match if the server has no Swedish descriptions; then **NO_MATCH**. Swedish term for “ankle fracture”; synonym DB used later in recommendations. |

---

### 2.2 Ontoserver (CSIRO) – user-selectable

- **URL:** `https://r4.ontoserver.csiro.au/fhir`
- **API:** FHIR R4 **ValueSet $expand** with SNOMED CT ValueSet URL and a text **filter**:
  - `GET /ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter={term}&count=50`
- **Languages:** Primarily **English** (display/filter in expansion). No dedicated Swedish terminology in the public instance.
- **Behaviour:** Returns expansion containing `code` and `display`; app chooses best match by Jaro–Winkler on the display string.

**Test terms (Ontoserver):**

| User term | Expected |
|-----------|----------|
| **MRI Heart** | Filter "MRI Heart" can return concepts containing that text (e.g. Cardiac MRI–related). |
| **Diabetes** | Filter "Diabetes" returns many concepts; best match chosen by similarity. |
| **Astma** | May return little or nothing (English filter); "Asthma" would match. |
| **Fotledsfraktur** | Unlikely (Swedish); "ankle fracture" would be needed for English. |

---

### 2.3 In-app synonym rewrites (inside Snowstorm flow)

- **Where:** `FhirSnomedService.getAlternativeSearchTerm()`
- **Purpose:** Before calling Snowstorm, user input is rewritten for known phrases so that the **remote** Snowstorm API gets a term it can match.
- **Coverage:** Examples: "MRI Heart" / "heart mri" → "cardiac mri"; "heart attack" → "myocardial infarction"; Swedish "hjärtinfarkt"/"hjärtattack" → "myocardial infarction"; "hjärtmri"/"hjärt-mri" → "cardiac mri".
- **Languages:** Both English and Swedish (for the phrases that are hardcoded).

This is not a separate “data source” but **extends** Snowstorm’s effective vocabulary for the Match Terms step.

---

## 3. Data Sources Not Used in "Match Terms" (but available)

### 3.1 In-app synonym database (`SynonymDatabaseServiceImpl`)

- **Used in:** AI/hybrid **recommendation** step for **unmatched** terms, not in the initial Match Terms request.
- **Content:** In-memory map of Swedish/English terms → SNOMED concept IDs, e.g.:
  - diabetes, sockersjuka → 73211009 (Diabetes mellitus)
  - astma, asthma → 195967001 (Asthma)
  - fotledsfraktur, ankelfraktur → 125605004 (Fracture of bone / ankle)
  - MRI heart, cardiac mri, hjärtmri, kardiell mri → 433390000 (Cardiac MRI)
- **Could be used in Match Terms:** Yes—e.g. check synonym DB first; if hit, return that concept and optionally skip or supplement remote call.

---

### 3.2 Local CSV (`sct_demo.csv`) and `LocalSnomedService`

- **File:** `backend/src/main/resources/sct_demo.csv`  
  Columns: `sctId`, `svPreferred`, `svFsn`, `enKeywords`.
- **Example rows:**
  - 363679005 – MRT hjärta (åtgärd) – keywords: mri heart; cardiac mri; heart mri
  - 80146002 – Diabetes mellitus (tillstånd) – keywords: diabetes; dm
  - 22298006 – Myokardinfarkt (tillstånd) – keywords: myocardial infarction; heart attack; ami
- **Service:** `LocalSnomedService` implements `SnomedService` and matches by keyword similarity (threshold 0.75) but is **not** wired into the Match Terms flow (not in `SnomedServiceFactory`).
- **Languages:** Swedish (preferred/FSN) and English (keywords).
- **Could be used in Match Terms:** Yes—e.g. add as a first-tier source (local CSV) before or in parallel with Snowstorm/Ontoserver.

---

### 3.3 tx.fhir.org (HL7 FHIR terminology server)

- **URL:** `https://tx.fhir.org/r4/` (and SNOMED-specific pages, e.g. `https://tx.fhir.org/snomed/`).
- **Content:** Multiple SNOMED CT editions, including **Sweden** and International; many ValueSets and CodeSystems.
- **API:** Standard FHIR terminology (e.g. **ValueSet $expand** with `filter`). Same pattern as Ontoserver: expand a SNOMED ValueSet with a text filter.
- **Languages:** English (International) and Swedish (Sweden edition).
- **Used in app:** No.
- **Could be used in Match Terms:** Yes—as another remote FHIR source (e.g. for Swedish-specific matching using Sweden edition).

---

## 4. How the Four Test Terms Are Matched (by source)

| Term | Snowstorm | Ontoserver | Synonym DB (recommendation) | sct_demo.csv |
|------|-----------|------------|----------------------------|--------------|
| **MRI Heart** | Yes – rewrite to "cardiac mri"; concept e.g. 241620005 Cardiac MRI | Yes – filter "MRI Heart" | Yes – "mri heart" → 433390000 | Yes – "mri heart" in keywords → 363679005 MRT hjärta |
| **Diabetes** | Yes – many hits; best match e.g. Diabetes mellitus 73211009 | Yes – filter "Diabetes" | Yes – diabetes → 73211009 | Yes – "diabetes" in keywords → 80146002 Diabetes mellitus |
| **Astma** | Yes – if Swedish index or fallback "asthma" → 195967001 | Weak – "Asthma" works, "Astma" may not | Yes – astma → 195967001 | No – not in CSV |
| **Fotledsfraktur** | Often no – Swedish term, server may have no Swedish | No – Swedish | Yes – fotledsfraktur → 125605004 | No – not in CSV |

So:

- **MRI Heart** and **Diabetes** are well covered by **Snowstorm**, **Ontoserver**, **synonym DB**, and (for the first two) **sct_demo.csv**.
- **Astma** is matched by **Snowstorm** (with fallback) and by the **synonym DB**; **Ontoserver** works with "Asthma".
- **Fotledsfraktur** is only matched in the current design by the **synonym DB** (in the recommendation step); adding the synonym DB or a Swedish-capable source (e.g. tx.fhir.org Sweden edition) to the Match Terms step would allow it to match there too.

---

## 5. Recommendations

1. **Keep Snowstorm as default** for Match Terms (best support for both English and Swedish with fallback and synonym rewrites).
2. **Use Ontoserver** for English-focused or FHIR-only environments.
3. **Consider using the synonym database in Match Terms** (e.g. first lookup; if hit, return that concept and optionally still call Snowstorm for FSN/display).
4. **Optionally wire `LocalSnomedService`** (sct_demo.csv) into the factory as a “local first” source for the demo set (MRI heart, Diabetes, etc.).
5. **Consider adding tx.fhir.org** as an optional server, especially for **Swedish** via the Sweden SNOMED edition (ValueSet $expand with filter).

---

## 6. References

- Snowstorm API: e.g. [GitHub – Using the API](https://github.com/IHTSDO/snowstorm/blob/master/docs/using-the-api.md).
- SNOMED International terminology services: [implementation.snomed.org/terminology-services](https://implementation.snomed.org/terminology-services).
- Ontoserver: [ontoserver.csiro.au](https://ontoserver.csiro.au/).
- tx.fhir.org: [tx.fhir.org/r4](https://tx.fhir.org/r4/), [tx.fhir.org/snomed](https://tx.fhir.org/snomed/).
- In-app: `FhirSnomedService`, `OntoserverSnomedService`, `SnomedServiceFactory`, `SynonymDatabaseServiceImpl`, `LocalSnomedService`, `application.yml` (`fhir.server.url`, `ontoserver.url`, `snomed.language`).
