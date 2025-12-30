# Snowstorm Integration Analysis

This document analyzes two alternatives for integrating Snowstorm (SNOMED CT terminology server) into the project architecture.

## Current Architecture

### Current Communication Pattern

The backend currently communicates with Snowstorm as an **external HTTP service**:

**Communication Method**: REST API calls via `RestTemplate`

**Endpoints Used**:
1. **Concept Search**: `GET /snowstorm/snomed-ct/{branch}/concepts?term={term}&limit=50`
2. **Concept Details**: `GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}`
3. **Hierarchical Relations**: `GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}/children` and `/parents`
4. **FHIR ValueSet Expand**: `POST /fhir/ValueSet/$expand` (alternative search method)

**Configuration**:
- Base URL: `fhir.server.url` in `application.yml` (default: `https://snowstorm-training.snomedtools.org`)
- Branch: `snomed.branch` (default: `MAIN`)
- Language: `snomed.language` (default: `sv` for Swedish)

**Services Using Snowstorm**:
- `FhirSnomedService` - Primary term matching
- `HybridRecommendationServiceImpl` - Hierarchical search and concept details

---

## Alternative 1: Snowstorm as Part of This Project

### Architecture Overview

Snowstorm would be integrated as a **submodule or subdirectory** within the same Git repository.

```
CodeSystemFactory/
├── backend/              # Current Spring Boot application
├── frontend/             # Current React application
├── snowstorm/            # Snowstorm instance (new)
│   ├── docker-compose.yml
│   ├── Dockerfile
│   └── configuration/
└── docker/
    └── docker-compose.yml  # Updated to include Snowstorm
```

### Implementation Approach

#### Option 1A: Docker Compose Integration
- Add Snowstorm as a Docker service in `docker/docker-compose.yml`
- Use official Snowstorm Docker image or custom build
- Configure networking between services

#### Option 1B: Maven Multi-Module Project
- Convert to Maven multi-module project
- Add Snowstorm as a module (if using Java-based deployment)
- Shared build and deployment pipeline

#### Option 1C: Git Submodule
- Add Snowstorm repository as Git submodule
- Maintain separate version control but in same repo structure

### Communication Pattern

**Same as Current**: HTTP REST API calls
- Backend → Snowstorm: `http://snowstorm:8080` (internal Docker network)
- Or: `http://localhost:8080` (if running locally)

**No Code Changes Required**: The existing `FhirSnomedService` and `HybridRecommendationServiceImpl` would work unchanged, just pointing to local Snowstorm instead of external URL.

### Configuration Changes

**Backend `application.yml`**:
```yaml
fhir:
  server:
    url: http://snowstorm:8080/fhir  # Internal Docker network
    # Or for local dev: http://localhost:8080/fhir
```

**Docker Compose**:
```yaml
services:
  snowstorm:
    image: ihtsdo/snowstorm:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/snowstorm
    depends_on:
      - mongo
    networks:
      - codesystemfactory-network
  
  mongo:
    image: mongo:latest
    volumes:
      - snowstorm-data:/data/db
    networks:
      - codesystemfactory-network
  
  backend:
    # ... existing config
    depends_on:
      - snowstorm
    environment:
      - FHIR_SERVER_URL=http://snowstorm:8080/fhir
```

### Advantages

1. **Unified Deployment**
   - Single repository for all components
   - Easier to deploy together
   - Version synchronization between components

2. **Simplified Development**
   - Developers can run entire stack locally
   - No external dependencies during development
   - Easier to test with controlled SNOMED data

3. **Data Control**
   - Can use specific SNOMED release versions
   - Can load custom extensions or local codes
   - No dependency on external training server

4. **Network Performance**
   - Internal Docker network (faster)
   - No external network latency
   - Better for high-volume operations

5. **Offline Development**
   - Works without internet connection
   - No external service availability concerns

### Disadvantages

1. **Repository Size**
   - Snowstorm is a large Java application
   - Increases repository size significantly
   - Slower Git operations

2. **Complexity**
   - More components to manage
   - Requires MongoDB for Snowstorm
   - More complex local development setup

3. **Resource Requirements**
   - Snowstorm requires significant memory (2-4GB+)
   - MongoDB requires additional resources
   - Higher system requirements for development

4. **Maintenance Overhead**
   - Need to maintain Snowstorm configuration
   - SNOMED data loading and updates
   - Database backups and management

5. **Deployment Complexity**
   - More services to deploy
   - More potential failure points
   - Requires orchestration (Docker Compose/Kubernetes)

6. **Version Coupling**
   - Snowstorm version tied to project version
   - Harder to upgrade Snowstorm independently
   - May need to coordinate releases

### Deployment Scenarios

**Local Development**:
```bash
docker-compose up  # Starts backend, frontend, Snowstorm, MongoDB
```

**Production (Render)**:
- Would need to deploy Snowstorm as separate service
- Or use managed Snowstorm instance
- More complex than current setup

---

## Alternative 2: Snowstorm as Separate Git Project

### Architecture Overview

Snowstorm would be maintained in a **separate Git repository** and deployed independently.

```
CodeSystemFactory/          # Current project
├── backend/
├── frontend/
└── docker/

snowstorm-deployment/       # Separate repository
├── docker-compose.yml
├── Dockerfile
├── configuration/
└── README.md
```

### Communication Pattern

**Same as Current**: HTTP REST API calls
- Backend → Snowstorm: `http://snowstorm.example.com` (external URL)
- Or: `http://localhost:8080` (if running locally)

**No Code Changes Required**: Same as Alternative 1 - existing services work unchanged.

### Configuration Changes

**Backend `application.yml`**:
```yaml
fhir:
  server:
    url: ${SNOWSTORM_URL:http://localhost:8080/fhir}  # Configurable URL
```

**Environment Variables**:
- Development: `SNOWSTORM_URL=http://localhost:8080/fhir`
- Production: `SNOWSTORM_URL=https://snowstorm.example.com/fhir`

### Advantages

1. **Separation of Concerns**
   - Clear boundaries between projects
   - Snowstorm can be used by multiple projects
   - Independent versioning and releases

2. **Simpler Repository**
   - Current repo stays focused on CodeSystemFactory
   - No large Snowstorm codebase in repo
   - Faster Git operations

3. **Independent Deployment**
   - Deploy Snowstorm once, use by many projects
   - Can upgrade Snowstorm without touching CodeSystemFactory
   - Better for microservices architecture

4. **Resource Efficiency**
   - Snowstorm can run on dedicated server
   - Shared by multiple applications
   - Better resource utilization

5. **Team Organization**
   - Different teams can maintain different repos
   - Clear ownership boundaries
   - Easier to assign responsibilities

6. **Flexibility**
   - Can switch between local and remote Snowstorm easily
   - Can use managed Snowstorm services
   - Can use different Snowstorm instances for different environments

7. **Deployment Options**
   - Deploy Snowstorm on dedicated infrastructure
   - Use cloud-managed Snowstorm (if available)
   - Share Snowstorm across multiple projects

### Disadvantages

1. **Deployment Coordination**
   - Need to deploy two separate projects
   - Version compatibility concerns
   - More complex CI/CD pipelines

2. **Development Setup**
   - Developers need to set up both projects
   - More complex onboarding
   - Need to manage two repositories

3. **Network Dependency**
   - Requires network connectivity
   - External service dependency
   - Potential latency issues

4. **Configuration Management**
   - Need to configure Snowstorm URL in multiple places
   - Environment-specific configurations
   - More configuration to manage

5. **Documentation**
   - Need documentation for both projects
   - Cross-project documentation
   - More places to look for information

### Deployment Scenarios

**Local Development**:
```bash
# Terminal 1: Start Snowstorm
cd snowstorm-deployment
docker-compose up

# Terminal 2: Start CodeSystemFactory
cd CodeSystemFactory
docker-compose up
```

**Production**:
- Deploy Snowstorm to dedicated server/cloud
- Configure CodeSystemFactory to point to Snowstorm URL
- Independent scaling and updates

---

## Communication Interface (Both Alternatives)

### Current Implementation

The backend uses **REST API calls** via Spring's `RestTemplate`:

**Service Layer**:
- `FhirSnomedService` - Makes HTTP calls to Snowstorm
- `HybridRecommendationServiceImpl` - Makes HTTP calls for hierarchical search

**API Endpoints Used**:

1. **Concept Search**:
   ```
   GET /snowstorm/snomed-ct/{branch}/concepts?term={term}&limit=50&activeFilter=true
   ```

2. **Concept Details**:
   ```
   GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}
   ```

3. **Hierarchical Relations**:
   ```
   GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}/children?limit=5
   GET /snowstorm/snomed-ct/{branch}/concepts/{conceptId}/parents?limit=5
   ```

4. **FHIR ValueSet Expand** (alternative):
   ```
   POST /fhir/ValueSet/$expand
   ```

### Communication Pattern (No Changes Needed)

**Both alternatives use the same communication pattern**:

```java
// Current implementation in FhirSnomedService
RestTemplate rest = new RestTemplate();
String url = snowstormBaseUrl + "/snowstorm/snomed-ct/" + branch + "/concepts";
ResponseEntity<Map<String, Object>> response = rest.exchange(url, HttpMethod.GET, ...);
```

**Only Configuration Changes**:
- Change `fhir.server.url` in `application.yml`
- Or set `SNOWSTORM_URL` environment variable
- No code changes required

### Alternative Communication Methods (Future Considerations)

#### Option A: Service Discovery
- Use Spring Cloud Service Discovery (Eureka, Consul)
- Snowstorm registers itself
- Backend discovers Snowstorm automatically

#### Option B: Message Queue
- Use RabbitMQ/Kafka for async communication
- Better for high-volume scenarios
- More complex but scalable

#### Option C: gRPC
- Replace REST with gRPC
- Better performance
- Requires Snowstorm gRPC support (may not exist)

**Recommendation**: Stick with REST API (current approach) - it's simple, well-supported, and works for both alternatives.

---

## Comparison Matrix

| Aspect | Alternative 1: Same Project | Alternative 2: Separate Project |
|--------|------------------------------|----------------------------------|
| **Repository Size** | Large (includes Snowstorm) | Small (focused) |
| **Git Operations** | Slower | Faster |
| **Deployment** | Single deployment | Separate deployments |
| **Development Setup** | One command (`docker-compose up`) | Two commands (two repos) |
| **Resource Usage** | Higher (all in one) | Lower (can share Snowstorm) |
| **Maintenance** | More complex | Simpler per project |
| **Versioning** | Coupled | Independent |
| **Reusability** | Snowstorm tied to project | Snowstorm reusable |
| **Network** | Internal (Docker network) | External (HTTP) |
| **Scalability** | Scale together | Scale independently |
| **Team Organization** | Single team | Separate teams possible |
| **Code Changes** | None required | None required |
| **Configuration** | Docker Compose | Environment variables |
| **Production Ready** | More complex | More flexible |

---

## Recommendations

### For Development/Testing: Alternative 1 (Same Project)

**Rationale**:
- Easier local development setup
- Developers can run everything with one command
- No external dependencies
- Better for testing with controlled data

**Implementation**:
- Add Snowstorm to `docker/docker-compose.yml`
- Use official Snowstorm Docker image
- Configure internal Docker networking

### For Production: Alternative 2 (Separate Project)

**Rationale**:
- Better separation of concerns
- Snowstorm can be shared across projects
- Independent scaling and updates
- More flexible deployment options
- Better resource utilization

**Implementation**:
- Deploy Snowstorm to dedicated infrastructure
- Configure backend with Snowstorm URL via environment variable
- Use service discovery or load balancer if needed

### Hybrid Approach (Recommended)

**Development**: Use Alternative 1 (Docker Compose with Snowstorm)
- Easy local setup
- All services in one place

**Production**: Use Alternative 2 (Separate deployment)
- Deploy Snowstorm separately
- Configure backend to point to production Snowstorm
- Better for scalability and maintenance

**Configuration Strategy**:
```yaml
# application.yml
fhir:
  server:
    url: ${SNOWSTORM_URL:http://snowstorm:8080/fhir}  # Default for Docker Compose
```

- **Local Dev**: Uses Docker Compose default (`snowstorm:8080`)
- **Production**: Set `SNOWSTORM_URL` environment variable to production URL

---

## Migration Path

### If Choosing Alternative 1 (Same Project)

1. **Add Snowstorm to Docker Compose**:
   - Add Snowstorm and MongoDB services
   - Configure networking
   - Update backend to use internal URL

2. **Update Documentation**:
   - Add Snowstorm setup instructions
   - Document data loading process
   - Update deployment guides

3. **No Code Changes Required**:
   - Existing services work as-is
   - Just change configuration

### If Choosing Alternative 2 (Separate Project)

1. **Create Snowstorm Repository**:
   - New Git repository
   - Docker Compose configuration
   - Documentation

2. **Deploy Snowstorm**:
   - Deploy to infrastructure
   - Configure URL/domain
   - Set up monitoring

3. **Update CodeSystemFactory**:
   - Change `application.yml` to use environment variable
   - Update deployment configuration
   - Document Snowstorm URL configuration

4. **No Code Changes Required**:
   - Existing services work as-is
   - Just change configuration URL

---

## Technical Considerations

### Snowstorm Requirements

**Infrastructure**:
- Java runtime (typically Java 17+)
- MongoDB database (for SNOMED data storage)
- Memory: 2-4GB+ (depends on SNOMED release size)
- Disk: 10-50GB+ (for SNOMED data)

**Data Loading**:
- Need to load SNOMED CT release files
- RF2 format files
- Can take significant time (hours for full release)

**APIs Provided**:
- REST API (what we currently use)
- FHIR API (what we currently use)
- Native Snowstorm API

### Backend Code Compatibility

**Current Code**: ✅ **Fully Compatible**
- Uses standard HTTP REST calls
- No Snowstorm-specific dependencies
- Just needs URL configuration

**No Refactoring Needed**:
- `FhirSnomedService` - works with any Snowstorm instance
- `HybridRecommendationServiceImpl` - works with any Snowstorm instance
- Just change the base URL

### Network Configuration

**Alternative 1 (Same Project)**:
- Docker internal network: `http://snowstorm:8080`
- No firewall rules needed
- Fast internal communication

**Alternative 2 (Separate Project)**:
- External URL: `https://snowstorm.example.com`
- May need firewall rules
- Network latency considerations
- SSL/TLS configuration

---

## Conclusion

**Both alternatives are viable** and require **no code changes** - only configuration changes.

**Recommended Approach**: **Hybrid**
- **Development**: Alternative 1 (Docker Compose) for easy local setup
- **Production**: Alternative 2 (Separate deployment) for flexibility and scalability

**Key Point**: The backend code is already designed to work with any Snowstorm instance - it just needs the correct URL configuration. This makes both alternatives equally feasible from a code perspective.

