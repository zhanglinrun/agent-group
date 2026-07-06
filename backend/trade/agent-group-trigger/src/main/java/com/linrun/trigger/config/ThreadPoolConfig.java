package com.linrun.trigger.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@EnableAsync
@Configuration
@EnableConfigurationProperties(ThreadPoolConfigProperties.class)
public class ThreadPoolConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(ThreadPoolExecutor.class)
    public ThreadPoolExecutor threadPoolExecutor(ThreadPoolConfigProperties properties) {
        return new ThreadPoolExecutor(
                properties.getCorePoolSize(),
                properties.getMaxPoolSize(),
                properties.getKeepAliveTime(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getBlockQueueSize()),
                Executors.defaultThreadFactory(),
                rejectedExecutionHandler(properties.getPolicy()));
    }

    @Bean
    @ConditionalOnMissingBean(AsyncTaskExecutor.class)
    public AsyncTaskExecutor mvcAsyncTaskExecutor(ThreadPoolExecutor threadPoolExecutor) {
        return new ConcurrentTaskExecutor(threadPoolExecutor);
    }

    @Bean
    public WebMvcConfigurer streamAsyncWebMvcConfigurer(AsyncTaskExecutor mvcAsyncTaskExecutor) {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setTaskExecutor(mvcAsyncTaskExecutor);
                configurer.setDefaultTimeout(60_000L);
            }
        };
    }

    private RejectedExecutionHandler rejectedExecutionHandler(String policy) {
        return switch (policy) {
            case "DiscardPolicy" -> new ThreadPoolExecutor.DiscardPolicy();
            case "DiscardOldestPolicy" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            case "CallerRunsPolicy" -> new ThreadPoolExecutor.CallerRunsPolicy();
            case "AbortPolicy" -> new ThreadPoolExecutor.AbortPolicy();
            default -> new ThreadPoolExecutor.AbortPolicy();
        };
    }
}















