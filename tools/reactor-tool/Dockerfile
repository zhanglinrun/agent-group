FROM ghcr.io/astral-sh/uv:python3.11-bookworm-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONIOENCODING=utf-8 \
    UV_LINK_MODE=copy

WORKDIR /app

COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

COPY . .

ENV PATH="/app/.venv/bin:${PATH}" \
    ENV=prod \
    FILE_SAVE_PATH=/app/data/files \
    SQLITE_DB_PATH=/app/data/autobots.db \
    SQLITE_PATH=/app/data/mrag_sqlite.db

RUN mkdir -p /app/data/files /app/logs

EXPOSE 1601

CMD ["python", "server.py", "--host", "0.0.0.0", "--port", "1601", "--workers", "1"]
