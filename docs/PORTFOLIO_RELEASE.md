# Portfolio Release — Việt Khám Phá

## Phạm vi bản `v1.0.0-rc.1`

- Tour ghép theo Departure, capacity và HDV thực tế.
- Tour riêng giá xác định trước, không dùng shared inventory.
- Booking có participant, commercial snapshot, hold và idempotency.
- Thanh toán VNPay sandbox, refund idempotency và outbox/Kafka.
- Notification service độc lập với in-app inbox, email tùy chọn và nhắc khởi hành.
- AI itinerary độc lập, grounded, có dự toán, quality signals, refine và share.
- Next.js frontend responsive cho toàn bộ hành trình khách hàng.

## Checklist E2E trên staging

Ghi commit SHA, thời gian kiểm định và người kiểm định vào release note. Staging chạy image `sha-<commit>`; chỉ gắn tag SemVer khi tất cả mục dưới đây đạt.

1. Đăng ký bằng email kiểm thử, nhận OTP qua SMTP và đăng nhập.
2. Cập nhật tên, số điện thoại, avatar URL và sở thích; tải lại trang vẫn giữ dữ liệu.
3. Lọc theo điểm đến và ngày; mở một Tour ghép có Departure `OPEN`.
4. Tạo Booking Tour ghép, xác nhận số chỗ giảm đúng và retry không tạo booking trùng.
5. Mở VNPay sandbox, quay lại đúng trang kết quả và trạng thái được lấy từ backend.
6. Kiểm tra notification Booking/Payment, đánh dấu đã đọc và thay đổi email preference.
7. Tạo Tour riêng, xác nhận không trừ capacity của Departure Tour ghép.
8. Tạo AI itinerary, khóa một ngày, refine phần còn lại, lưu và mở link share khi đã logout.
9. Logout; access token cũ phải nhận `401` và refresh token cũ không tạo được phiên mới.
10. Kiểm tra desktop/mobile, bàn phím, loading/empty/error và không có lỗi nghiêm trọng trong console.

## Bằng chứng nên đưa vào portfolio

- Sơ đồ kiến trúc trong README của frontend.
- Ảnh trang khám phá, chi tiết tour, booking, notification và AI itinerary.
- Video 2–3 phút đi qua một luồng Tour ghép và một luồng AI.
- Link staging, hai repository, CI xanh và tag release.
- Một đoạn ngắn giải thích các quyết định: Departure-level capacity, snapshot giá, outbox/idempotency và AI tách khỏi Tour.

## Gợi ý nội dung CV

> Xây dựng Việt Khám Phá — nền tảng đặt tour Việt Nam và lập lịch trình AI với Next.js, Spring Boot/FastAPI, PostgreSQL, MongoDB, Redis, Kafka và Elasticsearch. Thiết kế booking theo Departure có khóa sức chứa, snapshot giá, payment idempotency/outbox, notification hướng sự kiện và AI itinerary độc lập; đóng gói Docker, CI và smoke test xuyên service.

Không ghi “production” khi mới có staging. Cách mô tả trung thực và mạnh hơn là “MVP release candidate triển khai công khai, có CI và kiểm thử E2E”.
