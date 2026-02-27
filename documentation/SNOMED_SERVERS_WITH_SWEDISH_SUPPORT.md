# SNOMED CT Terminology Servers with Swedish Edition Support

This document lists **existing public SNOMED CT terminology servers** that provide API access and contain **both International and Swedish editions** of SNOMED CT. These can be used as alternatives to the current Snowstorm/Ontoserver setup.

---

## Summary Table

| Server | Base URL | API Type | International Edition | Swedish Edition | API Method | Status |
|--------|----------|----------|----------------------|----------------|------------|--------|
| **tx.fhir.org** | `https://tx.fhir.org/r4/` | FHIR ValueSet $expand | ✅ Yes (multiple versions) | ✅ Yes (20231130, 20220531) | `GET /ValueSet/$expand` | ✅ **Recommended** |
| **tx.hl7europe.eu** | `https://tx.hl7europe.eu/r4/` | FHIR ValueSet $expand | ✅ Yes (20240801) | ❌ No | `GET /ValueSet/$expand` | ⚠️ International only |
| **snowstorm.snomedtools.org** | `https://snowstorm.snomedtools.org` | Native + FHIR | ✅ Yes | ❓ Unclear | Native REST + FHIR | ⚠️ Swedish support unclear |
| **snowstorm-training.snomedtools.org** | `https://snowstorm-training.snomedtools.org` | Native + FHIR | ✅ Yes | ❓ Unclear | Native REST + FHIR | ⚠️ Currently used, Swedish unclear |
| **r4.ontoserver.csiro.au** | `https://r4.ontoserver.csiro.au/fhir` | FHIR ValueSet $expand | ✅ Yes | ❌ No | `GET /ValueSet/$expand` | ⚠️ Currently used, International only |

---

## 1. tx.fhir.org (HL7 FHIR Terminology Server) ⭐ **RECOMMENDED**

### Overview
Public FHIR terminology server that hosts **multiple SNOMED CT editions**, including **Sweden**. This is the **best option** for accessing both International and Swedish SNOMED CT via API.

### Swedish Edition Support
- **Sweden Edition 20231130** (most recent): `http://snomed.info/sct/45991000052106/version/20231130`
- **Sweden Edition 20220531** (older): `http://snomed.info/sct/45991000052106/version/20220531`

### International Edition Support
- Multiple International versions available
- Standard ValueSet URL: `http://snomed.info/sct/900000000000207008?fhir_vs`

### API Access

**Base URL:** `https://tx.fhir.org/r4/`

**For International Edition:**
```http
GET https://tx.fhir.org/r4/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter={term}&count=50
Accept: application/fhir+json
Accept-Language: en
```

**For Swedish Edition:**
```http
GET https://tx.fhir.org/r4/ValueSet/$expand?url=http://snomed.info/sct/45991000052106/version/20231130?fhir_vs&filter={term}&count=50
Accept: application/fhir+json
Accept-Language: sv
```

### Implementation Notes
- Uses **FHIR ValueSet $expand** (same pattern as Ontoserver)
- Can specify Swedish edition via the `url` parameter
- Supports `Accept-Language` header for preferred language
- Returns FHIR ValueSet with `expansion.contains[]` array

### How to Use in This App
1. Add new service implementation similar to `OntoserverSnomedService`
2. Use Swedish edition URL: `http://snomed.info/sct/45991000052106/version/20231130?fhir_vs`
3. Set `Accept-Language: sv` for Swedish terms
4. Fallback to International edition if Swedish edition doesn't have the term

### Example Request (Swedish Edition)
```bash
curl "https://tx.fhir.org/r4/ValueSet/\$expand?url=http://snomed.info/sct/45991000052106/version/20231130?fhir_vs&filter=Diabetes&count=5" \
  -H "Accept: application/fhir+json" \
  -H "Accept-Language: sv"
```

### Pros
- ✅ **Explicit Swedish edition support** (confirmed)
- ✅ Public and free
- ✅ Standard FHIR API (same as Ontoserver)
- ✅ Multiple Swedish versions available
- ✅ Can switch between International and Swedish editions

### Cons
- ⚠️ May have rate limits (check terms of use)
- ⚠️ Requires specifying edition in URL (not automatic)

---

## 2. tx.hl7europe.eu (HL7 Europe Terminology Service)

### Overview
FHIR terminology server maintained by HL7 Europe. Supports SNOMED CT International Edition but **does not currently host Swedish edition**.

### International Edition Support
- **International Edition 20240801**: `http://snomed.info/sct/900000000000207008/version/20240801`
- Standard ValueSet URL: `http://snomed.info/sct/900000000000207008?fhir_vs`

### API Access

**Base URL:** `https://tx.hl7europe.eu/r4/`

**Request:**
```http
GET https://tx.hl7europe.eu/r4/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter={term}&count=50
Accept: application/fhir+json
Accept-Language: en
```

### Status
- ✅ Works for International edition (tested)
- ❌ **No Swedish edition** (confirmed via search)
- ✅ Very recent International version (20240801)

### Pros
- ✅ Very recent International edition
- ✅ Public and free
- ✅ Standard FHIR API

### Cons
- ❌ **No Swedish edition support**

---

## 3. snowstorm.snomedtools.org (SNOMED International Public Instance)

### Overview
Public demonstration instance of Snowstorm maintained by SNOMED International. Provides both native REST API and FHIR API.

### API Access

**Base URLs:**
- Native API: `https://snowstorm.snomedtools.org/snowstorm/snomed-ct`
- FHIR API: `https://snowstorm.snomedtools.org/fhir`

**Native API Request:**
```http
GET https://snowstorm.snomedtools.org/snowstorm/snomed-ct/{branch}/concepts?term={term}&limit=50&activeFilter=true
Accept: application/json
Accept-Language: sv
```

**FHIR API Request:**
```http
GET https://snowstorm.snomedtools.org/fhir/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter={term}&count=50
Accept: application/fhir+json
Accept-Language: sv
```

### Swedish Edition Support
- ❓ **Unclear** - Documentation mentions multi-lingual support but doesn't explicitly confirm Swedish edition content
- May support Swedish if Swedish refsets/extensions are loaded
- Would need testing to confirm

### Pros
- ✅ Both native and FHIR APIs available
- ✅ Same API pattern as current `snowstorm-training` instance
- ✅ Multi-lingual support mentioned

### Cons
- ❓ Swedish edition support **not confirmed**
- ⚠️ Explicitly **not for production use** (demonstration only)
- ⚠️ May have rate limits/IP blocking

---

## 4. snowstorm-training.snomedtools.org (Currently Used)

### Overview
Training/testing instance of Snowstorm. Currently configured in the app as the default server.

### API Access
Same as `snowstorm.snomedtools.org` (native REST API).

### Swedish Edition Support
- ❓ **Unclear** - App is configured with `snomed.language: sv` and `fallback-to-english: true`
- May work for Swedish terms if Swedish content is loaded
- Falls back to English if Swedish not available

### Status
- ✅ Currently working in the app
- ❓ Swedish support depends on server content (not confirmed)

---

## 5. r4.ontoserver.csiro.au (Currently Used)

### Overview
Ontoserver FHIR terminology server maintained by CSIRO (Australia). Currently available as a user-selectable option.

### API Access

**Base URL:** `https://r4.ontoserver.csiro.au/fhir`

**Request:**
```http
GET https://r4.ontoserver.csiro.au/fhir/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter={term}&count=50
Accept: application/fhir+json
Accept-Language: sv
```

### Swedish Edition Support
- ❌ **No** - Only International edition
- `Accept-Language: sv` may return English terms with Swedish metadata if available, but no dedicated Swedish edition

### Status
- ✅ Currently working in the app
- ❌ **No Swedish edition** (International only)

---

## Recommendations

### Best Option: tx.fhir.org ⭐

**Why:**
1. ✅ **Confirmed Swedish edition support** (versions 20231130 and 20220531)
2. ✅ Standard FHIR API (same pattern as Ontoserver)
3. ✅ Can access both International and Swedish editions
4. ✅ Public and free
5. ✅ Easy to implement (similar to existing `OntoserverSnomedService`)

**Implementation Strategy:**
1. Create a new service `TxFhirSnomedService` similar to `OntoserverSnomedService`
2. Add configuration option to choose edition (International vs Swedish)
3. Use Swedish edition URL for Swedish terms: `http://snomed.info/sct/45991000052106/version/20231130?fhir_vs`
4. Fallback to International edition if needed
5. Add to `SnomedServiceFactory` as a new option

### Alternative: Keep Current Setup + Add tx.fhir.org

- Keep Snowstorm as default (works well for International)
- Keep Ontoserver as option (works well for International)
- **Add tx.fhir.org as a new option** specifically for Swedish edition support
- User can choose "tx.fhir.org (Swedish)" when matching Swedish terms

---

## Implementation Example

### Configuration (`application.yml`)
```yaml
# tx.fhir.org configuration (FHIR R4 terminology server with Swedish edition)
tx-fhir:
  url: ${TX_FHIR_URL:https://tx.fhir.org/r4}
  # Swedish edition URL
  swedish-edition-url: http://snomed.info/sct/45991000052106/version/20231130?fhir_vs
  # International edition URL
  international-edition-url: http://snomed.info/sct/900000000000207008?fhir_vs
```

### Service Implementation
Similar to `OntoserverSnomedService` but:
- Use `tx-fhir.url` as base URL
- For Swedish terms, use `swedish-edition-url` in ValueSet $expand
- For English terms or fallback, use `international-edition-url`
- Parse FHIR ValueSet expansion response (same as Ontoserver)

---

## Testing the Four Terms

| Term | tx.fhir.org (Swedish) | tx.fhir.org (International) | Current Snowstorm | Current Ontoserver |
|------|----------------------|----------------------------|-------------------|-------------------|
| **MRI Heart** | ✅ Should match | ✅ Should match | ✅ Works | ✅ Works |
| **Diabetes** | ✅ Should match | ✅ Should match | ✅ Works | ✅ Works |
| **Astma** | ✅ Should match (Swedish) | ⚠️ May need "Asthma" | ✅ Works (fallback) | ⚠️ Needs "Asthma" |
| **Fotledsfraktur** | ✅ Should match (Swedish) | ❌ Unlikely | ❌ Unlikely | ❌ Unlikely |

**Key advantage:** tx.fhir.org with Swedish edition should match **"Fotledsfraktur"** directly, which current servers cannot.

---

## References

- tx.fhir.org SNOMED editions: https://tx.fhir.org/snomed/
- tx.hl7europe.eu: https://tx.hl7europe.eu/r4/
- SNOMED International terminology services: https://implementation.snomed.org/terminology-services
- Snowstorm documentation: https://github.com/IHTSDO/snowstorm
- FHIR ValueSet $expand operation: https://hl7.org/fhir/valueset-operation-expand.html
