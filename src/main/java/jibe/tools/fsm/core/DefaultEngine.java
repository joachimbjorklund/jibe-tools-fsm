package jibe.tools.fsm.core;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import jibe.tools.fsm.api.Context;
import jibe.tools.fsm.api.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 *
 */
public class DefaultEngine implements Engine {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEngine.class);

    private final DefaultContext context;
    private final Object fsm;
    private final EngineHelper helper;

    public DefaultEngine(Object fsm) {
        this.fsm = fsm;
        this.helper = new EngineHelper(fsm);
        this.context = new DefaultContext();
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public void event(Object event) {
        synchronized (context) {
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

    private void executeOnEnter(Object state) {
        for (Method m : helper.findOnEnter(state)) {
            executeAction(state, m);
        }
    }

    private void executeOnExit(Object state) {
        for (Method m : helper.findOnExit(state)) {
            executeAction(state, m);
        }
    }

    private void executeAction(Object state, Method m) {
        Class<?> returnType = m.getReturnType();
        if (!returnType.equals(Void.TYPE)) {
            LOGGER.warn("invoking Action with return-type: " + returnType + ". I don't know what to do with it...");
        }

        try {
            if (m.getParameterTypes().length == 1) {
                m.invoke(state, fsm);
            } else {
                m.invoke(state);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     *
     */
    private class DefaultContext implements Context {
        private Object currentState;

        public DefaultContext() {
            try {
                Optional<Object> startState = helper.findStartState();
                if (!startState.isPresent()) {
                    throw new RuntimeException("could not find any suitable startState, check your machine setup");
                }
                for (Method m : helper.findOnEnter(startState.get())) {
                    if (m.getParameterTypes().length == 1) {
                        m.invoke(startState.get(), fsm);
                    } else {
                        m.invoke(startState.get());
                    }
                }
                this.currentState = startState.get();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }

        @Override
        public Object currentState() {
            return currentState;
        }
    }
}
