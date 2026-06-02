package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.AcademicArtifactPO;
import com.linrun.infrastructure.po.AcademicFilePO;
import com.linrun.infrastructure.po.AcademicMessagePO;
import com.linrun.infrastructure.po.AcademicSessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IAcademicAgentDao {

    void insertSessionIfAbsent(AcademicSessionPO session);

    int updateSession(AcademicSessionPO session);

    void insertMessage(AcademicMessagePO message);

    List<AcademicMessagePO> queryMessages(@Param("userId") String userId, @Param("sessionId") String sessionId);

    AcademicSessionPO querySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    List<AcademicSessionPO> querySessions(@Param("userId") String userId, @Param("limit") int limit);

    int deleteSession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int deleteMessagesBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int deleteFilesBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int deleteArtifactsBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    void insertFile(AcademicFilePO file);

    AcademicFilePO queryFile(@Param("userId") String userId, @Param("fileId") String fileId);

    List<AcademicFilePO> queryFiles(@Param("userId") String userId, @Param("limit") int limit);

    List<AcademicFilePO> queryFilesBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int deleteFile(@Param("userId") String userId, @Param("fileId") String fileId);

    void insertArtifact(AcademicArtifactPO artifact);

    List<AcademicArtifactPO> queryArtifacts(@Param("userId") String userId, @Param("sessionId") String sessionId);
}
