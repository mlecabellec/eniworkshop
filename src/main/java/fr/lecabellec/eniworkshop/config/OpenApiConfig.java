package fr.lecabellec.eniworkshop.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI descriptor for ENI Workshop REST APIs.
 *
 * <p>Requirement: REQ-00009, REQ-00011, Feature: FR-00009.
 */
@Configuration(proxyBeanMethods = false)
public final class OpenApiConfig {

    /**
     * Builds the OpenAPI specification metadata with Apache 2.0 license notice.
     *
     * @return configured {@link OpenAPI} bean
     */
    @Bean
    public OpenAPI eniWorkshopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ENI Workshop API")
                        .version("0.1.0")
                        .description("ETG.2100 ENI, ETG.2000 ESI, Bus Design, Validation, and Quasar Integration API")
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
