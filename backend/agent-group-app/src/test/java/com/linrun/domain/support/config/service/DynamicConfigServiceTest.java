package com.linrun.domain.support.config.service;

import com.linrun.domain.support.config.adapter.DynamicConfigRepository;
import com.linrun.domain.support.config.model.DynamicConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicConfigServiceTest {

    @Test
    void shouldUpdateAndReadDynamicConfig() {
        FakeDynamicConfigRepository repository = new FakeDynamicConfigRepository();
        DynamicConfigService service = new DynamicConfigService(repository);

        service.updateConfig(DynamicConfigService.DOWNGRADE_SWITCH, "1");
        service.updateConfig(DynamicConfigService.SC_BLACKLIST, "s01c01,s02c02");

        assertTrue(service.isDowngradeSwitch());
        assertTrue(service.isSourceChannelBlackIntercept("s01", "c01"));
        assertFalse(service.isSourceChannelBlackIntercept("s01", "c02"));
    }

    @Test
    void shouldReadCompensationAndQuoteDefaults() {
        DynamicConfigService service = new DynamicConfigService(new FakeDynamicConfigRepository());

        assertEquals(50, service.tradeCompensationBatchSize());
        assertEquals(15, service.quotaQuoteTtlMinutes());

        service.updateConfig(DynamicConfigService.TRADE_COMPENSATION_BATCH_SIZE, "80");
        service.updateConfig(DynamicConfigService.QUOTA_QUOTE_TTL_MINUTES, "20");

        assertEquals(80, service.tradeCompensationBatchSize());
        assertEquals(20, service.quotaQuoteTtlMinutes());
    }

    private static class FakeDynamicConfigRepository implements DynamicConfigRepository {

        private final Map<String, DynamicConfig> configs = new HashMap<>();

        @Override
        public Optional<DynamicConfig> queryByKey(String configKey) {
            return Optional.ofNullable(configs.get(configKey));
        }

        @Override
        public void saveOrUpdate(DynamicConfig config) {
            configs.put(config.getConfigKey(), config);
        }
    }
}















