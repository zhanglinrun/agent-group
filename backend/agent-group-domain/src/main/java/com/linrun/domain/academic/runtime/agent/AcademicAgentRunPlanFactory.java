package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.List;

public class AcademicAgentRunPlanFactory {

    public AcademicAgentPlan build(String taskType, boolean webSearchEnabled) {
        return switch (normalizeTaskType(taskType)) {
            case "file" -> filePlan();
            case "ppt" -> pptPlan(webSearchEnabled);
            case "deep" -> deepResearchPlan(webSearchEnabled);
            case "image" -> imagePlan();
            case "data" -> dataPlan();
            case "skills" -> skillsPlan();
            case "manual-skills" -> manualSkillsPlan();
            default -> chatPlan(webSearchEnabled);
        };
    }

    private AcademicAgentPlan filePlan() {
        return new AcademicAgentPlan("文件问答", List.of(
                step("S1", "读取文件并确认问�?, 1, "文件理解智能�?),
                step("S2", "检索文件中的相关片�?, 2, "检索智能体", "S1"),
                step("S3", "结合证据生成回答", 3, "回答智能�?, "S2")
        ));
    }

    private AcademicAgentPlan pptPlan(boolean webSearchEnabled) {
        String materialStep = webSearchEnabled ? "搜索并整理主题资�? : "整理主题素材和结�?;
        return new AcademicAgentPlan("PPT 生成", List.of(
                step("S1", "拆解主题、受众和页数", 1, "规划智能�?),
                step("S2", materialStep, 2, webSearchEnabled ? "搜索智能�? : "内容智能�?, "S1"),
                step("S3", "生成页面大纲和演示文稿结�?, 3, "内容智能�?, "S2"),
                step("S4", "渲染 PPTX 并登记产�?, 4, "产物智能�?, "S3")
        ));
    }

    private AcademicAgentPlan deepResearchPlan(boolean webSearchEnabled) {
        String evidenceStep = webSearchEnabled ? "检索公开资料和参考来�? : "整理已有上下文和知识库信�?;
        return new AcademicAgentPlan("深度研究", List.of(
                step("S1", "拆解研究问题、范围和风险�?, 1, "规划智能�?),
                step("S2", evidenceStep, 2, webSearchEnabled ? "搜索智能�? : "检索智能体", "S1"),
                step("S3", "调用可用工具补充证据", 2, "工具智能�?, "S1"),
                step("S4", "交叉校验证据并形成结�?, 3, "推理智能�?, "S2", "S3"),
                step("S5", "输出结构化报告和参考来�?, 4, "报告智能�?, "S4")
        ));
    }

    private AcademicAgentPlan imagePlan() {
        return new AcademicAgentPlan("图像生成", List.of(
                step("S1", "整理画面目标、风格和约束", 1, "规划智能�?),
                step("S2", "生成可复用图像提示词", 2, "提示词智能体", "S1"),
                step("S3", "调用图像生成工具产出图片", 3, "图像智能�?, "S2"),
                step("S4", "登记图像产物并给出下载说�?, 4, "产物智能�?, "S3")
        ));
    }

    private AcademicAgentPlan dataPlan() {
        return new AcademicAgentPlan("数据问答", List.of(
                step("S1", "确认数据口径、权限和交易约束", 1, "规划智能�?),
                step("S2", "读取表格、指标或数据库元信息", 2, "数据智能�?, "S1"),
                step("S3", "执行数据分析、表格问答或自然语言�?SQL", 3, "分析智能�?, "S2"),
                step("S4", "校验额度、订单、支付和拼团状态来�?, 4, "交易校验智能�?, "S3"),
                step("S5", "输出结论、口径和可复核证�?, 5, "报告智能�?, "S4")
        ));
    }

    private AcademicAgentPlan skillsPlan() {
        return new AcademicAgentPlan("技能执�?, List.of(
                step("S1", "选择匹配任务的技�?, 1, "技能智能体"),
                step("S2", "读取技能说明和依赖材料", 2, "技能智能体", "S1"),
                step("S3", "执行工具或脚本并生成产物", 3, "工具智能�?, "S2"),
                step("S4", "整理产物和最终说�?, 4, "报告智能�?, "S3")
        ));
    }

    private AcademicAgentPlan manualSkillsPlan() {
        return new AcademicAgentPlan("手动技能执�?, List.of(
                step("S1", "读取用户选择的技能说�?, 1, "技能智能体"),
                step("S2", "确认脚本、文件和工具权限", 2, "安全智能�?, "S1"),
                step("S3", "执行技能脚本或工具�?, 3, "工具智能�?, "S2"),
                step("S4", "登记产物并输出结果摘�?, 4, "报告智能�?, "S3")
        ));
    }

    private AcademicAgentPlan chatPlan(boolean webSearchEnabled) {
        String secondStep = webSearchEnabled ? "按需检索或搜索补充信息" : "组织上下文和已有知识";
        return new AcademicAgentPlan("通用对话", List.of(
                step("S1", "理解问题和用户意�?, 1, "对话智能�?),
                step("S2", secondStep, 2, webSearchEnabled ? "搜索智能�? : "对话智能�?, "S1"),
                step("S3", "生成回答并说明依�?, 3, "回答智能�?, "S2")
        ));
    }

    private AcademicPlanStep step(String stepId,
                                  String instruction,
                                  int order,
                                  String assignedAgent,
                                  String... dependencies) {
        return AcademicPlanStep.builder(stepId, instruction)
                .order(order)
                .assignedAgent(assignedAgent)
                .dependencies(List.of(dependencies))
                .build();
    }

    private String normalizeTaskType(String taskType) {
        String type = StringUtils.hasText(taskType) ? taskType.trim().toLowerCase() : "chat";
        return switch (type) {
            case "paper", "file" -> "file";
            case "ppt", "pptx" -> "ppt";
            case "deep", "deep-research" -> "deep";
            case "image", "image-generation", "workspace-image" -> "image";
            case "data", "data-qa", "workspace-data", "nl2sql", "table-rag",
                 "trade", "trade-flow", "group-trade", "workspace-trade" -> "data";
            case "skills" -> "skills";
            case "manual", "manual-skills", "skills-manual" -> "manual-skills";
            default -> "chat";
        };
    }
}















