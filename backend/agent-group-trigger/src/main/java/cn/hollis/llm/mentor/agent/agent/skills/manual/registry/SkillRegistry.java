package cn.hollis.llm.mentor.agent.agent.skills.manual.registry;

import cn.hollis.llm.mentor.agent.agent.skills.manual.model.SkillLoadingException;
import cn.hollis.llm.mentor.agent.agent.skills.manual.model.SkillMetadata;

import java.util.List;

/**
 * 技能注册表接口。
 *
 * 定义技能的注册、查询和加载操作。
 *
 * @author bigchui
 * 
 */
public interface SkillRegistry {

    List<SkillMetadata> listAll() throws SkillLoadingException;

    SkillMetadata get(String name) throws SkillLoadingException;

    boolean contains(String name);

    int size();

    String readSkillContent(String name) throws SkillLoadingException;

    default void reload() throws SkillLoadingException {
        throw new UnsupportedOperationException("Reload not supported");
    }

    default void clearCache() {
        // 默认不做任何操作
    }
}
