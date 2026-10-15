package com.fooddelivery.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdUtilsTest {

    @Test
    void testGenerateAndDecodeSuccess() {
        // Given
        EntityType type = EntityType.ORDER;

        // When
        String id = IdUtils.generate(type);

        // Then - Basic structural checks
        assertThat(id).isNotNull();
        assertThat(id).hasSize(14);
        assertThat(id).matches("\\d+");

        // Then - Decoding checks matching IdUtils methods
        assertThat(IdUtils.decodeObjectType(id)).isEqualTo(type);
        assertThat(IdUtils.decodeObjectTypeCode(id)).isEqualTo(type.getCode());
        assertThat(IdUtils.decodeTimestamp(id)).isPositive();
        assertThat(IdUtils.decodeTimestamp(id)).isLessThanOrEqualTo(System.currentTimeMillis() / 1000);
    }

    @Test
    void testInvalidIdThrowsException() {
        // Null ID
        assertThatThrownBy(() -> IdUtils.decodeObjectType(null))
                .isInstanceOf(IllegalArgumentException.class);

        // Wrong length (too short)
        assertThatThrownBy(() -> IdUtils.decodeTimestamp("1234567890"))
                .isInstanceOf(IllegalArgumentException.class);

        // Wrong length (too long)
        assertThatThrownBy(() -> IdUtils.decodeObjectTypeCode("123456789012345"))
                .isInstanceOf(IllegalArgumentException.class);

        // Non-digit characters
        assertThatThrownBy(() -> IdUtils.decodeObjectType("1776330000X015"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}