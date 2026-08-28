# AI Planner V1 — Việt Khám Phá

## Mục tiêu

AI Planner là miền hành trình tự túc, độc lập với Tour, Departure, Booking và Payment. V1 ưu tiên
tạo kế hoạch có thể giải thích và kiểm tra được thay vì để mô hình ngôn ngữ tự quyết định toàn bộ.

Pipeline:

```text
Nhu cầu người dùng
  -> chuẩn hóa ràng buộc
  -> chọn địa điểm từ catalog kiểm duyệt
  -> phân bổ theo khu vực/nhịp độ
  -> ước tính di chuyển và chi phí
  -> kiểm tra ngân sách/đối tượng khách
  -> Gemini cá nhân hóa phần diễn đạt
  -> lưu revision và chia sẻ an toàn
```

Gemini không được thay `placeId`, thời gian, tọa độ hoặc chi phí do planner tạo. Nếu Gemini lỗi hoặc
không được cấu hình, hành trình grounded vẫn hoạt động.

## Dữ liệu V1

Catalog hiện bao phủ bốn cụm demo:

- Hà Nội – Ninh Bình;
- Đà Nẵng – Hội An;
- Đà Lạt;
- Huế.

Mỗi điểm có nhóm chủ đề, khu vực, thời lượng, chi phí người lớn/trẻ em tham khảo, tọa độ, bối cảnh
trong/ngoài trời, đối tượng phù hợp và lưu ý văn hóa. Dữ liệu không phải giá hay giờ mở cửa thời gian
thực. Điểm đến ngoài catalog vẫn nhận được khung hành trình nhưng được gắn `catalogCoverage=GENERIC`
và cảnh báo rõ ràng.

## Các ràng buộc được kiểm tra

- Đủ số ngày yêu cầu và không chồng lấn hoạt động trong một ngày.
- Phân bố địa điểm để tránh dùng hết dữ liệu trong những ngày đầu.
- Nhóm có trẻ em/người cao tuổi không được tự động xếp hoạt động không phù hợp.
- Xe máy được đổi sang taxi/xe công nghệ khi nhóm có trẻ em hoặc người cao tuổi.
- Chi phí gồm lưu trú, ăn uống, di chuyển nội vùng, hoạt động và dự phòng.
- Planner thử hạ hạng chi tiêu trước khi kết luận vượt ngân sách.
- Nếu chi phí nền vẫn đủ, planner chuyển hoạt động trả phí nhỏ nhất cần thiết thành lựa chọn bổ sung
  và công khai trong `budgetAdjustments` thay vì âm thầm sửa giá.
- Khi ngân sách thực sự không đủ, response trả `OVER_BUDGET`; không bóp méo giá để báo vừa ngân sách.
- Ngày được khóa khi refine phải giữ nguyên.

## Refinement V1

Endpoint refine hiểu một số ràng buộc tiếng Việt phổ biến:

- đổi nhịp độ toàn hành trình hoặc một ngày;
- giảm/thay đổi ngân sách;
- ưu tiên trẻ em, người cao tuổi, ẩm thực, văn hóa, thiên nhiên, làng nghề;
- bỏ một địa điểm có tên trong catalog;
- khóa các ngày không muốn thay đổi.

Mỗi lần chỉnh sửa tăng `revision` và lưu thay đổi đã áp dụng. Nội dung câu lệnh không xuất hiện ở
link chia sẻ công khai.

## Quality gates

Trước khi merge, tối thiểu phải đạt:

- unit test cho lịch, ngân sách, group constraints, catalog alias, refinement parser và AI guardrail;
- integration test `create -> refine -> share` với MongoDB;
- Docker image build thành công;
- CI của toàn repository xanh.

Các tình huống demo chuẩn:

1. Huế 3 ngày, 2 khách, có trẻ em, thích di sản/ẩm thực, ngân sách 6 triệu.
2. Đà Lạt 4 ngày, cặp đôi, nhịp thư giãn, ngân sách 5 triệu.
3. Đà Nẵng – Hội An 5 ngày, gia đình 3 người, ngân sách thấp để kiểm tra cảnh báo thiếu ngân sách.
4. Refine: “Làm ngày 2 nhẹ hơn, bỏ Đại Nội Huế và giảm ngân sách xuống 5 triệu”, khóa ngày 1.

## Sau V1

- Nguồn dữ liệu có quy trình kiểm duyệt và thời điểm cập nhật riêng cho từng địa điểm.
- Ma trận thời tiết/mùa vụ và khả năng thay thế hoạt động trong nhà.
- Routing theo API bản đồ và dữ liệu giao thông khi có ngân sách vận hành.
- Đánh giá tự động trên tập tình huống lớn hơn và thu thập feedback người dùng.
- Mở rộng catalog theo từng cụm điểm đến, không nhập dữ liệu toàn quốc một lần.
