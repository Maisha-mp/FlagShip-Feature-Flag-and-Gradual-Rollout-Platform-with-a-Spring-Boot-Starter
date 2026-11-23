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
    private final CheckoutService checkoutService;

    public CheckoutController(FlagClient flags, CheckoutService checkoutService) {
        this.flags = flags;
        this.checkoutService = checkoutService;
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

    /** Same flag, but decided by the @FlagGuard annotation on CheckoutService. */
    @GetMapping("/checkout-guarded")
    public Map<String, Object> guardedCheckout(@RequestParam String user,
                                               @RequestParam(defaultValue = "US") String country) {
        EvaluationContext context = EvaluationContext.builder(user)
                .attribute("country", country)
                .build();

        return Map.of(
                "user", user,
                "country", country,
                "page", checkoutService.checkout(context));
    }
}