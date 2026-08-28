# Việt Khám Phá — BE MVP API contract

Tài liệu này mô tả contract đang được FE sử dụng sau khi hoàn thiện miền Tour MVP. Mọi request đi qua API Gateway tại `http://localhost:8090`.

## Quy tắc chung

- Endpoint quản trị `/v1/admin/**` yêu cầu JWT có claim `role=ADMIN`.
- Endpoint của khách hàng yêu cầu JWT hợp lệ; Gateway truyền `X-User-Id` từ claim `sub`, client không được tự gửi danh tính thay thế.
- `Idempotency-Key` bắt buộc khi tạo Booking và nên được dùng khi tạo Payment. Cùng key và cùng payload trả lại cùng tài nguyên; cùng key nhưng payload khác trả `409`.
- Tiền tệ của MVP là VND. BE tự tính giá và lưu commercial snapshot; không nhận tổng tiền do FE tính.
- `tourType`, `priceModel`, loại Booking và việc giữ chỗ được quyết định từ Tour/Departure trên BE.

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
- Khách sạn, xe, bữa ăn, vé và bảo hiểm là dữ liệu embedded trong package, không phải inventory độc lập.

### API

| Method | Path | Quyền | Ý nghĩa |
|---|---|---|---|
| `GET` | `/v1/tours` | Public | Tìm Tour; hỗ trợ `q`, `destination`, `minPrice`, `maxPrice`, `fromDate`, `toDate`, `tourType`, vị trí, sort và paging |
| `GET` | `/v1/tours/{tourId}` | Public | Chi tiết Tour đang hoạt động |
| `POST` | `/v1/admin/tours` | Admin | Tạo Tour package |
| `PUT` | `/v1/admin/tours/{tourId}` | Admin | Cập nhật Tour package |
| `DELETE` | `/v1/admin/tours/{tourId}` | Admin | Ngừng bán (soft delete) |
| `POST` | `/v1/admin/tours/reindex` | Admin | Đồng bộ lại Elasticsearch projection |
| `POST/GET/DELETE` | `/v1/admin/tour-guides[/{guideId}]` | Admin | Quản lý hồ sơ HDV |

Payload Tour đại diện:

```json
{
  "name": "TP.HCM - Huế 3N2Đ",
  "description": "Hành trình khám phá di sản Cố đô.",
  "destination": {
    "province": "Thừa Thiên Huế",
    "geo": { "lat": 16.4637, "lng": 107.5909 }
  },
  "basePrice": 2800000,
  "tourType": "GROUP",
  "priceModel": "PER_PERSON",
  "departureLocation": "TP.HCM",
  "meetingPoint": "Nhà Văn hóa Thanh Niên, Quận 1",
  "meetingTime": "05:30",
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
      "title": "TP.HCM - Huế",
      "activities": [{ "time": "05:30", "description": "Tập trung và khởi hành" }]
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
| `GET` | `/v1/payments/vnpay-return` | Public | Return URL sau thanh toán |

Cancellation event được xử lý qua inbox idempotent; bảng refund chỉ cho phép một refund trên mỗi payment. Không gọi API hoàn tiền từ FE.

## AI itinerary độc lập

| Method | Path | Quyền | Ý nghĩa |
|---|---|---|---|
| `POST` | `/v1/ai/itineraries` | Customer | Tạo và lưu lịch trình + dự toán |
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
  "groupProfile": "FAMILY",
  "preferences": ["di sản", "ẩm thực", "làng nghề"]
}
```

AI itinerary không có Departure, capacity, HDV hay payment. Khi Gemini không được cấu hình hoặc tạm lỗi, service trả phương án dự phòng có cấu trúc và vẫn lưu được.

## Kiểm tra xuyên service

Sau khi stack chạy, thực thi:

```powershell
./scripts/smoke-be-mvp.ps1
```

Script tạo dữ liệu có prefix `[SMOKE ...]`, kiểm tra toàn bộ luồng nhưng chỉ khởi tạo payment ở trạng thái `INITIATED`; không chuyển tiền và không gọi refund thật.
