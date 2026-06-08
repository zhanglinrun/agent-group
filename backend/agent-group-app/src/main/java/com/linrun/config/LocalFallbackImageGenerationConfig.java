package com.linrun.config;

import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import com.linrun.infrastructure.agent.port.LocalFallbackImageGenerationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocalFallbackImageGenerationConfig {

    @Bean
    @ConditionalOnMissingBean(AcademicImageGenerationPort.class)
    @ConditionalOnProperty(prefix = "agent.group.reactor-tool", name = "enabled", havingValue = "false", matchIfMissing = true)
    public AcademicImageGenerationPort localFallbackImageGenerationPort() {
        return new LocalFallbackImageGenerationPort();
    }
}
