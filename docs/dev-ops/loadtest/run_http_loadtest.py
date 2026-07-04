#!/usr/bin/env python3
"""简易压测脚本（无 JMeter 时的备选），并发打拼团锁单 / 直接购买接口。

用法：
  python docs/dev-ops/loadtest/run_http_loadtest.py --token <JWT> --scenario lock --threads 50 --duration 60
"""

from __future__ import annotations

import argparse
import json
import statistics
import threading
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    sorted_vals = sorted(values)
    k = (len(sorted_vals) - 1) * p / 100
    f = int(k)
    c = min(f + 1, len(sorted_vals) - 1)
    if f == c:
        return sorted_vals[f]
    return sorted_vals[f] + (sorted_vals[c] - sorted_vals[f]) * (k - f)


def one_request(url: str, token: str, body: dict) -> tuple[bool, bool, float]:
    """Returns (http_ok, business_ok, latency_ms)."""
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
        },
        method="POST",
    )
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            elapsed = (time.perf_counter() - start) * 1000
            http_ok = 200 <= resp.status < 300
            business_ok = False
            if http_ok:
                try:
                    payload = json.loads(resp.read().decode("utf-8"))
                    business_ok = payload.get("code") == "0000"
                except Exception:
                    business_ok = http_ok
            return http_ok, business_ok, elapsed
    except urllib.error.HTTPError as e:
        return False, False, (time.perf_counter() - start) * 1000
    except Exception:
        return False, False, (time.perf_counter() - start) * 1000


def run_load(host: str, port: int, token: str, scenario: str, threads: int, duration: int) -> dict:
    if scenario == "lock":
        path = "/api/v1/market/trade/lock"
        def body():
            return {
                "goodsId": "G10001",
                "activityId": "A10001",
                "idempotentKey": f"LT{uuid.uuid4().hex[:16]}",
                "payChannel": "ALIPAY",
            }
    else:
        path = "/api/v1/trade/order/direct"
        def body():
            return {
                "goodsId": "G10001",
                "idempotentKey": f"LT{uuid.uuid4().hex[:16]}",
                "payChannel": "ALIPAY",
            }

    url = f"http://{host}:{port}{path}"
    latencies: list[float] = []
    http_errors = 0
    http_success = 0
    business_success = 0
    lock = threading.Lock()
    stop_at = time.time() + duration

    def worker():
        nonlocal http_errors, http_success, business_success
        while time.time() < stop_at:
            http_ok, biz_ok, ms = one_request(url, token, body())
            with lock:
                if biz_ok:
                    latencies.append(ms)
                if http_ok:
                    http_success += 1
                    if biz_ok:
                        business_success += 1
                else:
                    http_errors += 1
            if not http_ok:
                time.sleep(0.01)

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=threads) as pool:
        futures = [pool.submit(worker) for _ in range(threads)]
        for f in as_completed(futures):
            f.result()
    elapsed = time.time() - t0
    total = http_success + http_errors
    return {
        "scenario": scenario,
        "threads": threads,
        "duration_sec": duration,
        "elapsed_sec": round(elapsed, 2),
        "total_requests": total,
        "http_success": http_success,
        "business_success": business_success,
        "http_errors": http_errors,
        "http_error_rate": round(http_errors / total, 4) if total else 0,
        "business_success_rate": round(business_success / total, 4) if total else 0,
        "qps": round(total / elapsed, 2) if elapsed else 0,
        "business_qps": round(business_success / elapsed, 2) if elapsed else 0,
        "p99_ms": round(percentile(latencies, 99), 2),
        "p95_ms": round(percentile(latencies, 95), 2),
        "avg_ms": round(statistics.mean(latencies), 2) if latencies else 0,
    }


def login(host: str, port: int, username: str, password: str) -> str:
    url = f"http://{host}:{port}/api/v1/auth/login"
    data = json.dumps({"username": username, "password": password}).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    token = payload.get("data", {}).get("token") or payload.get("data", {}).get("accessToken")
    if not token:
        raise RuntimeError(f"登录失败: {payload}")
    return token


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--token", default="")
    parser.add_argument("--username", default="demo")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--scenario", choices=["lock", "direct"], default="lock")
    parser.add_argument("--threads", type=int, default=50)
    parser.add_argument("--duration", type=int, default=60)
    args = parser.parse_args()

    token = args.token or login(args.host, args.port, args.username, args.password)
    result = run_load(args.host, args.port, token, args.scenario, args.threads, args.duration)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
