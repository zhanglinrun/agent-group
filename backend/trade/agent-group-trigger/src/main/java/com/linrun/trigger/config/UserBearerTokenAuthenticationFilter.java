package com.linrun.trigger.config;

import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
public class UserBearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final UserAccountService userAccountService;

    public UserBearerTokenAuthenticationFilter(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && isBearerToken(authorization)) {
            authenticateUser(request, authorization);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateUser(HttpServletRequest request, String authorization) {
        try {
            UserAccount user = userAccountService.requireUserByToken(authorization);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user.getUserId(),
                    null,
                    List.of(new SimpleGrantedAuthority(roleAuthority(user.getRole()))));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException ignored) {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isBearerToken(String authorization) {
        return StringUtils.hasText(authorization)
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    private String roleAuthority(String role) {
        String normalized = StringUtils.hasText(role)
                ? role.trim().toUpperCase(Locale.ROOT)
                : "USER";
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}















