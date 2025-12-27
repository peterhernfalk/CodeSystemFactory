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

    @Bean
    public OpenAPI customOpenAPI() {
        List<Server> servers = new ArrayList<>();
        
        // Add production server if SERVER_URL is set
        if (serverUrl != null && !serverUrl.trim().isEmpty()) {
            servers.add(new Server()
                    .url(serverUrl)
                    .description("Production server"));
        }
        
        // Always add localhost for local development
        servers.add(new Server()
                .url("http://localhost:" + serverPort)
                .description("Local development server"));
        
        return new OpenAPI()
                .info(new Info()
                        .title("Code System Builder API")
                        .version("2.0.0")
                        .description("Stateless APIs for matching domain terms with SNOMED CT (Swedish), " +
                                "generating AI-assisted recommendations, and exporting code systems.")
                        .contact(new Contact()
                                .name("Code System Factory")
                                .email("support@example.com")))
                .servers(servers);
    }
}

