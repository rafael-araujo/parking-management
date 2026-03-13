package com.parking.management.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI parkingManagementOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Parking Management API")
                        .description("REST API for managing parking garage events, spot occupancy, and revenue reporting.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Parking Management")
                                .email("dev@parking-management.com")));
    }
}
