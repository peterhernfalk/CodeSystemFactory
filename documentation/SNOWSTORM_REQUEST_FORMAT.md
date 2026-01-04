# Snowstorm Request Format for Term Matching

This document shows the exact HTTP request format used to match terms with SNOMED CT via Snowstorm.

## Primary Request

### HTTP Method
```
GET
```

### Base URL Structure
```
{snowstormBaseUrl}/snowstorm/snomed-ct/{branch}/concepts
```

### Default Configuration
- **Base URL**: `https://snowstorm-training.snomedtools.org`
- **Branch**: `MAIN`
- **Language**: `sv` (Swedish)
- **Fallback**: English (`en`) if Swedish fails

### Complete URL Example
```
https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=Diabetes&limit=50&activeFilter=true
```

## Request Details

### URL Components

**Path Segments**:
- `/snowstorm/snomed-ct` - Snowstorm API base path
- `/{branch}` - SNOMED CT branch (typically `MAIN`)
- `/concepts` - Concepts endpoint

**Query Parameters**:
- `term` - The search term (required)
- `limit` - Maximum number of results (default: 50)
- `activeFilter` - Filter for active concepts only (default: `true`)
- `languageRefset` - Language reference set ID (optional, if configured)

### HTTP Headers

```http
Accept: application/json
Accept-Language: sv
```

**Header Details**:
- `Accept`: `application/json` - Request JSON response
- `Accept-Language`: `sv` or `en` - Language preference (Swedish first, then English fallback)

## Complete Request Example

### Example 1: Search for "Diabetes" (Swedish)

**Request**:
```http
GET /snowstorm/snomed-ct/MAIN/concepts?term=Diabetes&limit=50&activeFilter=true HTTP/1.1
Host: snowstorm-training.snomedtools.org
Accept: application/json
Accept-Language: sv
```

**cURL Command**:
```bash
curl -X GET \
  "https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=Diabetes&limit=50&activeFilter=true" \
  -H "Accept: application/json" \
  -H "Accept-Language: sv"
```

### Example 2: Search for "Fotledsfraktur" (Swedish)

**Request**:
```http
GET /snowstorm/snomed-ct/MAIN/concepts?term=Fotledsfraktur&limit=50&activeFilter=true HTTP/1.1
Host: snowstorm-training.snomedtools.org
Accept: application/json
Accept-Language: sv
```

**cURL Command**:
```bash
curl -X GET \
  "https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=Fotledsfraktur&limit=50&activeFilter=true" \
  -H "Accept: application/json" \
  -H "Accept-Language: sv"
```

### Example 3: With Language Refset (if configured)

**Request**:
```http
GET /snowstorm/snomed-ct/MAIN/concepts?term=Diabetes&limit=50&activeFilter=true&languageRefset=46011000052107 HTTP/1.1
Host: snowstorm-training.snomedtools.org
Accept: application/json
Accept-Language: sv
```

## Request Flow in Code

### Step 1: Build URL

```java
UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(snowstormBaseUrl)
    .path("/snowstorm/snomed-ct")
    .pathSegment(branch, "concepts")
    .queryParam("term", tryTerm)
    .queryParam("limit", "50")
    .queryParam("activeFilter", "true");

// Add language refset if configured
if (languageRefset != null && tryLanguage.equals(language)) {
    urlBuilder.queryParam("languageRefset", languageRefset);
}

String searchUrl = urlBuilder.toUriString();
```

### Step 2: Create HTTP Headers

```java
HttpHeaders headers = new HttpHeaders();
headers.setAccept(List.of(MediaType.APPLICATION_JSON));
headers.setAcceptLanguage(java.util.Locale.LanguageRange.parse(tryLanguage));
HttpEntity<Void> req = new HttpEntity<>(headers);
```

### Step 3: Execute Request

```java
ResponseEntity<Map<String, Object>> searchResp = rest.exchange(
    searchUrl, 
    HttpMethod.GET, 
    req, 
    new ParameterizedTypeReference<Map<String, Object>>() {}
);
```

## Search Strategy

The application tries multiple search strategies in order:

### Strategy 1: Original Term in Preferred Language
```
GET /snowstorm/snomed-ct/MAIN/concepts?term={originalTerm}&limit=50&activeFilter=true
Accept-Language: sv
```

### Strategy 2: Lowercase Term in Preferred Language
```
GET /snowstorm/snomed-ct/MAIN/concepts?term={lowercaseTerm}&limit=50&activeFilter=true
Accept-Language: sv
```

### Strategy 3: Original Term in English (Fallback)
```
GET /snowstorm/snomed-ct/MAIN/concepts?term={originalTerm}&limit=50&activeFilter=true
Accept-Language: en
```

### Strategy 4: Alternative Term (Synonym Mapping)
If no results, tries alternative terms from synonym database:
```
GET /snowstorm/snomed-ct/MAIN/concepts?term={alternativeTerm}&limit=50&activeFilter=true
Accept-Language: sv
```

### Strategy 5: Individual Words (for multi-word terms)
If still no results, splits term into words and searches each:
```
GET /snowstorm/snomed-ct/MAIN/concepts?term={word1}&limit=50&activeFilter=true
GET /snowstorm/snomed-ct/MAIN/concepts?term={word2}&limit=50&activeFilter=true
...
```

## Response Format

### Successful Response

```json
{
  "items": [
    {
      "conceptId": "73211009",
      "pt": {
        "term": "Diabetes mellitus",
        "lang": "sv"
      },
      "fsn": {
        "term": "Diabetes mellitus (sjukdom)",
        "lang": "sv"
      },
      "descriptions": [
        {
          "term": "Diabetes mellitus",
          "type": "SYNONYM",
          "lang": "sv",
          "active": true
        },
        {
          "term": "Diabetes",
          "type": "SYNONYM",
          "lang": "sv",
          "active": true
        }
      ],
      "active": true
    }
  ],
  "total": 1,
  "limit": 50,
  "offset": 0
}
```

### Empty Response (No Matches)

```json
{
  "items": [],
  "total": 0,
  "limit": 50,
  "offset": 0
}
```

## Configuration Values

From `application.yml`:

```yaml
fhir:
  server:
    url: https://snowstorm-training.snomedtools.org/fhir

snomed:
  branch: MAIN
  language: sv
  language-refset: 
  fallback-to-english: true
```

**URL Extraction**:
- FHIR URL: `https://snowstorm-training.snomedtools.org/fhir`
- Extracted Base URL: `https://snowstorm-training.snomedtools.org` (removes `/fhir`)

## Code Location

**File**: `backend/src/main/java/com/example/codesys/service/impl/FhirSnomedService.java`

**Method**: `searchByTerm(String term)` (lines 69-420)

**Key Code Sections**:
- Lines 98-110: URL building
- Lines 91-94: Header creation
- Lines 114-115: Request execution

## Testing the Request

### Using cURL

```bash
# Search for "Diabetes" in Swedish
curl -X GET \
  "https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=Diabetes&limit=50&activeFilter=true" \
  -H "Accept: application/json" \
  -H "Accept-Language: sv" \
  | jq '.'
```

### Using HTTPie

```bash
http GET \
  "https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts" \
  term==Diabetes \
  limit==50 \
  activeFilter==true \
  Accept:application/json \
  Accept-Language:sv
```

### Using Postman

1. **Method**: GET
2. **URL**: `https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts`
3. **Query Parameters**:
   - `term`: `Diabetes`
   - `limit`: `50`
   - `activeFilter`: `true`
4. **Headers**:
   - `Accept`: `application/json`
   - `Accept-Language`: `sv`

## Debugging

The code includes debug logging:

```java
System.out.println("DEBUG: Searching with URL: " + searchUrl + " (language: " + tryLanguage + ")");
System.out.println("DEBUG: Response status: " + searchResp.getStatusCode());
System.out.println("DEBUG: Search for '" + tryTerm + "' (language: " + tryLanguage + ") returned " + 
    (itemsList instanceof List ? ((List<?>) itemsList).size() : 0) + " items");
```

Check backend console output to see the exact URLs being called.

## Alternative Endpoints

### Get Specific Concept by ID

```
GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}
```

**Example**:
```
GET /snowstorm/snomed-ct/MAIN/concepts/73211009
```

### Get Concept Descriptions

```
GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}/descriptions
```

**Example**:
```
GET /snowstorm/snomed-ct/MAIN/concepts/73211009/descriptions
```

### Get Concept Parents/Children

```
GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}/parents
GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}/children
```

**Example**:
```
GET /snowstorm/snomed-ct/MAIN/concepts/73211009/parents
GET /snowstorm/snomed-ct/MAIN/concepts/73211009/children
```

## Summary

**Primary Request Format**:
```
GET {baseUrl}/snowstorm/snomed-ct/{branch}/concepts?term={term}&limit=50&activeFilter=true
Headers:
  Accept: application/json
  Accept-Language: sv (or en for fallback)
```

**Default Values**:
- Base URL: `https://snowstorm-training.snomedtools.org`
- Branch: `MAIN`
- Language: `sv` (Swedish)
- Limit: `50`
- Active Filter: `true`

---

**Last Updated**: 2024
**Application Version**: 0.0.2-SNAPSHOT

