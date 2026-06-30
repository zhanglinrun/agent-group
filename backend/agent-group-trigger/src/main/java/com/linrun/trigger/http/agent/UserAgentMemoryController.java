package com.linrun.trigger.http.agent;

import com.linrun.api.dto.UserAgentMemoryRequest;
import com.linrun.api.dto.UserAgentMemoryResponse;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.academic.memory.model.UserAgentMemory;
import com.linrun.domain.academic.memory.service.UserAgentMemoryService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/academic/memories")
public class UserAgentMemoryController {

    private final UserAccountService userAccountService;
    private final UserAgentMemoryService memoryService;

    public UserAgentMemoryController(UserAccountService userAccountService,
                                     UserAgentMemoryService memoryService) {
        this.userAccountService = userAccountService;
        this.memoryService = memoryService;
    }

    @GetMapping
    public Response<List<UserAgentMemoryResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String token) {
        UserAccount user = userAccountService.requireUserByToken(token);
        List<UserAgentMemoryResponse> memories = memoryService.query(user.getUserId(), 50).stream()
                .map(this::toResponse)
                .toList();
        return Response.success(memories, RequestTraceContext.getRequestId());
    }

    @PostMapping
    public Response<UserAgentMemoryResponse> save(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) UserAgentMemoryRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        UserAgentMemoryRequest safeRequest = request == null ? new UserAgentMemoryRequest() : request;
        UserAgentMemory memory = memoryService.save(user.getUserId(), safeRequest.getMemoryType(),
                safeRequest.getContent(), safeRequest.getEnabled());
        return Response.success(toResponse(memory), RequestTraceContext.getRequestId());
    }

    @DeleteMapping("/{memoryType}")
    public Response<Boolean> disable(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String memoryType) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(memoryService.disable(user.getUserId(), memoryType),
                RequestTraceContext.getRequestId());
    }

    private UserAgentMemoryResponse toResponse(UserAgentMemory memory) {
        UserAgentMemoryResponse response = new UserAgentMemoryResponse();
        response.setMemoryType(memory.getMemoryType());
        response.setContent(memory.getContent());
        response.setEnabled(Boolean.TRUE.equals(memory.getEnabled()));
        response.setUpdateTime(memory.getUpdateTime());
        return response;
    }
}
