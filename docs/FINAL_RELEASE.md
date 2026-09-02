# Final release — `v1.0.0-rc.1`

Đây là checklist đóng MVP thành một release candidate có thể kiểm chứng. Staging dùng image tag theo commit (`sha-xxxxxxxxxxxx`); chỉ tạo tag/GitHub Release `v1.0.0-rc.1` và ghi “deployed publicly” trong CV sau khi toàn bộ gate bắt buộc đạt.

## Kiến trúc triển khai đã chọn

- Frontend: Vercel Hobby, repository `journeyai-frontend`, HTTPS do nền tảng quản lý.
- Backend: một VPS AMD64 chạy Docker Compose; Caddy là public ingress duy nhất.
- Hostname API trước khi mua domain: `api.<IPv4-dùng-dấu-gạch-ngang>.sslip.io`.
- Database, Kafka, Redis, Elasticsearch, Zipkin và Gateway chỉ bind loopback.
- Application service chỉ hiện diện trong Docker network nội bộ.

Kiến trúc này tối ưu cho portfolio/staging, không giả vờ là production HA. Khi có người dùng thật cần bổ sung backup tự động, alerting, secret manager, replica và diễn tập khôi phục.

## Gate bắt buộc

| Gate | Bằng chứng | Trạng thái ban đầu |
| --- | --- | --- |
| CI của `main` xanh ở cả hai repository | Link workflow run | Chưa ghi nhận |
| Tám backend image có cùng tag | GitHub Packages | Chưa phát hành |
| FE và API có HTTPS công khai | Hai URL staging | Chưa triển khai |
| Public smoke 10/10 | Output `smoke-staging.ps1` | Chưa chạy trên staging |
| Auth/OTP/Account qua SMTP | Timestamp + email test | Chưa chạy trên staging |
| GROUP/PRIVATE/Payment/Notification/AI | Checklist E2E | Chưa chạy trên staging |
| Desktop/mobile và console sạch | Ảnh + video demo | Chưa ghi nhận |
| Release note có commit SHA | GitHub Release | Chưa tạo |

## Thứ tự phát hành

1. Merge PR release của BE và FE khi CI xanh.
2. Tạo tài khoản Vercel và import repository FE; chưa deploy production cho đến khi biết API hostname.
3. Tạo VPS Ubuntu 24.04 AMD64, tối thiểu 8 GB RAM; 16 GB giúp Kafka/Elasticsearch ổn định hơn khi build và demo.
4. Trên VPS, cài Git tối thiểu, clone backend tại đúng commit đã merge rồi chạy bootstrap. Script cài Docker, Caddy, PowerShell, firewall và swap cần thiết:

   ```bash
   apt-get update && apt-get install -y git
   git clone https://github.com/trinhxuanhuan/journeyai.git /opt/viet-kham-pha/app
   cd /opt/viet-kham-pha/app
   git checkout <BE_RELEASE_COMMIT_SHA>
   bash ./scripts/install-staging-host.sh
   cp .env.staging.example /opt/viet-kham-pha/staging.env
   chmod 600 /opt/viet-kham-pha/staging.env
   ```

   Thay toàn bộ placeholder trong `staging.env`; không gửi file này qua chat hoặc commit vào Git.
5. Lấy IPv4 của VPS, đặt API hostname theo mẫu `api.203-0-113-10.sslip.io`, rồi chạy `scripts/configure-staging-caddy.sh <hostname>`.
6. Đặt `NEXT_PUBLIC_API_URL` trên Vercel bằng API HTTPS vừa có; deploy để nhận URL FE ổn định.
7. Cập nhật `FRONTEND_BASE_URL`, hai CORS origin và VNPay return URL trong staging env.
8. Chạy thủ công workflow `Publish release images` tại đúng commit `main`. Workflow tạo tag image `sha-<12-ký-tự-commit>`; điền tag này vào `RELEASE_VERSION`. Đặt package visibility là public hoặc đăng nhập GHCR trên VPS bằng token chỉ có `read:packages`.
9. Trên VPS, validate và dựng đúng artifact:

   ```bash
   pwsh ./scripts/validate-staging-env.ps1 -EnvFile /opt/viet-kham-pha/staging.env
   docker compose --env-file /opt/viet-kham-pha/staging.env \
     -f docker-compose.yml -f docker-compose.release.yml pull
   docker compose --env-file /opt/viet-kham-pha/staging.env \
     -f docker-compose.yml -f docker-compose.release.yml up -d --no-build
   docker compose --env-file /opt/viet-kham-pha/staging.env \
     -f docker-compose.yml -f docker-compose.release.yml ps
   ```

10. Import catalog, chạy public smoke và checklist authenticated trong `PORTFOLIO_RELEASE.md`.
11. Chỉ khi tất cả gate đạt: tạo tag Git `v1.0.0-rc.1` ở đúng commit đã kiểm định. Workflow sẽ gắn thêm tag SemVer cho image; sau đó tạo GitHub prerelease, ảnh và video demo.

## Rollback

Giữ `RELEASE_VERSION` của bản đang chạy và bản trước đó. Khi application release lỗi, đổi lại tag trước rồi chạy `pull` và `up -d --no-build`. Không xóa volume và không rollback schema tự động. Sao lưu PostgreSQL/MongoDB trước mọi migration không còn additive.
