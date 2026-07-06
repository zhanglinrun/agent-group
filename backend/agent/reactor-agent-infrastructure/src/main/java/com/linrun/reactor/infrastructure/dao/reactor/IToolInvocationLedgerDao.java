package com.linrun.reactor.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.linrun.reactor.domain.agent.ledger.entity.ToolInvocation;
import com.linrun.reactor.domain.agent.ledger.model.ToolInvocationView;

import java.util.List;

/**
 * 工具调用账本 DAO。
 */
@Mapper
public interface IToolInvocationLedgerDao {

    int insertToolInvocation(ToolInvocation invocation);

    int updateToolInvocationFinish(ToolInvocation invocation);

    List<ToolInvocation> queryByRunId(@Param("runId") Long runId);

    List<ToolInvocation> queryByRunIds(@Param("runIds") List<Long> runIds);

    List<ToolInvocation> queryByLlmInvocationIds(@Param("llmInvocationIds") List<Long> llmInvocationIds);

    List<ToolInvocationView> queryRecentByToolName(@Param("toolName") String toolName,
                                                   @Param("limit") int limit);
}
