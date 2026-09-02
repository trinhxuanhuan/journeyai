# Triển khai staging

Tài liệu này mô tả một topology MVP có thể vận hành và đủ gọn cho portfolio:

```text
Trình duyệt -> Frontend HTTPS -> API Gateway HTTPS -> các service nội bộ
                                                -> PostgreSQL/MongoDB/Redis/Kafka/Elasticsearch
```

Frontend và backend vẫn là hai repository độc lập. Với quy mô MVP, backend có thể chạy trên một VPS bằng Docker Compose; không cần tách từng service sang một nền tảng trả phí riêng. Chỉ reverse proxy được phép truy cập Gateway qua loopback. Database, broker, cache và search tuyệt đối không công khai ra Internet.

## 1. Yêu cầu

- VPS AMD64 chạy Ubuntu 24.04 hoặc Debian, tối thiểu 4 vCPU và 8 GB RAM; 16 GB ổn định hơn khi Kafka và Elasticsearch cùng chạy.
- Frontend URL do Vercel cấp và một API hostname HTTPS. Chưa cần mua domain: dùng `api.<IPv4-dùng-dấu-gạch-ngang>.sslip.io`.
- SMTP test, Gemini API key và tài khoản VNPay sandbox.

Trên VPS mới, clone đúng release commit rồi chạy bootstrap. Script cài Docker Engine, Compose v2, Caddy, PowerShell 7, Git, UFW và chỉ mở SSH/HTTP/HTTPS:

```bash
apt-get update && apt-get install -y git
git clone https://github.com/trinhxuanhuan/journeyai.git /opt/viet-kham-pha/app
cd /opt/viet-kham-pha/app
git checkout <release-commit-sha>
bash ./scripts/install-staging-host.sh
```

## 2. Secret staging

Tạo file secret ngoài repository:

```bash
cp .env.staging.example /opt/viet-kham-pha/staging.env
chmod 600 /opt/viet-kham-pha/staging.env
```

Thay toàn bộ placeholder. `POSTGRES_PASSWORD` và `MONGO_PASSWORD` chỉ áp dụng tự động khi volume được khởi tạo lần đầu; thay biến môi trường không tự đổi mật khẩu của database đã tồn tại. Với môi trường cũ, phải rotate credential trong database trước rồi mới cập nhật URI service.

Kiểm tra file mà không in giá trị secret:

```bash
pwsh ./scripts/validate-staging-env.ps1 -EnvFile /opt/viet-kham-pha/staging.env
```

Preflight bắt buộc Gateway và hạ tầng bind `127.0.0.1`, frontend/CORS dùng cùng HTTPS origin, VNPay callback đúng route và không còn placeholder.

## 3. Dựng backend

```bash
docker compose --env-file /opt/viet-kham-pha/staging.env \
  -f docker-compose.yml -f docker-compose.release.yml pull
docker compose --env-file /opt/viet-kham-pha/staging.env \
  -f docker-compose.yml -f docker-compose.release.yml up -d --no-build
docker compose --env-file /opt/viet-kham-pha/staging.env \
  -f docker-compose.yml -f docker-compose.release.yml ps
```

Cấu hình Caddy sau khi thay IPv4 bằng dạng dấu gạch ngang:

```bash
bash ./scripts/configure-staging-caddy.sh api.203-0-113-10.sslip.io
```

Caddy tự cấp TLS và chỉ proxy API hostname tới `127.0.0.1:8090`. Không proxy trực tiếp bất kỳ application service hoặc cổng hạ tầng nào. Endpoint kiểm tra bên ngoài:

```bash
curl --fail --silent https://api.203-0-113-10.sslip.io/actuator/health
curl --fail --silent https://api.203-0-113-10.sslip.io/v1/ai/ping
```

## 4. Dữ liệu trình diễn

Đăng nhập tài khoản quản trị và import catalog đã kiểm chứng. Access token và file ánh xạ HDV chỉ tồn tại trên máy vận hành:

```powershell
$env:VKP_ADMIN_ACCESS_TOKEN = "<access-token>"
./scripts/import-verified-tour-catalog.ps1 `
  -BaseUrl https://api.203-0-113-10.sslip.io `
  -AdminAccessToken $env:VKP_ADMIN_ACCESS_TOKEN `
  -PublishDepartures `
  -GuideMapPath /opt/viet-kham-pha/guide-map.staging.json
```

Không dùng khách hàng thật cho demo. Dùng hộp thư kiểm thử do nhóm quản lý để xác nhận OTP và thông báo email.

## 5. Triển khai frontend

Build repository frontend với hai biến public tại thời điểm build:

```text
NEXT_PUBLIC_API_URL=https://api-staging.vietkhampha.vn
NEXT_PUBLIC_SITE_URL=https://staging.vietkhampha.vn
```

Dùng Vercel theo hướng dẫn trong `docs/VERCEL_STAGING.md` của repository frontend. Sau khi frontend lên, chạy checklist E2E trong `docs/PORTFOLIO_RELEASE.md` trên đúng URL staging.

## 6. Rollback và dữ liệu

- Lưu commit SHA và image digest của lần deploy đạt kiểm định.
- Sao lưu PostgreSQL và MongoDB trước mỗi lần nâng phiên bản có migration.
- Rollback application bằng cách đổi `RELEASE_VERSION` về image `sha-...` trước đó rồi `pull` và `up -d --no-build`; không tự động hạ schema.
- Migration hiện tại additive, nhưng vẫn phải đọc release note trước khi rollback qua nhiều phiên bản.
- Không xóa volume khi rollback. Lệnh `docker compose down -v` không được dùng trên staging.
