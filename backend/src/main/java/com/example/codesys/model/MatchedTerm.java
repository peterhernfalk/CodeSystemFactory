package com.example.codesys.model;

public record MatchedTerm(
    String inputTerm,
    String snomedId,
    String preferredTerm,
    String fsn,
    double similarity,
    String description
) {}

