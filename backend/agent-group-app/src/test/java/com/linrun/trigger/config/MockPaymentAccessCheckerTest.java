package com.linrun.trigger.config;

import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPaymentAccessCheckerTest {

    @Test
    void shouldAllowWhenExplicitlyEnabled() {
        MockPaymentAccessChecker checker = new MockPaymentAccessChecker(true);

        assertTrue(checker.isAllowed(null));
    }

    @Test
    void shouldAllowAdminWhenDisabled() {
        MockPaymentAccessChecker checker = new MockPaymentAccessChecker(false);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "admin",
                "password",
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));

        assertTrue(checker.isAllowed(authentication));
    }

    @Test
    void shouldRejectAnonymousWhenDisabled() {
        MockPaymentAccessChecker checker = new MockPaymentAccessChecker(false);

        try {
            AppException exception = assertThrows(AppException.class, checker::assertAllowed);

            assertEquals("PAY_0007", exception.getCode());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
