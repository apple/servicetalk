package io.servicetalk.utils.internal;

import org.junit.jupiter.api.Test;

import static java.util.concurrent.ThreadLocalRandom.current;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RandomUtilsTest {

    @Test
    void illegalArguments() {
        repeated(() -> {
            long lowerBound = current().nextLong(Long.MIN_VALUE + 1, Long.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () ->
                    RandomUtils.nextLongInclusive(lowerBound, lowerBound - 1));
        });
    }

    @Test
    void lowerEqualsUpperBound() {
        repeated(() -> {
            final long bound = current().nextLong();
            assertThat(RandomUtils.nextLongInclusive(bound, bound), equalTo(bound));
        });
    }

    @Test
    void longMaxValue() {
        repeated(() ->
            assertThat(RandomUtils.nextLongInclusive(Long.MAX_VALUE - 1, Long.MAX_VALUE),
                    anyOf(equalTo(Long.MAX_VALUE - 1), equalTo(Long.MAX_VALUE))));
    }

    @Test
    void longMinValue() {
        repeated(() ->
            assertThat(RandomUtils.nextLongInclusive(Long.MIN_VALUE, Long.MIN_VALUE + 1),
                    anyOf(equalTo(Long.MIN_VALUE), equalTo(Long.MIN_VALUE + 1))));
    }

    private static void repeated(Runnable r) {
        for (int i = 0; i < 100; i++) {
            r.run();
        }
    }
}
