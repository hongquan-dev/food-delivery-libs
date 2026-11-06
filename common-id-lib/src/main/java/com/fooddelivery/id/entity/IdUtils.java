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
 * <p>IDs are {@code long} values, so they map directly to a {@code Long} entity ID and a {@code BIGINT} column, and
 * sorting by ID sorts by creation time. 14 digits stay below 2^53, so a JSON client such as JavaScript reads them
 * without rounding.
 *
 * <p>Limits: at most 1000 IDs per second per JVM (the sequence wraps after 999), and the counter is in memory, so
 * two instances of a service can generate the same ID in the same second.
 */
public class IdUtils {
    // Smallest and largest 14-digit numbers; decoders reject anything outside this range
    private static final long MIN_ID = 10_000_000_000_000L;
    private static final long MAX_ID = 99_999_999_999_999L;

    // Shared by all entity types; only its last 3 digits are used
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /**
     * Generates a new ID. Thread-safe.
     *
     * @param type kind of object the ID is for
     * @return a 14-digit ID, e.g. {@code ORDER -> 17290500004007}
     */
    public static long generate(EntityType type) {
        long epochSeconds = Instant.now().getEpochSecond();
        // floorMod keeps the sequence in 0-999 after the counter overflows to negative values
        int sequence = Math.floorMod(COUNTER.incrementAndGet(), 1000);
        return epochSeconds * 10_000 + type.getCode() * 1_000L + sequence;
    }

    /**
     * Reads the type of object an ID belongs to, from its 11th digit.
     *
     * @param id a 14-digit ID, e.g. {@code 17290500004007}
     * @return the type, e.g. {@link EntityType#ORDER}
     * @throws IllegalArgumentException if the ID is not 14 digits or its 11th digit is not a known type code
     */
    public static EntityType decodeObjectType(long id) {
        return EntityType.fromCode(typeDigit(id));
    }

    /**
     * Same as {@link #decodeObjectType} but returns the raw digit.
     *
     * @param id a 14-digit ID, e.g. {@code 17290500004007}
     * @return the type code, e.g. {@code 4}
     * @throws IllegalArgumentException if the ID is not 14 digits or its 11th digit is not a known type code
     */
    public static int decodeObjectTypeCode(long id) {
        int typeCode = typeDigit(id);
        // Only called for its exception: an unknown digit must fail here as in decodeObjectType
        EntityType.fromCode(typeCode);
        return typeCode;
    }

    /**
     * Reads the creation time of an ID, from its first 10 digits.
     *
     * @param id a 14-digit ID, e.g. {@code 17290500004007}
     * @return creation time in epoch seconds, e.g. {@code 1729050000}
     * @throws IllegalArgumentException if the ID is not 14 digits
     */
    public static long decodeTimestamp(long id) {
        validateId(id);
        return id / 10_000;
    }

    // 11th digit of a valid ID, e.g. 17290500004007 -> 4
    private static int typeDigit(long id) {
        validateId(id);
        return (int) (id / 1_000 % 10);
    }

    // Decoders accept only positive numbers of exactly 14 digits; anything else throws IllegalArgumentException
    private static void validateId(long id) {
        if (id < MIN_ID || id > MAX_ID) {
            throw new IllegalArgumentException("Invalid numeric ID format (expected 14 digits): " + id);
        }
    }
}
