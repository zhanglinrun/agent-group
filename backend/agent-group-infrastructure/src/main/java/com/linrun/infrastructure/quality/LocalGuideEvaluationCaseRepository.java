package com.linrun.infrastructure.quality;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.conversation.service.AgentToolRegistry;
import com.linrun.domain.quality.adapter.GuideEvaluationCaseRepository;
import com.linrun.domain.quality.model.GuideEvaluationCase;
import com.linrun.domain.conversation.model.GuideIntentType;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Repository
public class LocalGuideEvaluationCaseRepository implements GuideEvaluationCaseRepository {

    private static final String GOODS_ID = "G10001";
    private final ObjectMapper objectMapper;
    private final String caseFile;

    public LocalGuideEvaluationCaseRepository() {
        this(new ObjectMapper(), "");
    }

    @Autowired
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
                evaluationCase("EV10001", "学生预算导购", "我是学生，预算有限，想买适合看网课的平板",
                        GuideIntentType.PRODUCT_RECOMMEND, false, List.of("学习", "网课"), List.of("拼团价", "2099")),
                evaluationCase("EV10002", "论文写作场景", "写论文和做笔记用，哪款平板更合适",
                        GuideIntentType.PRODUCT_RECOMMEND, false, List.of("论文", "笔记"), List.of("标准版")),
                evaluationCase("EV10003", "手写笔记场景", "需要经常手写笔记，预算不要太高",
                        GuideIntentType.PRODUCT_RECOMMEND, false, List.of("手写"), List.of("手写")),
                evaluationCase("EV10004", "网课学习场景", "主要看网课和课堂学习，推荐哪款",
                        GuideIntentType.PRODUCT_RECOMMEND, false, List.of("网课"), List.of("网课")),
                evaluationCase("EV10005", "日常轻办公", "日常办公和文档编辑用，想要性价比",
                        GuideIntentType.PRODUCT_RECOMMEND, false, List.of("文档"), List.of("性价比")),
                evaluationCase("EV10006", "拼团价格咨询", "这款平板拼团价是多少",
                        GuideIntentType.GROUP_RULE, false, List.of("拼团"), List.of("2099")),
                evaluationCase("EV10007", "拼团成团人数", "拼团需要几个人才能成团",
                        GuideIntentType.GROUP_RULE, false, List.of("成团"), List.of("成团")),
                evaluationCase("EV10008", "拼团剩余时间", "拼团活动还剩多长时间",
                        GuideIntentType.GROUP_RULE, false, List.of("活动"), List.of("剩余")),
                evaluationCase("EV10009", "未成团退款", "拼团失败会自动退款吗",
                        GuideIntentType.AFTER_SALE, false, List.of("退款"), List.of("退款")),
                evaluationCase("EV10010", "售后退货规则", "买了以后不合适可以退货吗",
                        GuideIntentType.AFTER_SALE, false, List.of("退货"), List.of("退货")),
                evaluationCase("EV10011", "质保规则", "这个平板质保多久",
                        GuideIntentType.AFTER_SALE, false, List.of("质保"), List.of("质保")),
                evaluationCase("EV10012", "标准版高配对比", "标准版和高配版哪个更划算",
                        GuideIntentType.PRODUCT_COMPARE, false, List.of("标准版"), List.of("标准版")),
                evaluationCase("EV10013", "创作应用限制", "如果我要剪视频，标准版够用吗",
                        GuideIntentType.PRODUCT_RECOMMEND, false, List.of("剪视频"), List.of("大型应用")),
                evaluationCase("EV10014", "预算追加追问", "刚才说预算有限，那还能再省一点吗",
                        GuideIntentType.PRODUCT_RECOMMEND, true, List.of("拼团"), List.of("拼团价")),
                evaluationCase("EV10015", "售后追问", "那上一轮推荐的商品售后怎么样",
                        GuideIntentType.AFTER_SALE, true, List.of("售后"), List.of("售后")),
                evaluationCase("EV10016", "拼团追问", "继续说下这个拼团失败怎么处理",
                        GuideIntentType.GROUP_RULE, true, List.of("退款"), List.of("退款")),
                evaluationCase("EV10017", "多轮规格追问", "那这个配置适合学生长期用吗",
                        GuideIntentType.PRODUCT_RECOMMEND, true, List.of("学生"), List.of("学习")),
                evaluationCase("EV10018", "图片截图导购", "用户上传商品截图后想知道适不适合买",
                        GuideIntentType.PRODUCT_RECOMMEND, false, List.of("商品"), List.of("商品")),
                evaluationCase("EV10019", "价格敏感对比", "如果我只看价格，直接买和拼团买哪个更合适",
                        GuideIntentType.GROUP_RULE, false, List.of("拼团"), List.of("拼团价")),
                evaluationCase("EV10020", "订单查询意图", "我想查订单和支付状态",
                        GuideIntentType.ORDER_QUERY, false, List.of("商品"), List.of("商品")),
                evaluationCase("EV10021", "拼团失败追问", "刚才那个拼团失败后会怎么处理？",
                        GuideIntentType.AFTER_SALE, true, List.of("未成团", "退款"), List.of("退款")),
                evaluationCase("EV10022", "预算上限导购", "预算 2500 以内，主要写论文和看网课，推荐哪款",
                        GuideIntentType.PRODUCT_COMPARE, false, List.of("论文", "网课"), List.of("2099")),
                evaluationCase("EV10023", "直接价拼团价一致性", "标准版直接买和拼团买分别多少钱",
                        GuideIntentType.GROUP_RULE, false, List.of("原价", "拼团价"), List.of("2399", "2099")),
                evaluationCase("EV10024", "支付不等于成团", "支付成功是不是就一定成团了",
                        GuideIntentType.GROUP_RULE, false, List.of("支付成功", "成团"), List.of("成团")),
                evaluationCase("EV10025", "幂等锁单规则", "我重复点拼团购买会不会占两个名额",
                        GuideIntentType.GROUP_RULE, false, List.of("幂等", "重复"), List.of("不重复")),
                evaluationCase("EV10026", "高配创作推荐", "预算够，主要绘图和剪视频，应该买哪款",
                        GuideIntentType.PRODUCT_COMPARE, false, List.of("高配", "剪视频"), List.of("高配")),
                evaluationCase("EV10027", "高配拼团信息", "高配创作平板拼团要几个人，价格是多少",
                        GuideIntentType.GROUP_RULE, false, List.of("高配", "5 人"), List.of("2899")),
                evaluationCase("EV10028", "活动不可用兜底", "如果活动过期或者队伍满了还能生成拼团入口吗",
                        GuideIntentType.GROUP_RULE, false, List.of("过期", "队伍已满"), List.of("不能")),
                evaluationCase("EV10029", "退货质保组合", "不合适退货和质量保修分别怎么算",
                        GuideIntentType.AFTER_SALE, false, List.of("7 天", "1 年"), List.of("7 天", "1 年")),
                evaluationCase("EV10030", "订单状态防编造", "查一下订单 O10001 现在是不是已成团",
                        GuideIntentType.ORDER_QUERY, false, List.of(), List.of("订单"))
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
        GuideEvaluationCase evaluationCase = new GuideEvaluationCase();
        evaluationCase.setCaseId(caseId);
        evaluationCase.setCaseName(caseName);
        evaluationCase.setQuestion(question);
        evaluationCase.setExpectedIntentType(expectedIntentType);
        evaluationCase.setExpectedGoodsId(GOODS_ID);
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
            return List.of(AgentToolRegistry.KNOWLEDGE_SEARCH, AgentToolRegistry.GUIDE_RECOMMEND, AgentToolRegistry.GROUP_TRIAL);
        }
        return List.of(AgentToolRegistry.KNOWLEDGE_SEARCH, AgentToolRegistry.GUIDE_RECOMMEND);
    }

    private boolean needsGroupTrial(String question, GuideIntentType intentType) {
        String normalized = question == null ? "" : question.toLowerCase();
        return GuideIntentType.GROUP_RULE.equals(intentType)
                || normalized.contains("预算")
                || normalized.contains("价格")
                || normalized.contains("拼团")
                || normalized.contains("成团")
                || normalized.contains("直接买")
                || normalized.contains("划算")
                || normalized.contains("省");
    }
}
