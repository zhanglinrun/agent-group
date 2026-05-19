package com.linrun.domain.dcc.adapter;

import com.linrun.domain.dcc.model.DynamicConfig;

import java.util.Optional;

public interface DynamicConfigRepository {

    Optional<DynamicConfig> queryByKey(String configKey);

    void saveOrUpdate(DynamicConfig config);
}
