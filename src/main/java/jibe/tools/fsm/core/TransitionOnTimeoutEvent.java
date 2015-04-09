package jibe.tools.fsm.core;

import jibe.tools.fsm.annotations.TransitionOnTimeout;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class TransitionOnTimeoutEvent {
    private final Method timeOutMethod;
    private final long period;
    private final TimeUnit timeUnit;

    public TransitionOnTimeoutEvent(Method m) {
        this.timeOutMethod = m;
        TransitionOnTimeout annotation = m.getAnnotation(TransitionOnTimeout.class);
        this.period = annotation.period();
        this.timeUnit = annotation.timeUnit();
    }

    public Method getTimeOutMethod() {
        return timeOutMethod;
    }

    public long getPeriod() {
        return period;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }
}
