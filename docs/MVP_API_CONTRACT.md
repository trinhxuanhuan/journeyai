# Việt Khám Phá — BE MVP API contract

Tài liệu này mô tả contract đang được FE sử dụng sau khi hoàn thiện miền Tour MVP. Mọi request đi qua API Gateway tại `http://localhost:8090`.

## Quy tắc chung

- Endpoint quản trị `/v1/admin/**` yêu cầu JWT có claim `role=ADMIN`.
- Endpoint của khách hàng yêu cầu JWT hợp lệ; Gateway truyền `X-User-Id` từ claim `sub`, client không được tự gửi danh tính thay thế.
- `Idempotency-Key` bắt buộc khi tạo Booking và nên được dùng khi tạo Payment. Cùng key và cùng payload trả lại cùng tài nguyên; cùng key nhưng payload khác trả `409`.
- Tiền tệ của MVP là VND. BE tự tính giá và lưu commercial snapshot; không nhận tổng tiền do FE tính.
- `tourType`, `priceModel`, loại Booking và việc giữ chỗ được quyết định từ Tour/Departure trên BE.

## Tài khoản và hồ sơ khách hàng

Danh tính đăng nhập do Auth Service sở hữu; thông tin mở rộng và sở thích do User Service sở hữu.
FE ghép hai response trong Account Center, không lấy email/họ tên từ JWT và không tự gửi `X-User-Id`.

| Method | Path | Quyền | Ý nghĩa |
|---|---|---|---|
| `GET` | `/v1/auth/me` | Customer/Admin | Danh tính hiện tại: email, họ tên, role, status và ngày tạo |
| `PATCH` | `/v1/auth/me` | Customer/Admin | Đổi họ tên; email là định danh đăng nhập chỉ đọc trong MVP |
| `GET` | `/v1/users/me` | Customer/Admin | Hồ sơ mở rộng: điện thoại, avatar và sở thích |
| `PATCH` | `/v1/users/me` | Customer/Admin | Cập nhật từng phần hồ sơ theo ngữ nghĩa PATCH |

Response danh tính:

```json
{
  "userId": "uuid",
  "email": "khach@example.com",
  "fullName": "Nguyễn Minh An",
  "role": "CUSTOMER",
  "status": "ACTIVE",
  "createdAt": "2026-09-01T08:00:00Z",
  "updatedAt": "2026-09-01T08:00:00Z"
}
```

Cập nhật danh tính:

```json
{ "fullName": "Nguyễn Minh An" }
```

Cập nhật hồ sơ mở rộng:

```json
{
  "phone": "0912345678",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "preferenceTags": [
    { "tagCode": "CULTURE", "weight": 1.0 },
    { "tagCode": "FOOD", "weight": 0.8 }
  ]
}
```

`phone` và `avatarUrl` bằng chuỗi rỗng nghĩa là xóa giá trị. Avatar MVP chỉ nhận URL HTTPS,
không nhận file upload. Tối đa 12 sở thích; `tagCode` được chuẩn hóa chữ hoa, không trùng trong
một hồ sơ và `weight` nằm trong khoảng `0.0..1.0`.

## Tour package

### Enum

| Trường | Giá trị |
|---|---|
| `tourType` | `GROUP`, `PRIVATE` |
| `priceModel` | `PER_PERSON`, `PER_GROUP` |
| `guideMode` | `INCLUDED`, `OPTIONAL`, `NONE` |

Ràng buộc MVP:

- `GROUP` chỉ dùng `PER_PERSON` và `INCLUDED`.
- `PRIVATE` dùng `PER_PERSON` hoặc `PER_GROUP`; không giữ shared capacity.
- Mỗi Tour chỉ có một `departureLocation`. Cùng hành trình nhưng khác nơi khởi hành phải tạo package Tour khác.
- `destination.name` là tên điểm đến du lịch hiển thị cho khách; `destination.province` là tỉnh/thành hành chính hiện hành. Dữ liệu cũ thiếu `name` được backfill từ `province`.
- Khách sạn, xe, bữa ăn, vé và bảo hiểm là dữ liệu embedded trong package, không phải inventory độc lập.

### API

| Method | Path | Quyền | Ý nghĩa |
|---|---|---|---|
| `GET` | `/v1/tours` | Public | Tìm Tour; `destination` khớp tên điểm đến hoặc tỉnh/thành; hỗ trợ `q`, giá, ngày, `tourType`, vị trí, sort và paging. Item trả thêm `destinationName` |
| `GET` | `/v1/tours/{tourId}` | Public | Chi tiết Tour đang hoạt động |
| `POST` | `/v1/admin/tours` | Admin | Tạo Tour package |
| `PUT` | `/v1/admin/tours/{tourId}` | Admin | Cập nhật Tour package |
| `DELETE` | `/v1/admin/tours/{tourId}` | Admin | Ngừng bán (soft delete) |
| `POST` | `/v1/admin/tours/reindex` | Admin | Đồng bộ lại Elasticsearch projection |
| `POST/GET/DELETE` | `/v1/admin/tour-guides[/{guideId}]` | Admin | Quản lý hồ sơ HDV |

Payload Tour đại diện:

```json
{
  "name": "Huế chậm một nhịp — Di sản cố đô 3N2Đ",
  "description": "Hành trình khám phá di sản Cố đô.",
  "destination": {
    "name": "Huế",
    "province": "Thành phố Huế",
    "geo": { "lat": 16.4637, "lng": 107.5909 }
  },
  "basePrice": 2800000,
  "tourType": "GROUP",
  "priceModel": "PER_PERSON",
  "departureLocation": "Huế",
  "meetingPoint": "Ga Huế, 02 Bùi Thị Xuân, phường Thuận Hóa",
  "meetingTime": "08:00",
  "minGroupSize": 1,
  "maxGroupSize": 30,
  "guideMode": "INCLUDED",
  "durationDays": 3,
  "durationNights": 2,
  "included": ["Xe du lịch", "Khách sạn", "Bữa ăn", "Vé tham quan", "HDV", "Bảo hiểm"],
  "excluded": ["Chi tiêu cá nhân", "Thuế VAT"],
  "packageDetails": {
    "accommodation": ["Khách sạn 3 sao, phòng đôi"],
    "transport": ["Xe du lịch theo chương trình"],
    "meals": ["Các bữa ăn ghi rõ trong lịch trình"],
    "tickets": ["Vé tham quan theo chương trình"],
    "insurance": ["Bảo hiểm du lịch nội địa"]
  },
  "childPolicy": {
    "description": "Trẻ em tính 70% giá người lớn",
    "pricePercentage": 70
  },
  "singleRoomSupplement": 500000,
  "cancellationPolicy": [
    { "minimumDaysBeforeDeparture": 15, "refundPercentage": 100 },
    { "minimumDaysBeforeDeparture": 7, "refundPercentage": 50 },
    { "minimumDaysBeforeDeparture": 0, "refundPercentage": 0 }
  ],
  "itinerary": [
    {
      "dayNumber": 1,
      "title": "Chạm Huế qua chợ Đông Ba và nhịp sống sông Hương",
      "activities": [{ "time": "08:00", "description": "Hướng dẫn viên đón đoàn tại Ga Huế và phổ biến lịch vận hành." }]
    }
  ]
}
```

## Departure của Tour ghép

Departure vẫn dùng bảng vật lý `tour_slots` để tránh migration phá vỡ dữ liệu, nhưng domain và API mới dùng tên Departure.

| Method | Path | Quyền | Ý nghĩa |
|---|---|---|---|
| `GET` | `/v1/tours/{tourId}/departures` | Public | Các Departure có thể hiển thị/bán |
| `POST` | `/v1/admin/tours/{tourId}/departures` | Admin | Tạo Departure cho Tour `GROUP` |
| `GET` | `/v1/admin/tours/{tourId}/departures` | Admin | Danh sách kể cả trạng thái vận hành |
| `PATCH` | `/v1/admin/departures/{departureId}` | Admin | Sửa ngày, capacity, HDV, giá override hoặc status |
| `POST` | `/v1/admin/departures/{departureId}/cancel` | Admin | Hủy lần khởi hành |
| `POST` | `/v1/admin/departures/{departureId}/complete` | Admin | Hoàn tất lần khởi hành |

Tạo Departure:

```json
{
  "startDate": "2026-10-10",
  "endDate": "2026-10-12",
  "capacity": 24,
  "guideId": "mongo-guide-id",
  "priceOverride": 3000000
}
```

Status lưu trữ: `OPEN`, `CLOSED`, `CANCELLED`, `COMPLETED`. Response có thể trả effective status `FULL` khi `availableSeats=0`; chỉ Departure `OPEN` và còn chỗ mới bookable.

Alias `/slots` và field `tourSlotId` vẫn hoạt động tạm thời để FE cũ không vỡ, nhưng đã deprecated.

## Booking

### Tour ghép

`POST /v1/bookings`, header `Idempotency-Key`:

```json
{
  "departureId": "uuid",
  "singleRoomCount": 1,
  "participants": [
    {
      "fullName": "Nguyễn Văn A",
      "phone": "0900000001",
      "primaryContact": true,
      "participantType": "ADULT"
    },
    {
      "fullName": "Nguyễn Bé An",
      "primaryContact": false,
      "participantType": "CHILD"
    }
  ]
}
```

BE khóa Departure, kiểm tra `OPEN`, capacity và HDV, sau đó giữ đúng số ghế trong 15 phút. Giá dùng `priceOverride` nếu có, nếu không dùng `basePrice`; trẻ em áp dụng `childPolicy.pricePercentage`.

### Tour riêng

```json
{
  "tourId": "mongo-tour-id",
  "requestedStartDate": "2026-10-10",
  "guideOptionSelected": true,
  "singleRoomCount": 0,
  "participants": [
    {
      "fullName": "Trần Minh Anh",
      "phone": "0910000001",
      "primaryContact": true,
      "participantType": "ADULT"
    },
    {
      "fullName": "Lê Hoàng Nam",
      "primaryContact": false,
      "participantType": "ADULT"
    }
  ]
}
```

Tour riêng không gửi `departureId` và không reserve shared capacity. HDV cụ thể được gán sau tại `PATCH /v1/admin/bookings/{bookingId}/guide` với `{ "guideId": "..." }`.

Các API còn lại:

| Method | Path | Quyền | Ý nghĩa |
|---|---|---|---|
| `GET` | `/v1/bookings/me` | Customer | Booking của người dùng |
| `GET` | `/v1/bookings/{bookingId}` | Chủ Booking | Chi tiết và commercial snapshot |
| `POST` | `/v1/bookings/{bookingId}/cancel` | Chủ Booking | Hủy theo policy snapshot |
| `GET` | `/v1/admin/bookings` | Admin | Lọc Booking theo Tour/ngày/status |
| `PATCH` | `/v1/admin/bookings/{bookingId}/guide` | Admin | Gán HDV cụ thể cho Booking PRIVATE |

Field `generatedItineraryId` trong request cũ được bỏ qua/deprecated. AI itinerary không gắn với Booking.

## Payment và refund

| Method | Path | Quyền | Ý nghĩa |
|---|---|---|---|
| `POST` | `/v1/payments` | Chủ Booking | Khởi tạo VNPay từ giá snapshot của Booking |
| `GET` | `/v1/payments/{paymentId}` | Chủ Payment | Đọc trạng thái payment |
| `GET` | `/v1/payments/webhooks/vnpay` | VNPay | IPN có kiểm tra chữ ký |
| `GET` | `/v1/payments/vnpay-return` | Public | Xác minh dữ liệu return và redirect `302` về FE `/thanh-toan/ket-qua`; FE vẫn đọc trạng thái chuẩn qua `GET /v1/payments/{paymentId}` |

Cancellation event được xử lý qua inbox idempotent; bảng refund chỉ cho phép một refund trên mỗi payment. Không gọi API hoàn tiền từ FE.

## Notification

Notification thuộc `notification-service` độc lập; mọi API đều yêu cầu JWT và chỉ truy cập dữ liệu của `X-User-Id`
do Gateway tin cậy tạo ra. Booking/Payment vẫn là nguồn sự thật; Notification chỉ lưu nội dung snapshot
để việc sửa Tour hoặc Booking sau đó không làm đổi thông báo đã gửi.

| Method | Path | Quyền | Ý nghĩa |
|---|---|---|---|
| `GET` | `/v1/notifications?status=ALL&page=0&size=20` | Customer | Danh sách thông báo; `status=ALL\|UNREAD\|READ`, `size` tối đa 100 |
| `GET` | `/v1/notifications/unread-count` | Customer | Số thông báo chưa đọc |
| `PATCH` | `/v1/notifications/{notificationId}/read` | Chủ thông báo | Đánh dấu đã đọc, gọi lặp vẫn an toàn |
| `PATCH` | `/v1/notifications/read-all` | Customer | Đánh dấu toàn bộ đã đọc |
| `GET` | `/v1/notifications/preferences` | Customer | Xem cấu hình nhận email |
| `PATCH` | `/v1/notifications/preferences` | Customer | Bật/tắt email với `{ "emailEnabled": true }` |

Response danh sách đại diện:

```json
{
  "content": [
    {
      "id": "uuid",
      "type": "BOOKING_CONFIRMED",
      "category": "PAYMENT",
      "title": "Đặt tour đã được xác nhận",
      "message": "Thanh toán thành công...",
      "actionUrl": "/bookings/uuid",
      "referenceType": "BOOKING",
      "referenceId": "uuid",
      "read": false,
      "readAt": null,
      "createdAt": "2026-08-28T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "unreadCount": 1
}
```

Các event khách hàng quan tâm gồm giữ chỗ, xác nhận/thất bại/hết hạn/hủy Booking, xử lý thanh
toán muộn, refund và nhắc khởi hành. Consumer dùng inbox có `eventId` để chống trùng và giữ event
chưa đủ dữ liệu để retry. `payment.succeeded/failed` không tạo thêm thông báo riêng vì trạng thái
Booking tương ứng là thông báo chuẩn, tránh gửi hai lần cho cùng một kết quả.

Service giữ recipient snapshot riêng từ `user.registered`; preference vẫn có thể cập nhật an toàn nếu
identity event đến muộn. Email chỉ tạo cho sự kiện quan trọng, tôn trọng preference và mặc định tắt ở local/CI. Delivery được
lưu bền vững, retry có backoff và trạng thái cuối `SENT | SKIPPED | FAILED`; Kafka consumer không gửi
SMTP trực tiếp. Nhắc khởi hành chạy theo múi giờ `Asia/Ho_Chi_Minh`, mặc định trước một ngày.

## AI itinerary độc lập

| Method | Path | Quyền | Ý nghĩa |
|---|---|---|---|
| `GET` | `/v1/ai/catalog` | Customer | Danh sách cụm điểm đến đã có dữ liệu kiểm duyệt |
| `POST` | `/v1/ai/itineraries` | Customer | Tạo và lưu lịch trình + dự toán |
| `POST` | `/v1/ai/itineraries/{id}/refine` | Chủ lịch trình | Chỉnh lịch trình bằng câu lệnh tự nhiên, có thể khóa ngày muốn giữ |
| `GET` | `/v1/ai/itineraries/me` | Customer | Danh sách đã lưu |
| `GET` | `/v1/ai/itineraries/{id}` | Chủ lịch trình | Xem chi tiết |
| `POST` | `/v1/ai/itineraries/{id}/share` | Chủ lịch trình | Bật link chia sẻ |
| `GET` | `/v1/ai/shared/{shareToken}` | Public | Xem bản chia sẻ, không lộ `userId` |

Payload tạo:

```json
{
  "destination": "Huế",
  "days": 3,
  "budget": 6000000,
  "travelerCount": 2,
  "childrenCount": 1,
  "seniorCount": 0,
  "groupProfile": "FAMILY",
  "preferences": ["di sản", "ẩm thực", "làng nghề"],
  "pace": "BALANCED",
  "transportPreference": "TAXI_RIDESHARE",
  "startDate": "2026-10-10"
}
```

`pace`: `RELAXED | BALANCED | ACTIVE`. `transportPreference`: `PUBLIC_TRANSPORT | TAXI_RIDESHARE | MOTORBIKE | PRIVATE_CAR | FLEXIBLE`.

Payload chỉnh sửa:

```json
{
  "instruction": "Làm ngày 2 nhẹ hơn, bỏ Đại Nội Huế và giảm ngân sách xuống 5 triệu",
  "lockedDayNumbers": [1]
}
```

Response V2 bổ sung lịch theo khung giờ, thời gian di chuyển, chi phí từng hoạt động,
`budgetAdjustments`, cảnh báo, giả định, `qualitySummary`, `plannerVersion`, `catalogVersion`,
`revision` và lịch sử chỉnh sửa.
Các ngày đã khóa được giữ nguyên. Link public không trả `userId` hoặc nội dung câu lệnh trong
`refinementHistory`.

AI itinerary không có Departure, capacity, HDV hay payment. Planner luôn lập lịch từ dữ liệu và
quy tắc trước; Gemini chỉ cá nhân hóa phần diễn đạt, không được sửa địa điểm, thời gian, tọa độ hay
chi phí. Khi Gemini không được cấu hình hoặc tạm lỗi, service vẫn trả kế hoạch grounded/rule-based.

## Kiểm tra xuyên service

Sau khi stack chạy, thực thi:

```powershell
./scripts/smoke-be-mvp.ps1
```

Script tạo dữ liệu có prefix `[SMOKE ...]`, kiểm tra toàn bộ luồng và Notification qua Kafka nhưng chỉ khởi tạo payment ở trạng thái `INITIATED`; không chuyển tiền và không gọi refund thật.
