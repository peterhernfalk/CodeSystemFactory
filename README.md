# Code System Factory

A stateless web application for building medical code systems by matching domain terms with SNOMED CT and generating AI-assisted modeling suggestions (including SNOMED CT Editorial Guide–style proposals for unmatched terms).

**Versions:** backend `0.2.1-SNAPSHOT` · frontend `0.2.1`

## Overview

Code System Factory helps healthcare organizations create structured code systems by:

- **Matching terms** with SNOMED CT via selectable terminology servers (Ontoserver by default)
- **Modeling unmatched terms** with Editorial Guide–oriented AI suggestions (existing concepts, postcoordination, or new local terms)
- **Suggesting new-term modeling** for matched terms and merging those results with unmatched modeling
- **Building and exporting** code systems (FHIR JSON, CSV, Excel) that include matched terms plus all remaining AI proposals
- **Supporting Swedish language** terminology with English fallback



## Typical workflow

1. Enter terms and press **Match Terms**
2. Press **Model unmatched terms (SNOMED Editorial Guide)** for unmatched rows
3. Press **Find New-Term Modeling Suggestions** for matched terms (results **merge** with step 2)
4. Review / remove unwanted proposals, then press **Build Code System** (includes matched terms **and all listed AI proposals**)
5. Export as FHIR / CSV / Excel



## Features



### Core functionality

- **Term matching**: Jaro-Winkler similarity against SNOMED CT (threshold 0.75 for direct matches)
- **Multi-server matching**: Ontoserver (default), Snowstorm, Inera Terminologitjänsten, or fallback chain
- **Hybrid recommendations / modeling**:
  - Synonym database (curated Swedish→SNOMED mappings)
  - Fuzzy rematch and FHIR `ValueSet/$expand` candidate seeding
  - Hierarchical neighbours of matched concepts (FHIR `$lookup` on Ontoserver by default)
  - AI **MODELING** mode with Editorial Guide proximal-primitive rules (per-term contract)
- **Code system building**: Matched terms + existing SNOMED additions + candidate new terms (`LOCAL-`*) + any other listed proposals
- **Multi-format export**: FHIR JSON, CSV, Excel
- **Swedish language support**: Primary `sv` with English fallback
- **Swagger UI**: Interactive API docs at `/swagger-ui.html`



### Technical features

- **Stateless architecture**: No database — state lives in the frontend session
- **RESTful API**: JSON request/response
- **CORS enabled** for browser access
- **Version management**: From Maven `pom.xml` / npm `package.json` (`/api/version`)
- **Provider switch**: Gemini / Ollama / other OpenAI-compatible endpoints via env vars



## Technology Stack



### Backend

- **Framework**: Spring Boot 3.5.16
- **Language**: Java 21
- **Build Tool**: Maven
- **AI Integration**: Spring AI 1.1.5 (OpenAI-compatible client)
- **API Documentation**: SpringDoc OpenAPI 2.8.15
- **SNOMED / FHIR**: Ontoserver (default), Snowstorm FHIR/REST, Inera (often requires auth)



### Frontend

- **Framework**: React 18
- **Build Tool**: Vite 5.4
- **Language**: TypeScript
- **Package Manager**: npm



## Quick Start



### Prerequisites

- **Java 21+**
- **Node.js 18+** (Node 20+ recommended)
- **Maven 3.9+**
- Network access to a FHIR terminology server (default: public Ontoserver)



### Local Development



#### 1. Start Backend

```bash
cd backend
# Optional: enable live AI (see Free Provider Presets below)
export AI_ENABLED=true
export AI_API_KEY=<your_key>
mvn spring-boot:run
```

Backend: `http://localhost:8080`

Defaults in `application.yml`:

- `ai.enabled` defaults to **true** (set `AI_ENABLED=false` for mock/offline)
- Hybrid FHIR / hierarchy uses **Ontoserver** (`FHIR_SERVER_URL`)



#### 2. Start Frontend

```bash
cd frontend
npm install
npm run dev
# On Apple Silicon, if Rollup arch errors appear:
# arch -arm64 npm run dev
```

Frontend: `http://localhost:5173` (Vite proxies `/api` to the backend)

### Access Points


| Resource     | URL                                                                            |
| ------------ | ------------------------------------------------------------------------------ |
| Frontend     | [http://localhost:5173](http://localhost:5173)                                 |
| Backend API  | [http://localhost:8080/api](http://localhost:8080/api)                         |
| Swagger UI   | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| OpenAPI Spec | [http://localhost:8080/api-docs](http://localhost:8080/api-docs)               |
| Version      | [http://localhost:8080/api/version](http://localhost:8080/api/version)         |




## API Endpoints



### Term Matching

- **POST** `/api/terms/match` — Match terms with SNOMED CT (`server` or `serverChain`)



### AI Recommendations / Modeling

- **POST** `/api/ai/recommend` — Hybrid / modeling suggestions  
Modes: `UNMATCHED`, `ADDITIONAL`, `BOTH`, `MODELING`  
UI flow uses `MODELING` for unmatched-term and new-term modeling
- **POST** `/api/ai/definitions` — AI definitions for terms



### Code System Management

- **POST** `/api/codesystems/build` — Build from matched + recommended / modeling proposals
- **POST** `/api/codesystems/export` — Export FHIR / CSV / Excel



### Documentation & Info

- **GET** `/api/version`
- **GET** `/docs`
- **GET** `/swagger-ui.html`
- **GET** `/api-docs`



## Configuration



### Backend (`application.yml` highlights)

```yaml
ai:
  enabled: ${AI_ENABLED:true}
  provider: ${AI_PROVIDER:gemini}

spring:
  ai:
    openai:
      api-key: ${AI_API_KEY:${OPENAI_API_KEY:...}}
      base-url: ${AI_BASE_URL:https://generativelanguage.googleapis.com/v1beta/openai}
      chat:
        options:
          model: ${AI_MODEL:gemini-2.0-flash}

# Used by hybrid recommendations (lookup, hierarchy, $expand)
fhir:
  server:
    url: ${FHIR_SERVER_URL:https://r4.ontoserver.csiro.au/fhir}

ontoserver:
  url: ${ONTOSERVER_URL:https://r4.ontoserver.csiro.au/fhir}

snomed:
  branch: MAIN
  language: sv
  fallback-to-english: true

recommendation:
  use-hybrid: true
  ai-fallback: true
```



### Environment Variables


| Variable          | Purpose                                                   |
| ----------------- | --------------------------------------------------------- |
| `AI_ENABLED`      | Live AI calls (`true`/`false`)                            |
| `AI_PROVIDER`     | Label for logs (`gemini`, `ollama`, …)                    |
| `AI_BASE_URL`     | OpenAI-compatible base URL                                |
| `AI_MODEL`        | Model id                                                  |
| `AI_API_KEY`      | Provider API key                                          |
| `OPENAI_API_KEY`  | Legacy fallback if `AI_API_KEY` unset                     |
| `FHIR_SERVER_URL` | FHIR base for hybrid recommendations (default Ontoserver) |
| `ONTOSERVER_URL`  | Ontoserver for term matching                              |
| `PORT`            | Server port (default `8080`)                              |
| `SERVER_URL`      | Public URL for Swagger in production                      |




### Free Provider Presets



#### Gemini (hosted)

```bash
export AI_ENABLED=true
export AI_PROVIDER=gemini
export AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
export AI_MODEL=gemini-2.0-flash
export AI_API_KEY=<your_gemini_key>
```



#### Ollama (local)

```bash
export AI_ENABLED=true
export AI_PROVIDER=ollama
export AI_BASE_URL=http://localhost:11434
export AI_MODEL=llama3.1:8b
export AI_API_KEY=ollama
```

Use `http://localhost:11434` **without** `/v1` — Spring AI appends `/v1/chat/completions`.

See `documentation/FREE_AI_PROVIDER_SETUP.md` and `documentation/GEMINI_LOCAL_AND_RENDER_SETUP.md`.

## SNOMED CT servers


| Option in UI          | Notes                                                                  |
| --------------------- | ---------------------------------------------------------------------- |
| **Ontoserver (FHIR)** | Default selection; solid public FHIR R4                                |
| Fallback chain        | Snowstorm → Ontoserver → Inera                                         |
| Snowstorm             | Training instance may be unreachable                                   |
| Inera                 | Swedish Terminologitjänsten; often not publicly callable without SITHS |


Hybrid AI/modeling FHIR calls use `FHIR_SERVER_URL` (Ontoserver by default), independent of the match dropdown unless you align env vars.

## Deployment



### [Render.com](http://Render.com)

`render.yaml` defines the Java backend and static frontend. Start command uses  
`backend/target/codesys-backend-0.2.1-SNAPSHOT.jar`.

See `documentation/RENDER_DEPLOYMENT.md`.

### Docker

- `backend/Dockerfile` — multi-stage Spring Boot build (Java 21)
- `frontend/Dockerfile` — Nginx static server



## Project Structure

```
CodeSystemFactory/
├── backend/                 # Spring Boot application
│   ├── src/main/java/
│   ├── src/main/resources/  # application.yml
│   ├── pom.xml              # version 0.2.1-SNAPSHOT
│   └── Dockerfile
├── frontend/                # React + Vite
│   ├── src/ui/              # App, MatchResults, AiRecommendations, …
│   ├── package.json         # version 0.2.1
│   └── Dockerfile
├── documentation/           # Guides and design notes
├── .cursor/skills/          # Agent skills (local AI, SNOMED, hybrid, Render)
├── render.yaml
└── README.md
```



## Documentation



### Getting Started

- `documentation/LOCAL_DEVELOPMENT.md`
- `documentation/RUNNING_THE_APP.md`
- `documentation/RENDER_DEPLOYMENT.md`
- `documentation/FREE_AI_PROVIDER_SETUP.md`
- `documentation/GEMINI_LOCAL_AND_RENDER_SETUP.md`



### Architecture & recommendations

- `documentation/HYBRID_RECOMMENDATION_SYSTEM.md`
- `documentation/AI_RECOMMENDATION_DATA_FLOW.md`
- `documentation/RECOMMENDATION_LOGIC_FLOW.md`
- `documentation/SERVER_SELECTION_DESIGN.md`
- `documentation/STATELESS_SOLUTION_DESIGN.md`



### API & operations

- `documentation/API_DOCUMENTATION.md`
- `documentation/ENDPOINT_DATA_MAPPING.md`
- `documentation/VERSION_MANAGEMENT.md`
- `documentation/DEPLOYMENT_GUIDE.md`



## Version Information


| Component                 | Version            |
| ------------------------- | ------------------ |
| Backend (`pom.xml`)       | `0.2.1-SNAPSHOT`   |
| Frontend (`package.json`) | `0.2.1`            |
| Version API               | `GET /api/version` |




## Development Notes



### AI

- Live AI is on by default in config; provide `AI_API_KEY` (or disable with `AI_ENABLED=false`)
- **MODELING** failures surface as HTTP 502 (not a silent empty fallback)
- Build includes **all** proposals still listed in the UI (remove cards to exclude)



### Matching & modeling

- Match threshold: **0.75**
- Unmatched modeling: Editorial Guide triage (existing / postcoordination / new)
- New-term modeling for matched inputs **merges** with prior unmatched modeling by `inputTerm`
- Local codes `LOCAL-*` are exported with source `MODELING_NEW_TERM`



## Contributing

1. Ensure tests pass (`backend`: `mvn test` with Java 21)
2. Update documentation for behavioural changes
3. Follow existing code style
4. Bump versions in `pom.xml` and `package.json` (and jar references in `Dockerfile` / `render.yaml`)



## License

[Add your license information here]

## Support

See `documentation/` or open an issue in the repository.

---

**Last Updated**: 2026-09-05  
**Backend Version**: 0.2.1-SNAPSHOT  
**Frontend Version**: 0.2.1