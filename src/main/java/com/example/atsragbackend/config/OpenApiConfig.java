package com.example.atsragbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI atsMatcherOpenAPI() {
        return new OpenAPI()
            .info(new Info()
            .title("AI ATS Matcher API")
            .description("REST API for parsing resumes and matching jobs using Apify and PGVector.")
            .version("v1.0"));
    }
}