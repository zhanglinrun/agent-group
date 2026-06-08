package com.linrun.trigger.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.StringUtils;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String USER_AUTH_INFO = "请先登录后再访问该接口";
    private static final String OPERATOR_AUTH_INFO = "请使用运营账号访问该接口";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   UserBearerTokenAuthenticationFilter userBearerTokenAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(userBearerTokenAuthenticationFilter, BasicAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"AUTH_0001\",\"info\":\""
                                    + authenticationInfo(request.getRequestURI()) + "\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"AUTH_0002\",\"info\":\"当前账号权限不足\"}");
                        }))
                .authorizeHttpRequests(registry -> registry
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payment/webhook", "/api/v1/payment/webhook/**",
                                "/api/v1/payment/alipay/notify",
                                "/api/v1/payment/refund/webhook/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/quota/packages").permitAll()
                        .requestMatchers("/api/v1/weixin/portal", "/api/v1/weixin/login/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/profile").hasRole("USER")
                        .requestMatchers("/api/v1/quota/**", "/api/v1/academic/**").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/trade/order/direct", "/api/v1/group/trade/lock").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payment/create").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/gbm/index/query_group_buy_market_config", "/api/v1/gbm/trade/lock_market_pay_order").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/trade/order/my").hasRole("USER")
                        .requestMatchers("/api/v1/weixin/template/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/mcp", "/api/v1/mcp/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/agent/admin/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/knowledge/**", "/api/v1/evaluate/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/ops/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/trade/order/status-flow").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/trade/order/admin", "/api/v1/trade/order/admin/refunds",
                                "/api/v1/trade/order/admin/consistency").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/gbm/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/group/trade/close-unpaid", "/api/v1/group/trade/refund").hasRole("ADMIN")
                        .requestMatchers("/api/v1/payment/refund", "/api/v1/payment/reconcile",
                                "/api/v1/payment/bill/download", "/api/v1/payment/refund/query",
                                "/api/v1/payment/certificate/refresh",
                                "/api/v1/payment/error-map", "/api/v1/payment/gateway/status").hasRole("ADMIN")
                        .anyRequest().authenticated());
        return http.build();
    }

    static String authenticationInfo(String requestUri) {
        return requiresOperatorAuth(requestUri) ? OPERATOR_AUTH_INFO : USER_AUTH_INFO;
    }

    private static boolean requiresOperatorAuth(String requestUri) {
        String path = StringUtils.hasText(requestUri) ? requestUri : "";
        return path.startsWith("/api/v1/mcp")
                || path.startsWith("/api/v1/agent/admin/")
                || path.startsWith("/api/v1/knowledge/")
                || path.startsWith("/api/v1/evaluate/")
                || path.startsWith("/api/v1/ops/")
                || path.startsWith("/api/v1/weixin/template/")
                || path.startsWith("/api/v1/trade/order/admin")
                || path.startsWith("/api/v1/trade/order/status-flow")
                || path.startsWith("/api/v1/group/trade/close-unpaid")
                || path.startsWith("/api/v1/group/trade/refund")
                || path.startsWith("/api/v1/payment/refund")
                || path.startsWith("/api/v1/payment/reconcile")
                || path.startsWith("/api/v1/payment/bill/download")
                || path.startsWith("/api/v1/payment/certificate/refresh")
                || path.startsWith("/api/v1/payment/error-map")
                || path.startsWith("/api/v1/payment/gateway/status");
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(
            @Value("${agent.group.security.admin-username:admin}") String adminUsername,
            @Value("${agent.group.security.admin-password:}") String adminPassword,
            @Value("${agent.group.security.operator-username:operator}") String operatorUsername,
            @Value("${agent.group.security.operator-password:}") String operatorPassword,
            PasswordEncoder passwordEncoder) {
        UserDetails admin = User.withUsername(safe(adminUsername, "admin"))
                .password(passwordEncoder.encode(required(adminPassword, "AGENT_GROUP_ADMIN_PASSWORD")))
                .roles("ADMIN", "OPERATOR")
                .build();
        UserDetails operator = User.withUsername(safe(operatorUsername, "operator"))
                .password(passwordEncoder.encode(required(operatorPassword, "AGENT_GROUP_OPERATOR_PASSWORD")))
                .roles("OPERATOR")
                .build();
        return new InMemoryUserDetailsManager(admin, operator);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String required(String value, String envName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envName + " must be set");
        }
        return value;
    }
}
