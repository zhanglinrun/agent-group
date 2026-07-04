-- 阶段 2.3：trade_order 用户订单列表慢查询优化索引
-- 场景：WHERE user_id = ? ORDER BY create_time DESC LIMIT 20
-- 压测灌数 10 万+ 行后，无组合索引易出现 filesort

ALTER TABLE trade_order
  ADD INDEX idx_user_time (user_id, create_time);
