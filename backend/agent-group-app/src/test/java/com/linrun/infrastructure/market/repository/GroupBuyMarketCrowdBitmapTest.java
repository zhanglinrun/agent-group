package com.linrun.infrastructure.market.repository;

import com.linrun.domain.market.tag.adapter.CrowdTagBitmapPort;
import com.linrun.infrastructure.dao.ICrowdTagDao;
import com.linrun.infrastructure.dao.IGroupBuyDiscountDao;
import com.linrun.infrastructure.dao.IGroupBuyMarketSkuDao;
import com.linrun.infrastructure.dao.ISourceChannelSkuActivityDao;
import com.linrun.infrastructure.market.cache.RedisGroupBuyMarketReadCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupBuyMarketCrowdBitmapTest {

    @Mock
    private IGroupBuyMarketSkuDao skuDao;
    @Mock
    private ISourceChannelSkuActivityDao sourceChannelSkuActivityDao;
    @Mock
    private IGroupBuyDiscountDao discountDao;
    @Mock
    private ICrowdTagDao crowdTagDao;

    @Test
    void shouldUseBitmapWhenTaggedUserExists() {
        InMemoryCrowdTagBitmapPort bitmapPort = new InMemoryCrowdTagBitmapPort();
        bitmapPort.markUserInTag("TAG100", 1001L);
        when(crowdTagDao.countCrowdTagUsers("TAG100")).thenReturn(1);

        MyBatisGroupBuyMarketRepository repository = new MyBatisGroupBuyMarketRepository(
                skuDao, sourceChannelSkuActivityDao, discountDao, crowdTagDao, bitmapPort, disabledReadCache());

        assertTrue(repository.isTagCrowdRange("TAG100", "U10001"));
        verify(crowdTagDao, never()).isTagCrowdRange("TAG100", "U10001");
    }

    @Test
    void shouldFallbackToDatabaseWhenBitmapUnavailable() {
        when(crowdTagDao.countCrowdTagUsers("TAG100")).thenReturn(2);
        when(crowdTagDao.isTagCrowdRange("TAG100", "U10002")).thenReturn(true);

        MyBatisGroupBuyMarketRepository repository = new MyBatisGroupBuyMarketRepository(
                skuDao, sourceChannelSkuActivityDao, discountDao, crowdTagDao, CrowdTagBitmapPort.noop(), disabledReadCache());

        assertTrue(repository.isTagCrowdRange("TAG100", "U10002"));
        verify(crowdTagDao).isTagCrowdRange("TAG100", "U10002");
    }

    @Test
    void shouldReturnFalseWhenUserNotInCrowd() {
        InMemoryCrowdTagBitmapPort bitmapPort = new InMemoryCrowdTagBitmapPort();
        bitmapPort.markUserInTag("TAG100", 1001L);
        when(crowdTagDao.countCrowdTagUsers("TAG100")).thenReturn(1);

        MyBatisGroupBuyMarketRepository repository = new MyBatisGroupBuyMarketRepository(
                skuDao, sourceChannelSkuActivityDao, discountDao, crowdTagDao, bitmapPort, disabledReadCache());

        assertFalse(repository.isTagCrowdRange("TAG100", "U99999"));
    }

    private static RedisGroupBuyMarketReadCache disabledReadCache() {
        return new RedisGroupBuyMarketReadCache(
                mock(RedissonClient.class), new ObjectMapper(), "test", 300L, false, null);
    }

    private static class InMemoryCrowdTagBitmapPort implements CrowdTagBitmapPort {

        private final Map<String, Map<Long, Boolean>> bitmaps = new HashMap<>();
        private final Map<String, Long> userNumericIds = Map.of(
                "U10001", 1001L,
                "U99999", 9999L);

        @Override
        public Optional<Long> queryUserNumericId(String userId) {
            return Optional.ofNullable(userNumericIds.get(userId));
        }

        @Override
        public Optional<Boolean> isUserInTag(String tagId, String userId) {
            return queryUserNumericId(userId).map(id -> bitmaps
                    .getOrDefault(tagId, Map.of())
                    .getOrDefault(id, false));
        }

        @Override
        public void markUserInTag(String tagId, long userNumericId) {
            bitmaps.computeIfAbsent(tagId, key -> new HashMap<>()).put(userNumericId, true);
        }

        @Override
        public int countTaggedUsers(String tagId) {
            return bitmaps.getOrDefault(tagId, Map.of()).size();
        }
    }
}
