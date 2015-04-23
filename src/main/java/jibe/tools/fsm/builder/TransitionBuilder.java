package jibe.tools.fsm.builder;

/**
 *
 */
public class TransitionBuilder {

    private final String name;
    public Object event;
    public String toState;

    public TransitionBuilder(String name) {
        this.name = name;
    }

    public TransitionBuilder toState(String toState) {
        this.toState = toState;
        return this;
    }

    public TransitionBuilder onEvent(Object event) {
        this.event = event;
        return this;
    }

    public TransitionFacade build() {
        return new TransitionFacade(name, event, toState);
    }

    class TransitionFacade {
        private final String name;
        private final String toState;
        private final Object event;

        public TransitionFacade(String name, Object event, String toState) {
            this.name = name;
            this.event = event;
            this.toState = toState;
        }

        public String getToState() {
            return toState;
        }
    }
}
