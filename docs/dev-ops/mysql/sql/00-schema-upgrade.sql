use agent_group;

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

create table if not exists guide_evaluation_report (
  id bigint unsigned not null auto_increment comment '自增主键',
  batch_no varchar(40) not null comment '评测批次编号',
  prompt_version varchar(64) not null comment '提示词版本',
  knowledge_version varchar(64) not null comment '知识版本',
  total_count int not null comment '用例总数',
  retrieval_hit_rate decimal(5, 2) not null comment '检索命中率',
  answer_accuracy_rate decimal(5, 2) not null comment '回答准确率',
  recommendation_reasonable_rate decimal(5, 2) not null comment '推荐合理率',
  context_consistency_rate decimal(5, 2) not null comment '多轮一致率',
  average_latency_millis bigint not null default 0 comment '平均耗时',
  p99_latency_millis bigint not null default 0 comment 'P99 耗时',
  total_prompt_tokens bigint not null default 0 comment '提示词 token 数',
  total_completion_tokens bigint not null default 0 comment '回答 token 数',
  total_tokens bigint not null default 0 comment '总 token 数',
  estimated_cost_yuan decimal(12, 6) not null default 0.000000 comment '预估成本',
  baseline_batch_no varchar(40) default null comment '对比基线批次',
  retrieval_hit_rate_delta decimal(6, 2) not null default 0.00 comment '检索命中率变化',
  answer_accuracy_rate_delta decimal(6, 2) not null default 0.00 comment '回答准确率变化',
  recommendation_reasonable_rate_delta decimal(6, 2) not null default 0.00 comment '推荐合理率变化',
  context_consistency_rate_delta decimal(6, 2) not null default 0.00 comment '多轮一致率变化',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_batch_no (batch_no),
  key idx_version_time (knowledge_version, prompt_version, create_time)
) engine=InnoDB default charset=utf8mb4 comment='导购评测报告表';

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
) engine=InnoDB default charset=utf8mb4 comment='导购评测明细表';

create table if not exists guide_evaluation_feedback (
  id bigint unsigned not null auto_increment comment '自增主键',
  batch_no varchar(40) not null comment '评测批次编号',
  target_type varchar(32) not null comment '反馈对象',
  priority varchar(16) not null comment '优先级',
  content varchar(512) not null comment '反馈内容',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  key idx_batch_priority (batch_no, priority)
) engine=InnoDB default charset=utf8mb4 comment='导购评测反馈表';
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
  notify_status int not null default 0 comment '0 init, 1 success, 2 retry, 3 error',
  parameter_json varchar(2048) not null comment 'notify payload',
  uuid varchar(128) not null comment 'unique key',
  create_time datetime not null default current_timestamp comment 'create time',
  update_time datetime not null default current_timestamp on update current_timestamp comment 'update time',
  primary key (id),
  unique key uk_uuid (uuid),
  key idx_team_status (team_id, notify_status),
  key idx_status_time (notify_status, update_time)
) engine=InnoDB default charset=utf8mb4 comment='notify task';

insert into dynamic_config (
  config_key, config_value, remark
) values
('downgradeSwitch', '0', 'market downgrade switch'),
('cutRange', '100', 'market cut range percent'),
('scBlacklist', '', 'source channel blacklist, split by comma'),
('cacheSwitch', '0', 'cache switch'),
('groupSettlementNotifyType', 'HTTP', 'group settlement notify type'),
('groupSettlementNotifyUrl', '', 'group settlement notify url'),
('groupSettlementNotifyMQ', 'agent.group.notify.group-settlement', 'group settlement notify mq')
on duplicate key update
  config_value = values(config_value),
  remark = values(remark);
