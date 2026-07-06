-- =============================================================================
-- Reactor-agent 运行时建表脚本
-- 依据 Java 代码逆向补齐：工具产出表列来自 ToolOutputWriterImpl 的 row.put，
-- 类型来自 domain 层 *ToolOutput 与 infrastructure 层 *PO；访客、模型元数据、
-- 管理员、画布配置表列来自对应 PO / 实体（含 MyBatis-Plus 注解）。
-- 存储引擎 InnoDB，字符集 utf8mb4。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 一、9 张工具产出表
-- 公共字段来自 AbstractToolOutputPO；读取端 ToolOutputReaderImpl 按 created_at
-- 反序列化，故时间列固定用 created_at / updated_at。
-- 幂等：写入端首写为准（DuplicateKeyException 忽略），故 (request_id, tool_call_id) 唯一。
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tool_output_deep_search` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tool_invocation_id` BIGINT       DEFAULT NULL COMMENT '工具调用ID',
    `run_id`             BIGINT       DEFAULT NULL COMMENT '运行ID',
    `request_id`         VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
    `request_source`     VARCHAR(32)  DEFAULT NULL COMMENT '请求来源',
    `session_id`         VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    `tool_call_id`       VARCHAR(128) DEFAULT NULL COMMENT '工具调用标识',
    `status`             INT          DEFAULT NULL COMMENT '状态',
    `error_msg`          VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `query`              VARCHAR(1024) DEFAULT NULL COMMENT '检索问题',
    `answer_summary`     LONGTEXT     COMMENT '回答摘要',
    `stages_json`        LONGTEXT     COMMENT '阶段明细JSON',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_tool_call` (`request_id`, `tool_call_id`),
    KEY `idx_tool_invocation_id` (`tool_invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='deep_search 工具产出表';

CREATE TABLE IF NOT EXISTS `tool_output_file_tool` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tool_invocation_id` BIGINT       DEFAULT NULL COMMENT '工具调用ID',
    `run_id`             BIGINT       DEFAULT NULL COMMENT '运行ID',
    `request_id`         VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
    `request_source`     VARCHAR(32)  DEFAULT NULL COMMENT '请求来源',
    `session_id`         VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    `tool_call_id`       VARCHAR(128) DEFAULT NULL COMMENT '工具调用标识',
    `status`             INT          DEFAULT NULL COMMENT '状态',
    `error_msg`          VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `command`            VARCHAR(255) DEFAULT NULL COMMENT '文件命令',
    `primary_file_name`  VARCHAR(512) DEFAULT NULL COMMENT '主文件名',
    `preview_url`        VARCHAR(1024) DEFAULT NULL COMMENT '预览地址',
    `download_url`       VARCHAR(1024) DEFAULT NULL COMMENT '下载地址',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_tool_call` (`request_id`, `tool_call_id`),
    KEY `idx_tool_invocation_id` (`tool_invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='file_tool 工具产出表';

CREATE TABLE IF NOT EXISTS `tool_output_code_interpreter` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tool_invocation_id` BIGINT       DEFAULT NULL COMMENT '工具调用ID',
    `run_id`             BIGINT       DEFAULT NULL COMMENT '运行ID',
    `request_id`         VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
    `request_source`     VARCHAR(32)  DEFAULT NULL COMMENT '请求来源',
    `session_id`         VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    `tool_call_id`       VARCHAR(128) DEFAULT NULL COMMENT '工具调用标识',
    `status`             INT          DEFAULT NULL COMMENT '状态',
    `error_msg`          VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `code_output`        LONGTEXT     COMMENT '代码运行输出',
    `content`            LONGTEXT     COMMENT '内容',
    `code`               LONGTEXT     COMMENT '代码',
    `explain`            LONGTEXT     COMMENT '解释说明',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_tool_call` (`request_id`, `tool_call_id`),
    KEY `idx_tool_invocation_id` (`tool_invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='code_interpreter 工具产出表';

CREATE TABLE IF NOT EXISTS `tool_output_report_tool` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tool_invocation_id` BIGINT       DEFAULT NULL COMMENT '工具调用ID',
    `run_id`             BIGINT       DEFAULT NULL COMMENT '运行ID',
    `request_id`         VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
    `request_source`     VARCHAR(32)  DEFAULT NULL COMMENT '请求来源',
    `session_id`         VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    `tool_call_id`       VARCHAR(128) DEFAULT NULL COMMENT '工具调用标识',
    `status`             INT          DEFAULT NULL COMMENT '状态',
    `error_msg`          VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `file_type`          VARCHAR(64)  DEFAULT NULL COMMENT '文件类型',
    `summary`            LONGTEXT     COMMENT '摘要',
    `content`            LONGTEXT     COMMENT '报告内容',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_tool_call` (`request_id`, `tool_call_id`),
    KEY `idx_tool_invocation_id` (`tool_invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='report_tool 工具产出表';

CREATE TABLE IF NOT EXISTS `tool_output_data_analysis` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tool_invocation_id` BIGINT       DEFAULT NULL COMMENT '工具调用ID',
    `run_id`             BIGINT       DEFAULT NULL COMMENT '运行ID',
    `request_id`         VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
    `request_source`     VARCHAR(32)  DEFAULT NULL COMMENT '请求来源',
    `session_id`         VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    `tool_call_id`       VARCHAR(128) DEFAULT NULL COMMENT '工具调用标识',
    `status`             INT          DEFAULT NULL COMMENT '状态',
    `error_msg`          VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `task`               VARCHAR(1024) DEFAULT NULL COMMENT '分析任务',
    `summary`            LONGTEXT     COMMENT '摘要',
    `content`            LONGTEXT     COMMENT '分析内容',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_tool_call` (`request_id`, `tool_call_id`),
    KEY `idx_tool_invocation_id` (`tool_invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='data_analysis 工具产出表';

CREATE TABLE IF NOT EXISTS `tool_output_multimodal_agent` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tool_invocation_id` BIGINT       DEFAULT NULL COMMENT '工具调用ID',
    `run_id`             BIGINT       DEFAULT NULL COMMENT '运行ID',
    `request_id`         VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
    `request_source`     VARCHAR(32)  DEFAULT NULL COMMENT '请求来源',
    `session_id`         VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    `tool_call_id`       VARCHAR(128) DEFAULT NULL COMMENT '工具调用标识',
    `status`             INT          DEFAULT NULL COMMENT '状态',
    `error_msg`          VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `summary`            LONGTEXT     COMMENT '摘要',
    `markdown_content`   LONGTEXT     COMMENT 'Markdown 内容',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_tool_call` (`request_id`, `tool_call_id`),
    KEY `idx_tool_invocation_id` (`tool_invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='multimodal_agent 工具产出表';

CREATE TABLE IF NOT EXISTS `tool_output_image_generation` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tool_invocation_id` BIGINT       DEFAULT NULL COMMENT '工具调用ID',
    `run_id`             BIGINT       DEFAULT NULL COMMENT '运行ID',
    `request_id`         VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
    `request_source`     VARCHAR(32)  DEFAULT NULL COMMENT '请求来源',
    `session_id`         VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    `tool_call_id`       VARCHAR(128) DEFAULT NULL COMMENT '工具调用标识',
    `status`             INT          DEFAULT NULL COMMENT '状态',
    `error_msg`          VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `prompt`             LONGTEXT     COMMENT '提示词',
    `mode`               VARCHAR(32)  DEFAULT NULL COMMENT '生成模式',
    `summary`            LONGTEXT     COMMENT '摘要',
    `size`               VARCHAR(32)  DEFAULT NULL COMMENT '图片尺寸',
    `batch_count`        INT          DEFAULT NULL COMMENT '生成数量',
    `source_image_count` INT          DEFAULT NULL COMMENT '源图数量',
    `mask_image_count`   INT          DEFAULT NULL COMMENT '蒙版图数量',
    `used_fallback`      TINYINT(1)   DEFAULT NULL COMMENT '是否走回退链路',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_tool_call` (`request_id`, `tool_call_id`),
    KEY `idx_tool_invocation_id` (`tool_invocation_id`),
    KEY `idx_request_source` (`request_source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='image_generation_tool 工具产出表';

CREATE TABLE IF NOT EXISTS `tool_output_script_runner` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tool_invocation_id` BIGINT       DEFAULT NULL COMMENT '工具调用ID',
    `run_id`             BIGINT       DEFAULT NULL COMMENT '运行ID',
    `request_id`         VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
    `request_source`     VARCHAR(32)  DEFAULT NULL COMMENT '请求来源',
    `session_id`         VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    `tool_call_id`       VARCHAR(128) DEFAULT NULL COMMENT '工具调用标识',
    `status`             INT          DEFAULT NULL COMMENT '状态',
    `error_msg`          VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `skill_name`         VARCHAR(255) DEFAULT NULL COMMENT '技能名称',
    `script_name`        VARCHAR(255) DEFAULT NULL COMMENT '脚本名称',
    `runtime`            VARCHAR(64)  DEFAULT NULL COMMENT '运行时',
    `success`            TINYINT(1)   DEFAULT NULL COMMENT '是否成功',
    `exit_code`          INT          DEFAULT NULL COMMENT '退出码',
    `stdout`             LONGTEXT     COMMENT '标准输出',
    `stderr`             LONGTEXT     COMMENT '标准错误',
    `summary`            LONGTEXT     COMMENT '摘要',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_tool_call` (`request_id`, `tool_call_id`),
    KEY `idx_tool_invocation_id` (`tool_invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='script_runner_tool 工具产出表';

CREATE TABLE IF NOT EXISTS `tool_output_planning` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tool_invocation_id` BIGINT       DEFAULT NULL COMMENT '工具调用ID',
    `run_id`             BIGINT       DEFAULT NULL COMMENT '运行ID',
    `request_id`         VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
    `request_source`     VARCHAR(32)  DEFAULT NULL COMMENT '请求来源',
    `session_id`         VARCHAR(128) DEFAULT NULL COMMENT '会话ID',
    `tool_call_id`       VARCHAR(128) DEFAULT NULL COMMENT '工具调用标识',
    `status`             INT          DEFAULT NULL COMMENT '状态',
    `error_msg`          VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `command`            VARCHAR(255) DEFAULT NULL COMMENT '本轮命令',
    `before_plan_json`   LONGTEXT     COMMENT '执行前计划快照JSON',
    `after_plan_json`    LONGTEXT     COMMENT '执行后计划快照JSON',
    `current_step`       VARCHAR(1024) DEFAULT NULL COMMENT '当前可执行步骤',
    `current_step_index` INT          DEFAULT NULL COMMENT '当前步骤索引',
    `auto_advanced`      TINYINT(1)   DEFAULT NULL COMMENT '是否自动推进',
    `auto_finished`      TINYINT(1)   DEFAULT NULL COMMENT '是否自动结束',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_tool_call` (`request_id`, `tool_call_id`),
    KEY `idx_tool_invocation_id` (`tool_invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='planning 工具产出表';

-- -----------------------------------------------------------------------------
-- 二、访客 / 模型元数据 / 管理员 / 画布配置
-- -----------------------------------------------------------------------------

-- 匿名访客表，列来自 VisitorIdentityPO。
CREATE TABLE IF NOT EXISTS `visitor_identity` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `visitor_id`       VARCHAR(64)  NOT NULL COMMENT '访客ID',
    `token_digest`     VARCHAR(128) NOT NULL COMMENT '访客令牌摘要',
    `status`           INT          DEFAULT NULL COMMENT '状态',
    `first_seen_at`    DATETIME     DEFAULT NULL COMMENT '首次出现时间',
    `last_seen_at`     DATETIME     DEFAULT NULL COMMENT '最近出现时间',
    `last_ip`          VARCHAR(64)  DEFAULT NULL COMMENT '最近IP',
    `last_user_agent`  VARCHAR(512) DEFAULT NULL COMMENT '最近UserAgent',
    `username`         VARCHAR(128) DEFAULT NULL COMMENT '绑定用户名',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_visitor_id` (`visitor_id`),
    UNIQUE KEY `uk_token_digest` (`token_digest`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匿名访客身份表';

-- 管理员用户表，列来自 AdminUser PO。
CREATE TABLE IF NOT EXISTS `admin_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `username`    VARCHAR(128) NOT NULL COMMENT '用户名',
    `password`    VARCHAR(255) DEFAULT NULL COMMENT '密码（加密存储）',
    `status`      INT          DEFAULT NULL COMMENT '状态(0:禁用,1:启用,2:锁定)',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员用户表';

-- AI 智能体拖拉拽配置主表，列来自 AiAgentDrawConfig PO。
CREATE TABLE IF NOT EXISTS `ai_agent_draw_config` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_id`   VARCHAR(64)  NOT NULL COMMENT '配置ID（唯一标识）',
    `config_name` VARCHAR(255) DEFAULT NULL COMMENT '配置名称',
    `description` VARCHAR(1024) DEFAULT NULL COMMENT '配置描述',
    `agent_id`    VARCHAR(64)  DEFAULT NULL COMMENT '关联智能体ID',
    `config_data` LONGTEXT     COMMENT '拖拉拽配置JSON数据（nodes 与 edges）',
    `version`     INT          DEFAULT NULL COMMENT '配置版本号',
    `status`      INT          DEFAULT NULL COMMENT '状态(0:禁用,1:启用)',
    `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_id` (`config_id`),
    KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体拖拉拽配置主表';

-- 模型信息表，列来自 ChatModelInfo 实体（MyBatis-Plus 默认驼峰转下划线）。
-- yn 为 @TableLogic 逻辑删除字段（1=有效,0=删除）。
CREATE TABLE IF NOT EXISTS `chat_model_info` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code`            VARCHAR(128) DEFAULT NULL COMMENT '模型编码',
    `type`            VARCHAR(64)  DEFAULT NULL COMMENT '模型类型',
    `content`         LONGTEXT     COMMENT '模型内容',
    `name`            VARCHAR(255) DEFAULT NULL COMMENT '模型名称',
    `use_prompt`      LONGTEXT     COMMENT '使用提示词',
    `business_prompt` LONGTEXT     COMMENT '业务提示词',
    `yn`              INT          NOT NULL DEFAULT 1 COMMENT '逻辑删除(1:有效,0:删除)',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话模型信息表';

-- 模型字段 schema 表，列来自 ChatModelSchema 实体（MyBatis-Plus 默认驼峰转下划线）。
CREATE TABLE IF NOT EXISTS `chat_model_schema` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `model_code`       VARCHAR(128) DEFAULT NULL COMMENT '模型编码',
    `column_id`        VARCHAR(128) DEFAULT NULL COMMENT '字段ID',
    `column_name`      VARCHAR(255) DEFAULT NULL COMMENT '字段名称',
    `column_comment`   VARCHAR(512) DEFAULT NULL COMMENT '字段注释',
    `few_shot`         LONGTEXT     COMMENT 'few-shot 示例',
    `data_type`        VARCHAR(64)  DEFAULT NULL COMMENT '数据类型',
    `synonyms`         LONGTEXT     COMMENT '同义词',
    `vector_uuid`      VARCHAR(64)  DEFAULT NULL COMMENT '向量UUID',
    `default_recall`   INT          NOT NULL DEFAULT 0 COMMENT '默认召回',
    `analyze_suggest`  INT          NOT NULL DEFAULT 0 COMMENT '分析建议',
    `yn`               INT          NOT NULL DEFAULT 1 COMMENT '逻辑删除(1:有效,0:删除)',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_model_code` (`model_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话模型字段schema表';
