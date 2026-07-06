package com.linrun.reactor.trigger.http.reactor;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import com.linrun.domain.account.model.UserAccount;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.linrun.reactor.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import com.linrun.reactor.application.agent.dispatch.IAgentDispatchService;
import com.linrun.reactor.application.agent.query.IGptQueryApplicationService;
import com.linrun.reactor.domain.agent.runtime.executor.AgentExecutorSupport;
import com.linrun.reactor.domain.agent.reactor.config.ReactorConfig;
import com.linrun.reactor.domain.agent.reactor.model.req.AgentRequest;
import com.linrun.reactor.domain.agent.reactor.model.req.GptQueryReq;
import com.linrun.reactor.trigger.http.support.ReactorAgentUserContextResolver;
import com.linrun.reactor.trigger.http.reactor.support.SseEmitterAgentSessionStream;
import com.linrun.reactor.trigger.http.reactor.support.SseLifecycleSupport;
import com.linrun.reactor.types.agent.config.AgentExecutorNames;
import com.linrun.reactor.types.agent.config.AgentExecutorProperties;
import com.linrun.reactor.types.agent.exception.AgentExecutorBusyException;
import com.linrun.reactor.types.agent.visitor.VisitorRequestContext;

import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


//暂时不用仅做调试 前端请求发到AiAgentController
@Slf4j
@RestController
@RequestMapping("/1")
public class ReactorController {
    @Autowired
    protected ReactorConfig reactorConfig;
    @Autowired
    private IGptQueryApplicationService gptQueryApplicationService;
    @Autowired
    private IAgentDispatchService agentDispatchService;

    @Autowired
    private ConversationSessionOwnershipApplicationService conversationSessionOwnershipApplicationService;

    @Resource
    private ReactorAgentUserContextResolver reactorAgentUserContextResolver;

    @Resource
    private AgentExecutorProperties agentExecutorProperties;

    @Resource
    @Qualifier(AgentExecutorNames.DISPATCH_EXECUTOR)
    private Executor dispatchExecutor;

    @Resource
    @Qualifier(AgentExecutorNames.HEARTBEAT_SCHEDULER)
    private TaskScheduler heartbeatScheduler;

    /**
     * 执行智能体调度
     * @param request
     * @return
     * @throws UnsupportedEncodingException
     */
    @PostMapping("/AutoAgent")
    public SseEmitter AutoAgent(HttpServletRequest servletRequest,
                                @RequestBody AgentRequest request) throws UnsupportedEncodingException {

        log.info("{} auto agent request: {}", request.getRequestId(), JSON.toJSONString(request));

        Long AUTO_AGENT_SSE_TIMEOUT = 600 * 600 * 1000L;

        SseEmitter emitter = SseLifecycleSupport.createEmitter(AUTO_AGENT_SSE_TIMEOUT);
        try {
            String visitorId = reactorAgentUserContextResolver.resolveAgentUserId(servletRequest, request);
            request.setVisitorId(visitorId);
            request.setErp(visitorId);
            conversationSessionOwnershipApplicationService.ensureSessionAccessible(
                    visitorId,
                    request.getSessionId(),
                    request.getQuery()
            );
        } catch (Exception e) {
            log.warn("{} reject auto agent request before dispatch", request.getRequestId(), e);
            emitter.completeWithError(e);
            return emitter;
        }
        // SSE心跳
        ScheduledFuture<?> heartbeatFuture = SseLifecycleSupport.startHeartbeat(
                heartbeatScheduler,
                emitter,
                request.getRequestId(),
                agentExecutorProperties.getHeartbeat().getIntervalMillis(),
                log
        );
        // 监听SSE事件
        SseLifecycleSupport.registerLifecycle(emitter, request.getRequestId(), heartbeatFuture, log);

        try {
            AgentExecutorSupport.execute(dispatchExecutor, "dispatch", () -> {
                try {
                    agentDispatchService.dispatch(request, new SseEmitterAgentSessionStream(emitter));
                    emitter.complete();
                } catch (Exception e) {
                    log.error("{} auto agent error", request.getRequestId(), e);
                    emitter.completeWithError(e);
                }
            });
        } catch (AgentExecutorBusyException e) {
            log.warn("{} dispatch rejected", request.getRequestId(), e);
            emitter.completeWithError(e);
        } catch (Exception e) {
            log.error("{} auto agent error", request.getRequestId(), e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 探活接口
     *
     * @return
     */
    @RequestMapping(value = "/web/health", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }


    /**
     * 处理Agent流式增量查询请求，返回SSE事件流
     * @param params 查询请求参数对象，包含GPT查询所需信息
     * @return 返回SSE事件发射器，用于流式传输增量响应结果
     */
    @RequestMapping(value = "/web/api/v1/gpt/queryAgentStreamIncr", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryAgentStreamIncr(HttpServletRequest servletRequest,
                                           @RequestBody GptQueryReq params) {
        SseEmitter emitter = SseLifecycleSupport.createEmitter(TimeUnit.HOURS.toMillis(1));
        SseLifecycleSupport.registerLifecycle(emitter,
                Objects.toString(params.getRequestId(), "legacy-gpt-query"),
                null,
                log);
        try {
            UserAccount user = reactorAgentUserContextResolver.requireUser(servletRequest);
            params.setUser(user.getUserId());
            VisitorRequestContext.bind(user.getUserId());
            gptQueryApplicationService.queryAgentStreamIncr(params, new SseEmitterAgentSessionStream(emitter));
        } catch (Exception e) {
            log.warn("{} reject agent query before dispatch", params.getRequestId(), e);
            emitter.completeWithError(e);
        } finally {
            VisitorRequestContext.clear();
        }
        return emitter;
    }

}
