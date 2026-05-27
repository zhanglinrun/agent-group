package com.linrun.domain.support.config.adapter;

import com.linrun.domain.support.config.model.DynamicConfig;

import java.util.Optional;

public interface DynamicConfigRepository {

    Optional<DynamicConfig> queryByKey(String configKey);

    void saveOrUpdate(DynamicConfig config);
}
