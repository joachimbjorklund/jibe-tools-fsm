package jibe.tools.fsm.builder;

import jibe.tools.fsm.annotations.StartState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class StartStateBuilder extends StateBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartStateBuilder.class);

    public StartStateBuilder(FSMBuilder fsmBuilder, String name) {
        super(fsmBuilder, name, StartState.class);
    }
}
