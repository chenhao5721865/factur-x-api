package com.facturxapi.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String schemeName = "api_key";
        return new OpenAPI()
            .info(new Info()
                .title("Factur-X Validation API")
                .version("1.0.0")
                .description("Validate Factur-X PDF and XML invoices against EN 16931 rules.")
                .contact(new Contact()
                    .name("Factur-X API Support")
                    .url("https://factur-x-api.com")))
            .addSecurityItem(new SecurityRequirement().addList(schemeName))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes(schemeName,
                    new SecurityScheme()
                        .name("X-API-Key")
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .description("Generate via POST /api/admin/provision-key")));
    }
}
