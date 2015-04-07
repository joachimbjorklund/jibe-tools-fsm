package jibe.tools.fsm.api.test.phone.states;

import jibe.tools.fsm.annotations.Action;
import jibe.tools.fsm.annotations.State;
import jibe.tools.fsm.annotations.Transition;
import jibe.tools.fsm.api.test.phone.PhoneFSM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jibe.tools.fsm.api.ActionType.OnEnter;
import static jibe.tools.fsm.api.ActionType.OnExit;

/**
 *
 */
@State
public class OffHook {
    private final Logger LOGGER = LoggerFactory.getLogger(OffHook.class);

    @Action(OnEnter)
    public void onEnter(PhoneFSM fsm) {
        LOGGER.debug(String.format("onEnter: fsm: %s", fsm));
    }

    @Action(OnEnter)
    public void onEnter2(PhoneFSM fsm) {
        LOGGER.debug(String.format("OnEnter: fsm2: %s", fsm));
    }

    @Action(OnExit)
    public void onExit() {
        LOGGER.debug(String.format("onExit: %s", this));
    }

    @Transition
    public Dialing event(String event) {
        if (!event.startsWith("dial")) {
            return null;
        }
        return new Dialing();
    }
}
