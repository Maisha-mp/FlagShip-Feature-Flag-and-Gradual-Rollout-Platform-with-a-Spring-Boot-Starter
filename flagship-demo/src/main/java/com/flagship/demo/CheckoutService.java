package com.flagship.demo;

import com.flagship.core.eval.EvaluationContext;
import com.flagship.starter.FlagGuard;
import org.springframework.stereotype.Service;

/**
 * Shows @FlagGuard: the method carries only an annotation, no "if" around the feature.
 * Flag on: checkout runs. Flag off: oldCheckout runs (same parameters, same return type).
 * The user is taken from the EvaluationContext parameter.
 */
@Service
public class CheckoutService {

    @FlagGuard(flag = "new-checkout", fallback = "oldCheckout")
    public String checkout(EvaluationContext context) {
        return "NEW checkout page for " + context.userKey();
    }

    public String oldCheckout(EvaluationContext context) {
        return "OLD checkout page for " + context.userKey();
    }
}