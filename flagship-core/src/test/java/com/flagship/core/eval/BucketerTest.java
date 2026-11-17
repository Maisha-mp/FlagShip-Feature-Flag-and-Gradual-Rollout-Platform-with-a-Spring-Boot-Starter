package com.flagship.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BucketerTest {

    @Test
    void sameInputGivesSameBucket() {
        assertEquals(Bucketer.bucket("f", "user-1"), Bucketer.bucket("f", "user-1"));
    }

    @Test
    void bucketsStayInRange() {
        for (int i = 0; i < 100_000; i++) {
            int b = Bucketer.bucket("f", "user-" + i);
            assertTrue(b >= 0 && b < Bucketer.BUCKETS);
        }
    }

    @Test
    void tenPercentRolloutIsRoughlyTenPercent() {
        int in = 0;
        for (int i = 0; i < 100_000; i++) {
            if (Bucketer.inRollout("f", "user-" + i, 10)) in++;
        }
        assertTrue(in > 9_500 && in < 10_500, "got " + in);
    }

    @Test
    void rampingFrom10To20KeepsEveryoneWhoWasIn() {
        int stillIn = 0, first = 0;
        for (int i = 0; i < 100_000; i++) {
            String user = "user-" + i;
            if (Bucketer.inRollout("f", user, 10)) {
                first++;
                assertTrue(Bucketer.inRollout("f", user, 20), user + " dropped out!");
                stillIn++;
            }
        }
        assertEquals(first, stillIn);
    }
}