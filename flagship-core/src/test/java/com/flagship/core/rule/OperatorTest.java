package com.flagship.core.rule;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperatorTest {

    @Test
    void equalsComparesNumbersAndText() {
        assertTrue(Operator.EQUALS.test("BD", List.of("BD")));
        assertTrue(Operator.EQUALS.test(5, List.of(5.0)));
        assertFalse(Operator.EQUALS.test("BD", List.of("US")));
    }

    @Test
    void inMatchesAnyListedValue() {
        assertTrue(Operator.IN.test("BD", List.of("US", "BD")));
        assertFalse(Operator.IN.test("JP", List.of("US", "BD")));
    }

    @Test
    void containsChecksForASubstring() {
        assertTrue(Operator.CONTAINS.test("alice@corp.com", List.of("@corp.com")));
        assertFalse(Operator.CONTAINS.test("alice@gmail.com", List.of("@corp.com")));
    }

    @Test
    void semverComparesNumbersNotText() {
        assertTrue(Operator.SEMVER_GT.test("1.10.0", List.of("1.9.0")));
        assertTrue(Operator.SEMVER_GTE.test("1.2.3", List.of("1.2.3")));
        assertTrue(Operator.SEMVER_LT.test("2.0.0-beta", List.of("2.0.0")));
        assertTrue(Operator.SEMVER_EQ.test("v1.2", List.of("1.2.0")));
        assertFalse(Operator.SEMVER_GT.test("not-a-version", List.of("1.0.0")));
    }

    @Test
    void regexFindsAPatternAndBadPatternsNeverMatch() {
        assertTrue(Operator.REGEX.test("user-1234", List.of("^user-\\d+$")));
        assertFalse(Operator.REGEX.test("admin-1", List.of("^user-\\d+$")));
        assertFalse(Operator.REGEX.test("anything", List.of("([unclosed")));
    }

    @Test
    void dateOperatorsAcceptFullTimestampsAndPlainDates() {
        assertTrue(Operator.DATE_BEFORE.test("2026-11-01T00:00:00Z", List.of("2026-12-01T00:00:00Z")));
        assertTrue(Operator.DATE_AFTER.test("2026-12-02", List.of("2026-12-01")));
        assertFalse(Operator.DATE_AFTER.test("garbage", List.of("2026-12-01")));
    }

    @Test
    void anOperatorWithNoValuesNeverMatches() {
        assertFalse(Operator.EQUALS.test("BD", List.of()));
        assertFalse(Operator.SEMVER_GT.test("1.0.0", List.of()));
    }
}
