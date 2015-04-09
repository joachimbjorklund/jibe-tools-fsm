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
        this.period = m.getAnnotation(TransitionOnTimeout.class).period();
        this.timeUnit = m.getAnnotation(TransitionOnTimeout.class).timeUnit();
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
