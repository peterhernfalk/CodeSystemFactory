# Code System Creation - Suggested Changes

## Overview

This document outlines the specific changes needed to implement code system creation functionality. The code system will compile SNOMED CT matches and AI definitions into a structured, exportable format.

## Architecture Approach

Since workflow sessions are not yet implemented, we'll start with a **simplified approach** that allows creating a code system directly from:
- A list of term matches (with their SNOMED IDs)
- Associated AI definitions
- User-provided metadata

This can be enhanced later to integrate with workflow sessions.

## Data Model Changes

### 1. Create CodeSystem Entity

**File**: `backend/src/main/java/com/example/codesys/model/CodeSystemEntity.java`

```java
package com.example.codesys.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "code_systems")
public class CodeSystemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String version;

    @Column(columnDefinition = "text")
    private String description;

    private String publisher;
    private String contact;
    
    @Enumerated(EnumType.STRING)
    private CodeSystemStatus status = CodeSystemStatus.DRAFT;

    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "codeSystem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CodeSystemItemEntity> items = new ArrayList<>();

    // Constructors, getters, setters
    // ... (standard JPA entity pattern)
}

enum CodeSystemStatus {
    DRAFT, PUBLISHED, ARCHIVED
}
```

**Key Points**:
- Self-contained entity with metadata
- One-to-many relationship with CodeSystemItemEntity
- Status tracking (DRAFT, PUBLISHED, ARCHIVED)
- Timestamps for audit trail

### 2. Create CodeSystemItem Entity

**File**: `backend/src/main/java/com/example/codesys/model/CodeSystemItemEntity.java`

```java
package com.example.codesys.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "code_system_items")
public class CodeSystemItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_system_id", nullable = false)
    private CodeSystemEntity codeSystem;

    @Column(nullable = false)
    private String code;  // SNOMED ID or custom code

    @Column(nullable = false)
    private String display;  // Preferred term/display name

    @Column(columnDefinition = "text")
    private String definition;

    @Column(columnDefinition = "text")
    private String relationsJson;  // JSON array of relations

    @Column(columnDefinition = "text")
    private String useCasesJson;  // JSON array of use cases

    @Column(columnDefinition = "text")
    private String motivation;

    @Enumerated(EnumType.STRING)
    private ItemSource source;  // Where this item came from

    @Column(nullable = true)
    private String parentCode;  // For hierarchical relationships

    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Constructors, getters, setters
    // ... (standard JPA entity pattern)
}

enum ItemSource {
    SNOMED_MATCH,      // From SNOMED CT matching
    AI_SUGGESTION,     // From AI complementary suggestions (future)
    MANUAL             // Manually added by user
}
```

**Key Points**:
- Many-to-one relationship with CodeSystemEntity
- Stores all relevant information (code, display, definition, relations, etc.)
- Tracks source of the item
- Supports hierarchical relationships via parentCode

### 3. Update Existing Entities (Optional - for future workflow integration)

**File**: `backend/src/main/java/com/example/codesys/model/TermMatchEntity.java`

Add optional field (can be added later):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "code_system_id", nullable = true)
private CodeSystemEntity codeSystem;
```

**File**: `backend/src/main/java/com/example/codesys/model/AiDefinitionEntity.java`

Add optional field (can be added later):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "code_system_id", nullable = true)
private CodeSystemEntity codeSystem;
```

**Note**: These relationships are optional and can be added later when workflow sessions are implemented. For now, we'll create code system items directly from the data.

## Request/Response Models

### 4. Create CodeSystemCreationRequest

**File**: `backend/src/main/java/com/example/codesys/model/CodeSystemCreationRequest.java`

```java
package com.example.codesys.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CodeSystemCreationRequest(
    @NotBlank(message = "Name is required")
    String name,
    
    @NotBlank(message = "Version is required")
    String version,
    
    String description,
    String publisher,
    String contact,
    
    @NotEmpty(message = "At least one item is required")
    List<CodeSystemItemRequest> items
) {
    public record CodeSystemItemRequest(
        @NotBlank(message = "Code is required")
        String code,
        
        @NotBlank(message = "Display name is required")
        String display,
        
        String definition,
        List<String> relations,
        List<String> useCases,
        String motivation,
        ItemSource source,
        String parentCode
    ) {}
}
```

**Key Points**:
- Validation annotations for required fields
- Nested record for items
- Flexible structure to accept data from various sources

### 5. Create CodeSystemResponse

**File**: `backend/src/main/java/com/example/codesys/model/CodeSystemResponse.java`

```java
package com.example.codesys.model;

import java.time.OffsetDateTime;
import java.util.List;

public record CodeSystemResponse(
    Long id,
    String name,
    String version,
    String description,
    String publisher,
    String contact,
    CodeSystemStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<CodeSystemItemResponse> items
) {
    public record CodeSystemItemResponse(
        Long id,
        String code,
        String display,
        String definition,
        List<String> relations,
        List<String> useCases,
        String motivation,
        ItemSource source,
        String parentCode
    ) {}
}
```

### 6. Create Simplified Creation Request (Alternative)

**File**: `backend/src/main/java/com/example/codesys/model/CodeSystemFromMatchesRequest.java`

This is a convenience request that takes term matches and AI definitions and automatically creates items:

```java
package com.example.codesys.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CodeSystemFromMatchesRequest(
    @NotBlank(message = "Name is required")
    String name,
    
    @NotBlank(message = "Version is required")
    String version,
    
    String description,
    String publisher,
    String contact,
    
    @NotEmpty(message = "At least one match is required")
    List<MatchWithDefinition> matches
) {
    public record MatchWithDefinition(
        String inputTerm,
        String snomedId,
        String preferredTerm,
        String fsn,
        String definition,        // From AI
        List<String> relations,    // From AI
        List<String> useCases,      // From AI
        String motivation          // From AI
    ) {}
}
```

**Key Points**:
- Combines match data with AI definition data
- Simplifies frontend integration
- Automatically maps to CodeSystemItemEntity

## Repository Layer

### 7. Create CodeSystemRepository

**File**: `backend/src/main/java/com/example/codesys/repository/CodeSystemRepository.java`

```java
package com.example.codesys.repository;

import com.example.codesys.model.CodeSystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeSystemRepository extends JpaRepository<CodeSystemEntity, Long> {
    
    Optional<CodeSystemEntity> findByNameAndVersion(String name, String version);
    
    List<CodeSystemEntity> findByStatus(CodeSystemStatus status);
    
    @Query("SELECT cs FROM CodeSystemEntity cs ORDER BY cs.createdAt DESC")
    List<CodeSystemEntity> findAllOrderByCreatedAtDesc();
    
    boolean existsByNameAndVersion(String name, String version);
}
```

### 8. Create CodeSystemItemRepository

**File**: `backend/src/main/java/com/example/codesys/repository/CodeSystemItemRepository.java`

```java
package com.example.codesys.repository;

import com.example.codesys.model.CodeSystemItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeSystemItemRepository extends JpaRepository<CodeSystemItemEntity, Long> {
    
    List<CodeSystemItemEntity> findByCodeSystemId(Long codeSystemId);
    
    List<CodeSystemItemEntity> findByCodeSystemIdAndParentCode(Long codeSystemId, String parentCode);
    
    boolean existsByCodeSystemIdAndCode(Long codeSystemId, String code);
}
```

## Service Layer

### 9. Create CodeSystemBuilderService

**File**: `backend/src/main/java/com/example/codesys/service/CodeSystemBuilderService.java`

```java
package com.example.codesys.service;

import com.example.codesys.model.*;

public interface CodeSystemBuilderService {
    CodeSystemResponse createCodeSystem(CodeSystemCreationRequest request);
    CodeSystemResponse createCodeSystemFromMatches(CodeSystemFromMatchesRequest request);
    CodeSystemResponse getCodeSystem(Long id);
    List<CodeSystemResponse> listCodeSystems();
    void deleteCodeSystem(Long id);
}
```

### 10. Implement CodeSystemBuilderService

**File**: `backend/src/main/java/com/example/codesys/service/impl/CodeSystemBuilderServiceImpl.java`

```java
package com.example.codesys.service.impl;

import com.example.codesys.model.*;
import com.example.codesys.repository.CodeSystemRepository;
import com.example.codesys.repository.CodeSystemItemRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CodeSystemBuilderServiceImpl implements CodeSystemBuilderService {
    
    private final CodeSystemRepository codeSystemRepository;
    private final CodeSystemItemRepository itemRepository;
    private final ObjectMapper objectMapper;
    
    public CodeSystemBuilderServiceImpl(
            CodeSystemRepository codeSystemRepository,
            CodeSystemItemRepository itemRepository,
            ObjectMapper objectMapper) {
        this.codeSystemRepository = codeSystemRepository;
        this.itemRepository = itemRepository;
        this.objectMapper = objectMapper;
    }
    
    @Override
    @Transactional
    public CodeSystemResponse createCodeSystem(CodeSystemCreationRequest request) {
        // Validate name/version uniqueness
        if (codeSystemRepository.existsByNameAndVersion(request.name(), request.version())) {
            throw new IllegalArgumentException(
                "Code system with name '" + request.name() + "' and version '" + 
                request.version() + "' already exists");
        }
        
        // Create code system entity
        CodeSystemEntity codeSystem = new CodeSystemEntity();
        codeSystem.setName(request.name());
        codeSystem.setVersion(request.version());
        codeSystem.setDescription(request.description());
        codeSystem.setPublisher(request.publisher());
        codeSystem.setContact(request.contact());
        codeSystem.setStatus(CodeSystemStatus.DRAFT);
        
        // Create and link items
        List<CodeSystemItemEntity> items = request.items().stream()
            .map(itemReq -> {
                CodeSystemItemEntity item = new CodeSystemItemEntity();
                item.setCodeSystem(codeSystem);
                item.setCode(itemReq.code());
                item.setDisplay(itemReq.display());
                item.setDefinition(itemReq.definition());
                item.setRelationsJson(safeWriteJson(itemReq.relations()));
                item.setUseCasesJson(safeWriteJson(itemReq.useCases()));
                item.setMotivation(itemReq.motivation());
                item.setSource(itemReq.source() != null ? itemReq.source() : ItemSource.MANUAL);
                item.setParentCode(itemReq.parentCode());
                return item;
            })
            .collect(Collectors.toList());
        
        codeSystem.setItems(items);
        
        // Save (cascade will save items)
        CodeSystemEntity saved = codeSystemRepository.save(codeSystem);
        
        return toResponse(saved);
    }
    
    @Override
    @Transactional
    public CodeSystemResponse createCodeSystemFromMatches(CodeSystemFromMatchesRequest request) {
        // Convert matches to items
        List<CodeSystemCreationRequest.CodeSystemItemRequest> items = request.matches().stream()
            .map(match -> {
                // Use SNOMED ID as code, or generate one if missing
                String code = match.snomedId() != null && !match.snomedId().isEmpty() 
                    ? match.snomedId() 
                    : generateCodeFromTerm(match.inputTerm());
                
                // Use preferred term or FSN as display
                String display = match.preferredTerm() != null && !match.preferredTerm().isEmpty()
                    ? match.preferredTerm()
                    : (match.fsn() != null ? match.fsn() : match.inputTerm());
                
                return new CodeSystemCreationRequest.CodeSystemItemRequest(
                    code,
                    display,
                    match.definition(),
                    match.relations(),
                    match.useCases(),
                    match.motivation(),
                    ItemSource.SNOMED_MATCH,
                    null  // Parent code can be derived from relations later
                );
            })
            .collect(Collectors.toList());
        
        // Create using standard creation method
        CodeSystemCreationRequest creationRequest = new CodeSystemCreationRequest(
            request.name(),
            request.version(),
            request.description(),
            request.publisher(),
            request.contact(),
            items
        );
        
        return createCodeSystem(creationRequest);
    }
    
    @Override
    @Transactional(readOnly = true)
    public CodeSystemResponse getCodeSystem(Long id) {
        CodeSystemEntity codeSystem = codeSystemRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Code system not found: " + id));
        return toResponse(codeSystem);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<CodeSystemResponse> listCodeSystems() {
        return codeSystemRepository.findAllOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void deleteCodeSystem(Long id) {
        if (!codeSystemRepository.existsById(id)) {
            throw new IllegalArgumentException("Code system not found: " + id);
        }
        codeSystemRepository.deleteById(id);
    }
    
    private CodeSystemResponse toResponse(CodeSystemEntity entity) {
        List<CodeSystemResponse.CodeSystemItemResponse> items = entity.getItems().stream()
            .map(item -> new CodeSystemResponse.CodeSystemItemResponse(
                item.getId(),
                item.getCode(),
                item.getDisplay(),
                item.getDefinition(),
                safeReadJsonList(item.getRelationsJson()),
                safeReadJsonList(item.getUseCasesJson()),
                item.getMotivation(),
                item.getSource(),
                item.getParentCode()
            ))
            .collect(Collectors.toList());
        
        return new CodeSystemResponse(
            entity.getId(),
            entity.getName(),
            entity.getVersion(),
            entity.getDescription(),
            entity.getPublisher(),
            entity.getContact(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            items
        );
    }
    
    private String safeWriteJson(Object obj) {
        try {
            return obj != null ? objectMapper.writeValueAsString(obj) : "[]";
        } catch (Exception e) {
            return "[]";
        }
    }
    
    private List<String> safeReadJsonList(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
    
    private String generateCodeFromTerm(String term) {
        // Simple code generation - can be enhanced later
        return "CUSTOM_" + term.toUpperCase().replaceAll("[^A-Z0-9]", "_");
    }
}
```

**Key Points**:
- Two creation methods: direct creation and from matches
- Automatic code generation for items without SNOMED IDs
- JSON serialization/deserialization for relations and use cases
- Transaction management
- Error handling

## Controller Layer

### 11. Create CodeSystemController

**File**: `backend/src/main/java/com/example/codesys/controller/CodeSystemController.java`

```java
package com.example.codesys.controller;

import com.example.codesys.model.*;
import com.example.codesys.service.CodeSystemBuilderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/codesystems")
@CrossOrigin
public class CodeSystemController {
    
    private final CodeSystemBuilderService codeSystemService;
    
    public CodeSystemController(CodeSystemBuilderService codeSystemService) {
        this.codeSystemService = codeSystemService;
    }
    
    @PostMapping(
        value = "",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CodeSystemResponse> createCodeSystem(
            @Valid @RequestBody CodeSystemCreationRequest request) {
        CodeSystemResponse response = codeSystemService.createCodeSystem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping(
        value = "/from-matches",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CodeSystemResponse> createCodeSystemFromMatches(
            @Valid @RequestBody CodeSystemFromMatchesRequest request) {
        CodeSystemResponse response = codeSystemService.createCodeSystemFromMatches(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CodeSystemResponse> getCodeSystem(@PathVariable Long id) {
        CodeSystemResponse response = codeSystemService.getCodeSystem(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CodeSystemResponse>> listCodeSystems() {
        List<CodeSystemResponse> codeSystems = codeSystemService.listCodeSystems();
        return ResponseEntity.ok(codeSystems);
    }
    
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteCodeSystem(@PathVariable Long id) {
        codeSystemService.deleteCodeSystem(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Key Points**:
- RESTful endpoints following existing patterns
- Two creation endpoints (direct and from matches)
- Standard CRUD operations
- Proper HTTP status codes

## Database Configuration

### 12. Enable JPA Configuration

**File**: `backend/src/main/resources/application.yml`

Uncomment and configure:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:codesys}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: update  # Use 'validate' or 'none' in production with migrations
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: true
```

**File**: `backend/src/main/resources/application-dev.yml`

For development with H2:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    username: sa
    password:
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
        format_sql: true
    show-sql: true
```

### 13. Add H2 Dependency (if using dev profile)

**File**: `backend/pom.xml`

Add to dependencies:
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

## API Documentation

### 14. Update OpenAPI Specification

**File**: `backend/openapi.yaml`

Add new paths:
```yaml
  /api/codesystems:
    post:
      summary: Create a new code system
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CodeSystemCreationRequest'
      responses:
        '201':
          description: Code system created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CodeSystemResponse'
    get:
      summary: List all code systems
      responses:
        '200':
          description: List of code systems
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/CodeSystemResponse'
  
  /api/codesystems/{id}:
    get:
      summary: Get a code system by ID
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: Code system details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CodeSystemResponse'
    delete:
      summary: Delete a code system
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
      responses:
        '204':
          description: Code system deleted
  
  /api/codesystems/from-matches:
    post:
      summary: Create a code system from term matches and AI definitions
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CodeSystemFromMatchesRequest'
      responses:
        '201':
          description: Code system created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CodeSystemResponse'
```

Add schemas:
```yaml
  CodeSystemCreationRequest:
    type: object
    required: [name, version, items]
    properties:
      name: { type: string }
      version: { type: string }
      description: { type: string }
      publisher: { type: string }
      contact: { type: string }
      items:
        type: array
        items:
          $ref: '#/components/schemas/CodeSystemItemRequest'
  
  CodeSystemItemRequest:
    type: object
    required: [code, display]
    properties:
      code: { type: string }
      display: { type: string }
      definition: { type: string }
      relations: 
        type: array
        items: { type: string }
      useCases:
        type: array
        items: { type: string }
      motivation: { type: string }
      source: { type: string, enum: [SNOMED_MATCH, AI_SUGGESTION, MANUAL] }
      parentCode: { type: string, nullable: true }
  
  CodeSystemFromMatchesRequest:
    type: object
    required: [name, version, matches]
    properties:
      name: { type: string }
      version: { type: string }
      description: { type: string }
      publisher: { type: string }
      contact: { type: string }
      matches:
        type: array
        items:
          $ref: '#/components/schemas/MatchWithDefinition'
  
  MatchWithDefinition:
    type: object
    properties:
      inputTerm: { type: string }
      snomedId: { type: string, nullable: true }
      preferredTerm: { type: string, nullable: true }
      fsn: { type: string, nullable: true }
      definition: { type: string }
      relations:
        type: array
        items: { type: string }
      useCases:
        type: array
        items: { type: string }
      motivation: { type: string }
  
  CodeSystemResponse:
    type: object
    properties:
      id: { type: integer }
      name: { type: string }
      version: { type: string }
      description: { type: string }
      publisher: { type: string }
      contact: { type: string }
      status: { type: string, enum: [DRAFT, PUBLISHED, ARCHIVED] }
      createdAt: { type: string, format: date-time }
      updatedAt: { type: string, format: date-time }
      items:
        type: array
        items:
          $ref: '#/components/schemas/CodeSystemItemResponse'
  
  CodeSystemItemResponse:
    type: object
    properties:
      id: { type: integer }
      code: { type: string }
      display: { type: string }
      definition: { type: string }
      relations:
        type: array
        items: { type: string }
      useCases:
        type: array
        items: { type: string }
      motivation: { type: string }
      source: { type: string, enum: [SNOMED_MATCH, AI_SUGGESTION, MANUAL] }
      parentCode: { type: string, nullable: true }
```

## Frontend Integration Points

### 15. Frontend Changes (High-Level)

The frontend will need to:

1. **Collect data from existing steps**:
   - Term matches from Step 2
   - AI definitions from Step 3

2. **Create metadata form**:
   - Name, version, description, publisher, contact

3. **Call creation endpoint**:
   ```typescript
   const createCodeSystem = async () => {
     const request = {
       name: metadata.name,
       version: metadata.version,
       description: metadata.description,
       publisher: metadata.publisher,
       contact: metadata.contact,
       matches: matches.map(match => ({
         inputTerm: match.input,
         snomedId: match.matchedSctId,
         preferredTerm: match.preferredTermSv,
         fsn: match.fsnSv,
         definition: aiDefinitions.find(d => d.term === match.input)?.definition || '',
         relations: aiDefinitions.find(d => d.term === match.input)?.relations || [],
         useCases: aiDefinitions.find(d => d.term === match.input)?.useCases || [],
         motivation: aiDefinitions.find(d => d.term === match.input)?.motivation || ''
       }))
     };
     
     const response = await fetch('/api/codesystems/from-matches', {
       method: 'POST',
       headers: { 'Content-Type': 'application/json' },
       body: JSON.stringify(request)
     });
     
     return response.json();
   };
   ```

4. **Display created code system**:
   - Show success message
   - Display code system details
   - Provide export options (future)

## Implementation Order

1. **Database Setup** (Steps 12-13)
   - Enable JPA configuration
   - Add database dependencies

2. **Data Models** (Steps 1-6)
   - Create entities
   - Create request/response models
   - Add enums

3. **Repositories** (Steps 7-8)
   - Create repository interfaces
   - Enable repositories (uncomment if needed)

4. **Service Layer** (Steps 9-10)
   - Create service interface
   - Implement service

5. **Controller** (Step 11)
   - Create REST controller
   - Add endpoints

6. **Documentation** (Step 14)
   - Update OpenAPI spec

7. **Testing**
   - Test creation endpoints
   - Test data persistence
   - Test validation

## Testing Considerations

### Unit Tests
- `CodeSystemBuilderServiceImpl`: Test creation logic, validation, mapping
- `CodeSystemController`: Test endpoint mappings, error handling

### Integration Tests
- Test full creation flow from request to database
- Test uniqueness validation
- Test cascade deletion

### Manual Testing
1. Create code system with valid data
2. Verify database persistence
3. Test duplicate name/version rejection
4. Test retrieval and listing
5. Test deletion

## Future Enhancements

1. **Workflow Session Integration**: Link code systems to workflow sessions
2. **Export Functionality**: Add export endpoints (FHIR, JSON, CSV)
3. **Hierarchical Relationships**: Parse relations to build parent-child relationships
4. **Validation Rules**: Add business rules for code system validation
5. **Versioning**: Support version history and updates
6. **Search/Filter**: Add search capabilities for code systems
7. **Bulk Operations**: Support bulk item updates

## Summary

This implementation provides:
- ✅ Complete data model for code systems
- ✅ Service layer for building code systems
- ✅ REST API for creation and retrieval
- ✅ Two creation methods (direct and from matches)
- ✅ Proper validation and error handling
- ✅ Database persistence
- ✅ Foundation for future enhancements

The design is flexible enough to integrate with workflow sessions later while providing immediate value for code system creation.

