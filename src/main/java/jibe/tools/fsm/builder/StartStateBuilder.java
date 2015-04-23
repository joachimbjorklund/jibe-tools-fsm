package jibe.tools.fsm.builder;

import jibe.tools.fsm.annotations.StartState;

/**
 *
 */
public class StartStateBuilder extends StateBuilder {

    public StartStateBuilder(FSMBuilder fsmBuilder, String name) {
        super(fsmBuilder, name, StartState.class);
    }
}
