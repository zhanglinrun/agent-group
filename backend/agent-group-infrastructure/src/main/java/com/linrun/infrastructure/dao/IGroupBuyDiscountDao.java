package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GroupBuyDiscountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGroupBuyDiscountDao {

    GroupBuyDiscountPO queryByDiscountId(@Param("discountId") String discountId);

    List<GroupBuyDiscountPO> queryDiscountList(@Param("limit") int limit);

    int insertDiscount(GroupBuyDiscountPO po);

    int updateDiscount(GroupBuyDiscountPO po);

    int updateDiscountEnabled(@Param("discountId") String discountId, @Param("enabled") Boolean enabled);

    int deleteByDiscountId(@Param("discountId") String discountId);
}















