
package com.example.codesys.model;
import java.util.List;

public record TermMatchResponse(
    List<MatchedTerm> matched,
    List<UnmatchedTerm> unmatched
) {}
