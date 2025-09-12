
package com.example.codesys.service;

import com.example.codesys.model.AiDefinitionRequest;
import com.example.codesys.model.AiDefinitionResponse;

public interface AiService {
    AiDefinitionResponse defineTerms(AiDefinitionRequest request);
}
