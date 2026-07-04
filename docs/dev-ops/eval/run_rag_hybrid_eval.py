#!/usr/bin/env python3
"""RAG 向量-only vs 关键词 vs hybrid 离线对比评测。

用法（项目根目录）：
  python docs/dev-ops/eval/run_rag_hybrid_eval.py
  python docs/dev-ops/eval/run_rag_hybrid_eval.py --limit 15   # 标准集抽样，省 API

依赖：.env 中 AGENT_GROUP_LLM_API_KEY（或 DASHSCOPE_API_KEY）。
输出：docs/rag-vector-eval-report.json，并打印 Markdown 片段供更新 rag-eval-report.md。
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
STANDARD_CASES = (
    ROOT / "backend/agent-group-app/src/test/resources/rag-eval/rag-cases.json"
)
DRIFT_CASES = (
    ROOT / "backend/agent-group-app/src/test/resources/rag-eval/rag-drift-cases.json"
)
REPORT_FILE = ROOT / "docs" / "rag-vector-eval-report.json"

CHUNK_SIZE = 500
CHUNK_OVERLAP = 50
TOP_K = 3
VECTOR_TOP_K = 5


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key, value = key.strip(), value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def extract_terms(question: str) -> list[str]:
    parts = re.split(r"[\s,，。！？；;、]+", question.lower())
    terms = []
    for part in parts:
        part = part.strip()
        if len(part) >= 2 and part not in terms:
            terms.append(part)
        if len(terms) >= 8:
            break
    return terms


def split_chunks(text: str) -> list[str]:
    chunks: list[str] = []
    start = 0
    while start < len(text):
        end = min(len(text), start + CHUNK_SIZE)
        chunks.append(text[start:end])
        if end >= len(text):
            break
        start = max(start + 1, end - CHUNK_OVERLAP)
    return chunks


def keyword_retrieve(document: str, question: str) -> list[str]:
    terms = extract_terms(question)
    if not terms:
        return []
    scored: list[tuple[int, str]] = []
    for chunk in split_chunks(document):
        lower = chunk.lower()
        score = sum(1 for term in terms if term in lower)
        if score > 0:
            scored.append((score, chunk))
    scored.sort(key=lambda item: item[0], reverse=True)
    return [chunk for _, chunk in scored[:TOP_K]]


def first_matching_rank(hits: list[str], expected: list[str]) -> int:
    for idx, content in enumerate(hits[:TOP_K]):
        if all(snippet in content for snippet in expected):
            return idx + 1
    for idx, content in enumerate(hits[:TOP_K]):
        if any(snippet in content for snippet in expected):
            return idx + 1
    return -1


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


def embed_texts(texts: list[str], base_url: str, api_key: str, model: str) -> list[list[float]]:
    url = base_url.rstrip("/") + "/embeddings"
    body = json.dumps({"model": model, "input": texts}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    data = payload.get("data") or []
    data.sort(key=lambda item: item.get("index", 0))
    return [item["embedding"] for item in data]


def vector_retrieve(
    document: str,
    question: str,
    base_url: str,
    api_key: str,
    model: str,
    cache: dict[str, list[float]],
) -> list[str]:
    chunks = split_chunks(document)
    if not chunks:
        return []

    pending: list[tuple[str, str]] = []
    for chunk in chunks:
        key = "doc::" + chunk
        if key not in cache:
            pending.append((key, chunk))
    qkey = "q::" + question
    if qkey not in cache:
        pending.append((qkey, question))

    if pending:
        vectors = embed_texts([text for _, text in pending], base_url, api_key, model)
        for (key, _), vector in zip(pending, vectors):
            cache[key] = vector

    qvec = cache[qkey]
    scored: list[tuple[float, str]] = []
    for chunk in chunks:
        cvec = cache["doc::" + chunk]
        scored.append((cosine(qvec, cvec), chunk))
    scored.sort(key=lambda item: item[0], reverse=True)
    return [chunk for _, chunk in scored[:VECTOR_TOP_K]]


def hybrid_retrieve(document: str, question: str, vector_hits: list[str]) -> list[str]:
    keyword_hits = keyword_retrieve(document, question)
    merged: list[str] = []
    seen: set[str] = set()
    for hit in vector_hits + keyword_hits:
        if hit not in seen:
            seen.add(hit)
            merged.append(hit)
        if len(merged) >= TOP_K:
            break
    return merged


def eval_mode(cases: list[dict], mode: str, embed_cfg: dict | None) -> dict:
    cache: dict[str, list[float]] = {}
    top3 = 0
    mrr_sum = 0.0
    for case in cases:
        document = case["document"]
        question = case["question"]
        expected = case["expectedSnippets"]
        if mode == "keyword":
            hits = keyword_retrieve(document, question)
        elif mode == "vector":
            if embed_cfg is None:
                raise RuntimeError("vector 模式需要 embedding API")
            hits = vector_retrieve(
                document,
                question,
                embed_cfg["base_url"],
                embed_cfg["api_key"],
                embed_cfg["model"],
                cache,
            )
        else:
            if embed_cfg is None:
                hits = keyword_retrieve(document, question)
            else:
                vector_hits = vector_retrieve(
                    document,
                    question,
                    embed_cfg["base_url"],
                    embed_cfg["api_key"],
                    embed_cfg["model"],
                    cache,
                )
                hits = hybrid_retrieve(document, question, vector_hits)
        rank = first_matching_rank(hits, expected)
        if 0 < rank <= 3:
            top3 += 1
        if rank > 0:
            mrr_sum += 1.0 / rank
    n = len(cases)
    return {
        "cases": n,
        "top3_rate": round(top3 / n, 4) if n else 0.0,
        "top3_hits": top3,
        "mrr": round(mrr_sum / n, 4) if n else 0.0,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=0, help="标准集抽样数量，0=全部")
    args = parser.parse_args()

    load_dotenv(ROOT / ".env")
    api_key = os.getenv("AGENT_GROUP_LLM_API_KEY") or os.getenv("DASHSCOPE_API_KEY")
    if not api_key:
        print("缺少 AGENT_GROUP_LLM_API_KEY", file=sys.stderr)
        sys.exit(1)

    base_url = os.getenv(
        "AGENT_GROUP_LLM_BASE_URL",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
    )
    model = os.getenv("AGENT_GROUP_LLM_EMBEDDING_MODEL", "text-embedding-v3")
    embed_cfg = {"base_url": base_url, "api_key": api_key, "model": model}

    standard = json.loads(STANDARD_CASES.read_text(encoding="utf-8"))
    drift = json.loads(DRIFT_CASES.read_text(encoding="utf-8"))
    if args.limit > 0:
        standard = standard[: args.limit]

    started = time.perf_counter()
    print(f"标准集 {len(standard)} 组 + 漂移集 {len(drift)} 组，embedding={model}")

    standard_keyword = eval_mode(standard, "keyword", None)
    standard_vector = eval_mode(standard, "vector", embed_cfg)
    standard_hybrid = eval_mode(standard, "hybrid", embed_cfg)

    drift_keyword = eval_mode(drift, "keyword", None)
    drift_vector = eval_mode(drift, "vector", embed_cfg)
    drift_hybrid = eval_mode(drift, "hybrid", embed_cfg)

    elapsed_s = int(time.perf_counter() - started)
    report = {
        "date": time.strftime("%Y-%m-%d"),
        "embedding_model": model,
        "chunk": f"{CHUNK_SIZE}/{CHUNK_OVERLAP}",
        "top_k": TOP_K,
        "elapsed_seconds": elapsed_s,
        "standard_set": {
            "keyword_only": standard_keyword,
            "vector_only": standard_vector,
            "hybrid": standard_hybrid,
        },
        "drift_set": {
            "keyword_only": drift_keyword,
            "vector_only": drift_vector,
            "hybrid": drift_hybrid,
        },
    }
    REPORT_FILE.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    print(f"\n报告已写入 {REPORT_FILE}")


if __name__ == "__main__":
    main()
