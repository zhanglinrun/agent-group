package com.linrun.trigger.http;

import com.linrun.api.dto.LoginRequest;
import com.linrun.api.dto.LoginResponse;
import com.linrun.api.dto.RegisterRequest;
import com.linrun.api.dto.UserProfileResponse;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserAccountService userAccountService;

    public AuthController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PostMapping("/register")
    public Response<LoginResponse> register(@RequestBody RegisterRequest request) {
        return Response.success(userAccountService.register(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/login")
    public Response<LoginResponse> login(@RequestBody LoginRequest request) {
        return Response.success(userAccountService.login(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/logout")
    public Response<Boolean> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        userAccountService.logout(token);
        return Response.success(Boolean.TRUE, RequestTraceContext.getRequestId());
    }

    @GetMapping("/profile")
    public Response<UserProfileResponse> profile(@RequestHeader(value = "Authorization", required = false) String token) {
        return Response.success(userAccountService.profile(token), RequestTraceContext.getRequestId());
    }
}
