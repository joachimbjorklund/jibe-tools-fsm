package jibe.tools.fsm.builder;

/**
 *
 */
public class TransitionOnTimeoutBuilder {

    private final String name;
    public String toState;

    public TransitionOnTimeoutBuilder(String name) {
        this.name = name;
    }

    public TransitionOnTimeoutBuilder to(String toState) {
        this.toState = toState;
        return this;
    }

    public TransitionOnTimeoutFacade build() {
        return new TransitionOnTimeoutFacade(name, toState);
    }

    class TransitionOnTimeoutFacade {
        private final String name;
        private final String toState;

        public TransitionOnTimeoutFacade(String name, String toState) {
            this.name = name;
            this.toState = toState;
        }
    }
}
