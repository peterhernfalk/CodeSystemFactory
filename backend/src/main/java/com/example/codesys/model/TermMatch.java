
package com.example.codesys.model;

public record TermMatch(
    String input,
    String matchedSctId,
    String preferredTermSv,
    String fsnSv,
    double similarity,
    String status
) {}
