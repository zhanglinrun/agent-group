package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.UserAgentMemoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IUserAgentMemoryDao {

    List<UserAgentMemoryPO> queryByUser(@Param("userId") String userId,
                                        @Param("enabledOnly") boolean enabledOnly,
                                        @Param("limit") int limit);

    UserAgentMemoryPO queryByType(@Param("userId") String userId,
                                  @Param("memoryType") String memoryType);

    void upsert(UserAgentMemoryPO memory);

    int disable(@Param("userId") String userId,
                @Param("memoryType") String memoryType);
}
