package jibe.tools.fsm.api;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 *
 */
public interface Engine extends Service {
    Object getFsm();

    Engine start();

    Engine stop();
    void event(Object event);

    Configuration getConfiguration();

    //    Context context();

    Optional<Object> getCurrentState();

    Optional<Object> getPreviousState();

    //    void timerAtFixedRate(Object timerEvent, long delay, long period, TimeUnit timeUnit);

    interface Configuration {
        ThreadFactory getThreadFactory();

        ExecutorService getExecutorService();

        ScheduledExecutorService getScheduledExecutorService();

        Integer getQueueSize();

        Long getActionTimeoutMillis();

        Long getTransitionTimeoutMillis();
    }
}
