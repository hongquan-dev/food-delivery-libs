# common-web-lib

Thư viện chuẩn hóa tầng web cho các service Spring MVC: format response chung (`ApiResponse`), xử lý exception tập trung (`GlobalExceptionHandler`), auto-configuration.

> **Trạng thái: chưa có nội dung.** Module mới có `pom.xml`, chưa có class nào để dùng.

Lib **chưa có cấu hình properties nào**.

---

## Cài đặt

Các service trong dự án đã có lib này qua `common-lib`. Nếu muốn dùng trực tiếp:

```xml
<dependency>
    <groupId>com.fooddelivery</groupId>
    <artifactId>common-web-lib</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Dự kiến

- `ErrorResponse`: format lỗi chung (`code`, `message`, `traceId`, `path`, `timestamp`).
- `ErrorCode` và `BusinessException`: code nghiệp vụ chỉ cần `throw new BusinessException(ErrorCode.XXX)`.
- `GlobalExceptionHandler` (`@RestControllerAdvice`): bắt mọi exception và trả về cùng một format.

Khi thêm class hoặc cấu hình mới, cập nhật lại README này. Nếu có cấu hình properties, liệt kê đầy đủ theo dạng bảng như README của `common-api-flow-lib`.
