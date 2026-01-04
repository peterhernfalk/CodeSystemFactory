# Ontoserver FHIR API Requests

This document shows how to adapt the Snowstorm requests to work with Ontoserver, which is a pure FHIR R4 terminology server.

## Key Differences

### Snowstorm (Current Implementation)
- Uses **native API**: `/snowstorm/snomed-ct/{branch}/concepts`
- Non-standard endpoints specific to Snowstorm
- Direct concept search by term

### Ontoserver
- Uses **FHIR R4 standard API**: `/fhir/CodeSystem` or `/fhir/$lookup`
- Standard FHIR terminology operations
- FHIR-compliant endpoints

## Ontoserver Base URL

```
https://r4.ontoserver.csiro.au/fhir
```

## FHIR Request Methods for Concept Search

### ⚠️ Important: CodeSystem Search Limitation

**CodeSystem search does NOT support term-based concept search** in Ontoserver. The error you received:
```json
{
  "severity": "error",
  "code": "not-supported",
  "diagnostics": "Invalid request: The FHIR endpoint on this server does not know how to handle GET operation[CodeSystem] with parameters [[_count, display:contains, url]]"
}
```

This means `CodeSystem?display:contains={term}` is **not supported**. Use **ValueSet $expand** instead (see Method 1 below).

---

### Method 1: ValueSet $expand (✅ RECOMMENDED - Works!)

Search for concepts by term using ValueSet expansion with filter.

#### Request Format

```http
GET /fhir/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter={term} HTTP/1.1
Host: r4.ontoserver.csiro.au
Accept: application/fhir+json
Accept-Language: sv
```

#### Complete Example

**For term "Diabetes"**:
```http
GET /fhir/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=50 HTTP/1.1
Host: r4.ontoserver.csiro.au
Accept: application/fhir+json
Accept-Language: sv
```

**Full URL**:
```
https://r4.ontoserver.csiro.au/fhir/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=50
```

#### cURL Command

```bash
curl -X GET \
  "https://r4.ontoserver.csiro.au/fhir/ValueSet/\$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=50" \
  -H "Accept: application/fhir+json" \
  -H "Accept-Language: sv"
```

#### Query Parameters

- `url=http://snomed.info/sct/900000000000207008?fhir_vs` - SNOMED CT ValueSet URL (all active concepts)
- `filter={term}` - Filter concepts by display name containing the term
- `count={number}` - Limit number of results (optional, default: 10)

#### Response Format

```json
{
  "resourceType": "ValueSet",
  "expansion": {
    "total": 1102,
    "contains": [
      {
        "system": "http://snomed.info/sct",
        "code": "399144008",
        "display": "Bronze diabetes"
      },
      {
        "system": "http://snomed.info/sct",
        "code": "702706001",
        "display": "Diabetes clinic"
      },
      {
        "system": "http://snomed.info/sct",
        "code": "309417009",
        "display": "Diabetes dietitian"
      }
    ]
  }
}
```

#### Extracting Concepts

From the response, extract concepts from `expansion.contains[]`:
- **Code**: `expansion.contains[].code`
- **Display**: `expansion.contains[].display`
- **System**: `expansion.contains[].system`

---

### Method 2: $lookup Operation

Look up a concept by code or display name.

#### Request Format

```http
GET /fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code={code} HTTP/1.1
Host: r4.ontoserver.csiro.au
Accept: application/fhir+json
```

**Note**: `$lookup` requires a code, not a term. For term search, use Method 1.

#### Example: Lookup by Code

```bash
curl -X GET \
  "https://r4.ontoserver.csiro.au/fhir/CodeSystem/\$lookup?system=http://snomed.info/sct&code=73211009" \
  -H "Accept: application/fhir+json"
```

#### Response Format

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "name",
      "valueString": "SNOMED CT"
    },
    {
      "name": "display",
      "valueString": "Diabetes mellitus"
    },
    {
      "name": "property",
      "part": [
        {
          "name": "code",
          "valueCode": "parent"
        },
        {
          "name": "value",
          "valueCode": "64572001"
        }
      ]
    }
  ]
}
```

---

### Method 3: CodeSystem Search (Limited - Not for Concept Search)

CodeSystem search in Ontoserver is for finding **CodeSystem resources**, not for searching concepts within them.

#### Supported Parameters

From Ontoserver metadata, CodeSystem search supports:
- `url` - Code system URL
- `name` - Code system name
- `description` - Code system description
- `status` - Code system status
- `_count`, `_sort` - Standard FHIR parameters

**Does NOT support**: `display:contains` or any concept-level search parameters.

#### Use Case

Only use CodeSystem search to find CodeSystem resources themselves:
```bash
curl "https://r4.ontoserver.csiro.au/fhir/CodeSystem?url=http://snomed.info/sct" \
  -H "Accept: application/fhir+json"
```

This returns the CodeSystem resource, not concepts.

---

## Comparison: Snowstorm vs Ontoserver

### Snowstorm (Current)

```bash
# Native API
curl -X GET \
  "https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=Diabetes&limit=50&activeFilter=true" \
  -H "Accept: application/json" \
  -H "Accept-Language: sv"
```

### Ontoserver (FHIR) ✅ CORRECT

```bash
# FHIR ValueSet $expand with filter
curl -X GET \
  "https://r4.ontoserver.csiro.au/fhir/ValueSet/\$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=50" \
  -H "Accept: application/fhir+json" \
  -H "Accept-Language: sv"
```

**Note**: CodeSystem search with `display:contains` does NOT work (returns error).

## Key Differences

| Aspect | Snowstorm | Ontoserver |
|--------|-----------|------------|
| **API Type** | Native (proprietary) | FHIR R4 (standard) |
| **Base Path** | `/snowstorm/snomed-ct/{branch}/concepts` | `/fhir/ValueSet/$expand` |
| **Search Parameter** | `term` | `filter` (in ValueSet $expand) |
| **ValueSet URL** | Not used | `url=http://snomed.info/sct/900000000000207008?fhir_vs` |
| **Response Format** | Custom JSON | FHIR ValueSet with expansion |
| **Accept Header** | `application/json` | `application/fhir+json` |
| **Language Support** | `Accept-Language` header | `Accept-Language` header |
| **Result Location** | `items[]` | `expansion.contains[]` |

## Complete cURL Examples

### Example 1: Search for "Diabetes" ✅ WORKS

```bash
curl -X GET \
  "https://r4.ontoserver.csiro.au/fhir/ValueSet/\$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=50" \
  -H "Accept: application/fhir+json" \
  -H "Accept-Language: sv" \
  | jq '.expansion.contains[0:5] | .[] | {code: .code, display: .display}'
```

**Expected Output**:
```json
{
  "code": "399144008",
  "display": "Bronze diabetes"
}
{
  "code": "702706001",
  "display": "Diabetes clinic"
}
...
```

### Example 2: Search for "Fotledsfraktur" (Swedish)

```bash
curl -X GET \
  "https://r4.ontoserver.csiro.au/fhir/ValueSet/\$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Fotledsfraktur&count=50" \
  -H "Accept: application/fhir+json" \
  -H "Accept-Language: sv" \
  | jq '.expansion.contains[0:5] | .[] | {code: .code, display: .display}'
```

### Example 3: Lookup Specific Concept by Code

```bash
curl -X GET \
  "https://r4.ontoserver.csiro.au/fhir/CodeSystem/\$lookup?system=http://snomed.info/sct&code=73211009" \
  -H "Accept: application/fhir+json" \
  | jq '.'
```

### Example 4: Search with Multiple Parameters

```bash
curl -X GET \
  "https://r4.ontoserver.csiro.au/fhir/ValueSet/\$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=10&offset=0" \
  -H "Accept: application/fhir+json" \
  -H "Accept-Language: sv,en" \
  | jq '.expansion.contains | length'
```

## FHIR Search Parameters

### ValueSet $expand Parameters

- `url` - ValueSet URL (for SNOMED: `http://snomed.info/sct/900000000000207008?fhir_vs`)
- `filter` - Filter concepts by display name containing the term
- `count` - Limit number of results (default: 10)
- `offset` - Pagination offset
- `includeDesignations` - Include additional designations (optional)

### Example with Multiple Parameters

```
GET /fhir/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=50&offset=0
```

### SNOMED CT ValueSet URL

The ValueSet URL `http://snomed.info/sct/900000000000207008?fhir_vs` represents:
- `900000000000207008` - SNOMED CT module identifier
- `?fhir_vs` - FHIR ValueSet expansion parameter
- This ValueSet contains all active SNOMED CT concepts

## Response Parsing

### FHIR ValueSet Expansion Response Structure

```json
{
  "resourceType": "ValueSet",
  "expansion": {
    "total": 1102,
    "contains": [
      {
        "system": "http://snomed.info/sct",
        "code": "399144008",
        "display": "Bronze diabetes"
      },
      {
        "system": "http://snomed.info/sct",
        "code": "702706001",
        "display": "Diabetes clinic"
      }
    ]
  }
}
```

### Extracting Concept Information

From the FHIR ValueSet expansion response, extract:
- **Code**: `expansion.contains[].code`
- **Display**: `expansion.contains[].display`
- **System**: `expansion.contains[].system` (should be `http://snomed.info/sct`)
- **Total**: `expansion.total` (total number of matching concepts)

## Adapting the Code for Ontoserver

### Current Code (Snowstorm Native API)

```java
UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(snowstormBaseUrl)
    .path("/snowstorm/snomed-ct")
    .pathSegment(branch, "concepts")
    .queryParam("term", tryTerm)
    .queryParam("limit", "50")
    .queryParam("activeFilter", "true");
```

### Adapted Code (Ontoserver FHIR API) ✅ CORRECT

```java
UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(fhirServerUrl)
    .path("/ValueSet/$expand")
    .queryParam("url", "http://snomed.info/sct/900000000000207008?fhir_vs")
    .queryParam("filter", tryTerm)
    .queryParam("count", "50");
```

### Complete Service Method Example

```java
private TermMatch searchByTermFhir(String term) {
    try {
        // Build FHIR ValueSet $expand URL
        UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(fhirServerUrl)
            .path("/ValueSet/$expand")
            .queryParam("url", "http://snomed.info/sct/900000000000207008?fhir_vs")
            .queryParam("filter", term)
            .queryParam("count", "50");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.parseMediaType("application/fhir+json")));
        headers.setAcceptLanguage(java.util.Locale.LanguageRange.parse(language));
        HttpEntity<Void> req = new HttpEntity<>(headers);
        
        ResponseEntity<Map<String, Object>> searchResp = rest.exchange(
            urlBuilder.toUriString(), 
            HttpMethod.GET, 
            req,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        if (searchResp.getStatusCode().is2xxSuccessful() && searchResp.getBody() != null) {
            Map<String, Object> valueSet = searchResp.getBody();
            
            // Parse FHIR ValueSet expansion response
            Object expansion = valueSet.get("expansion");
            if (expansion instanceof Map) {
                Object contains = ((Map<?, ?>) expansion).get("contains");
                if (contains instanceof List) {
                    // Extract concepts from expansion.contains[]
                    for (Object item : (List<?>) contains) {
                        if (item instanceof Map) {
                            String code = (String) ((Map<?, ?>) item).get("code");
                            String display = (String) ((Map<?, ?>) item).get("display");
                            // Calculate similarity and create TermMatch
                            // ... parse and convert to TermMatch
                        }
                    }
                }
            }
        }
    } catch (Exception e) {
        // Handle error
    }
    return null;
}
```

## Testing Ontoserver

### Test 1: Basic Search ✅ WORKS

```bash
curl "https://r4.ontoserver.csiro.au/fhir/ValueSet/\$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=5" \
  -H "Accept: application/fhir+json" \
  | jq '.expansion.contains[0:3] | .[] | {code: .code, display: .display}'
```

**Expected**: Returns concepts with "Diabetes" in display name.

### Test 2: Check Server Capabilities

```bash
curl "https://r4.ontoserver.csiro.au/fhir/metadata" \
  -H "Accept: application/fhir+json" \
  | jq '.rest[0].resource[] | select(.type == "ValueSet") | .interaction[].code'
```

### Test 3: List Available Code Systems

```bash
curl "https://r4.ontoserver.csiro.au/fhir/CodeSystem" \
  -H "Accept: application/fhir+json" \
  | jq '.entry[].resource.url'
```

### Test 4: Verify ValueSet $expand Works

```bash
curl "https://r4.ontoserver.csiro.au/fhir/ValueSet/\$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter=Diabetes&count=1" \
  -H "Accept: application/fhir+json" \
  | jq '.expansion.total, .expansion.contains[0]'
```

**Expected**: Returns total count and first matching concept.

## Important Notes

### 1. FHIR vs Native API

- **Snowstorm**: Has both FHIR API (`/fhir/*`) and native API (`/snowstorm/*`)
- **Ontoserver**: Only FHIR API (no native endpoints)

### 2. Response Format

- **Snowstorm Native**: Custom JSON structure with `items[]`
- **Ontoserver FHIR**: FHIR ValueSet with `expansion.contains[]`

### 3. Search Method

- **Snowstorm**: Direct concept search via `/snowstorm/snomed-ct/{branch}/concepts?term=...`
- **Ontoserver**: ValueSet expansion via `/fhir/ValueSet/$expand?filter=...`

### 4. ValueSet URL

- **Snowstorm**: Not used (uses branch in path)
- **Ontoserver**: Requires ValueSet URL: `http://snomed.info/sct/900000000000207008?fhir_vs`

### 5. Search Syntax

- **Snowstorm**: `term={searchTerm}`
- **Ontoserver**: `filter={searchTerm}` (in ValueSet $expand)

### 6. CodeSystem Search Limitation

⚠️ **Important**: CodeSystem search in Ontoserver does NOT support concept-level search. It only searches for CodeSystem resources themselves, not concepts within them. Use ValueSet $expand instead.

### 7. Language Support

Both support `Accept-Language` header:
- **Snowstorm**: May have language-specific endpoints
- **Ontoserver**: Uses FHIR standard language handling in ValueSet expansion

## Configuration for Ontoserver

### application.yml

```yaml
fhir:
  server:
    url: https://r4.ontoserver.csiro.au/fhir

snomed:
  branch: MAIN  # Not used for Ontoserver, but kept for compatibility
  language: sv
  fallback-to-english: true
```

### Environment Variable

```bash
export FHIR_SERVER_URL=https://r4.ontoserver.csiro.au/fhir
```

## Summary

**For Ontoserver, use FHIR ValueSet $expand** ✅:

```bash
curl -X GET \
  "https://r4.ontoserver.csiro.au/fhir/ValueSet/\$expand?url=http://snomed.info/sct/900000000000207008?fhir_vs&filter={term}&count=50" \
  -H "Accept: application/fhir+json" \
  -H "Accept-Language: sv"
```

**Key Changes from Snowstorm**:
1. Use `/fhir/ValueSet/$expand` instead of `/snowstorm/snomed-ct/{branch}/concepts`
2. Use `filter` parameter instead of `term`
3. Add `url=http://snomed.info/sct/900000000000207008?fhir_vs` parameter (ValueSet URL)
4. Use `application/fhir+json` instead of `application/json`
5. Parse FHIR ValueSet expansion response (`expansion.contains[]`) instead of custom JSON

**⚠️ Important**: CodeSystem search with `display:contains` does NOT work in Ontoserver. Always use ValueSet $expand for term-based concept search.

---

**Last Updated**: 2024
**Application Version**: 0.0.2-SNAPSHOT

