#!/usr/bin/env python3
"""批量灌入压测数据：trade_order、pay_order、group_buy_order_lock。

用法（在项目根目录）：
  python docs/dev-ops/loadtest/seed_bulk_trade_data.py --rows 100000

默认连接 docker 映射端口 13306，可通过环境变量覆盖：
  AGENT_GROUP_MYSQL_HOST / AGENT_GROUP_MYSQL_PORT / AGENT_GROUP_MYSQL_PASSWORD
"""

from __future__ import annotations

import argparse
import os
import sys
import time

try:
    import pymysql
except ImportError:
    print("请先安装 pymysql: pip install pymysql", file=sys.stderr)
    sys.exit(1)

HOST = os.getenv("AGENT_GROUP_MYSQL_HOST", "127.0.0.1")
PORT = int(os.getenv("AGENT_GROUP_MYSQL_PORT", "13306"))
USER = os.getenv("AGENT_GROUP_MYSQL_USER", "root")
PASSWORD = os.getenv("AGENT_GROUP_MYSQL_PASSWORD", "agent_group_dev")
DATABASE = os.getenv("AGENT_GROUP_MYSQL_DATABASE", "agent_group")
BATCH = 1000


def connect():
    return pymysql.connect(
        host=HOST,
        port=PORT,
        user=USER,
        password=PASSWORD,
        database=DATABASE,
        charset="utf8mb4",
        autocommit=False,
    )


def seed_trade_orders(cursor, start: int, count: int) -> None:
    for batch_start in range(start, start + count, BATCH):
        batch_end = min(batch_start + BATCH, start + count)
        values = []
        for i in range(batch_start, batch_end):
            order_id = f"LT{ i:08d}"
            idem = f"LT-SEED-{ i:08d}"
            user_id = f"U{10000 + (i % 500):05d}"
            values.append(
                f"('{order_id}','{idem}','{user_id}','G10001','基础额度包','A10001',"
                f"'GROUP_BUY',19.90,16.90,'CREATE',NULL,NULL)"
            )
        sql = (
            "INSERT INTO trade_order "
            "(order_id,idempotent_key,user_id,goods_id,goods_name,activity_id,buy_type,"
            "origin_amount,pay_amount,order_status,pay_time,close_time) VALUES "
            + ",".join(values)
        )
        cursor.execute(sql)


def seed_pay_orders(cursor, start: int, count: int) -> None:
    for batch_start in range(start, start + count, BATCH):
        batch_end = min(batch_start + BATCH, start + count)
        values = []
        for i in range(batch_start, batch_end):
            order_id = f"LT{ i:08d}"
            pay_id = f"P{ i:08d}"
            values.append(
                f"('{pay_id}','{order_id}','ALIPAY',16.90,'WAIT_PAY',NULL,NULL,NULL)"
            )
        sql = (
            "INSERT INTO pay_order "
            "(pay_order_id,order_id,pay_channel,pay_amount,pay_status,pay_url,out_trade_no,pay_time) VALUES "
            + ",".join(values)
        )
        cursor.execute(sql)


def seed_lock_orders(cursor, start: int, count: int) -> None:
    for batch_start in range(start, start + count, BATCH):
        batch_end = min(batch_start + BATCH, start + count)
        values = []
        for i in range(batch_start, batch_end):
            order_id = f"LT{ i:08d}"
            lock_id = f"LK{ i:08d}"
            idem = f"LT-SEED-{ i:08d}"
            user_id = f"U{10000 + (i % 500):05d}"
            team_id = f"T{10000 + (i % 200):05d}"
            values.append(
                f"('{lock_id}','{idem}','{user_id}','{team_id}','{order_id}',"
                f"'A10001','G10001',16.90,'LOCKED',NOW())"
            )
        sql = (
            "INSERT INTO group_buy_order_lock "
            "(lock_id,idempotent_key,user_id,team_id,order_id,activity_id,goods_id,"
            "lock_amount,lock_status,lock_time) VALUES "
            + ",".join(values)
        )
        cursor.execute(sql)


def count_rows(cursor, table: str) -> int:
    cursor.execute(f"SELECT COUNT(*) FROM {table}")
    return int(cursor.fetchone()[0])


def main() -> None:
    parser = argparse.ArgumentParser(description="灌入压测用交易数据")
    parser.add_argument("--rows", type=int, default=100_000, help="每个表灌入行数")
    parser.add_argument("--start", type=int, default=1, help="序号起始（避免与演示数据冲突）")
    args = parser.parse_args()

    print(f"连接 MySQL {HOST}:{PORT}/{DATABASE} ...")
    conn = connect()
    try:
        cursor = conn.cursor()
        before_order = count_rows(cursor, "trade_order")
        before_pay = count_rows(cursor, "pay_order")
        before_lock = count_rows(cursor, "group_buy_order_lock")
        print(f"灌数前: trade_order={before_order}, pay_order={before_pay}, lock={before_lock}")

        t0 = time.time()
        print(f"灌入 trade_order {args.rows} 行 ...")
        seed_trade_orders(cursor, args.start, args.rows)
        conn.commit()
        print(f"灌入 pay_order {args.rows} 行 ...")
        seed_pay_orders(cursor, args.start, args.rows)
        conn.commit()
        print(f"灌入 group_buy_order_lock {args.rows} 行 ...")
        seed_lock_orders(cursor, args.start, args.rows)
        conn.commit()
        elapsed = time.time() - t0

        after_order = count_rows(cursor, "trade_order")
        after_pay = count_rows(cursor, "pay_order")
        after_lock = count_rows(cursor, "group_buy_order_lock")
        print(
            f"完成，耗时 {elapsed:.1f}s\n"
            f"灌数后: trade_order={after_order}, pay_order={after_pay}, lock={after_lock}"
        )
    finally:
        conn.close()


if __name__ == "__main__":
    main()
