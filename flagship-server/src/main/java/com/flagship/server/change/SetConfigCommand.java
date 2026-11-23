package com.flagship.server.change;

import com.flagship.server.entity.FlagEnvironmentConfig;

/**
 * Replaces one environment's whole configuration. The old state is remembered when the
 * command is created, so undo() can put it back. A restore is this same command with an
 * older version's state as the new state.
 */
public final class SetConfigCommand implements ChangeCommand {

    private final FlagEnvironmentConfig target;
    private final ConfigState before;
    private final ConfigState after;

    public SetConfigCommand(FlagEnvironmentConfig target, ConfigState after) {
        this.target = target;
        this.before = ConfigState.from(target);
        this.after = after;
    }

    @Override
    public void execute() {
        after.writeTo(target);
    }

    @Override
    public void undo() {
        before.writeTo(target);
    }

    @Override
    public String describe() {
        return "Set the " + target.getEnvironment() + " configuration";
    }

    @Override
    public FlagEnvironmentConfig target() {
        return target;
    }
}
