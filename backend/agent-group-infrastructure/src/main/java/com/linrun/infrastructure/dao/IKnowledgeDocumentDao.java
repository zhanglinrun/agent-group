package com.linrun.infrastructure.dao;

import com.linrun.domain.knowledge.model.KnowledgeDocument;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IKnowledgeDocumentDao {

    void insertDocument(KnowledgeDocument document);

    void insertFragments(@Param("fragments") List<KnowledgeFragment> fragments);

    KnowledgeDocument queryDocumentByDocumentId(@Param("documentId") String documentId);

    List<KnowledgeFragment> queryFragmentsByDocumentId(@Param("documentId") String documentId);
}
