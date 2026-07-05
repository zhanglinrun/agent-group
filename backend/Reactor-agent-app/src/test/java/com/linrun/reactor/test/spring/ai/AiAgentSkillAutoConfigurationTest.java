package com.linrun.reactor.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import com.linrun.reactor.config.AiAgentSkillAutoConfiguration;
import com.linrun.reactor.config.AiAgentSkillProperties;
import com.linrun.reactor.config.SkillDirectoryResolver;
import com.linrun.reactor.domain.agent.runtime.tool.skill.SkillRuntimeOptions;

import java.nio.file.Path;
import java.util.List;

/**
 * Skill 自动装配测试。
 */
public class AiAgentSkillAutoConfigurationTest {

    @Test
    public void shouldResolveDirectoriesBeforeBuildingRuntimeOptions() {
        AiAgentSkillProperties properties = new AiAgentSkillProperties();
        properties.setEnabled(true);
        properties.setDirectories(List.of("D:/invalid/project/runtime/skills"));

        SkillDirectoryResolver resolver = new SkillDirectoryResolver(Path.of("D:/repo/Reactor-agent/Reactor-agent-app"));
        AiAgentSkillAutoConfiguration autoConfiguration = new AiAgentSkillAutoConfiguration(resolver);

        SkillRuntimeOptions options = autoConfiguration.skillRuntimeOptions(properties);

        Assert.assertTrue(options.isEnabled());
        Assert.assertTrue(options.getDirectories().isEmpty());
    }
}
