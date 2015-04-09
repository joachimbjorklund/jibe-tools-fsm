package jibe.tools.fsm.api;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 *
 */
public interface Engine<F, E> extends Service {
    F getFsm();

    Engine start();

    Engine stop();

    void event(E event);

    Configuration getConfiguration();

    Snapshot getSnapshot();

    interface Configuration {
        ThreadFactory getThreadFactory();

        ExecutorService getExecutorService();

        ScheduledExecutorService getScheduledExecutorService();

        Integer getQueueSize();

        Long getActionTimeoutMillis();

        Long getTransitionTimeoutMillis();
    }

    interface Snapshot {
        Optional<Object> getCurrentState();
    }
}
