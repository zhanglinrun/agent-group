package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.KnowledgeDocumentPO;
import com.linrun.infrastructure.po.KnowledgeFragmentPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IKnowledgeDocumentDao {

    void insertDocument(KnowledgeDocumentPO document);

    void insertFragments(@Param("fragments") List<KnowledgeFragmentPO> fragments);

    KnowledgeDocumentPO queryDocumentByDocumentId(@Param("documentId") String documentId);

    List<KnowledgeFragmentPO> queryFragmentsByDocumentId(@Param("documentId") String documentId);

    List<KnowledgeFragmentPO> queryEnabledFragmentsByVersion(@Param("knowledgeVersion") String knowledgeVersion);

    List<KnowledgeDocumentPO> queryDocumentsByStatus(@Param("status") String status,
                                                     @Param("limit") int limit);

    int updateDocumentStatus(KnowledgeDocumentPO document);
}
