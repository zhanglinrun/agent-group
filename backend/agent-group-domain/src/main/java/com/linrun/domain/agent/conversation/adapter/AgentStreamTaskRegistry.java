package com.linrun.domain.agent.conversation.adapter;

public interface AgentStreamTaskRegistry {

    boolean register(String sessionId, String requestId);

    void bind(String sessionId, String requestId, Runnable cancelCallback);

    void complete(String sessionId, String requestId);

    static AgentStreamTaskRegistry noop() {
        return new AgentStreamTaskRegistry() {
            @Override
            public boolean register(String sessionId, String requestId) {
                return true;
            }

            @Override
            public void bind(String sessionId, String requestId, Runnable cancelCallback) {
            }

            @Override
            public void complete(String sessionId, String requestId) {
            }
        };
    }
}
