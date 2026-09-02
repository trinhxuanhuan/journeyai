# Lộ trình hoàn thiện MVP

Thứ tự ưu tiên hiện tại nhằm giữ tiến độ xin intern nhưng không làm sản phẩm hời hợt:

1. **Đã hoàn thành — AI Planner V1**: grounded planner, budget/schedule validation, refinement, evaluation và contract.
2. **Đã hoàn thành — Notification BE MVP**: in-app notification, read state, preference, Kafka inbox chống trùng,
   email retry và nhắc khởi hành.
3. **Đã hoàn thành — FE integration**: đăng nhập, Tour/Departure, Booking/Payment, Notification Center,
   AI Planner timeline và trang chia sẻ.
4. **Đã hoàn thành — Account Center**: API danh tính/hồ sơ, menu tài khoản, chỉnh sửa hồ sơ và sở thích.
5. **Đang thực hiện — Release candidate**: đã có clean startup healthcheck, Auth/OTP/Account smoke bằng token thật,
   smoke xuyên service cho nghiệp vụ cốt lõi và catalog kiểm chứng; tiếp theo là deploy staging, observability và video demo.

Ngoài phạm vi trước khi hoàn thành năm mục trên: booking khách sạn/vé tự túc, realtime pricing,
catalog toàn quốc, vector database lớn, multi-agent và huấn luyện mô hình riêng.
