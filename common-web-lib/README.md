# common-web-lib

Format chung cho mọi phản hồi API. Controller trả về qua `ResponseHandler`, có 2 hàm:

```java
// Thành công (2xx)
return ResponseHandler.generateResponse(status, body);
return ResponseHandler.generateResponse(status, headers, body);

// Lỗi (4xx, 5xx): tự tạo body lỗi, tự lấy path của request, code mặc định lấy theo status
return ResponseHandler.generateErrorResponse(status);
return ResponseHandler.generateErrorResponse(status, message);
return ResponseHandler.generateErrorResponse(status, errors);
return ResponseHandler.generateErrorResponse(status, message, errors);
return ResponseHandler.generateErrorResponse(status, code, message, errors);           // code riêng
return ResponseHandler.generateErrorResponse(status, headers, code, message, errors);  // kèm header
```

```
com.fooddelivery.web
├── handler/   ResponseHandler                         trả response: generateResponse, generateErrorResponse
├── model/     ApiResponse, FieldErrorDetail, PageMeta  cấu trúc body
└── status/    ApiStatus                               mã HTTP, kèm code và message mặc định
```

```java
import com.fooddelivery.web.handler.ResponseHandler;
import com.fooddelivery.web.model.ApiResponse;
import com.fooddelivery.web.model.FieldErrorDetail;
import com.fooddelivery.web.model.PageMeta;
import com.fooddelivery.web.status.ApiStatus;
```

```java
@GetMapping("/orders/{id}")
public ResponseEntity<ApiResponse<OrderDto>> getOrder(@PathVariable Long id) {
    OrderDto order = orderService.findById(id);
    if (order == null) {
        return ResponseHandler.generateErrorResponse(ApiStatus.NOT_FOUND, "Order " + id + " not found");
    }
    return ResponseHandler.generateResponse(ApiStatus.OK, ApiResponse.success(order));
}
```

- Kiểu trả về của method là `ResponseEntity<ApiResponse<T>>` của Spring. Luôn tạo qua `ResponseHandler`, không dùng `ResponseEntity.ok(...)` trực tiếp.
- **Mỗi hàm chỉ nhận đúng loại status của nó**: `generateResponse` chỉ nhận 2xx và body thành công, `generateErrorResponse` chỉ nhận 4xx/5xx. Dùng sai sẽ báo lỗi `IllegalArgumentException`.
- Field `status` trong body là mã HTTP của response, lib tự điền từ `ApiStatus` bạn truyền vào.
- Status dùng enum `ApiStatus` của lib (không dùng `HttpStatus` của Spring): `OK`, `CREATED`, `ACCEPTED`, `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `CONFLICT`, `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `UNPROCESSABLE_ENTITY`, `TOO_MANY_REQUESTS`, `INTERNAL_SERVER_ERROR`, `BAD_GATEWAY`, `SERVICE_UNAVAILABLE`, `GATEWAY_TIMEOUT`.

---

## Trả thành công: chọn status

### 200: `ApiStatus.OK`

Khi nào: lấy dữ liệu, cập nhật, xóa... Dùng nhiều nhất.

```java
return ResponseHandler.generateResponse(ApiStatus.OK, ApiResponse.success(order));
```

```json
// 200
{ "success": true, "status": 200, "code": "SUCCESS", "message": "Success", "data": { "id": 1, "food": "Món 1" }, "timestamp": "..." }
```

### 201: `ApiStatus.CREATED`

Khi nào: API tạo mới.

```java
return ResponseHandler.generateResponse(ApiStatus.CREATED, ApiResponse.success(order, "Order created"));
```

```json
// 201
{ "success": true, "status": 201, "code": "SUCCESS", "message": "Order created", "data": { ... }, "timestamp": "..." }
```

### 202: `ApiStatus.ACCEPTED`

Khi nào: đã nhận yêu cầu, xử lý nền sau (gửi email, xuất báo cáo).

```java
return ResponseHandler.generateResponse(ApiStatus.ACCEPTED, ApiResponse.success(job, "Report is being generated"));
```

```json
// 202
{ "success": true, "status": 202, "code": "SUCCESS", "message": "Report is being generated",
  "data": { "jobId": "report-001", "status": "PROCESSING" }, "timestamp": "..." }
```

### Status tùy điều kiện lúc chạy

Khi nào: ví dụ upsert, có rồi thì cập nhật (200), chưa có thì tạo mới (201).

```java
return ResponseHandler.generateResponse(exists ? ApiStatus.OK : ApiStatus.CREATED,
        ApiResponse.success(order, exists ? "Order updated" : "Order created"));
```

---

## Trả thành công: thêm header

Khi nào: cần trả thêm header, ví dụ `Location` cho API tạo mới, `Cache-Control`...

```java
HttpHeaders headers = new HttpHeaders();
headers.setLocation(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(order.id()).toUri());
return ResponseHandler.generateResponse(ApiStatus.CREATED, headers, ApiResponse.success(order, "Order created"));
```

```
// 201, header Location: http://localhost:8081/orders/6
{ "success": true, "status": 201, "code": "SUCCESS", "message": "Order created", "data": { ... }, "timestamp": "..." }
```

```java
HttpHeaders headers = new HttpHeaders();
headers.setCacheControl(CacheControl.maxAge(Duration.ofSeconds(60)));
headers.add("X-Data-Source", "cache");
return ResponseHandler.generateResponse(ApiStatus.OK, headers, ApiResponse.success(order));
```

```
// 200, header Cache-Control: max-age=60, X-Data-Source: cache
```

---

## Trả thành công: chọn body

### `ApiResponse.success(data)`

Khi nào: trả về dữ liệu (một đối tượng hoặc danh sách).

```java
return ResponseHandler.generateResponse(ApiStatus.OK, ApiResponse.success(order));
```

```json
{ "success": true, "status": 200, "code": "SUCCESS", "message": "Success", "data": { ... }, "timestamp": "..." }
```

Danh sách rỗng vẫn dùng hàm này: `ApiResponse.success(List.of())` → `"data": []`.

### `ApiResponse.success(data, message)`

Khi nào: trả dữ liệu kèm câu thông báo riêng.

```java
return ResponseHandler.generateResponse(ApiStatus.OK, ApiResponse.success(order, "Order updated"));
```

```json
{ "success": true, "status": 200, "code": "SUCCESS", "message": "Order updated", "data": { ... }, "timestamp": "..." }
```

### `ApiResponse.ok()`

Khi nào: không có dữ liệu trả về (xóa, đổi trạng thái...). Dùng thay cho 204.

```java
return ResponseHandler.generateResponse(ApiStatus.OK, ApiResponse.ok());
```

```json
{ "success": true, "status": 200, "code": "SUCCESS", "message": "Success", "data": null, "timestamp": "..." }
```

### `ApiResponse.page(items, PageMeta.of(page, size, total))`

Khi nào: danh sách có phân trang. Trang bắt đầu từ 0.

```java
return ResponseHandler.generateResponse(ApiStatus.OK,
        ApiResponse.page(result.getContent(), PageMeta.of(page, size, result.getTotalElements())));
```

```json
{ "success": true, "status": 200, "code": "SUCCESS", "message": "Success", "data": [ { ... }, { ... } ],
  "meta": { "page": 1, "size": 2, "totalElements": 5, "totalPages": 3 }, "timestamp": "..." }
```

---

## Trả lỗi: `generateErrorResponse`

Lib tự tạo body lỗi (`success: false`, `data: null`) và tự điền `path` là đường dẫn của request hiện tại.

Không truyền `code` thì lib lấy **code mặc định của status** trong `ApiStatus`. Không truyền `message` thì lấy message mặc định của status.

| `ApiStatus` | HTTP | `code` mặc định | `message` mặc định |
|---|---|---|---|
| `BAD_REQUEST` | 400 | `BAD_REQUEST` | Bad Request |
| `UNAUTHORIZED` | 401 | `UNAUTHORIZED` | Unauthorized |
| `FORBIDDEN` | 403 | `FORBIDDEN` | Forbidden |
| `NOT_FOUND` | 404 | `NOT_FOUND` | Not Found |
| `METHOD_NOT_ALLOWED` | 405 | `METHOD_NOT_ALLOWED` | Method Not Allowed |
| `CONFLICT` | 409 | `CONFLICT` | Conflict |
| `PAYLOAD_TOO_LARGE` | 413 | `PAYLOAD_TOO_LARGE` | Payload Too Large |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `UNSUPPORTED_MEDIA_TYPE` | Unsupported Media Type |
| `UNPROCESSABLE_ENTITY` | 422 | `UNPROCESSABLE_ENTITY` | Unprocessable Entity |
| `TOO_MANY_REQUESTS` | 429 | `TOO_MANY_REQUESTS` | Too Many Requests |
| `INTERNAL_SERVER_ERROR` | 500 | `INTERNAL_ERROR` | Internal Server Error |
| `BAD_GATEWAY` | 502 | `BAD_GATEWAY` | Bad Gateway |
| `SERVICE_UNAVAILABLE` | 503 | `SERVICE_UNAVAILABLE` | Service Unavailable |
| `GATEWAY_TIMEOUT` | 504 | `GATEWAY_TIMEOUT` | Gateway Timeout |

### `generateErrorResponse(status, message)`

Khi nào: dùng nhiều nhất, lỗi chung với câu thông báo riêng.

```java
return ResponseHandler.generateErrorResponse(ApiStatus.UNAUTHORIZED, "Token is invalid or expired");
```

```json
// 401
{ "success": false, "status": 401, "code": "UNAUTHORIZED", "message": "Token is invalid or expired",
  "data": null, "timestamp": "...", "path": "/orders/7" }
```

Chỉ cần message mặc định thì truyền mỗi status: `generateErrorResponse(ApiStatus.NOT_FOUND)` → `"code": "NOT_FOUND", "message": "Not Found"`.

### `generateErrorResponse(status, errors)`

Khi nào: chỉ cần báo lỗi theo field, dùng message mặc định của status.

```java
List<FieldErrorDetail> errors = List.of(
        new FieldErrorDetail("username", "is required"),
        new FieldErrorDetail("email", "is required"));
return ResponseHandler.generateErrorResponse(ApiStatus.BAD_REQUEST, errors);
```

```json
// 400
{ "success": false, "status": 400, "code": "BAD_REQUEST", "message": "Bad Request", "data": null,
  "errors": [ { "field": "username", "message": "is required" },
              { "field": "email", "message": "is required" } ],
  "timestamp": "...", "path": "/users" }
```

Lưu ý: `generateErrorResponse(status, null)` sẽ bị compiler báo mơ hồ (không biết `null` là message hay errors). Muốn message mặc định thì dùng `generateErrorResponse(status)`.

### `generateErrorResponse(status, message, errors)`

Khi nào: dữ liệu sai ở một hoặc nhiều field, để frontend hiện lỗi dưới từng ô.

```java
List<FieldErrorDetail> errors = List.of(
        new FieldErrorDetail("minPrice", "must not be negative"),
        new FieldErrorDetail("maxPrice", "must not be negative"));
return ResponseHandler.generateErrorResponse(ApiStatus.BAD_REQUEST, "Request data is invalid", errors);
```

```json
// 400
{ "success": false, "status": 400, "code": "BAD_REQUEST", "message": "Request data is invalid", "data": null,
  "errors": [ { "field": "minPrice", "message": "must not be negative" },
              { "field": "maxPrice", "message": "must not be negative" } ],
  "timestamp": "...", "path": "/orders" }
```

### `generateErrorResponse(status, code, message, errors)`

Khi nào: frontend cần một **mã nghiệp vụ riêng** để xử lý khác đi, thay cho code mặc định. Không có lỗi theo field thì truyền `errors` là `null`.

```java
return ResponseHandler.generateErrorResponse(ApiStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order 99 not found", null);
```

```json
// 404
{ "success": false, "status": 404, "code": "ORDER_NOT_FOUND", "message": "Order 99 not found",
  "data": null, "timestamp": "...", "path": "/orders/99" }
```

### `generateErrorResponse(status, headers, code, message, errors)`

Khi nào: lỗi cần kèm header, ví dụ `Retry-After` cho 429. `code` là `null` thì dùng code mặc định.

```java
HttpHeaders headers = new HttpHeaders();
headers.add(HttpHeaders.RETRY_AFTER, "30");
return ResponseHandler.generateErrorResponse(ApiStatus.TOO_MANY_REQUESTS, headers, null,
        "Too many requests, try again in 30 seconds", null);
```

```
// 429, header Retry-After: 30
{ "success": false, "status": 429, "code": "TOO_MANY_REQUESTS", "message": "Too many requests, try again in 30 seconds",
  "data": null, "timestamp": "...", "path": "/orders" }
```
