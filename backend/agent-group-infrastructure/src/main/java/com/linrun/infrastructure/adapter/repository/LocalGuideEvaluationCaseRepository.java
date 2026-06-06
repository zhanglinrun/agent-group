package com.linrun.infrastructure.adapter.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.conversation.service.AgentToolRegistry;
import com.linrun.domain.agent.quality.adapter.GuideEvaluationCaseRepository;
import com.linrun.domain.agent.quality.model.GuideEvaluationCase;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Repository
public class LocalGuideEvaluationCaseRepository implements GuideEvaluationCaseRepository {

    private static final String DEFAULT_GOODS_ID = "G10001";
    private ObjectMapper objectMapper;
    @Value("${agent.group.evaluate.case-file:}")
    private String caseFile;

    public LocalGuideEvaluationCaseRepository() {
        this(new ObjectMapper(), "");
    }

    public LocalGuideEvaluationCaseRepository(@Value("${agent.group.evaluate.case-file:}") String caseFile) {
        this(new ObjectMapper(), caseFile);
    }

    LocalGuideEvaluationCaseRepository(ObjectMapper objectMapper, String caseFile) {
        this.objectMapper = objectMapper;
        this.caseFile = caseFile;
    }

    @Override
    public List<GuideEvaluationCase> queryEnabledCases() {
        if (StringUtils.hasText(caseFile)) {
            return loadCasesFromFile(caseFile);
        }
        return defaultCases();
    }

    private List<GuideEvaluationCase> defaultCases() {
        return List.of(
                evaluationCase("EV10001", "轻量额度推荐", "预算有限，只做普通学术问答和资料摘要，买哪个额度包",
                        GuideIntentType.PRODUCT_RECOMMEND, false, "G10001", List.of("基础额度"), List.of("额度")),
                evaluationCase("EV10002", "论文阅读场景", "我要上传论文做精读笔记和相关工作摘要，哪款额度包更合适",
                        GuideIntentType.PRODUCT_COMPARE, false, "G10002", List.of("论文"), List.of("论文")),
                evaluationCase("EV10003", "PPT 创作场景", "组会汇报需要生成 PPT 大纲和讲稿，推荐哪个额度包",
                        GuideIntentType.PRODUCT_RECOMMEND, false, "G10003", List.of("PPT"), List.of("PPT")),
                evaluationCase("EV10004", "图表重建场景", "想把论文图和流程图转成可编辑 Mermaid，应该买哪个额度包",
                        GuideIntentType.PRODUCT_RECOMMEND, false, "G10004", List.of("图表"), List.of("图表")),
                evaluationCase("EV10005", "深度研究场景", "要做复杂主题调研和技术路线规划，哪个额度包更合适",
                        GuideIntentType.PRODUCT_COMPARE, false, "G10005", List.of("深度研究"), List.of("深度")),
                evaluationCase("EV10006", "团队共享场景", "实验室小组想一起拼团购买额度，推荐哪个额度包",
                        GuideIntentType.GROUP_RULE, false, "G10006", List.of("团队"), List.of("拼团")),
                evaluationCase("EV10007", "拼团成团人数", "拼团需要几个人才能成团",
                        GuideIntentType.GROUP_RULE, false, "G10001", List.of("成团"), List.of("成团")),
                evaluationCase("EV10008", "拼团剩余时间", "拼团活动还剩多长时间",
                        GuideIntentType.GROUP_RULE, false, "G10001", List.of("活动"), List.of("剩余")),
                evaluationCase("EV10009", "未成团退款", "拼团失败会自动退款吗，额度会不会到账",
                        GuideIntentType.AFTER_SALE, false, "G10001", List.of("退款"), List.of("退款")),
                evaluationCase("EV10010", "额度退款规则", "买了额度包以后没用完可以退款吗",
                        GuideIntentType.AFTER_SALE, false, "G10001", List.of("退款"), List.of("退款")),
                evaluationCase("EV10011", "额度回滚规则", "退款时已经发放的额度怎么处理",
                        GuideIntentType.AFTER_SALE, false, "G10001", List.of("额度"), List.of("额度")),
                evaluationCase("EV10012", "基础论文对比", "基础额度包和论文阅读额度包哪个更划算",
                        GuideIntentType.PRODUCT_COMPARE, false, "G10002", List.of("论文"), List.of("论文")),
                evaluationCase("EV10013", "轻量任务边界", "基础额度包适合做长报告和深度研究吗",
                        GuideIntentType.PRODUCT_RECOMMEND, false, "G10005", List.of("深度研究"), List.of("深度")),
                evaluationCase("EV10014", "预算追加追问", "刚才说预算有限，那还能再省一点吗",
                        GuideIntentType.PRODUCT_RECOMMEND, true, "G10001", List.of("拼团"), List.of("拼团价")),
                evaluationCase("EV10015", "退款追问", "那上一轮推荐的额度包退款规则怎么样",
                        GuideIntentType.AFTER_SALE, true, "G10001", List.of("退款"), List.of("退款")),
                evaluationCase("EV10016", "拼团追问", "继续说下这个拼团失败怎么处理",
                        GuideIntentType.GROUP_RULE, true, "G10001", List.of("退款"), List.of("退款")),
                evaluationCase("EV10017", "额度消耗追问", "那这个额度够不够我连续做几次论文总结",
                        GuideIntentType.PRODUCT_RECOMMEND, true, "G10002", List.of("论文"), List.of("额度")),
                evaluationCase("EV10018", "截图购买咨询", "用户上传额度包截图后想知道适不适合买",
                        GuideIntentType.PRODUCT_RECOMMEND, false, "G10001", List.of("额度包"), List.of("额度包")),
                evaluationCase("EV10019", "价格敏感对比", "如果我只看价格，直接买和拼团买哪个更合适",
                        GuideIntentType.GROUP_RULE, false, "G10001", List.of("拼团"), List.of("拼团价")),
                evaluationCase("EV10020", "订单查询意图", "我想查订单和支付状态",
                        GuideIntentType.ORDER_QUERY, false, "G10001", List.of(), List.of("订单")),
                evaluationCase("EV10021", "拼团失败追问", "刚才那个拼团失败后会怎么处理？",
                        GuideIntentType.AFTER_SALE, true, "G10001", List.of("未成团", "退款"), List.of("退款")),
                evaluationCase("EV10022", "预算上限额度", "预算 60 元以内，主要写论文和整理资料，推荐哪款",
                        GuideIntentType.PRODUCT_COMPARE, false, "G10002", List.of("论文"), List.of("论文")),
                evaluationCase("EV10023", "直接价拼团价一致性", "基础额度包直接买和拼团买分别多少钱",
                        GuideIntentType.GROUP_RULE, false, "G10001", List.of("原价", "拼团价"), List.of("拼团价")),
                evaluationCase("EV10024", "支付不等于成团", "支付成功是不是就一定成团并到账了",
                        GuideIntentType.GROUP_RULE, false, "G10001", List.of("支付成功", "成团"), List.of("成团")),
                evaluationCase("EV10025", "幂等锁单规则", "我重复点拼团购买会不会占两个名额",
                        GuideIntentType.GROUP_RULE, false, "G10001", List.of("幂等", "重复"), List.of("不重复")),
                evaluationCase("EV10026", "图表任务推荐", "主要做论文图表和架构图重建，应该买哪款",
                        GuideIntentType.PRODUCT_COMPARE, false, "G10004", List.of("图表"), List.of("图表")),
                evaluationCase("EV10027", "团队拼团信息", "团队拼团额度包要几个人，价格是多少",
                        GuideIntentType.GROUP_RULE, false, "G10006", List.of("团队"), List.of("拼团")),
                evaluationCase("EV10028", "活动不可用兜底", "如果活动过期或者队伍满了还能生成拼团入口吗",
                        GuideIntentType.GROUP_RULE, false, "G10001", List.of("过期", "队伍已满"), List.of("不能")),
                evaluationCase("EV10029", "已用额度退款", "额度包已经消耗了一部分，还能全部退款吗",
                        GuideIntentType.AFTER_SALE, false, "G10001", List.of("额度"), List.of("退款")),
                evaluationCase("EV10030", "订单状态防编造", "查一下订单 O10001 现在是不是已成团",
                        GuideIntentType.ORDER_QUERY, false, "G10001", List.of(), List.of("订单"))
        );
    }

    private List<GuideEvaluationCase> loadCasesFromFile(String file) {
        Path path = Path.of(file);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new AppException("EVAL_0001", "评测用例文件不存在：" + file);
        }
        try {
            List<GuideEvaluationCase> cases = objectMapper.readValue(
                    Files.readString(path),
                    new TypeReference<>() {
                    });
            if (cases == null || cases.isEmpty()) {
                throw new AppException("EVAL_0002", "评测用例文件不能为空");
            }
            return cases;
        } catch (IOException e) {
            throw new AppException("EVAL_0003", "评测用例文件解析失败：" + e.getMessage());
        }
    }

    private GuideEvaluationCase evaluationCase(String caseId,
                                               String caseName,
                                               String question,
                                               GuideIntentType expectedIntentType,
                                               boolean contextRequired,
                                               List<String> referenceKeywords,
                                               List<String> answerKeywords) {
        return evaluationCase(caseId, caseName, question, expectedIntentType, contextRequired,
                DEFAULT_GOODS_ID, referenceKeywords, answerKeywords);
    }

    private GuideEvaluationCase evaluationCase(String caseId,
                                               String caseName,
                                               String question,
                                               GuideIntentType expectedIntentType,
                                               boolean contextRequired,
                                               String expectedGoodsId,
                                               List<String> referenceKeywords,
                                               List<String> answerKeywords) {
        GuideEvaluationCase evaluationCase = new GuideEvaluationCase();
        evaluationCase.setCaseId(caseId);
        evaluationCase.setCaseName(caseName);
        evaluationCase.setQuestion(question);
        evaluationCase.setExpectedIntentType(expectedIntentType);
        evaluationCase.setExpectedGoodsId(expectedGoodsId);
        evaluationCase.setContextRequired(contextRequired);
        evaluationCase.setRequiredReferenceKeywords(referenceKeywords);
        evaluationCase.setRequiredAnswerKeywords(answerKeywords);
        List<String> toolNames = expectedToolNames(question, expectedIntentType);
        evaluationCase.setExpectedToolNames(toolNames);
        evaluationCase.setExpectedToolOrder(toolNames);
        evaluationCase.setScenarioTags(List.of(expectedIntentType.name()));
        return evaluationCase;
    }

    private List<String> expectedToolNames(String question, GuideIntentType intentType) {
        if (GuideIntentType.ORDER_QUERY.equals(intentType)) {
            return List.of(AgentToolRegistry.ORDER_STATUS);
        }
        if (needsGroupTrial(question, intentType)) {
            return List.of(AgentToolRegistry.KNOWLEDGE_SEARCH, AgentToolRegistry.QUOTA_RECOMMEND, AgentToolRegistry.GROUP_TRIAL);
        }
        return List.of(AgentToolRegistry.KNOWLEDGE_SEARCH, AgentToolRegistry.QUOTA_RECOMMEND);
    }

    private boolean needsGroupTrial(String question, GuideIntentType intentType) {
        String normalized = question == null ? "" : question.toLowerCase();
        return GuideIntentType.GROUP_RULE.equals(intentType)
                || GuideIntentType.PRODUCT_COMPARE.equals(intentType)
                || normalized.contains("预算")
                || normalized.contains("价格")
                || normalized.contains("拼团")
                || normalized.contains("成团")
                || normalized.contains("直接买")
                || normalized.contains("划算")
                || normalized.contains("省");
    }
}
