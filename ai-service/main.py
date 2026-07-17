"""
AI Recommendation Service — khung sườn Sprint 0 (T-000-1b).

Chưa có logic nghiệp vụ (Pipeline 1/2 sẽ triển khai ở Sprint 7-8 theo
PRODUCT_BACKLOG.md). Mục tiêu Sprint 0: xác nhận service build/chạy được
trong Docker Compose, kết nối MongoDB + Redis thành công.

Base path "/v1/ai" khớp đúng API_CONTRACT.md §8.
"""

from datetime import datetime, timezone

from fastapi import FastAPI

from app.config import settings

app = FastAPI(title="JourneyAI — AI Recommendation Service", version="0.1.0")


@app.get("/v1/ai/ping")
def ping() -> dict:
    return {
        "service": "ai-service",
        "status": "UP",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


@app.get("/health")
def health() -> dict:
    """Endpoint riêng cho Docker healthcheck (không dùng /v1/ai/* để tránh
    lẫn với các route nghiệp vụ sẽ thêm sau)."""
    return {"status": "UP"}
