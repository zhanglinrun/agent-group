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
  start_time datetime not null comment '开始时间',
  end_time datetime not null comment '结束时间',
  enabled tinyint not null default 1 comment '是否启用',
  create_time datetime not null default current_timestamp comment '创建时间',
  update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  primary key (id),
  unique key uk_activity_id (activity_id),
  key idx_goods_id (goods_id)
) engine=InnoDB default charset=utf8mb4 comment='拼团活动表';

create table if not exists knowledge_document (
  id bigint unsigned not null auto_increment comment '自增主键',
  document_id varchar(32) not null comment '文档编号',
  document_name varchar(128) not null comment '文档名称',
  document_type varchar(32) not null comment '文档类型',
  knowledge_version varchar(32) not null comment '知识版本',
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
  activity_id, goods_id, group_price, team_size, start_time, end_time, enabled
) values
('A10001', 'G10001', 2099.00, 3, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), 1),
('A10002', 'G10002', 2899.00, 5, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), 1)
on duplicate key update
  goods_id = values(goods_id),
  group_price = values(group_price),
  team_size = values(team_size),
  start_time = values(start_time),
  end_time = values(end_time),
  enabled = values(enabled);

insert into knowledge_document (
  document_id, document_name, document_type, knowledge_version, enabled
) values
('DOC10001', '学习平板商品详情说明', '商品详情', 'v1', 1),
('DOC10002', '学习平板拼团活动规则', '营销规则', 'v1', 1),
('DOC10003', '学习平板售后政策', '售后政策', 'v1', 1)
on duplicate key update
  document_name = values(document_name),
  document_type = values(document_type),
  knowledge_version = values(knowledge_version),
  enabled = values(enabled);

insert into knowledge_fragment (
  fragment_id, document_id, goods_id, document_type, knowledge_version, content, rank_no, enabled
) values
('KF10001', 'DOC10001', 'G10001', '商品详情', 'v1', '轻薄学习平板标准版适合写论文、看网课和日常笔记。', 1, 1),
('KF10002', 'DOC10002', 'G10001', '营销规则', 'v1', '标准版支持 3 人拼团，拼团价比原价低 300 元。', 2, 1),
('KF10003', 'DOC10003', 'G10001', '售后政策', 'v1', '拼团商品成团后支持 7 天无理由退货，未成团时系统自动退款。', 3, 1)
on duplicate key update
  document_id = values(document_id),
  goods_id = values(goods_id),
  document_type = values(document_type),
  knowledge_version = values(knowledge_version),
  content = values(content),
  rank_no = values(rank_no),
  enabled = values(enabled);
