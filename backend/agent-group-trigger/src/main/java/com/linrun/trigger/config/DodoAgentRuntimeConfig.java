package com.linrun.trigger.config;

import com.zaxxer.hikari.HikariDataSource;
import io.minio.MinioClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@ComponentScan(basePackages = {
        "cn.hollis.llm.mentor.agent.service",
        "cn.hollis.llm.mentor.agent.tool",
        "cn.hollis.llm.mentor.agent.utils"
})
@MapperScan({"cn.hollis.llm.mentor.agent.mapper", "com.linrun.infrastructure.dao"})
public class DodoAgentRuntimeConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    @ConditionalOnMissingBean(name = "agentGroupDataSourceProperties")
    public DataSourceProperties agentGroupDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    @ConditionalOnMissingBean(name = "dataSource")
    public HikariDataSource dataSource(
            @Qualifier("agentGroupDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "pgVectorDataSource")
    @ConditionalOnMissingBean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource(
            @Value("${agent.group.vector.host:127.0.0.1}") String host,
            @Value("${agent.group.vector.port:15432}") String port,
            @Value("${agent.group.vector.database:agent_group_vector}") String database,
            @Value("${agent.group.vector.username:agent_group}") String username,
            @Value("${agent.group.vector.password:agent_group_dev}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(1);
        dataSource.setPoolName("DodoPgVectorPool");
        return dataSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient(
            @Value("${agent.group.minio.endpoint:http://127.0.0.1:9000}") String endpoint,
            @Value("${agent.group.minio.access-key:agent_group}") String accessKey,
            @Value("${agent.group.minio.secret-key:agent_group_dev}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
