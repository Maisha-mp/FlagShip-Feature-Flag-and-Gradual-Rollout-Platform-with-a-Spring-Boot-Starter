package com.flagship.server.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flagship.core.model.Environment;
import com.flagship.server.entity.FlagEnvironmentConfig;
import org.junit.jupiter.api.Test;

class SetConfigCommandTest {

    private static FlagEnvironmentConfig config(boolean kill, String defaultVariation, String rollout) {
        FlagEnvironmentConfig c = new FlagEnvironmentConfig();
        c.setEnvironment(Environment.PRODUCTION);
        c.setKillSwitchOn(kill);
        c.setOffVariation("off");
        c.setDefaultVariation(defaultVariation);
        c.setUserTargetsJson("{}");
        c.setRulesJson("[]");
        c.setRolloutJson(rollout);
        return c;
    }

    @Test
    void executeWritesTheNewStateAndUndoPutsTheOldOneBack() {
        FlagEnvironmentConfig target = config(false, "off", null);
        ConfigState after = ConfigState.from(config(true, "on", "{\"variation\":\"on\",\"percent\":10}"));
        SetConfigCommand command = new SetConfigCommand(target, after);

        command.execute();
        assertTrue(target.isKillSwitchOn());
        assertEquals("on", target.getDefaultVariation());
        assertEquals("{\"variation\":\"on\",\"percent\":10}", target.getRolloutJson());

        command.undo();
        assertFalse(target.isKillSwitchOn());
        assertEquals("off", target.getDefaultVariation());
        assertNull(target.getRolloutJson());
    }

    @Test
    void describeNamesTheEnvironment() {
        SetConfigCommand command = new SetConfigCommand(config(false, "off", null),
                ConfigState.from(config(true, "off", null)));
        assertTrue(command.describe().contains("PRODUCTION"));
    }
}