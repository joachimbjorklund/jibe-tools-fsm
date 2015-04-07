package jibe.tools.fsm.api.test.phone;

import jibe.tools.fsm.annotations.State;
import jibe.tools.fsm.annotations.StateMachine;
import jibe.tools.fsm.api.test.phone.states.Dialing;
import jibe.tools.fsm.api.test.phone.states.OffHook;

/**
 *
 */
@StateMachine
public class PhoneFSM {

    @State
    public OffHook stateOffHook;

    @State
    public Dialing stateDialing;

    @State
    public class Ringing {

    }

}
