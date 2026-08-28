"""
Cấu hình đọc từ biến môi trường (khớp docker-compose.yml).

JWT_SIGNING_SECRET: dùng để AI Service tự verify JWT độc lập, KHÔNG kế
thừa Spring Security filter chain của các service Java — quyết định đã
chốt ở AI_PIPELINE.md §2.3.1. Secret phải trùng với secret Auth Service
dùng để ký JWT (chia sẻ qua biến môi trường/secret manager, không hardcode
trong code).
"""

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    mongo_uri: str = os.getenv("MONGO_URI", "mongodb://localhost:27017")
    mongo_database: str = os.getenv("MONGO_DATABASE", "ai_service_db")
    redis_url: str = os.getenv("REDIS_URL", "redis://localhost:6379")
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    jwt_signing_secret: str = os.getenv("JWT_SIGNING_SECRET", "dev_only_change_me")
    tour_service_base_url: str = os.getenv("TOUR_SERVICE_BASE_URL", "http://tour-service:8080")
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    gemini_timeout_seconds: float = float(os.getenv("GEMINI_TIMEOUT_SECONDS", "20"))


settings = Settings()
