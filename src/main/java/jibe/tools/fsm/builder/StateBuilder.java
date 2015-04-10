package jibe.tools.fsm.builder;

import com.google.common.base.Throwables;
import javassist.CtClass;
import javassist.CtMethod;
import jibe.tools.fsm.annotations.State;

import java.lang.annotation.Annotation;

/**
 *
 */
public class StateBuilder {
    protected final String name;
    protected final FSMBuilder fsmBuilder;
    private CtClass state;
    private TransitionBuilder[] transitionBuilders;

    protected StateBuilder(FSMBuilder fsmBuilder, String name, Class<? extends Annotation> annotationClass) {
        this.fsmBuilder = fsmBuilder;
        this.name = name;
        state = fsmBuilder.makeClassWithAnnotation(name, annotationClass);
    }

    public StateBuilder(FSMBuilder fsmBuilder, String name) {
        this(fsmBuilder, name, State.class);
    }

    public FSMBuilder transitions(TransitionBuilder... transitionBuilders) {
        this.transitionBuilders = transitionBuilders;
        return fsmBuilder;
    }

    public FSMBuilder build() {
        try {
            for (TransitionBuilder tb : transitionBuilders) {
                CtMethod m = makeTransitionMethod(tb);
                if (m != null) {
                    state.addMethod(m);
                }
            }
            FSMBuilder.writeFile(state);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return fsmBuilder;
    }

    private CtMethod makeTransitionMethod(TransitionBuilder transitionBuilder) {
        String toState = transitionBuilder.getToState();
        StateBuilder stateBuilder = fsmBuilder.state(toState);
        fsmBuilder.makeTransitionMethod(state, stateBuilder.state, transitionBuilder.getOnEvent());
        return null;
    }
}
