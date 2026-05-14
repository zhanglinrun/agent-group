package com.linrun.domain.guide.adapter;

public interface GuideStreamControlRepository {

    void markStopped(String sessionId);

    boolean isStopped(String sessionId);

    void clearStopped(String sessionId);
}
