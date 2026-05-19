package com.linrun.infrastructure.dcc.repository;

import com.linrun.domain.dcc.adapter.DynamicConfigRepository;
import com.linrun.domain.dcc.model.DynamicConfig;
import com.linrun.infrastructure.dao.IDynamicConfigDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisDynamicConfigRepository implements DynamicConfigRepository {

    private final IDynamicConfigDao dynamicConfigDao;

    public MyBatisDynamicConfigRepository(IDynamicConfigDao dynamicConfigDao) {
        this.dynamicConfigDao = dynamicConfigDao;
    }

    @Override
    public Optional<DynamicConfig> queryByKey(String configKey) {
        return Optional.ofNullable(dynamicConfigDao.queryByKey(configKey));
    }

    @Override
    public void saveOrUpdate(DynamicConfig config) {
        dynamicConfigDao.saveOrUpdate(config);
    }
}
