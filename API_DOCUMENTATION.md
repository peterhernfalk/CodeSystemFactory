# API Documentation Endpoints

This document describes all available endpoints for accessing API documentation and Swagger UI.

## Swagger UI Endpoints

### Primary Swagger UI Access

1. **`/swagger-ui.html`** (Recommended)
   - Main entry point for Swagger UI
   - Redirects to `/swagger-ui/index.html`
   - **Usage**: Open in browser: `http://localhost:8080/swagger-ui.html`
   - **Features**:
     - Interactive API exploration
     - Test endpoints directly from the browser
     - View request/response schemas
     - See example requests

2. **`/swagger-ui/index.html`**
   - Direct access to Swagger UI
   - Same as above, but direct URL

### Documentation Info Endpoint

3. **`/docs`** (New)
   - Returns JSON with information about all documentation endpoints
   - **Usage**: `GET http://localhost:8080/docs`
   - **Response**: JSON object with:
     - Swagger UI URLs
     - OpenAPI spec URL
     - List of all API endpoints

4. **`/docs/swagger`** (New)
   - Redirect endpoint to Swagger UI
   - Returns HTTP 303 redirect to `/swagger-ui.html`
   - **Usage**: `GET http://localhost:8080/docs/swagger`

## OpenAPI Specification Endpoints

5. **`/api-docs`**
   - OpenAPI 3.0 specification in JSON format
   - **Usage**: `GET http://localhost:8080/api-docs`
   - **Response**: Complete OpenAPI JSON specification
   - **Content-Type**: `application/json`
   - **Use cases**:
     - Import into API clients (Postman, Insomnia, etc.)
     - Generate client SDKs
     - API validation
     - Documentation generation

## Quick Access Guide

### For Developers

**Interactive Testing:**
```
http://localhost:8080/swagger-ui.html
```

**Get OpenAPI Spec:**
```bash
curl http://localhost:8080/api-docs
```

**Documentation Info:**
```bash
curl http://localhost:8080/docs
```

### For API Clients

**Import OpenAPI Spec:**
- URL: `http://localhost:8080/api-docs`
- Format: OpenAPI 3.0 JSON
- Can be imported into:
  - Postman
  - Insomnia
  - Swagger Editor
  - OpenAPI Generator (for SDK generation)

## Configuration

All documentation endpoints are configured in `application.yml`:

```yaml
springdoc:
  api-docs:
    path: /api-docs
    enabled: true
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    try-it-out-enabled: true
    filter: true
    display-request-duration: true
```

## Available API Endpoints (Documented in Swagger)

When you access Swagger UI, you'll see documentation for:

1. **`POST /api/terms/match`** - Match terms with SNOMED CT
2. **`POST /api/ai/recommend`** - Get AI recommendations
3. **`POST /api/ai/definitions`** - Get AI definitions
4. **`POST /api/codesystems/build`** - Build a code system
5. **`POST /api/codesystems/export`** - Export code system

## Production Considerations

In production, you may want to:

1. **Disable Swagger UI** (if not needed):
   ```yaml
   springdoc:
     swagger-ui:
       enabled: false
   ```

2. **Restrict Access** (add authentication):
   - Use Spring Security to protect `/swagger-ui.html` and `/api-docs`
   - Or use IP whitelisting

3. **Custom Path** (for security through obscurity):
   ```yaml
   springdoc:
     swagger-ui:
       path: /custom-docs-path.html
   ```

## Examples

### Access Swagger UI
```bash
# Open in browser
open http://localhost:8080/swagger-ui.html

# Or use curl to verify it's accessible
curl -I http://localhost:8080/swagger-ui.html
```

### Get OpenAPI Spec
```bash
# Save to file
curl http://localhost:8080/api-docs > openapi.json

# Pretty print
curl http://localhost:8080/api-docs | jq .
```

### Get Documentation Info
```bash
curl http://localhost:8080/docs | jq .
```

## Troubleshooting

**Swagger UI not loading:**
- Verify backend is running: `curl http://localhost:8080/api-docs`
- Check SpringDoc dependency in `pom.xml`
- Check application logs for errors

**404 on `/swagger-ui.html`:**
- Verify `springdoc.swagger-ui.enabled=true` in `application.yml`
- Check that `springdoc-openapi-starter-webmvc-ui` is in dependencies

**OpenAPI spec empty:**
- Ensure controllers have `@RestController` annotation
- Check that endpoints are properly annotated with `@PostMapping`, `@GetMapping`, etc.
- Verify CORS is configured if accessing from different origin

