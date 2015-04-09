package jibe.tools.fsm.core;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import jibe.tools.fsm.annotations.TimerEvent;
import jibe.tools.fsm.api.Context;
import jibe.tools.fsm.api.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.util.concurrent.MoreExecutors.platformThreadFactory;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.Executors.newScheduledThreadPool;

/**
 *
 */
public class DefaultEngine extends AbstractExecutionThreadService implements Engine {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEngine.class);
    private final Configuration configuration;
    private final Object fsm;
    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutorService;
    private DefaultContext context;
    private EngineHelper helper;
    private BlockingQueue<Object> queue;
    private ThreadFactory threadFactory;

    private CountDownLatch startLatch = new CountDownLatch(1);
    private Map<Object, ScheduledFuture> scheduledFutures = newHashMap();

    DefaultEngine(Object fsm) {
        this(fsm, new DefaultConfiguration());
    }

    DefaultEngine(Object fsm, Configuration configuration) {
        this.fsm = fsm;
        this.configuration = new DefaultConfiguration().merge(configuration);

        configure(configuration);
    }

    public static ConfigurationBuilder configurationBuilder() {
        return new ConfigurationBuilder();
    }

    private void configure(Configuration configuration) {
        helper = new EngineHelper(this);
        context = new DefaultContext();
        queue = new LinkedBlockingQueue<>(configuration.getQueueSize());
        threadFactory = configuration.getThreadFactory();
        executorService = configuration.getExecutorService();
        scheduledExecutorService = configuration.getScheduledExecutorService();
    }

    //    @Override
    //    public Context context() {
    //        return context;
    //    }

    @Override
    public Optional<Object> getCurrentState() {
        synchronized (context) {
            return context.currentState;
        }
    }

    @Override
    public Optional<Object> getPreviousState() {
        return context.previousState;
    }

    private void timerAtFixedRate(final Object timerEvent, long delay, long period, TimeUnit timeUnit) {
        ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (isRunning() && getCurrentState().isPresent()) {
                    event(timerEvent);
                }
            }
        }, delay, period, timeUnit);

        scheduledFutures.put(timerEvent, scheduledFuture);
    }

    private void timerAt(final Object timerEvent, long delay, TimeUnit timeUnit) {
        ScheduledFuture<?> scheduledFuture = scheduledExecutorService.schedule(new Runnable() {
            @Override
            public void run() {
                if (isRunning() && getCurrentState().isPresent()) {
                    event(timerEvent);
                }
            }
        }, delay, timeUnit);

        scheduledFutures.put(timerEvent, scheduledFuture);
    }

    @Override
    public Object getFsm() {
        return fsm;
    }

    @Override
    public Engine start() {
        LOGGER.debug("start");
        Engine engine = (Engine) startAsync();
        engine.awaitRunning();
        LOGGER.debug("service running");
        try {
            boolean await = startLatch.await(2, TimeUnit.SECONDS);
            if (!await) {
                throw new RuntimeException("not started in time");
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return engine;
    }

    @Override
    public Engine stop() {
        Engine engine = (Engine) stopAsync();
        engine.awaitTerminated();
        return engine;
    }

    @Override
    public void event(Object event) {
        if (!isRunning()) {
            throw new IllegalStateException("not running");
        }
        try {
            queue(event);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void fire(Object event) {
        synchronized (context) {
            if (ServiceEvent.START == event) {
                startLatch.countDown();

                Optional<Set<Object>> startStates = helper.findStartState();
                int foundNbrStartStates = startStates.isPresent() ? startStates.get().size() : 0;
                if (foundNbrStartStates != 1) {
                    if (foundNbrStartStates == 0) {
                        LOGGER.error("no start-state found");
                    } else {
                        LOGGER.error("to many start-states found: " + startStates.get());
                    }
                    triggerShutdown();
                    return;
                }

                Object startState = startStates.get().iterator().next();

                executeActionImplied(startState);
                executeActionOnEnter(startState);
                context.currentState = Optional.of(startState);

                for (TransitionOnTimeoutEvent e : helper.getTimeoutTransitions(context.currentState.get())) {
                    timerAt(e, e.getPeriod(), e.getTimeUnit());
                }
                return;
            }

            executeActionImplied(event);

            Object currentState = context.currentState.get();
            Optional<Set<Method>> foundTransitions;
            if (event instanceof TransitionOnTimeoutEvent) {
                foundTransitions = Optional.<Set<Method>>of(newHashSet(((TransitionOnTimeoutEvent) event).getTimeOutMethod()));
            } else {
                foundTransitions = helper.findTransitionForEvent(currentState, event);
            }

            if (!foundTransitions.isPresent()) {
                return;
            }
            if (foundTransitions.get().size() > 1) {
                LOGGER.error("to many transitions found: " + foundTransitions.get());
                triggerShutdown();
                return;
            }

            Method transitionMethod = foundTransitions.get().iterator().next();
            Optional<Object> foundToState = helper.findState(transitionMethod.getReturnType());
            if (!foundToState.isPresent()) {
                throw new RuntimeException("transition returns something that is not a known state");
            }

            try {
                transitionMethod.setAccessible(true);
                Object[] methodArgs = new Object[0];
                if (transitionMethod.getParameterTypes().length == 1) {
                    methodArgs = new Object[]{ event };
                }
                Object result = transitionMethod.invoke(currentState, methodArgs);
                if (result == null) {
                    return;
                }

                executeActionImplied(currentState);
                executeActionOnExit(currentState);

                for (TransitionOnTimeoutEvent e : helper.getTimeoutTransitions(currentState)) {
                    if (scheduledFutures.containsKey(e)) {
                        boolean cancelled = scheduledFutures.get(e).cancel(false);
                        if (cancelled) {
                            LOGGER.info("cancelled timeout-transition for state: " + currentState);
                        }
                    }
                }

                currentState = result;

                context.previousState = context.currentState;
                context.currentState = Optional.of(result);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            executeActionImplied(currentState);
            executeActionOnEnter(currentState);

            for (TransitionOnTimeoutEvent e : helper.getTimeoutTransitions(currentState)) {
                timerAt(e, e.getPeriod(), e.getTimeUnit());
            }
        }
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    private void executeActionImplied(Object obj) {
        for (Method m : helper.findActionImplied(obj)) {
            executeAction(obj, m);
        }
    }

    private void executeActionOnEnter(Object obj) {
        for (Method m : helper.findActionOnEnter(obj)) {
            executeAction(obj, m);
        }
    }

    private void executeActionOnExit(Object obj) {
        for (Method m : helper.findActionOnExit(obj)) {
            executeAction(obj, m);
        }
    }

    private void executeAction(Object obj, Method m) {
        Class<?> returnType = m.getReturnType();
        if (!returnType.equals(Void.TYPE)) {
            LOGGER.warn("invoking Action with return-type: " + returnType + ". I don't know what to do with it...");
        }

        try {
            m.setAccessible(true);
            if (m.getParameterTypes().length == 1) {
                m.invoke(obj, fsm);
            } else {
                m.invoke(obj);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected Executor executor() {
        return executorService;
    }

    @Override
    protected void shutDown() throws Exception {
        LOGGER.info("shutDown");
        executorService.shutdownNow();
        scheduledExecutorService.shutdownNow();
        LOGGER.debug("executorServices is now shutdown");
    }

    @Override
    protected void startUp() throws Exception {
        LOGGER.info("startUp");
        scheduleTimerEvents(helper.getTimerEvents());
        queue(ServiceEvent.START);
    }

    private void scheduleTimerEvents(Set<Object> timerEvents) {
        for (Object timerEvent : timerEvents) {
            TimerEvent annotation = timerEvent.getClass().getAnnotation(TimerEvent.class);
            switch (annotation.type()) {
            case ScheduledFixedRateTimer:
                timerAtFixedRate(timerEvent, annotation.delay(), annotation.period(), annotation.timeUnit());
                break;
            case ScheduledTimer:
                timerAt(timerEvent, annotation.delay(), annotation.timeUnit());
                break;
            default:
                throw new RuntimeException("unknown timer type...");
            }
        }
    }

    private void queue(Object event) {
        try {
            queue.add(event);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected void triggerShutdown() {
        LOGGER.info("triggerShutdown");
        queue(ServiceEvent.STOP);
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            Object event = queue.take();
            if (ServiceEvent.STOP != event) {
                fire(event);
            } else {
                LOGGER.debug("Leaving main-loop");
                return;
            }
        }
        LOGGER.debug("Leaving main-loop");
    }

    private enum ServiceEvent {
        START,
        STOP
    }

    @SuppressWarnings("unused")
    public static class ConfigurationBuilder {
        private final DefaultConfiguration configuration = new DefaultConfiguration();

        public ConfigurationBuilder threadFactory(ThreadFactory threadFactory) {
            configuration.setThreadFactory(threadFactory);
            return this;
        }

        public ConfigurationBuilder executorService(ExecutorService executorService) {
            configuration.setExecutorService(executorService);
            return this;
        }

        public ConfigurationBuilder queueSize(int queueSize) {
            configuration.setQueueSize(queueSize);
            return this;
        }

        public ConfigurationBuilder actionTimeoutMills(long millis) {
            configuration.setActionTimeoutMills(millis);
            return this;
        }

        public ConfigurationBuilder transitionTimeoutMills(long millis) {
            configuration.setTransitionTimeoutMills(millis);
            return this;
        }

        public Configuration build() {
            return configuration;
        }
    }

    private static class DefaultConfiguration implements Configuration {
        private ThreadFactory threadFactory;
        private ExecutorService executorService;
        private ScheduledExecutorService scheduledExecutorService;
        private int queueSize;
        private long actionTimeoutMills;
        private long transitionTimeoutMills;

        private DefaultConfiguration() {
            threadFactory = platformThreadFactory();
            executorService = newFixedThreadPool(10, threadFactory);
            scheduledExecutorService = newScheduledThreadPool(10, threadFactory);
            queueSize = 1024;
            actionTimeoutMills = 1000;
            transitionTimeoutMills = 1000;
        }

        public DefaultConfiguration merge(Configuration configuration) {
            Long actionTimeoutMillis = configuration.getActionTimeoutMillis();
            if (actionTimeoutMillis != null) {
                setActionTimeoutMills(actionTimeoutMillis);
            }
            Long transitionTimeoutMillis = configuration.getTransitionTimeoutMillis();
            if (transitionTimeoutMillis != null) {
                setTransitionTimeoutMills(transitionTimeoutMillis);
            }
            Integer queueSize = configuration.getQueueSize();
            if (queueSize != null) {
                setQueueSize(queueSize);
            }
            ThreadFactory threadFactory = configuration.getThreadFactory();
            if (threadFactory != null) {
                setThreadFactory(threadFactory);
            }

            ExecutorService executorService = configuration.getExecutorService();
            if (executorService != null) {
                setExecutorService(executorService);
            }

            ScheduledExecutorService scheduledExecutorService = configuration.getScheduledExecutorService();
            if (scheduledExecutorService != null) {
                setScheduledExecutorService(scheduledExecutorService);
            }

            return this;
        }

        @Override
        public ThreadFactory getThreadFactory() {
            return threadFactory;
        }

        public void setThreadFactory(ThreadFactory threadFactory) {
            this.threadFactory = requireNonNull(threadFactory);
        }

        @Override
        public ExecutorService getExecutorService() {
            return executorService;
        }

        public void setExecutorService(ExecutorService executorService) {
            this.executorService = requireNonNull(executorService);
        }

        @Override
        public ScheduledExecutorService getScheduledExecutorService() {
            return scheduledExecutorService;
        }

        public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            this.scheduledExecutorService = requireNonNull(scheduledExecutorService);
        }

        @Override
        public Integer getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = (int) assertPositiveNotZero(queueSize);
        }

        @Override
        public Long getActionTimeoutMillis() {
            return actionTimeoutMills;
        }

        @Override
        public Long getTransitionTimeoutMillis() {
            return transitionTimeoutMills;
        }

        public void setActionTimeoutMills(long actionTimeoutMills) {
            this.actionTimeoutMills = assertPositiveNotZero(actionTimeoutMills);
        }

        public void setTransitionTimeoutMills(long transitionTimeoutMills) {
            this.transitionTimeoutMills = assertPositiveNotZero(transitionTimeoutMills);
        }

        private long assertPositiveNotZero(long value) {
            if (value > 0) {
                return value;
            }
            throw new RuntimeException("timeouts must be a positive number > 0");
        }
    }

    /**
     *
     */
    private class DefaultContext implements Context {
        private Optional<Object> currentState = Optional.absent();
        private Optional<Object> previousState = Optional.absent();
    }
}
