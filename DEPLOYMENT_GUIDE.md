# Deployment Guide - Free Platform Options

This guide provides recommendations for deploying both the backend (Spring Boot) and frontend (React/Vite) to free hosting platforms.

## Recommended Platform Combinations

### 🏆 **Option 1: Railway (Backend) + Vercel (Frontend)** ⭐ **RECOMMENDED**

**Why this combination:**
- **Railway**: Excellent for Spring Boot, simple deployment, good free tier
- **Vercel**: Best-in-class for React apps, automatic deployments, excellent performance
- Both have generous free tiers and are easy to set up

**Free Tier Limits:**
- **Railway**: $5/month credit (enough for small apps), 500 hours/month
- **Vercel**: Unlimited deployments, 100GB bandwidth/month, excellent performance

---

### 🥈 **Option 2: Render (Both Backend & Frontend)**

**Why this option:**
- Single platform for both services
- Simple deployment process
- Good free tier

**Free Tier Limits:**
- **Render**: 750 hours/month per service, sleeps after 15 min inactivity
- Auto-wakes on first request (may take 30-60 seconds)

---

### 🥉 **Option 3: Fly.io (Backend) + Netlify (Frontend)**

**Why this option:**
- **Fly.io**: Great for containerized apps, good free tier
- **Netlify**: Excellent for static sites, good free tier

**Free Tier Limits:**
- **Fly.io**: 3 shared-cpu-1x VMs, 3GB persistent volumes
- **Netlify**: 100GB bandwidth/month, 300 build minutes/month

---

## Detailed Setup Instructions

## Option 1: Railway + Vercel (Recommended)

### Backend on Railway

1. **Sign up**: https://railway.app (use GitHub login)

2. **Create new project**:
   - Click "New Project"
   - Select "Deploy from GitHub repo"
   - Choose your repository

3. **Configure service**:
   - Railway will auto-detect Spring Boot
   - Add environment variables:
     ```
     OPENAI_API_KEY=your_key_here
     SPRING_PROFILES_ACTIVE=default
     PORT=8080
     ```
   - Railway will automatically:
     - Build with Maven
     - Run `mvn spring-boot:run` or detect JAR
     - Expose port 8080

4. **Get backend URL**:
   - Railway provides a URL like: `https://your-app.up.railway.app`
   - Note this URL for frontend configuration

### Frontend on Vercel

1. **Sign up**: https://vercel.com (use GitHub login)

2. **Create new project**:
   - Click "Add New Project"
   - Import your GitHub repository
   - Select `frontend` folder as root directory

3. **Configure build settings**:
   - **Framework Preset**: Vite
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
   - **Install Command**: `npm install`

4. **Add environment variables**:
   ```
   VITE_API_URL=https://your-app.up.railway.app
   ```

5. **Update frontend code** (see below)

6. **Deploy**: Vercel will automatically deploy on every push to main branch

---

## Option 2: Render (Both Services)

### Backend on Render

1. **Sign up**: https://render.com (use GitHub login)

2. **Create Web Service**:
   - Click "New" → "Web Service"
   - Connect your GitHub repository
   - Settings:
     - **Name**: `codesys-backend`
     - **Environment**: Java
     - **Build Command**: `cd backend && mvn clean package -DskipTests`
     - **Start Command**: `cd backend && java -jar target/codesys-backend-0.0.1-SNAPSHOT.jar`
     - **Root Directory**: `backend`

3. **Environment Variables**:
   ```
   OPENAI_API_KEY=your_key_here
   SPRING_PROFILES_ACTIVE=default
   PORT=8080
   ```

4. **Get backend URL**: `https://codesys-backend.onrender.com`

### Frontend on Render

1. **Create Static Site**:
   - Click "New" → "Static Site"
   - Connect your GitHub repository
   - Settings:
     - **Name**: `codesys-frontend`
     - **Root Directory**: `frontend`
     - **Build Command**: `npm install && npm run build`
     - **Publish Directory**: `dist`

2. **Environment Variables**:
   ```
   VITE_API_URL=https://codesys-backend.onrender.com
   ```

3. **Deploy**: Render will build and deploy automatically

---

## Option 3: Fly.io + Netlify

### Backend on Fly.io

1. **Install Fly CLI**: `curl -L https://fly.io/install.sh | sh`

2. **Login**: `fly auth login`

3. **Create app**: `cd backend && fly launch`

4. **Create `fly.toml`** (see below)

5. **Deploy**: `fly deploy`

### Frontend on Netlify

1. **Sign up**: https://netlify.com (use GitHub login)

2. **Create site**:
   - Click "Add new site" → "Import an existing project"
   - Connect GitHub repository
   - Settings:
     - **Base directory**: `frontend`
     - **Build command**: `npm run build`
     - **Publish directory**: `dist`

3. **Environment Variables**:
   ```
   VITE_API_URL=https://your-app.fly.dev
   ```

---

## Required Code Changes

### 1. Update Frontend API Configuration

**File**: `frontend/vite.config.ts`

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.VITE_API_URL || 'http://localhost:8080'
    }
  },
  define: {
    // Make API URL available at build time
    'import.meta.env.VITE_API_URL': JSON.stringify(process.env.VITE_API_URL || 'http://localhost:8080')
  }
})
```

**File**: `frontend/src/ui/App.tsx` (or create `frontend/src/config.ts`)

```typescript
// Add at top of App.tsx or create config.ts
const API_BASE_URL = import.meta.env.VITE_API_URL || '/api'

// Update all fetch calls from:
fetch('/api/terms/match', ...)
// To:
fetch(`${API_BASE_URL}/terms/match`, ...)
```

Or create a helper:

**File**: `frontend/src/api.ts` (new file)

```typescript
const API_BASE = import.meta.env.VITE_API_URL || '/api'

export const api = {
  matchTerms: (terms: string[]) => 
    fetch(`${API_BASE}/terms/match`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ terms })
    }).then(r => r.json()),
  
  getRecommendations: (unmatchedTerms: string[], matchedSnomedIds: string[], context: string) =>
    fetch(`${API_BASE}/ai/recommend`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ unmatchedTerms, matchedSnomedIds, context })
    }).then(r => r.json()),
  
  buildCodeSystem: (data: any) =>
    fetch(`${API_BASE}/codesystems/build`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    }).then(r => r.json()),
  
  exportCodeSystem: (codeSystem: any, format: string) =>
    fetch(`${API_BASE}/codesystems/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ codeSystem, format })
    }).then(r => r.blob())
}
```

### 2. Update Backend CORS Configuration

**File**: `backend/src/main/java/com/example/codesys/config/CorsConfig.java` (new file)

```java
package com.example.codesys.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow frontend domain (set via environment variable or allow all in dev)
        String allowedOrigin = System.getenv("FRONTEND_URL");
        if (allowedOrigin == null || allowedOrigin.isEmpty()) {
            allowedOrigin = "*"; // Allow all in development
        }
        
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

### 3. Create Proper Dockerfile for Backend (if using Docker)

**File**: `backend/Dockerfile`

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/codesys-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=default
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 4. Create Dockerfile for Frontend (if using Docker)

**File**: `frontend/Dockerfile`

```dockerfile
# Build stage
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**File**: `frontend/nginx.conf`

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass ${VITE_API_URL};
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## Platform-Specific Configuration Files

### Railway Configuration

**File**: `railway.json` (optional, Railway auto-detects)

```json
{
  "$schema": "https://railway.app/railway.schema.json",
  "build": {
    "builder": "NIXPACKS"
  },
  "deploy": {
    "startCommand": "java -jar target/codesys-backend-0.0.1-SNAPSHOT.jar",
    "restartPolicyType": "ON_FAILURE",
    "restartPolicyMaxRetries": 10
  }
}
```

### Render Configuration

**File**: `render.yaml` (optional)

```yaml
services:
  - type: web
    name: codesys-backend
    env: java
    buildCommand: cd backend && mvn clean package -DskipTests
    startCommand: cd backend && java -jar target/codesys-backend-0.0.1-SNAPSHOT.jar
    envVars:
      - key: OPENAI_API_KEY
        sync: false
      - key: SPRING_PROFILES_ACTIVE
        value: default
      - key: PORT
        value: 8080

  - type: web
    name: codesys-frontend
    env: static
    buildCommand: cd frontend && npm install && npm run build
    staticPublishPath: frontend/dist
    envVars:
      - key: VITE_API_URL
        fromService:
          name: codesys-backend
          type: web
          property: host
```

### Fly.io Configuration

**File**: `backend/fly.toml`

```toml
app = "codesys-backend"
primary_region = "iad"

[build]
  builder = "paketobuildpacks/builder:base"

[env]
  PORT = "8080"
  SPRING_PROFILES_ACTIVE = "default"

[[services]]
  internal_port = 8080
  protocol = "tcp"

  [[services.ports]]
    port = 80
    handlers = ["http"]
    force_https = true

  [[services.ports]]
    port = 443
    handlers = ["tls", "http"]
```

---

## Environment Variables Summary

### Backend Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `OPENAI_API_KEY` | OpenAI API key for AI features | No* | - |
| `SPRING_PROFILES_ACTIVE` | Spring profile | No | `default` |
| `PORT` | Server port | No | `8080` |
| `FHIR_SERVER_URL` | SNOMED CT API URL | No | `https://snowstorm-training.snomedtools.org/fhir` |
| `FRONTEND_URL` | Frontend URL for CORS | No | `*` (all) |

*Required only if `ai.enabled=true`

### Frontend Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `VITE_API_URL` | Backend API URL | Yes | `http://localhost:8080` |

---

## Deployment Checklist

### Before Deployment

- [ ] Update frontend to use environment variable for API URL
- [ ] Add CORS configuration to backend
- [ ] Set up environment variables on hosting platform
- [ ] Test API endpoints are accessible
- [ ] Verify OpenAI API key is set (if using AI features)

### After Deployment

- [ ] Test frontend can connect to backend
- [ ] Verify CORS is working
- [ ] Test term matching functionality
- [ ] Test AI recommendations (if enabled)
- [ ] Check logs for any errors
- [ ] Verify HTTPS is enabled (most platforms do this automatically)

---

## Troubleshooting

### Backend Issues

**Problem**: Backend won't start
- Check Java version (needs Java 17+)
- Verify Maven build succeeds locally
- Check environment variables are set correctly
- Review platform logs

**Problem**: CORS errors
- Verify `FRONTEND_URL` environment variable is set
- Check CORS configuration allows your frontend domain
- Ensure backend allows OPTIONS requests

### Frontend Issues

**Problem**: Can't connect to backend
- Verify `VITE_API_URL` is set correctly
- Check backend URL is accessible (try in browser)
- Verify CORS is configured on backend
- Check browser console for errors

**Problem**: Build fails
- Ensure Node.js version is compatible (18+)
- Check `package.json` dependencies
- Verify `fix-rollup.cjs` runs successfully
- Review build logs

---

## Cost Comparison

| Platform | Free Tier | Paid Tier (if needed) |
|----------|-----------|----------------------|
| **Railway** | $5/month credit | $5-20/month |
| **Vercel** | Unlimited (generous) | $20/month (Pro) |
| **Render** | 750 hrs/month | $7/month per service |
| **Fly.io** | 3 VMs, 3GB storage | $1.94/month per VM |
| **Netlify** | 100GB bandwidth | $19/month (Pro) |

**Estimated Monthly Cost (if free tier exceeded):**
- **Option 1 (Railway + Vercel)**: $0-5/month (usually stays free)
- **Option 2 (Render)**: $0-14/month (usually stays free)
- **Option 3 (Fly.io + Netlify)**: $0-20/month (usually stays free)

---

## Recommendation

**For this project, I recommend Option 1: Railway + Vercel**

**Reasons:**
1. **Railway** is excellent for Spring Boot apps - auto-detects, simple setup
2. **Vercel** is the best platform for React/Vite apps - excellent performance, automatic deployments
3. Both have very generous free tiers
4. Easy to set up and maintain
5. Great developer experience

**Next Steps:**
1. Deploy backend to Railway
2. Deploy frontend to Vercel
3. Configure environment variables
4. Test the deployment

Would you like me to help you set up the code changes needed for deployment?

