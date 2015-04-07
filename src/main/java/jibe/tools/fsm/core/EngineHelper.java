package jibe.tools.fsm.core;

import com.google.common.base.Converter;
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
import jibe.tools.fsm.annotations.Transition;
import jibe.tools.fsm.api.ActionType;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.MethodParameterScanner;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newHashSet;
import static jibe.tools.fsm.core.EngineHelper.StateDefinition.StateType.START_STATE;
import static jibe.tools.fsm.core.EngineHelper.StateDefinition.StateType.STATE;
import static org.reflections.ReflectionUtils.getAll;
import static org.reflections.ReflectionUtils.getAllMethods;
import static org.reflections.ReflectionUtils.withAnnotation;
import static org.reflections.ReflectionUtils.withParameters;

/**
 *
 */
public class EngineHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineHelper.class);

    private static final Converter<Class<?>, AnnotatedElement> CLASS_AE_CONVERTER = new Converter<Class<?>, AnnotatedElement>() {
        @Override
        protected AnnotatedElement doForward(Class<?> aClass) {
            return aClass;
        }

        @Override
        protected Class<?> doBackward(AnnotatedElement annotatedElement) {
            return (Class<?>) annotatedElement;
        }
    };

    private final Object fsm;
    private final Reflections reflections;
    private final HashMap<Class<?>, StateDefinition> stateMap = new HashMap<>();

    public EngineHelper(Object fsm) {
        this.fsm = fsm;
        reflections = setupReflections();
        try {
            setupStatesAndFields();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public Optional<Object> findState(Class<?> stateClass) {
        Set<Object> states =
                newHashSet(transform(filter(stateMap.values(), withTypeAndClass(STATE, stateClass)), toState()));
        if (states.iterator().hasNext()) {
            return Optional.of(states.iterator().next());
        }

        return Optional.absent();
    }

    Optional<Object> findStartState() {
        Set<Object> startStates =
                newHashSet(transform(filter(stateMap.values(), withType(START_STATE)), toState()));

        if (startStates.isEmpty()) {
            return Optional.absent();
        }

        if (startStates.size() == 1) {
            return Optional.of(startStates.iterator().next());
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
            return Optional.of(filtered.iterator().next());
        }

        throw new RuntimeException("ambiguous startStates: " + startStates);
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
        throw new RuntimeException("don't know: " + annotationClass);
    }

    private void setupStatesAndFields() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        if (!stateMap.isEmpty()) {
            return;
        }
        for (Class<? extends Annotation> stateAnnotation : newHashSet(StartState.class, State.class)) {
            for (final Class c : getAnnotatedWith(Class.class, stateAnnotation)) {
                if ((c.getAnnotation(StartState.class) != null) && (c.getAnnotation(State.class) != null)) {

                }
                try {
                    stateMap.put(c, new StateDefinition(c.getDeclaredConstructor(fsm.getClass()).newInstance(fsm), stateAnnotation));
                } catch (NoSuchMethodException e) {
                    stateMap.put(c, new StateDefinition(c.getDeclaredConstructor().newInstance(), stateAnnotation));
                }
            }

            for (final Method m : getAnnotatedWith(Method.class, stateAnnotation)) {
                Class<?> returnType = m.getReturnType();
                if (stateMap.containsKey(returnType)) {
                    continue;
                }

                if (!m.getDeclaringClass().equals(fsm.getClass())) {
                    LOGGER.debug("skipping state constructing method: " + m + ", as it is not declared by the fsm itself...");
                    continue;
                }
                Class<?>[] parameterTypes = m.getParameterTypes();
                if (parameterTypes.length == 0) {
                    stateMap.put(returnType, new StateDefinition(m.invoke(fsm), stateAnnotation));
                } else if ((parameterTypes.length == 1) && parameterTypes[0].equals(fsm.getClass())) {
                    stateMap.put(parameterTypes[0], new StateDefinition(m.invoke(fsm, fsm), stateAnnotation));
                } else {
                    throw new RuntimeException("unknown parameters for state constructing method: " + m);
                }
            }

            for (final Field f : getAnnotatedWith(Field.class, stateAnnotation)) {
                if (!f.getDeclaringClass().equals(fsm.getClass())) {
                    LOGGER.debug("skipping field: " + f + ", as it is not declared by the fsm itself...");
                    continue;
                }

                if (!stateMap.containsKey(f.getType())) {
                    throw new RuntimeException("type of field: " + f + " does not constitute a known state");
                }

                f.setAccessible(true);
                Object state = f.get(fsm);
                if (state != null) {
                    LOGGER.warn("overwriting field: " + f + " with: " + stateMap.get(f.getType()));
                }
                f.set(fsm, stateMap.get(f.getType()).state);
            }
        }
    }

    private Function<StateDefinition, Object> toState() {
        return new Function<StateDefinition, Object>() {
            @Override
            public Object apply(StateDefinition input) {
                return input.state;
            }
        };
    }

    private Predicate<StateDefinition> withType(final StateDefinition.StateType stateType) {
        return new Predicate<StateDefinition>() {
            @Override
            public boolean apply(StateDefinition input) {
                return input.stateType == stateType;
            }
        };
    }

    private Predicate<StateDefinition> withTypeAndClass(final StateDefinition.StateType stateType, final Class<?> clazz) {
        return new Predicate<StateDefinition>() {
            @Override
            public boolean apply(StateDefinition input) {
                return (input.stateType == stateType) && input.state.getClass().equals(clazz);
            }
        };
    }

    //    Optional<Object> findState(Object fsm, Class<? extends Annotation> annotationType)
    //            throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
    //        for (final Class c : getAnnotatedWith(Class.class, annotationType)) {
    //            try {
    //                stateMap.put(c, c.getDeclaredConstructor(fsm.getClass()).newInstance(fsm));
    //            } catch (NoSuchMethodException e) {
    //                try {
    //                    stateMap.put(c, c.getDeclaredConstructor().newInstance());
    //                } catch (NoSuchMethodException e1) {
    //                    return Optional.absent();
    //                }
    //            }
    //        }
    //
    //        for (final Method m : getAnnotatedWith(Method.class, annotationType)) {
    //            Class<?> returnType = m.getReturnType();
    //            if (stateMap.containsKey(returnType)) {
    //                continue;
    //            }
    //            if (!m.getDeclaringClass().equals(fsm.getClass())) {
    //                continue;
    //            }
    //            Class<?>[] parameterTypes = m.getParameterTypes();
    //            if (parameterTypes.length == 0) {
    //                stateMap.put(returnType, m.invoke(fsm));
    //            } else if (parameterTypes.length == 1 && parameterTypes[0].equals(fsm.getClass())) {
    //                stateMap.put(parameterTypes[0], m.invoke(fsm, fsm));
    //            } else {
    //                throw new RuntimeException("dont know how to invoke: " + m);
    //            }
    //        }
    //
    //        for (final Field f : getAnnotatedWith(Field.class, annotationType)) {
    //            if (!f.getDeclaringClass().equals(fsm.getClass())) {
    //                continue;
    //            }
    //            f.setAccessible(true);
    //            Object startState = f.get(fsm);
    //            if (startState != null) {
    //                if (!stateMap.containsKey(startState.getClass())) {
    //                    stateMap.put(startState.getClass(), startState);
    //                }
    //            } else if (stateMap.containsKey(f.getType())) {
    //                f.set(fsm, stateMap.get(f.getType()));
    //            } else {
    //                Set<? extends Class<?>> all = getAll(newHashSet(f.getType()));
    //                if (all.size() == 1) {
    //                    Class<?> next = all.iterator().next();
    //                    try {
    //                        stateMap.put(f.getType(), next.getDeclaredConstructor(fsm.getClass()).newInstance(fsm));
    //                    } catch (NoSuchMethodException e) {
    //                        try {
    //                            stateMap.put(f.getType(), next.getDeclaredConstructor().newInstance());
    //                        } catch (NoSuchMethodException e1) {
    //                            throw e1;
    //                        }
    //                    }
    //                    f.set(fsm, stateMap.get(f.getType()));
    //                }
    //            }
    //        }
    //
    //        if (stateMap.isEmpty()) {
    //            return Optional.absent();
    //        }
    //
    //        if (stateMap.size() == 1) {
    //            return Optional.of(stateMap.values().iterator().next());
    //        }
    //
    //        Set<Class<?>> classes = newHashSet(CLASS_AE_CONVERTER.reverse()
    //                .convertAll(filterAnnotations(fsm, StartState.class, CLASS_AE_CONVERTER.convertAll(stateMap.keySet()))));
    //        LOGGER.debug(String.format("classes: %s", classes));
    //
    //        if (classes.size() == 1) {
    //            return Optional.of(stateMap.get(classes.iterator().next()));
    //        }
    //
    //        throw new RuntimeException("ambiguous start stateMap: " + stateMap.values());
    //    }

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

    //    private Set<AnnotatedElement> filterAnnotations(Object fsm, Class<? extends Annotation> type, Iterable<AnnotatedElement> search) {
    //        Set<AnnotatedElement> answer = new HashSet<>();
    //        for (AnnotatedElement annotated : search) {
    //            Annotation annotation = annotated.getAnnotation(type);
    //            Set<Method> fsmMeth = new HashSet<>();
    //            if (annotation != null) {
    //                fsmMeth = ReflectionUtils.getMethods(annotation.getClass(), new Predicate<Method>() {
    //                    @Override
    //                    public boolean apply(@Nullable Method input) {
    //                        return input.getName().equals("fsm") && (input.getParameterTypes().length == 0);
    //                    }
    //                });
    //            }
    //
    //            String belongTo = null;
    //            if (fsmMeth.size() == 1) {
    //                try {
    //                    belongTo = (String) fsmMeth.iterator().next().invoke(annotation);
    //                } catch (Exception e) {
    //                    throw Throwables.propagate(e);
    //                }
    //            }
    //
    //            if (!Strings.isNullOrEmpty(belongTo)) {
    //                if (getFsmName(fsm).equals(belongTo)) {
    //                    answer.add(annotated);
    //                }
    //            } else {
    //                Class<?> declaringClass;
    //                if (annotated instanceof Member) {
    //                    declaringClass = ((Member) annotated).getDeclaringClass();
    //                } else {
    //                    declaringClass = ((Class) annotated).getDeclaringClass();
    //                }
    //                if ((declaringClass != null) && (declaringClass.equals(fsm.getClass()) || declaringClass.isAssignableFrom(fsm.getClass()))) {
    //                    answer.add(annotated);
    //                }
    //            }
    //        }
    //        return answer;
    //    }

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
        Set<String> answer = new HashSet<>();
        if (annotation != null) {
            answer = newHashSet(annotation.pkgs());
        }
        if (answer.isEmpty()) {
            answer = newHashSet(fsm.getClass().getPackage().getName());
        }
        return answer;
    }

    public Optional<Method> findTransitionForEvent(Object state, Object event) {
        Set<Method> transitions = getAllMethods(state.getClass(), withAnnotation(Transition.class), withParameters(event.getClass()));
        if (transitions.isEmpty()) {
            return Optional.absent();
        } else if (transitions.size() > 1) {
            throw new RuntimeException("ambiguous transitions for event: " + event.getClass() + " state: " + state.getClass());
        } else {
            final Method transition = transitions.iterator().next();
            Set<Class<?>> foundStates = getAll(reflections.getTypesAnnotatedWith(State.class), new Predicate<Class<?>>() {
                @Override
                public boolean apply(@Nullable Class<?> input) {
                    return input.equals(transition.getReturnType());
                }
            });
            if (foundStates.size() != 1) {
                throw new RuntimeException("transition found but not returning a @State annotated object");
            }
            return Optional.of(transition);
        }
    }

    public Set<Method> findOnExit(Object state) {
        Set<Method> methods = getAllMethods(state.getClass(), withAnnotation(Action.class), withActionType(ActionType.OnExit), withParameters());
        methods.addAll(getAllMethods(state.getClass(), withAnnotation(Action.class), withActionType(ActionType.OnExit), withParameters(fsm.getClass())));
        return methods;
    }

    public Set<Method> findOnEnter(Object state) {
        Set<Method> methods = getAllMethods(state.getClass(), withAnnotation(Action.class), withActionType(ActionType.OnEnter), withParameters());
        methods.addAll(getAllMethods(state.getClass(), withAnnotation(Action.class), withActionType(ActionType.OnEnter), withParameters(fsm.getClass())));
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

    static class StateDefinition {
        private final StateType stateType;
        private final Object state;

        private StateDefinition(Object state, Class<? extends Annotation> stateAnnotation) {
            this.stateType = StateType.from(stateAnnotation);
            this.state = state;
        }

        @Override
        public String toString() {
            return "StateDefinition{" +
                    "state=" + state +
                    ", stateType=" + stateType +
                    '}';
        }

        enum StateType {
            START_STATE,
            STATE;

            public static StateType from(Class<? extends Annotation> annotationType) {
                if (annotationType.equals(State.class)) {
                    return StateType.STATE;
                }
                if (annotationType.equals(StartState.class)) {
                    return StateType.START_STATE;
                }
                throw new RuntimeException("unknown annotationType: " + annotationType);
            }
        }
    }
}
