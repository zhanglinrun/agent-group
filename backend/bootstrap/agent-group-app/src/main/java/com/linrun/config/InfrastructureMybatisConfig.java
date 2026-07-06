package com.linrun.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 交易/拼团等业务 DAO 使用原生 MyBatis，与 trigger 模块的 MyBatis-Plus（Agent 会话表）分离扫描。
 */
@Configuration
@MapperScan("com.linrun.infrastructure.dao")
public class InfrastructureMybatisConfig {
}
