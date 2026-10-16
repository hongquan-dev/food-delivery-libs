# common-dto-lib

Thư viện chứa các model dùng chung giữa các service: enum, DTO cơ sở, model phân trang.

> **Trạng thái: chưa có nội dung.** Module mới có khung và một file `Main.java` giữ chỗ, chưa có class nào để dùng.

Lib **không có cấu hình properties nào**.

---

## Cài đặt

Các service trong dự án đã có lib này qua `common-lib`. Nếu muốn dùng trực tiếp:

```xml
<dependency>
    <groupId>com.fooddelivery</groupId>
    <artifactId>common-dto-lib</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Lib phụ thuộc `common-id-lib`, nên service dùng lib này cũng có luôn `common-id-lib`.

---

## Nên đặt gì vào đây

Chỉ đặt những gì **nhiều service cùng dùng** và **không chứa logic nghiệp vụ**, ví dụ:

- Enum dùng chung: `OrderStatus`, `PaymentMethod`...
- DTO trao đổi giữa các service (request/response khi service này gọi service kia).
- Model phân trang: `PageRequest`, `PageResponse<T>`.

Không nên đặt vào đây:

- DTO chỉ một service dùng: để trong service đó.
- Entity JPA: mỗi service quản lý DB riêng.

Khi thêm class mới, cập nhật lại README này.
