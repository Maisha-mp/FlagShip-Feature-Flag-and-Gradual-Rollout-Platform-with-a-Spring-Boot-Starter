package com.flagship.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.FlagCodec;
import com.flagship.core.model.Environment;
import com.flagship.core.model.EnvironmentConfig;
import com.flagship.core.model.Flag;
import com.flagship.core.model.Rollout;
import com.flagship.core.model.Rule;
import com.flagship.core.model.Variation;
import com.flagship.server.entity.FlagEntity;
import com.flagship.server.entity.FlagEnvironmentConfig;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Server side of the translation: reads database entities and builds the core
 * model. The JSON parsing itself lives in FlagCodec (module flagship-codec),
 * which the starter shares.
 */
@Component
public class FlagConverter {

    private final FlagCodec codec;

    public FlagConverter(ObjectMapper mapper) {
        this.codec = new FlagCodec(mapper);
    }

    // ---------- entity -> core ----------

    public Flag toCoreFlag(FlagEntity flag, List<FlagEnvironmentConfig> configs) {
        Map<Environment, EnvironmentConfig> envs = new EnumMap<>(Environment.class);
        for (FlagEnvironmentConfig c : configs) {
            envs.put(c.getEnvironment(), toCoreConfig(c));
        }
        return new Flag(flag.getKey(), flag.getType(),
                variationsFromJson(flag.getVariationsJson()), envs);
    }

    public EnvironmentConfig toCoreConfig(FlagEnvironmentConfig e) {
        return new EnvironmentConfig(
                e.isKillSwitchOn(),
                e.getOffVariation(),
                e.getDefaultVariation(),
                userTargetsFromJson(e.getUserTargetsJson()),
                rulesFromJson(e.getRulesJson()),
                rolloutFromJson(e.getRolloutJson()));
    }

    // ---------- JSON helpers (delegate to the shared codec) ----------

    public Map<String, Variation> variationsFromJson(String json) {
        return codec.variationsFromJson(json);
    }

    public String variationsToJson(Map<String, Variation> variations) {
        return codec.variationsToJson(variations);
    }

    public Map<String, String> userTargetsFromJson(String json) {
        return codec.userTargetsFromJson(json);
    }

    public String userTargetsToJson(Map<String, String> targets) {
        return codec.userTargetsToJson(targets);
    }

    public Rollout rolloutFromJson(String json) {
        return codec.rolloutFromJson(json);
    }

    public String rolloutToJson(Rollout rollout) {
        return codec.rolloutToJson(rollout);
    }

    public List<Rule> rulesFromJson(String json) {
        return codec.rulesFromJson(json);
    }

    public String rulesToJson(List<Rule> rules) {
        return codec.rulesToJson(rules);
    }
}