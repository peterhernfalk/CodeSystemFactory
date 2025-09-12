
package com.example.codesys.service.impl;

import com.example.codesys.model.AiDefinitionRequest;
import com.example.codesys.model.AiDefinitionResponse;
import com.example.codesys.service.AiService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SpringAiService implements AiService {

    private final ChatClient chatClient;
    private final boolean enabled;

    @Autowired
    public SpringAiService(@Value("${ai.enabled:true}") boolean enabled, ChatClient.Builder chatClientBuilder) {
        this.enabled = enabled;
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public AiDefinitionResponse defineTerms(AiDefinitionRequest request) {
        if(!enabled){
            List<AiDefinitionResponse.DefinedTerm> res = new ArrayList<>();
            for(var t : request.terms()){
                res.add(new AiDefinitionResponse.DefinedTerm(
                        t.term(),
                        t.snomedId(),
                        "Mock definition for " + t.term(),
                        List.of("is-a: Example relation"),
                        "Selected due to lexical similarity and domain relevance.",
                        List.of("Clinical documentation", "Decision support")
                ));
            }
            return new AiDefinitionResponse(res);
        }

        String prompt = buildPrompt(request);
        String content = this.chatClient.prompt(prompt).call().content();
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(content, AiDefinitionResponse.class);
        } catch (Exception e){
            List<AiDefinitionResponse.DefinedTerm> one = new ArrayList<>();
            for(var t : request.terms()){
                one.add(new AiDefinitionResponse.DefinedTerm(
                        t.term(), t.snomedId(), content, List.of(), "LLM free-form.", List.of()));
            }
            return new AiDefinitionResponse(one);
        }
    }

    private String buildPrompt(AiDefinitionRequest req){
        String list = req.terms().stream()
                .map(t -> String.format("{term:\\\"%s\\\", snomedId:\\\"%s\\\"}", t.term(), t.snomedId()==null? "" : t.snomedId()))
                .collect(Collectors.joining(", "));
        String context = req.context()==null? "": req.context();
        String prompt = ""
            + "You are a clinical terminology assistant helping build a local code system aligned with SNOMED CT (Swedish).\n"
            + "Given the input terms (and optional SNOMED IDs), return a JSON with the structure:\n"
            + "{\n"
            + "  \\\"results\\\":[\n"
            + "    {\n"
            + "      \\\"term\\\":\\\"<input term>\\\",\n"
            + "      \\\"snomedId\\\":\\\"<if known>\\\",\n"
            + "      \\\"definition\\\":\\\"<clear, concise Swedish definition>\\\",\n"
            + "      \\\"relations\\\":[\\\"<SNOMED relation suggestions: is-a/part-of/associated-with ...>\\\"],\n"
            + "      \\\"motivation\\\":\\\"<why the term is appropriate and how it aligns with SNOMED>\\\",\n"
            + "      \\\"useCases\\\":[\\\"<2-3 concrete use cases in Swedish>\\\"]\n"
            + "    }, ...\n"
            + "  ]\n"
            + "}\n"
            + "Only output valid JSON and nothing else.\n"
            + "Context: " + context + "\n"
            + "Terms: [" + list + "]\n";
        return prompt;
    }
}
