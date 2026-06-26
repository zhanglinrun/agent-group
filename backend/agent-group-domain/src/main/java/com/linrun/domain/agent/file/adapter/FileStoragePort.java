package com.linrun.domain.agent.file.adapter;

import java.io.InputStream;

/**
 * 会话文件对象存储端口。
 *
 * <p>封装 MinIO 等对象存储的上传、下载、删除、预签名 URL 能力，
 * 屏蔽具体 SDK，由 infrastructure 提供实现。</p>
 */
public interface FileStoragePort {

    /**
     * 上传字节数组内容，返回可访问的下载 URL。
     */
    String upload(String objectName, byte[] content, String contentType) throws Exception;

    /**
     * 下载对象，返回输入流。
     */
    InputStream download(String objectName) throws Exception;

    /**
     * 删除对象。
     */
    void delete(String objectName) throws Exception;

    /**
     * 生成预签名下载 URL。
     */
    String presignedDownloadUrl(String objectName) throws Exception;
}
