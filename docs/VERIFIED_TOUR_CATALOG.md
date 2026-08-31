# Verified tour catalog

`catalog/verified-tour-catalog.v1.json` là catalog nội dung tour đầu tiên của Việt Khám Phá được quản lý bằng phiên bản. Catalog dùng trực tiếp contract `TourRequest`; không tạo thêm inventory khách sạn, phòng, chuyến bay hay vé tham quan.

## Điều gì đã được kiểm chứng

- Tên địa danh, tuyến tham quan và giá trị di sản được đối chiếu với UNESCO, Vietnam Tourism và văn bản Chính phủ được ghi ngay trong từng tour.
- `destination.name` là tên điểm đến mà khách du lịch nhận biết, ví dụ `Hà Giang`, `Hội An`, `Phú Quốc`.
- `destination.province` là đơn vị hành chính hiện hành tại ngày `verifiedAt`. Hai trường này cố ý tách nhau để không làm mất tên điểm đến sau thay đổi địa giới.
- Tọa độ chỉ phục vụ mở bản đồ và nhận biết điểm tham quan; không dùng làm dữ liệu dẫn đường hay cam kết vị trí đón chính xác.

Giá bán, khách sạn ở cấp tiêu chuẩn, thực đơn, hạn mức bảo hiểm và lịch khởi hành là dữ liệu vận hành của Việt Khám Phá. Đây không phải giá “chính thức” từ UNESCO hoặc cơ quan du lịch. Trước khi public, người vận hành phải ký/đối soát dịch vụ đầu vào, xác nhận thuế phí và cập nhật nội dung nếu điều kiện thực tế thay đổi.

## Quy tắc public

1. Tour chỉ được import khi người phụ trách sản phẩm đã duyệt giá và tiêu chuẩn package.
2. Lịch trong catalog có `publicationStatus: DRAFT`; script không tạo Departure theo mặc định.
3. Departure tour ghép chỉ được tạo khi có `guideId` thật. Không tạo tài khoản HDV giả để làm dữ liệu mẫu.
4. Tour riêng không có Departure và không dùng shared capacity.
5. Khi điểm đến đóng cửa, thời tiết xấu hoặc có phân luồng, vận hành có thể đổi thứ tự nhưng phải bảo toàn quyền lợi hoặc ghi nhận phương án thay thế với khách.

## Nhập catalog an toàn

Smoke test mặc định tự soft-deactivate các tour nó tạo. Chỉ dùng `-KeepTestData` khi cần điều tra thủ công và phải dọn lại sau đó.

Với database local đã có dữ liệu test từ các lần chạy cũ, luôn xem trước phạm vi:

```powershell
./scripts/cleanup-tour-test-data.ps1 `
  -BaseUrl 'http://localhost:8090' `
  -AdminAccessToken '<admin-token>' `
  -WhatIf
```

Chạy lại không có `-WhatIf` để soft-deactivate đúng các tour có tên theo chuẩn `[SMOKE yyyyMMdd-HHmmss]`. Thêm `-IncludeLegacySamples` chỉ khi muốn vô hiệu hóa ba tour mẫu cũ dùng ảnh `example.com`. Script không xóa cứng Tour, Booking, Payment hay dữ liệu khách hàng.

Xem trước thao tác, không ghi dữ liệu:

```powershell
./scripts/import-verified-tour-catalog.ps1 `
  -BaseUrl 'http://localhost:8090' `
  -AdminAccessToken '<admin-token>' `
  -WhatIf
```

Tạo hoặc cập nhật tour theo tên chính xác và yêu cầu reindex:

```powershell
./scripts/import-verified-tour-catalog.ps1 `
  -BaseUrl 'http://localhost:8090' `
  -AdminAccessToken '<admin-token>'
```

Chỉ sau khi lịch và HDV đã được duyệt, tạo file cục bộ không commit, ví dụ `guide-map.local.json`:

```json
{
  "NORTH": "guide-id-that-exists-in-tour-service",
  "CENTRAL": "another-real-guide-id"
}
```

Sau đó chạy:

```powershell
./scripts/import-verified-tour-catalog.ps1 `
  -BaseUrl 'http://localhost:8090' `
  -AdminAccessToken '<admin-token>' `
  -PublishDepartures `
  -GuideMapPath './guide-map.local.json'
```

Script idempotent ở mức nghiệp vụ: tour được upsert theo tên chính xác, trùng nhiều bản ghi sẽ dừng, Departure trùng `startDate` sẽ được bỏ qua. Tour đã `INACTIVE` không được tự kích hoạt lại.

## Bảo trì

- Chạy test contract của `tour-service` sau mọi chỉnh sửa catalog.
- Ghi lại ngày kiểm chứng mới khi nội dung nguồn đã được đối chiếu lại.
- Không commit token, guide map vận hành, hợp đồng nhà cung cấp hoặc thông tin cá nhân của HDV.
- Nên rà soát nội dung ít nhất mỗi quý và trước mỗi đợt mở bán lớn.
