package com.linrun.reactor.domain.agent.reactor.service.imagegeneration;

import com.linrun.reactor.domain.agent.reactor.model.imagegeneration.ImageGenerationExecuteCommand;
import com.linrun.reactor.domain.agent.reactor.model.imagegeneration.ImageGenerationExecutionResult;

/**
 * 生图执行内核。
 */
public interface IImageGenerationExecutionKernel {

    ImageGenerationExecutionResult execute(ImageGenerationExecuteCommand command);
}
