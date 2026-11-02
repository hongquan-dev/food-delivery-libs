# food-delivery-libs

Multi-module Maven project gồm các thư viện dùng chung: `common-api-flow-lib`, `common-dto-lib`, `common-id-lib`, `common-web-lib`.

## Quy ước comment trong source code

Chuẩn tham chiếu là `common-api-flow-lib` (vd. `ApiRegistry`, `ApiFlowInterceptor`, `ApiRequest`, `RouteDefinition`).
Áp dụng cho **mọi module** khi viết code mới hoặc sửa code cũ. Khi sửa logic, phải cập nhật comment liên quan để không bị lệch với code.

### Nguyên tắc chung

- Comment viết bằng **tiếng Anh**, ngắn gọn, thì hiện tại, giọng mô tả ("Finds the route...", "Thrown at startup when...").
- Comment giải thích **tại sao / ràng buộc / hành vi không hiển nhiên**, không lặp lại điều code đã nói rõ.
- Luôn nói rõ ngữ nghĩa của `null`/rỗng khi có: "`null` if absent", "empty map if the path does not match", "`""` if the request has no JSON body".
- Phân biệt rõ thời điểm chạy: **at startup** (load/validate config) và **at request time**.
- Dùng `e.g.` để đưa ví dụ cụ thể, dùng `->` để mô tả ánh xạ: `/orders/{id} + /orders/7 -> {id=7}`.
- Dòng tối đa ~120 ký tự; xuống dòng tự nhiên, không căn lề kiểu hộp.
- KHÔNG dùng: `@author`, ngày tháng, changelog, banner phân đoạn (`// ===== ... =====`), code bị comment-out, comment cuối dòng (end-of-line), comment kiểu "// getter", "// constructor".

### 1. Class / interface / enum / record — Javadoc `/** */`

Mọi type public đều có Javadoc:

- **Câu/đoạn đầu**: nó là gì và vai trò trong luồng xử lý.
- **Đoạn `<p>` tiếp theo** (nếu cần): ai tạo ra nó, khi nào (startup / request time), cách hoạt động chính, đảm bảo (thread-safety, read-only, null policy), quan hệ với các class khác.
- Danh sách dùng `<ul><li>`, ví dụ cấu hình dùng `<pre>`.
- Record: dùng `@param` cho từng component.

```java
/**
 * In-memory registry of the routes declared in the routes file (default {@code classpath:api-routes.xml}). It is the
 * service's allow list: a request whose method + path is not found here is rejected with 404.
 *
 * <p>The constructor validates every {@code <route>}, preloads its JSON Schema and throws all problems at once as an
 * {@link ApiFlowConfigException}; checks that need the Spring context are left to {@link ApiRouteVerifier}.
 * Read-only after startup, so safe to share between request threads.
 */
```

```java
/**
 * Errors the API flow returns to the client at request time, as an {@code ApiResponse} (see common-web-lib).
 *
 * <ul>
 *   <li>{@link #API_NOT_FOUND} (404): method + path not declared in api-routes.xml, or no controller handles it.</li>
 *   <li>{@link #INVALID_REQUEST_BODY} (400): body missing, not JSON, or not matching the route's JSON Schema.</li>
 * </ul>
 */
```

```java
/**
 * Settings of the library, bound from {@code api-flow.*} in {@code application.properties}/{@code .yml}.
 * Every property is optional.
 *
 * <pre>
 * api-flow.routes-location=api-routes.xml          # classpath location of the routes file
 * api-flow.max-body-size=1MB                       # larger JSON bodies are rejected with 413
 * </pre>
 */
```

Exception class: nói rõ **khi nào bị throw** và **hệ quả** (vd. "so the application refuses to start").

### 2. Method

Chọn hình thức theo mức độ quan trọng:

| Loại method | Hình thức |
|---|---|
| Public API chính, có tham số/kết quả cần giải thích | Javadoc với `@param` / `@return` (căn thẳng tên param) |
| Public method có hành vi đặc biệt (exception, null, side effect) | Javadoc, câu đầu tóm tắt + đoạn `<p>` cho chi tiết |
| Method ngắn, private/package-private helper, override | Một/vài dòng `//` ngay trên method |
| Getter/setter/constructor hiển nhiên | Không comment |
| Getter có ngữ nghĩa không hiển nhiên (format, null, đơn vị) | Một dòng `//` ngay trên, kèm ví dụ |

```java
/**
 * Finds the route declared for a request.
 *
 * @param method HTTP method of the request (any case)
 * @param path   request URI without query string, e.g. {@code /orders/7/items}
 * @return the matching route, or {@code null} if the API is not declared
 */
public RouteDefinition getRoute(String method, String path) {
```

```java
// Returns true to continue to the controller, false after writing an error response
@Override
public boolean preHandle(...)
```

```java
// Body parsed as JSON, null if the body is empty or not valid JSON. Parsed once, on first call
public JsonNode getBodyJson() {
```

```java
// Replaces simple variables with "{}" so paths that differ only by variable name compare equal:
// /orders/{id} and /orders/{orderId} -> /orders/{}. Variables with a regex ({id:\d+}) and
// capture-all variables ({*rest}) are left as they are
static String normalizePattern(String path) {
```

Khi visibility hẹp hơn bình thường có chủ đích, giải thích lý do:

```java
// Package-private: only ApiRouteVerifier sets it, once, during startup
void setResolvedValidator(ResolvedValidator resolvedValidator) { ... }
```

### 3. Field / constant

Dùng `//` một dòng ngay trên field khi ý nghĩa không hiển nhiên: format của key, giá trị ví dụ, ý nghĩa của `null`, đơn vị, ai dùng nó.
Không comment field hiển nhiên (logger, dependency được inject, field trùng tên getter rõ nghĩa).

```java
// Key: "METHOD:/exact/path"
private final Map<String, RouteDefinition> exactRoutes = new HashMap<>();

// Raw "<full class name>.<method name>" from the XML, null if none
private final String validator;

// Lazily parsed body; bodyJsonParsed tells "not parsed yet" apart from "parsed, result is null"
private JsonNode bodyJson;
private boolean bodyJsonParsed;
```

### 4. Comment trong thân method

- Đặt **trên dòng riêng**, ngay trước khối code nó giải thích; không đặt cuối dòng.
- Chỉ dùng cho quyết định không hiển nhiên, trường hợp biên, hoặc lý do bỏ qua/cho phép điều gì.

```java
// Not a controller method (e.g. the static resource handler that Spring falls back to)
if (!(handler instanceof HandlerMethod)) {

// The body is only needed (and decoded) when a schema or a validator will look at it
String requestBody = "";

// Each check below records an error and skips the route, so one run reports every broken route
if (!HTTP_METHODS.contains(method)) {

// An empty <routes/> is allowed; ApiRouteVerifier will then report every controller endpoint as undeclared
if (config == null || config.getRoutes() == null) {
```

### 5. Định dạng

- Comment `//`: viết hoa chữ đầu, **không** chấm câu ở cuối dòng cuối. Nếu nhiều câu thì các câu giữa có dấu chấm. Comment dài thì tách thành nhiều dòng `//` liên tiếp.
- Javadoc: câu hoàn chỉnh, có dấu chấm cuối câu; đoạn mới bắt đầu bằng `<p>` trên dòng riêng sau một dòng ` *` trống.
- Trong Javadoc:
  - `{@code ...}` cho literal, tên property, XML tag (`{@code <schema>}`), mã lỗi, giá trị (`{@code null}`, `{@code true}`), path.
  - `{@link ...}` cho class/member mà file hiện tại có thể tham chiếu.
  - `{@code ClassName}` (không phải `{@link}`) cho class ở package/module khác mà không muốn tạo import chỉ vì comment.
  - HTTP status kèm mã lỗi: `404 {@code API_NOT_FOUND}`.
- Trong comment `//`: viết tên class/giá trị dạng text thường, chuỗi đặt trong `"..."`, không dùng tag Javadoc.
