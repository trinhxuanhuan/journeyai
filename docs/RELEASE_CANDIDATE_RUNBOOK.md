# Runbook kiểm định Release Candidate

Mục tiêu của runbook là chứng minh bản dựng có thể khởi động sạch và các luồng quan trọng hoạt động xuyên service trước khi gắn nhãn Release Candidate.

## 1. Quality gates tĩnh

```powershell
mvn clean verify
python -m pytest ai-service/tests
```

Nếu máy kiểm định chưa cài Python/pytest, chạy cùng bộ test bằng đúng runtime của image sau khi stack đã lên:

```powershell
docker compose exec -T ai-service python -m pytest tests
```

Mọi module phải build và toàn bộ test phải qua. Không bỏ qua test để tạo bản phát hành.

## 2. Dựng stack local

```powershell
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

Chỉ tiếp tục khi tất cả application service và hạ tầng hiển thị `healthy`. Gateway phụ thuộc healthcheck của các upstream cốt lõi nên không mở sớm trong lúc Auth/Tour/Booking vẫn đang khởi động.

## 3. Critical smoke flows

```powershell
./scripts/smoke-auth-account.ps1
./scripts/smoke-be-mvp.ps1
```

Gate Auth/Account phải qua đủ 8 bước: register, OTP, identity, Kafka profile, preferences, refresh rotation, logout và revocation. Gate BE MVP phải qua đủ 10 bước: GROUP/PRIVATE Tour, Departure capacity, pricing/idempotency, Payment snapshot, Notification qua Kafka và AI itinerary độc lập.

`smoke-auth-account.ps1` chỉ dành cho local Docker với `EMAIL_ENABLED=false` vì lấy OTP từ log của `jai-auth-service` mà không in OTP/token ra console. Khi staging bật SMTP, thực hiện OTP bằng hộp thư kiểm thử được quản lý thay vì đọc log.

Hai script tạo dữ liệu tổng hợp, không gọi thanh toán hay refund thật. Auth smoke giữ lại một tài khoản `@example.invalid`. BE smoke dọn Tour/Elasticsearch, vô hiệu hóa HDV vừa tạo nhưng giữ các snapshot Booking/Payment/Notification/AI phục vụ audit.

## 4. Điều kiện phát hành staging

- Secret JWT, SMTP, Gemini và VNPay sandbox được cấp qua secret manager; không commit `.env`.
- CORS và `FRONTEND_BASE_URL` trỏ đúng domain staging.
- Chạy lại quality gates và critical smoke trên artifact/commit sẽ deploy.
- Kiểm tra log không lộ token, mật khẩu, OTP hoặc dữ liệu khách thật.
- Lưu commit SHA, kết quả CI, thời điểm kiểm định và người phê duyệt trong release note.

Ngoài gate hiện tại: giao dịch VNPay thật, email SMTP thật, tải hiệu năng, backup/restore và diễn tập rollback phải được kiểm tra riêng trước production.
