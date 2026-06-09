package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.DynamicConfigPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IDynamicConfigDao {

    DynamicConfigPO queryByKey(@Param("configKey") String configKey);

    List<DynamicConfigPO> queryAll();

    void saveOrUpdate(DynamicConfigPO config);
}















