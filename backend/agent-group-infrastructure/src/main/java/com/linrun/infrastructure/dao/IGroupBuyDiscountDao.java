package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GroupBuyDiscountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGroupBuyDiscountDao {

    GroupBuyDiscountPO queryByDiscountId(@Param("discountId") String discountId);
}















