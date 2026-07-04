#!/usr/bin/env python3
"""Deep 模式 LLM 端到端评测：固定 taskType=deep，每任务跑 N 次，统计成功率与耗时。

用法（项目根目录）：
  python docs/dev-ops/eval/run_deep_agent_eval.py --runs 3
  python docs/dev-ops/eval/run_deep_agent_eval.py --runs 1 --limit 3   #  smoke

依赖：后端已启动且 .env 中配置了 AGENT_GROUP_LLM_API_KEY。
"""

from __future__ import annotations

import argparse
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
TASKS_FILE = Path(__file__).resolve().parent / "deep-agent-tasks.json"
REPORT_FILE = ROOT / "docs" / "agent-eval-llm-report.json"


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or ":" in line and line.split(":", 1)[0].strip().isalpha() and "alipay" in line.lower():
            continue
        if "=" not in line:
            continue
        key, _, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if key and key not in os.environ:
            os.environ[key] = value


def post_json(url: str, body: dict, token: str | None = None, timeout: int = 300) -> str:
    data = json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json", "Accept": "text/event-stream"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", errors="replace")


def login(base: str) -> str:
    url = f"{base}/api/v1/auth/login"
    data = json.dumps({"username": "demo", "password": "123456"}).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=15) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    token = payload.get("data", {}).get("token") or payload.get("data", {}).get("accessToken")
    if not token:
        raise RuntimeError(f"登录失败: {payload}")
    return token


def parse_sse(raw: str) -> list[dict]:
    events = []
    for block in raw.split("\n\n"):
        for line in block.splitlines():
            if line.startswith("data:"):
                payload = line[5:].strip()
                if payload and payload != "[DONE]":
                    try:
                        events.append(json.loads(payload))
                    except json.JSONDecodeError:
                        pass
    return events


def evaluate_run(events: list[dict], success_keywords: list[str]) -> dict:
    types = []
    for e in events:
        t = e.get("event") or e.get("type") or e.get("eventType") or ""
        if t:
            types.append(t)
    texts = []
    for e in events:
        for key in ("content", "message", "text", "delta", "summary"):
            val = e.get(key)
            if isinstance(val, str):
                texts.append(val)
        data = e.get("data")
        if isinstance(data, dict):
            for key in ("content", "message", "text", "summary", "delta", "question"):
                val = data.get(key)
                if isinstance(val, str):
                    texts.append(val)
    merged = "\n".join(texts)
    has_plan = any(t in types for t in ("plan_delta", "execution_applied", "mode_selection", "task_analysis"))
    has_error = any(t in ("error", "run_error") for t in types) or any(
        isinstance(e.get("code"), str) and e.get("code") != "0000" for e in events if isinstance(e, dict)
    )
    keyword_hit = any(k.lower() in merged.lower() for k in success_keywords) if success_keywords else bool(merged.strip())
    success = (not has_error) and has_plan and (keyword_hit or len(merged) > 200)
    return {
        "success": success,
        "has_plan_events": has_plan,
        "keyword_hit": keyword_hit,
        "output_chars": len(merged),
        "event_types": sorted(set(types)),
    }


def run_task(base: str, token: str, task: dict, run_index: int) -> dict:
    session_id = f"AS{uuid.uuid4().hex[:12].upper()}"
    body = {
        "sessionId": session_id,
        "query": task["question"],
        "taskType": "deep",
        "webSearchEnabled": False,
    }
    url = f"{base}/api/v1/agent/stream"
    started = time.perf_counter()
    try:
        raw = post_json(url, body, token=token, timeout=600)
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        events = parse_sse(raw)
        metrics = evaluate_run(events, task.get("successKeywords", []))
        metrics["elapsed_ms"] = elapsed_ms
        metrics["error"] = None
        return metrics
    except Exception as e:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return {
            "success": False,
            "elapsed_ms": elapsed_ms,
            "error": str(e),
            "has_plan_events": False,
            "keyword_hit": False,
            "output_chars": 0,
            "event_types": [],
        }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=os.getenv("AGENT_EVAL_BASE_URL", "http://127.0.0.1:8080"))
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--limit", type=int, default=0, help="仅跑前 N 个任务，0=全部")
    args = parser.parse_args()

    load_dotenv(ROOT / ".env")
    if not os.getenv("AGENT_GROUP_LLM_API_KEY") and not os.getenv("DASHSCOPE_API_KEY"):
        print("缺少 AGENT_GROUP_LLM_API_KEY，请检查 .env", file=sys.stderr)
        sys.exit(1)

    tasks = json.loads(TASKS_FILE.read_text(encoding="utf-8"))
    if args.limit > 0:
        tasks = tasks[: args.limit]

    token = login(args.base)
    model = os.getenv("AGENT_GROUP_LLM_CHAT_MODEL", "qwen-plus")
    temperature = "0.2"

    results = []
    print(f"评测 {len(tasks)} 任务 × {args.runs} 次，model={model}")
    for task in tasks:
        runs = []
        for i in range(args.runs):
            print(f"  {task['taskId']} run {i + 1}/{args.runs} ...", flush=True)
            runs.append(run_task(args.base, token, task, i))
            time.sleep(2)
        success_rate = sum(1 for r in runs if r["success"]) / len(runs)
        latencies = [r["elapsed_ms"] for r in runs]
        results.append({
            "taskId": task["taskId"],
            "question": task["question"],
            "runs": runs,
            "success_rate": round(success_rate, 4),
            "avg_latency_ms": int(statistics.mean(latencies)),
            "p99_latency_ms": sorted(latencies)[min(len(latencies) - 1, int(len(latencies) * 0.99))],
        })

    overall_success = statistics.mean(r["success_rate"] for r in results)
    overall_latency = statistics.mean(r["avg_latency_ms"] for r in results)
    report = {
        "date": time.strftime("%Y-%m-%d"),
        "model": model,
        "temperature": temperature,
        "task_count": len(tasks),
        "runs_per_task": args.runs,
        "overall_success_rate": round(overall_success, 4),
        "overall_avg_latency_ms": int(overall_latency),
        "results": results,
    }
    REPORT_FILE.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "overall_success_rate": report["overall_success_rate"],
        "overall_avg_latency_ms": report["overall_avg_latency_ms"],
        "report": str(REPORT_FILE),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
