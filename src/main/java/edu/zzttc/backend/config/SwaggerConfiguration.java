package edu.zzttc.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {

        @Bean
        public OpenAPI backendOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Backend API")
                                                .description("ZZTTC Backend 通用接口文档")
                                                .version("v1.0.0"));
        }
}
