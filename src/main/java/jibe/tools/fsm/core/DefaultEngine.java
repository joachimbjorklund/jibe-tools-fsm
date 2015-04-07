package jibe.tools.fsm.core;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import jibe.tools.fsm.api.Context;
import jibe.tools.fsm.api.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.MoreExecutors.getExitingExecutorService;
import static com.google.common.util.concurrent.MoreExecutors.platformThreadFactory;
import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 *
 */
public class DefaultEngine extends AbstractExecutionThreadService implements Engine {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEngine.class);
    private final Configuration configuration;
    private final Object fsm;
    private ExecutorService executorService;
    private DefaultContext context;
    private EngineHelper helper;
    private ArrayBlockingQueue<Object> queue;
    private ThreadFactory threadFactory;

    private CountDownLatch startLatch = new CountDownLatch(1);

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
        queue = new ArrayBlockingQueue<>(configuration.getQueueSize());
        threadFactory = configuration.getThreadFactory();
        executorService = configuration.getExecutorService();
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public Object currentState() {
        synchronized (context) {
            return context.currentState;
        }
    }

    @Override
    public Object fsm() {
        return fsm;
    }

    @Override
    public Engine start() {
        Engine engine = (Engine) startAsync();
        engine.awaitRunning();
        try {
            startLatch.await();
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
            offer(event);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void fire(Object event) {
        LOGGER.debug(String.format("fire event: %s", event));
        synchronized (context) {
            if (ServiceEvent.START == event) {
                Optional<Object> startState = helper.findStartState();
                if (!startState.isPresent()) {
                    try {
                        stop();
                    } finally {
                        throw new RuntimeException("no start-state found");
                    }
                }
                executeOnEnter(startState.get());
                context.currentState = startState.get();
                LOGGER.debug("currentState: " + startState.get());
                startLatch.countDown();
                return;
            }

            Optional<Method> foundTransition = helper.findTransitionForEvent(context.currentState, event);
            if (!foundTransition.isPresent()) {
                LOGGER.info("transition not found for event: " + event + ", current: " + context.currentState);
                return;
            }

            Method transitionMethod = foundTransition.get();
            Optional<Object> foundToState = helper.findState(transitionMethod.getReturnType());
            if (!foundToState.isPresent()) {
                throw new RuntimeException("transition returns something that is not a known @State");
            }

            try {
                Object result = transitionMethod.invoke(context.currentState, event);
                if (result == null) {
                    return;
                }
                executeOnExit(context.currentState);
                context.currentState = foundToState.get(); // we are not replacing anything
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            executeOnEnter(context.currentState);
        }
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    private Future<?> invokeAsync(final Method method, final Object holder, final Object... args) {
        return executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return method.invoke(holder, args);
            }
        });
    }

    private Set<Future<?>> executeOnEnter(Object state) {
        Set<Future<?>> futures = new HashSet<>();
        for (Method m : helper.findOnEnter(state)) {
            executeAction(state, m);
        }
        return futures;
    }

    private Set<Future<?>> executeOnExit(Object state) {
        Set<Future<?>> futures = new HashSet<>();
        for (Method m : helper.findOnExit(state)) {
            executeAction(state, m);
        }
        return futures;
    }

    private Optional<Future<?>> executeAction(Object state, Method m) {
        Class<?> returnType = m.getReturnType();
        if (!returnType.equals(Void.TYPE) && !Future.class.isAssignableFrom(returnType)) {
            LOGGER.warn("invoking Action with return-type: " + returnType + ". I don't know what to do with it...");
        }

        try {
            if (m.getParameterTypes().length == 1) {
                Object invoke = m.invoke(state, fsm);
            } else {
                Object invoke = m.invoke(state);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        return Optional.absent(); // TODO:
    }

    @Override
    protected Executor executor() {
        return executorService;
    }

    @Override
    protected void shutDown() throws Exception {
        LOGGER.debug("shutDown");
    }

    @Override
    protected void startUp() throws Exception {
        LOGGER.debug("startUp");
        offer(ServiceEvent.START);
    }

    private void offer(Object event) {
        try {
            queue.offer(event, 1, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected void triggerShutdown() {
        LOGGER.debug("triggerShutdown");
        offer(ServiceEvent.STOP);
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            Object event = queue.take();
            if (ServiceEvent.STOP != event) {
                fire(event);
            }
        }
    }

    private enum ServiceEvent {
        START,
        STOP
    }

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
        private int queueSize;
        private long actionTimeoutMills;
        private long transitionTimeoutMills;

        private DefaultConfiguration() {
            threadFactory = platformThreadFactory();
            executorService = getExitingExecutorService((ThreadPoolExecutor) newFixedThreadPool(10, threadFactory));
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

            return this;
        }

        @Override
        public ThreadFactory getThreadFactory() {
            return threadFactory;
        }

        public void setThreadFactory(ThreadFactory threadFactory) {
            this.threadFactory = Objects.requireNonNull(threadFactory);
        }

        @Override
        public ExecutorService getExecutorService() {
            return executorService;
        }

        public void setExecutorService(ExecutorService executorService) {
            this.executorService = Objects.requireNonNull(executorService);
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
        private Object currentState;

        public DefaultContext() {
        }
    }
}
