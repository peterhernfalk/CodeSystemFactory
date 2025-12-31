# Deploying Snowstorm to Render.com

This guide covers deploying both **Snowstorm Lite** and **Full Snowstorm** instances to Render.com, including code system import and integration with the CodeSystemFactory project.

## Table of Contents

1. [Overview: Snowstorm Lite vs Full Snowstorm](#overview)
2. [Prerequisites](#prerequisites)
3. [Deploying Snowstorm Lite](#snowstorm-lite)
4. [Deploying Full Snowstorm](#full-snowstorm)
5. [Importing Code Systems](#importing-code-systems)
6. [Integration with CodeSystemFactory](#integration)
7. [Troubleshooting](#troubleshooting)

---

## Overview: Snowstorm Lite vs Full Snowstorm {#overview}

### Snowstorm Lite
- **Purpose**: Lightweight version for development and small deployments
- **Database**: Uses embedded Elasticsearch (simpler setup)
- **Memory**: Lower memory requirements (~2-4GB)
- **Use Case**: Development, testing, small-scale production
- **Limitations**: Limited to smaller SNOMED CT releases

### Full Snowstorm
- **Purpose**: Production-ready, full-featured SNOMED CT terminology server
- **Database**: Requires external Elasticsearch cluster
- **Memory**: Higher memory requirements (~4-8GB+)
- **Use Case**: Production deployments, large code systems
- **Features**: Full FHIR API, advanced search, better performance

---

## Prerequisites {#prerequisites}

1. **Render.com Account** (free tier available)
2. **GitHub Repository** with your CodeSystemFactory project
3. **SNOMED CT Release Files** (RF2 format) - if importing code systems
4. **Docker Knowledge** (basic understanding)

### Render.com Service Limits (Free Tier)
- **Web Services**: 750 hours/month
- **Disk Space**: 512MB per service
- **Memory**: 512MB RAM per service
- **Note**: For full Snowstorm, you may need a paid plan due to memory requirements

---

## Deploying Snowstorm Lite {#snowstorm-lite}

Snowstorm Lite is easier to deploy as it includes embedded Elasticsearch.

### Step 1: Create Dockerfile for Snowstorm Lite

Create a new directory and Dockerfile:

```bash
mkdir snowstorm-lite
cd snowstorm-lite
```

Create `Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Download Snowstorm Lite
# Check https://github.com/IHTSDO/snowstorm-lite/releases for latest version
ARG SNOWSTORM_VERSION=8.12.0
RUN wget -O snowstorm.jar \
    https://github.com/IHTSDO/snowstorm-lite/releases/download/v${SNOWSTORM_VERSION}/snowstorm-lite-${SNOWSTORM_VERSION}.jar

# Expose Snowstorm port (default: 8080)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run Snowstorm Lite
ENTRYPOINT ["java", "-jar", "snowstorm.jar"]
```

### Step 2: Create render.yaml Entry

Add to your existing `render.yaml`:

```yaml
services:
  # ... existing services ...

  # Snowstorm Lite Service
  - type: web
    name: snowstorm-lite
    env: docker
    region: frankfurt
    plan: free  # Or 'starter' for more resources
    dockerfilePath: ./snowstorm-lite/Dockerfile
    dockerContext: ./snowstorm-lite
    healthCheckPath: /health
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: production
      - key: SERVER_PORT
        value: 8080
      - key: ELASTICSEARCH_HOST
        value: localhost  # Embedded Elasticsearch
      - key: ELASTICSEARCH_PORT
        value: 9200
      - key: SPRING_DATA_ELASTICSEARCH_CLUSTER_NAME
        value: snowstorm-cluster
      # Optional: Configure code system import
      - key: SNOMED_IMPORT_PATH
        value: /app/imports
```

### Step 3: Deploy to Render

1. **Push to GitHub**:
   ```bash
   git add snowstorm-lite/
   git add render.yaml
   git commit -m "Add Snowstorm Lite deployment"
   git push
   ```

2. **Deploy via Render Dashboard**:
   - Go to Render Dashboard
   - Click "New +" → "Blueprint"
   - Select your repository
   - Render will detect `render.yaml` and deploy all services

3. **Or Deploy Manually**:
   - Go to Render Dashboard
   - Click "New +" → "Web Service"
   - Connect your repository
   - Select "Docker" as environment
   - Set Dockerfile path: `snowstorm-lite/Dockerfile`
   - Set Docker context: `snowstorm-lite`
   - Click "Create Web Service"

### Step 4: Verify Deployment

Once deployed, verify Snowstorm Lite is running:

```bash
# Get the service URL (e.g., https://snowstorm-lite.onrender.com)
curl https://snowstorm-lite.onrender.com/health
```

Expected response:
```json
{"status":"UP"}
```

---

## Deploying Full Snowstorm {#full-snowstorm}

Full Snowstorm requires an external Elasticsearch instance. On Render.com, you'll need to deploy both services.

### Step 1: Deploy Elasticsearch

**Option A: Use Render's Elasticsearch Service (if available)**

Render.com may offer managed Elasticsearch. Check their services.

**Option B: Deploy Elasticsearch as a Web Service**

Create `elasticsearch/Dockerfile`:

```dockerfile
FROM docker.elastic.co/elasticsearch/elasticsearch:8.11.0

# Disable security features for development
ENV xpack.security.enabled=false
ENV discovery.type=single-node
ENV "ES_JAVA_OPTS=-Xms512m -Xmx512m"

EXPOSE 9200 9300

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:9200/_cluster/health || exit 1
```

**Note**: Elasticsearch requires significant memory. Free tier may not be sufficient.

### Step 2: Create Dockerfile for Full Snowstorm

Create `snowstorm/Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Download Full Snowstorm
# Check https://github.com/IHTSDO/snowstorm/releases for latest version
ARG SNOWSTORM_VERSION=8.12.0
RUN wget -O snowstorm.jar \
    https://github.com/IHTSDO/snowstorm/releases/download/v${SNOWSTORM_VERSION}/snowstorm-${SNOWSTORM_VERSION}.jar

# Expose Snowstorm port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run Snowstorm
ENTRYPOINT ["java", "-jar", "snowstorm.jar"]
```

### Step 3: Create application.yml for Snowstorm

Create `snowstorm/application.yml`:

```yaml
server:
  port: 8080

spring:
  data:
    elasticsearch:
      cluster-name: ${ELASTICSEARCH_CLUSTER_NAME:snowstorm-cluster}
      cluster-nodes: ${ELASTICSEARCH_HOST:localhost}:${ELASTICSEARCH_PORT:9200}
      repositories:
        enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  health:
    elasticsearch:
      enabled: true

# Snowstorm configuration
snowstorm:
  import:
    rf2:
      path: ${SNOMED_IMPORT_PATH:/app/imports}
```

### Step 4: Update render.yaml

Add to `render.yaml`:

```yaml
services:
  # ... existing services ...

  # Elasticsearch Service (if deploying separately)
  - type: web
    name: elasticsearch
    env: docker
    region: frankfurt
    plan: starter  # Requires paid plan for sufficient memory
    dockerfilePath: ./elasticsearch/Dockerfile
    dockerContext: ./elasticsearch
    healthCheckPath: /_cluster/health
    envVars:
      - key: xpack.security.enabled
        value: false
      - key: discovery.type
        value: single-node
      - key: ES_JAVA_OPTS
        value: -Xms512m -Xmx512m

  # Full Snowstorm Service
  - type: web
    name: snowstorm
    env: docker
    region: frankfurt
    plan: starter  # Requires paid plan for sufficient memory
    dockerfilePath: ./snowstorm/Dockerfile
    dockerContext: ./snowstorm
    healthCheckPath: /health
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: production
      - key: SERVER_PORT
        value: 8080
      - key: ELASTICSEARCH_CLUSTER_NAME
        value: snowstorm-cluster
      - key: ELASTICSEARCH_HOST
        fromService:
          type: web
          name: elasticsearch
          property: host
      - key: ELASTICSEARCH_PORT
        value: 9200
      - key: SNOMED_IMPORT_PATH
        value: /app/imports
```

### Step 5: Deploy to Render

1. **Push to GitHub**
2. **Deploy via Blueprint** or manually create services
3. **Wait for services to start** (Elasticsearch may take 2-3 minutes)

---

## Importing Code Systems {#importing-code-systems}

### Method 1: Import via Snowstorm API (Recommended)

Once Snowstorm is deployed, import SNOMED CT releases via API:

```bash
# 1. Create import job
curl -X POST \
  https://snowstorm-lite.onrender.com/imports \
  -H "Content-Type: application/json" \
  -d '{
    "branchPath": "MAIN",
    "createCodeSystemVersion": true,
    "type": "SNAPSHOT"
  }'

# 2. Upload RF2 files
# Note: This requires the RF2 files to be accessible
# You may need to host them separately or use Snowstorm's import API
```

### Method 2: Pre-load in Docker Image

For Snowstorm Lite, you can pre-load a code system:

1. **Download SNOMED CT RF2 Release** (from SNOMED International or your organization)

2. **Create import script** (`snowstorm-lite/import.sh`):

```bash
#!/bin/sh
# Wait for Snowstorm to start
sleep 30

# Import code system
# This is a simplified example - actual import requires RF2 files
echo "Code system import would happen here"
```

3. **Update Dockerfile**:

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl wget

# Download Snowstorm Lite
ARG SNOWSTORM_VERSION=8.12.0
RUN wget -O snowstorm.jar \
    https://github.com/IHTSDO/snowstorm-lite/releases/download/v${SNOWSTORM_VERSION}/snowstorm-lite-${SNOWSTORM_VERSION}.jar

# Copy import script
COPY import.sh /app/import.sh
RUN chmod +x /app/import.sh

EXPOSE 8080

# Start Snowstorm and import
CMD ["sh", "-c", "java -jar snowstorm.jar & /app/import.sh && wait"]
```

### Method 3: Use Snowstorm's Import API

For full Snowstorm, use the import API:

```bash
# 1. Create import job
IMPORT_ID=$(curl -X POST \
  https://snowstorm.onrender.com/imports \
  -H "Content-Type: application/json" \
  -d '{
    "branchPath": "MAIN",
    "createCodeSystemVersion": true,
    "type": "SNAPSHOT"
  }' | jq -r '.id')

# 2. Upload RF2 files (requires file hosting)
# Note: RF2 files are large (several GB) and may need to be hosted separately
```

**Important**: RF2 files are very large (several GB). Consider:
- Using a file hosting service (S3, Google Cloud Storage)
- Importing only necessary subsets
- Using Snowstorm Lite with pre-loaded data

---

## Integration with CodeSystemFactory {#integration}

### Step 1: Update application.yml

Update `backend/src/main/resources/application.yml`:

```yaml
fhir:
  server:
    # Point to your deployed Snowstorm instance
    url: ${FHIR_SERVER_URL:https://snowstorm-lite.onrender.com/fhir}
    # Or for full Snowstorm:
    # url: ${FHIR_SERVER_URL:https://snowstorm.onrender.com/fhir}
```

### Step 2: Update render.yaml

Update the backend service in `render.yaml`:

```yaml
services:
  - type: web
    name: codesys-backend
    # ... existing config ...
    envVars:
      # ... existing vars ...
      - key: FHIR_SERVER_URL
        fromService:
          type: web
          name: snowstorm-lite  # Or 'snowstorm' for full version
          property: host
        # This will be set to: https://snowstorm-lite.onrender.com/fhir
```

### Step 3: Verify Integration

Test the integration:

```bash
# Test Snowstorm health
curl https://snowstorm-lite.onrender.com/health

# Test FHIR endpoint
curl https://snowstorm-lite.onrender.com/fhir/CodeSystem?url=http://snomed.info/sct

# Test from your backend
curl https://codesys-backend.onrender.com/api/terms/match \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"terms": ["Diabetes"]}'
```

---

## Troubleshooting {#troubleshooting}

### Issue: Snowstorm fails to start

**Symptoms**: Service shows "Unhealthy" or crashes

**Solutions**:
1. Check logs in Render dashboard
2. Verify memory allocation (may need paid plan)
3. Check Elasticsearch connection (for full Snowstorm)
4. Verify health check endpoint: `/health`

### Issue: Code system not found

**Symptoms**: API returns 404 or empty results

**Solutions**:
1. Verify code system was imported
2. Check branch path (usually "MAIN")
3. Verify Snowstorm has code system loaded
4. Check Snowstorm logs for import errors

### Issue: Out of memory errors

**Symptoms**: Service crashes or becomes unresponsive

**Solutions**:
1. Upgrade to Render paid plan (more memory)
2. Use Snowstorm Lite instead of full Snowstorm
3. Reduce Elasticsearch heap size
4. Import smaller code system subset

### Issue: Slow performance

**Symptoms**: API requests take a long time

**Solutions**:
1. Upgrade to paid plan (better CPU/memory)
2. Optimize Elasticsearch settings
3. Use Snowstorm Lite for smaller deployments
4. Consider caching frequently accessed concepts

### Issue: Cannot connect to Elasticsearch

**Symptoms**: Snowstorm logs show connection errors

**Solutions**:
1. Verify Elasticsearch service is running
2. Check `ELASTICSEARCH_HOST` and `ELASTICSEARCH_PORT` environment variables
3. Verify network connectivity between services
4. Check Elasticsearch health: `curl https://elasticsearch.onrender.com/_cluster/health`

---

## Cost Considerations

### Free Tier Limitations

- **Memory**: 512MB may not be sufficient for full Snowstorm
- **Disk**: 512MB may not hold large code systems
- **CPU**: Limited CPU may cause slow performance

### Recommended Plans

- **Snowstorm Lite**: Free tier may work for small deployments
- **Full Snowstorm**: Requires at least "Starter" plan ($7/month)
- **Elasticsearch**: Requires at least "Starter" plan ($7/month)

**Total Estimated Cost**:
- Snowstorm Lite only: **$0-7/month**
- Full Snowstorm + Elasticsearch: **$14-25/month**

---

## Alternative: Use External Snowstorm

If deploying Snowstorm to Render is too complex or expensive, consider:

1. **Use existing Snowstorm instance**: `snowstorm-training.snomedtools.org`
2. **Deploy to other platforms**: Railway, Fly.io, DigitalOcean
3. **Use managed Snowstorm**: Some organizations provide managed instances
4. **Local deployment**: Run Snowstorm locally for development

---

## Summary

### Quick Start: Snowstorm Lite

1. Create `snowstorm-lite/Dockerfile`
2. Add service to `render.yaml`
3. Deploy via Render Blueprint
4. Update `FHIR_SERVER_URL` in backend config
5. Test integration

### Full Snowstorm Deployment

1. Deploy Elasticsearch service
2. Deploy Snowstorm service
3. Configure connection between services
4. Import code system
5. Update backend configuration
6. Test integration

**Note**: Full Snowstorm deployment is more complex and resource-intensive. Consider starting with Snowstorm Lite for development and testing.

---

**Last Updated**: 2024
**Application Version**: 0.0.2-SNAPSHOT

