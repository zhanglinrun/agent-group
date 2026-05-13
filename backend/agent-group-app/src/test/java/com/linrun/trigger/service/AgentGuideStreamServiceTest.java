package com.linrun.trigger.service;

import com.linrun.api.agent.model.GuideEventType;
import com.linrun.api.agent.request.GuideStreamRequest;
import com.linrun.api.agent.response.ErrorDTO;
import com.linrun.api.agent.response.GuideStreamEvent;
import com.linrun.api.agent.response.ProductCardDTO;
import com.linrun.api.agent.response.SelfCheckDTO;
import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.guide.service.GuideDecisionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGuideStreamServiceTest {

    @Test
    void shouldBuildGuideEventsWithProductAndReferences() {
        AgentGuideStreamService service = new AgentGuideStreamService(new GuideDecisionService(new FakeGuideDataRepository()));
        GuideStreamRequest request = new GuideStreamRequest();
        request.setQuestion("我是学生，预算有限，想买适合看网课的平板");

        List<GuideStreamEvent<?>> events = service.buildEvents(request, "S10001", "R10001");

        assertEquals(List.of(
                GuideEventType.TOOL_CALL.getCode(),
                GuideEventType.REFERENCE_DELTA.getCode(),
                GuideEventType.REFERENCE_DELTA.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.PRODUCT_CARD.getCode(),
                GuideEventType.SELF_CHECK.getCode(),
                GuideEventType.DONE.getCode()
        ), events.stream().map(GuideStreamEvent::getEvent).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), events.stream().map(GuideStreamEvent::getSequence).toList());

        ProductCardDTO productCard = assertInstanceOf(ProductCardDTO.class, events.get(7).getData());
        assertEquals("G10001", productCard.getGoodsId());
        assertEquals("轻薄学习平板标准版", productCard.getGoodsName());
        assertEquals(new BigDecimal("2099.00"), productCard.getGroupPrice());
        SelfCheckDTO selfCheck = assertInstanceOf(SelfCheckDTO.class, events.get(8).getData());
        assertEquals(Boolean.TRUE, selfCheck.getPassed());
        assertEquals("推荐商品、价格、规格和推荐理由完整", selfCheck.getMessage());
    }

    @Test
    void shouldReturnErrorWhenQuestionIsBlank() {
        AgentGuideStreamService service = new AgentGuideStreamService(new GuideDecisionService(new FakeGuideDataRepository()));
        GuideStreamRequest request = new GuideStreamRequest();
        request.setQuestion(" ");

        List<GuideStreamEvent<?>> events = service.buildEvents(request, "S10001", "R10001");

        assertEquals(1, events.size());
        assertEquals(GuideEventType.ERROR.getCode(), events.get(0).getEvent());
        ErrorDTO error = assertInstanceOf(ErrorDTO.class, events.get(0).getData());
        assertEquals("0001", error.getCode());
        assertEquals("问题不能为空", error.getMessage());
    }

    @Test
    void shouldReturnErrorWhenRepositoryFails() {
        AgentGuideStreamService service = new AgentGuideStreamService(new GuideDecisionService(new FailingGuideDataRepository()));
        GuideStreamRequest request = new GuideStreamRequest();
        request.setQuestion("推荐一款学习平板");

        List<GuideStreamEvent<?>> events = service.buildEvents(request, "S10001", "R10001");

        assertEquals(2, events.size());
        assertEquals(GuideEventType.TOOL_CALL.getCode(), events.get(0).getEvent());
        assertEquals(GuideEventType.ERROR.getCode(), events.get(1).getEvent());
        ErrorDTO error = assertInstanceOf(ErrorDTO.class, events.get(1).getData());
        assertEquals("DATA_0001", error.getCode());
        assertTrue(error.getMessage().contains("导购数据源不可用"));
    }

    private static class FakeGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of(reference("KF10001", 1), reference("KF10002", 2));
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId("G10001");
            product.setGoodsName("轻薄学习平板标准版");
            product.setImageUrl("");
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setGroupPrice(new BigDecimal("2099.00"));
            product.setSpecSummary("10.9 英寸屏幕，128GB 存储，支持手写笔");
            product.setAfterSalePolicy("7 天无理由退货，1 年质保");
            product.setRecommendReason("预算有限、学习和网课场景下性价比更高");
            product.setNotSuitableFor("长期剪视频或运行大型应用的用户");
            product.setActivityId("A10001");
            product.setTeamSize(3);
            product.setRemainingSeconds(1800);
            return Optional.of(product);
        }

        private GuideReference reference(String fragmentId, int rank) {
            GuideReference reference = new GuideReference();
            reference.setFragmentId(fragmentId);
            reference.setDocumentId("DOC10001");
            reference.setGoodsId("G10001");
            reference.setDocumentType("商品详情");
            reference.setKnowledgeVersion("v1");
            reference.setContent("轻薄学习平板标准版适合写论文、看网课和日常笔记。");
            reference.setRank(rank);
            return reference;
        }
    }

    private static class FailingGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            throw new IllegalStateException("database unavailable");
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            throw new IllegalStateException("database unavailable");
        }
    }
}
