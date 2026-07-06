package com.linrun.trigger.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessAuditFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessAuditFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startMillis = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            LOGGER.info("access audit, method={}, path={}, status={}, costMillis={}",
                    request.getMethod(),
                    safePath(request),
                    response.getStatus(),
                    System.currentTimeMillis() - startMillis);
        }
    }

    private String safePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null ? "" : uri.replaceAll("[\\r\\n\\t]", "");
    }
}















