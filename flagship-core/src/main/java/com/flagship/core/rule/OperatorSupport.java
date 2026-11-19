package com.flagship.core.rule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class OperatorSupport {

    private static final ConcurrentHashMap<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    private OperatorSupport() { }

    /** Numbers compare as numbers (5 equals 5.0); everything else compares as text. */
    static boolean same(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    /** True if the pattern is found anywhere in the text. A bad pattern never matches. */
    static boolean regex(Object actual, Object pattern) {
        try {
            Pattern p = PATTERNS.computeIfAbsent(String.valueOf(pattern), Pattern::compile);
            return p.matcher(String.valueOf(actual)).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    // ---- semantic versions: major.minor.patch[-prerelease][+build] ----

    private record Semver(int major, int minor, int patch, String pre) { }

    private static Semver parse(String raw) {
        String s = raw.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int plus = s.indexOf('+');
        if (plus >= 0) s = s.substring(0, plus);          // build metadata is ignored
        String pre = "";
        int dash = s.indexOf('-');
        if (dash >= 0) { pre = s.substring(dash + 1); s = s.substring(0, dash); }
        String[] p = s.split("\\.");
        if (p.length > 3) throw new NumberFormatException("bad version: " + raw);
        return new Semver(num(p, 0), num(p, 1), num(p, 2), pre);
    }

    private static int num(String[] parts, int i) {
        return i < parts.length ? Integer.parseInt(parts[i]) : 0;
    }

    private static int compareSemver(Object a, Object b) {
        Semver x = parse(String.valueOf(a));
        Semver y = parse(String.valueOf(b));
        int c = Integer.compare(x.major(), y.major());
        if (c != 0) return c;
        c = Integer.compare(x.minor(), y.minor());
        if (c != 0) return c;
        c = Integer.compare(x.patch(), y.patch());
        if (c != 0) return c;
        // same numbers: a release (no prerelease) is higher than a prerelease
        if (x.pre().equals(y.pre())) return 0;
        if (x.pre().isEmpty()) return 1;
        if (y.pre().isEmpty()) return -1;
        return x.pre().compareTo(y.pre());
    }

    /** Compares two versions and applies the check to the result; bad versions give false. */
    static boolean semver(Object actual, Object expected, IntPredicate check) {
        try {
            return check.test(compareSemver(actual, expected));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ---- dates: "2026-12-01T00:00:00Z" or just "2026-12-01" (UTC) ----

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) return i;
        String s = String.valueOf(o).trim();
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }

    static boolean date(Object actual, Object expected, boolean before) {
        try {
            Instant a = toInstant(actual);
            Instant e = toInstant(expected);
            return before ? a.isBefore(e) : a.isAfter(e);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }
}