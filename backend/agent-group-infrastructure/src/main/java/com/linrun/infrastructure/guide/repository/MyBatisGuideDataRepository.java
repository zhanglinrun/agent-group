package com.linrun.infrastructure.guide.repository;

import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.infrastructure.guide.mapper.GuideDataMapper;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisGuideDataRepository implements GuideDataRepository {

    private final GuideDataMapper guideDataMapper;

    public MyBatisGuideDataRepository(GuideDataMapper guideDataMapper) {
        this.guideDataMapper = guideDataMapper;
    }

    @Override
    public List<GuideReference> queryReferences(String question, int limit) {
        return guideDataMapper.queryReferences(question, limit);
    }

    @Override
    public Optional<GuideProduct> queryRecommendProduct(String question) {
        GuideProduct product = guideDataMapper.queryRecommendProduct(question);
        if (product == null) {
            return Optional.empty();
        }
        if (product.getGroupPrice() == null) {
            product.setGroupPrice(product.getOriginPrice());
        }
        if (product.getTeamSize() == null) {
            product.setTeamSize(1);
        }
        if (product.getRemainingSeconds() == null || product.getRemainingSeconds() <= 0) {
            product.setRemainingSeconds((int) Duration.ofMinutes(30).toSeconds());
        }
        return Optional.of(product);
    }
}
