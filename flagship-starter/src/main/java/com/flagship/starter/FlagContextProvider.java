package com.flagship.starter;

import com.flagship.core.eval.EvaluationContext;

/**
 * Tells @FlagGuard who the current user is. An application defines its own bean of this
 * type (for example one that reads the logged-in user); the starter's default is anonymous.
 */
@FunctionalInterface
public interface FlagContextProvider {

    EvaluationContext currentContext();
}