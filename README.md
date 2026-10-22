# food-delivery-libs

Các thư viện dùng chung cho các microservice của dự án Food Delivery.

## Danh sách thư viện

| Thư viện | Mô tả | Có cấu hình properties? | Trạng thái |
|---|---|---|---|
| [common-id-lib](common-id-lib/README.md) | Sinh và giải mã ID 14 chữ số theo thời gian | Không | Đang dùng |
| [common-dto-lib](common-dto-lib/README.md) | Enum, DTO, model phân trang dùng chung | Không | Chưa có nội dung |
| [common-web-lib](common-web-lib/README.md) | Format response chung: `ApiResponse` (body), `ResponseHandler` (trả response), `ApiStatus` (status) | Không | Đang dùng |
| [common-api-flow-lib](common-api-flow-lib/README.md) | Đăng ký API, check JSON Schema, gọi validator, giới hạn body. Phụ thuộc `common-web-lib` | **Có** (`api-flow.*`) | Đang dùng |

Yêu cầu: Java 21, Maven, Spring Boot 3.3.2 (version của các dependency Spring được quản lý chung qua `spring-boot-dependencies` trong `pom.xml` gốc).

---

## Các service dùng thư viện như thế nào

Các service không kéo từng lib riêng lẻ mà kéo qua `common-lib` (nằm trong project backend):

```
food-delivery-libs  ──(mvn install)──►  ~/.m2  ──►  common-lib  ──►  service-auth, service-order, ...
```

`common-lib/pom.xml` khai báo version chung qua property `food-delivery-libs.version`:

```xml
<properties>
    <food-delivery-libs.version>1.0.0-SNAPSHOT</food-delivery-libs.version>
</properties>

<dependency>
    <groupId>com.fooddelivery</groupId>
    <artifactId>common-api-flow-lib</artifactId>
    <version>${food-delivery-libs.version}</version>
</dependency>
```

---

## Build và cài đặt

### Trên máy cá nhân

```bash
# 1. Build và cài các lib vào ~/.m2
cd food-delivery-libs
mvn clean install

# Chỉ build lại một lib
mvn install -pl common-api-flow-lib

# 2. Cài lại common-lib (chỉ cần khi common-lib/pom.xml thay đổi, ví dụ thêm lib mới)
cd ../hahaha
mvn install -pl common-lib

# 3. Build lại service
mvn package -pl service-auth
```

Sau đó **reload Maven trong IDE** và restart service.

### Cho cả team / CI

`~/.m2` chỉ có trên máy bạn, máy khác không lấy được. Để cả team dùng chung, publish lib lên một repository chung:

- **JitPack** (đã khai báo sẵn trong `common-lib/pom.xml`): push lên GitHub, tạo tag, JitPack tự build.
- Hoặc **GitHub Packages / Nexus**: khai báo `distributionManagement` rồi chạy `mvn deploy`.

---

## Quản lý version

- **Đang phát triển:** dùng version `-SNAPSHOT` (hiện tại `1.0.0-SNAPSHOT`). Mỗi lần `mvn install` sẽ ghi đè bản cũ.
- **Khi ổn định:** release version cố định (ví dụ `1.0.0`). Các service dùng bản cố định để không bị ảnh hưởng khi lib thay đổi.
- Đổi version: sửa ở `pom.xml` gốc và `<parent>` của từng module, rồi sửa `food-delivery-libs.version` trong `common-lib/pom.xml`.

---

## Thêm thư viện mới

1. Tạo thư mục module, ví dụ `common-xxx-lib/`, với `pom.xml` có `<parent>` là `food-delivery-libs`.
2. Thêm `<module>common-xxx-lib</module>` vào `pom.xml` gốc.
3. Thêm version mapping vào `<dependencyManagement>` của `pom.xml` gốc.
4. Nếu lib cần tự tạo bean Spring: viết class `@AutoConfiguration` và khai báo trong `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Xem `common-api-flow-lib` làm mẫu.
5. Viết `README.md` cho lib. Nếu có cấu hình properties, liệt kê đầy đủ: tên, giá trị mặc định, ý nghĩa.
6. Thêm dependency vào `common-lib/pom.xml` và cập nhật bảng ở đầu file này.
