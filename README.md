# Code System Factory

A stateless web application for building medical code systems by matching domain terms with SNOMED CT (Swedish) and generating AI-assisted recommendations.

## Overview

Code System Factory helps healthcare organizations create structured code systems by:
- **Matching terms** with SNOMED CT concepts using similarity algorithms
- **Providing AI recommendations** for unmatched terms using hybrid strategies
- **Building and exporting** code systems in multiple formats (FHIR JSON, CSV, Excel)
- **Supporting Swedish language** terminology with English fallback

## Features

### ✅ Core Functionality

- **Term Matching**: Match domain terms with SNOMED CT concepts using Jaro-Winkler similarity
- **Hybrid Recommendations**: Multi-strategy recommendation system:
  - Synonym database lookup (curated Swedish→SNOMED mappings)
  - Fuzzy matching (similarity 0.4-0.6)
  - Hierarchical search (parent/child concepts)
  - AI fallback (OpenAI/LLM)
- **Code System Building**: Combine matched and recommended terms into structured code systems
- **Multi-format Export**: Export code systems as FHIR JSON, CSV, or Excel
- **Swedish Language Support**: Primary language Swedish with English fallback
- **Swagger UI**: Interactive API documentation at `/swagger-ui.html`

### ✅ Technical Features

- **Stateless Architecture**: No database dependencies - all state managed in frontend
- **RESTful API**: Clean REST endpoints with JSON request/response
- **CORS Enabled**: Configured for browser access
- **Version Management**: Automatic version tracking from Maven/package.json
- **Development Mode**: Mock AI responses when AI is disabled

## Technology Stack

### Backend
- **Framework**: Spring Boot 3.3.2
- **Language**: Java 17
- **Build Tool**: Maven
- **AI Integration**: Spring AI 1.0.0-M3 (OpenAI support)
- **API Documentation**: SpringDoc OpenAPI 2.6.0
- **SNOMED Integration**: Snowstorm FHIR API

### Frontend
- **Framework**: React 18.2.0
- **Build Tool**: Vite 5.4.21
- **Language**: TypeScript 5.4.0
- **Package Manager**: npm

## Quick Start

### Prerequisites

- **Java 17+** (for backend)
- **Node.js 18+** (for frontend)
- **Maven 3.9+** (for backend builds)
- **Snowstorm Server** (default: `https://snowstorm-training.snomedtools.org`)

### Local Development

#### 1. Start Backend

```bash
cd backend
mvn spring-boot:run
```

Backend will start on `http://localhost:8080`

**Development Mode**:
- AI features disabled by default (`ai.enabled: false`)
- Mock API key used (no OpenAI key required)
- Mock responses returned for AI endpoints

#### 2. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend will start on `http://localhost:5173`

The Vite dev server is configured to proxy `/api` requests to the backend.

**Apple Silicon (M1/M2/M3)**: If you see a Rollup error like `Cannot find module @rollup/rollup-darwin-x64` or `incompatible architecture (have 'arm64', need 'x86_64')`, run the dev server with native Node so it uses the arm64 build:  
`arch -arm64 npm run dev`

### Access Points

- **Frontend**: http://localhost:5173
- **Backend API**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/api-docs
- **Version Endpoint**: http://localhost:8080/api/version

## API Endpoints

### Term Matching
- **POST** `/api/terms/match` - Match terms with SNOMED CT

### AI Recommendations
- **POST** `/api/ai/recommend` - Get AI recommendations for unmatched terms
- **POST** `/api/ai/definitions` - Get AI definitions for terms

### Code System Management
- **POST** `/api/codesystems/build` - Build a code system from matched/recommended terms
- **POST** `/api/codesystems/export` - Export code system (FHIR/CSV/Excel)

### Documentation & Info
- **GET** `/api/version` - Get application version
- **GET** `/docs` - Documentation information
- **GET** `/swagger-ui.html` - Swagger UI
- **GET** `/api-docs` - OpenAPI specification

## Configuration

### Backend Configuration (`application.yml`)

```yaml
# SNOMED CT Configuration
snomed:
  branch: MAIN
  language: sv                    # Swedish
  fallback-to-english: true

# FHIR Server (Snowstorm)
fhir:
  server:
    url: https://snowstorm-training.snomedtools.org/fhir

# AI Configuration
ai:
  enabled: false                  # Set to true for production with API key
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}  # Required if ai.enabled=true

# Recommendation System
recommendation:
  use-hybrid: true
  fuzzy-match:
    min-similarity: 0.4
    max-similarity: 0.6
  ai-fallback: true
```

### Environment Variables

- `OPENAI_API_KEY` - OpenAI API key (required if `ai.enabled=true`)
- `FHIR_SERVER_URL` - Snowstorm server URL
- `PORT` - Server port (default: 8080)
- `SERVER_URL` - Production server URL (for Swagger UI)

## Deployment

### Render.com Deployment

The project includes `render.yaml` for automated deployment to Render.com.

**Services**:
- Backend: Java web service
- Frontend: Static site

See `documentation/RENDER_DEPLOYMENT.md` for detailed instructions.

### Docker Deployment

Dockerfiles are available for both backend and frontend:
- `backend/Dockerfile` - Multi-stage Spring Boot build
- `frontend/Dockerfile` - Nginx static server

## Project Structure

```
CodeSystemFactory/
├── backend/                 # Spring Boot application
│   ├── src/main/java/      # Java source code
│   ├── src/main/resources/ # Configuration files
│   ├── pom.xml             # Maven dependencies
│   └── Dockerfile          # Docker build
├── frontend/               # React application
│   ├── src/                # React components
│   ├── package.json        # npm dependencies
│   └── Dockerfile          # Docker build
├── documentation/          # Comprehensive documentation
├── render.yaml            # Render.com deployment config
└── README.md              # This file
```

## Documentation

Comprehensive documentation is available in the `documentation/` directory:

### Getting Started
- `LOCAL_DEVELOPMENT.md` - Local setup and testing guide
- `RENDER_DEPLOYMENT.md` - Deployment to Render.com

### Architecture & Design
- `STATELESS_SOLUTION_DESIGN.md` - Stateless architecture overview
- `ARCHITECTURE_ANALYSIS.md` - System architecture analysis
- `HYBRID_RECOMMENDATION_SYSTEM.md` - Recommendation system design

### API Documentation
- `API_DOCUMENTATION.md` - Complete API reference
- `ENDPOINT_DATA_MAPPING.md` - Data source mapping for all endpoints

### Features
- `SWEDISH_LANGUAGE_SUPPORT.md` - Swedish language configuration
- `AI_RECOMMENDATION_DATA_FLOW.md` - AI recommendation flow
- `RECOMMENDATION_LOGIC_FLOW.md` - Detailed recommendation logic
- `PUBLIC_SYNONYM_DATABASES.md` - Synonym database options

### Deployment & Operations
- `SNOWSTORM_DEPLOYMENT_RENDER.md` - Deploying Snowstorm to Render
- `FREE_AI_API_OPTIONS.md` - Free AI API alternatives
- `VERSION_MANAGEMENT.md` - Version management guide

## Version Information

- **Backend Version**: 0.0.2-SNAPSHOT (from `pom.xml`)
- **Frontend Version**: 0.0.2 (from `package.json`)
- **Version Endpoint**: `/api/version` returns current versions

## Development Notes

### AI Features

- **Development**: AI is disabled by default (`ai.enabled: false`)
- **Mock Mode**: Returns mock responses when AI is disabled
- **Production**: Set `ai.enabled: true` and provide `OPENAI_API_KEY`

### SNOMED CT Integration

- **Default Server**: `snowstorm-training.snomedtools.org` (public instance)
- **Language**: Configured for Swedish (`sv`) with English fallback
- **Matching Threshold**: 0.75 (75% similarity) for direct matches

### Synonym Database

- **Current**: In-memory hardcoded mappings
- **Future**: Can be enhanced to load from SNOMED CT descriptions
- See `PUBLIC_SYNONYM_DATABASES.md` for alternatives

## Contributing

1. Ensure all tests pass
2. Update documentation for new features
3. Follow existing code style
4. Update version numbers in `pom.xml` and `package.json`

## License

[Add your license information here]

## Support

For issues, questions, or contributions, please refer to the documentation in the `documentation/` directory or open an issue in the repository.

---

**Last Updated**: 2024  
**Backend Version**: 0.0.2-SNAPSHOT  
**Frontend Version**: 0.0.2
