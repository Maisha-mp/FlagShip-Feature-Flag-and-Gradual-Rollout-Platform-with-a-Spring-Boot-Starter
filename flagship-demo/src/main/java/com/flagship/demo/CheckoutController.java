package com.flagship.demo;

import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.flagship.starter.FlagClient;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController {

    private final FlagClient flags;

    public CheckoutController(FlagClient flags) {
        this.flags = flags;
    }

    @GetMapping("/checkout")
    public Map<String, Object> checkout(@RequestParam String user,
                                        @RequestParam(defaultValue = "US") String country) {
        EvaluationContext context = EvaluationContext.builder(user)
                .attribute("country", country)
                .build();

        Optional<EvaluationResult> result = flags.evaluate("new-checkout", context);
        boolean newCheckout = flags.boolValue("new-checkout", context, false);

        return Map.of(
                "user", user,
                "country", country,
                "newCheckout", newCheckout,
                "reason", result.map(r -> r.reason().name()).orElse("FALLBACK"));
    }
}