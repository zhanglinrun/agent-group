-- Reactor 执行账本建表 SQL
-- 覆盖会话、run、LLM 调用、工具调用、产物 5 张账本表
-- 引擎 InnoDB，字符集 utf8mb4，含主键自增 id、create_time/update_time 与常用索引

SET NAMES utf8mb4;

-- ----------------------------
-- 会话主表
-- ----------------------------
DROP TABLE IF EXISTS `dialogue_session`;
CREATE TABLE `dialogue_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `visitor_id` VARCHAR(64) DEFAULT NULL COMMENT '匿名访客ID',
    `title` VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
    `status` INT DEFAULT NULL COMMENT '会话状态',
    `latest_request_id` VARCHAR(64) DEFAULT NULL COMMENT '最新请求ID',
    `latest_query_text` LONGTEXT COMMENT '最新用户问题',
    `latest_summary_text` LONGTEXT COMMENT '最新总结文本',
    `run_count` INT DEFAULT 0 COMMENT 'run 总数',
    `finished_run_count` INT DEFAULT 0 COMMENT '已完成 run 数',
    `failed_run_count` INT DEFAULT 0 COMMENT '失败 run 数',
    `started_at` DATETIME DEFAULT NULL COMMENT '会话开始时间',
    `last_active_at` DATETIME DEFAULT NULL COMMENT '最近活跃时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_visitor_active` (`visitor_id`, `last_active_at`),
    KEY `idx_last_active_at` (`last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话级执行摘要';

-- ----------------------------
-- 对话执行总账
-- ----------------------------
DROP TABLE IF EXISTS `dialogue_run`;
CREATE TABLE `dialogue_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_uid` VARCHAR(64) DEFAULT NULL COMMENT '对外稳定运行标识',
    `request_id` VARCHAR(64) NOT NULL COMMENT '单次请求ID',
    `session_id` VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
    `visitor_id` VARCHAR(64) DEFAULT NULL COMMENT '匿名访客ID',
    `entry_agent` VARCHAR(64) DEFAULT NULL COMMENT '入口执行链 react / plan_solve',
    `status` INT DEFAULT NULL COMMENT '运行状态',
    `query_text` LONGTEXT COMMENT '用户原始问题',
    `final_summary_text` LONGTEXT COMMENT '最终总结文本',
    `llm_call_count` INT DEFAULT 0 COMMENT 'LLM 调用次数',
    `tool_call_count` INT DEFAULT 0 COMMENT '工具调用次数',
    `artifact_count` INT DEFAULT 0 COMMENT '产物数量',
    `prompt_tokens_total` INT DEFAULT 0 COMMENT 'LLM 输入 token 总量',
    `completion_tokens_total` INT DEFAULT 0 COMMENT 'LLM 输出 token 总量',
    `total_tokens_total` INT DEFAULT 0 COMMENT 'LLM token 总量',
    `error_code` VARCHAR(64) DEFAULT NULL COMMENT '失败码',
    `error_msg` LONGTEXT COMMENT '失败信息',
    `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
    `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '总耗时(毫秒)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`),
    KEY `idx_session_started` (`session_id`, `started_at`),
    KEY `idx_run_uid` (`run_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单次对话执行总账';

-- ----------------------------
-- LLM 调用账本
-- ----------------------------
DROP TABLE IF EXISTS `llm_invocation`;
CREATE TABLE `llm_invocation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id` BIGINT NOT NULL COMMENT '所属 run',
    `invocation_seq` INT DEFAULT NULL COMMENT 'run 内递增序号',
    `agent_name` VARCHAR(64) DEFAULT NULL COMMENT '当前 agent 名称',
    `step_no` INT DEFAULT NULL COMMENT '当前步号',
    `call_kind` VARCHAR(32) DEFAULT NULL COMMENT 'ask / askTool',
    `streaming` INT DEFAULT NULL COMMENT '是否流式',
    `model_name` VARCHAR(128) DEFAULT NULL COMMENT '模型名',
    `response_text` LONGTEXT COMMENT '完整响应文本',
    `tool_call_count` INT DEFAULT 0 COMMENT '工具调用数量',
    `prompt_tokens` INT DEFAULT 0 COMMENT 'prompt token',
    `completion_tokens` INT DEFAULT 0 COMMENT 'completion token',
    `total_tokens` INT DEFAULT 0 COMMENT 'total token',
    `finish_reason` VARCHAR(64) DEFAULT NULL COMMENT '完成原因',
    `status` INT DEFAULT NULL COMMENT '状态',
    `error_msg` LONGTEXT COMMENT '错误信息',
    `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
    `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_run_seq` (`run_id`, `invocation_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单次 LLM 调用账本';

-- ----------------------------
-- 工具调用账本
-- ----------------------------
DROP TABLE IF EXISTS `tool_invocation`;
CREATE TABLE `tool_invocation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id` BIGINT NOT NULL COMMENT '所属 run',
    `llm_invocation_id` BIGINT DEFAULT NULL COMMENT '来源 LLM 调用',
    `tool_call_id` VARCHAR(128) DEFAULT NULL COMMENT '模型返回的 toolCallId',
    `dispatch_index` INT DEFAULT NULL COMMENT '原始分发顺序',
    `agent_name` VARCHAR(64) DEFAULT NULL COMMENT '当前 agent 名称',
    `step_no` INT DEFAULT NULL COMMENT '当前步号',
    `tool_name` VARCHAR(128) DEFAULT NULL COMMENT '工具名称',
    `tool_provider` VARCHAR(32) DEFAULT NULL COMMENT 'local / mcp',
    `input_json` LONGTEXT COMMENT '入参 JSON',
    `llm_observation` LONGTEXT COMMENT '主智能体 observation',
    `status` INT DEFAULT NULL COMMENT '状态',
    `error_msg` LONGTEXT COMMENT '错误信息',
    `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
    `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_run_dispatch` (`run_id`, `dispatch_index`),
    KEY `idx_llm_invocation_id` (`llm_invocation_id`),
    KEY `idx_tool_name_started` (`tool_name`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单次工具调用账本';

-- ----------------------------
-- 产物账本
-- ----------------------------
DROP TABLE IF EXISTS `artifact_record`;
CREATE TABLE `artifact_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id` BIGINT DEFAULT NULL COMMENT '所属 run',
    `request_id` VARCHAR(64) DEFAULT NULL COMMENT '非 run 场景下用于直连请求',
    `tool_invocation_id` BIGINT DEFAULT NULL COMMENT '输出文件对应的 tool invocation',
    `tool_call_id` VARCHAR(128) DEFAULT NULL COMMENT '输出文件对应的 toolCallId',
    `artifact_role` VARCHAR(32) DEFAULT NULL COMMENT 'input / output',
    `visibility` VARCHAR(32) DEFAULT NULL COMMENT 'visible / internal',
    `source_type` VARCHAR(32) DEFAULT NULL COMMENT 'user_upload / tool_output',
    `source_name` VARCHAR(255) DEFAULT NULL COMMENT '来源名称',
    `file_name` VARCHAR(255) DEFAULT NULL COMMENT '文件名',
    `storage_key` VARCHAR(512) DEFAULT NULL COMMENT '稳定资源 key',
    `download_url` VARCHAR(1024) DEFAULT NULL COMMENT '下载地址',
    `preview_url` VARCHAR(1024) DEFAULT NULL COMMENT '预览地址',
    `mime_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小',
    `file_hash` VARCHAR(128) DEFAULT NULL COMMENT '文件哈希',
    `metadata_json` LONGTEXT COMMENT '扩展元数据',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_run_role` (`run_id`, `artifact_role`),
    KEY `idx_tool_invocation_role` (`tool_invocation_id`, `artifact_role`),
    KEY `idx_request_toolcall` (`request_id`, `tool_call_id`),
    KEY `idx_run_toolcall` (`run_id`, `tool_call_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='输入/输出文件归属账本';
