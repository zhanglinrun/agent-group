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
  quota_amount decimal(10, 2) not null default 0.00 comment '额度数量',
  product_type varchar(32) not null default 'PHYSICAL' comment '商品类型',
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
) engine=InnoDB default charset=utf8mb4 comment='额度包表';

set @sql := if(
  (select count(*) from information_schema.columns
   where table_schema = database() and table_name = 'guide_goods' and column_name = 'quota_amount') = 0,
  'alter table guide_goods add column quota_amount decimal(10, 2) not null default 0.00 comment ''额度数量'' after origin_price',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql := if(
  (select count(*) from information_schema.columns
   where table_schema = database() and table_name = 'guide_goods' and column_name = 'product_type') = 0,
  'alter table guide_goods add column product_type varchar(32) not null default ''PHYSICAL'' comment ''商品类型'' after quota_amount',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

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
  content text not null comment '片段内容',
  rank_no int not null default 100 comment '命中排序',
  parent_fragment_id varchar(32) default null comment '父片段编号',
  brother_group_id varchar(64) not null default '' comment '兄弟片段组',
  brother_index int not null default 1 comment '兄弟片段序号',
  brother_total int not null default 1 comment '兄弟片段总数',
  chunk_type varchar(16) not null default 'CHILD' comment '片段类型',
  embedding_enabled tinyint not null default 1 comment '是否写入向量',
  fragment_status varchar(32) not null default 'ENABLED' comment '片段状态',
  enabled tinyint not null default 1 comment '是否启用',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_fragment_id (fragment_id),
  key idx_goods_rank (goods_id, rank_no),
  key idx_parent_fragment (parent_fragment_id),
  key idx_brother_group (brother_group_id, brother_index)
) engine=InnoDB default charset=utf8mb4 comment='知识片段表';

create table if not exists trade_order (
  id bigint unsigned not null auto_increment comment '自增主键',
  order_id varchar(40) not null comment '订单编号',
  idempotent_key varchar(128) default null comment '幂等键',
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
  unique key uk_idempotent_key (idempotent_key),
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

create table if not exists user_account (
  id bigint unsigned not null auto_increment comment '自增主键',
  user_id varchar(64) not null comment '用户编号',
  username varchar(64) not null comment '登录账号',
  password_hash varchar(128) not null comment '密码摘要',
  password_salt varchar(128) not null comment '密码盐',
  nickname varchar(64) not null default '' comment '昵称',
  email varchar(128) not null default '' comment '邮箱',
  role varchar(32) not null default 'USER' comment '角色',
  status varchar(32) not null default 'ENABLED' comment '状态',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_user_id (user_id),
  unique key uk_username (username)
) engine=InnoDB default charset=utf8mb4 comment='用户账号表';

create table if not exists user_login_session (
  token varchar(128) not null comment '登录令牌',
  user_id varchar(64) not null comment '用户编号',
  expire_time datetime not null comment '过期时间',
  status varchar(32) not null default 'ACTIVE' comment '状态',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (token),
  key idx_user_status (user_id, status)
) engine=InnoDB default charset=utf8mb4 comment='用户登录会话表';

create table if not exists user_quota_account (
  user_id varchar(64) not null comment '用户编号',
  quota_balance decimal(12, 2) not null default 0.00 comment '可用额度',
  frozen_quota decimal(12, 2) not null default 0.00 comment '冻结额度',
  used_quota decimal(12, 2) not null default 0.00 comment '已用额度',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (user_id)
) engine=InnoDB default charset=utf8mb4 comment='用户额度账户表';

create table if not exists user_quota_flow (
  id bigint unsigned not null auto_increment comment '自增主键',
  flow_id varchar(40) not null comment '流水编号',
  user_id varchar(64) not null comment '用户编号',
  flow_type varchar(32) not null comment '流水类型',
  biz_id varchar(64) not null default '' comment '业务编号',
  quota_amount decimal(12, 2) not null default 0.00 comment '额度变动',
  before_balance decimal(12, 2) not null default 0.00 comment '变动前余额',
  after_balance decimal(12, 2) not null default 0.00 comment '变动后余额',
  remark varchar(256) not null default '' comment '说明',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_flow_id (flow_id),
  unique key uk_user_biz_flow (user_id, flow_type, biz_id),
  key idx_user_time (user_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='用户额度流水表';

create table if not exists model_usage_record (
  id bigint unsigned not null auto_increment comment '自增主键',
  usage_id varchar(40) not null comment '用量编号',
  user_id varchar(64) not null comment '用户编号',
  session_id varchar(64) not null default '' comment '会话编号',
  task_type varchar(32) not null default '' comment '任务类型',
  model varchar(64) not null default '' comment '模型',
  prompt_tokens bigint not null default 0 comment '提示词 token',
  completion_tokens bigint not null default 0 comment '回答 token',
  total_tokens bigint not null default 0 comment '总 token',
  quota_cost decimal(12, 2) not null default 0.00 comment '消耗额度',
  latency_millis bigint not null default 0 comment '耗时',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_usage_id (usage_id),
  key idx_user_time (user_id, create_time),
  key idx_session_time (session_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='模型用量记录表';

create table if not exists academic_agent_session (
  id bigint unsigned not null auto_increment comment '自增主键',
  session_id varchar(64) not null comment '会话编号',
  user_id varchar(64) not null comment '用户编号',
  title varchar(128) not null default '' comment '标题',
  task_type varchar(32) not null default '' comment '任务类型',
  last_message varchar(256) not null default '' comment '最后消息',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_session_id (session_id),
  key idx_user_time (user_id, update_time)
) engine=InnoDB default charset=utf8mb4 comment='学术智能体会话表';

create table if not exists academic_agent_message (
  id bigint unsigned not null auto_increment comment '自增主键',
  message_id varchar(40) not null comment '消息编号',
  session_id varchar(64) not null comment '会话编号',
  user_id varchar(64) not null comment '用户编号',
  role varchar(32) not null comment '角色',
  content mediumtext not null comment '消息内容',
  image_url varchar(2048) not null default '' comment '图片地址',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_message_id (message_id),
  key idx_user_session_time (user_id, session_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='学术智能体消息表';

create table if not exists academic_agent_file (
  id bigint unsigned not null auto_increment comment '自增主键',
  file_id varchar(40) not null comment '文件编号',
  user_id varchar(64) not null comment '用户编号',
  session_id varchar(64) not null default '' comment '会话编号',
  file_name varchar(160) not null comment '文件名',
  file_type varchar(32) not null default '' comment '文件类型',
  file_size bigint not null default 0 comment '文件大小',
  object_url varchar(512) not null default '' comment '对象存储地址',
  content mediumtext not null comment '解析文本',
  summary varchar(1024) not null default '' comment '摘要',
  status varchar(32) not null default 'PARSED' comment '状态',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_file_id (file_id),
  key idx_user_time (user_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='学术智能体文件表';

create table if not exists academic_agent_artifact (
  id bigint unsigned not null auto_increment comment '自增主键',
  artifact_id varchar(40) not null comment '产物编号',
  session_id varchar(64) not null comment '会话编号',
  user_id varchar(64) not null comment '用户编号',
  artifact_type varchar(32) not null comment '产物类型',
  title varchar(128) not null default '' comment '标题',
  content mediumtext not null comment '产物内容',
  download_url varchar(512) not null default '' comment '下载地址',
  create_time datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  unique key uk_artifact_id (artifact_id),
  key idx_user_session_time (user_id, session_id, create_time)
) engine=InnoDB default charset=utf8mb4 comment='学术智能体产物表';

create table if not exists ai_file_info (
  id bigint not null auto_increment comment '主键ID',
  file_id varchar(255) not null comment '文件唯一标识',
  file_name varchar(500) not null comment '原始文件名',
  file_type varchar(50) default null comment '文件类型',
  file_size bigint default null comment '文件大小',
  minio_path varchar(1000) default null comment '对象存储路径',
  extracted_text longtext comment '解析后的纯文本内容',
  created_at datetime default current_timestamp comment '创建时间',
  conversation_id varchar(255) default null comment '会话ID',
  status varchar(50) default 'PENDING' comment '文件状态',
  update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
  embed tinyint default 0 comment '是否向量化',
  primary key (id),
  unique key uk_file_id (file_id),
  key idx_conversation_id (conversation_id)
) engine=InnoDB default charset=utf8mb4 comment='Dodo 文件元数据表';

create table if not exists ai_session (
  id bigint not null auto_increment comment '主键ID',
  session_id varchar(255) not null comment '会话ID',
  question longtext comment '用户问题',
  answer longtext comment 'AI回复',
  tools varchar(1024) default null comment '工具名称',
  first_response_time bigint default null comment '首次响应时间',
  total_response_time bigint default null comment '整体回复时间',
  create_time datetime default current_timestamp comment '创建时间',
  update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
  reference longtext comment '参考链接',
  agent_type varchar(255) default null comment '智能体类型',
  thinking longtext comment '思考过程',
  fileid varchar(255) default null comment '文件ID',
  recommend varchar(1000) default null comment '推荐问题',
  primary key (id),
  key idx_session_id (session_id),
  key idx_create_time (create_time)
) engine=InnoDB default charset=utf8mb4 comment='Dodo 智能体会话表';

create table if not exists ai_ppt_inst (
  id bigint not null auto_increment comment '实例ID',
  conversation_id varchar(64) default null comment '会话ID',
  template_code varchar(50) default null comment '模板编码',
  status varchar(32) default 'INIT' comment '状态',
  query text comment '用户原始需求',
  requirement longtext comment '需求澄清',
  search_info longtext comment '搜索信息',
  outline longtext comment '大纲',
  ppt_schema longtext comment 'PPT 规划 JSON',
  file_url varchar(1000) default null comment '生成文件URL',
  error_msg text comment '失败原因',
  create_time datetime default current_timestamp,
  update_time datetime default current_timestamp on update current_timestamp,
  primary key (id),
  key idx_conversation_id (conversation_id),
  key idx_status (status),
  key idx_template_code (template_code)
) engine=InnoDB default charset=utf8mb4 comment='Dodo PPT 生成实例表';

create table if not exists ai_ppt_template (
  id bigint not null auto_increment comment '模板ID',
  template_code varchar(50) not null comment '模板唯一编码',
  template_name varchar(100) not null comment '模板名称',
  template_desc text comment '模板说明',
  template_schema longtext not null comment '模板结构 JSON',
  file_path varchar(500) not null comment 'PPT 模板文件路径',
  style_tags varchar(200) default null comment '风格标签',
  slide_count int default null comment '模板页数',
  create_time datetime default current_timestamp,
  primary key (id),
  unique key uk_template_code (template_code),
  key idx_template_code (template_code)
) engine=InnoDB default charset=utf8mb4 comment='Dodo PPT 模板表';

insert into ai_ppt_template (
  template_code, template_name, template_desc, template_schema, file_path, style_tags, slide_count
) values (
  'ai',
  'AI科技风PPT',
  '适用于AI、人工智能、科技风等场景的PPT',
  '{"slides":[{"pageType":"COVER","pageDesc":"封面页","pageIndex":1},{"pageType":"CATALOG","pageDesc":"目录页","pageIndex":2},{"pageType":"COMPARE","pageDesc":"内容页，用于两者对比","pageIndex":3},{"pageType":"CONTENT","pageDesc":"内容页","pageIndex":4},{"pageType":"END","pageDesc":"结束页","pageIndex":5}]}',
  'classpath:dodo/templates/ai.pptx',
  '科技、AI、人工智能',
  5
) on duplicate key update
  template_name = values(template_name),
  template_desc = values(template_desc),
  template_schema = values(template_schema),
  file_path = values(file_path),
  style_tags = values(style_tags),
  slide_count = values(slide_count);

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
('groupRefundNotifyMQ', 'agent.group.notify.group-refund', 'group refund notify mq')
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
  goods_id, goods_name, image_url, origin_price, quota_amount, product_type, spec_summary, after_sale_policy,
  recommend_reason, not_suitable_for, enabled, sort_order
) values
('G10001', '基础额度包', '', 19.90, 40.00, 'QUOTA_PACKAGE', '适合普通对话、论文摘要和轻量问答', '虚拟额度到账后不支持无理由退款；未使用额度退款时会回滚到账额度', '适合刚开始体验学术助手的用户', '需要批量生成 PPT 或长时间深度研究的用户', 1, 10),
('G10002', '论文阅读额度包', '', 49.90, 120.00, 'QUOTA_PACKAGE', '适合上传论文、生成精读笔记和实验复现清单', '虚拟额度到账后不支持无理由退款；未使用额度退款时会回滚到账额度', '适合研究生读论文、整理相关工作和复现实验', '只进行普通短对话的用户', 1, 20),
('G10003', 'PPT 创作额度包', '', 69.90, 180.00, 'QUOTA_PACKAGE', '适合生成组会汇报、开题答辩和论文分享 PPT', '虚拟额度到账后不支持无理由退款；未使用额度退款时会回滚到账额度', '适合需要多次生成和修改学术演示稿的用户', '只需要偶尔问答的用户', 1, 30),
('G10004', '图表重建额度包', '', 39.90, 90.00, 'QUOTA_PACKAGE', '适合图片、流程图和架构图转可编辑草稿', '虚拟额度到账后不支持无理由退款；未使用额度退款时会回滚到账额度', '适合论文图、实验流程和系统架构图的重建编辑', '要求严格 1:1 商业级复刻的用户', 1, 40),
('G10005', '深度研究额度包', '', 99.90, 260.00, 'QUOTA_PACKAGE', '适合复杂主题拆解、多轮调研和报告生成', '虚拟额度到账后不支持无理由退款；未使用额度退款时会回滚到账额度', '适合秋招项目调研、论文选题和技术路线规划', '只需要单轮短问答的用户', 1, 50),
('G10006', '团队拼团额度包', '', 129.90, 360.00, 'QUOTA_PACKAGE', '适合实验室小组共享演示，额度更多且拼团优惠更明显', '虚拟额度到账后不支持无理由退款；未使用额度退款时会回滚到账额度', '适合组会、课题组内部演示和多人拼团充值', '个人轻量体验用户', 1, 60)
on duplicate key update
  goods_name = values(goods_name),
  image_url = values(image_url),
  origin_price = values(origin_price),
  quota_amount = values(quota_amount),
  product_type = values(product_type),
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
('A10001', 'G10001', 16.90, 3, '基础额度包拼团', 'D10001', 0, 2, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), null, null, 1),
('A10002', 'G10002', 42.90, 5, '论文阅读额度包拼团', 'D10002', 0, 1, 5, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), 'TAG_PAY_2000', '2', 1),
('A10003', 'G10003', 59.90, 3, 'PPT 创作额度包拼团', 'D10002', 0, 1, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 5 day), null, null, 1),
('A10004', 'G10004', 33.90, 4, '图表重建额度包拼团', 'D10002', 0, 1, 4, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 5 day), null, null, 1),
('A10005', 'G10005', 84.90, 2, '深度研究额度包拼团', 'D10001', 0, 2, 2, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 10 day), null, null, 1),
('A10006', 'G10006', 109.90, 3, '团队拼团额度包拼团', 'D10001', 0, 2, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), null, null, 1)
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
('D10001', '额度包直减', '基础额度包拼团直减 3 元', 0, 'ZJ', '3', null),
('D10002', '额度包满减', '高阶额度包拼团优惠', 0, 'MJ', '30,7', null),
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
('s01', 'c01', 'G10001', '基础额度包', 19.90),
('s01', 'c01', 'G10002', '论文阅读额度包', 49.90),
('s01', 'c01', 'G10003', 'PPT 创作额度包', 69.90),
('s01', 'c01', 'G10004', '图表重建额度包', 39.90),
('s01', 'c01', 'G10005', '深度研究额度包', 99.90),
('s01', 'c01', 'G10006', '团队拼团额度包', 129.90)
on duplicate key update
  source = values(source),
  channel = values(channel),
  goods_name = values(goods_name),
  original_price = values(original_price);

insert into sc_sku_activity (
  source, channel, activity_id, goods_id
) values
('s01', 'c01', 'A10001', 'G10001'),
('s01', 'c01', 'A10002', 'G10002'),
('s01', 'c01', 'A10003', 'G10003'),
('s01', 'c01', 'A10004', 'G10004'),
('s01', 'c01', 'A10005', 'G10005'),
('s01', 'c01', 'A10006', 'G10006')
on duplicate key update
  activity_id = values(activity_id);

insert into group_buy_stock (
  activity_id, goods_id, total_stock, available_stock, locked_stock, paid_stock
) values
('A10001', 'G10001', 100, 100, 0, 0),
('A10002', 'G10002', 100, 100, 0, 0),
('A10003', 'G10003', 80, 80, 0, 0),
('A10004', 'G10004', 60, 60, 0, 0),
('A10005', 'G10005', 120, 120, 0, 0),
('A10006', 'G10006', 100, 100, 0, 0)
on duplicate key update
  goods_id = values(goods_id),
  total_stock = values(total_stock),
  available_stock = greatest(values(total_stock) - locked_stock - paid_stock, 0);

insert into knowledge_document (
  document_id, document_name, document_type, knowledge_version, source_type, source_name, document_status, enabled
) values
('DOC10001', '学术额度包说明', '额度包资料', 'v1', 'INIT_DATA', '初始化数据', 'ENABLED', 1),
('DOC10002', '学术额度包拼团规则', '拼团规则', 'v1', 'INIT_DATA', '初始化数据', 'ENABLED', 1),
('DOC10003', '学术额度包退款规则', '退款规则', 'v1', 'INIT_DATA', '初始化数据', 'ENABLED', 1),
('DOC10005', '学术 Agent 任务说明', 'Agent 任务规则', 'v1', 'INIT_DATA', '初始化数据', 'ENABLED', 1)
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
('KF10001', 'DOC10001', 'G10001', '额度包资料', 'v1', '基础额度包包含 40 点额度，适合普通学术问答、论文摘要和轻量资料整理。', 1, 'ENABLED', 1),
('KF10002', 'DOC10002', 'G10001', '拼团规则', 'v1', '基础额度包支持 3 人拼团，拼团价 16.90 元，直接购买价 19.90 元，支付成功后自动发放额度。', 2, 'ENABLED', 1),
('KF10003', 'DOC10003', 'G10001', '退款规则', 'v1', '额度属于虚拟商品，到账后不支持无理由退款；未使用额度退款时系统会回滚已发放额度。', 3, 'ENABLED', 1),
('KF10011', 'DOC10005', 'G10003', 'Agent 任务规则', 'v1', 'PPT 创作额度包适合组会汇报、开题答辩和论文分享，支持生成演示稿结构、页面大纲和讲稿草稿。', 11, 'ENABLED', 1),
('KF10012', 'DOC10005', 'G10004', 'Agent 任务规则', 'v1', '图表重建额度包适合把论文图、流程图和架构图转换成可编辑 Mermaid 或结构化草稿。', 12, 'ENABLED', 1),
('KF10013', 'DOC10005', 'G10005', 'Agent 任务规则', 'v1', '深度研究额度包适合复杂主题拆解、技术路线规划、相关工作整理和长报告生成。', 13, 'ENABLED', 1),
('KF10014', 'DOC10005', 'G10006', 'Agent 任务规则', 'v1', '团队拼团额度包适合实验室小组共享演示，拼团价 109.90 元，可获得 360 点额度。', 14, 'ENABLED', 1),
('KF10015', 'DOC10005', 'G10001', '交易规则', 'v1', '购买入口由后端交易系统创建订单，订单金额、支付单金额和额度发放数量都以后端交易系统为准。', 15, 'ENABLED', 1)
on duplicate key update
  document_id = values(document_id),
  goods_id = values(goods_id),
  document_type = values(document_type),
  knowledge_version = values(knowledge_version),
  content = values(content),
  rank_no = values(rank_no),
  fragment_status = values(fragment_status),
  enabled = values(enabled);
