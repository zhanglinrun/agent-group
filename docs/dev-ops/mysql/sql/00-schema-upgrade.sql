use agent_group;

create table if not exists user_membership_account (
  user_id varchar(64) not null comment 'user id',
  plan_code varchar(32) not null default 'FREE' comment 'membership plan',
  plan_name varchar(64) not null default 'Free' comment 'membership name',
  status varchar(32) not null default 'INACTIVE' comment 'membership status',
  monthly_quota decimal(12, 2) not null default 0.00 comment 'monthly quota',
  monthly_used_quota decimal(12, 2) not null default 0.00 comment 'monthly used quota',
  cycle_start_time datetime not null comment 'cycle start time',
  cycle_end_time datetime not null comment 'cycle end time',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (user_id),
  key idx_status_cycle (status, cycle_end_time)
) engine=InnoDB default charset=utf8mb4 comment='user membership account';

create table if not exists user_model_config (
  user_id varchar(64) not null comment 'user id',
  enabled tinyint not null default 0 comment 'custom model enabled',
  base_url varchar(256) not null default '' comment 'api base url',
  model varchar(128) not null default '' comment 'model name',
  text_base_url varchar(256) not null default '' comment 'text model api base url',
  text_model varchar(128) not null default '' comment 'text model name',
  image_base_url varchar(256) not null default '' comment 'image model api base url',
  image_model varchar(128) not null default '' comment 'image model name',
  encrypted_api_key text null comment 'encrypted api key',
  encrypted_text_api_key text null comment 'encrypted text model api key',
  encrypted_image_api_key text null comment 'encrypted image model api key',
  key_masked varchar(64) not null default '' comment 'masked api key',
  text_key_masked varchar(64) not null default '' comment 'masked text model api key',
  image_key_masked varchar(64) not null default '' comment 'masked image model api key',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (user_id)
) engine=InnoDB default charset=utf8mb4 comment='user model config';

create table if not exists user_agent_memory (
  id bigint unsigned not null auto_increment comment 'auto id',
  user_id varchar(64) not null comment 'user id',
  memory_type varchar(32) not null comment 'memory type',
  content varchar(2048) not null comment 'memory content',
  enabled tinyint not null default 1 comment 'enabled',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_user_memory_type (user_id, memory_type),
  key idx_user_enabled_time (user_id, enabled, update_time)
) engine=InnoDB default charset=utf8mb4 comment='user agent long memory';

set @sql = (
  select if(count(*) = 0,
    'alter table user_model_config add column text_model varchar(128) not null default '''' comment ''text model name'' after model',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user_model_config'
    and column_name = 'text_model'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table user_model_config add column text_base_url varchar(256) not null default '''' comment ''text model api base url''',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user_model_config'
    and column_name = 'text_base_url'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table user_model_config add column image_model varchar(128) not null default '''' comment ''image model name'' after text_model',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user_model_config'
    and column_name = 'image_model'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table user_model_config add column image_base_url varchar(256) not null default '''' comment ''image model api base url''',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user_model_config'
    and column_name = 'image_base_url'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table user_model_config add column encrypted_text_api_key text null comment ''encrypted text model api key''',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user_model_config'
    and column_name = 'encrypted_text_api_key'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table user_model_config add column encrypted_image_api_key text null comment ''encrypted image model api key''',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user_model_config'
    and column_name = 'encrypted_image_api_key'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table user_model_config add column text_key_masked varchar(64) not null default '''' comment ''masked text model api key''',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user_model_config'
    and column_name = 'text_key_masked'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table user_model_config add column image_key_masked varchar(64) not null default '''' comment ''masked image model api key''',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user_model_config'
    and column_name = 'image_key_masked'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

update user_model_config
set text_base_url = base_url
where text_base_url = ''
  and base_url <> '';

update user_model_config
set text_model = model
where text_model = ''
  and model <> '';

update user_model_config
set encrypted_text_api_key = encrypted_api_key
where encrypted_text_api_key is null
  and encrypted_api_key is not null;

update user_model_config
set text_key_masked = key_masked
where text_key_masked = ''
  and key_masked <> '';

update user_model_config
set image_model = 'gpt-image-2'
where image_model = '';

create table if not exists academic_agent_run (
  id bigint unsigned not null auto_increment comment '自增主键',
  run_id varchar(40) not null comment '运行编号',
  session_id varchar(64) not null comment '会话编号',
  project_id varchar(40) not null default '' comment 'academic project id',
  request_id varchar(64) not null comment '请求编号',
  user_id varchar(64) not null comment '用户编号',
  task_type varchar(32) not null default '' comment '任务类型',
  question varchar(2048) not null default '' comment '用户问题',
  status varchar(32) not null default 'RUNNING' comment '运行状态',
  model_name varchar(128) not null default '' comment '模型名称',
  final_summary mediumtext null comment '最终摘要',
  error_code varchar(64) not null default '' comment '错误码',
  error_message varchar(1024) not null default '' comment '错误信息',
  started_at datetime not null default current_timestamp comment '开始时间',
  finished_at datetime null comment '结束时间',
  duration_millis bigint not null default 0 comment '耗时毫秒',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_run_id (run_id),
  key idx_request_id (request_id),
  key idx_user_project_time (user_id, project_id, started_at),
  key idx_user_session_time (user_id, session_id, started_at)
) engine=InnoDB default charset=utf8mb4 comment='Agent智能体执行运行表';

create table if not exists academic_llm_invocation (
  id bigint unsigned not null auto_increment comment '自增主键',
  invocation_id varchar(40) not null comment '模型调用编号',
  run_id varchar(40) not null comment '运行编号',
  request_id varchar(64) not null comment '请求编号',
  session_id varchar(64) not null comment '会话编号',
  user_id varchar(64) not null comment '用户编号',
  model_name varchar(128) not null default '' comment '模型名称',
  prompt_summary varchar(2048) not null default '' comment '提示词摘要',
  response_text mediumtext null comment '模型响应',
  status varchar(32) not null default 'RUNNING' comment '调用状态',
  prompt_tokens bigint not null default 0 comment '输入 token 数',
  completion_tokens bigint not null default 0 comment '输出 token 数',
  total_tokens bigint not null default 0 comment '总 token 数',
  fallback_used tinyint not null default 0 comment '是否使用回退结果',
  error_message varchar(1024) not null default '' comment '错误信息',
  started_at datetime not null default current_timestamp comment '开始时间',
  finished_at datetime null comment '结束时间',
  latency_millis bigint not null default 0 comment '耗时毫秒',
  primary key (id),
  unique key uk_invocation_id (invocation_id),
  key idx_run_time (run_id, started_at)
) engine=InnoDB default charset=utf8mb4 comment='Agent智能体模型调用表';

create table if not exists academic_tool_invocation (
  id bigint unsigned not null auto_increment comment '自增主键',
  invocation_id varchar(40) not null comment '工具调用编号',
  run_id varchar(40) not null comment '运行编号',
  request_id varchar(64) not null comment '请求编号',
  session_id varchar(64) not null comment '会话编号',
  user_id varchar(64) not null comment '用户编号',
  tool_call_id varchar(80) not null default '' comment '模型侧工具调用编号',
  tool_name varchar(128) not null comment '工具名称',
  action varchar(128) not null default '' comment '工具动作',
  arguments_json mediumtext null comment '工具入参',
  result_summary varchar(1024) not null default '' comment '结果摘要',
  result_json mediumtext null comment '结构化结果',
  status varchar(32) not null default 'RUNNING' comment '调用状态',
  retry_count int not null default 0 comment '重试次数',
  error_message varchar(1024) not null default '' comment '错误信息',
  started_at datetime not null default current_timestamp comment '开始时间',
  finished_at datetime null comment '结束时间',
  latency_millis bigint not null default 0 comment '耗时毫秒',
  primary key (id),
  unique key uk_invocation_id (invocation_id),
  key idx_run_time (run_id, started_at),
  key idx_tool_name_time (tool_name, started_at)
) engine=InnoDB default charset=utf8mb4 comment='Agent智能体工具调用表';

create table if not exists academic_project (
  id bigint unsigned not null auto_increment comment 'auto id',
  project_id varchar(40) not null comment 'academic project id',
  user_id varchar(64) not null comment 'user id',
  title varchar(120) not null default '' comment 'project title',
  research_question varchar(500) not null default '' comment 'research question',
  target_venue varchar(120) not null default '' comment 'target venue',
  writing_status varchar(40) not null default 'DRAFTING' comment 'writing status',
  progress_note varchar(500) not null default '' comment 'progress note',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_project_id (project_id),
  key idx_user_time (user_id, update_time)
) engine=InnoDB default charset=utf8mb4 comment='academic project';

create table if not exists academic_project_file (
  id bigint unsigned not null auto_increment comment 'auto id',
  project_id varchar(40) not null comment 'academic project id',
  user_id varchar(64) not null comment 'user id',
  file_id varchar(64) not null comment 'file id',
  file_name varchar(160) not null default '' comment 'file name',
  file_type varchar(80) not null default '' comment 'file type',
  folder_type varchar(40) not null default 'draftManuscripts' comment 'folder type',
  summary varchar(800) not null default '' comment 'summary',
  content_preview mediumtext null comment 'content preview',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_project_file (project_id, file_id),
  key idx_user_project (user_id, project_id)
) engine=InnoDB default charset=utf8mb4 comment='academic project file';

create table if not exists academic_project_patch (
  id bigint unsigned not null auto_increment comment 'auto id',
  project_id varchar(40) not null comment 'academic project id',
  user_id varchar(64) not null comment 'user id',
  patch_id varchar(64) not null comment 'patch id',
  file_id varchar(64) not null comment 'file id',
  title varchar(160) not null default '' comment 'patch title',
  reason varchar(1000) not null default '' comment 'patch reason',
  before_text mediumtext null comment 'before text',
  after_text mediumtext null comment 'after text',
  status varchar(32) not null default 'PENDING' comment 'patch status',
  create_time datetime not null default current_timestamp comment 'create time',
  apply_time datetime null comment 'apply time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_patch_id (patch_id),
  key idx_project_status (project_id, status),
  key idx_user_project (user_id, project_id)
) engine=InnoDB default charset=utf8mb4 comment='academic project patch';

set @sql = (
  select if(count(*) = 0,
    'alter table academic_agent_run add column project_id varchar(40) not null default '''' comment ''academic project id'' after session_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'academic_agent_run'
    and column_name = 'project_id'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table academic_agent_run add index idx_user_project_time (user_id, project_id, started_at)',
    'select 1')
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'academic_agent_run'
    and index_name = 'idx_user_project_time'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) > 0,
    'alter table academic_agent_artifact modify column artifact_id varchar(256) not null comment ''产物编号''',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'academic_agent_artifact'
    and column_name = 'artifact_id'
    and character_maximum_length < 256
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table academic_agent_artifact add column run_id varchar(40) not null default '''' comment ''运行编号'' after user_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'academic_agent_artifact'
    and column_name = 'run_id'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table academic_agent_artifact add column tool_invocation_id varchar(40) not null default '''' comment ''工具调用编号'' after run_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'academic_agent_artifact'
    and column_name = 'tool_invocation_id'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table academic_agent_artifact add column source_type varchar(32) not null default ''AGENT'' comment ''来源类型'' after tool_invocation_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'academic_agent_artifact'
    and column_name = 'source_type'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table academic_agent_artifact add column source_name varchar(128) not null default '''' comment ''来源名称'' after source_type',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'academic_agent_artifact'
    and column_name = 'source_name'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table academic_agent_artifact add index idx_run_tool (run_id, tool_invocation_id)',
    'select 1')
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'academic_agent_artifact'
    and index_name = 'idx_run_tool'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table trade_order add column idempotent_key varchar(128) default null comment ''幂等键'' after order_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'trade_order'
    and column_name = 'idempotent_key'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 1,
    'alter table pay_order modify column pay_url mediumtext null comment ''payment url or page form html''',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'pay_order'
    and column_name = 'pay_url'
    and data_type <> 'mediumtext'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table trade_order add unique key uk_idempotent_key (idempotent_key)',
    'select 1')
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'trade_order'
    and index_name = 'uk_idempotent_key'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_document add column source_type varchar(32) not null default ''INIT_DATA'' comment ''来源类型'' after knowledge_version',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_document'
    and column_name = 'source_type'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_document add column source_name varchar(128) not null default ''初始化数据'' comment ''来源名称'' after source_type',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_document'
    and column_name = 'source_name'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_document add column document_status varchar(32) not null default ''ENABLED'' comment ''文档状态'' after source_name',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_document'
    and column_name = 'document_status'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add column fragment_status varchar(32) not null default ''ENABLED'' comment ''片段状态'' after rank_no',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and column_name = 'fragment_status'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

alter table knowledge_fragment modify column content text not null comment '片段内容';

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add column parent_fragment_id varchar(32) default null comment ''父片段编号'' after rank_no',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and column_name = 'parent_fragment_id'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add column brother_group_id varchar(64) not null default '''' comment ''兄弟片段组'' after parent_fragment_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and column_name = 'brother_group_id'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add column brother_index int not null default 1 comment ''兄弟片段序号'' after brother_group_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and column_name = 'brother_index'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add column brother_total int not null default 1 comment ''兄弟片段总数'' after brother_index',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and column_name = 'brother_total'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add column chunk_type varchar(16) not null default ''CHILD'' comment ''片段类型'' after brother_total',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and column_name = 'chunk_type'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add column embedding_enabled tinyint not null default 1 comment ''是否写入向量'' after chunk_type',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and column_name = 'embedding_enabled'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add index idx_parent_fragment (parent_fragment_id)',
    'select 1')
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and index_name = 'idx_parent_fragment'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add index idx_brother_group (brother_group_id, brother_index)',
    'select 1')
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and index_name = 'idx_brother_group'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists group_buy_stock (
  id bigint unsigned not null auto_increment comment '自增主键',
  activity_id varchar(32) not null comment '活动编号',
  goods_id varchar(32) not null comment '商品编号',
  total_stock int not null comment '总库存',
  available_stock int not null comment '可用库存',
  locked_stock int not null default 0 comment '锁定库存',
  paid_stock int not null default 0 comment '已支付库存',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_activity_id (activity_id),
  key idx_goods_id (goods_id)
) engine=InnoDB default charset=utf8mb4 comment='拼团库存表';

create table if not exists group_buy_stock_flow (
  id bigint unsigned not null auto_increment comment '自增主键',
  flow_id varchar(40) not null comment '库存流水编号',
  activity_id varchar(32) not null comment '活动编号',
  goods_id varchar(32) not null comment '商品编号',
  team_id varchar(40) not null comment '拼团队伍编号',
  order_id varchar(40) not null comment '交易订单编号',
  flow_type varchar(32) not null comment '流水类型',
  quantity int not null comment '变动数量',
  before_available_stock int not null comment '变动前可用库存',
  after_available_stock int not null comment '变动后可用库存',
  remark varchar(256) not null default '' comment '说明',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_flow_id (flow_id),
  unique key uk_order_flow (order_id, flow_type),
  key idx_activity_time (activity_id, create_time),
  key idx_team_id (team_id)
) engine=InnoDB default charset=utf8mb4 comment='拼团库存流水表';

insert into group_buy_stock (
  activity_id, goods_id, total_stock, available_stock, locked_stock, paid_stock
)
select activity_id, goods_id, 100, 100, 0, 0
from group_activity
on duplicate key update
  goods_id = values(goods_id);

create table if not exists guide_conversation_memory (
  id bigint unsigned not null auto_increment comment 'auto id',
  session_id varchar(64) not null comment 'session id',
  role varchar(32) not null comment 'USER or ASSISTANT',
  content varchar(4096) not null comment 'message content',
  image_url varchar(512) not null default '' comment 'image url',
  create_time datetime not null default current_timestamp comment 'create time',
  primary key (id),
  key idx_session_time (session_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='guide conversation long memory';

create table if not exists guide_decision_snapshot (
  id bigint unsigned not null auto_increment comment '自增主键',
  decision_id varchar(40) not null comment '价格快照编号',
  session_id varchar(64) not null default '' comment '会话编号',
  request_id varchar(64) not null default '' comment '请求编号',
  user_id varchar(64) not null default '' comment '用户编号',
  question varchar(1024) not null default '' comment '用户问题',
  goods_id varchar(32) not null comment '商品编号',
  goods_name varchar(128) not null default '' comment '商品名称',
  activity_id varchar(32) not null default '' comment '活动编号',
  origin_amount decimal(10, 2) not null comment '额度包原价',
  group_amount decimal(10, 2) not null comment '额度包拼团价',
  reference_ids varchar(256) not null default '' comment '引用知识片段',
  tool_names varchar(256) not null default '' comment '工具调用列表',
  quote_expire_time datetime not null comment '价格快照过期时间',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_decision_id (decision_id),
  key idx_user_time (user_id, create_time),
  key idx_quote_expire_time (quote_expire_time)
) engine=InnoDB default charset=utf8mb4 comment='额度包价格快照表';

create table if not exists guide_evaluation_report (
  id bigint unsigned not null auto_increment comment '自增主键',
  batch_no varchar(40) not null comment '评测批次编号',
  prompt_version varchar(64) not null comment '提示词版本',
  knowledge_version varchar(64) not null comment '知识版本',
  total_count int not null comment '用例总数',
  retrieval_hit_rate decimal(5, 2) not null comment '检索命中率',
  answer_accuracy_rate decimal(5, 2) not null comment '回答准确率',
  recommendation_reasonable_rate decimal(5, 2) not null comment '任务匹配率',
  context_consistency_rate decimal(5, 2) not null comment '多轮一致率',
  tool_call_accuracy_rate decimal(5, 2) not null default 0.00 comment '工具调用正确率',
  tool_argument_accuracy_rate decimal(5, 2) not null default 0.00 comment '工具参数正确率',
  tool_result_reference_rate decimal(5, 2) not null default 0.00 comment '工具结果引用率',
  average_latency_millis bigint not null default 0 comment '平均耗时',
  p99_latency_millis bigint not null default 0 comment 'P99 耗时',
  total_prompt_tokens bigint not null default 0 comment '提示词 token 数',
  total_completion_tokens bigint not null default 0 comment '回答 token 数',
  total_tokens bigint not null default 0 comment '总 token 数',
  estimated_cost_yuan decimal(12, 6) not null default 0.000000 comment '预估成本',
  baseline_batch_no varchar(40) default null comment '对比基线批次',
  retrieval_hit_rate_delta decimal(6, 2) not null default 0.00 comment '检索命中率变化',
  answer_accuracy_rate_delta decimal(6, 2) not null default 0.00 comment '回答准确率变化',
  recommendation_reasonable_rate_delta decimal(6, 2) not null default 0.00 comment '任务匹配率变化',
  context_consistency_rate_delta decimal(6, 2) not null default 0.00 comment '多轮一致率变化',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_batch_no (batch_no),
  key idx_version_time (knowledge_version, prompt_version, create_time)
) engine=InnoDB default charset=utf8mb4 comment='Agent 评测报告表';

create table if not exists guide_evaluation_item (
  id bigint unsigned not null auto_increment comment '自增主键',
  batch_no varchar(40) not null comment '评测批次编号',
  case_id varchar(64) not null comment '用例编号',
  case_name varchar(128) not null comment '用例名称',
  question varchar(1024) not null comment '用户问题',
  expected_goods_id varchar(32) not null default '' comment '期望商品',
  actual_goods_id varchar(32) not null default '' comment '实际商品',
  reference_passed tinyint not null default 0 comment '检索是否通过',
  answer_passed tinyint not null default 0 comment '回答是否通过',
  recommendation_passed tinyint not null default 0 comment '推荐是否通过',
  context_passed tinyint not null default 0 comment '上下文是否通过',
  actual_tool_names varchar(256) not null default '' comment '实际工具调用',
  tool_call_passed tinyint not null default 0 comment '工具调用是否通过',
  tool_argument_passed tinyint not null default 0 comment '工具参数是否通过',
  tool_result_reference_passed tinyint not null default 0 comment '工具结果引用是否通过',
  latency_millis bigint not null default 0 comment '总耗时',
  llm_latency_millis bigint not null default 0 comment '模型耗时',
  prompt_tokens bigint not null default 0 comment '提示词 token 数',
  completion_tokens bigint not null default 0 comment '回答 token 数',
  total_tokens bigint not null default 0 comment '总 token 数',
  estimated_cost_yuan decimal(12, 6) not null default 0.000000 comment '预估成本',
  fallback_used tinyint not null default 0 comment '是否使用兜底回答',
  score int not null default 0 comment '用例得分',
  suggestion varchar(512) not null default '' comment '优化建议',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_batch_case (batch_no, case_id),
  key idx_batch_score (batch_no, score)
) engine=InnoDB default charset=utf8mb4 comment='Agent 评测明细表';

create table if not exists guide_evaluation_feedback (
  id bigint unsigned not null auto_increment comment '自增主键',
  batch_no varchar(40) not null comment '评测批次编号',
  target_type varchar(32) not null comment '反馈对象',
  priority varchar(16) not null comment '优先级',
  content varchar(512) not null comment '反馈内容',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  key idx_batch_priority (batch_no, priority)
) engine=InnoDB default charset=utf8mb4 comment='Agent 评测反馈表';

set @sql = (
  select if(count(*) = 0,
    'alter table guide_evaluation_report add column tool_call_accuracy_rate decimal(5, 2) not null default 0.00 comment ''工具调用正确率'' after context_consistency_rate',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'guide_evaluation_report'
    and column_name = 'tool_call_accuracy_rate'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table guide_evaluation_report add column tool_argument_accuracy_rate decimal(5, 2) not null default 0.00 comment ''工具参数正确率'' after tool_call_accuracy_rate',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'guide_evaluation_report'
    and column_name = 'tool_argument_accuracy_rate'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table guide_evaluation_report add column tool_result_reference_rate decimal(5, 2) not null default 0.00 comment ''工具结果引用率'' after tool_argument_accuracy_rate',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'guide_evaluation_report'
    and column_name = 'tool_result_reference_rate'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table guide_evaluation_item add column actual_tool_names varchar(256) not null default '''' comment ''实际工具调用'' after context_passed',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'guide_evaluation_item'
    and column_name = 'actual_tool_names'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table guide_evaluation_item add column tool_call_passed tinyint not null default 0 comment ''工具调用是否通过'' after actual_tool_names',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'guide_evaluation_item'
    and column_name = 'tool_call_passed'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table guide_evaluation_item add column tool_argument_passed tinyint not null default 0 comment ''工具参数是否通过'' after tool_call_passed',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'guide_evaluation_item'
    and column_name = 'tool_argument_passed'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table guide_evaluation_item add column tool_result_reference_passed tinyint not null default 0 comment ''工具结果引用是否通过'' after tool_argument_passed',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'guide_evaluation_item'
    and column_name = 'tool_result_reference_passed'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- agent group dcc, crowd tags and notify task extension
create table if not exists dynamic_config (
  id bigint unsigned not null auto_increment comment 'auto id',
  config_key varchar(64) not null comment 'config key',
  config_value varchar(512) not null default '' comment 'config value',
  remark varchar(256) not null default '' comment 'remark',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_config_key (config_key)
) engine=InnoDB default charset=utf8mb4 comment='dynamic config';

create table if not exists crowd_tags (
  id bigint unsigned not null auto_increment comment 'auto id',
  tag_id varchar(32) not null comment 'tag id',
  tag_name varchar(128) not null comment 'tag name',
  tag_desc varchar(256) not null default '' comment 'tag desc',
  statistics int not null default 0 comment 'user count',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_tag_id (tag_id)
) engine=InnoDB default charset=utf8mb4 comment='crowd tags';

create table if not exists crowd_tags_detail (
  id bigint unsigned not null auto_increment comment 'auto id',
  tag_id varchar(32) not null comment 'tag id',
  user_id varchar(64) not null comment 'user id',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_tag_user (tag_id, user_id),
  key idx_user_id (user_id)
) engine=InnoDB default charset=utf8mb4 comment='crowd tag detail';

create table if not exists crowd_tags_job (
  id bigint unsigned not null auto_increment comment 'auto id',
  tag_id varchar(32) not null comment 'tag id',
  batch_id varchar(40) not null comment 'batch id',
  tag_type int not null default 0 comment '1 order count, 2 pay amount',
  tag_rule varchar(64) not null default '' comment 'tag rule',
  stat_start_time datetime default null comment 'stat start time',
  stat_end_time datetime default null comment 'stat end time',
  status int not null default 0 comment '0 init, 1 running, 3 done',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_tag_batch (tag_id, batch_id)
) engine=InnoDB default charset=utf8mb4 comment='crowd tag job';

create table if not exists notify_task (
  id bigint unsigned not null auto_increment comment 'auto id',
  activity_id varchar(32) not null comment 'activity id',
  team_id varchar(40) not null comment 'team id',
  notify_category varchar(64) not null comment 'notify category',
  notify_type varchar(16) not null comment 'HTTP or MQ',
  notify_mq varchar(128) default null comment 'mq routing key',
  notify_url varchar(512) default null comment 'http notify url',
  notify_count int not null default 0 comment 'notify count',
  notify_status int not null default 0 comment '0 init, 1 success, 2 retry, 3 error, 4 processing',
  parameter_json varchar(2048) not null comment 'notify payload',
  uuid varchar(128) not null comment 'unique key',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_uuid (uuid),
  key idx_team_status (team_id, notify_status),
  key idx_status_time (notify_status, update_time)
) engine=InnoDB default charset=utf8mb4 comment='notify task';

create table if not exists trade_event_outbox (
  id bigint unsigned not null auto_increment comment 'auto id',
  event_id varchar(128) not null comment 'event id',
  order_id varchar(40) not null comment 'order id',
  biz_type varchar(32) not null comment 'biz type',
  biz_id varchar(64) not null comment 'biz id',
  event_type varchar(32) not null comment 'event type',
  routing_key varchar(128) not null comment 'routing key',
  from_status varchar(32) default null comment 'from status',
  to_status varchar(32) not null comment 'to status',
  remark varchar(256) not null default '' comment 'remark',
  send_count int not null default 0 comment 'send count',
  send_status int not null default 0 comment '0 init, 1 success, 2 retry, 3 dead, 4 processing',
  last_error varchar(512) default null comment 'last error',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_event_id (event_id),
  key idx_status_time (send_status, update_time)
) engine=InnoDB default charset=utf8mb4 comment='trade event outbox';

create table if not exists trade_event_consume_record (
  id bigint unsigned not null auto_increment comment 'auto id',
  event_id varchar(128) not null comment 'event id',
  order_id varchar(40) not null comment 'order id',
  biz_type varchar(32) not null comment 'biz type',
  biz_id varchar(64) not null comment 'biz id',
  event_type varchar(32) not null comment 'event type',
  routing_key varchar(128) not null comment 'routing key',
  consume_count int not null default 0 comment 'consume count',
  consume_status int not null default 0 comment '0 init, 1 consumed, 2 retry, 3 dead, 4 processing',
  last_error varchar(512) default null comment 'last error',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_event_id (event_id),
  key idx_status_time (consume_status, update_time)
) engine=InnoDB default charset=utf8mb4 comment='trade event consume record';

insert into dynamic_config (
  config_key, config_value, remark
) values
('downgradeSwitch', '0', 'market downgrade switch'),
('cutRange', '100', 'market cut range percent'),
('scBlacklist', '', 'source channel blacklist, split by comma'),
('cacheSwitch', '0', 'cache switch'),
('groupSettlementNotifyType', 'HTTP', 'group settlement notify type'),
('groupSettlementNotifyUrl', '', 'group settlement notify url'),
('groupSettlementNotifyMQ', 'agent.group.notify.group-settlement', 'group settlement notify mq'),
('groupRefundNotifyType', 'HTTP', 'group refund notify type'),
('groupRefundNotifyUrl', '', 'group refund notify url'),
('groupRefundNotifyMQ', 'agent.group.notify.group-refund', 'group refund notify mq'),
('agentBillingPromptCostPer1k', '0.20', 'qwen3.7-plus prompt quota cost per 1k tokens'),
('agentBillingCompletionCostPer1k', '0.80', 'qwen3.7-plus completion quota cost per 1k tokens'),
('agentBillingCustomModelServiceRate', '0.10', 'custom model service fee rate')
on duplicate key update
  config_value = values(config_value),
  remark = values(remark);

-- group project marketing rule extension
set @sql = (
  select if(count(*) = 0,
    'alter table group_activity add column activity_name varchar(128) not null default '''' comment ''activity name'' after team_size',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_activity'
    and column_name = 'activity_name'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table group_activity add column discount_id varchar(32) default null comment ''discount id'' after activity_name',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_activity'
    and column_name = 'discount_id'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table group_activity add column group_type tinyint not null default 0 comment ''group type'' after discount_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_activity'
    and column_name = 'group_type'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table group_activity add column take_limit_count int not null default 1 comment ''user take limit'' after group_type',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_activity'
    and column_name = 'take_limit_count'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table group_activity add column target int not null default 1 comment ''target count'' after take_limit_count',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_activity'
    and column_name = 'target'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table group_activity add column valid_time int not null default 1440 comment ''team valid minutes'' after target',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_activity'
    and column_name = 'valid_time'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table group_activity add column status tinyint not null default 1 comment ''activity status'' after valid_time',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_activity'
    and column_name = 'status'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table group_activity add column tag_id varchar(32) default null comment ''crowd tag id'' after end_time',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_activity'
    and column_name = 'tag_id'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table group_activity add column tag_scope varchar(16) default null comment ''tag scope'' after tag_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_activity'
    and column_name = 'tag_scope'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists group_buy_discount (
  id bigint unsigned not null auto_increment comment 'auto id',
  discount_id varchar(32) not null comment 'discount id',
  discount_name varchar(64) not null comment 'discount name',
  discount_desc varchar(256) not null default '' comment 'discount desc',
  discount_type tinyint not null default 0 comment '0 base, 1 tag',
  market_plan varchar(8) not null comment 'ZJ/MJ/ZK/N',
  market_expr varchar(64) not null comment 'discount expression',
  tag_id varchar(32) default null comment 'tag id for tag discount',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_discount_id (discount_id)
) engine=InnoDB default charset=utf8mb4 comment='group buy discount';

create table if not exists sku (
  id bigint unsigned not null auto_increment comment 'auto id',
  source varchar(32) not null default '' comment 'source',
  channel varchar(32) not null default '' comment 'channel',
  goods_id varchar(32) not null comment 'goods id',
  goods_name varchar(128) not null comment 'goods name',
  original_price decimal(10, 2) not null comment 'original price',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_goods_id (goods_id)
) engine=InnoDB default charset=utf8mb4 comment='sku';

create table if not exists sc_sku_activity (
  id bigint unsigned not null auto_increment comment 'auto id',
  source varchar(32) not null comment 'source',
  channel varchar(32) not null comment 'channel',
  activity_id varchar(32) not null comment 'activity id',
  goods_id varchar(32) not null comment 'goods id',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_sc_goods (source, channel, goods_id),
  key idx_activity_id (activity_id)
) engine=InnoDB default charset=utf8mb4 comment='source channel sku activity';

-- 给老库补齐额度流水幂等唯一约束：同一用户、同一业务类型、同一业务单只允许一条流水，
-- 作为额度发放/回滚在数据库层的最后一道防线，防止并发回调写入重复流水。
set @sql = (
  select if(count(*) = 0,
    'alter table user_quota_flow add unique key uk_user_biz_flow (user_id, flow_type, biz_id)',
    'select 1')
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'user_quota_flow'
    and index_name = 'uk_user_biz_flow'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 折扣表补齐 enabled 列，支持运营端启停折扣。
set @sql = (
  select if(count(*) = 0,
    'alter table group_buy_discount add column enabled tinyint not null default 1 comment ''discount enabled'' after tag_id',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'group_buy_discount'
    and column_name = 'enabled'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
