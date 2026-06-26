package com.linrun.domain.agent.file.adapter;

import com.linrun.domain.agent.file.model.EmbeddingChunk;
import com.linrun.domain.agent.file.model.RagRetrievalResult;

import java.util.List;

/**
 * 会话文件向量化与 RAG 检索端口。
 *
 * <p>封装向量入库和语义检索能力，底层 Spring AI EmbeddingModel/VectorStore
 * 由 infrastructure 实现并屏蔽。向量库不可用时实现应安全降级。</p>
 */
public interface EmbeddingPort {

    /**
     * 将文本片段向量化并写入向量库。
     */
    void embedAndStore(List<EmbeddingChunk> chunks);

    /**
     * 根据文件 ID 和问题做 RAG 语义检索。
     */
    RagRetrievalResult ragRetrieve(String fileId, String question);
}
