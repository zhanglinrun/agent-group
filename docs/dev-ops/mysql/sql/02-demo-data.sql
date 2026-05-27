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
  goods_id, goods_name, image_url, origin_price, spec_summary, after_sale_policy,
  recommend_reason, not_suitable_for, enabled, sort_order
) values
('G10001', '轻薄学习平板标准版', 'https://example.com/agent-group/goods/G10001.png', 2399.00, '10.9 英寸屏幕，128GB 存储，支持手写笔，适合学习和轻办公', '7 天无理由退货，1 年质保；拼团未成团自动退款', '预算有限、学习和网课场景下性价比更高，适合作为学生主力学习平板', '长期剪视频、绘图或运行大型应用的用户', 1, 10),
('G10002', '高配创作平板', 'https://example.com/agent-group/goods/G10002.png', 3299.00, '12.1 英寸高刷屏，256GB 存储，适合绘图、剪视频和多任务', '7 天无理由退货，1 年质保；拼团未成团自动退款', '性能更强，适合创作类应用，但对学生轻办公场景预算压力更大', '只做笔记、看网课且预算有限的用户', 1, 20),
('G10003', '通勤办公二合一平板', 'https://example.com/agent-group/goods/G10003.png', 3699.00, '11.5 英寸护眼屏，256GB 存储，磁吸键盘套装，适合文档编辑和会议记录', '7 天无理由退货，1 年质保；键盘套装单独保修 6 个月', '适合研究生论文写作、轻办公和通勤携带，输入效率高于普通学习平板', '重度游戏、专业视频剪辑和预算低于 2500 元的用户', 1, 30),
('G10004', '游戏影音高刷平板', 'https://example.com/agent-group/goods/G10004.png', 2999.00, '12 英寸高刷屏，四扬声器，散热增强，适合影音娱乐和中大型游戏', '7 天无理由退货，1 年质保；人为进液和摔损不在质保范围', '适合高刷屏、影音和游戏诉求，性能强于标准版，价格低于创作平板', '主要写论文、网课和课堂笔记的预算敏感用户', 1, 40),
('G10005', '儿童学习护眼平板', 'https://example.com/agent-group/goods/G10005.png', 1899.00, '10.4 英寸护眼屏，家长管控，学习内容分级，适合儿童学习', '7 天无理由退货，1 年质保；学习内容权益按激活规则处理', '适合家长为儿童学习、网课和阅读购买，价格低且管控能力完整', '成人论文写作、专业绘图和大型应用用户', 1, 50),
('G10006', '手写笔记套装平板', 'https://example.com/agent-group/goods/G10006.png', 2699.00, '11 英寸屏幕，标配手写笔和类纸膜，适合课堂笔记、资料批注和考研复习', '7 天无理由退货，1 年质保；手写笔耗材不参与无理由退货', '适合笔记、批注和复习场景，配件一次配齐，长期学习成本更可控', '剪视频、绘图渲染和重度游戏用户', 1, 60)
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
('A10002', 'G10002', 2899.00, 5, '高配创作平板拼团', 'D10002', 0, 1, 5, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), 'TAG_PAY_2000', '2', 1),
('A10003', 'G10003', 3299.00, 3, '通勤办公套装拼团', 'D10002', 0, 1, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 5 day), null, null, 1),
('A10004', 'G10004', 2599.00, 4, '游戏影音平板拼团', 'D10002', 0, 1, 4, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 5 day), null, null, 1),
('A10005', 'G10005', 1699.00, 2, '儿童护眼平板拼团', 'D10001', 0, 2, 2, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 10 day), null, null, 1),
('A10006', 'G10006', 2399.00, 3, '手写笔记套装拼团', 'D10001', 0, 2, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), null, null, 1)
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
('s01', 'c01', 'G10002', '高配创作平板', 3299.00),
('s01', 'c01', 'G10003', '通勤办公二合一平板', 3699.00),
('s01', 'c01', 'G10004', '游戏影音高刷平板', 2999.00),
('s01', 'c01', 'G10005', '儿童学习护眼平板', 1899.00),
('s01', 'c01', 'G10006', '手写笔记套装平板', 2699.00)
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
('A10001', 'G10001', 100, 99, 0, 1),
('A10002', 'G10002', 100, 100, 0, 0),
('A10003', 'G10003', 80, 80, 0, 0),
('A10004', 'G10004', 60, 60, 0, 0),
('A10005', 'G10005', 120, 120, 0, 0),
('A10006', 'G10006', 100, 100, 0, 0)
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
('DOC10004', '学习平板评测场景说明', '评测样例', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10005', '多商品导购规则说明', '导购规则', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10006', '拼团交易安全规则', '交易规则', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10007', '多商品场景补充说明', '导购规则', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10008', '导购回答边界说明', '安全边界', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1)
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
('KF10001', 'DOC10001', 'G10001', '商品详情', 'v1', '轻薄学习平板标准版适合写论文、看网课、手写笔记、日常文档编辑和轻办公。', 1, 'ENABLED', 1),
('KF10002', 'DOC10002', 'G10001', '营销规则', 'v1', '标准版支持 3 人拼团，拼团价 2099 元，比原价低 300 元，适合预算敏感用户。', 2, 'ENABLED', 1),
('KF10003', 'DOC10003', 'G10001', '售后政策', 'v1', '拼团商品成团后支持 7 天无理由退货，未成团时系统自动退款，标准版享受 1 年质保。', 3, 'ENABLED', 1),
('KF10004', 'DOC10001', 'G10002', '商品详情', 'v1', '高配创作平板适合剪视频、绘图和多任务，性能强于标准版，但价格更高。', 4, 'ENABLED', 1),
('KF10005', 'DOC10002', 'G10002', '营销规则', 'v1', '高配创作平板支持 5 人拼团，拼团价 2899 元，适合创作应用用户。', 5, 'ENABLED', 1),
('KF10006', 'DOC10004', 'G10001', '评测样例', 'v1', '学生预算导购需要同时说明学习场景、拼团价、原价和不适合大型应用的边界。', 6, 'ENABLED', 1),
('KF10007', 'DOC10004', 'G10001', '评测样例', 'v1', '多轮追问预算限制时，应延续上一轮学生和预算有限上下文，继续比较直接购买和拼团购买。', 7, 'ENABLED', 1),
('KF10008', 'DOC10004', 'G10001', '评测样例', 'v1', '图片截图导购场景需要结合商品外观、价格、优惠和拼团信息，不直接编造图片中没有出现的参数。', 8, 'ENABLED', 1),
('KF10009', 'DOC10002', 'G10001', '营销规则', 'v1', '直接购买按原价 2399 元创建订单，拼团购买按拼团价 2099 元锁单并等待成团。', 9, 'ENABLED', 1),
('KF10010', 'DOC10003', 'G10001', '售后政策', 'v1', '如果订单已支付但拼团最终失败，系统会记录退款单并释放对应拼团名额。', 10, 'ENABLED', 1),
('KF10011', 'DOC10005', 'G10003', '导购规则', 'v1', '通勤办公二合一平板适合论文写作、会议记录和文档编辑，标配键盘套装，拼团价 3299 元。', 11, 'ENABLED', 1),
('KF10012', 'DOC10005', 'G10004', '导购规则', 'v1', '游戏影音高刷平板适合高刷屏、影音和中大型游戏，拼团价 2599 元，不优先推荐给只看网课的预算敏感学生。', 12, 'ENABLED', 1),
('KF10013', 'DOC10005', 'G10005', '导购规则', 'v1', '儿童学习护眼平板适合儿童网课、阅读和家长管控，拼团价 1699 元，不适合成人论文和专业绘图。', 13, 'ENABLED', 1),
('KF10014', 'DOC10005', 'G10006', '导购规则', 'v1', '手写笔记套装平板适合课堂笔记、资料批注和考研复习，拼团价 2399 元，手写笔耗材不参与无理由退货。', 14, 'ENABLED', 1),
('KF10015', 'DOC10005', 'G10001', '导购规则', 'v1', '导购回答生成下单入口前必须生成导购报价凭证，凭证内部包含导购决策编号、商品、活动、报价和有效期；如果用户隔很久再下单，之前的价格可能过期，必须重新校验活动、商品和价格；订单金额要和商品卡片、支付单保持一致。', 15, 'ENABLED', 1),
('KF10016', 'DOC10006', 'G10001', '交易规则', 'v1', '拼团锁单使用幂等键，用户重复点击拼团购买或重复下单不重复占用名额，也不会重复生成订单。', 16, 'ENABLED', 1),
('KF10017', 'DOC10006', 'G10001', '交易规则', 'v1', '活动过期、商品下架、库存不足或队伍已满时，后端不能继续锁单，不能生成拼团入口，也不会创建支付单；库存不足时后端不能建议用户先支付保留名额。', 17, 'ENABLED', 1),
('KF10018', 'DOC10006', 'G10001', '交易规则', 'v1', '拼团支付成功只代表订单已支付和名额已锁定，订单需要等待成团结算；支付成功不等于已经成团。', 18, 'ENABLED', 1),
('KF10019', 'DOC10006', 'G10001', '交易规则', 'v1', '支付平台重复通知同一笔订单时，系统按支付单和回调流水做防重放与幂等处理，不会二次扣费，也不会重复推进状态。', 19, 'ENABLED', 1),
('KF10020', 'DOC10006', 'G10001', '交易规则', 'v1', '支付成功但成团结算消息发送失败时，系统通过 Outbox 事件表和补偿任务继续推进订单状态，避免订单一直卡住。', 20, 'ENABLED', 1),
('KF10021', 'DOC10006', 'G10001', '交易规则', 'v1', '前端不能直接决定支付金额，后端会按导购报价凭证、导购卡片、商品卡片、活动、订单金额和支付单金额重新校验，三方不一致时以后端交易系统为准。', 21, 'ENABLED', 1),
('KF10022', 'DOC10003', 'G10001', '售后政策', 'v1', '用户主动申请退款时，系统需要记录退款单、退款金额、退款原因、订单状态和处理状态；直接购买退款与拼团未成团退款要区分处理。', 22, 'ENABLED', 1),
('KF10023', 'DOC10003', 'G10001', '售后政策', 'v1', '标准版和多数平板支持 7 天无理由退货与 1 年保修，拼团未成团自动退款；售后回答需要同时说明退货和保修边界。', 23, 'ENABLED', 1),
('KF10024', 'DOC10007', 'G10001', '导购规则', 'v1', '导购判断规则：轻薄学习平板标准版适合学生学习、论文写作、网课、轻办公、日常文档编辑、课堂笔记和资料批注，预算有限时优先推荐标准版。', 24, 'ENABLED', 1),
('KF10025', 'DOC10007', 'G10002', '导购规则', 'v1', '高配创作平板包含高刷屏，适合剪视频、绘图、多任务和创作应用；标准版不建议长期剪视频、绘图或大型游戏。', 25, 'ENABLED', 1),
('KF10026', 'DOC10007', 'G10003', '导购规则', 'v1', '通勤办公二合一平板适合研究生通勤、论文写作、会议记录和键盘输入，不应误推荐儿童学习护眼平板。', 26, 'ENABLED', 1),
('KF10027', 'DOC10008', 'G10001', '安全边界', 'v1', '导购判断规则要求先说明依据；商品详情没有承诺时，不能保证一款平板三年持续流畅，也不能编造没有依据的性能承诺。', 27, 'ENABLED', 1),
('KF10028', 'DOC10008', 'G10001', '安全边界', 'v1', '如果没有通过工具查到活动库存或队伍名额，回答必须说明不能直接给出剩余名额，不能编造固定剩余名额。', 28, 'ENABLED', 1)
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
  order_id, idempotent_key, user_id, goods_id, goods_name, activity_id, buy_type,
  origin_amount, pay_amount, order_status, pay_time
) values
('O-DEMO-DIRECT-001', 'DEMO-DIRECT-001', 'U10001', 'G10001', '轻薄学习平板标准版', null, 'DIRECT', 2399.00, 2399.00, 'PAY_SUCCESS', date_sub(now(), interval 1 hour)),
('O-DEMO-GROUP-001', 'DEMO-GROUP-LOCK-001', 'U10001', 'G10001', '轻薄学习平板标准版', 'A10001', 'GROUP_BUY', 2399.00, 2099.00, 'GROUP_SETTLED', date_sub(now(), interval 30 minute))
on duplicate key update
  idempotent_key = values(idempotent_key),
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
