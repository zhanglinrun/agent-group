package com.linrun.trigger.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
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
import org.springframework.util.StringUtils;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   MockPaymentAccessChecker mockPaymentAccessChecker) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"AUTH_0001\",\"info\":\"请使用运营账号访问该接口\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"AUTH_0002\",\"info\":\"当前账号权限不足\"}");
                        }))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/agent/guide/stream", "/api/v1/agent/guide/image", "/api/v1/agent/stop").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/trade/order/direct", "/api/v1/group/trade/lock").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payment/create", "/api/v1/payment/webhook", "/api/v1/payment/webhook/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/gbm/index/query_group_buy_market_config", "/api/v1/gbm/trade/lock_market_pay_order").permitAll()
                        .requestMatchers("/api/v1/weixin/portal", "/api/v1/weixin/login/**").permitAll()
                        .requestMatchers("/api/v1/weixin/template/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/mcp", "/api/v1/mcp/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/knowledge/**", "/api/v1/evaluate/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/trade/order/mock-pay-success")
                        .access((authentication, context) -> new AuthorizationDecision(
                                mockPaymentAccessChecker.isAllowed(authentication.get())))
                        .requestMatchers("/api/v1/trade/order/status-flow").permitAll()
                        .requestMatchers("/api/v1/alipay/**", "/api/v1/gbm/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/group/trade/close-unpaid", "/api/v1/group/trade/refund").hasRole("ADMIN")
                        .requestMatchers("/api/v1/payment/refund", "/api/v1/payment/reconcile").hasRole("ADMIN")
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(
            @Value("${agent.group.security.admin-username:admin}") String adminUsername,
            @Value("${agent.group.security.admin-password:admin_dev}") String adminPassword,
            @Value("${agent.group.security.operator-username:operator}") String operatorUsername,
            @Value("${agent.group.security.operator-password:operator_dev}") String operatorPassword,
            PasswordEncoder passwordEncoder) {
        UserDetails admin = User.withUsername(safe(adminUsername, "admin"))
                .password(passwordEncoder.encode(safe(adminPassword, "admin_dev")))
                .roles("ADMIN", "OPERATOR")
                .build();
        UserDetails operator = User.withUsername(safe(operatorUsername, "operator"))
                .password(passwordEncoder.encode(safe(operatorPassword, "operator_dev")))
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
}
