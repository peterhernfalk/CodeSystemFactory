
package com.example.codesys.model;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
public record TermRequest(@NotEmpty List<String> terms) {}
