package com.example.codesys.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "term_matches")
public class TermMatchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String inputText;
    private String matchedSctId;
    private String preferredTermSv;
    private String fsnSv;
    private double similarity;
    private String status;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public TermMatchEntity() {}

    public TermMatchEntity(String inputText, String matchedSctId, String preferredTermSv, String fsnSv, double similarity, String status) {
        this.inputText = inputText;
        this.matchedSctId = matchedSctId;
        this.preferredTermSv = preferredTermSv;
        this.fsnSv = fsnSv;
        this.similarity = similarity;
        this.status = status;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }
    public String getMatchedSctId() { return matchedSctId; }
    public void setMatchedSctId(String matchedSctId) { this.matchedSctId = matchedSctId; }
    public String getPreferredTermSv() { return preferredTermSv; }
    public void setPreferredTermSv(String preferredTermSv) { this.preferredTermSv = preferredTermSv; }
    public String getFsnSv() { return fsnSv; }
    public void setFsnSv(String fsnSv) { this.fsnSv = fsnSv; }
    public double getSimilarity() { return similarity; }
    public void setSimilarity(double similarity) { this.similarity = similarity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
