package com.linrun.domain.agent.file.adapter;

import com.linrun.domain.agent.file.model.ParsedFile;

/**
 * 会话文件解析端口。
 *
 * <p>负责把 PDF、DOCX、TXT、Markdown 等文件解析成纯文本，
 * 具体解析技术（PDFBox、POI 等）由 infrastructure 实现。</p>
 */
public interface FileParsePort {

    /**
     * 解析文件内容。
     *
     * @param fileName    原始文件名，用于判断文件类型
     * @param content     文件字节内容
     * @param contentType MIME 类型（可空）
     * @return 解析结果，含全量文本和截断文本
     */
    ParsedFile parse(String fileName, byte[] content, String contentType);
}
