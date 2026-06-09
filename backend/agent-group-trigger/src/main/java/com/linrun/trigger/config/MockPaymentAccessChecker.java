package com.linrun.trigger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentAccessChecker {

    private final boolean mockPaymentEnabled;

    public MockPaymentAccessChecker(@Value("${agent.group.security.mock-payment-enabled:false}") boolean mockPaymentEnabled) {
        this.mockPaymentEnabled = mockPaymentEnabled;
    }

    public boolean isAllowed(Authentication authentication) {
        if (!mockPaymentEnabled || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ROLE_OPERATOR".equals(role));
    }
}















