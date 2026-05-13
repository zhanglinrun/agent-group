package com.linrun.trigger.http;

import com.linrun.api.agent.request.GuideStreamRequest;
import com.linrun.api.agent.response.GuideStreamEvent;
import com.linrun.trigger.service.AgentGuideStreamService;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 导购流式接口。
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/agent")
public class AgentGuideController {

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();
    private final AgentGuideStreamService agentGuideStreamService;

    public AgentGuideController(AgentGuideStreamService agentGuideStreamService) {
        this.agentGuideStreamService = agentGuideStreamService;
    }

    @PostMapping(value = "/guide/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter guideStream(@RequestBody GuideStreamRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        streamExecutor.submit(() -> doGuideStream(request, emitter));
        return emitter;
    }

    private void doGuideStream(GuideStreamRequest request, SseEmitter emitter) {
        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId()
                : "S" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        try {
            for (GuideStreamEvent<?> event : agentGuideStreamService.buildEvents(request, sessionId, requestId)) {
                send(emitter, event);
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void send(SseEmitter emitter, GuideStreamEvent<?> event) throws IOException {
        emitter.send(SseEmitter.event().name(event.getEvent()).data(event, MediaType.APPLICATION_JSON));
        pause();
    }

    private void pause() {
        try {
            Thread.sleep(350L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void destroy() {
        streamExecutor.shutdown();
    }
}
