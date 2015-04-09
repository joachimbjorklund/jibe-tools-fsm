package jibe.tools.fsm.api.test.trafficlight;

import jibe.tools.fsm.annotations.Action;
import jibe.tools.fsm.annotations.StartState;
import jibe.tools.fsm.annotations.State;
import jibe.tools.fsm.annotations.StateMachine;
import jibe.tools.fsm.annotations.Transition;
import jibe.tools.fsm.annotations.TransitionOnTimeout;
import jibe.tools.fsm.api.ActionType;
import jibe.tools.fsm.api.Engine;
import jibe.tools.fsm.core.EngineFactory;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 *
 */
@StateMachine
public class TrafficLightFSM {

    private Object currentState;

    public static void main(String[] args) throws InterruptedException {

        Engine engine = EngineFactory.newInstance().newEngine(new TrafficLightFSM());
        engine.start();

        Thread.sleep(30000);
        engine.event("error");

        Thread.sleep(10000);
        engine.event("fixed");

        Thread.sleep(30000);
        engine.stop();
    }

    @StartState
    private class RedLight {
        @Action(ActionType.OnEnter)
        public void onEnter() {
            System.out.println("RED");
            currentState = this;
        }

        @TransitionOnTimeout(period = 10, timeUnit = SECONDS)
        public RedAndYellowLight timeout() {
            return new RedAndYellowLight();
        }

        @Transition
        public BlinkingYellowLight event(String s) {
            if ("error".equals(s)) {
                return new BlinkingYellowLight();
            }
            return null;
        }
    }

    @State
    private class RedAndYellowLight {
        @Action(ActionType.OnEnter)
        public void onEnter() {
            System.out.println("RED_AND_YELLOW");
            currentState = this;
        }

        @TransitionOnTimeout(period = 2, timeUnit = SECONDS)
        public GreenLight timeout() {
            return new GreenLight();
        }

        @Transition
        public BlinkingYellowLight event(String s) {
            if ("error".equals(s)) {
                return new BlinkingYellowLight();
            }
            return null;
        }
    }

    @State
    private class YellowLight {
        @Action(ActionType.OnEnter)
        public void onEnter() {
            System.out.println("YELLOW");
            currentState = this;
        }

        @TransitionOnTimeout(period = 2, timeUnit = SECONDS)
        public RedLight timeout() {
            return new RedLight();
        }

        @Transition
        public BlinkingYellowLight event(String s) {
            if ("error".equals(s)) {
                return new BlinkingYellowLight();
            }
            return null;
        }
    }

    @State
    private class GreenLight {
        @Action(ActionType.OnEnter)
        public void onEnter() {
            System.out.println("GREEN");
            currentState = this;
        }

        @TransitionOnTimeout(period = 2, timeUnit = SECONDS)
        public YellowLight timeout() {
            return new YellowLight();
        }

        @Transition
        public BlinkingYellowLight event(String s) {
            if ("error".equals(s)) {
                return new BlinkingYellowLight();
            }
            return null;
        }
    }

    @State
    private class BlinkingYellowLight {
        @Action(ActionType.OnEnter)
        public void onEnter() {
            System.out.println("BLINKING_YELLOW");
            currentState = this;
        }

        @Transition
        public RedLight event(String s) {
            if ("fixed".equals(s)) {
                return new RedLight();
            }
            return null;
        }
    }
}
