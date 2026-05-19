package com.linrun.infrastructure.dao;

import com.linrun.domain.dcc.model.DynamicConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IDynamicConfigDao {

    DynamicConfig queryByKey(@Param("configKey") String configKey);

    void saveOrUpdate(DynamicConfig config);
}
