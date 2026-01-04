
package com.example.codesys.model;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request model for term matching.
 * 
 * @param terms List of terms to match with SNOMED CT
 * @param server Optional server selection: "snowstorm" (default) or "ontoserver"
 */
public record TermRequest(
    @NotEmpty List<String> terms,
    String server  // Optional: "snowstorm" or "ontoserver", defaults to "snowstorm"
) {}
