package com.linrun.reactor.application.agent.task;

import com.linrun.reactor.domain.agent.model.valobj.AiAgentTaskScheduleVO;

import java.util.List;

/**
 * Agent 应用层任务接口。
 */
public interface ITaskService {

    List<AiAgentTaskScheduleVO> queryAllValidTaskSchedule();

    List<Long> queryAllInvalidTaskScheduleIds();
}
