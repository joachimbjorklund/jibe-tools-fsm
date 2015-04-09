package jibe.tools.fsm.api.test.trafficlight;

import jibe.tools.fsm.annotations.Action;
import jibe.tools.fsm.annotations.StartState;
import jibe.tools.fsm.annotations.State;
import jibe.tools.fsm.annotations.StateMachine;
import jibe.tools.fsm.annotations.TimerEvent;
import jibe.tools.fsm.annotations.Transition;
import jibe.tools.fsm.annotations.TransitionOnTimeout;
import jibe.tools.fsm.api.ActionType;
import jibe.tools.fsm.api.Engine;
import jibe.tools.fsm.core.EngineFactory;
import org.joda.time.LocalDateTime;
import org.joda.time.Period;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static jibe.tools.fsm.api.EventType.ScheduledFixedRateTimer;

/**
 *
 */
@StateMachine
public class TrafficLightFSM {

    public static final int RED_MAX = 10;
    private static final int RED_YELLOW_MAX = 2;
    private static final int YELLOW_MAX = 2;
    private static final int GREEN_MAX = 5;

    private LocalDateTime wentOn;

    public static void main(String[] args) throws InterruptedException {

        TrafficLightFSM fsm = new TrafficLightFSM();
        Engine engine = EngineFactory.newInstance().newEngine(fsm);
        engine.start();
    }

    @StartState
    private class Red {

        @TransitionOnTimeout(period = RED_MAX, timeUnit = SECONDS)
        public RedYellow timeout() {
            return new RedYellow();
        }

        @Action(ActionType.OnEnter)
        public void onEnter() {
            System.out.println("RED");
            wentOn = LocalDateTime.now();
        }
    }

    @State
    private class RedYellow {
        @Transition
        public Green event(HeartBeat heartBeat) {
            if (new Period(wentOn, heartBeat.getTriggeredAt()).getSeconds() >= RED_YELLOW_MAX) {
                return new Green();
            }
            return null;
        }

        @Action(ActionType.OnEnter)
        public void onEnter() {
            System.out.println("RED_AND_YELLOW");
            wentOn = LocalDateTime.now();
        }
    }

    @State
    private class Yellow {
        @Transition
        public Red event(HeartBeat heartBeat) {
            if (new Period(wentOn, heartBeat.getTriggeredAt()).getSeconds() >= YELLOW_MAX) {
                return new Red();
            }
            return null;
        }

        @Action(ActionType.OnEnter)
        public void onEnter() {
            System.out.println("YELLOW");
            wentOn = LocalDateTime.now();
        }
    }

    @State
    private class Green {
        @Transition
        public Yellow event(HeartBeat heartBeat) {
            if (new Period(wentOn, heartBeat.getTriggeredAt()).getSeconds() >= GREEN_MAX) {
                return new Yellow();
            }
            return null;
        }

        @Action(ActionType.OnEnter)
        public void onEnter() {
            System.out.println("GREEN");
            wentOn = LocalDateTime.now();
        }
    }

    @TimerEvent(type = ScheduledFixedRateTimer, delay = 0, period = 100, timeUnit = TimeUnit.MILLISECONDS)
    public class HeartBeat {
        private LocalDateTime triggeredAt;

        public LocalDateTime getTriggeredAt() {
            return triggeredAt;
        }

        @Action(ActionType.Implied)
        public void trigger() {
            triggeredAt = LocalDateTime.now();
        }
    }
}
