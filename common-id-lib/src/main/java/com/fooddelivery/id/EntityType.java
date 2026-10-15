package com.fooddelivery.id;

public enum EntityType {

    USER(1, "User", "Người dùng"),
    RESTAURANT(2, "Restaurant", "Nhà hàng"),
    FOOD(3, "Food", "Món ăn"),
    ORDER(4, "Order", "Đơn hàng"),
    PAYMENT(5, "Payment", "Thanh toán");

    private final int code;
    private final String displayName;
    private final String vietnameseName;

    EntityType(int code, String displayName, String vietnameseName) {
        this.code = code;
        this.displayName = displayName;
        this.vietnameseName = vietnameseName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVietnameseName() {
        return vietnameseName;
    }

    public static EntityType fromCode(int code) {
        for (EntityType type : EntityType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid entity type code: " + code);
    }

    public static EntityType fromName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Entity type name cannot be null or blank");
        }

        String trimmedName = name.trim();
        for (EntityType type : EntityType.values()) {
            if (type.name().equalsIgnoreCase(trimmedName) ||
                    type.displayName.equalsIgnoreCase(trimmedName) ||
                    type.vietnameseName.equalsIgnoreCase(trimmedName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid entity type name: " + name);
    }
}