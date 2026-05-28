package com.linrun.domain.support.config.adapter;

import com.linrun.domain.support.config.model.DynamicConfig;

import java.util.Optional;
import java.util.List;

public interface DynamicConfigRepository {

    Optional<DynamicConfig> queryByKey(String configKey);

    default List<DynamicConfig> queryAll() {
        return List.of();
    }

    void saveOrUpdate(DynamicConfig config);
}
