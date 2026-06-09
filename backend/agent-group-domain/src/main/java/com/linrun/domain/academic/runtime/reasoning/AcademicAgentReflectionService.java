package com.linrun.domain.academic.runtime.reasoning;

import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicPlanLifecycleService;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 反思与自我评估服务
 * 在关键节点评估计划执行质量、识别改进空�?
 */
public class AcademicAgentReflectionService {

    /**
     * 对计划执行结果进行反�?
     */
    public ReflectionResult reflect(AcademicAgentPlan plan, List<AcademicPlanStep> completedSteps) {
        if (plan == null || completedSteps == null || completedSteps.isEmpty()) {
            return ReflectionResult.empty();
        }

        double quality = evaluatePlanQuality(completedSteps);
        List<String> improvements = identifyImprovements(plan, completedSteps);
        boolean needReplan = quality < 0.7 || !improvements.isEmpty();
        String summary = generateReflectionSummary(quality, improvements, needReplan);

        return new ReflectionResult(quality, improvements, needReplan, summary);
    }

    /**
     * 评估计划执行质量
     */
    private double evaluatePlanQuality(List<AcademicPlanStep> completedSteps) {
        if (completedSteps.isEmpty()) {
            return 0.0;
        }

        int totalSteps = completedSteps.size();
        long successfulSteps = completedSteps.stream()
                .filter(s -> AcademicPlanLifecycleService.STATUS_COMPLETED.equals(s.getStatus()))
                .count();
        
        double completionRate = (double) successfulSteps / totalSteps;

        long stepsWithOutput = completedSteps.stream()
                .filter(s -> s.getNote() != null && !s.getNote().trim().isEmpty())
                .count();
        double usability = (double) stepsWithOutput / totalSteps;

        return (completionRate * 0.6 + usability * 0.4);
    }

    /**
     * 识别可改进点
     */
    private List<String> identifyImprovements(AcademicAgentPlan plan, List<AcademicPlanStep> completedSteps) {
        List<String> improvements = new ArrayList<>();

        long failedSteps = completedSteps.stream()
                .filter(s -> AcademicPlanLifecycleService.STATUS_BLOCKED.equals(s.getStatus()))
                .count();
        if (failedSteps > 0) {
            improvements.add("本" + failedSteps + " 个步骤执行失败，需要调整策�?);
        }

        if (plan.getSteps().size() > 8) {
            improvements.add("计划步骤过多） + plan.getSteps().size() + "步），可以合并相似步验);
        }

        long emptyOutputSteps = completedSteps.stream()
                .filter(s -> s.getNote() == null || s.getNote().trim().isEmpty())
                .count();
        if (emptyOutputSteps > completedSteps.size() / 2) {
            improvements.add("超过半数步骤缺少输出，可能需要调整执行方开);
        }

        return improvements;
    }

    /**
     * 生成反思总结
     */
    private String generateReflectionSummary(double quality, List<String> improvements, boolean needReplan) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("执行质量评分）).append(String.format("%.1f", quality * 100)).append("/100\n");
        
        if (quality >= 0.9) {
            summary.append("质量等级：优秀\n");
        } else if (quality >= 0.7) {
            summary.append("质量等级：良好\n");
        } else if (quality >= 0.5) {
            summary.append("质量等级：及格\n");
        } else {
            summary.append("质量等级：较差\n");
        }

        if (!improvements.isEmpty()) {
            summary.append("\n改进建议：\n");
            for (int i = 0; i < improvements.size(); i++) {
                summary.append((i + 1)).append(". ").append(improvements.get(i)).append("\n");
            }
        }

        if (needReplan) {
            summary.append("\n建议：根数据反思结果调整后继续计�?);
        } else {
            summary.append("\n建议：当前计划执行良好，可继继续按原计划进行);
        }

        return summary.toString();
    }

    /**
     * 反思结构
     */
    public static class ReflectionResult {
        private final double quality;
        private final List<String> improvements;
        private final boolean needReplan;
        private final String summary;

        public ReflectionResult(double quality, List<String> improvements, 
                               boolean needReplan, String summary) {
            this.quality = quality;
            this.improvements = improvements != null ? improvements : new ArrayList<>();
            this.needReplan = needReplan;
            this.summary = summary != null ? summary : "";
        }

        public static ReflectionResult empty() {
            return new ReflectionResult(0.0, new ArrayList<>(), false, "无数据);
        }

        public double getQuality() {
            return quality;
        }

        public List<String> getImprovements() {
            return improvements;
        }

        public boolean needReplan() {
            return needReplan;
        }

        public String getSummary() {
            return summary;
        }

        public String getQualityGrade() {
            if (quality >= 0.9) return "优秀";
            if (quality >= 0.7) return "良好";
            if (quality >= 0.5) return "及格";
            return "较差";
        }
    }
}















