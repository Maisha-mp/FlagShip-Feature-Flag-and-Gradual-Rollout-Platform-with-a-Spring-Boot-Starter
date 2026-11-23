package com.flagship.server.change;

import com.flagship.server.entity.FlagEnvironmentConfig;

/** Command pattern: a change that can be applied and taken back. */
public interface ChangeCommand {

    /** Applies the change to the target. */
    void execute();

    /** Puts the target back the way it was before execute(). */
    void undo();

    String describe();

    /** The configuration this command changes. */
    FlagEnvironmentConfig target();
}