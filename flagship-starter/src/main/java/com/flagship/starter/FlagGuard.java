package com.flagship.starter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this on a method of a Spring bean to run it only while a feature flag is on.
 * When the flag is off the fallback method runs instead, or FlagDisabledException is thrown.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FlagGuard {

    /** The key of the boolean flag, e.g. "new-checkout". */
    String flag();

    /**
     * Name of a method in the same class with the same parameters and a compatible
     * return type, called when the flag is off. Empty means: throw FlagDisabledException.
     */
    String fallback() default "";

    /** Used when the flag is unknown or no snapshot has loaded yet. False keeps the feature off. */
    boolean defaultValue() default false;
}