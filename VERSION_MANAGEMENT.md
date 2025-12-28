# Version Management Solution

## Overview

This document describes the version management solution for the Code System Factory application, including how versions are displayed in the UI and how to increment them.

## Solution Design

### 1. Single Source of Truth
- **Backend**: Version stored in `pom.xml` (Maven project version)
- **Frontend**: Version stored in `package.json`
- **API Version**: Exposed via backend endpoint `/api/version`

### 2. Version Format
Use **Semantic Versioning** (SemVer): `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes (incompatible API changes)
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes (backward compatible)

Examples:
- `1.0.0` - Initial release
- `1.0.1` - Bug fix
- `1.1.0` - New feature
- `2.0.0` - Breaking change

### 3. Implementation

#### Backend
1. Read version from `pom.xml` at build time
2. Expose via `/api/version` endpoint
3. Include in OpenAPI documentation

#### Frontend
1. Read version from `package.json` at build time
2. Fetch backend version on app load
3. Display in UI footer/header

### 4. Version Display Location
- **Footer**: Small, unobtrusive version display
- **Format**: `v1.0.0` or `Frontend: v1.0.0 | Backend: v1.0.0`

## Version Increment Guidelines

### When to Increment MAJOR (X.0.0)
- Breaking API changes
- Incompatible database schema changes
- Major architectural changes
- Removal of features

### When to Increment MINOR (0.X.0)
- New features added
- New API endpoints
- New UI components
- Backward-compatible enhancements
- New export formats

### When to Increment PATCH (0.0.X)
- Bug fixes
- Security patches
- Performance improvements
- Documentation updates
- Code refactoring (no functional changes)

## Version Synchronization

### Option 1: Independent Versions (Recommended)
- Frontend and backend can have different versions
- Useful when frontend/backend are deployed separately
- More flexible for independent releases

### Option 2: Synchronized Versions
- Keep frontend and backend versions in sync
- Simpler for coordinated releases
- Requires manual synchronization

**Recommendation**: Use Option 1 (Independent Versions) for flexibility.

## Implementation

### Backend Version Endpoint
- **Endpoint**: `GET /api/version`
- **Response**: `{"version": "0.0.1-SNAPSHOT", "application": "codesys-backend"}`
- **Source**: Reads from `pom.xml` via Maven build info

### Frontend Version Display
- **Location**: Footer of the application
- **Format**: `Frontend: v0.0.1 | Backend: v0.0.1`
- **Source**: Frontend version from `package.json`, backend version fetched from API

## How to Increment Versions

### Backend Version

1. **Edit `backend/pom.xml`**:
   ```xml
   <version>0.0.1-SNAPSHOT</version>
   ```
   Change to:
   ```xml
   <version>1.0.0</version>  <!-- or 1.0.1, 1.1.0, 2.0.0, etc. -->
   ```

2. **Rebuild the backend**:
   ```bash
   cd backend
   mvn clean package
   ```

3. **The version will automatically be available** at `/api/version`

### Frontend Version

1. **Edit `frontend/package.json`**:
   ```json
   {
     "version": "0.0.1"
   }
   ```
   Change to:
   ```json
   {
     "version": "1.0.0"  <!-- or 1.0.1, 1.1.0, 2.0.0, etc. -->
   }
   ```

2. **Rebuild the frontend**:
   ```bash
   cd frontend
   npm run build
   ```

3. **The version will automatically be displayed** in the UI footer

### Version Increment Workflow

**For a bug fix (PATCH):**
```bash
# Backend
# Edit backend/pom.xml: 0.0.1-SNAPSHOT → 0.0.2
cd backend && mvn clean package

# Frontend  
# Edit frontend/package.json: 0.0.1 → 0.0.2
cd frontend && npm run build
```

**For a new feature (MINOR):**
```bash
# Backend
# Edit backend/pom.xml: 0.0.1-SNAPSHOT → 0.1.0
cd backend && mvn clean package

# Frontend
# Edit frontend/package.json: 0.0.1 → 0.1.0
cd frontend && npm run build
```

**For a breaking change (MAJOR):**
```bash
# Backend
# Edit backend/pom.xml: 0.0.1-SNAPSHOT → 1.0.0
cd backend && mvn clean package

# Frontend
# Edit frontend/package.json: 0.0.1 → 1.0.0
cd frontend && npm run build
```

### Removing -SNAPSHOT Suffix

When releasing a stable version, remove the `-SNAPSHOT` suffix:

**Before release:**
- `0.0.1-SNAPSHOT` (development)

**After release:**
- `0.0.1` (stable release)
- `0.0.2-SNAPSHOT` (next development version)

## Example Version Display

```
┌─────────────────────────────────────────┐
│  SNOMED Code System Builder             │
│  ...                                    │
│                                         │
│  [Application content]                  │
│                                         │
│  ─────────────────────────────────────  │
│  v1.0.0 | Backend API v1.0.0           │
└─────────────────────────────────────────┘
```

