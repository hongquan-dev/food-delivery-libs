package com.fooddelivery.id.entity;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates and decodes 14-digit numeric IDs that sort by creation time. Plain Java, no Spring.
 *
 * <pre>
 * 1729050000 4 007
 * |          | └ sequence, 000-999
 * |          └ EntityType code, 1 digit
 * └ creation time, epoch seconds, 10 digits
 * </pre>
 *
 * <p>Limits: at most 1000 IDs per second per JVM (the sequence wraps after 999), and the counter is in memory, so
 * two instances of a service can generate the same ID in the same second.
 */
public class IdUtils {
    // Shared by all entity types; only its last 3 digits are used
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    // New ID for the given type, e.g. ORDER -> "17290500004007". Thread-safe
    public static String generate(EntityType type) {
        long epochSeconds = Instant.now().getEpochSecond();
        int sequence = COUNTER.incrementAndGet() % 1000;
        return String.format("%d%d%03d", epochSeconds, type.getCode(), sequence);
    }

    // Type stored in the 11th digit of the ID
    public static EntityType decodeObjectType(String id) {
        validateId(id);
        int typeCode = Character.getNumericValue(id.charAt(10));
        return EntityType.fromCode(typeCode);
    }

    // Same as decodeObjectType but returns the raw digit; still fails if the digit is not a known type
    public static int decodeObjectTypeCode(String id) {
        validateId(id);
        int typeCode = Character.getNumericValue(id.charAt(10));
        EntityType.fromCode(typeCode);
        return typeCode;
    }

    // Creation time of the ID, in epoch seconds (first 10 digits)
    public static long decodeTimestamp(String id) {
        validateId(id);
        String epochSecondsStr = id.substring(0, 10);
        return Long.parseLong(epochSecondsStr);
    }

    // Decoders accept only strings of exactly 14 digits; anything else throws IllegalArgumentException
    private static void validateId(String id) {
        if (id == null || id.length() != 14 || !id.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid numeric ID format (expected 14 digits): " + id);
        }
    }
}
