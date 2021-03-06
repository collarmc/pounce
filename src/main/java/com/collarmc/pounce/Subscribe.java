package com.collarmc.pounce;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate like so:
 *  @Subscribe(preference=Preference.CALLER, priority=99)
 *  methodName(MyEvent)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
    Preference value() default Preference.POOL;
    int priority() default 100;
}
