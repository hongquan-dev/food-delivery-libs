package com.fooddelivery.id.entity;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class IdUtils {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static String generate(EntityType type) {
        long epochSeconds = Instant.now().getEpochSecond();
        int sequence = COUNTER.incrementAndGet() % 1000;
        return String.format("%d%d%03d", epochSeconds, type.getCode(), sequence);
    }

    public static EntityType decodeObjectType(String id) {
        validateId(id);
        int typeCode = Character.getNumericValue(id.charAt(10));
        return EntityType.fromCode(typeCode);
    }

    public static int decodeObjectTypeCode(String id) {
        validateId(id);
        int typeCode = Character.getNumericValue(id.charAt(10));
        EntityType.fromCode(typeCode);
        return typeCode;
    }

    public static long decodeTimestamp(String id) {
        validateId(id);
        String epochSecondsStr = id.substring(0, 10);
        return Long.parseLong(epochSecondsStr);
    }

    private static void validateId(String id) {
        if (id == null || id.length() != 14 || !id.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid numeric ID format (expected 14 digits): " + id);
        }
    }
}