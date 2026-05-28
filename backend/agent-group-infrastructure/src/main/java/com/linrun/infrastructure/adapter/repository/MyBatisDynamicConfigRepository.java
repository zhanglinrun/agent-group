package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.support.config.adapter.DynamicConfigRepository;
import com.linrun.domain.support.config.model.DynamicConfig;
import com.linrun.infrastructure.converter.SupportPOConverter;
import com.linrun.infrastructure.dao.IDynamicConfigDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public class MyBatisDynamicConfigRepository implements DynamicConfigRepository {

    private final IDynamicConfigDao dynamicConfigDao;

    public MyBatisDynamicConfigRepository(IDynamicConfigDao dynamicConfigDao) {
        this.dynamicConfigDao = dynamicConfigDao;
    }

    @Override
    public Optional<DynamicConfig> queryByKey(String configKey) {
        return Optional.ofNullable(SupportPOConverter.toEntity(dynamicConfigDao.queryByKey(configKey)));
    }

    @Override
    public List<DynamicConfig> queryAll() {
        return dynamicConfigDao.queryAll().stream()
                .map(SupportPOConverter::toEntity)
                .toList();
    }

    @Override
    public void saveOrUpdate(DynamicConfig config) {
        dynamicConfigDao.saveOrUpdate(SupportPOConverter.toPO(config));
    }
}
