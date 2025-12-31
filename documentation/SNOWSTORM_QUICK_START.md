# Snowstorm Quick Start Guide for Render.com

## Quick Decision Guide

**Choose Snowstorm Lite if:**
- ✅ You want a simple setup
- ✅ You're on Render free tier
- ✅ You need basic SNOMED CT functionality
- ✅ You're doing development/testing

**Choose Full Snowstorm if:**
- ✅ You need production-grade performance
- ✅ You can afford paid Render plans ($14+/month)
- ✅ You need advanced features
- ✅ You're deploying for production use

---

## Quick Start: Snowstorm Lite (5 minutes)

### Step 1: Add Files to Your Repository

The following files are already created:
- `snowstorm-lite/Dockerfile` ✅
- `render.yaml.example` (shows how to add Snowstorm Lite) ✅

### Step 2: Update render.yaml

Add this to your existing `render.yaml`:

```yaml
  # Snowstorm Lite Service
  - type: web
    name: snowstorm-lite
    env: docker
    region: frankfurt
    plan: free
    dockerfilePath: ./snowstorm-lite/Dockerfile
    dockerContext: ./snowstorm-lite
    healthCheckPath: /health
    envVars:
      - key: SERVER_PORT
        value: 8080
```

### Step 3: Update Backend Configuration

In your `render.yaml`, update the backend's `FHIR_SERVER_URL`:

```yaml
  - type: web
    name: codesys-backend
    # ... other config ...
    envVars:
      # ... other vars ...
      - key: FHIR_SERVER_URL
        fromService:
          type: web
          name: snowstorm-lite
          property: host
        # This becomes: https://snowstorm-lite.onrender.com/fhir
```

### Step 4: Deploy

```bash
git add snowstorm-lite/ render.yaml
git commit -m "Add Snowstorm Lite deployment"
git push
```

Render will automatically deploy all services via Blueprint.

### Step 5: Verify

```bash
# Check Snowstorm health
curl https://snowstorm-lite.onrender.com/health

# Test from your backend
curl https://codesys-backend.onrender.com/api/terms/match \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"terms": ["Diabetes"]}'
```

---

## Quick Start: Full Snowstorm (15 minutes)

### Prerequisites
- Render paid plan (Starter or higher)
- ~$14-25/month for both Elasticsearch and Snowstorm

### Step 1: Add Files

Files already created:
- `snowstorm/Dockerfile` ✅
- `snowstorm/application.yml` ✅
- `elasticsearch/Dockerfile` ✅
- `render-full-snowstorm.yaml.example` ✅

### Step 2: Update render.yaml

Copy configuration from `render-full-snowstorm.yaml.example` or add:

```yaml
  # Elasticsearch Service
  - type: web
    name: elasticsearch
    env: docker
    region: frankfurt
    plan: starter  # Paid plan required
    dockerfilePath: ./elasticsearch/Dockerfile
    dockerContext: ./elasticsearch
    healthCheckPath: /_cluster/health
    envVars:
      - key: xpack.security.enabled
        value: false
      - key: discovery.type
        value: single-node

  # Full Snowstorm Service
  - type: web
    name: snowstorm
    env: docker
    region: frankfurt
    plan: starter  # Paid plan required
    dockerfilePath: ./snowstorm/Dockerfile
    dockerContext: ./snowstorm
    healthCheckPath: /health
    envVars:
      - key: ELASTICSEARCH_HOST
        fromService:
          type: web
          name: elasticsearch
          property: host
      - key: ELASTICSEARCH_PORT
        value: 9200
```

### Step 3: Deploy

```bash
git add snowstorm/ elasticsearch/ render.yaml
git commit -m "Add full Snowstorm deployment"
git push
```

---

## Importing Code Systems

### Option 1: Use Snowstorm's Default Data

Snowstorm Lite comes with some default data. Test it first:

```bash
curl https://snowstorm-lite.onrender.com/fhir/CodeSystem?url=http://snomed.info/sct
```

### Option 2: Import via API

```bash
# Create import job
curl -X POST https://snowstorm-lite.onrender.com/imports \
  -H "Content-Type: application/json" \
  -d '{
    "branchPath": "MAIN",
    "createCodeSystemVersion": true,
    "type": "SNAPSHOT"
  }'
```

**Note**: RF2 files are large (several GB). You'll need to host them separately or use Snowstorm's import mechanisms.

---

## Troubleshooting

### Service won't start
- Check Render logs
- Verify Dockerfile syntax
- Check memory limits (may need paid plan)

### Can't connect to Snowstorm
- Verify service is running: `curl https://snowstorm-lite.onrender.com/health`
- Check `FHIR_SERVER_URL` in backend config
- Verify service names match in `render.yaml`

### Out of memory
- Upgrade to paid plan
- Reduce Java heap size in Dockerfile
- Use Snowstorm Lite instead of full Snowstorm

---

## Cost Estimate

| Service | Free Tier | Starter Plan |
|---------|-----------|--------------|
| Snowstorm Lite | ✅ Works | ✅ Better |
| Full Snowstorm | ❌ No | ✅ Required |
| Elasticsearch | ❌ No | ✅ Required |
| **Total** | **$0** | **$14-25/month** |

---

## Next Steps

1. ✅ Deploy Snowstorm Lite (recommended to start)
2. ✅ Test integration with your backend
3. ✅ Import code systems if needed
4. ✅ Monitor performance and upgrade if needed

For detailed information, see `SNOWSTORM_DEPLOYMENT_RENDER.md`.

---

**Last Updated**: 2024

