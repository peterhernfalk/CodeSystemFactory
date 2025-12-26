# How to Run the Application

## Prerequisites

- **Java 17+** (for backend)
- **Maven 3.6+** (for backend)
- **Node.js 16+** and **npm** (for frontend)

## Quick Start (Development Mode)

### 1. Start the Backend

Open a terminal in the project root and run:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**What this does:**
- Starts Spring Boot on `http://localhost:8080`
- Uses H2 in-memory database (no setup required)
- AI features are disabled (uses mock responses)
- Database is recreated on each startup

**Expected output:**
```
Started CodesysApplication in X.XXX seconds
```

**Access points:**
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:testdb`, User: `sa`, Password: empty)

### 2. Start the Frontend

Open a **new terminal** in the project root and run:

```bash
cd frontend
npm install
npm run dev
```

**What this does:**
- Installs dependencies (first time only)
- Starts Vite dev server on `http://localhost:5173`
- Proxies API requests to `http://localhost:8080`

**Expected output:**
```
  VITE v5.x.x  ready in XXX ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

### 3. Access the Application

Open your browser and navigate to:
```
http://localhost:5173
```

## Production Mode (PostgreSQL)

If you want to use PostgreSQL instead of H2:

### 1. Setup PostgreSQL Database

```sql
CREATE DATABASE codesys;
CREATE USER postgres WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE codesys TO postgres;
```

### 2. Configure Environment Variables

Set these environment variables (or update `application.yml`):

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=codesys
export DB_USER=postgres
export DB_PASSWORD=postgres
```

### 3. Run Backend

```bash
cd backend
mvn spring-boot:run
```

**Note:** The default profile uses PostgreSQL. Make sure PostgreSQL is running before starting the backend.

## Troubleshooting

### Backend Issues

**Port 8080 already in use:**
```bash
# Find and kill the process
lsof -ti:8080 | xargs kill -9
```

**Maven dependencies not downloading:**
```bash
cd backend
mvn clean install
```

**Database connection errors (PostgreSQL):**
- Verify PostgreSQL is running: `pg_isready`
- Check connection details in `application.yml`
- Ensure database exists

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
- Verify backend is running on `http://localhost:8080`
- Check browser console for CORS errors
- Verify proxy configuration in `vite.config.ts`

### Database Issues

**H2 Console not accessible:**
- Ensure you're using the `dev` profile
- Access at: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (leave empty)

**Tables not created:**
- Check application logs for Hibernate DDL output
- Verify JPA is enabled in `application.yml`
- In dev mode, tables are created automatically on startup

## Development Workflow

1. **Start backend first** (it takes longer to start)
2. **Wait for "Started CodesysApplication"** message
3. **Start frontend** in a separate terminal
4. **Make changes** - both will hot-reload automatically
5. **Check logs** in both terminals for errors

## Testing the API

### Using Swagger UI

1. Start the backend
2. Navigate to: `http://localhost:8080/swagger-ui.html`
3. Explore and test endpoints interactively

### Using curl

**Create a code system:**
```bash
curl -X POST http://localhost:8080/api/codesystems/from-matches \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Code System",
    "version": "1.0.0",
    "matches": [
      {
        "inputTerm": "Diabetes",
        "snomedId": "73211009",
        "preferredTerm": "Diabetes mellitus",
        "fsn": "Diabetes mellitus (disorder)",
        "definition": "A metabolic disorder",
        "relations": ["is-a: Disorder"],
        "useCases": ["Clinical documentation"],
        "motivation": "Test"
      }
    ]
  }'
```

**List code systems:**
```bash
curl http://localhost:8080/api/codesystems
```

## Stopping the Application

- **Backend**: Press `Ctrl+C` in the backend terminal
- **Frontend**: Press `Ctrl+C` in the frontend terminal

## Next Steps

Once running:
1. Test the term matching: `POST /api/terms/match`
2. Test AI definitions: `POST /api/ai/definitions`
3. Create a code system: `POST /api/codesystems/from-matches`
4. View code systems: `GET /api/codesystems`

