package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BackoffTest {

    private final Backoff backoff = new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(30));

    @Test
    void startsAtTheInitialDelay() {
        assertEquals(Duration.ofSeconds(1), backoff.delay(0));
        assertEquals(Duration.ofSeconds(1), backoff.delay(1));
    }

    @Test
    void doublesWithEachFailureInARow() {
        assertEquals(Duration.ofSeconds(2), backoff.delay(2));
        assertEquals(Duration.ofSeconds(4), backoff.delay(3));
        assertEquals(Duration.ofSeconds(8), backoff.delay(4));
        assertEquals(Duration.ofSeconds(16), backoff.delay(5));
    }

    @Test
    void neverExceedsTheMaximum() {
        assertEquals(Duration.ofSeconds(30), backoff.delay(6));
        assertEquals(Duration.ofSeconds(30), backoff.delay(100));
        assertEquals(Duration.ofSeconds(30), backoff.delay(Integer.MAX_VALUE));
    }

    @Test
    void nonsenseSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Backoff(Duration.ZERO, Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class,
                () -> new Backoff(Duration.ofSeconds(10), Duration.ofSeconds(5)));
    }
}