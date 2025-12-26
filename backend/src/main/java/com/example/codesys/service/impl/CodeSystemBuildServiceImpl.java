package com.example.codesys.service.impl;

import com.example.codesys.model.CodeSystemBuildRequest;
import com.example.codesys.model.CodeSystemBuildResponse;
import com.example.codesys.service.CodeSystemBuildService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CodeSystemBuildServiceImpl implements CodeSystemBuildService {

    @Override
    public CodeSystemBuildResponse build(CodeSystemBuildRequest request) {
        List<CodeSystemBuildResponse.CodeSystemItem> items = new ArrayList<>();
        
        // Add matched terms
        items.addAll(request.matchedTerms().stream()
            .map(mt -> new CodeSystemBuildResponse.CodeSystemItem(
                mt.snomedId(),
                mt.preferredTerm(),
                mt.description(),
                List.of(), // Relations can be added later if needed
                "SNOMED_MATCH"
            ))
            .collect(Collectors.toList()));
        
        // Add recommended terms
        if (request.recommendedTerms() != null) {
            items.addAll(request.recommendedTerms().stream()
                .map(rt -> new CodeSystemBuildResponse.CodeSystemItem(
                    rt.snomedId(),
                    rt.recommendedTerm(),
                    rt.definition(),
                    rt.relations() != null ? rt.relations() : List.of(),
                    "AI_RECOMMENDATION"
                ))
                .collect(Collectors.toList()));
        }
        
        // Add suggested additional terms
        if (request.suggestedTerms() != null) {
            items.addAll(request.suggestedTerms().stream()
                .map(st -> new CodeSystemBuildResponse.CodeSystemItem(
                    st.snomedId(),
                    st.term(),
                    st.definition(),
                    st.relations() != null ? st.relations() : List.of(),
                    "AI_SUGGESTION"
                ))
                .collect(Collectors.toList()));
        }
        
        CodeSystemBuildResponse.CodeSystem codeSystem = new CodeSystemBuildResponse.CodeSystem(
            request.metadata().name(),
            request.metadata().version(),
            request.metadata().description(),
            request.metadata().publisher(),
            request.metadata().contact(),
            items
        );
        
        return new CodeSystemBuildResponse(codeSystem);
    }
}

