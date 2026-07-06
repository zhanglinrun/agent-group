package com.linrun.reactor.trigger.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import com.linrun.reactor.application.agent.visitor.AnonymousVisitorApplicationService;
import com.linrun.reactor.trigger.http.visitor.VisitorIdentityFilter;
import com.linrun.reactor.types.agent.config.AgentExecutorProperties;

/**
 * @author bjwangjuntao
 */
@Configuration
@EnableConfigurationProperties(AgentExecutorProperties.class)
public class BaseFilterConfig {
	public BaseFilterConfig() {
	}

	@Bean(name = "reactorCorsFilterRegistration")
	public FilterRegistrationBean<CorsFilter> reactorCorsFilterRegistration(AgentExecutorProperties properties) {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		if (CollectionUtils.isNotEmpty(properties.getVisitorCookie().getAllowedOrigins())) {
			config.setAllowCredentials(true);
			config.setAllowedOrigins(properties.getVisitorCookie().getAllowedOrigins());
		}
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		source.registerCorsConfiguration("/**", config);
		CorsFilter corsFilter = new CorsFilter(source);
		return this.creatAllFilter(corsFilter, 1);
	}

	@Bean
	public FilterRegistrationBean<VisitorIdentityFilter> visitorIdentityFilter(
			AnonymousVisitorApplicationService anonymousVisitorApplicationService,
			AgentExecutorProperties properties) {
		VisitorIdentityFilter filter = new VisitorIdentityFilter(
				anonymousVisitorApplicationService,
				properties.getVisitorCookie()
		);
		return this.creatAllFilter(filter, 2);
	}


	<T extends Filter> FilterRegistrationBean<T> creatAllFilter(T filter, int order) {
		return this.createFilter(filter, order, "/*");
	}

	<T extends Filter> FilterRegistrationBean<T> createFilter(T filter, int order, String... urlPatterns) {
		FilterRegistrationBean<T> bean = new FilterRegistrationBean<>();
		bean.setFilter(filter);
		bean.setOrder(order);
		bean.addUrlPatterns(urlPatterns);
		bean.setDispatcherTypes(DispatcherType.REQUEST, new DispatcherType[0]);
		return bean;
	}
}
