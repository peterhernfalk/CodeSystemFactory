
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
    String server, // Optional: "snowstorm" | "ontoserver" | "inera" ; defaults to "snowstorm"
    List<String> serverChain // Optional ordered fallback chain, e.g. ["snowstorm","ontoserver","inera"]
) {}
