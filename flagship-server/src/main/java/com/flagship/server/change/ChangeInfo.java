package com.flagship.server.change;

/** Who made a change and why. */
public record ChangeInfo(String author, String reason) {

    /** Cleans up what the request sent: no author means "anonymous", no reason means "". */
    public static ChangeInfo of(String author, String reason) {
        String a = author == null || author.isBlank() ? "anonymous" : author.trim();
        String r = reason == null ? "" : reason.trim();
        if (a.length() > 100) {
            throw new IllegalArgumentException("author is longer than 100 characters");
        }
        if (r.length() > 500) {
            throw new IllegalArgumentException("reason is longer than 500 characters");
        }
        return new ChangeInfo(a, r);
    }

    public static ChangeInfo system(String reason) {
        return new ChangeInfo("system", reason);
    }

    /** Same author, but this reason when none was given. */
    public ChangeInfo withDefaultReason(String defaultReason) {
        return reason.isBlank() ? new ChangeInfo(author, defaultReason) : this;
    }
}