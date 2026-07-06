package com.linrun.reactor;

/**
 * Reactor-agent 原独立启动类。
 *
 * 迁移进 agent-group 单进程后，唯一启动入口是 com.linrun.Application，
 * 这里不再作为 Spring Boot 应用入口，去掉 @SpringBootApplication 等注解，
 * 避免同一进程内出现两个 @SpringBootApplication 导致的组件扫描与自动配置重复。
 *
 * Reactor 侧的组件由 com.linrun.Application 的组件扫描（com.linrun 根包）统一纳入，
 * MyBatis mapper 由 com.linrun.config.ReactorMybatisConfig 负责扫描。
 */
public final class Application {

    private Application() {
    }

}
