package com.linrun.trigger.config;

import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentAccessChecker {

    private final boolean mockPaymentEnabled;

    public MockPaymentAccessChecker(
            @Value("${agent.group.security.mock-payment-enabled:false}") boolean mockPaymentEnabled) {
        this.mockPaymentEnabled = mockPaymentEnabled;
    }

    public boolean isAllowed(Authentication authentication) {
        return mockPaymentEnabled || isAdmin(authentication);
    }

    public void assertAllowed() {
        if (!isAllowed(SecurityContextHolder.getContext().getAuthentication())) {
            throw new AppException("PAY_0007", "模拟支付只允许显式开启或管理员使用");
        }
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
