package jibe.tools.fsm.api.test.simple;

import jibe.tools.fsm.annotations.StartState;
import jibe.tools.fsm.annotations.State;
import jibe.tools.fsm.annotations.StateMachine;
import jibe.tools.fsm.annotations.Transition;

@StateMachine
class SimpleFSM {
    @StartState
    static class State1 {
        @Transition
        public State2 state2(String event) {
            // guards applied here.... return null;
            return new State2();
        }
    }

    @State
    static class State2 {
        @Transition
        public State1 state1(String event) {
            // guards applied here.... return null;
            return new State1();
        }
    }
}
