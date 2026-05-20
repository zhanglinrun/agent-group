package com.linrun.infrastructure.dao;

import com.linrun.domain.marketing.model.GroupBuyDiscount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IGroupBuyDiscountDao {

    GroupBuyDiscount queryByDiscountId(@Param("discountId") String discountId);
}
