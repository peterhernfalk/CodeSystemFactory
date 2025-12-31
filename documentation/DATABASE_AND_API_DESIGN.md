# Database Structure and API Design

## Database Structure

### Entity Relationship Diagram

```
┌─────────────────────┐
│   code_systems      │
├─────────────────────┤
│ id (PK)             │
│ name                │
│ version             │
│ description         │
│ publisher           │
│ contact             │
│ status              │
│ created_at          │
│ updated_at          │
└─────────────────────┘
         │
         │ 1:N
         │
┌─────────────────────┐
│ code_system_items   │
├─────────────────────┤
│ id (PK)             │
│ code_system_id (FK) │
│ code                │
│ display             │
│ definition          │
│ relations_json      │
│ use_cases_json      │
│ motivation          │
│ source              │
│ parent_code         │
│ created_at          │
└─────────────────────┘
```

### Table Definitions

#### 1. `code_systems` Table

**Purpose**: Stores code system metadata and configuration.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| `name` | VARCHAR(255) | NOT NULL | Code system name |
| `version` | VARCHAR(50) | NOT NULL | Version identifier |
| `description` | TEXT | NULL | Description of the code system |
| `publisher` | VARCHAR(255) | NULL | Publisher name |
| `contact` | VARCHAR(255) | NULL | Contact information |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'DRAFT' | Status: DRAFT, PUBLISHED, ARCHIVED |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE | Last update timestamp |

**Indexes**:
- UNIQUE INDEX `idx_code_system_name_version` ON (`name`, `version`)
- INDEX `idx_code_system_status` ON (`status`)
- INDEX `idx_code_system_created_at` ON (`created_at`)

**Constraints**:
- Unique constraint on (`name`, `version`) combination

#### 2. `code_system_items` Table

**Purpose**: Stores individual codes/concepts within a code system.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| `code_system_id` | BIGINT | NOT NULL, FOREIGN KEY | Reference to code_systems.id |
| `code` | VARCHAR(255) | NOT NULL | Code identifier (SNOMED ID or custom) |
| `display` | VARCHAR(500) | NOT NULL | Display name/preferred term |
| `definition` | TEXT | NULL | Definition text |
| `relations_json` | TEXT | NULL | JSON array of relations |
| `use_cases_json` | TEXT | NULL | JSON array of use cases |
| `motivation` | TEXT | NULL | Motivation for inclusion |
| `source` | VARCHAR(20) | NOT NULL | Source: SNOMED_MATCH, AI_SUGGESTION, MANUAL |
| `parent_code` | VARCHAR(255) | NULL | Parent code for hierarchy |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

**Indexes**:
- INDEX `idx_item_code_system_id` ON (`code_system_id`)
- INDEX `idx_item_code` ON (`code`)
- INDEX `idx_item_parent_code` ON (`parent_code`)
- UNIQUE INDEX `idx_item_code_system_code` ON (`code_system_id`, `code`)

**Foreign Keys**:
- `FK_code_system_items_code_system` FOREIGN KEY (`code_system_id`) REFERENCES `code_systems`(`id`) ON DELETE CASCADE

**Constraints**:
- Unique constraint on (`code_system_id`, `code`) combination

### Database Schema SQL

```sql
-- Code Systems Table
CREATE TABLE code_systems (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    description TEXT,
    publisher VARCHAR(255),
    contact VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_code_system_name_version UNIQUE (name, version),
    INDEX idx_code_system_status (status),
    INDEX idx_code_system_created_at (created_at)
);

-- Code System Items Table
CREATE TABLE code_system_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code_system_id BIGINT NOT NULL,
    code VARCHAR(255) NOT NULL,
    display VARCHAR(500) NOT NULL,
    definition TEXT,
    relations_json TEXT,
    use_cases_json TEXT,
    motivation TEXT,
    source VARCHAR(20) NOT NULL,
    parent_code VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_item_code_system_code UNIQUE (code_system_id, code),
    CONSTRAINT FK_code_system_items_code_system 
        FOREIGN KEY (code_system_id) 
        REFERENCES code_systems(id) 
        ON DELETE CASCADE,
    INDEX idx_item_code_system_id (code_system_id),
    INDEX idx_item_code (code),
    INDEX idx_item_parent_code (parent_code)
);
```

## API Endpoints

### Base URL
```
http://localhost:8080/api
```

### Endpoint Categories

1. **Code System Creation** - APIs for creating new code systems
2. **Code System Retrieval** - APIs for fetching existing code systems
3. **Code System Management** - APIs for updating and deleting code systems
4. **Code System Query** - APIs for searching and filtering code systems

---

## 1. Code System Creation APIs

### 1.1 Create Code System (Direct)

**Endpoint**: `POST /api/codesystems`

**Description**: Create a code system by providing complete item definitions.

**Request Body**:
```json
{
  "name": "Swedish Cardiology Terms",
  "version": "1.0.0",
  "description": "Code system for Swedish cardiology terminology",
  "publisher": "Swedish Health Authority",
  "contact": "contact@example.se",
  "items": [
    {
      "code": "73211009",
      "display": "Diabetes mellitus",
      "definition": "A metabolic disorder characterized by hyperglycemia",
      "relations": ["is-a: Disorder", "associated-with: Cardiovascular disease"],
      "useCases": ["Clinical documentation", "Decision support"],
      "motivation": "Core concept in cardiology",
      "source": "SNOMED_MATCH",
      "parentCode": null
    }
  ]
}
```

**Response**: `201 Created`
```json
{
  "id": 1,
  "name": "Swedish Cardiology Terms",
  "version": "1.0.0",
  "description": "Code system for Swedish cardiology terminology",
  "publisher": "Swedish Health Authority",
  "contact": "contact@example.se",
  "status": "DRAFT",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "items": [
    {
      "id": 1,
      "code": "73211009",
      "display": "Diabetes mellitus",
      "definition": "A metabolic disorder characterized by hyperglycemia",
      "relations": ["is-a: Disorder", "associated-with: Cardiovascular disease"],
      "useCases": ["Clinical documentation", "Decision support"],
      "motivation": "Core concept in cardiology",
      "source": "SNOMED_MATCH",
      "parentCode": null
    }
  ]
}
```

### 1.2 Create Code System from Matches

**Endpoint**: `POST /api/codesystems/from-matches`

**Description**: Create a code system from term matches and AI definitions (convenience method).

**Request Body**:
```json
{
  "name": "Swedish Cardiology Terms",
  "version": "1.0.0",
  "description": "Code system for Swedish cardiology terminology",
  "publisher": "Swedish Health Authority",
  "contact": "contact@example.se",
  "matches": [
    {
      "inputTerm": "Diabetes",
      "snomedId": "73211009",
      "preferredTerm": "Diabetes mellitus",
      "fsn": "Diabetes mellitus (disorder)",
      "definition": "A metabolic disorder characterized by hyperglycemia",
      "relations": ["is-a: Disorder"],
      "useCases": ["Clinical documentation"],
      "motivation": "Selected due to lexical similarity"
    }
  ]
}
```

**Response**: `201 Created` (same structure as 1.1)

---

## 2. Code System Retrieval APIs

### 2.1 Get Code System by ID

**Endpoint**: `GET /api/codesystems/{id}`

**Description**: Retrieve a specific code system with all its items.

**Path Parameters**:
- `id` (integer, required): Code system ID

**Response**: `200 OK`
```json
{
  "id": 1,
  "name": "Swedish Cardiology Terms",
  "version": "1.0.0",
  "description": "Code system for Swedish cardiology terminology",
  "publisher": "Swedish Health Authority",
  "contact": "contact@example.se",
  "status": "DRAFT",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "items": [...]
}
```

**Error Responses**:
- `404 Not Found`: Code system not found

### 2.2 List All Code Systems

**Endpoint**: `GET /api/codesystems`

**Description**: Retrieve a list of all code systems (without items for performance).

**Query Parameters**:
- `status` (string, optional): Filter by status (DRAFT, PUBLISHED, ARCHIVED)
- `page` (integer, optional): Page number (default: 0)
- `size` (integer, optional): Page size (default: 20, max: 100)
- `sort` (string, optional): Sort field (default: createdAt)
- `direction` (string, optional): Sort direction (ASC, DESC, default: DESC)

**Response**: `200 OK`
```json
{
  "content": [
    {
      "id": 1,
      "name": "Swedish Cardiology Terms",
      "version": "1.0.0",
      "description": "Code system for Swedish cardiology terminology",
      "publisher": "Swedish Health Authority",
      "status": "DRAFT",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z",
      "itemCount": 15
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

### 2.3 Get Code System by Name and Version

**Endpoint**: `GET /api/codesystems/by-name-version`

**Description**: Retrieve a code system by its unique name and version combination.

**Query Parameters**:
- `name` (string, required): Code system name
- `version` (string, required): Code system version

**Response**: `200 OK` (same structure as 2.1)

**Error Responses**:
- `404 Not Found`: Code system not found

### 2.4 Get Code System Items

**Endpoint**: `GET /api/codesystems/{id}/items`

**Description**: Retrieve all items for a specific code system.

**Path Parameters**:
- `id` (integer, required): Code system ID

**Query Parameters**:
- `source` (string, optional): Filter by source (SNOMED_MATCH, AI_SUGGESTION, MANUAL)
- `parentCode` (string, optional): Filter by parent code

**Response**: `200 OK`
```json
[
  {
    "id": 1,
    "code": "73211009",
    "display": "Diabetes mellitus",
    "definition": "A metabolic disorder...",
    "relations": ["is-a: Disorder"],
    "useCases": ["Clinical documentation"],
    "motivation": "Core concept",
    "source": "SNOMED_MATCH",
    "parentCode": null
  }
]
```

---

## 3. Code System Management APIs

### 3.1 Update Code System Metadata

**Endpoint**: `PUT /api/codesystems/{id}`

**Description**: Update code system metadata (name, version, description, etc.). Items are not updated through this endpoint.

**Path Parameters**:
- `id` (integer, required): Code system ID

**Request Body**:
```json
{
  "name": "Swedish Cardiology Terms v2",
  "version": "1.0.0",
  "description": "Updated description",
  "publisher": "Swedish Health Authority",
  "contact": "newcontact@example.se",
  "status": "PUBLISHED"
}
```

**Response**: `200 OK` (updated code system)

**Error Responses**:
- `404 Not Found`: Code system not found
- `400 Bad Request`: Validation error or duplicate name/version

### 3.2 Update Code System Status

**Endpoint**: `PATCH /api/codesystems/{id}/status`

**Description**: Update only the status of a code system.

**Path Parameters**:
- `id` (integer, required): Code system ID

**Request Body**:
```json
{
  "status": "PUBLISHED"
}
```

**Response**: `200 OK`
```json
{
  "id": 1,
  "status": "PUBLISHED",
  "updatedAt": "2024-01-15T11:00:00Z"
}
```

### 3.3 Add Item to Code System

**Endpoint**: `POST /api/codesystems/{id}/items`

**Description**: Add a new item to an existing code system.

**Path Parameters**:
- `id` (integer, required): Code system ID

**Request Body**:
```json
{
  "code": "123456789",
  "display": "New term",
  "definition": "Definition text",
  "relations": ["is-a: Concept"],
  "useCases": ["Use case"],
  "motivation": "Motivation",
  "source": "MANUAL",
  "parentCode": "73211009"
}
```

**Response**: `201 Created`
```json
{
  "id": 2,
  "code": "123456789",
  "display": "New term",
  ...
}
```

**Error Responses**:
- `404 Not Found`: Code system not found
- `409 Conflict`: Item with same code already exists

### 3.4 Update Code System Item

**Endpoint**: `PUT /api/codesystems/{id}/items/{itemId}`

**Description**: Update an existing item in a code system.

**Path Parameters**:
- `id` (integer, required): Code system ID
- `itemId` (integer, required): Item ID

**Request Body**: (same as 3.3)

**Response**: `200 OK` (updated item)

### 3.5 Delete Code System Item

**Endpoint**: `DELETE /api/codesystems/{id}/items/{itemId}`

**Description**: Remove an item from a code system.

**Path Parameters**:
- `id` (integer, required): Code system ID
- `itemId` (integer, required): Item ID

**Response**: `204 No Content`

### 3.6 Delete Code System

**Endpoint**: `DELETE /api/codesystems/{id}`

**Description**: Delete a code system and all its items (cascade delete).

**Path Parameters**:
- `id` (integer, required): Code system ID

**Response**: `204 No Content`

**Error Responses**:
- `404 Not Found`: Code system not found

---

## 4. Code System Query APIs

### 4.1 Search Code Systems

**Endpoint**: `GET /api/codesystems/search`

**Description**: Search code systems by name, description, or publisher.

**Query Parameters**:
- `q` (string, optional): Search query
- `status` (string, optional): Filter by status
- `page` (integer, optional): Page number
- `size` (integer, optional): Page size

**Response**: `200 OK` (same structure as 2.2)

### 4.2 Get Code Systems by Status

**Endpoint**: `GET /api/codesystems/status/{status}`

**Description**: Get all code systems with a specific status.

**Path Parameters**:
- `status` (string, required): Status (DRAFT, PUBLISHED, ARCHIVED)

**Response**: `200 OK` (array of code systems without items)

---

## Complete OpenAPI/Swagger Specification

See the updated `openapi.yaml` file for complete Swagger documentation.

