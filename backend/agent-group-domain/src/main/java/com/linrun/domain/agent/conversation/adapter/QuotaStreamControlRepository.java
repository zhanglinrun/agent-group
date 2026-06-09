package com.linrun.domain.agent.conversation.adapter;

public interface QuotaStreamControlRepository {

    void markStopped(String sessionId);

    boolean isStopped(String sessionId);

    void clearStopped(String sessionId);
}















