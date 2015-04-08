package jibe.tools.fsm.annotations;

import jibe.tools.fsm.api.EventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Inherited
@Target({ ElementType.TYPE, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface TimerEvent {
    String fsm() default "";

    long period();

    EventType type();

    long delay() default 0;

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}
