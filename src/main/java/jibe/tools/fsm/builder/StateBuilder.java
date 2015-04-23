package jibe.tools.fsm.builder;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import jibe.tools.fsm.annotations.State;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;

/**
 *
 */
public class StateBuilder {

    final String name;
    private final Class<? extends Annotation> annotationClass;
    private final FSMBuilder fsmBuilder;
    private final Set<TransitionBuilder> transitionBuilders = newHashSet();
    private Optional<String> onEnterCode = Optional.absent();
    private Optional<String> onExitCode = Optional.absent();

    public StateBuilder(FSMBuilder fsmBuilder, String name) {
        this(fsmBuilder, name, State.class);
    }

    public StateBuilder(FSMBuilder fsmBuilder, String name, Class<? extends Annotation> annotationClass) {
        this.fsmBuilder = fsmBuilder;
        this.name = name;
        this.annotationClass = annotationClass;
        fsmBuilder.addStateBuilder(this);
    }

    public StateBuilder transitions(TransitionBuilder... transitionBuilders) {
        this.transitionBuilders.addAll(newHashSet(transitionBuilders));
        return this;
    }

    StateFacade build() {
        StateFacade stateFacade = new StateFacade(name, annotationClass, onEnterCode, onExitCode);
        for (TransitionBuilder tb : transitionBuilders) {
            stateFacade.addTransition(tb.build());
        }
        return stateFacade;
    }

    public StateBuilder onEnter(String onEnterCode) {
        this.onEnterCode = Optional.of(Objects.requireNonNull(Strings.nullToEmpty(onEnterCode)));
        return this;
    }

    public StateBuilder onExit(String onExitCode) {
        this.onExitCode = Optional.of(Objects.requireNonNull(Strings.nullToEmpty(onExitCode)));
        return this;
    }

    class StateFacade {
        private final String name;
        private Class<? extends Annotation> annotationClass;
        private Set<TransitionBuilder.TransitionFacade> transitions = newHashSet();

        public StateFacade(String name, Class<? extends Annotation> annotationClass, Optional<String> onEnterCode, Optional<String> onExitCode) {
            this.name = name;
            this.annotationClass = annotationClass;
        }

        public void addTransition(TransitionBuilder.TransitionFacade transitionFacade) {
            this.transitions.add(transitionFacade);
        }

        public Set<TransitionBuilder.TransitionFacade> getTransitions() {
            return transitions;
        }

        public String getName() {
            return name;
        }

        public Class<? extends Annotation> getAnnotationClass() {
            return annotationClass;
        }
    }
}
