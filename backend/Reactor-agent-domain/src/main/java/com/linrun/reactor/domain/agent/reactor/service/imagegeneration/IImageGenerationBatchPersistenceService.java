package com.linrun.reactor.domain.agent.reactor.service.imagegeneration;

import com.linrun.reactor.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;

/**
 * 生图批次持久化服务。
 */
public interface IImageGenerationBatchPersistenceService {

    void persistWorkspaceBatch(String requestId, ImageGenerationExecutionResult result);
}
