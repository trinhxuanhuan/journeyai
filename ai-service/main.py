"""AI Itinerary Service độc lập cho MVP Việt Khám Phá."""

from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI

from app.itineraries import ensure_indexes, router as itineraries_router


@asynccontextmanager
async def lifespan(_: FastAPI):
    await ensure_indexes()
    yield


app = FastAPI(title="Việt Khám Phá — AI Itinerary Service", version="0.2.0", lifespan=lifespan)
app.include_router(itineraries_router)


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
