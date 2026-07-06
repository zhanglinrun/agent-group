package com.linrun.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IAgentFileContentDao {

    String queryExtractedText(@Param("fileId") String fileId);
}
