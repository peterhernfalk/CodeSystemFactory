package com.example.codesys.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${SERVER_URL:}")
    private String serverUrl;

    @Value("${info.build.version:${project.version:0.0.1-SNAPSHOT}}")
    private String applicationVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        List<Server> servers = new ArrayList<>();
        
        // Check if we're in production (SERVER_URL is set)
        boolean isProduction = serverUrl != null && !serverUrl.trim().isEmpty();
        
        if (isProduction) {
            // In production: Add production server first (so it's the default)
            // Ensure URL has protocol (https://) if not present
            String productionUrl = serverUrl.trim();
            if (!productionUrl.startsWith("http://") && !productionUrl.startsWith("https://")) {
                productionUrl = "https://" + productionUrl;
            }
            
            servers.add(new Server()
                    .url(productionUrl)
                    .description("Production server"));
            
            // Optionally add localhost as a secondary option for testing
            // (commented out to avoid confusion - uncomment if needed)
            // servers.add(new Server()
            //         .url("http://localhost:" + serverPort)
            //         .description("Local development server"));
        } else {
            // In development: Only show localhost
            servers.add(new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Local development server"));
        }
        
        return new OpenAPI()
                .info(new Info()
                        .title("Code System Builder API")
                        .version(applicationVersion)
                        .description("Stateless APIs for matching domain terms with SNOMED CT (Swedish), " +
                                "generating AI-assisted recommendations, and exporting code systems.")
                        .contact(new Contact()
                                .name("Code System Factory")
                                .email("support@example.com")))
                .servers(servers);
    }
}

