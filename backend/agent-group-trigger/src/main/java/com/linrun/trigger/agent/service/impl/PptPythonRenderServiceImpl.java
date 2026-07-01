package com.linrun.trigger.agent.service.impl;

import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.entity.record.pptx.AiPptTemplate;
import com.linrun.trigger.agent.service.AiPptTemplateService;
import com.linrun.trigger.agent.service.PptPythonRenderService;
import com.linrun.domain.agent.file.adapter.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PPT Python 渲染服务实现
 */
@Slf4j
@Service
public class PptPythonRenderServiceImpl implements PptPythonRenderService {

    private final AiPptTemplateService templateService;
    private final FileStoragePort fileStoragePort;

    public PptPythonRenderServiceImpl(AiPptTemplateService templateService, FileStoragePort fileStoragePort) {
        this.templateService = templateService;
        this.fileStoragePort = fileStoragePort;
    }

    @Override
    public String renderPpt(AiPptInst inst, String pptSchema) throws Exception {

        log.info("开始渲染PPT: instId={}", inst.getId());

        // ---------- 获取模板 ----------
        AiPptTemplate template = templateService.getByCode(inst.getTemplateCode());
        if (template == null) {
            throw new RuntimeException("模板不存在 " + inst.getTemplateCode());
        }

        // 本次渲染产生的临时文件统一登记，结束后无论成败都清理，避免长期运行后临时目录膨胀
        List<Path> tempFiles = new ArrayList<>();
        String outputFilePath = "";
        try {
            String pythonScriptPath = copyClasspathResource("agent-runtime/python/render_ppt.py",
                    "agent_agent_render_", ".py", tempFiles);
            String templateFilePath = resolveTemplateFilePath(template.getFilePath(), tempFiles);
            String outputDir = getOutputDir();

            String outputFileName = "ppt_" + inst.getId() + "_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".pptx";
            outputFilePath = outputDir + File.separator + outputFileName;

            File templateFile = new File(templateFilePath);
            if (!templateFile.exists()) {
                throw new RuntimeException("模板文件不存在 " + templateFilePath);
            }

            // ---------- 构建命令 ----------
            List<String> command = List.of(
                    "python",
                    pythonScriptPath,
                    "--template", templateFilePath,
                    "--output", outputFilePath
            );

            log.info("执行Python命令: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            env.put("PYTHONIOENCODING", "utf-8");

            // ---------- 处理 JSON 传参 ----------
            // Windows 环境变量长度有限（约 32KB），大 JSON 自动改走临时文件
            if (pptSchema.length() > 20000) {
                Path schemaFile = Files.createTempFile("ppt_schema_", ".json");
                Files.writeString(schemaFile, pptSchema, StandardOpenOption.TRUNCATE_EXISTING);
                tempFiles.add(schemaFile);
                env.put("PPT_SCHEMA_FILE", schemaFile.toAbsolutePath().toString());
                log.info("JSON 过大，使用临时文件传参 {}", schemaFile);
            } else {
                env.put("PPT_SCHEMA", pptSchema);
            }

            // ---------- 启动并等待 ----------
            // 输出在后台线程读取：如果在主线程阻塞读，Python 卡住不退出时会永远读不到 EOF，
            // 超时控制就失效了；waitFor(超时) 才是真正的兜底
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (output) {
                            output.append(line).append("\n");
                        }
                        log.info("Python输出: {}", line);
                    }
                } catch (Exception e) {
                    log.warn("读取Python输出中断: {}", e.getMessage());
                }
            }, "ppt-render-output-" + inst.getId());
            outputReader.setDaemon(true);
            outputReader.start();

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Python执行超时");
            }
            outputReader.join(5000);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                synchronized (output) {
                    log.error("Python执行失败: {}", output);
                    throw new RuntimeException("Python脚本执行失败:\n" + output);
                }
            }

            // ---------- 检查输出 ----------
            File outputFile = new File(outputFilePath);
            if (!outputFile.exists()) {
                throw new RuntimeException("PPT 未生成 " + outputFilePath);
            }

            // ---------- 上传到MinIO ----------
            log.info("PPT生成成功，开始上传到MinIO");
            byte[] fileBytes = Files.readAllBytes(outputFile.toPath());

            // 构建MinIO对象名称: ppt/{conversationId}/{filename}
            String objectName = "ppt/" + inst.getConversationId() + "/" + outputFileName;

            String fileUrl = fileStoragePort.upload(objectName, fileBytes, "application/vnd.openxmlformats-officedocument.presentationml.presentation");

            log.info("PPT已上传到MinIO: {}", fileUrl);
            return fileUrl;
        } finally {
            if (!outputFilePath.isEmpty()) {
                tempFiles.add(Paths.get(outputFilePath));
            }
            for (Path tempFile : tempFiles) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.warn("清理渲染临时文件失败: {}", tempFile, e);
                }
            }
        }
    }

    private String resolveTemplateFilePath(String configuredPath, List<Path> tempFiles) {
        if (configuredPath != null && configuredPath.startsWith("classpath:")) {
            return copyClasspathResource(configuredPath.substring("classpath:".length()),
                    "agent_agent_template_", ".pptx", tempFiles);
        }
        if (configuredPath != null && new File(configuredPath).exists()) {
            return configuredPath;
        }
        return copyClasspathResource("agent-runtime/templates/ai.pptx", "agent_agent_template_", ".pptx", tempFiles);
    }

    private String copyClasspathResource(String resourcePath, String prefix, String suffix, List<Path> tempFiles) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            Path tempFile = Files.createTempFile(prefix, suffix);
            tempFiles.add(tempFile);
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new RuntimeException("读取PPT渲染资源失败: " + resourcePath, e);
        }
    }

    /**
     * 获取输出目录（用于临时存储）
     */
    private String getOutputDir() {
        String projectRoot = System.getProperty("user.dir");
        String outputDir = projectRoot + File.separator + "output" + File.separator + "ppt";
        try {
            Path path = Paths.get(outputDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            log.error("创建输出目录失败: {}", outputDir, e);
        }
        return outputDir;
    }
}















