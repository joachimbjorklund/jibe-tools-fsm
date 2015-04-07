package jibe.tools.fsm.api;

import com.google.common.util.concurrent.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 *
 */
public interface Engine extends Service {
    Object fsm();

    Engine start();

    Engine stop();
    void event(Object event);

    Configuration configuration();

    Context context();

    Object currentState();

    interface Configuration {
        ThreadFactory getThreadFactory();

        ExecutorService getExecutorService();

        Integer getQueueSize();

        Long getActionTimeoutMillis();

        Long getTransitionTimeoutMillis();
    }
}
