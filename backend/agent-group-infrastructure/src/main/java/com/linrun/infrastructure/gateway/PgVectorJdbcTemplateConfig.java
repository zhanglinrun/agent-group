package com.linrun.infrastructure.gateway;

import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

@Configuration
public class PgVectorJdbcTemplateConfig {

    @Bean(name = "pgVectorJdbcTemplate")
    @ConditionalOnProperty(prefix = "agent.group.vector", name = {"host", "database"})
    public JdbcTemplate pgVectorJdbcTemplate(@Value("${agent.group.vector.host}") String host,
                                             @Value("${agent.group.vector.port:15432}") int port,
                                             @Value("${agent.group.vector.database}") String database,
                                             @Value("${agent.group.vector.username}") String username,
                                             @Value("${agent.group.vector.password}") String password) {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(Driver.class);
        dataSource.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return new JdbcTemplate(dataSource);
    }
}
