package jibe.tools.fsm.core;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
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
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
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
        reflections = setupReflections(engine.getConfiguration().getClassLoader());
        try {
            scanStatesAndFields();
            scanTimers();
            scanTimeouts();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    Set<Class<?>> getTimerEvents() {
        Iterable<TypeDefinition> filter = filter(filter(typeMap.values(), withType(TIMER_EVENT)), annotationMatchingMyFsm(TimerEvent.class));
        Iterable<Class<?>> transform = Iterables.transform(filter, toClass());
        return newHashSet(transform);
    }

    Set<TransitionOnTimeoutEvent> getTimeoutTransitions(Class<?> stateClass) {
        return typeMap.get(stateClass).timeoutEvents;
    }

    private Predicate<TypeDefinition> annotationMatchingMyFsm(final Class<? extends Annotation> annotationClass) {
        return new Predicate<TypeDefinition>() {
            @Override
            public boolean apply(@Nullable TypeDefinition input) {
                String fsmName = getFsmNameFromAnnotation(input.cls, annotationClass);
                return Strings.isNullOrEmpty(fsmName) || getFsmName(fsm.getClass()).equals(fsmName);
            }
        };
    }

    Optional<Class<?>> findStateClass(Class<?> stateClass) {
        Set<Class<?>> states =
                newHashSet(transform(filter(typeMap.values(), withTypeAndClass(STATE, stateClass)), toClass()));
        if (states.iterator().hasNext()) {
            return Optional.<Class<?>>of(states.iterator().next());
        }

        states = newHashSet(transform(filter(typeMap.values(), withTypeAndClass(START_STATE, stateClass)), toClass()));
        if (states.iterator().hasNext()) {
            return Optional.<Class<?>>of(states.iterator().next());
        }

        return Optional.absent();
    }

    Optional<Set<Class<?>>> findStartState() {
        Set<Class<?>> startStateClasses = newHashSet(transform(filter(typeMap.values(), withType(START_STATE)), toClass()));

        if (startStateClasses.isEmpty()) {
            return Optional.absent();
        }

        if (startStateClasses.size() == 1) {
            Class<?> startStateClass = startStateClasses.iterator().next();
            String fsmName = getFsmNameFromAnnotation(startStateClass, StartState.class);
            if (Strings.isNullOrEmpty(fsmName) || getFsmName(fsm.getClass()).equals(fsmName)) {
                return Optional.<Set<Class<?>>>of(Sets.<Class<?>>newHashSet(startStateClass));
            }
            return Optional.absent();
        }

        Set<Class<?>> filtered = newHashSet(filter(startStateClasses, new Predicate<Class<?>>() {
            @Override
            public boolean apply(Class<?> input) {
                return getFsmName(fsm.getClass()).equals(getFsmNameFromAnnotation(input, StartState.class)) || input.getClass().getDeclaringClass()
                        .equals(fsm.getClass());
            }
        }));

        if (filtered.isEmpty()) {
            return Optional.absent();
        }

        if (filtered.size() == 1) {
            return Optional.<Set<Class<?>>>of(Sets.<Class<?>>newHashSet(filtered.iterator().next()));
        }

        return Optional.of(filtered);
    }

    private String getFsmNameFromAnnotation(Class<?> input, Class<? extends Annotation> annotationClass) {
        if (annotationClass.equals(StartState.class)) {
            StartState annotation = input.getAnnotation((Class<StartState>) annotationClass);
            if (annotation == null) {
                throw new RuntimeException("shouldn't input: " + input + ", have annotation: " + annotationClass);
            }
            return annotation.fsm();
        }
        if (annotationClass.equals(State.class)) {
            State annotation = input.getAnnotation((Class<State>) annotationClass);
            if (annotation == null) {
                throw new RuntimeException("shouldn't input: " + input + ", have annotation: " + annotationClass);
            }
            return annotation.fsm();
        }
        if (annotationClass.equals(TimerEvent.class)) {
            TimerEvent annotation = input.getAnnotation((Class<TimerEvent>) annotationClass);
            if (annotation == null) {
                throw new RuntimeException("shouldn't input: " + input + ", have annotation: " + annotationClass);
            }
            return annotation.fsm();
        }
        throw new RuntimeException("don't know: " + annotationClass);
    }

    private void scanTimers() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        for (Class c : getAnnotatedWith(Class.class, TimerEvent.class)) {
            typeMap.put(c, new TypeDefinition(c, TimerEvent.class));
            //            try {
            //                Constructor declaredConstructor = c.getDeclaredConstructor(fsm.getClass());
            //                declaredConstructor.setAccessible(true);
            //                typeMap.put(c, new TypeDefinition(declaredConstructor.newInstance(fsm), TimerEvent.class));
            //            } catch (NoSuchMethodException e) {
            //                Constructor declaredConstructor = c.getDeclaredConstructor();
            //                declaredConstructor.setAccessible(true);
            //                typeMap.put(c, new TypeDefinition(declaredConstructor.newInstance(), TimerEvent.class));
            //            }
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
                typeMap.put(c, new TypeDefinition(c, stateAnnotation));
                //                try {
                //                    Constructor declaredConstructor = c.getDeclaredConstructor(fsm.getClass());
                //                    declaredConstructor.setAccessible(true);
                //                    typeMap.put(c, new TypeDefinition(declaredConstructor.newInstance(fsm), stateAnnotation));
                //                } catch (NoSuchMethodException e) {
                //                    Constructor declaredConstructor = c.getDeclaredConstructor();
                //                    declaredConstructor.setAccessible(true);
                //                    typeMap.put(c, new TypeDefinition(declaredConstructor.newInstance(), stateAnnotation));
                //                }
            }

            //            for (final Method m : getAnnotatedWith(Method.class, stateAnnotation)) {
            //                Class<?> returnType = m.getReturnType();
            //                if (typeMap.containsKey(returnType)) {
            //                    continue;
            //                }
            //
            //                if (!m.getDeclaringClass().equals(fsm.getClass())) {
            //                    LOGGER.debug("skipping state constructing method: " + m + ", as it is not declared by the fsm itself...");
            //                    continue;
            //                }
            //                Class<?>[] parameterTypes = m.getParameterTypes();
            //                if (parameterTypes.length == 0) {
            //                    typeMap.put(returnType, new TypeDefinition(m.invoke(fsm), stateAnnotation));
            //                } else if ((parameterTypes.length == 1) && parameterTypes[0].equals(fsm.getClass())) {
            //                    typeMap.put(parameterTypes[0], new TypeDefinition(m.invoke(fsm, fsm), stateAnnotation));
            //                } else {
            //                    throw new RuntimeException("unknown parameters for state constructing method: " + m);
            //                }
            //            }
            //
            //            for (final Field f : getAnnotatedWith(Field.class, stateAnnotation)) {
            //                if (!f.getDeclaringClass().equals(fsm.getClass())) {
            //                    LOGGER.debug("skipping field: " + f + ", as it is not declared by the fsm itself...");
            //                    continue;
            //                }
            //
            //                if (!typeMap.containsKey(f.getType())) {
            //                    throw new RuntimeException("type of field: " + f + " does not constitute a known state");
            //                }
            //
            //                f.setAccessible(true);
            //                Object state = f.get(fsm);
            //                if (state != null) {
            //                    LOGGER.warn("overwriting field: " + f + " with: " + typeMap.get(f.getType()));
            //                }
            //                f.set(fsm, typeMap.get(f.getType()).cls);
            //            }
        }
    }

    private Function<TypeDefinition, Class<?>> toClass() {
        return new Function<TypeDefinition, Class<?>>() {
            @Override
            public Class<?> apply(TypeDefinition input) {
                return input.cls;
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
                return (input.type == type) && input.cls.equals(clazz);
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

    private String getFsmName(Class<?> fsmClass) {
        StateMachine annotation = fsmClass.getAnnotation(StateMachine.class);
        if (annotation == null) {
            throw new RuntimeException("fsm: " + fsmClass + " must be annotated with StateMachine");
        }
        return !Strings.isNullOrEmpty(annotation.name()) ? annotation.name() : fsmClass.getClass().getName();
    }

    private Reflections setupReflections(ClassLoader classLoader) {
        final Set<String> pkgs = getPackages(fsm.getClass());
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder()
                .addClassLoader(classLoader)
                .addUrls(tmpDir())
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

    private URL tmpDir() {
        try {
            return new File(System.getProperty("java.io.tmpdir")).toURI().toURL();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Set<String> getPackages(Class<?> fsmClass) {
        StateMachine annotation = fsmClass.getAnnotation(StateMachine.class);
        Set<String> answer = newHashSet();
        if (annotation != null) {
            answer = newHashSet(annotation.pkgs());
        }
        if (answer.isEmpty()) {
            answer = newHashSet(fsmClass.getPackage().getName());
        }
        return answer;
    }

    Optional<Set<Method>> findTransitionForEvent(Class<?> stateClass, Object event) {
        Set<Method> transitions = getAllMethods(stateClass, withAnnotation(Transition.class), withParameters(event.getClass()));
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

    Set<Method> findActionImpliedMethods(Class<?> cls) {
        Set<Method> methods = getAllMethods(cls, withAnnotation(Action.class), withActionType(ActionType.Implied), withParameters());
        methods.addAll(getAllMethods(cls, withAnnotation(Action.class), withActionType(ActionType.Implied), withParameters(fsm.getClass())));
        return methods;
    }

    Set<Method> findActionOnExitMethods(Class<?> cls) {
        Set<Method> methods = getAllMethods(cls, withAnnotation(Action.class), withActionType(ActionType.OnExit), withParameters());
        methods.addAll(getAllMethods(cls, withAnnotation(Action.class), withActionType(ActionType.OnExit), withParameters(fsm.getClass())));
        return methods;
    }

    Set<Method> findActionOnEnterMethods(Class<?> cls) {
        Set<Method> methods = getAllMethods(cls, withAnnotation(Action.class), withActionType(ActionType.OnEnter), withParameters());
        methods.addAll(getAllMethods(cls, withAnnotation(Action.class), withActionType(ActionType.OnEnter), withParameters(fsm.getClass())));
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

    static class TypeDefinition<T> {
        private final Type type;
        private final Class<T> cls;
        private final Set<TransitionOnTimeoutEvent> timeoutEvents = newHashSet();

        private TypeDefinition(Class<T> cls, Class<? extends Annotation> stateAnnotation) {
            this.type = Type.from(stateAnnotation);
            this.cls = cls;
        }

        @Override
        public String toString() {
            return "StateDefinition{" +
                    "cls=" + cls +
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
