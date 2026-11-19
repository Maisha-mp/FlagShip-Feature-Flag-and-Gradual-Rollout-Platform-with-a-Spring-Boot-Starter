package com.flagship.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import com.flagship.core.model.*;
import com.flagship.core.rule.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationSpeedTest {

    @Test
    void evaluatingAFlagWithTenRulesTakesUnder50MicrosecondsAtP99() {
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rules.add(new Rule("rule-" + i,
                    new AllOf(List.of(
                            new Condition("country", Operator.IN, List.of("US", "JP", "DE")),
                            new Condition("appVersion", Operator.SEMVER_GTE, List.of("1.0.0")),
                            new Condition("email", Operator.REGEX, List.of("^x" + i + ".*@corp\\.com$")))),
                    "on"));
        }
        EnvironmentConfig prod = new EnvironmentConfig(
                false, "off", "off", Map.of(), rules, new Rollout("on", 10));
        Flag flag = new Flag("speed-flag", FlagType.BOOLEAN,
                Map.of("on", new Variation("on", true), "off", new Variation("off", false)),
                Map.of(Environment.PRODUCTION, prod));

        // Every rule's first two conditions pass and the regex fails, so all 10 rules run fully.
        EvaluationContext ctx = EvaluationContext.builder("bob")
                .attribute("country", "US")
                .attribute("appVersion", "2.1.0")
                .attribute("email", "bob@gmail.com")
                .build();

        FlagEvaluator evaluator = new FlagEvaluator();

        for (int i = 0; i < 200_000; i++) {                       // warm-up
            evaluator.evaluate(flag, Environment.PRODUCTION, ctx);
        }

        int n = 100_000;
        long[] nanos = new long[n];
        for (int i = 0; i < n; i++) {
            long start = System.nanoTime();
            evaluator.evaluate(flag, Environment.PRODUCTION, ctx);
            nanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        long p99 = nanos[(int) (n * 0.99)];
        System.out.println("p99 evaluation time: " + p99 + " ns (limit 50000 ns)");

        assertTrue(p99 < 50_000, "p99 was " + p99 + " ns");
    }
}
