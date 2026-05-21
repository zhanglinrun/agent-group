package com.linrun.trigger.config;

import com.linrun.types.exception.AppException;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class MockPaymentAccessChecker {

    private final Environment environment;

    public MockPaymentAccessChecker(Environment environment) {
        this.environment = environment;
    }

    public boolean isAllowed(Authentication authentication) {
        return isDevProfile() || isAdmin(authentication);
    }

    public void assertAllowed() {
        if (!isAllowed(SecurityContextHolder.getContext().getAuthentication())) {
            throw new AppException("PAY_0007", "模拟支付只允许开发环境或管理员使用");
        }
    }

    private boolean isDevProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile));
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
