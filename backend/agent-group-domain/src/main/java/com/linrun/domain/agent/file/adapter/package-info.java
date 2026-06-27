/**
 * 会话文件技术接入端口。
 *
 * <p>端口定义在 domain，由 infrastructure 提供实现，trigger 面向端口编程，
 * 避免技术实现（MinIO、PDFBox、Spring AI 向量库）泄漏到 trigger 层。</p>
 */
package com.linrun.domain.agent.file.adapter;
