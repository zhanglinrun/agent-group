create database if not exists agent_group
  default character set utf8mb4
  default collate utf8mb4_general_ci;

use agent_group;

create table if not exists guide_goods (
  id bigint unsigned not null auto_increment comment '自增主键',
  goods_id varchar(32) not null comment '商品编号',
  goods_name varchar(128) not null comment '商品名称',
  image_url varchar(512) not null default '' comment '商品图片',
  origin_price decimal(10, 2) not null comment '原价',
  spec_summary varchar(512) not null comment '规格摘要',
  after_sale_policy varchar(512) not null comment '售后政策',
  recommend_reason varchar(512) not null comment '推荐理由',
  not_suitable_for varchar(512) not null comment '不适合人群',
  enabled tinyint not null default 1 comment '是否启用',
  sort_order int not null default 100 comment '排序',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_goods_id (goods_id)
) engine=InnoDB default charset=utf8mb4 comment='导购商品表';

create table if not exists group_activity (
  id bigint unsigned not null auto_increment comment '自增主键',
  activity_id varchar(32) not null comment '活动编号',
  goods_id varchar(32) not null comment '商品编号',
  group_price decimal(10, 2) not null comment '拼团价',
  team_size int not null comment '成团人数',
  activity_name varchar(128) not null default '' comment 'activity name',
  discount_id varchar(32) default null comment 'discount id',
  group_type tinyint not null default 0 comment 'group type',
  take_limit_count int not null default 1 comment 'user take limit',
  target int not null default 1 comment 'target count',
  valid_time int not null default 1440 comment 'team valid minutes',
  status tinyint not null default 1 comment 'activity status',
  start_time datetime not null comment '开始时间',
  end_time datetime not null comment '结束时间',
  tag_id varchar(32) default null comment 'crowd tag id',
  tag_scope varchar(16) default null comment 'tag scope, 1 visible, 2 enable',
  enabled tinyint not null default 1 comment '是否启用',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_activity_id (activity_id),
  key idx_goods_id (goods_id)
) engine=InnoDB default charset=utf8mb4 comment='拼团活动表';

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

create table if not exists group_buy_team (
  id bigint unsigned not null auto_increment comment '自增主键',
  team_id varchar(40) not null comment '拼团队伍编号',
  activity_id varchar(32) not null comment '活动编号',
  goods_id varchar(32) not null comment '商品编号',
  target_count int not null comment '目标人数',
  complete_count int not null default 0 comment '支付完成人数',
  lock_count int not null default 0 comment '锁单人数',
  team_status varchar(32) not null comment '队伍状态',
  valid_start_time datetime not null comment '队伍开始时间',
  valid_end_time datetime default null comment '队伍结束时间',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_team_id (team_id),
  key idx_activity_status (activity_id, team_status)
) engine=InnoDB default charset=utf8mb4 comment='拼团队伍表';

create table if not exists group_buy_order_lock (
  id bigint unsigned not null auto_increment comment '自增主键',
  lock_id varchar(40) not null comment '锁单编号',
  idempotent_key varchar(128) not null comment '幂等键',
  user_id varchar(64) not null comment '用户编号',
  team_id varchar(40) not null comment '拼团队伍编号',
  order_id varchar(40) not null comment '交易订单编号',
  activity_id varchar(32) not null comment '活动编号',
  goods_id varchar(32) not null comment '商品编号',
  lock_amount decimal(10, 2) not null comment '锁单金额',
  lock_status varchar(32) not null comment '锁单状态',
  lock_time datetime not null comment '锁单时间',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_lock_id (lock_id),
  unique key uk_idempotent_key (idempotent_key),
  unique key uk_order_id (order_id),
  key idx_team_status (team_id, lock_status),
  key idx_user_activity (user_id, activity_id)
) engine=InnoDB default charset=utf8mb4 comment='拼团锁单表';

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

create table if not exists knowledge_document (
  id bigint unsigned not null auto_increment comment '自增主键',
  document_id varchar(32) not null comment '文档编号',
  document_name varchar(128) not null comment '文档名称',
  document_type varchar(32) not null comment '文档类型',
  knowledge_version varchar(32) not null comment '知识版本',
  source_type varchar(32) not null default 'INIT_DATA' comment '来源类型',
  source_name varchar(128) not null default '初始化数据' comment '来源名称',
  document_status varchar(32) not null default 'ENABLED' comment '文档状态',
  enabled tinyint not null default 1 comment '是否启用',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_document_id (document_id)
) engine=InnoDB default charset=utf8mb4 comment='知识文档表';

create table if not exists knowledge_fragment (
  id bigint unsigned not null auto_increment comment '自增主键',
  fragment_id varchar(32) not null comment '片段编号',
  document_id varchar(32) not null comment '文档编号',
  goods_id varchar(32) not null comment '商品编号',
  document_type varchar(32) not null comment '文档类型',
  knowledge_version varchar(32) not null comment '知识版本',
  content varchar(1024) not null comment '片段内容',
  rank_no int not null default 100 comment '命中排序',
  fragment_status varchar(32) not null default 'ENABLED' comment '片段状态',
  enabled tinyint not null default 1 comment '是否启用',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_fragment_id (fragment_id),
  key idx_goods_rank (goods_id, rank_no)
) engine=InnoDB default charset=utf8mb4 comment='知识片段表';

create table if not exists trade_order (
  id bigint unsigned not null auto_increment comment '自增主键',
  order_id varchar(40) not null comment '订单编号',
  user_id varchar(64) not null comment '用户编号',
  goods_id varchar(32) not null comment '商品编号',
  goods_name varchar(128) not null comment '商品名称',
  activity_id varchar(32) default null comment '活动编号',
  buy_type varchar(32) not null comment '购买类型',
  origin_amount decimal(10, 2) not null comment '订单原价',
  pay_amount decimal(10, 2) not null comment '支付金额',
  order_status varchar(32) not null comment '订单状态',
  pay_time datetime default null comment '支付时间',
  close_time datetime default null comment '关闭时间',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_order_id (order_id),
  key idx_user_status (user_id, order_status),
  key idx_goods_id (goods_id)
) engine=InnoDB default charset=utf8mb4 comment='交易订单表';

create table if not exists pay_order (
  id bigint unsigned not null auto_increment comment '自增主键',
  pay_order_id varchar(40) not null comment '支付单编号',
  order_id varchar(40) not null comment '订单编号',
  pay_channel varchar(32) not null comment '支付渠道',
  pay_amount decimal(10, 2) not null comment '支付金额',
  pay_status varchar(32) not null comment '支付状态',
  pay_url varchar(512) not null default '' comment '支付链接',
  out_trade_no varchar(64) default null comment '外部交易单号',
  pay_time datetime default null comment '支付时间',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_pay_order_id (pay_order_id),
  unique key uk_order_id (order_id),
  key idx_pay_status (pay_status)
) engine=InnoDB default charset=utf8mb4 comment='支付单表';

create table if not exists refund_order (
  id bigint unsigned not null auto_increment comment '自增主键',
  refund_id varchar(40) not null comment '退款单编号',
  order_id varchar(40) not null comment '订单编号',
  pay_order_id varchar(40) not null comment '支付单编号',
  user_id varchar(64) not null comment '用户编号',
  refund_amount decimal(10, 2) not null comment '退款金额',
  refund_status varchar(32) not null comment '退款状态',
  refund_reason varchar(256) not null default '' comment '退款原因',
  refund_time datetime not null comment '退款时间',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_refund_id (refund_id),
  unique key uk_order_id (order_id),
  key idx_user_status (user_id, refund_status)
) engine=InnoDB default charset=utf8mb4 comment='退款单表';

create table if not exists trade_status_flow (
  id bigint unsigned not null auto_increment comment '自增主键',
  flow_id varchar(40) not null comment '流水编号',
  order_id varchar(40) not null comment '订单编号',
  biz_type varchar(32) not null comment '业务类型',
  biz_id varchar(64) not null comment '业务编号',
  event_type varchar(32) not null comment '事件类型',
  from_status varchar(32) default null comment '变更前状态',
  to_status varchar(32) not null comment '变更后状态',
  remark varchar(256) not null default '' comment '说明',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_flow_id (flow_id),
  key idx_order_time (order_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='交易状态流水表';

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

insert into crowd_tags (
  tag_id, tag_name, tag_desc, statistics
) values
('TAG_ORDER_2', '复购用户', '统计期内支付订单数达到 2 单的用户', 0),
('TAG_PAY_2000', '高价值用户', '统计期内支付金额达到 2000 元的用户', 0)
on duplicate key update
  tag_name = values(tag_name),
  tag_desc = values(tag_desc);

insert into crowd_tags_job (
  tag_id, batch_id, tag_type, tag_rule, stat_start_time, stat_end_time, status
) values
('TAG_ORDER_2', 'BATCH_DEMO_001', 1, '2', date_sub(now(), interval 30 day), now(), 0),
('TAG_PAY_2000', 'BATCH_DEMO_001', 2, '2000', date_sub(now(), interval 30 day), now(), 0)
on duplicate key update
  tag_type = values(tag_type),
  tag_rule = values(tag_rule),
  stat_start_time = values(stat_start_time),
  stat_end_time = values(stat_end_time),
  status = values(status);

insert into guide_goods (
  goods_id, goods_name, image_url, origin_price, spec_summary, after_sale_policy,
  recommend_reason, not_suitable_for, enabled, sort_order
) values
('G10001', '轻薄学习平板标准版', '', 2399.00, '10.9 英寸屏幕，128GB 存储，支持手写笔', '7 天无理由退货，1 年质保', '预算有限、学习和网课场景下性价比更高', '长期剪视频或运行大型应用的用户', 1, 10),
('G10002', '高配创作平板', '', 3299.00, '12.1 英寸高刷屏，256GB 存储，适合多任务', '7 天无理由退货，1 年质保', '适合剪视频、绘图和大型应用，但预算压力更大', '只做笔记和看网课且预算有限的用户', 1, 20)
on duplicate key update
  goods_name = values(goods_name),
  image_url = values(image_url),
  origin_price = values(origin_price),
  spec_summary = values(spec_summary),
  after_sale_policy = values(after_sale_policy),
  recommend_reason = values(recommend_reason),
  not_suitable_for = values(not_suitable_for),
  enabled = values(enabled),
  sort_order = values(sort_order);

insert into group_activity (
  activity_id, goods_id, group_price, team_size, activity_name, discount_id,
  group_type, take_limit_count, target, valid_time, status, start_time, end_time,
  tag_id, tag_scope, enabled
) values
('A10001', 'G10001', 2099.00, 3, '学习平板标准版拼团', 'D10001', 0, 2, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), null, null, 1),
('A10002', 'G10002', 2899.00, 5, '高配创作平板拼团', 'D10002', 0, 1, 5, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), 'TAG_PAY_2000', '2', 1)
on duplicate key update
  goods_id = values(goods_id),
  group_price = values(group_price),
  team_size = values(team_size),
  activity_name = values(activity_name),
  discount_id = values(discount_id),
  group_type = values(group_type),
  take_limit_count = values(take_limit_count),
  target = values(target),
  valid_time = values(valid_time),
  status = values(status),
  start_time = values(start_time),
  end_time = values(end_time),
  tag_id = values(tag_id),
  tag_scope = values(tag_scope),
  enabled = values(enabled);

insert into group_buy_discount (
  discount_id, discount_name, discount_desc, discount_type, market_plan, market_expr, tag_id
) values
('D10001', '直减 300', '标准版拼团直减 300 元', 0, 'ZJ', '300', null),
('D10002', '满 3000 减 400', '高配版满减优惠', 0, 'MJ', '3000,400', null),
('D10003', '八折优惠', '折扣算法示例', 0, 'ZK', '0.8', null),
('D10004', 'N 元购', '固定金额算法示例', 0, 'N', '1.99', null)
on duplicate key update
  discount_name = values(discount_name),
  discount_desc = values(discount_desc),
  discount_type = values(discount_type),
  market_plan = values(market_plan),
  market_expr = values(market_expr),
  tag_id = values(tag_id);

insert into sku (
  source, channel, goods_id, goods_name, original_price
) values
('s01', 'c01', 'G10001', '学习平板标准版', 2399.00),
('s01', 'c01', 'G10002', '高配创作平板', 3299.00)
on duplicate key update
  source = values(source),
  channel = values(channel),
  goods_name = values(goods_name),
  original_price = values(original_price);

insert into sc_sku_activity (
  source, channel, activity_id, goods_id
) values
('s01', 'c01', 'A10001', 'G10001'),
('s01', 'c01', 'A10002', 'G10002')
on duplicate key update
  activity_id = values(activity_id);

insert into group_buy_stock (
  activity_id, goods_id, total_stock, available_stock, locked_stock, paid_stock
) values
('A10001', 'G10001', 100, 100, 0, 0),
('A10002', 'G10002', 100, 100, 0, 0)
on duplicate key update
  goods_id = values(goods_id),
  total_stock = values(total_stock),
  available_stock = greatest(values(total_stock) - locked_stock - paid_stock, 0);

insert into knowledge_document (
  document_id, document_name, document_type, knowledge_version, source_type, source_name, document_status, enabled
) values
('DOC10001', '学习平板商品详情说明', '商品详情', 'v1', 'INIT_DATA', '初始化数据', 'ENABLED', 1),
('DOC10002', '学习平板拼团活动规则', '营销规则', 'v1', 'INIT_DATA', '初始化数据', 'ENABLED', 1),
('DOC10003', '学习平板售后政策', '售后政策', 'v1', 'INIT_DATA', '初始化数据', 'ENABLED', 1)
on duplicate key update
  document_name = values(document_name),
  document_type = values(document_type),
  knowledge_version = values(knowledge_version),
  source_type = values(source_type),
  source_name = values(source_name),
  document_status = values(document_status),
  enabled = values(enabled);

insert into knowledge_fragment (
  fragment_id, document_id, goods_id, document_type, knowledge_version, content, rank_no, fragment_status, enabled
) values
('KF10001', 'DOC10001', 'G10001', '商品详情', 'v1', '轻薄学习平板标准版适合写论文、看网课和日常笔记。', 1, 'ENABLED', 1),
('KF10002', 'DOC10002', 'G10001', '营销规则', 'v1', '标准版支持 3 人拼团，拼团价比原价低 300 元。', 2, 'ENABLED', 1),
('KF10003', 'DOC10003', 'G10001', '售后政策', 'v1', '拼团商品成团后支持 7 天无理由退货，未成团时系统自动退款。', 3, 'ENABLED', 1)
on duplicate key update
  document_id = values(document_id),
  goods_id = values(goods_id),
  document_type = values(document_type),
  knowledge_version = values(knowledge_version),
  content = values(content),
  rank_no = values(rank_no),
  fragment_status = values(fragment_status),
  enabled = values(enabled);
