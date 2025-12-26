package com.example.codesys.service;

import com.example.codesys.model.CodeSystemBuildRequest;
import com.example.codesys.model.CodeSystemBuildResponse;

public interface CodeSystemBuildService {
    CodeSystemBuildResponse build(CodeSystemBuildRequest request);
}

