package com.linrun.trigger.agent.agent.skills.manual.registry;

import com.linrun.trigger.agent.agent.skills.manual.model.SkillLoadingException;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillMetadata;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillScriptDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 文件系统技能注册表。
 *
 * 从本地文件系统加载技能。支持目录扫描，自动发现
 * 技能目录中的 SKILL.md 文件。
 *
 * @author bigchui
 * 
 */
public class FileSystemSkillRegistry extends AbstractSkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSkillRegistry.class);

    private static final String SKILL_MD_FILE = "SKILL.md";
    private static final String SCRIPTS_YAML_FILE = "scripts.yaml";
    private static final Map<String, String> EXTENSION_RUNTIME_MAP = Map.of(
            ".py", "python",
            ".js", "node",
            ".mjs", "node",
            ".cjs", "node",
            ".sh", "shell",
            ".ps1", "powershell",
            ".bat", "bat",
            ".cmd", "bat"
    );
    private static final Set<String> SUPPORTED_RUNTIMES = Set.of("python", "node", "shell", "powershell", "bat");

    private final List<Path> directories;
    private final boolean autoReload;
    private final Set<String> excludedSkills;

    private FileSystemSkillRegistry(List<Path> directories, boolean autoReload, Set<String> excludedSkills) {
        super();
        this.directories = List.copyOf(directories);
        this.autoReload = autoReload;
        this.excludedSkills = excludedSkills == null ? Set.of() : Set.copyOf(excludedSkills);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void loadSkills() throws SkillLoadingException {
        Map<String, SkillMetadata> newMetadata = new HashMap<>();

        for (Path dirPath : directories) {
            loadSkillsFromDirectory(dirPath, newMetadata);
        }

        metadataCache.putAll(newMetadata);
    }

    private void loadSkillsFromDirectory(Path dirPath, Map<String, SkillMetadata> metadataMap)
            throws SkillLoadingException {
        if (!Files.exists(dirPath)) {
            log.debug("Skills directory does not exist: {}", dirPath);
            return;
        }

        if (!Files.isDirectory(dirPath)) {
            log.warn("Skills path is not a directory: {}", dirPath);
            return;
        }

        try {
            try (var stream = Files.list(dirPath)) {
                stream.forEach(subDir -> {
                    if (!Files.isDirectory(subDir)) {
                        return;
                    }

                    String skillName = subDir.getFileName().toString();
                    if (excludedSkills.contains(skillName)) {
                        log.debug("Skipped disabled skill: {} from {}", skillName, dirPath);
                        return;
                    }

                    Path skillFile = subDir.resolve(SKILL_MD_FILE);
                    if (!Files.exists(skillFile)) {
                        return;
                    }

                    try {
                        String content = Files.readString(skillFile);
                        SkillMetadata metadata = parseSkillMetadata(skillName, content, subDir, skillFile);
                        metadataMap.put(skillName, metadata);
                        log.debug("Loaded skill: {} from {}", skillName, dirPath);
                    } catch (IOException e) {
                        log.error("Failed to read skill file: {}", skillFile, e);
                    }
                });
            }
        } catch (IOException e) {
            throw new SkillLoadingException("Failed to load skills from directory: " + dirPath, e);
        }
    }

    private SkillMetadata parseSkillMetadata(String name, String content, Path skillPath, Path skillFile) {
        String description = extractDescription(name, content);
        List<String> allowedTools = extractAllowedTools(content);
        Map<String, SkillScriptDefinition> scripts = discoverSkillScripts(skillPath);

        return SkillMetadata.builder()
                .name(name)
                .description(description)
                .skillPath(skillPath)
                .source(SkillMetadata.SkillSource.PROJECT)
                .allowedTools(allowedTools)
                .skillFile(skillFile)
                .scripts(scripts)
                .build();
    }

    private Map<String, SkillScriptDefinition> discoverSkillScripts(Path skillPath) {
        Path normalizedSkillPath = skillPath.toAbsolutePath().normalize();
        Map<String, SkillScriptDefinition> scripts = discoverFromScriptsDirectory(normalizedSkillPath);
        discoverFromScriptsYaml(normalizedSkillPath).forEach(scripts::put);
        return scripts;
    }

    private Map<String, SkillScriptDefinition> discoverFromScriptsDirectory(Path skillPath) {
        Path scriptsDir = skillPath.resolve("scripts");
        if (!Files.isDirectory(scriptsDir)) {
            return new LinkedHashMap<>();
        }

        Map<String, SkillScriptDefinition> scripts = new LinkedHashMap<>();
        try (var pathStream = Files.walk(scriptsDir)) {
            List<Path> scriptPaths = pathStream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                    .toList();
            for (Path scriptPath : scriptPaths) {
                String runtime = inferRuntime(scriptPath);
                if (runtime == null) {
                    continue;
                }
                String scriptName = stripExtension(scriptPath.getFileName().toString());
                if (scripts.containsKey(scriptName)) {
                    throw new SkillLoadingException("Duplicate script name under skill: " + scriptName);
                }
                scripts.put(scriptName, scriptDefinition(skillPath, scriptName, scriptPath, runtime,
                        "自动发现脚本", Map.of("source", "auto")));
            }
            return scripts;
        } catch (IOException e) {
            throw new SkillLoadingException("Failed to discover scripts under: " + scriptsDir, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, SkillScriptDefinition> discoverFromScriptsYaml(Path skillPath) {
        Path scriptsYaml = skillPath.resolve(SCRIPTS_YAML_FILE);
        if (!Files.isRegularFile(scriptsYaml)) {
            return new LinkedHashMap<>();
        }

        try {
            Object loaded = new Yaml().load(Files.readString(scriptsYaml));
            if (!(loaded instanceof Map<?, ?> loadedMap)) {
                throw new SkillLoadingException("scripts.yaml must be a yaml map: " + scriptsYaml);
            }
            Object scriptsNode = loadedMap.containsKey("scripts") ? loadedMap.get("scripts") : loadedMap;
            Map<String, SkillScriptDefinition> scripts = new LinkedHashMap<>();
            if (scriptsNode instanceof Map<?, ?> scriptsMap) {
                for (Map.Entry<?, ?> entry : scriptsMap.entrySet()) {
                    String scriptName = String.valueOf(entry.getKey()).trim();
                    scripts.put(scriptName, configuredScript(skillPath, scriptName, normalizeScriptConfig(entry.getValue(), scriptsYaml)));
                }
                return scripts;
            }
            if (scriptsNode instanceof List<?> scriptList) {
                for (Object item : scriptList) {
                    if (!(item instanceof Map<?, ?> itemMap)) {
                        throw new SkillLoadingException("scripts list item must be a map: " + scriptsYaml);
                    }
                    Map<String, Object> config = new LinkedHashMap<>();
                    itemMap.forEach((key, value) -> {
                        if (key != null) {
                            config.put(String.valueOf(key), value);
                        }
                    });
                    String scriptName = firstConfigValue(config, "name", "script_name");
                    if (scriptName.isBlank()) {
                        throw new SkillLoadingException("script name is required in: " + scriptsYaml);
                    }
                    scripts.put(scriptName, configuredScript(skillPath, scriptName, config));
                }
                return scripts;
            }
            throw new SkillLoadingException("Unsupported scripts.yaml structure: " + scriptsYaml);
        } catch (IOException e) {
            throw new SkillLoadingException("Failed to read scripts.yaml: " + scriptsYaml, e);
        }
    }

    private Map<String, Object> normalizeScriptConfig(Object rawConfig, Path scriptsYaml) {
        if (rawConfig instanceof String path) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("path", path);
            return config;
        }
        if (!(rawConfig instanceof Map<?, ?> rawMap)) {
            throw new SkillLoadingException("script config must be a string or map: " + scriptsYaml);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                config.put(String.valueOf(key), value);
            }
        });
        return config;
    }

    private SkillScriptDefinition configuredScript(Path skillPath, String scriptName, Map<String, Object> config) {
        String relativePath = firstConfigValue(config, "path", "script", "relative_path");
        if (relativePath.isBlank()) {
            throw new SkillLoadingException("script path is required: " + scriptName);
        }
        Path absolutePath = ensureUnderSkillPath(skillPath, skillPath.resolve(relativePath));
        if (!Files.isRegularFile(absolutePath)) {
            throw new SkillLoadingException("configured script does not exist: " + absolutePath);
        }
        String runtime = firstConfigValue(config, "runtime");
        if (runtime.isBlank()) {
            runtime = inferRuntime(absolutePath);
        }
        if (runtime == null || !SUPPORTED_RUNTIMES.contains(runtime.toLowerCase(Locale.ROOT))) {
            throw new SkillLoadingException("unsupported runtime for script " + scriptName + ": " + runtime);
        }
        String description = firstConfigValue(config, "description", "desc");
        Map<String, Object> metadata = new LinkedHashMap<>(config);
        List.of("name", "script_name", "path", "script", "relative_path", "runtime", "description", "desc")
                .forEach(metadata::remove);
        metadata.put("source", "config");
        return scriptDefinition(skillPath, scriptName, absolutePath, runtime.toLowerCase(Locale.ROOT),
                description.isBlank() ? "脚本定义来源 scripts.yaml" : description, metadata);
    }

    private SkillScriptDefinition scriptDefinition(Path skillPath,
                                                   String scriptName,
                                                   Path scriptPath,
                                                   String runtime,
                                                   String description,
                                                   Map<String, Object> metadata) {
        Path normalizedPath = ensureUnderSkillPath(skillPath, scriptPath);
        return SkillScriptDefinition.builder()
                .scriptName(scriptName)
                .relativePath(normalizeRelativePath(skillPath.relativize(normalizedPath)))
                .absolutePath(normalizedPath)
                .runtime(runtime)
                .description(description)
                .metadata(metadata)
                .build();
    }

    private Path ensureUnderSkillPath(Path skillPath, Path candidatePath) {
        Path root = skillPath.toAbsolutePath().normalize();
        Path candidate = candidatePath.toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            throw new SkillLoadingException("script path escapes skill directory: " + candidate);
        }
        return candidate;
    }

    private String inferRuntime(Path scriptPath) {
        String fileName = scriptPath.getFileName().toString().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : EXTENSION_RUNTIME_MAP.entrySet()) {
            if (fileName.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String normalizeRelativePath(Path relativePath) {
        List<String> parts = new ArrayList<>();
        for (Path pathPart : relativePath) {
            parts.add(pathPart.toString());
        }
        return String.join("/", parts);
    }

    private String firstConfigValue(Map<String, Object> config, String... keys) {
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private String extractDescription(String name, String content) {
        String frontmatter = extractFrontmatter(content);
        if (frontmatter != null) {
            try {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(frontmatter);
                if (data != null && data.containsKey("description")) {
                    Object desc = data.get("description");
                    if (desc != null) {
                        return desc.toString();
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse frontmatter YAML for skill: {}", name, e);
            }
        }

        String withoutFrontmatter = removeFrontmatter(content);
        String[] lines = withoutFrontmatter.split("\n");
        StringBuilder desc = new StringBuilder();
        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                if (!desc.isEmpty()) break;
                continue;
            }
            if (desc.length() > 0) desc.append(" ");
            desc.append(line.trim());
        }
        return desc.length() > 0 ? desc.toString() : "Skill: " + name;
    }

    private List<String> extractAllowedTools(String content) {
        String frontmatter = extractFrontmatter(content);
        if (frontmatter == null) {
            return null;
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(frontmatter);
            if (data != null && data.containsKey("allowedTools")) {
                Object toolsObj = data.get("allowedTools");
                if (toolsObj instanceof List<?> toolsList) {
                    List<String> tools = new ArrayList<>();
                    for (Object item : toolsList) {
                        if (item != null) {
                            tools.add(item.toString());
                        }
                    }
                    return tools.isEmpty() ? null : tools;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse allowedTools from frontmatter", e);
        }

        return null;
    }

    private String extractFrontmatter(String content) {
        if (!content.startsWith("---")) {
            return null;
        }
        int endIndex = content.indexOf("---", 3);
        if (endIndex < 0) {
            return null;
        }
        return content.substring(3, endIndex);
    }

    private String removeFrontmatter(String content) {
        if (!content.startsWith("---")) {
            return content;
        }
        int endIndex = content.indexOf("---", 3);
        if (endIndex < 0) {
            return content;
        }
        return content.substring(endIndex + 3).trim();
    }

    @Override
    protected String loadContent(SkillMetadata metadata) throws SkillLoadingException {
        try {
            return Files.readString(metadata.skillFile());
        } catch (IOException e) {
            throw SkillLoadingException.ioException(metadata.name(), metadata.skillFile().toString(), e);
        }
    }

    @Override
    public void reload() throws SkillLoadingException {
        if (!autoReload) {
            throw new UnsupportedOperationException("Auto reload is not enabled");
        }
        clearCache();
        log.debug("Skills reloaded");
    }

    public static class Builder {
        private final List<Path> directories = new ArrayList<>();
        private final Set<String> excludedSkills = new HashSet<>();
        private boolean autoReload = false;

        public Builder addDirectory(String path) {
            return addDirectory(Path.of(path));
        }

        public Builder addDirectory(Path path) {
            directories.add(path);
            return this;
        }

        public Builder excludedSkills(Set<String> skillNames) {
            if (skillNames != null) {
                skillNames.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .forEach(excludedSkills::add);
            }
            return this;
        }

        public Builder autoReload(boolean autoReload) {
            this.autoReload = autoReload;
            return this;
        }

        public FileSystemSkillRegistry build() {
            return new FileSystemSkillRegistry(directories, autoReload, excludedSkills);
        }
    }
}















