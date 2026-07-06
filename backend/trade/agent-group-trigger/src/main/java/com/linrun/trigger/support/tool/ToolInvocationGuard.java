package com.linrun.trigger.support.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具调用准入守卫。
 *
 * 通过配置控制 Agent 可调用的工具范围：
 * - agent.group.tool.blocked-tools：逗号分隔的禁用工具名，命中即拒绝；
 * - agent.group.tool.allowed-tools：逗号分隔的白名单，配置后只放行名单内的工具。
 *
 * 两项默认都为空，即默认放行全部工具，行为与历史版本一致；
 * 需要收紧时只改配置，不用改代码。
 */
@Component
public class ToolInvocationGuard {

    private final Set<String> allowedTools;
    private final Set<String> blockedTools;

    public ToolInvocationGuard(
            @Value("${agent.group.tool.allowed-tools:}") String allowedTools,
            @Value("${agent.group.tool.blocked-tools:}") String blockedTools) {
        this.allowedTools = parse(allowedTools);
        this.blockedTools = parse(blockedTools);
    }

    /**
     * 返回拒绝原因；空表示放行。
     */
    public Optional<String> rejectReason(String toolName) {
        String name = normalize(toolName);
        if (!StringUtils.hasText(name)) {
            return Optional.of("tool name is blank");
        }
        if (blockedTools.contains(name)) {
            return Optional.of("tool is blocked by policy: " + name);
        }
        if (!allowedTools.isEmpty() && !allowedTools.contains(name)) {
            return Optional.of("tool is not in allowed list: " + name);
        }
        return Optional.empty();
    }

    private Set<String> parse(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
