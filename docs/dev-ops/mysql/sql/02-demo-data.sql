use agent_group;

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
('G10001', '轻薄学习平板标准版', 'https://example.com/agent-group/goods/G10001.png', 2399.00, '10.9 英寸屏幕，128GB 存储，支持手写笔，适合学习和轻办公', '7 天无理由退货，1 年质保；拼团未成团自动退款', '预算有限、学习和网课场景下性价比更高，适合作为学生主力学习平板', '长期剪视频、绘图或运行大型应用的用户', 1, 10),
('G10002', '高配创作平板', 'https://example.com/agent-group/goods/G10002.png', 3299.00, '12.1 英寸高刷屏，256GB 存储，适合绘图、剪视频和多任务', '7 天无理由退货，1 年质保；拼团未成团自动退款', '性能更强，适合创作类应用，但对学生轻办公场景预算压力更大', '只做笔记、看网课且预算有限的用户', 1, 20)
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

insert into group_buy_stock (
  activity_id, goods_id, total_stock, available_stock, locked_stock, paid_stock
) values
('A10001', 'G10001', 100, 99, 0, 1),
('A10002', 'G10002', 100, 100, 0, 0)
on duplicate key update
  goods_id = values(goods_id),
  total_stock = values(total_stock),
  available_stock = values(available_stock),
  locked_stock = values(locked_stock),
  paid_stock = values(paid_stock);

insert into knowledge_document (
  document_id, document_name, document_type, knowledge_version, source_type, source_name, document_status, enabled
) values
('DOC10001', '学习平板商品详情说明', '商品详情', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10002', '学习平板拼团活动规则', '营销规则', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10003', '学习平板售后政策', '售后政策', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10004', '学习平板评测场景说明', '评测样例', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1)
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
('KF10001', 'DOC10001', 'G10001', '商品详情', 'v1', '轻薄学习平板标准版适合写论文、看网课、手写笔记和日常文档编辑。', 1, 'ENABLED', 1),
('KF10002', 'DOC10002', 'G10001', '营销规则', 'v1', '标准版支持 3 人拼团，拼团价 2099 元，比原价低 300 元，适合预算敏感用户。', 2, 'ENABLED', 1),
('KF10003', 'DOC10003', 'G10001', '售后政策', 'v1', '拼团商品成团后支持 7 天无理由退货，未成团时系统自动退款，标准版享受 1 年质保。', 3, 'ENABLED', 1),
('KF10004', 'DOC10001', 'G10002', '商品详情', 'v1', '高配创作平板适合剪视频、绘图和多任务，性能强于标准版，但价格更高。', 4, 'ENABLED', 1),
('KF10005', 'DOC10002', 'G10002', '营销规则', 'v1', '高配创作平板支持 5 人拼团，拼团价 2899 元，适合创作应用用户。', 5, 'ENABLED', 1),
('KF10006', 'DOC10004', 'G10001', '评测样例', 'v1', '学生预算导购需要同时说明学习场景、拼团价、原价和不适合大型应用的边界。', 6, 'ENABLED', 1),
('KF10007', 'DOC10004', 'G10001', '评测样例', 'v1', '多轮追问预算限制时，应延续上一轮学生和预算有限上下文，继续比较直接购买和拼团购买。', 7, 'ENABLED', 1),
('KF10008', 'DOC10004', 'G10001', '评测样例', 'v1', '图片截图导购场景需要结合商品外观、价格、优惠和拼团信息，不直接编造图片中没有出现的参数。', 8, 'ENABLED', 1),
('KF10009', 'DOC10002', 'G10001', '营销规则', 'v1', '直接购买按原价 2399 元创建订单，拼团购买按拼团价 2099 元锁单并等待成团。', 9, 'ENABLED', 1),
('KF10010', 'DOC10003', 'G10001', '售后政策', 'v1', '如果订单已支付但拼团最终失败，系统会记录退款单并释放对应拼团名额。', 10, 'ENABLED', 1)
on duplicate key update
  document_id = values(document_id),
  goods_id = values(goods_id),
  document_type = values(document_type),
  knowledge_version = values(knowledge_version),
  content = values(content),
  rank_no = values(rank_no),
  fragment_status = values(fragment_status),
  enabled = values(enabled);

insert into group_buy_team (
  team_id, activity_id, goods_id, target_count, complete_count, lock_count,
  team_status, valid_start_time, valid_end_time
) values
('TEAM-DEMO-001', 'A10001', 'G10001', 3, 3, 3, 'SUCCESS', date_sub(now(), interval 2 hour), date_add(now(), interval 22 hour))
on duplicate key update
  activity_id = values(activity_id),
  goods_id = values(goods_id),
  target_count = values(target_count),
  complete_count = values(complete_count),
  lock_count = values(lock_count),
  team_status = values(team_status),
  valid_start_time = values(valid_start_time),
  valid_end_time = values(valid_end_time);

insert into trade_order (
  order_id, user_id, goods_id, goods_name, activity_id, buy_type,
  origin_amount, pay_amount, order_status, pay_time
) values
('O-DEMO-DIRECT-001', 'U10001', 'G10001', '轻薄学习平板标准版', null, 'DIRECT', 2399.00, 2399.00, 'PAY_SUCCESS', date_sub(now(), interval 1 hour)),
('O-DEMO-GROUP-001', 'U10001', 'G10001', '轻薄学习平板标准版', 'A10001', 'GROUP_BUY', 2399.00, 2099.00, 'GROUP_SETTLED', date_sub(now(), interval 30 minute))
on duplicate key update
  user_id = values(user_id),
  goods_id = values(goods_id),
  goods_name = values(goods_name),
  activity_id = values(activity_id),
  buy_type = values(buy_type),
  origin_amount = values(origin_amount),
  pay_amount = values(pay_amount),
  order_status = values(order_status),
  pay_time = values(pay_time);

insert into pay_order (
  pay_order_id, order_id, pay_channel, pay_amount, pay_status, pay_url, out_trade_no, pay_time
) values
('P-DEMO-DIRECT-001', 'O-DEMO-DIRECT-001', 'MOCK_PAY', 2399.00, 'SUCCESS', 'mock://pay/O-DEMO-DIRECT-001', 'OUT-DEMO-DIRECT-001', date_sub(now(), interval 1 hour)),
('P-DEMO-GROUP-001', 'O-DEMO-GROUP-001', 'MOCK_PAY', 2099.00, 'SUCCESS', 'mock://pay/O-DEMO-GROUP-001', 'OUT-DEMO-GROUP-001', date_sub(now(), interval 30 minute))
on duplicate key update
  pay_channel = values(pay_channel),
  pay_amount = values(pay_amount),
  pay_status = values(pay_status),
  pay_url = values(pay_url),
  out_trade_no = values(out_trade_no),
  pay_time = values(pay_time);

insert into group_buy_order_lock (
  lock_id, idempotent_key, user_id, team_id, order_id, activity_id,
  goods_id, lock_amount, lock_status, lock_time
) values
('L-DEMO-GROUP-001', 'DEMO-GROUP-LOCK-001', 'U10001', 'TEAM-DEMO-001', 'O-DEMO-GROUP-001', 'A10001', 'G10001', 2099.00, 'PAID', date_sub(now(), interval 35 minute))
on duplicate key update
  user_id = values(user_id),
  team_id = values(team_id),
  order_id = values(order_id),
  activity_id = values(activity_id),
  goods_id = values(goods_id),
  lock_amount = values(lock_amount),
  lock_status = values(lock_status),
  lock_time = values(lock_time);

insert into trade_status_flow (
  flow_id, order_id, biz_type, biz_id, event_type, from_status, to_status, remark, create_time
) values
('F-DEMO-DIRECT-001', 'O-DEMO-DIRECT-001', 'ORDER', 'O-DEMO-DIRECT-001', 'CREATE_DIRECT_ORDER', null, 'PAY_WAIT', '演示数据：直接购买订单创建', date_sub(now(), interval 70 minute)),
('F-DEMO-DIRECT-002', 'O-DEMO-DIRECT-001', 'PAY', 'P-DEMO-DIRECT-001', 'PAY_SUCCESS', 'PAY_WAIT', 'PAY_SUCCESS', '演示数据：模拟支付成功', date_sub(now(), interval 60 minute)),
('F-DEMO-GROUP-001', 'O-DEMO-GROUP-001', 'ORDER', 'O-DEMO-GROUP-001', 'CREATE_GROUP_ORDER', null, 'PAY_WAIT', '演示数据：拼团订单创建', date_sub(now(), interval 40 minute)),
('F-DEMO-GROUP-002', 'O-DEMO-GROUP-001', 'GROUP', 'L-DEMO-GROUP-001', 'GROUP_LOCKED', null, 'LOCKED', '演示数据：拼团名额锁定', date_sub(now(), interval 38 minute)),
('F-DEMO-GROUP-003', 'O-DEMO-GROUP-001', 'PAY', 'P-DEMO-GROUP-001', 'PAY_SUCCESS', 'PAY_WAIT', 'PAY_SUCCESS', '演示数据：拼团订单支付成功', date_sub(now(), interval 30 minute)),
('F-DEMO-GROUP-004', 'O-DEMO-GROUP-001', 'GROUP', 'TEAM-DEMO-001', 'GROUP_SETTLED', 'PAY_SUCCESS', 'GROUP_SETTLED', '演示数据：拼团成团结算', date_sub(now(), interval 20 minute))
on duplicate key update
  order_id = values(order_id),
  biz_type = values(biz_type),
  biz_id = values(biz_id),
  event_type = values(event_type),
  from_status = values(from_status),
  to_status = values(to_status),
  remark = values(remark),
  create_time = values(create_time);
