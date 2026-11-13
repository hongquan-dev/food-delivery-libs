# common-dto-lib

Thư viện chứa các model thuần dùng chung giữa các service: format response của API, enum và DTO trao đổi giữa các
service.

Lib là **Java thuần**, chỉ phụ thuộc `jackson-annotations`, không cần Spring. Vì vậy service không chạy Spring MVC (ví
dụ gateway chạy WebFlux) vẫn dùng được để trả lỗi cùng format với các service khác.

Lib **không có cấu hình properties nào**.

---

## Cài đặt

Các service Spring MVC đã có lib này qua `common-lib` (và qua `common-web-lib`). Nếu muốn dùng trực tiếp, ví dụ ở
gateway:

```xml
<dependency>
    <groupId>com.fooddelivery</groupId>
    <artifactId>common-dto-lib</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Nội dung

```
com.fooddelivery.dto
└── response/  ApiResponse       body JSON chung của mọi API, cả thành công lẫn lỗi
               ApiStatus         mã HTTP được phép dùng, kèm code và message mặc định của body lỗi
               FieldErrorDetail  một phần tử của "errors": field nào sai và vì sao
               PageMeta          thông tin phân trang trong "meta"
```

```java
import com.fooddelivery.dto.response.ApiResponse;
import com.fooddelivery.dto.response.ApiStatus;
import com.fooddelivery.dto.response.FieldErrorDetail;
import com.fooddelivery.dto.response.PageMeta;
```

Cách dùng các model này trong controller (`ResponseHandler`, `BusinessException`...) xem
[common-web-lib](../common-web-lib/README.md).

Ở service không có Spring MVC, tạo body bằng các factory của `ApiResponse` rồi gọi `withStatus` để điền `status`:

```java
ApiResponse<Void> body = ApiResponse.<Void>error("SERVICE_UNAVAILABLE", "Order service is unavailable", path)
        .withStatus(ApiStatus.SERVICE_UNAVAILABLE);
```

---

## Nên đặt gì vào đây

Chỉ đặt những gì **nhiều service cùng dùng** và **không chứa logic nghiệp vụ**, ví dụ:

- Enum dùng chung: `OrderStatus`, `PaymentMethod`, `UserRole`...
- DTO trao đổi giữa các service (request/response khi service này gọi service kia).

Không nên đặt vào đây:

- DTO chỉ một service dùng: để trong service đó.
- Entity JPA: mỗi service quản lý DB riêng.
- Class cần Spring hoặc servlet: để ở `common-web-lib`, nếu không lib sẽ mất tính Java thuần.

Khi thêm class mới, cập nhật lại README này.
