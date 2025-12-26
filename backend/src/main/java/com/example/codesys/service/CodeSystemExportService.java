package com.example.codesys.service;

import com.example.codesys.model.CodeSystemExportRequest;

public interface CodeSystemExportService {
    ExportResult export(CodeSystemExportRequest request);
    
    record ExportResult(byte[] content, String contentType, String filename) {}
}

