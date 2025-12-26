package com.example.codesys.service.impl;

import com.example.codesys.model.CodeSystemExportRequest;
import com.example.codesys.service.CodeSystemExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class CodeSystemExportServiceImpl implements CodeSystemExportService {

    private final ObjectMapper objectMapper;

    public CodeSystemExportServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ExportResult export(CodeSystemExportRequest request) {
        return switch (request.format()) {
            case FHIR -> exportFhir(request);
            case CSV -> exportCsv(request);
            case EXCEL -> exportExcel(request);
        };
    }

    private ExportResult exportFhir(CodeSystemExportRequest request) {
        try {
            ObjectNode fhir = objectMapper.createObjectNode();
            fhir.put("resourceType", "CodeSystem");
            fhir.put("id", sanitizeId(request.codeSystem().name()));
            fhir.put("url", "http://example.org/fhir/CodeSystem/" + sanitizeId(request.codeSystem().name()));
            fhir.put("version", request.codeSystem().version());
            fhir.put("name", sanitizeName(request.codeSystem().name()));
            fhir.put("title", request.codeSystem().name());
            fhir.put("status", "draft");
            
            if (request.codeSystem().publisher() != null) {
                fhir.put("publisher", request.codeSystem().publisher());
            }
            
            if (request.codeSystem().contact() != null) {
                var contact = objectMapper.createArrayNode();
                var contactObj = objectMapper.createObjectNode();
                contactObj.put("name", "Contact");
                var telecom = objectMapper.createArrayNode();
                var telecomObj = objectMapper.createObjectNode();
                telecomObj.put("system", "email");
                telecomObj.put("value", request.codeSystem().contact());
                telecom.add(telecomObj);
                contactObj.set("telecom", telecom);
                contact.add(contactObj);
                fhir.set("contact", contact);
            }
            
            fhir.put("content", "complete");
            
            var concepts = objectMapper.createArrayNode();
            for (var item : request.codeSystem().items()) {
                var concept = objectMapper.createObjectNode();
                concept.put("code", item.code());
                concept.put("display", item.display());
                if (item.definition() != null && !item.definition().isEmpty()) {
                    concept.put("definition", item.definition());
                }
                concepts.add(concept);
            }
            fhir.set("concept", concepts);
            
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fhir);
            return new ExportResult(
                json.getBytes(),
                "application/json",
                sanitizeFilename(request.codeSystem().name()) + "_" + request.codeSystem().version() + ".json"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to export FHIR", e);
        }
    }

    private ExportResult exportCsv(CodeSystemExportRequest request) {
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("Code,Display,Definition,Source\n");
        
        // Items
        for (var item : request.codeSystem().items()) {
            csv.append(escapeCsv(item.code())).append(",");
            csv.append(escapeCsv(item.display())).append(",");
            csv.append(escapeCsv(item.definition() != null ? item.definition() : "")).append(",");
            csv.append(escapeCsv(item.source())).append("\n");
        }
        
        return new ExportResult(
            csv.toString().getBytes(),
            "text/csv",
            sanitizeFilename(request.codeSystem().name()) + "_" + request.codeSystem().version() + ".csv"
        );
    }

    private ExportResult exportExcel(CodeSystemExportRequest request) {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Metadata sheet
            Sheet metadataSheet = workbook.createSheet("Metadata");
            createMetadataSheet(metadataSheet, request.codeSystem());
            
            // Items sheet
            Sheet itemsSheet = workbook.createSheet("Items");
            createItemsSheet(itemsSheet, request.codeSystem().items());
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            
            return new ExportResult(
                out.toByteArray(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                sanitizeFilename(request.codeSystem().name()) + "_" + request.codeSystem().version() + ".xlsx"
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to export Excel", e);
        }
    }

    private void createMetadataSheet(Sheet sheet, CodeSystemExportRequest.CodeSystem codeSystem) {
        int rowNum = 0;
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue("Name");
        row.createCell(1).setCellValue(codeSystem.name());
        
        row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue("Version");
        row.createCell(1).setCellValue(codeSystem.version());
        
        if (codeSystem.description() != null) {
            row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue("Description");
            row.createCell(1).setCellValue(codeSystem.description());
        }
        
        if (codeSystem.publisher() != null) {
            row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue("Publisher");
            row.createCell(1).setCellValue(codeSystem.publisher());
        }
        
        if (codeSystem.contact() != null) {
            row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue("Contact");
            row.createCell(1).setCellValue(codeSystem.contact());
        }
        
        // Auto-size columns
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createItemsSheet(Sheet sheet, List<CodeSystemExportRequest.CodeSystemItem> items) {
        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Code");
        headerRow.createCell(1).setCellValue("Display");
        headerRow.createCell(2).setCellValue("Definition");
        headerRow.createCell(3).setCellValue("Relations");
        headerRow.createCell(4).setCellValue("Source");
        
        // Style header
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        for (int i = 0; i < 5; i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }
        
        // Data rows
        int rowNum = 1;
        for (var item : items) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.code());
            row.createCell(1).setCellValue(item.display());
            row.createCell(2).setCellValue(item.definition() != null ? item.definition() : "");
            row.createCell(3).setCellValue(item.relations() != null ? String.join("; ", item.relations()) : "");
            row.createCell(4).setCellValue(item.source());
        }
        
        // Auto-size columns
        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String sanitizeId(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "-");
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "");
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}

