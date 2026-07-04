#!/usr/bin/env python3
"""压测期间采集 Prometheus / Actuator 指标快照，写入 loadtest 观察结论。

用法：
  python docs/dev-ops/loadtest/collect_observations.py --duration 65
"""

from __future__ import annotations

import argparse
import json
import statistics
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "docs" / "dev-ops" / "loadtest" / "observations.json"


def prom_query(base: str, query: str) -> float | None:
    url = f"{base}/api/v1/query?{urllib.parse.urlencode({'query': query})}"
    try:
        with urllib.request.urlopen(url, timeout=5) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
        result = payload.get("data", {}).get("result", [])
        if not result:
            return None
        return float(result[0]["value"][1])
    except Exception:
        return None


def actuator_gauge(actuator_base: str, metric_prefix: str, labels: dict[str, str]) -> float | None:
    """从 /actuator/prometheus 文本格式解析 gauge（Prometheus 不可达时的回退）。"""
    try:
        with urllib.request.urlopen(f"{actuator_base}/actuator/prometheus", timeout=8) as resp:
            text = resp.read().decode("utf-8", errors="replace")
    except Exception:
        return None
    label_part = ",".join(f'{k}="{v}"' for k, v in labels.items())
    needle = f"{metric_prefix}{{{label_part}}}"
    for line in text.splitlines():
        if line.startswith(needle):
            try:
                return float(line.rsplit(" ", 1)[-1])
            except ValueError:
                return None
    return None


def sample_loop(prom: str, actuator: str, seconds: int, interval: float = 2.0) -> dict:
    hikari_active = []
    hikari_pending = []
    jvm_threads = []
    gc_pause_max = []
    end = time.time() + seconds
    hikari_labels = {"application": "agent-group", "pool": "AgentGroup_HikariCP"}
    while time.time() < end:
        active = prom_query(prom, 'hikaricp_connections_active{pool="AgentGroup_HikariCP"}')
        pending = prom_query(prom, 'hikaricp_connections_pending{pool="AgentGroup_HikariCP"}')
        threads = prom_query(prom, "jvm_threads_live_threads")
        if active is None:
            active = actuator_gauge(actuator, "hikaricp_connections_active", hikari_labels)
        if pending is None:
            pending = actuator_gauge(actuator, "hikaricp_connections_pending", hikari_labels)
        if threads is None:
            threads = prom_query(prom, "jvm_threads_live_threads")
            if threads is None:
                threads = actuator_gauge(actuator, "jvm_threads_live_threads", {"application": "agent-group"})
        gc_max = actuator_gauge(actuator, "jvm_gc_pause_seconds_max", {})
        if gc_max is not None:
            gc_pause_max.append(gc_max)
        if active is not None:
            hikari_active.append(active)
        if pending is not None:
            hikari_pending.append(pending)
        if threads is not None:
            jvm_threads.append(threads)
        time.sleep(interval)

    def stats(vals: list[float]) -> dict:
        if not vals:
            return {}
        return {
            "max": max(vals),
            "avg": round(statistics.mean(vals), 2),
            "samples": len(vals),
        }

    return {
        "hikari_active": stats(hikari_active),
        "hikari_pending": stats(hikari_pending),
        "jvm_live_threads": stats(jvm_threads),
        "jvm_gc_pause_seconds_max": stats(gc_pause_max),
        "data_source": "prometheus+actuator_fallback" if hikari_active else "actuator_only",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prometheus", default="http://127.0.0.1:9090")
    parser.add_argument("--actuator", default="http://127.0.0.1:8080")
    parser.add_argument("--duration", type=int, default=65)
    args = parser.parse_args()

    print(f"采样 {args.duration}s ...")
    data = sample_loop(args.prometheus, args.actuator, args.duration)
    data["prometheus"] = args.prometheus
    data["actuator"] = args.actuator
    data["sampled_at"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(data, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
