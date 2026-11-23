package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flagship.core.eval.EvaluationContext;
import com.flagship.core.eval.EvaluationResult;
import com.flagship.core.eval.Reason;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class FlagGuardAspectTest {

    /** A pretend FlagClient: the test decides which flags are on, and it remembers who was asked about. */
    private static final class FakeFlags implements FlagClient {
        final Map<String, Boolean> values = new HashMap<>();
        final List<EvaluationContext> contexts = new ArrayList<>();
        int lookups;

        @Override
        public Optional<EvaluationResult> evaluate(String flagKey, EvaluationContext context) {
            lookups++;
            contexts.add(context);
            Boolean value = values.get(flagKey);
            return value == null
                    ? Optional.empty()
                    : Optional.of(new EvaluationResult(value ? "on" : "off", value, Reason.DEFAULT));
        }
    }

    /** The class being guarded. */
    static class Service {

        @FlagGuard(flag = "checkout")
        public String pay() {
            return "new";
        }

        @FlagGuard(flag = "checkout", fallback = "oldPay")
        public String payWithFallback() {
            return "new";
        }

        String oldPay() {
            return "old";
        }

        @FlagGuard(flag = "checkout", fallback = "oldPayFor")
        public String payFor(EvaluationContext context, int amount) {
            return "new " + context.userKey() + " " + amount;
        }

        String oldPayFor(EvaluationContext context, int amount) {
            return "old " + context.userKey() + " " + amount;
        }

        @FlagGuard(flag = "checkout", defaultValue = true)
        public String payDefaultOn() {
            return "new";
        }

        @FlagGuard(flag = "checkout", fallback = "doesNotExist")
        public String brokenFallback() {
            return "new";
        }

        @FlagGuard(flag = "checkout", fallback = "wrongType")
        public String wrongTypeFallback() {
            return "new";
        }

        int wrongType() {
            return 1;
        }

        @FlagGuard(flag = "checkout")
        public String explodes() {
            throw new IllegalArgumentException("boom");
        }

        public String unguarded() {
            return "plain";
        }
    }

    private final FakeFlags flags = new FakeFlags();
    private final AtomicInteger providerCalls = new AtomicInteger();

    private Service service() {
        FlagContextProvider provider = () -> {
            providerCalls.incrementAndGet();
            return EvaluationContext.builder("provider-user").build();
        };
        AspectJProxyFactory factory = new AspectJProxyFactory(new Service());
        factory.addAspect(new FlagGuardAspect(flags, provider));
        return factory.getProxy();
    }

    @Test
    void flagOnRunsTheMethod() {
        flags.values.put("checkout", true);

        assertEquals("new", service().pay());
    }

    @Test
    void flagOffThrowsFlagDisabledExceptionWithTheKey() {
        flags.values.put("checkout", false);

        FlagDisabledException e = assertThrows(FlagDisabledException.class, () -> service().pay());

        assertEquals("checkout", e.flagKey());
    }

    @Test
    void flagOffUsesTheFallbackMethod() {
        flags.values.put("checkout", false);

        assertEquals("old", service().payWithFallback());
    }

    @Test
    void fallbackGetsTheSameArguments() {
        flags.values.put("checkout", false);

        String result = service().payFor(EvaluationContext.builder("u1").build(), 5);

        assertEquals("old u1 5", result);
    }

    @Test
    void contextArgumentIsUsedWhenTheMethodHasOne() {
        flags.values.put("checkout", true);

        String result = service().payFor(EvaluationContext.builder("arg-user").build(), 7);

        assertEquals("new arg-user 7", result);
        assertEquals("arg-user", flags.contexts.get(0).userKey());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void otherwiseTheProviderSuppliesTheContext() {
        flags.values.put("checkout", true);

        service().pay();

        assertEquals("provider-user", flags.contexts.get(0).userKey());
        assertEquals(1, providerCalls.get());
    }

    @Test
    void unknownFlagUsesTheDefaultValue() {
        // no value for "checkout": the flag is unknown
        assertThrows(FlagDisabledException.class, () -> service().pay());   // default false: stays off
        assertEquals("new", service().payDefaultOn());                       // defaultValue = true: runs
    }

    @Test
    void missingFallbackGivesAClearError() {
        flags.values.put("checkout", false);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service().brokenFallback());

        assertTrue(e.getMessage().contains("doesNotExist"), e.getMessage());
    }

    @Test
    void fallbackWithTheWrongReturnTypeGivesAClearError() {
        flags.values.put("checkout", false);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service().wrongTypeFallback());

        assertTrue(e.getMessage().contains("must return"), e.getMessage());
    }

    @Test
    void exceptionsFromTheMethodAreNotWrapped() {
        flags.values.put("checkout", true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service().explodes());

        assertEquals("boom", e.getMessage());
    }

    @Test
    void unguardedMethodsAreNotTouched() {
        assertEquals("plain", service().unguarded());
        assertEquals(0, flags.lookups);
    }
}