package com.workout.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI metadata + a bearer-JWT security scheme, so Swagger UI's "Authorize" button takes a
 * real Supabase token and every operation is documented as requiring it.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("workout-api")
                    .version("v1")
                    .description("Workout tracker API — auth, exercise catalog, logging, and bidirectional history."),
            ).components(
                Components().addSecuritySchemes(
                    "bearer-jwt",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Supabase access token (mint one with scripts/dev-token.sh)"),
                ),
            ).addSecurityItem(SecurityRequirement().addList("bearer-jwt"))
}
