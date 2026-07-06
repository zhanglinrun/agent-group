package com.linrun.infrastructure.market.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.support.config.service.DynamicConfigService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisGroupBuyMarketReadCacheTest {

    @Test
    void getOrLoadBypassesCacheWhenDisabled() {
        AtomicInteger loads = new AtomicInteger();
        RedisGroupBuyMarketReadCache cache = new RedisGroupBuyMarketReadCache(
                mock(RedissonClient.class),
                new ObjectMapper(),
                "test",
                300L,
                false,
                null);

        Optional<String> result = cache.getOrLoad("group:market:sku:G1", String.class,
                () -> {
                    loads.incrementAndGet();
                    return Optional.of("loaded");
                });

        assertTrue(result.isPresent());
        assertEquals("loaded", result.get());
        assertEquals(1, loads.get());
    }

    @Test
    void getOrLoadBypassesCacheWhenDynamicSwitchClosed() {
        AtomicInteger loads = new AtomicInteger();
        DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
        when(dynamicConfigService.isCacheOpenSwitch()).thenReturn(false);
        RedisGroupBuyMarketReadCache cache = new RedisGroupBuyMarketReadCache(
                mock(RedissonClient.class),
                new ObjectMapper(),
                "test",
                300L,
                true,
                dynamicConfigService);

        Optional<String> result = cache.getOrLoad("group:market:sku:G1", String.class,
                () -> {
                    loads.incrementAndGet();
                    return Optional.of("from-db");
                });

        assertTrue(result.isPresent());
        assertEquals("from-db", result.get());
        assertEquals(1, loads.get());
    }
}
