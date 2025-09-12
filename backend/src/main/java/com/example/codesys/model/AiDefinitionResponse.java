
package com.example.codesys.model;
import java.util.List;

public record AiDefinitionResponse(List<DefinedTerm> results) {
  public record DefinedTerm(
    String term,
    String snomedId,
    String definition,
    List<String> relations,
    String motivation,
    List<String> useCases
  ) {}
}
