package com.linrun.trigger.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiterAccessInterceptor {

    String key();

    String fallbackMethod() default "";

    double permitsPerSecond() default 1.0d;

    int blacklistCount() default 0;

    long blacklistSeconds() default 300L;
}















