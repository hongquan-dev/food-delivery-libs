# common-id-lib

Thư viện Java thuần (không phụ thuộc Spring) để sinh và giải mã ID dạng số 14 chữ số, sắp xếp được theo thời gian tạo.

Lib **không có cấu hình properties nào**.

---

## Cài đặt

Các service trong dự án đã có lib này qua `common-lib`. Nếu muốn dùng trực tiếp:

```xml
<dependency>
    <groupId>com.fooddelivery</groupId>
    <artifactId>common-id-lib</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Cấu trúc ID

```
1729050000   4   007
└────┬───┘   │   └┬┘
 epoch giây  │   số thứ tự (000–999)
 (10 số)     mã loại đối tượng (1 số)
```

- **10 số đầu:** thời điểm tạo (epoch seconds), nên ID tạo sau luôn lớn hơn ID tạo trước.
- **Số thứ 11:** mã loại đối tượng (`EntityType`).
- **3 số cuối:** số thứ tự tăng dần, quay vòng sau 999.

### Các loại đối tượng (`EntityType`)

| Enum | Mã | Tên tiếng Anh | Tên tiếng Việt |
|---|---|---|---|
| `USER` | 1 | User | Người dùng |
| `RESTAURANT` | 2 | Restaurant | Nhà hàng |
| `FOOD` | 3 | Food | Món ăn |
| `ORDER` | 4 | Order | Đơn hàng |
| `PAYMENT` | 5 | Payment | Thanh toán |

---

## Cách dùng

```java
import com.fooddelivery.id.entity.EntityType;
import com.fooddelivery.id.entity.IdUtils;

// Sinh ID
String orderId = IdUtils.generate(EntityType.ORDER);       // vd: "17290500004007"
        Long userId = Long.parseLong(IdUtils.generate(EntityType.USER));

        // Giải mã
        EntityType type = IdUtils.decodeObjectType(orderId);       // EntityType.ORDER
        int typeCode = IdUtils.decodeObjectTypeCode(orderId);    // 4
        long epochSec = IdUtils.decodeTimestamp(orderId);         // 1729050000

// Tra cứu EntityType
EntityType.

        fromCode(2);            // RESTAURANT
EntityType.

        fromName("Đơn hàng");   // ORDER (nhận tên enum, tên tiếng Anh hoặc tiếng Việt, không phân biệt hoa thường)
```

Các hàm giải mã ném `IllegalArgumentException` nếu ID không phải chuỗi đúng 14 chữ số, hoặc mã loại không tồn tại.

---

## Giới hạn cần biết

- **Tối đa 1000 ID mỗi giây trên một instance.** Số thứ tự quay vòng sau 999, nên sinh hơn 1000 ID trong cùng một giây sẽ bị trùng.
- **Có thể trùng giữa nhiều instance.** Bộ đếm nằm trong bộ nhớ của từng JVM. Hai instance cùng service sinh ID trong cùng một giây có thể ra cùng ID. Nếu chạy nhiều instance, cần bổ sung mã instance (kiểu Snowflake) hoặc để DB kiểm tra unique.
- **Chỉ có 9 mã loại** (1–9), vì mã loại chỉ chiếm 1 chữ số.
- **Thêm loại đối tượng mới:** thêm vào enum `EntityType` với mã chưa dùng, rồi build lại lib. Không được đổi mã của loại đã có, vì ID cũ sẽ bị giải mã sai.
