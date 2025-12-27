# Local Development Guide

This guide will help you start and test the application locally.

## Prerequisites

Before starting, ensure you have:

- **Java 17+** (check with `java -version`)
- **Maven 3.6+** (check with `mvn -version`)
- **Node.js 18+** and **npm** (check with `node -v` and `npm -v`)

## Quick Start

### Step 1: Start the Backend

Open a terminal in the project root directory:

```bash
cd backend
mvn spring-boot:run
```

**What happens:**
- Maven downloads dependencies (first time only)
- Spring Boot compiles and starts the application
- Backend runs on `http://localhost:8080`
- AI features are disabled by default (uses mock responses)

**Expected output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| | ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.3.2)

Started CodesysApplication in X.XXX seconds
```

**Wait for:** `Started CodesysApplication` message before proceeding.

### Step 2: Start the Frontend

Open a **new terminal** window (keep the backend running):

```bash
cd frontend
npm install  # Only needed first time or after dependency changes
npm run dev
```

**What happens:**
- Installs dependencies (first time only)
- Starts Vite development server
- Frontend runs on `http://localhost:5173`
- API requests are proxied to `http://localhost:8080`

**Expected output:**
```
  VITE v5.x.x  ready in XXX ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

### Step 3: Access the Application

Open your browser and navigate to:
```
http://localhost:5173
```

You should see the SNOMED Code System Builder interface.

## Testing the Application

### 1. Test the Frontend UI

1. **Open** `http://localhost:5173` in your browser
2. **Enter some terms** in the text area (one per line):
   ```
   Diabetes
   Heart attack
   MRI Heart
   Unknown term
   ```
3. **Click "Match Terms"** - should show matched and unmatched results
4. **Click "Get AI Recommendations"** - should show AI recommendations (mock data)
5. **Build a Code System** - fill in metadata and build
6. **Export** - try exporting in different formats (FHIR, CSV, Excel)

### 2. Test the Backend API Directly

#### Test the OpenAPI Documentation Endpoint

```bash
# Get the OpenAPI specification
curl http://localhost:8080/api-docs
```

This should return a JSON object with the complete API specification.

#### Test Term Matching

```bash
curl -X POST http://localhost:8080/api/terms/match \
  -H "Content-Type: application/json" \
  -d '{"terms": ["Diabetes", "Heart attack", "Unknown term"]}'
```

Expected response:
```json
{
  "matched": [
    {
      "inputTerm": "Diabetes",
      "snomedId": "...",
      "preferredTerm": "...",
      "fsn": "...",
      "similarity": 0.95
    }
  ],
  "unmatched": [
    {
      "inputTerm": "Unknown term",
      "reason": "No match found in SNOMED CT (similarity < 0.75)"
    }
  ]
}
```

#### Test AI Recommendations

```bash
curl -X POST http://localhost:8080/api/ai/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "unmatchedTerms": ["Unknown term"],
    "matchedSnomedIds": ["73211009"],
    "context": "Swedish healthcare terminology"
  }'
```

### 3. Use Swagger UI (Interactive API Testing)

1. **Navigate to:** `http://localhost:8080/swagger-ui.html`
2. **Explore endpoints:**
   - `/api/terms/match` - Term matching
   - `/api/ai/recommend` - AI recommendations
   - `/api/ai/definitions` - AI definitions
   - `/api/codesystems/build` - Build code system
   - `/api/codesystems/export` - Export code system
3. **Test endpoints** directly from the UI
4. **View the OpenAPI spec** at `/api-docs` (also accessible via Swagger UI)

## Available Endpoints

### API Documentation
- **OpenAPI Spec (JSON):** `http://localhost:8080/api-docs`
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`

### API Endpoints
- `POST /api/terms/match` - Match terms with SNOMED CT
- `POST /api/ai/recommend` - Get AI recommendations
- `POST /api/ai/definitions` - Get AI definitions
- `POST /api/codesystems/build` - Build a code system
- `POST /api/codesystems/export` - Export code system

## Troubleshooting

### Backend Issues

**Port 8080 already in use:**
```bash
# macOS/Linux - Find and kill the process
lsof -ti:8080 | xargs kill -9

# Or change the port in application.yml:
# server.port: 8081
```

**Maven build fails:**
```bash
cd backend
mvn clean install
```

**Java version issues:**
- Ensure Java 17+ is installed: `java -version`
- Set JAVA_HOME if needed: `export JAVA_HOME=/path/to/java17`

**Dependencies not downloading:**
```bash
cd backend
mvn clean
mvn dependency:resolve
mvn spring-boot:run
```

### Frontend Issues

**Port 5173 already in use:**
- Vite will automatically try the next available port
- Or specify a different port: `npm run dev -- --port 3000`

**Module not found errors:**
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
```

**API requests failing:**
- Verify backend is running: `curl http://localhost:8080/api-docs`
- Check browser console (F12) for errors
- Verify proxy configuration in `vite.config.ts`
- Check CORS settings (should be enabled by default)

**Frontend can't connect to backend:**
- Ensure backend is running first
- Check that backend is on port 8080
- Verify proxy in `vite.config.ts` points to `http://localhost:8080`

### General Issues

**Changes not reflecting:**
- Backend: Restart the Spring Boot application
- Frontend: Vite hot-reloads automatically, but you may need to refresh the browser

**Browser cache issues:**
- Hard refresh: `Cmd+Shift+R` (Mac) or `Ctrl+Shift+R` (Windows/Linux)
- Clear browser cache

## Development Workflow

1. **Start backend first** (takes longer to start)
2. **Wait for "Started CodesysApplication"** message
3. **Start frontend** in a separate terminal
4. **Make code changes** - both will hot-reload automatically:
   - Backend: Restart required for Java changes
   - Frontend: Vite hot-reloads React changes automatically
5. **Check logs** in both terminals for errors
6. **Test in browser** at `http://localhost:5173`

## Enabling Real AI Features (Optional)

To use real OpenAI API calls instead of mock responses:

1. **Get an OpenAI API key** from https://platform.openai.com
2. **Set the environment variable:**
   ```bash
   export OPENAI_API_KEY=your-api-key-here
   ```
3. **Enable AI in application.yml:**
   ```yaml
   ai:
     enabled: true
   ```
4. **Restart the backend**

**Note:** This will make actual API calls and may incur costs.

## Testing Checklist

- [ ] Backend starts successfully
- [ ] Frontend starts successfully
- [ ] Can access frontend at `http://localhost:5173`
- [ ] Can access Swagger UI at `http://localhost:8080/swagger-ui.html`
- [ ] Can access OpenAPI spec at `http://localhost:8080/api-docs`
- [ ] Term matching works in UI
- [ ] Term matching works via API (curl/Swagger)
- [ ] AI recommendations work (mock or real)
- [ ] Code system build works
- [ ] Code system export works (FHIR, CSV, Excel)

## Next Steps

- Review the API documentation at `/api-docs`
- Explore endpoints in Swagger UI
- Test the full workflow in the frontend
- Check the deployment guide for production setup

