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
('groupRefundNotifyMQ', 'agent.group.notify.group-refund', 'group refund notify mq'),
('agentBillingPromptCostPer1k', '0.10', 'platform prompt quota cost per 1k tokens'),
('agentBillingCompletionCostPer1k', '0.30', 'platform completion quota cost per 1k tokens'),
('agentBillingCustomModelServiceRate', '0.10', 'custom model service fee rate')
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
('G10006', '团队拼团额度包', '', 129.90, 360.00, 'QUOTA_PACKAGE', '适合实验室小组共享演示，额度更多且拼团优惠更明显', '虚拟额度到账后不支持无理由退款；未使用额度退款时会回滚到账额度', '适合组会、课题组内部演示和多人拼团充值', '个人轻量体验用户', 1, 60),
('MEMBER_PLUS_MONTH', 'Plus 会员', '', 39.90, 1000.00, 'MEMBERSHIP_PLAN', '每月 1000 点会员额度，自定义模型会员免费，适合高频论文问答和 PPT 生成', '会员属于虚拟服务，支付开通后按平台会员服务规则处理售后', '适合需要稳定使用对话、文件问答、图像和深度研究能力的个人用户', '只偶尔体验一次的用户', 1, 1),
('MEMBER_PRO_MONTH', 'Pro 会员', '', 99.90, 5000.00, 'MEMBERSHIP_PLAN', '每月 5000 点会员额度，适合深度研究、长文档处理和多模态生成', '会员属于虚拟服务，支付开通后按平台会员服务规则处理售后', '适合项目复盘、论文精读、答辩材料和复杂 Agent 任务高频使用', '只进行普通短对话的用户', 1, 2)
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
('A10004', 'G10004', 33.90, 3, '图表重建额度包拼团', 'D10002', 0, 1, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 5 day), null, null, 1),
('A10005', 'G10005', 84.90, 5, '深度研究额度包拼团', 'D10001', 0, 2, 5, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 10 day), null, null, 1),
('A10006', 'G10006', 109.90, 3, '团队拼团额度包拼团', 'D10001', 0, 2, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), null, null, 1),
('A10007', 'MEMBER_PLUS_MONTH', 32.90, 3, 'Plus 会员拼团', 'D10001', 0, 1, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), null, null, 1),
('A10008', 'MEMBER_PRO_MONTH', 84.90, 3, 'Pro 会员拼团', 'D10001', 0, 1, 3, 1440, 1, date_sub(now(), interval 1 day), date_add(now(), interval 7 day), null, null, 1)
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
('s01', 'c01', 'G10006', '团队拼团额度包', 129.90),
('s01', 'c01', 'MEMBER_PLUS_MONTH', 'Plus 会员', 39.90),
('s01', 'c01', 'MEMBER_PRO_MONTH', 'Pro 会员', 99.90)
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
('s01', 'c01', 'A10006', 'G10006'),
('s01', 'c01', 'A10007', 'MEMBER_PLUS_MONTH'),
('s01', 'c01', 'A10008', 'MEMBER_PRO_MONTH')
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
('A10006', 'G10006', 100, 100, 0, 0),
('A10007', 'MEMBER_PLUS_MONTH', 100, 100, 0, 0),
('A10008', 'MEMBER_PRO_MONTH', 100, 100, 0, 0)
on duplicate key update
  goods_id = values(goods_id),
  total_stock = values(total_stock),
  available_stock = values(available_stock),
  locked_stock = values(locked_stock),
  paid_stock = values(paid_stock);

insert into knowledge_document (
  document_id, document_name, document_type, knowledge_version, source_type, source_name, document_status, enabled
) values
('DOC10001', '学术额度包说明', '额度包资料', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10002', '学术额度包拼团规则', '拼团规则', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10003', '学术额度包退款规则', '退款规则', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10004', '学术 Agent 评测场景说明', '评测样例', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10005', '学术 Agent 任务说明', 'Agent 任务规则', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10006', '拼团交易安全规则', '交易规则', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10007', '学术场景补充说明', 'Agent 任务规则', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1),
('DOC10008', '学术 Agent 回答边界说明', '安全边界', 'v1', 'DEMO_DATA', '演示数据脚本', 'ENABLED', 1)
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
('KF10004', 'DOC10001', 'G10002', '额度包资料', 'v1', '论文阅读额度包包含 120 点额度，适合上传论文、生成精读笔记、方法复现清单和相关工作摘要。', 4, 'ENABLED', 1),
('KF10005', 'DOC10002', 'G10002', '拼团规则', 'v1', '论文阅读额度包支持 5 人拼团，拼团价 42.90 元，直接购买价 49.90 元。', 5, 'ENABLED', 1),
('KF10006', 'DOC10004', 'G10001', '评测样例', 'v1', '学术 Agent 回答需要同时说明任务目标、输入资料、生成结果和额度消耗，不能把模型输出当成交易状态。', 6, 'ENABLED', 1),
('KF10007', 'DOC10004', 'G10001', '评测样例', 'v1', '多轮追问论文、PPT 或流程图时，应延续上一轮文件和任务上下文，保持标题、术语和章节结构一致。', 7, 'ENABLED', 1),
('KF10008', 'DOC10004', 'G10004', '评测样例', 'v1', '图片转图表场景需要先说明识别到的元素，再生成可编辑草稿；不能承诺严格 1:1 商业复刻。', 8, 'ENABLED', 1),
('KF10009', 'DOC10002', 'G10001', '拼团规则', 'v1', '直接购买按原价 19.90 元创建订单，拼团购买按拼团价 16.90 元锁单并等待成团。', 9, 'ENABLED', 1),
('KF10010', 'DOC10003', 'G10001', '退款规则', 'v1', '如果订单已支付但拼团最终失败，系统会记录退款单并释放对应拼团名额，已发放额度需要按退款规则回滚。', 10, 'ENABLED', 1),
('KF10011', 'DOC10005', 'G10003', 'Agent 任务规则', 'v1', 'PPT 创作额度包适合组会汇报、开题答辩和论文分享，支持生成演示稿结构、页面大纲和讲稿草稿。', 11, 'ENABLED', 1),
('KF10012', 'DOC10005', 'G10004', 'Agent 任务规则', 'v1', '图表重建额度包适合把论文图、流程图和架构图转换成可编辑 Mermaid 或结构化草稿。', 12, 'ENABLED', 1),
('KF10013', 'DOC10005', 'G10005', 'Agent 任务规则', 'v1', '深度研究额度包适合复杂主题拆解、技术路线规划、相关工作整理和长报告生成。', 13, 'ENABLED', 1),
('KF10014', 'DOC10005', 'G10006', 'Agent 任务规则', 'v1', '团队拼团额度包适合实验室小组共享演示，拼团价 109.90 元，可获得 360 点额度。', 14, 'ENABLED', 1),
('KF10015', 'DOC10005', 'G10001', '交易规则', 'v1', '购买入口由后端交易系统创建订单，订单金额、支付单金额和额度发放数量都以后端交易系统为准。', 15, 'ENABLED', 1),
('KF10016', 'DOC10006', 'G10001', '交易规则', 'v1', '拼团锁单使用幂等键，用户重复点击拼团购买或重复下单不重复占用名额，也不会重复生成订单。', 16, 'ENABLED', 1),
('KF10017', 'DOC10006', 'G10001', '交易规则', 'v1', '活动过期、商品下架、库存不足或队伍已满时，后端不能继续锁单，不能生成拼团入口，也不会创建支付单。', 17, 'ENABLED', 1),
('KF10018', 'DOC10006', 'G10001', '交易规则', 'v1', '拼团支付成功只代表订单已支付和名额已锁定，额度发放需要以后端支付回调和订单状态为准。', 18, 'ENABLED', 1),
('KF10019', 'DOC10006', 'G10001', '交易规则', 'v1', '支付平台重复通知同一笔订单时，系统按支付单和回调流水做防重放与幂等处理，不会二次扣费，也不会重复推进状态。', 19, 'ENABLED', 1),
('KF10020', 'DOC10006', 'G10001', '交易规则', 'v1', '支付成功但成团结算消息发送失败时，系统通过 Outbox 事件表和补偿任务继续推进订单状态，避免订单一直卡住。', 20, 'ENABLED', 1),
('KF10021', 'DOC10006', 'G10001', '交易规则', 'v1', '前端不能直接决定支付金额或额度数量，后端会按商品、活动、订单金额和支付单金额重新校验。', 21, 'ENABLED', 1),
('KF10022', 'DOC10003', 'G10001', '退款规则', 'v1', '用户主动申请退款时，系统需要记录退款单、退款金额、退款原因、订单状态和处理状态；额度回滚要和退款单保持一致。', 22, 'ENABLED', 1),
('KF10023', 'DOC10003', 'G10001', '退款规则', 'v1', '额度包到账后可以继续用于学术对话、论文阅读、PPT 生成和图表重建；已消耗额度不能退款。', 23, 'ENABLED', 1),
('KF10024', 'DOC10007', 'G10001', 'Agent 任务规则', 'v1', '学术额度包判断规则：普通短问答优先推荐基础额度包，读论文优先推荐论文阅读额度包。', 24, 'ENABLED', 1),
('KF10025', 'DOC10007', 'G10003', 'Agent 任务规则', 'v1', '需要批量生成组会汇报、开题答辩或论文分享材料时，优先推荐 PPT 创作额度包。', 25, 'ENABLED', 1),
('KF10026', 'DOC10007', 'G10004', 'Agent 任务规则', 'v1', '需要把论文图片、流程图、架构图转成可编辑图表时，优先推荐图表重建额度包。', 26, 'ENABLED', 1),
('KF10027', 'DOC10008', 'G10001', '安全边界', 'v1', '学术 Agent 可以生成草稿、提纲和可编辑结构，但不能保证论文结论真实、实验结果正确或外部资料已实际下载。', 27, 'ENABLED', 1),
('KF10028', 'DOC10008', 'G10001', '安全边界', 'v1', '如果没有通过后端工具查到订单、支付或额度账户状态，回答必须说明不能直接确认到账或退款。', 28, 'ENABLED', 1)
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
('O-DEMO-DIRECT-001', 'DEMO-DIRECT-001', 'U10001', 'G10001', '基础额度包', null, 'DIRECT', 19.90, 19.90, 'PAY_SUCCESS', date_sub(now(), interval 1 hour)),
('O-DEMO-GROUP-001', 'DEMO-GROUP-LOCK-001', 'U10001', 'G10001', '基础额度包', 'A10001', 'GROUP_BUY', 19.90, 16.90, 'GROUP_SETTLED', date_sub(now(), interval 30 minute))
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
('P-DEMO-DIRECT-001', 'O-DEMO-DIRECT-001', 'MOCK_PAY', 19.90, 'SUCCESS', 'mock://pay/O-DEMO-DIRECT-001', 'OUT-DEMO-DIRECT-001', date_sub(now(), interval 1 hour)),
('P-DEMO-GROUP-001', 'O-DEMO-GROUP-001', 'MOCK_PAY', 16.90, 'SUCCESS', 'mock://pay/O-DEMO-GROUP-001', 'OUT-DEMO-GROUP-001', date_sub(now(), interval 30 minute))
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
('L-DEMO-GROUP-001', 'DEMO-GROUP-LOCK-001', 'U10001', 'TEAM-DEMO-001', 'O-DEMO-GROUP-001', 'A10001', 'G10001', 16.90, 'PAID', date_sub(now(), interval 35 minute))
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
