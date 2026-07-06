package com.linrun.trigger.http.market;

import com.linrun.api.dto.NotifyTaskExecuteResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/market/notify")
public class NotifyTaskController {

    private final NotifyTaskService notifyTaskService;

    public NotifyTaskController(NotifyTaskService notifyTaskService) {
        this.notifyTaskService = notifyTaskService;
    }

    @PostMapping("/exec_job")
    public Response<NotifyTaskExecuteResponse> execJob(@RequestParam(required = false) String teamId) {
        return Response.success(notifyTaskService.execNotifyJob(teamId), RequestTraceContext.getRequestId());
    }

    @PostMapping("/exec_task")
    public Response<NotifyTaskExecuteResponse> execTask(@RequestParam String uuid) {
        return Response.success(notifyTaskService.execNotifyTask(uuid), RequestTraceContext.getRequestId());
    }
}















