package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.model.GuideQueryRoute;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class GuideQueryRouterService {

    public static final String STRATEGY_TRADE_SYSTEM = "trade_system";
    public static final String STRATEGY_MARKET_SYSTEM = "market_system";
    public static final String STRATEGY_KNOWLEDGE_BASE = "knowledge_base";
    public static final String STRATEGY_HYBRID = "hybrid";

    public GuideQueryRoute route(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "question cannot be blank");
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "order", "订单", "支付状态", "退款状态", "物流", "璁㈠崟", "鏀粯鐘舵€?", "閫€娆剧姸鎬?")) {
            return GuideQueryRoute.of(
                    STRATEGY_TRADE_SYSTEM,
                    "trade_state_query",
                    "structured order or payment state should come from trade tables",
                    0.92,
                    List.of("trade_order", "pay_order", "refund_order"));
        }
        if (containsAny(normalized, "拼团", "成团", "库存", "名额", "价格", "优惠", "group", "stock", "price",
                "鎷煎洟", "鎴愬洟", "搴撳瓨", "鍚嶉", "浠锋牸", "浼樻儬")) {
            return GuideQueryRoute.of(
                    STRATEGY_HYBRID,
                    "market_rule_query",
                    "market questions need activity data plus policy fragments",
                    0.88,
                    List.of("group_activity", "group_buy_stock", "vector_store", "keyword_index"));
        }
        if (containsAny(normalized, "推荐", "对比", "适合", "售后", "退货", "保修", "policy", "compare",
                "鎺ㄨ崘", "瀵规瘮", "閫傚悎", "鍞悗", "閫€璐?", "淇濅慨")) {
            return GuideQueryRoute.of(
                    STRATEGY_KNOWLEDGE_BASE,
                    "knowledge_answer",
                    "semantic product and policy questions should retrieve knowledge fragments first",
                    0.84,
                    List.of("vector_store", "keyword_index", "bge_reranker"));
        }
        return GuideQueryRoute.of(
                STRATEGY_HYBRID,
                "general_guide",
                "general guide query uses product database and knowledge retrieval together",
                0.76,
                List.of("guide_goods", "vector_store", "keyword_index"));
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
