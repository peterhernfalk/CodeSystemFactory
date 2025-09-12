package com.example.codesys.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_definitions")
public class AiDefinitionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String term;
    private String snomedId;

    @Column(columnDefinition = "text")
    private String definition;

    @Column(columnDefinition = "text")
    private String relationsJson;

    @Column(columnDefinition = "text")
    private String motivation;

    @Column(columnDefinition = "text")
    private String useCasesJson;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    public AiDefinitionEntity() {}

    public AiDefinitionEntity(String term, String snomedId, String definition, String relationsJson, String motivation, String useCasesJson) {
        this.term = term;
        this.snomedId = snomedId;
        this.definition = definition;
        this.relationsJson = relationsJson;
        this.motivation = motivation;
        this.useCasesJson = useCasesJson;
    }

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public String getSnomedId() { return snomedId; }
    public void setSnomedId(String snomedId) { this.snomedId = snomedId; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public String getRelationsJson() { return relationsJson; }
    public void setRelationsJson(String relationsJson) { this.relationsJson = relationsJson; }
    public String getMotivation() { return motivation; }
    public void setMotivation(String motivation) { this.motivation = motivation; }
    public String getUseCasesJson() { return useCasesJson; }
    public void setUseCasesJson(String useCasesJson) { this.useCasesJson = useCasesJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
