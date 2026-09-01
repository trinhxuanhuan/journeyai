# Runbook migration dữ liệu legacy

Booking, Payment và User trước đây dùng Hibernate để tạo schema nên database cũ có thể chưa có `flyway_schema_history`. Cấu hình mặc định giữ `baseline-on-migrate: false` để một schema lạ không bị Flyway âm thầm nhận là V1.

## Nguyên tắc

1. Backup PostgreSQL và MongoDB trước khi deploy.
2. Chạy preflight tương ứng. Chỉ tiếp tục nếu script trả fingerprint `LEGACY_V1_COMPATIBLE`, `PAYMENT_LEGACY_V1_COMPATIBLE` hoặc `USER_PROFILE_LEGACY_V1_COMPATIBLE`.
3. Chỉ ở lần chạy đầu tiên, bật `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` cho đúng service.
4. Xác nhận migration đã đến Booking `v6`, Payment `v3` và User `v2`, sau đó bỏ biến override. Các lần khởi động sau dùng cấu hình mặc định `false`.

## Docker Compose

Sao chép và chạy preflight:

```powershell
docker cp booking-service/src/main/resources/db/preflight/verify_legacy_v1.sql jai-postgres:/tmp/verify_booking_legacy_v1.sql
docker exec jai-postgres psql -v ON_ERROR_STOP=1 -U journeyai -d booking_service_db -f /tmp/verify_booking_legacy_v1.sql

docker cp payment-service/src/main/resources/db/preflight/verify_legacy_v1.sql jai-postgres:/tmp/verify_payment_legacy_v1.sql
docker exec jai-postgres psql -v ON_ERROR_STOP=1 -U journeyai -d payment_service_db -f /tmp/verify_payment_legacy_v1.sql

docker cp user-service/src/main/resources/db/preflight/verify_legacy_v1.sql jai-postgres:/tmp/verify_user_legacy_v1.sql
docker exec jai-postgres psql -v ON_ERROR_STOP=1 -U journeyai -d user_service_db -f /tmp/verify_user_legacy_v1.sql
```

Chạy migration một lần bằng container tạm:

```powershell
docker compose run -d --name jai-booking-migrator -e SPRING_FLYWAY_BASELINE_ON_MIGRATE=true booking-service
docker compose run -d --name jai-payment-migrator -e SPRING_FLYWAY_BASELINE_ON_MIGRATE=true payment-service
docker compose run -d --name jai-user-migrator -e SPRING_FLYWAY_BASELINE_ON_MIGRATE=true user-service

docker logs jai-booking-migrator
docker logs jai-payment-migrator
docker logs jai-user-migrator
```

Chỉ sau khi log báo `Successfully applied` và service đã start:

```powershell
docker stop jai-booking-migrator jai-payment-migrator jai-user-migrator
docker rm jai-booking-migrator jai-payment-migrator jai-user-migrator
docker compose up -d booking-service payment-service user-service
```

Database trống không cần baseline override: Flyway tự chạy từ V1.

## Ảnh hưởng dữ liệu

- Booking V5 giữ bảng `tour_slots`, thêm dữ liệu Departure và đóng các slot legacy đang `OPEN` nhưng chưa có HDV.
- Booking V6 backfill Tour/ngày/giá/snapshot cho Booking cũ; migration dừng nếu phát hiện Booking mồ côi không có slot.
- Participant cũ được backfill `ADULT`.
- Payment V3 dừng nếu đã tồn tại nhiều refund cho cùng một payment trước khi tạo unique index.
- User V2 dừng nếu dữ liệu legacy có số điện thoại, avatar, tag hoặc weight sai định dạng; migration chuẩn hóa tag sang chữ hoa và tạo unique constraint theo hồ sơ.
- Notification Service dùng database mới và chạy Flyway từ V1; không baseline vào `user_service_db`.
- Tour MongoDB được backfill additive và idempotent khi service khởi động; `tourGuideId` cũ vẫn được giữ để chuyển đổi vận hành dần.

Không chạy `flyway clean`, không xóa volume và không đổi tên vật lý bảng `tour_slots` trong giai đoạn MVP.
