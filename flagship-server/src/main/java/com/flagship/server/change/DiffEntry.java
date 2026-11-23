package com.flagship.server.change;

/** One difference between two versions. before or after is null when the thing did not exist. */
public record DiffEntry(String field, String kind, String before, String after) {

    public static final String ADDED = "ADDED";
    public static final String REMOVED = "REMOVED";
    public static final String CHANGED = "CHANGED";
}