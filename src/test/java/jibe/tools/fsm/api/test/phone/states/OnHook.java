package jibe.tools.fsm.api.test.phone.states;

import jibe.tools.fsm.annotations.Action;
import jibe.tools.fsm.annotations.StartState;
import jibe.tools.fsm.annotations.Transition;
import jibe.tools.fsm.api.test.phone.PhoneFSM;
import jibe.tools.fsm.api.test.phone.events.OffHookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jibe.tools.fsm.api.ActionType.OnEnter;
import static jibe.tools.fsm.api.ActionType.OnExit;

/**
 *
 */
@StartState
public class OnHook {
    private final Logger LOGGER = LoggerFactory.getLogger(OnHook.class);
    private PhoneFSM phoneFSM;

    public OnHook(PhoneFSM phoneFSM) {
        this.phoneFSM = phoneFSM;
    }

    @Transition
    public OffHook event(String event) {
        if ("someone picks up the handset".equals(event)) {
            return phoneFSM.stateOffHook;
        }
        return null;
    }

    @Transition
    public OffHook event(OffHookEvent offHookEvent) {
        return new OffHook();
    }

    @Action(OnEnter)
    public void onEnter() {
        LOGGER.debug("Im in...");
    }

    @Action(OnExit)
    public void onExit() {
        LOGGER.debug("Im out of here...");
    }
}
