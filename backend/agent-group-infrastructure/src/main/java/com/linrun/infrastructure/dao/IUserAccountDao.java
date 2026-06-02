package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.UserAccountPO;
import com.linrun.infrastructure.po.UserLoginSessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IUserAccountDao {

    void insertUser(UserAccountPO userAccount);

    UserAccountPO queryByUsername(@Param("username") String username);

    UserAccountPO queryByUserId(@Param("userId") String userId);

    void insertSession(UserLoginSessionPO session);

    UserLoginSessionPO querySessionByToken(@Param("token") String token);

    int invalidSession(@Param("token") String token);
}
