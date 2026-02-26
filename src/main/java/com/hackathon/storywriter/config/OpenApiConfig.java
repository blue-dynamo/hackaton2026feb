package com.hackathon.storywriter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures the OpenAPI / Swagger UI metadata for story-writer.
 *
 * <p>UI is available at {@code http://localhost:8080/swagger-ui.html}
 * and the raw spec at {@code http://localhost:8080/v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI storyWriterOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Story-Writer API")
                        .description("""
                                AI-empowered service that converts test failures and application logs \
                                into structured bug reports, user stories, and severity assessments \
                                using the GitHub Copilot CLI multi-agent pipeline.
                                """)
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact()
                                .name("Hackathon 2026")
                                .url("https://github.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")
                ));
    }
}
