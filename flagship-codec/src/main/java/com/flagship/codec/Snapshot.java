package com.flagship.codec;

import com.flagship.core.model.Environment;
import com.flagship.core.model.Flag;
import java.time.Instant;
import java.util.Map;

/** All flags of one project for ONE environment, as downloaded from the server. */
public record Snapshot(
        String project,
        Environment environment,
        Instant generatedAt,
        Map<String, Flag> flags) {   // flag key -> flag

    public Snapshot {
        flags = Map.copyOf(flags);
    }

    /** The flag with this key, or null if the snapshot has none. */
    public Flag flag(String key) {
        return flags.get(key);
    }
}