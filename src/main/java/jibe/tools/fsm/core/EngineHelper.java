package jibe.tools.fsm.core;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import jibe.tools.fsm.annotations.Action;
import jibe.tools.fsm.annotations.StartState;
import jibe.tools.fsm.annotations.State;
import jibe.tools.fsm.annotations.StateMachine;
import jibe.tools.fsm.annotations.TimerEvent;
import jibe.tools.fsm.annotations.Transition;
import jibe.tools.fsm.annotations.TransitionOnTimeout;
import jibe.tools.fsm.api.ActionType;
import jibe.tools.fsm.api.Engine;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.MethodParameterScanner;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Set;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newHashSet;
import static jibe.tools.fsm.core.EngineHelper.TypeDefinition.Type.START_STATE;
import static jibe.tools.fsm.core.EngineHelper.TypeDefinition.Type.STATE;
import static jibe.tools.fsm.core.EngineHelper.TypeDefinition.Type.TIMER_EVENT;
import static org.reflections.ReflectionUtils.getAllMethods;
import static org.reflections.ReflectionUtils.withAnnotation;
import static org.reflections.ReflectionUtils.withParameters;

/**
 *
 */
public class EngineHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineHelper.class);

    private final Engine engine;
    private final Object fsm;
    private final Reflections reflections;
    private final HashMap<Class<?>, TypeDefinition> typeMap = new HashMap<>();

    EngineHelper(Engine engine) {
        this.engine = engine;
        this.fsm = engine.getFsm();
        reflections = setupReflections();
        try {
            scanStatesAndFields();
            scanTimers();
            scanTimeouts();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    Set<Object> getTimerEvents() {
        return newHashSet(transform(filter(filter(typeMap.values(), withType(TIMER_EVENT)), annotationMatchingMyFsm(TimerEvent.class)), toObj()));
    }

    Set<TransitionOnTimeoutEvent> getTimeoutTransitions(Object state) {
        return typeMap.get(state.getClass()).timeoutEvents;
    }

    private Predicate<TypeDefinition> annotationMatchingMyFsm(final Class<? extends Annotation> annotationClass) {
        return new Predicate<TypeDefinition>() {
            @Override
            public boolean apply(@Nullable TypeDefinition input) {
                String fsmName = getFsmNameFromAnnotation(input.obj, annotationClass);
                return Strings.isNullOrEmpty(fsmName) || getFsmName(fsm).equals(fsmName);
            }
        };
    }

    Optional<Object> findState(Class<?> stateClass) {
        Set<Object> states =
                newHashSet(transform(filter(typeMap.values(), withTypeAndClass(STATE, stateClass)), toObj()));
        if (states.iterator().hasNext()) {
            return Optional.of(states.iterator().next());
        }

        states = newHashSet(transform(filter(typeMap.values(), withTypeAndClass(START_STATE, stateClass)), toObj()));
        if (states.iterator().hasNext()) {
            return Optional.of(states.iterator().next());
        }

        return Optional.absent();
    }

    Optional<Set<Object>> findStartState() {
        Set<Object> startStates = newHashSet(transform(filter(typeMap.values(), withType(START_STATE)), toObj()));

        if (startStates.isEmpty()) {
            return Optional.absent();
        }

        if (startStates.size() == 1) {
            Object startState = startStates.iterator().next();
            String fsmName = getFsmNameFromAnnotation(startState, StartState.class);
            if (Strings.isNullOrEmpty(fsmName) || getFsmName(fsm).equals(fsmName)) {
                return Optional.<Set<Object>>of(newHashSet(startState));
            }
            return Optional.absent();
        }

        Set<Object> filtered = newHashSet(filter(startStates, new Predicate<Object>() {
            @Override
            public boolean apply(Object input) {
                return getFsmName(fsm).equals(getFsmNameFromAnnotation(input, StartState.class)) || input.getClass().getDeclaringClass().equals(fsm.getClass());
            }
        }));

        if (filtered.isEmpty()) {
            return Optional.absent();
        }

        if (filtered.size() == 1) {
            return Optional.<Set<Object>>of(newHashSet(filtered.iterator().next()));
        }

        return Optional.of(filtered);
    }

    private String getFsmNameFromAnnotation(Object input, Class<? extends Annotation> annotationClass) {
        if (annotationClass.equals(StartState.class)) {
            StartState annotation = input.getClass().getAnnotation((Class<StartState>) annotationClass);
            if (annotation == null) {
                throw new RuntimeException("shouldn't input: " + input + ", have annotation: " + annotationClass);
            }
            return annotation.fsm();
        }
        if (annotationClass.equals(State.class)) {
            State annotation = input.getClass().getAnnotation((Class<State>) annotationClass);
            if (annotation == null) {
                throw new RuntimeException("shouldn't input: " + input + ", have annotation: " + annotationClass);
            }
            return annotation.fsm();
        }
        if (annotationClass.equals(TimerEvent.class)) {
            TimerEvent annotation = input.getClass().getAnnotation((Class<TimerEvent>) annotationClass);
            if (annotation == null) {
                throw new RuntimeException("shouldn't input: " + input + ", have annotation: " + annotationClass);
            }
            return annotation.fsm();
        }
        throw new RuntimeException("don't know: " + annotationClass);
    }

    private void scanTimers() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        for (Class c : getAnnotatedWith(Class.class, TimerEvent.class)) {
            try {
                Constructor declaredConstructor = c.getDeclaredConstructor(fsm.getClass());
                declaredConstructor.setAccessible(true);
                typeMap.put(c, new TypeDefinition(declaredConstructor.newInstance(fsm), TimerEvent.class));
            } catch (NoSuchMethodException e) {
                Constructor declaredConstructor = c.getDeclaredConstructor();
                declaredConstructor.setAccessible(true);
                typeMap.put(c, new TypeDefinition(declaredConstructor.newInstance(), TimerEvent.class));
            }
        }
    }

    private void scanTimeouts() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        for (Method m : getAnnotatedWith(Method.class, TransitionOnTimeout.class)) {
            if (!typeMap.containsKey(m.getReturnType())) {
                LOGGER.warn("timeout-annotated method: " + m + " does not transit to any known state");
                continue;
            }

            if (!typeMap.containsKey(m.getDeclaringClass())) {
                LOGGER.warn("timeout-annotated method: " + m + " is not declared in any known state");
                continue;
            }

            TypeDefinition typeDefinition = typeMap.get(m.getDeclaringClass());
            typeDefinition.addTimeout(new TransitionOnTimeoutEvent(m));
        }
    }

    private void scanStatesAndFields() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        if (!typeMap.isEmpty()) {
            throw new IllegalStateException("already scanned....");
        }
        for (Class<? extends Annotation> stateAnnotation : newHashSet(StartState.class, State.class)) {
            for (final Class c : getAnnotatedWith(Class.class, stateAnnotation)) {
                try {
                    Constructor declaredConstructor = c.getDeclaredConstructor(fsm.getClass());
                    declaredConstructor.setAccessible(true);
                    typeMap.put(c, new TypeDefinition(declaredConstructor.newInstance(fsm), stateAnnotation));
                } catch (NoSuchMethodException e) {
                    Constructor declaredConstructor = c.getDeclaredConstructor();
                    declaredConstructor.setAccessible(true);
                    typeMap.put(c, new TypeDefinition(declaredConstructor.newInstance(), stateAnnotation));
                }
            }

            for (final Method m : getAnnotatedWith(Method.class, stateAnnotation)) {
                Class<?> returnType = m.getReturnType();
                if (typeMap.containsKey(returnType)) {
                    continue;
                }

                if (!m.getDeclaringClass().equals(fsm.getClass())) {
                    LOGGER.debug("skipping state constructing method: " + m + ", as it is not declared by the fsm itself...");
                    continue;
                }
                Class<?>[] parameterTypes = m.getParameterTypes();
                if (parameterTypes.length == 0) {
                    typeMap.put(returnType, new TypeDefinition(m.invoke(fsm), stateAnnotation));
                } else if ((parameterTypes.length == 1) && parameterTypes[0].equals(fsm.getClass())) {
                    typeMap.put(parameterTypes[0], new TypeDefinition(m.invoke(fsm, fsm), stateAnnotation));
                } else {
                    throw new RuntimeException("unknown parameters for state constructing method: " + m);
                }
            }

            for (final Field f : getAnnotatedWith(Field.class, stateAnnotation)) {
                if (!f.getDeclaringClass().equals(fsm.getClass())) {
                    LOGGER.debug("skipping field: " + f + ", as it is not declared by the fsm itself...");
                    continue;
                }

                if (!typeMap.containsKey(f.getType())) {
                    throw new RuntimeException("type of field: " + f + " does not constitute a known state");
                }

                f.setAccessible(true);
                Object state = f.get(fsm);
                if (state != null) {
                    LOGGER.warn("overwriting field: " + f + " with: " + typeMap.get(f.getType()));
                }
                f.set(fsm, typeMap.get(f.getType()).obj);
            }
        }
    }

    private Function<TypeDefinition, Object> toObj() {
        return new Function<TypeDefinition, Object>() {
            @Override
            public Object apply(TypeDefinition input) {
                return input.obj;
            }
        };
    }

    private Predicate<TypeDefinition> withType(final TypeDefinition.Type... types) {
        final Set<TypeDefinition.Type> withTypes = newHashSet(types);
        return new Predicate<TypeDefinition>() {
            @Override
            public boolean apply(TypeDefinition input) {
                return withTypes.contains(input.type);
            }
        };
    }

    private Predicate<TypeDefinition> withTypeAndClass(final TypeDefinition.Type type, final Class<?> clazz) {
        return new Predicate<TypeDefinition>() {
            @Override
            public boolean apply(TypeDefinition input) {
                return (input.type == type) && input.obj.getClass().equals(clazz);
            }
        };
    }

    private <T> Set<T> getAnnotatedWith(Class<T> type, Class<? extends Annotation> annotation) {
        if (type.equals(Field.class)) {
            return (Set<T>) reflections.getFieldsAnnotatedWith(annotation);
        }
        if (type.equals(Method.class)) {
            return (Set<T>) reflections.getMethodsAnnotatedWith(annotation);
        }
        if (type.equals(Class.class)) {
            return (Set<T>) reflections.getTypesAnnotatedWith(annotation);
        }
        throw new RuntimeException("unknown type: " + type);
    }

    private String getFsmName(Object fsm) {
        StateMachine annotation = fsm.getClass().getAnnotation(StateMachine.class);
        if (annotation == null) {
            throw new RuntimeException("fsm: " + fsm + " must be annotated with StateMachine");
        }
        return !Strings.isNullOrEmpty(annotation.name()) ? annotation.name() : fsm.getClass().getName();
    }

    private Reflections setupReflections() {
        final Set<String> pkgs = getPackages(fsm);
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
                .forPackages(pkgs.toArray(new String[0]))
                .filterInputsBy(new Predicate<String>() {
                    @Override
                    public boolean apply(@Nullable final String file) {
                        boolean any = Iterables.any(pkgs, new Predicate<String>() {
                            @Override
                            public boolean apply(String pkg) {
                                return file.startsWith(pkg);
                            }
                        });
                        return any;
                    }
                })
                .addScanners(
                        new MethodAnnotationsScanner(),
                        new MethodParameterScanner(),
                        new FieldAnnotationsScanner());
        return new Reflections(configurationBuilder);
    }

    private Set<String> getPackages(Object fsm) {
        StateMachine annotation = fsm.getClass().getAnnotation(StateMachine.class);
        Set<String> answer = newHashSet();
        if (annotation != null) {
            answer = newHashSet(annotation.pkgs());
        }
        if (answer.isEmpty()) {
            answer = newHashSet(fsm.getClass().getPackage().getName());
        }
        return answer;
    }

    Optional<Set<Method>> findTransitionForEvent(Object state, Object event) {
        Set<Method> transitions = getAllMethods(state.getClass(), withAnnotation(Transition.class), withParameters(event.getClass()));
        if (transitions.isEmpty()) {
            return Optional.absent();
        } else if (transitions.size() > 1) {
            return Optional.of(transitions);
        } else {
            final Method transition = transitions.iterator().next();
            TypeDefinition stateDefinition = typeMap.get(transition.getReturnType());
            if (stateDefinition == null) {
                return Optional.absent();
            }
            return Optional.<Set<Method>>of(newHashSet(transition));
        }
    }

    Set<Method> findActionImplied(Object obj) {
        Set<Method> methods = getAllMethods(obj.getClass(), withAnnotation(Action.class), withActionType(ActionType.Implied), withParameters());
        methods.addAll(getAllMethods(obj.getClass(), withAnnotation(Action.class), withActionType(ActionType.Implied), withParameters(fsm.getClass())));
        return methods;
    }

    Set<Method> findActionOnExit(Object obj) {
        Set<Method> methods = getAllMethods(obj.getClass(), withAnnotation(Action.class), withActionType(ActionType.OnExit), withParameters());
        methods.addAll(getAllMethods(obj.getClass(), withAnnotation(Action.class), withActionType(ActionType.OnExit), withParameters(fsm.getClass())));
        return methods;
    }

    Set<Method> findActionOnEnter(Object obj) {
        Set<Method> methods = getAllMethods(obj.getClass(), withAnnotation(Action.class), withActionType(ActionType.OnEnter), withParameters());
        methods.addAll(getAllMethods(obj.getClass(), withAnnotation(Action.class), withActionType(ActionType.OnEnter), withParameters(fsm.getClass())));
        return methods;
    }

    private Predicate<Method> withActionType(final ActionType actionType) {
        return new Predicate<Method>() {
            @Override
            public boolean apply(@Nullable Method input) {
                return input.getAnnotation(Action.class).value() == actionType;
            }
        };
    }

    static class TypeDefinition {
        private final Type type;
        private final Object obj;
        private final Set<TransitionOnTimeoutEvent> timeoutEvents = newHashSet();

        private TypeDefinition(Object obj, Class<? extends Annotation> stateAnnotation) {
            this.type = Type.from(stateAnnotation);
            this.obj = obj;
        }

        @Override
        public String toString() {
            return "StateDefinition{" +
                    "obj=" + obj +
                    ", type=" + type +
                    '}';
        }

        public void addTimeout(TransitionOnTimeoutEvent transitionOnTimeoutEvent) {
            timeoutEvents.add(transitionOnTimeoutEvent);
        }

        enum Type {
            START_STATE,
            STATE,
            TIMER_EVENT;

            public static Type from(Class<? extends Annotation> annotationType) {
                if (annotationType.equals(State.class)) {
                    return Type.STATE;
                }
                if (annotationType.equals(StartState.class)) {
                    return Type.START_STATE;
                }
                if (annotationType.equals(TimerEvent.class)) {
                    return Type.TIMER_EVENT;
                }
                throw new RuntimeException("unknown annotationType: " + annotationType);
            }
        }
    }
}
