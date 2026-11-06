package com.fooddelivery.id.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdUtilsTest {

    @Test
    void generateReturnsFourteenDigitIdOfTheGivenTypeAndCurrentTime() {
        long before = Instant.now().getEpochSecond();
        long id = IdUtils.generate(EntityType.ORDER);
        long after = Instant.now().getEpochSecond();

        assertThat(String.valueOf(id)).hasSize(14);
        assertThat(IdUtils.decodeObjectType(id)).isEqualTo(EntityType.ORDER);
        assertThat(IdUtils.decodeTimestamp(id)).isBetween(before, after);
    }

    @Test
    void generateGivesDifferentIdsInTheSameSecond() {
        long first = IdUtils.generate(EntityType.USER);
        long second = IdUtils.generate(EntityType.USER);

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void decodersReadEachPartOfTheId() {
        long id = 17290500004007L;

        assertThat(IdUtils.decodeObjectType(id)).isEqualTo(EntityType.ORDER);
        assertThat(IdUtils.decodeObjectTypeCode(id)).isEqualTo(4);
        assertThat(IdUtils.decodeTimestamp(id)).isEqualTo(1729050000L);
    }

    @Test
    void decodersRejectIdsThatAreNotFourteenDigits() {
        assertThatThrownBy(() -> IdUtils.decodeTimestamp(1729050000400L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdUtils.decodeObjectType(172905000040071L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdUtils.decodeObjectTypeCode(-17290500004007L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodersRejectUnknownTypeCode() {
        // 11th digit 0 is not an EntityType code
        assertThatThrownBy(() -> IdUtils.decodeObjectType(17290500000007L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdUtils.decodeObjectTypeCode(17290500000007L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
