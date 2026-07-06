package com.linrun.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Reactor-agent DAO 使用同一个数据源与 MyBatis-Plus SqlSessionFactory。
 *
 * <p>本项目已经有 InfrastructureMybatisConfig 扫描 com.linrun.infrastructure.dao。
 * 因为存在显式 MapperScan，MyBatis 的自动 @Mapper 扫描不会再兜底扫描
 * com.linrun.reactor.infrastructure.dao，所以这里为迁入的 Reactor 持久层补上显式扫描。</p>
 */
@Configuration
@MapperScan("com.linrun.reactor.infrastructure.dao")
public class ReactorMybatisConfig {
}
