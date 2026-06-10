package com.linrun.trigger.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConfigTest {

    @Test
    void shouldUseUserLoginMessageForUserEndpoints() {
        assertEquals("请先登录后再访问该接口", SecurityConfig.authenticationInfo("/api/v1/quota/summary"));
        assertEquals("请先登录后再访问该接口", SecurityConfig.authenticationInfo("/api/v1/academic/stream"));
        assertEquals("请先登录后再访问该接口", SecurityConfig.authenticationInfo("/api/v1/academic/capabilities"));
    }

    @Test
    void shouldUseOperatorMessageForAdminEndpoints() {
        assertEquals("请使用运营账号访问该接口", SecurityConfig.authenticationInfo("/api/v1/mcp/admin/servers"));
        assertEquals("请使用运营账号访问该接口", SecurityConfig.authenticationInfo("/api/v1/agent/admin/configs"));
        assertEquals("请使用运营账号访问该接口", SecurityConfig.authenticationInfo("/api/v1/quota/admin/grant-by-orders"));
        assertEquals("请使用运营账号访问该接口", SecurityConfig.authenticationInfo("/api/v1/knowledge/documents"));
    }
}















