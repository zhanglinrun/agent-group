package com.linrun.trigger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI agentGroupOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agent Group API")
                        .description("Login, quota, group-buy, payment and agent workspace compatible APIs.")
                        .version("1.0.0")
                        .license(new License().name("internal")))
                .servers(List.of(new Server().url("/").description("current server")));
    }
}















