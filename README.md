# JourneyAI — Sprint 0 (Dev Environment Setup)

Khung sườn hạ tầng + service mẫu cho đồ án ĐATN "Xây dựng website quản lý và
đặt tour du lịch tích hợp gợi ý hành trình bằng AI cho Công ty Du lịch Việt
Khám Phá theo kiến trúc Microservices".

## Trạng thái Sprint 0

| Task | Trạng thái |
|---|---|
| `T-000-1` — Multi-module Maven (6 service Java) | ⏳ Chỉ có `auth-service` làm mẫu — 5 service còn lại thêm ở Sprint 1-6 |
| `T-000-1b` — FastAPI project cho `ai-service` | ✅ Khung sườn xong |
| `T-000-2` — Docker Compose hạ tầng | ✅ Xong (Postgres, MongoDB, Redis, Elasticsearch, Kafka) |
| `T-000-3` — GitHub Actions CI | ⏳ Chưa làm — bước tiếp theo |
| `T-000-4` — Zipkin container | ✅ Có trong docker-compose.yml (chưa tích hợp code, đúng scope Sprint 0) |

## Cách chạy

```bash
cp .env.example .env
# Điền JWT_SIGNING_SECRET (bất kỳ chuỗi random dài nào cho môi trường dev)

docker compose up -d --build
```

## Kiểm tra hạ tầng đã lên đúng chưa

```bash
docker compose ps
# Tất cả container phải ở trạng thái "healthy" hoặc "running"

curl http://localhost:8081/v1/auth/ping
# Kỳ vọng: {"service":"auth-service","status":"UP","timestamp":"..."}

curl http://localhost:8087/v1/ai/ping
# Kỳ vọng: {"service":"ai-service","status":"UP","timestamp":"..."}

curl http://localhost:9200
# Elasticsearch info

curl http://localhost:9411
# Zipkin UI (mở trình duyệt)
```

## Cấu trúc thư mục

```
journeyai/
├── docker-compose.yml          # Toàn bộ hạ tầng + service
├── pom.xml                     # Maven parent (multi-module)
├── infra/
│   └── postgres-init/          # Script tạo database-per-service
├── auth-service/                # Module Java mẫu — chuẩn hóa pattern
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/vietkhampha/authservice/
│       ├── AuthServiceApplication.java
│       ├── config/SecurityConfig.java
│       └── controller/PingController.java
└── ai-service/                  # Python/FastAPI — Polyglot (AI_PIPELINE.md §2)
    ├── requirements.txt
    ├── Dockerfile
    ├── main.py
    └── app/config.py
```

## Nguyên tắc nhân bản sang 5 service Java còn lại (Sprint 1-6)

Mỗi service Java mới (`user-service`, `tour-service`, `booking-service`,
`payment-service`, `notification-service`) nhân bản đúng cấu trúc
`auth-service`:

1. Copy `auth-service/pom.xml` → đổi `artifactId`, xóa dependency không cần
   (vd `tour-service` dùng MongoDB thay vì PostgreSQL — xem `ERD.md` §6).
2. Thêm `<module>xxx-service</module>` vào `pom.xml` gốc.
3. Copy `Dockerfile`, đổi đường dẫn `target/xxx-service.jar`.
4. Thêm service vào `docker-compose.yml`, đúng port đã quy ước (Gateway sẽ
   route theo Docker DNS — tên container = tên service).
5. Base path controller theo đúng `API_CONTRACT.md` (`/v1/users`, `/v1/tours`...).

## Việc còn lại trước khi coi Sprint 0 hoàn tất

- `T-000-3`: thêm `.github/workflows/ci.yml` (build + test tự động).
- Thêm `api-gateway` module (route request tới các service qua Docker DNS,
  JWT validation tập trung — `ARCHITECTURE.md` §4.1/§6.1).
