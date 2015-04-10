package jibe.tools.fsm.builder;

/**
 *
 */
public class TransitionBuilder {

    private Object onEvent;
    private String toState;

    public static TransitionBuilder transition() {
        return new TransitionBuilder();
    }

    String getToState() {
        return toState;
    }

    Object getOnEvent() {
        return onEvent;
    }

    public TransitionBuilder to(String toState) {
        this.toState = toState;
        return this;
    }

    public TransitionBuilder onEvent(Object event) {
        this.onEvent = event;
        return this;
    }
}
