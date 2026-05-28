package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.DynamicConfigPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IDynamicConfigDao {

    DynamicConfigPO queryByKey(@Param("configKey") String configKey);

    void saveOrUpdate(DynamicConfigPO config);
}
