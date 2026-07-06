package com.linrun.reactor.domain.agent.runtime;

import lombok.Builder;
import lombok.Value;
import org.springframework.core.env.Environment;
import com.linrun.reactor.domain.agent.adapter.port.AgentQuotaPort;
import com.linrun.reactor.domain.agent.adapter.port.FileArtifactPort;
import com.linrun.reactor.domain.agent.adapter.port.RemoteHttpPort;
import com.linrun.reactor.domain.agent.adapter.port.RemoteStreamPort;
import com.linrun.reactor.domain.agent.runtime.llm.LLMSettings;
import com.linrun.reactor.domain.agent.runtime.tool.mcp.runtime.McpToolExecutor;
import com.linrun.reactor.domain.agent.reactor.config.ReactorConfig;
import com.linrun.reactor.domain.agent.reactor.service.imagegeneration.IImageGenerationExecutionKernel;
import org.springframework.scheduling.TaskScheduler;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Reactor 运行时依赖包。
 * domain 侧只依赖这个 typed bundle，不再直接触碰 Spring 容器全局入口。
 */
@Value
@Builder(toBuilder = true)
public class ReactorRuntimeDependencies {

    ReactorConfig reactorConfig;

    Environment environment;

    ReactorLlmDependencies llmDependencies;

    McpToolExecutor mcpToolExecutor;

    IImageGenerationExecutionKernel imageGenerationExecutionKernel;

    RemoteHttpPort remoteHttpPort;

    RemoteStreamPort remoteStreamPort;

    FileArtifactPort fileArtifactPort;

    AgentQuotaPort agentQuotaPort;

    //预留给之后并发调用llm
    Executor llmExecutor;

    Executor taskExecutor;

    Executor toolExecutor;

    TaskScheduler heartbeatScheduler;

    public ReactorConfig requireReactorConfig() {
        return Objects.requireNonNull(reactorConfig, "ReactorConfig must not be null");
    }

    public Environment requireEnvironment() {
        return Objects.requireNonNull(environment, "Environment must not be null");
    }

    public ReactorLlmDependencies requireLlmDependencies() {
        return Objects.requireNonNull(llmDependencies, "ReactorLlmDependencies must not be null");
    }

    public McpToolExecutor getOptionalMcpToolExecutor() {
        return mcpToolExecutor;
    }

    public IImageGenerationExecutionKernel requireImageGenerationExecutionKernel() {
        return Objects.requireNonNull(imageGenerationExecutionKernel, "IImageGenerationExecutionKernel must not be null");
    }

    public RemoteHttpPort requireRemoteHttpPort() {
        return Objects.requireNonNull(remoteHttpPort, "RemoteHttpPort must not be null");
    }

    public RemoteStreamPort requireRemoteStreamPort() {
        return Objects.requireNonNull(remoteStreamPort, "RemoteStreamPort must not be null");
    }

    public FileArtifactPort requireFileArtifactPort() {
        return Objects.requireNonNull(fileArtifactPort, "FileArtifactPort must not be null");
    }

    public AgentQuotaPort getOptionalAgentQuotaPort() {
        return agentQuotaPort;
    }

    public Executor requireLlmExecutor() {
        return Objects.requireNonNull(llmExecutor, "llmExecutor must not be null");
    }

    public Executor requireToolExecutor() {
        return Objects.requireNonNull(toolExecutor, "toolExecutor must not be null");
    }

    public Executor requireTaskExecutor() {
        return Objects.requireNonNull(taskExecutor, "taskExecutor must not be null");
    }

    public TaskScheduler requireHeartbeatScheduler() {
        return Objects.requireNonNull(heartbeatScheduler, "heartbeatScheduler must not be null");
    }

    /**
     * 统一解析 LLM 配置。
     * 环境中的 llm.default / spring.ai.openai 是部署级网关配置，优先级高于库表/llm.settings 中的旧地址。
     */
    public LLMSettings resolveLlmSettings(String modelName) {
        ReactorConfig config = requireReactorConfig();
        String normalizedModelName = modelName == null ? "" : modelName.trim();
        LLMSettings defaultConfig = buildDefaultLlmSettings();
        if (config.getLlmSettingsMap() != null && !normalizedModelName.isBlank()) {
            LLMSettings settings = config.getLlmSettingsMap().get(normalizedModelName);
            if (settings != null) {
                return mergeWithDefaultGateway(settings, defaultConfig);
            }
        }

        if (!normalizedModelName.isBlank()) {
            defaultConfig.setModel(normalizedModelName);
        }
        return defaultConfig;
    }

    private LLMSettings buildDefaultLlmSettings() {
        Environment env = requireEnvironment();
        return LLMSettings.builder()
                .model(env.getProperty("llm.default.model", "gpt-4o-0806"))
                .maxTokens(parseInt(env.getProperty("llm.default.max_tokens"), 16384))
                .temperature(parseDouble(env.getProperty("llm.default.temperature"), 0.0))
                .baseUrl(firstConfigured(env,
                        "llm.default.base_url",
                        "llm.default.base-url",
                        "spring.ai.openai.base-url"))
                .interfaceUrl(firstConfiguredOrDefault(env,
                        "/v1/chat/completions",
                        "llm.default.interface_url",
                        "llm.default.interface-url",
                        "spring.ai.openai.chat.completions-path"))
                .functionCallType(firstConfiguredOrDefault(env,
                        "function_call",
                        "llm.default.function_call_type",
                        "llm.default.function-call-type"))
                .apiKey(firstConfigured(env,
                        "llm.default.apikey",
                        "llm.default.api_key",
                        "llm.default.api-key",
                        "spring.ai.openai.api-key"))
                .maxInputTokens(parseInt(env.getProperty("llm.default.max_input_tokens"), 100000))
                .extParams(new HashMap<>())
                .build();
    }

    private LLMSettings mergeWithDefaultGateway(LLMSettings settings, LLMSettings defaultConfig) {
        return LLMSettings.builder()
                .model(StringUtils.defaultIfBlank(settings.getModel(), defaultConfig.getModel()))
                .maxTokens(settings.getMaxTokens() > 0 ? settings.getMaxTokens() : defaultConfig.getMaxTokens())
                .temperature(settings.getTemperature() != 0.0 ? settings.getTemperature() : defaultConfig.getTemperature())
                .apiType(settings.getApiType())
                .apiKey(StringUtils.defaultIfBlank(defaultConfig.getApiKey(), settings.getApiKey()))
                .apiVersion(settings.getApiVersion())
                .baseUrl(StringUtils.defaultIfBlank(defaultConfig.getBaseUrl(), settings.getBaseUrl()))
                .interfaceUrl(StringUtils.defaultIfBlank(defaultConfig.getInterfaceUrl(), settings.getInterfaceUrl()))
                .functionCallType(StringUtils.defaultIfBlank(settings.getFunctionCallType(), defaultConfig.getFunctionCallType()))
                .maxInputTokens(settings.getMaxInputTokens() > 0 ? settings.getMaxInputTokens() : defaultConfig.getMaxInputTokens())
                .extParams(settings.getExtParams() == null ? new HashMap<>() : settings.getExtParams())
                .build();
    }

    private String firstConfigured(Environment env, String... keys) {
        return firstConfiguredOrDefault(env, "", keys);
    }

    private String firstConfiguredOrDefault(Environment env, String defaultValue, String... keys) {
        for (String key : keys) {
            String value = env.getProperty(key);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return defaultValue;
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    private double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
