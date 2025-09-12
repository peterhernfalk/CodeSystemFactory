
package com.example.codesys.model;
import java.util.List;

public record AiDefinitionRequest(List<Item> terms, String context) {
  public record Item(String term, String snomedId) {}
}
