package com.linrun.reactor.test.domain;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import com.linrun.reactor.domain.agent.reactor.model.req.AgentRequest;
import com.linrun.reactor.domain.agent.service.execute.react.step.RootNode;
import com.linrun.reactor.domain.agent.service.execute.react.step.RunReactNode;
import com.linrun.reactor.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

public class StepReactNodeRoutingTest {

    @Test
    public void shouldRouteReactRootNodeToRunReactNode() throws Exception {
        RootNode rootNode = new RootNode();
        RunReactNode runReactNode = Mockito.mock(RunReactNode.class);
        ReflectionTestUtils.setField(rootNode, "step2RunReactNode", runReactNode);

        AgentRequest request = new AgentRequest();
        request.setRequestId("request-" + System.currentTimeMillis());
        request.setQuery("测试 ReAct 路由");
        request.setSessionId("session-" + System.currentTimeMillis());

        DefaultReactAgentExecuteStrategyFactory.DynamicContext context = new DefaultReactAgentExecuteStrategyFactory.DynamicContext();

        StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> next = rootNode.get(request, context);
        Assert.assertNotNull("React 根节点应当能路由到执行节点", next);
        Assert.assertSame("React 根节点应直接路由到 ReAct 执行节点", runReactNode, next);
    }
}
