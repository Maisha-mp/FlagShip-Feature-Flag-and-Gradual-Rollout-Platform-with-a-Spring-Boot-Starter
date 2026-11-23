package com.flagship.starter;

import com.flagship.core.eval.EvaluationContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/** Runs around every method annotated with @FlagGuard. */
@Aspect
public class FlagGuardAspect {

    private final FlagClient flags;
    private final FlagContextProvider contexts;

    public FlagGuardAspect(FlagClient flags, FlagContextProvider contexts) {
        this.flags = flags;
        this.contexts = contexts;
    }

    @Around(value = "@annotation(guard)", argNames = "pjp,guard")
    public Object around(ProceedingJoinPoint pjp, FlagGuard guard) throws Throwable {
        EvaluationContext context = contextFor(pjp.getArgs());
        if (flags.boolValue(guard.flag(), context, guard.defaultValue())) {
            return pjp.proceed();
        }
        if (guard.fallback().isEmpty()) {
            throw new FlagDisabledException(guard.flag());
        }
        return callFallback(pjp, guard);
    }

    /** An EvaluationContext argument of the method wins; otherwise the provider decides. */
    private EvaluationContext contextFor(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof EvaluationContext context) {
                return context;
            }
        }
        return contexts.currentContext();
    }

    private Object callFallback(ProceedingJoinPoint pjp, FlagGuard guard) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Object target = pjp.getTarget();

        Method fallback = ReflectionUtils.findMethod(
                target.getClass(), guard.fallback(), signature.getParameterTypes());
        if (fallback == null) {
            throw new IllegalStateException("@FlagGuard on " + signature.toShortString()
                    + ": there is no fallback method '" + guard.fallback()
                    + "' with the same parameters");
        }
        if (!ClassUtils.isAssignable(signature.getReturnType(), fallback.getReturnType())) {
            throw new IllegalStateException("@FlagGuard on " + signature.toShortString()
                    + ": fallback method '" + guard.fallback() + "' must return "
                    + signature.getReturnType().getSimpleName()
                    + " but returns " + fallback.getReturnType().getSimpleName());
        }

        ReflectionUtils.makeAccessible(fallback);
        try {
            return fallback.invoke(target, pjp.getArgs());
        } catch (InvocationTargetException e) {
            throw e.getCause();   // show the real exception, not the reflection wrapper
        }
    }
}