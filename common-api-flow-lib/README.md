# common-api-flow-lib

Thư viện xử lý luồng request API dùng chung cho các service Spring MVC. Lib giúp mỗi service:

- **Đăng ký API tập trung** trong file `api-routes.xml`. API nào không khai báo thì bị chặn.
- **Kiểm tra cấu trúc body** bằng JSON Schema: đủ field, đúng kiểu dữ liệu, đúng cấu trúc lồng nhau/mảng.
- **Gọi validator nghiệp vụ** của service, khai báo theo từng API.
- **Giới hạn kích thước body JSON** để không bị làm sập bằng body lớn.

Lib là một Spring Boot starter: thêm dependency là tự chạy, không cần viết thêm code cấu hình.

> Chỉ dùng cho service **Spring MVC (servlet)**. Service WebFlux (ví dụ Spring Cloud Gateway) sẽ tự bỏ qua lib.

---

## Luồng xử lý một request

```
Request
  │
  ▼
RequestCachingFilter      Body JSON: đọc và cache lại (tối đa max-body-size, vượt quá → 413)
  │                       Content type khác (multipart, form, file): đi thẳng qua
  ▼
Path có trong danh sách loại trừ? ── có ──► Controller (bỏ qua toàn bộ các bước dưới)
  │ không
  ▼
ApiFlowInterceptor
  (api-routes.xml đã được kiểm tra lúc khởi động, xem mục "Kiểm tra cấu hình lúc khởi động")
  1. Tìm route trong api-routes.xml theo method + path     không thấy → 404
  2. Có controller xử lý path này không?                   không có  → 404
  3. Route có <schema>: kiểm tra body theo JSON Schema     sai       → 400
  4. Route có <validator>: gọi hàm validate của service    trả false → 400
  ▼
Controller
```

---

## Cấu trúc package

```
com.fooddelivery.apiflow
├── autoconfigure/       Tự cấu hình Spring Boot
│   ├── ApiFlowAutoConfiguration      Khai báo các bean của lib
│   └── ApiFlowProperties             Cấu hình api-flow.*
├── route/               Quản lý route trong api-routes.xml
│   ├── ApiRegistry                   Đọc api-routes.xml, tìm route theo method + path
│   ├── RouteDefinition               Thông tin một route
│   ├── ApiRouteVerifier              Đối chiếu route với controller, resolve validator lúc khởi động
│   └── xml/ApiConfigWrapper          Model map file XML
├── validation/          Kiểm tra body
│   ├── JsonSchemaUtil                Kiểm tra body theo JSON Schema
│   └── ResolvedValidator             Gọi validator nghiệp vụ của service
├── exception/           Lỗi cấu hình lúc khởi động
│   ├── ApiFlowConfigException
│   └── ApiFlowConfigFailureAnalyzer  In lỗi dạng "APPLICATION FAILED TO START"
└── web/
    ├── filter/          RequestCachingFilter, CachedBodyHttpServletRequest (cache body JSON)
    └── interceptor/     ApiFlowInterceptor (route → schema → validator)
```

---

## Cài đặt

Các service trong dự án đã có lib này qua `common-lib`, không cần làm gì thêm.

Nếu một service muốn dùng trực tiếp:

```xml
<dependency>
    <groupId>com.fooddelivery</groupId>
    <artifactId>common-api-flow-lib</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Bắt đầu nhanh

Ví dụ thêm API `POST /orders/{id}/items` cho service order.

### Bước 1: Khai báo route trong `src/main/resources/api-routes.xml`

File này **bắt buộc** phải có ở mọi service dùng lib. Thiếu file thì service không khởi động được.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<routes>
    <route>
        <method>POST</method>
        <path>/orders/{id}/items</path>
        <schema>schemas/add-order-item.json</schema>
        <validator>com.fooddelivery.order.validation.OrderValidator.validateAddItem</validator>
        <controller>OrderController</controller>
    </route>

    <!-- API không cần kiểm tra body thì để trống schema và validator -->
    <route>
        <method>GET</method>
        <path>/orders/{id}</path>
        <schema></schema>
        <validator></validator>
        <controller>OrderController</controller>
    </route>
</routes>
```

| Thẻ | Bắt buộc | Ý nghĩa |
|---|---|---|
| `<method>` | Có | `GET`, `POST`, `PUT`, `DELETE`... |
| `<path>` | Có | Đường dẫn API, cùng cú pháp với `@GetMapping`/`@PostMapping` của Spring. Hỗ trợ biến (`{id}`, `{id:\d+}`) và wildcard (`*`, `**`). Nếu có cả `/orders/new` và `/orders/{id}` thì path cụ thể hơn được ưu tiên. |
| `<schema>` | Không | Đường dẫn file JSON Schema trong `resources`. Để trống nếu không cần kiểm tra cấu trúc body. |
| `<validator>` | Không | `<tên class đầy đủ>.<tên method>` của validator nghiệp vụ. Để trống nếu không cần. |
| `<controller>` | Không | Tên controller, chỉ để ghi chú cho người đọc. Lib tự tìm controller theo method và path. |

### Bước 2: Viết JSON Schema trong `src/main/resources/schemas/`

Schema chỉ dùng để kiểm tra **cấu trúc và kiểu dữ liệu**. Các rule về giá trị (độ dài, định dạng, nghiệp vụ) đặt trong validator.

```json
{
  "type": "object",
  "required": ["foodId", "quantity", "options"],
  "properties": {
    "foodId":   { "type": "integer" },
    "quantity": { "type": "integer" },
    "note":     { "type": ["string", "null"] },
    "options":  {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["name"],
        "properties": { "name": { "type": "string" } }
      }
    }
  }
}
```

Các keyword được hỗ trợ:

| Keyword | Ý nghĩa |
|---|---|
| `type` | `object`, `array`, `string`, `integer`, `number`, `boolean`, `null`. Có thể là mảng, ví dụ `["string", "null"]`. |
| `required` | Danh sách field bắt buộc phải có. |
| `properties` | Định nghĩa từng field, lồng nhau được không giới hạn cấp. |
| `items` | Schema áp dụng cho từng phần tử của mảng. Không khai báo thì phần tử nào cũng hợp lệ. |
| `additionalProperties` | **Mặc định là `false`**: body có field không khai báo trong `properties` thì bị chặn, ở mọi cấp. Đặt `true` để cho phép field lạ. |

Lưu ý:
- **Khác chuẩn JSON Schema:** chuẩn mặc định `additionalProperties` là `true`. Lib đặt mặc định `false` để body phải giống hệt cấu trúc schema.
- **Giá trị `null`** chỉ hợp lệ khi `type` của field có `"null"`.
- **Chưa hỗ trợ** `minLength`, `maxLength`, `pattern`, `format`, `enum`, `minimum`, `maximum`, `$ref`, `oneOf`... Các keyword này bị bỏ qua.

### Bước 3: Viết validator nghiệp vụ

Validator bắt buộc:
- Là Spring bean (`@Component`), nằm trong package được scan (package con của class `@SpringBootApplication`).
- Có method đúng chữ ký `public boolean tenHam(HttpServletRequest request, String requestBody)`.
- Trả `true` nếu hợp lệ, `false` nếu không.

```java
@Component
public class OrderValidator {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Cấu trúc và kiểu dữ liệu đã được kiểm tra qua schema, ở đây chỉ kiểm tra nghiệp vụ
    public boolean validateAddItem(HttpServletRequest request, String requestBody) throws Exception {
        JsonNode body = objectMapper.readTree(requestBody);
        return body.path("quantity").asInt() > 0;
    }
}
```

> Validator được tìm và kiểm tra ngay lúc khởi động. Sai tên class, sai tên method, sai chữ ký, hoặc class không phải bean thì service **không khởi động được** (xem mục [Kiểm tra cấu hình lúc khởi động](#kiểm-tra-cấu-hình-lúc-khởi-động)).

### Bước 4: Viết controller như bình thường

```java
@PostMapping("/orders/{id}/items")
public ResponseEntity<?> addItem(@PathVariable Long id, @RequestBody AddOrderItemRequest request) { ... }
```

Controller đọc `@RequestBody` bình thường, vì body đã được cache lại.

---

## Cấu hình (`application.properties`)

Mọi cấu hình đều có tiền tố `api-flow.` và đều **không bắt buộc**.

| Property | Mặc định | Ý nghĩa |
|---|---|---|
| `api-flow.routes-location` | `api-routes.xml` | Đường dẫn file route trong classpath. |
| `api-flow.max-body-size` | `1MB` | Kích thước tối đa của body JSON. Vượt quá trả **413**. Đơn vị: `B`, `KB`, `MB`, `GB`. |
| `api-flow.exclude-paths` | (trống) | Các path bỏ qua toàn bộ luồng (không check route, schema, validator). Ngăn cách bằng dấu phẩy, dùng cú pháp pattern của Spring. |
| `api-flow.use-default-excludes` | `true` | Đặt `false` để tắt danh sách loại trừ mặc định (xem bên dưới). |

Ví dụ:

```properties
api-flow.routes-location=api-routes.xml
api-flow.max-body-size=2MB
api-flow.exclude-paths=/auth/.well-known/**,/auth/oauth2/callback/**
#api-flow.use-default-excludes=false
```

### Danh sách loại trừ

Lib tự loại trừ sẵn các endpoint hệ thống, vì chúng không phải API nghiệp vụ và không cần khai báo trong `api-routes.xml`:

| Path | Dùng cho | Tắt được? |
|---|---|---|
| `/error` | Trang lỗi của Spring Boot | **Không** (tắt đi thì mọi lỗi đều thành 404) |
| `/actuator/**` | Spring Boot Actuator (health check, metrics) | Có |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Tài liệu API (springdoc/Swagger) | Có |
| `/favicon.ico` | Icon của trình duyệt | Có |

Danh sách cuối cùng = `/error` + danh sách mặc định (nếu `use-default-excludes=true`) + `exclude-paths`.

Khi khởi động, lib in danh sách cuối cùng ra log để kiểm tra:

```
API flow excluded paths: [/error, /actuator/**, /v3/api-docs/**, ..., /auth/.well-known/**]
```

Lưu ý:
- **Cú pháp pattern:** `/abc/**` khớp **cả** `/abc` lẫn mọi path bên dưới. `/abc/*` chỉ khớp một cấp bên dưới.
- **Không loại trừ quá rộng.** Ví dụ `/api/**` sẽ làm toàn bộ lớp kiểm tra vô dụng.
- **Loại trừ khỏi lib không có nghĩa là bảo mật.** Ví dụ `/actuator/env` hay `/actuator/heapdump` lộ cấu hình và bộ nhớ. Hãy giới hạn bằng `management.endpoints.web.exposure.include=health,prometheus` hoặc Spring Security.

---

## Response lỗi

Mọi lỗi do lib trả về đều có dạng JSON `{"error": "..."}`:

| HTTP | Message | Nguyên nhân |
|---|---|---|
| 404 | `API not found in system configuration!` | Method + path không có trong `api-routes.xml`. |
| 404 | `API not found in controller!` | Path có trong xml nhưng không có controller xử lý. |
| 400 | `Invalid request body!` | Body không đúng schema: thiếu field, sai kiểu, thừa field, không phải JSON, body rỗng. |
| 400 | `Request data failed validation!` | Validator trả `false`. |
| 413 | `Request body too large!` | Body JSON vượt `api-flow.max-body-size`. |

Lỗi 405 (sai method) và các lỗi khác do Spring tự trả theo format mặc định của Spring Boot.

Exception do validator tự ném ra được chuyển nguyên vẹn cho Spring xử lý (mặc định là 500).

---

## Kiểm tra cấu hình lúc khởi động

Lib kiểm tra `api-routes.xml` ngay khi service khởi động, **trước khi mở cổng nhận request**. Có lỗi thì service không khởi động và in toàn bộ lỗi một lần:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

The API routes config 'api-routes.xml' has 2 error(s):

    - [POST] /orders/{id}: duplicate route (same as /orders/{orderId})
    - [GET] /orders: validator class not found: com.fooddelivery.order.validation.OrderValidatr

Action:

Fix the routes above in 'api-routes.xml' (or the controllers/validators they point to). ...
```

| Kiểm tra | Khi sai |
|---|---|
| `<method>` là method HTTP hợp lệ (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`, `TRACE`) | Không khởi động |
| `<path>` không rỗng, bắt đầu bằng `/`, đúng cú pháp pattern | Không khởi động |
| Không có route trùng. `/items/{id}` và `/items/{itemId}` cùng method được coi là trùng | Không khởi động |
| File `<schema>` tồn tại và là JSON object | Không khởi động |
| Mỗi route có controller xử lý đúng method và path | Không khởi động |
| `<validator>`: đúng định dạng `class.method`, class tồn tại, là Spring bean (đúng 1 bean), có method `public boolean tenHam(HttpServletRequest, String)` | Không khởi động |
| Endpoint có trong controller nhưng **không** khai báo trong xml và không thuộc danh sách loại trừ | Chỉ **cảnh báo** trong log, vẫn khởi động |

Cảnh báo cho endpoint chưa khai báo có dạng:

```
WARN  ApiRouteVerifier - Controller endpoint [POST] /orders/{id} (OrderController#update) is not declared in api-routes.xml, its requests will get 404
```

Khởi động thành công thì log có dòng:

```
INFO  ApiRouteVerifier - API flow: 12 routes verified against controllers
```